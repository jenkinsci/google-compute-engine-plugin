package com.google.jenkins.plugins.computeengine;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.*;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.util.Collections;
import java.util.logging.Logger;

public class ComputeEngineInstance extends AbstractCloudSlave {
    private static final Logger LOGGER = Logger.getLogger(ComputeEngineInstance.class.getName());
    public final String zone;
    public final String cloudName;
    public Integer launchTimeout; // Seconds
    private Boolean connected;

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
        if (cloud == null || cloud.client == null) {
            listener.error(String.format("Cloud (%s or Cloud Client were null", cloud.getCloudName()));
            return;
        }

        // If the instance is running, attempt to terminate it. This is an asynch call and we
        // return immediately, hoping for the best.
        cloud.client.terminateInstanceWithStatus(cloud.projectId, zone, name, "RUNNING");
    }

    public void onConnected() {
        this.connected = true;
    }

    public Boolean getConnected() {
        return this.connected;
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
