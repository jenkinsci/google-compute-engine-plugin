package com.google.jenkins.plugins.computeengine;

import com.google.api.services.compute.model.Operation;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import hudson.slaves.AbstractCloudComputer;
import hudson.slaves.AbstractCloudSlave;
import hudson.model.Node;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.NodeProperty;
import hudson.slaves.RetentionStrategy;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

public class ComputeEngineInstance extends AbstractCloudSlave {
    private static final Logger LOGGER = Logger.getLogger(ComputeEngineInstance.class.getName());

    public Integer launchTimeout; // Seconds
    public final String zone;
    public final String cloudName;

    public ComputeEngineInstance(String cloudName,
                                 String name,
                                 String zone,
                                 String nodeDescription,
                                 String remoteFS,
                                 int numExecutors,
                                 Node.Mode mode,
                                 String labelString,
                                 ComputerLauncher launcher,
                                 RetentionStrategy retentionStrategy,
                                 Integer launchTimeout)
            throws Descriptor.FormException,
            IOException {
        super(name, nodeDescription, remoteFS, numExecutors, mode, labelString, launcher, retentionStrategy, Collections.<NodeProperty<?>>emptyList());
        this.launchTimeout = launchTimeout;
        this.zone = zone;
        this.cloudName = cloudName;
    }

    @Override
    public AbstractCloudComputer createComputer() {
        return new ComputeEngineComputer(this);
    }

    @Override
    protected void _terminate(TaskListener listener) throws IOException, InterruptedException {
        ComputeEngineCloud cloud = getCloud();
        if (cloud != null || cloud.client != null) {
            listener.error(String.format("Cloud ({0} or Cloud Client were null", cloud.getCloudName()));
            return;
        }

        // If the instance is running, attempt to terminate it
        Operation.Error opError = cloud.client.terminateInstanceWithStatus(cloud.projectId, zone, name, "RUNNING");
        if(opError != null) {
            listener.error(String.format("Instance {0} could not be terminated: {1}", name, opError.getErrors().get(0).getMessage()));
            return;
        } else {
            LOGGER.info(String.format("Instance {0} was successfully terminated", name));
        }


    }

    public long getLaunchTimeoutMillis() {
        return launchTimeout * 1000L;
    }
    public ComputeEngineCloud getCloud() {
        return (ComputeEngineCloud) Jenkins.getInstance().getCloud(cloudName);
    }

    @Extension
    public static final class DescriptorImpl extends SlaveDescriptor {
        @Override
        public String getDisplayName() {
            return Messages.ComputeEngineAgent_DisplayName();
        }
    }
}
