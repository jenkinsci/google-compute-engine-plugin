/*
 * Copyright 2018 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.jenkins.plugins.computeengine;

import com.google.api.services.compute.model.Operation;
import com.google.jenkins.plugins.computeengine.client.ComputeClient;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import hudson.slaves.AbstractCloudComputer;
import hudson.slaves.AbstractCloudSlave;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.NodeProperty;
import hudson.slaves.RetentionStrategy;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Optional;

public class ComputeEngineInstance extends AbstractCloudSlave {
    private static final long serialVersionUID = 1;
    private static final Logger LOGGER = Logger.getLogger(ComputeEngineInstance.class.getName());

    // TODO: https://issues.jenkins-ci.org/browse/JENKINS-55518
    public final String zone;
    public final String cloudName;
    public final String sshUser;
    public transient final Optional<WindowsConfiguration> windowsConfig;
    public final boolean createSnapshot;
    public final boolean oneShot;
    public Integer launchTimeout; // Seconds
    private Boolean connected;

    public ComputeEngineInstance(String cloudName,
                                 String name,
                                 String zone,
                                 String nodeDescription,
                                 String sshUser,
                                 String remoteFS,
                                 Optional<WindowsConfiguration> windowsConfig,
                                 boolean createSnapshot,
                                 boolean oneShot,
                                 int numExecutors,
                                 Mode mode,
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
        this.sshUser = sshUser;
        this.windowsConfig = windowsConfig;
        this.createSnapshot = createSnapshot;
        this.oneShot = oneShot;
    }

    @Override
    public AbstractCloudComputer createComputer() {
        return new ComputeEngineComputer(this);
    }

    @Override
    protected void _terminate(TaskListener listener) throws IOException, InterruptedException {
        try {
            ComputeEngineCloud cloud = getCloud();

            ExecutorService executor = Executors.newFixedThreadPool(1);

            executor.submit(new TerminateRunnable(this, cloud));
            //TODO: if termination fails, then jenkins doesn't know, deleted node from pool,
        } catch (CloudNotFoundException cnfe) {
            listener.error(cnfe.getMessage());
            return;
        }

    }

    /**
     * Runnable to parallelize the termination and snapshot creation process.
     */
    private class TerminateRunnable implements Runnable {
        private ComputeEngineCloud cloud;
        private Computer computer;
        private ComputeEngineInstance computeEngineInstance;

        /**
         * Constructor.
         *
         * @param computeEngineInstance
         * @param cloud
         */
        TerminateRunnable(ComputeEngineInstance computeEngineInstance, ComputeEngineCloud cloud) {
            this.cloud = cloud;
            this.computer = computeEngineInstance.toComputer();
            this.computeEngineInstance = computeEngineInstance;
        }

        /**
         * Checks to see if snapshot creation at deletion time requirements are fulfilled and creates if true.
         * Terminates the instance afterwards.
         */
        @Override
        public void run() {
            String projectId = cloud.getProjectId();
            if (computeEngineInstance.oneShot && computeEngineInstance.createSnapshot && computer != null &&
                    !computer.getBuilds().failureOnly().isEmpty()) {
                LOGGER.log(Level.INFO, "Creating snapshot for node " + name);
                try {
                    cloud.getClient().createSnapshot(projectId, zone, name);
                } catch (IOException | InterruptedException e) {
                    // exceptions thrown by snapshot creation are InterruptedException and IOException
                    LOGGER.log(Level.WARNING, "Error creating snapshot for instance " + name + ". " + e);
                }
            }

            // If the instance is running, attempt to terminate it. This is an asynch call and we
            // return immediately, hoping for the best.
            try {
                ComputeClient client = cloud.getClient();
                Operation deleteOperation = client.terminateInstanceWithStatus(cloud.projectId, zone, name, "RUNNING");
                // Ensure erros from the execution of the operation itself are logged.
                client.waitForOperationCompletion(projectId, deleteOperation.getName(), zone, 300 * 1000);
            } catch (IOException | InterruptedException e) {
                LOGGER.log(Level.WARNING, "Error deleting instance " + name + ". " + e);
            }
        }
    }

    /**
     * Based on the instance configuration, whether to create snapshot for an instance with failed builds at deletion time.
     * @return Whether or not to create the snapshot.
     */
    public boolean isCreateSnapshot() {
        return createSnapshot;
    }

    public String getCloudName() {
        return cloudName;
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

    public ComputeEngineCloud getCloud() throws CloudNotFoundException {
        ComputeEngineCloud cloud = (ComputeEngineCloud) Jenkins.get().getCloud(cloudName);
        if (cloud == null)
            throw new CloudNotFoundException(String.format("Could not find cloud %s in Jenkins configuration", cloudName));
        return cloud;
    }

    @Extension
    public static final class DescriptorImpl extends SlaveDescriptor {
        @Override
        public String getDisplayName() {
            return Messages.ComputeEngineAgent_DisplayName();
        }
    }
}
