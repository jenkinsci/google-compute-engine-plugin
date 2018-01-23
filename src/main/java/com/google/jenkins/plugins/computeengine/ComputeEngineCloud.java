package com.google.jenkins.plugins.computeengine;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.Region;
import com.google.jenkins.plugins.computeengine.client.ClientFactory;
import com.google.jenkins.plugins.computeengine.client.ComputeClient;
import com.google.jenkins.plugins.credentials.oauth.GoogleOAuth2Credentials;
import hudson.Extension;
import hudson.model.*;

import java.io.IOException;
import java.io.PrintStream;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import hudson.security.ACL;
import hudson.slaves.AbstractCloudImpl;
import hudson.slaves.NodeProvisioner.PlannedNode;
import hudson.slaves.Cloud;

import javax.annotation.Nonnull;
import javax.servlet.ServletException;

import hudson.util.FormValidation;
import hudson.util.HttpResponses;
import hudson.util.ListBoxModel;
import hudson.util.StreamTaskListener;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;

public class ComputeEngineCloud extends AbstractCloudImpl {
    private static final Logger LOGGER = Logger.getLogger(ComputeEngineCloud.class.getName());
    private static final SimpleFormatter sf = new SimpleFormatter();
    private static final String CLOUD_PREFIX = "gce-";
    private static Map<String, String> REQUIRED_LABEL;

    static {
        REQUIRED_LABEL = new HashMap<String, String>();
        REQUIRED_LABEL.put("jenkinscloud", "gcp");
    }

    public final String projectId;
    public final String credentialsId;
    public final List<? extends InstanceConfiguration> configurations;

    protected transient ComputeClient client;

    @DataBoundConstructor
    public ComputeEngineCloud(
            String cloudName,
            String projectId,
            String credentialsId,
            String instanceCapStr,
            List<? extends InstanceConfiguration> configurations) {
        super(createCloudId(cloudName), instanceCapStr);

        if (configurations == null) {
            this.configurations = Collections.emptyList();
        } else {
            this.configurations = configurations;
        }

        this.credentialsId = credentialsId;
        this.projectId = projectId;

        readResolve();
    }

    public String getCloudName() {
        return name.substring(CLOUD_PREFIX.length());
    }

    @Override
    public String getDisplayName() {
        return getCloudName();
    }

    private static String createCloudId(String name) {
        return CLOUD_PREFIX + name.trim();
    }

    private void readResolve() {
        try {
            ClientFactory clientFactory = new ClientFactory(Jenkins.getInstance(), new ArrayList<DomainRequirement>(),
                    credentialsId);
            this.client = clientFactory.compute();
        } catch (IOException e) {
            this.client = null;
            //TODO: log
        }

        for (InstanceConfiguration c : configurations) {
            c.cloud = this;
            if (c.googleLabels == null) {
                c.googleLabels = new HashMap<>();
            }
            c.googleLabels.putAll(REQUIRED_LABEL);
        }
    }

    @Override
    public Collection<PlannedNode> provision(Label label, int excessWorkload) {
        try {
            List<PlannedNode> r = new ArrayList<PlannedNode>();
            //TODO: retrieve and iterate a list of InstanceConfiguration that match label
            final InstanceConfiguration config = getInstanceConfig(label);
            LOGGER.log(Level.INFO, "Provisioning node from config " + config + " for excess workload of " + excessWorkload + " units of label '" + label + "'");
            while (excessWorkload > 0) {
                if (config == null) {
                    LOGGER.warning(String.format("Could not find instance configuration {0} in cloud {1}", config.getDisplayName(), getCloudName()));
                    break;
                }

                Integer availableCapacity = availableNodeCapacity();
                if (availableCapacity <= 0) {
                    LOGGER.warning(String.format("Could not provision new nodes to meet excess workload demand ({0}). Cloud provider {1} has reached its configured capacity of {2}", excessWorkload, getCloudName(), getInstanceCap()));
                    break;
                }

                final ComputeEngineInstance node = config.provision(StreamTaskListener.fromStdout(), label);
                Jenkins.getInstance().addNode(node);
                r.add(new PlannedNode(config.getDisplayName(), Computer.threadPoolForRemoting.submit(new Callable<Node>() {
                    public Node call() throws Exception {
                        long startTime = System.currentTimeMillis();
                        while ((System.currentTimeMillis() - startTime) < config.getLaunchTimeout() * 1000) {
                            try {
                                node.toComputer().connect(false).get();
                            } catch (Exception e) {
                            }
                        }
                        LOGGER.log(Level.WARNING, "Failed to connect to node within launch timeout");
                        return node;
                    }
                }), node.getNumExecutors()));
                excessWorkload -= 1;
            }
        } catch (Exception e) {
            LOGGER.log(Level.INFO, "Error provisioning node: {0}", e.getMessage());
        }
        return null;
    }

    /**
     * Determine the number of nodes that may be provisioned for this Cloud.
     *
     * @return
     * @throws IOException
     */
    private synchronized Integer availableNodeCapacity() throws IOException {
        try {
            List<Instance> instances = client.getInstancesWithLabel(projectId, REQUIRED_LABEL);
            Integer capacity = getInstanceCap() - instances.size();
            LOGGER.info(String.format("Found capacity for {0} nodes in cloud {1}", capacity, getCloudName()));
            return (getInstanceCap() - instances.size());
        } catch (IOException ioe) {
            LOGGER.warning(String.format("An error occurred counting the number of existing instances in cloud {0}: {1}", getCloudName(), ioe.getMessage()));
            throw ioe;
        }
    }

    private synchronized ComputeEngineInstance getAnAgent(InstanceConfiguration config, Label requiredLabel) {
        try {
            return config.provision(StreamTaskListener.fromStdout(), requiredLabel);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean canProvision(Label label) {
        //TODO: implement
        return true;
    }

    /**
     * Gets {@link InstanceConfiguration} that has the matching {@link Label}.
     */
    public InstanceConfiguration getInstanceConfig(Label label) {
        for (InstanceConfiguration c : configurations) {
            if (c.getMode() == Node.Mode.NORMAL) {
                if (label == null || label.matches(c.getLabelSet())) {
                    return c;
                }
            } else if (c.getMode() == Node.Mode.EXCLUSIVE) {
                if (label != null && label.matches(c.getLabelSet())) {
                    return c;
                }
            }
        }
        return null;
    }

    /**
     * Gets {@link InstanceConfiguration} that has the matching Description.
     */
    public InstanceConfiguration getInstanceConfig(String description) {
        for (InstanceConfiguration c : configurations) {
            if (c.description.equals(description)) {
                return c;
            }
        }
        return null;
    }

    public HttpResponse doProvision(@QueryParameter String configuration) throws ServletException, IOException {
        checkPermission(PROVISION);
        if (configuration == null) {
            throw HttpResponses.error(SC_BAD_REQUEST, "The 'configuration' query parameter is missing");
        }
        InstanceConfiguration c = getInstanceConfig(configuration);
        if (c == null) {
            throw HttpResponses.error(SC_BAD_REQUEST, "No such Instance Configuration: " + configuration);
        }

        ComputeEngineInstance node = c.provision(StreamTaskListener.fromStdout(), null);
        if (node == null)
            throw HttpResponses.error(SC_BAD_REQUEST, "Could not provision new node.");
        Jenkins.getInstance().addNode(node);

        return HttpResponses.redirectViaContextPath("/computer/" + node.getNodeName());
    }

    @Extension
    public static class GoogleCloudDescriptor extends Descriptor<Cloud> {

        @Nonnull
        @Override
        public String getDisplayName() {
            return Messages.ComputeEngineCloud_DisplayName();
        }


        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Jenkins context, @QueryParameter String value) {
            if (context == null || !context.hasPermission(Item.CONFIGURE)) {
                return new StandardListBoxModel();
            }

            List<DomainRequirement> domainRequirements = new ArrayList<DomainRequirement>();
            return new StandardListBoxModel()
                    .withEmptySelection()
                    .withMatching(
                            CredentialsMatchers.instanceOf(GoogleOAuth2Credentials.class),
                            CredentialsProvider.lookupCredentials(
                                    StandardCredentials.class, context, ACL.SYSTEM,
                                    domainRequirements));
        }

        public FormValidation doCheckCredentialsId(@AncestorInPath Jenkins context,
                                                   @QueryParameter("projectId") String projectId,
                                                   @QueryParameter String value) {
            if (value == "") {
                return FormValidation.warning("No credential selected");
            }

            try {
                ClientFactory clientFactory = new ClientFactory(context, new ArrayList<DomainRequirement>(), value);
                ComputeClient compute = clientFactory.compute();
                List<Region> regions = compute.getRegions(projectId);
                return FormValidation.ok("The credential successfully made an API request to Google Compute Engine.");
            } catch (IOException ioe) {
                return FormValidation.error(ioe.getMessage());
            }
        }
    }

    public static void log(Logger logger, Level level, TaskListener listener, String message) {
        log(logger, level, listener, message, null);
    }

    public static void log(Logger logger, Level level, TaskListener listener, String message, Throwable exception) {
        logger.log(level, message, exception);
        if (listener != null) {
            if (exception != null)
                message += " Exception: " + exception;
            LogRecord lr = new LogRecord(level, message);
            PrintStream printStream = listener.getLogger();
            printStream.print(sf.format(lr));
        }
    }
}
