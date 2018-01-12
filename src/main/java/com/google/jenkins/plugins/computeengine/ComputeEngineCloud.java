package com.google.jenkins.plugins.computeengine;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.google.api.services.compute.model.Region;
import com.google.jenkins.plugins.computeengine.client.ClientFactory;
import com.google.jenkins.plugins.computeengine.client.ComputeClient;
import com.google.jenkins.plugins.credentials.oauth.GoogleOAuth2Credentials;
import hudson.Extension;
import hudson.model.Item;

import java.io.IOException;
import java.util.Collections;
import java.util.logging.Logger;

import hudson.model.Label;
import hudson.model.Node;
import hudson.security.ACL;
import hudson.slaves.AbstractCloudImpl;
import hudson.slaves.NodeProvisioner.PlannedNode;
import hudson.slaves.Cloud;
import hudson.model.Descriptor;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.StreamTaskListener;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

public class ComputeEngineCloud extends AbstractCloudImpl {
    private static final Logger logger =
            Logger.getLogger(ComputeEngineCloud.class.getName());


    public final String projectId;
    public final String credentialsId;
    public final List<? extends InstanceConfiguration> configurations;

    protected transient ComputeClient client;

    @DataBoundConstructor
    public ComputeEngineCloud(
            String name,
            String projectId,
            String credentialsId,
            String instanceCapStr,
            List<? extends InstanceConfiguration> configurations) {
        super(name, instanceCapStr);
        if (configurations == null) {
            this.configurations = Collections.emptyList();
        } else {
            this.configurations = configurations;
        }
        this.credentialsId = credentialsId;
        this.projectId = projectId;
    }

    private void readResolve() {
        try {
            ClientFactory clientFactory = new ClientFactory(Jenkins.getInstance(), new ArrayList<DomainRequirement>(),
                    credentialsId);
        } catch (IOException e) {
            this.client = null;
            //TODO: log
        }

        for(InstanceConfiguration c : configurations) {
           c.cloud = this;
       }
    }
    @Override
    public Collection<PlannedNode> provision(Label label, int excessWorkload) {
        try {
            List<PlannedNode> r = new ArrayList<PlannedNode>();
            final InstanceConfiguration config = getTemplate(label);
            while(excessWorkload > 0) {

            }
        } catch (Exception e) {}
        return null;
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
    public InstanceConfiguration getTemplate(Label label) {
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

        public FormValidation doCheckCredentialsId(@AncestorInPath Jenkins context, @QueryParameter String value) {
            if (value == "") {
                return FormValidation.warning("No credential selected");
            }

            try {
                ClientFactory clientFactory = new ClientFactory(context, new ArrayList<DomainRequirement>(), value);
                ComputeClient compute = clientFactory.compute();
                List<Region> regions = compute.getRegions();
                return FormValidation.ok("The credential successfully made an API request to Google Compute Engine.");
            } catch (IOException ioe) {
                return FormValidation.error(ioe.getMessage());
            }
        }
    }
}
