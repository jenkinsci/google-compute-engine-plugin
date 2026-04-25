/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.jenkins.plugins.computeengine;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.cloud.graphite.platforms.plugin.client.ComputeClient.OperationException;
import com.google.common.base.Strings;
import com.google.jenkins.plugins.computeengine.ssh.GoogleKeyCredential;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import hudson.slaves.AbstractCloudComputer;
import hudson.slaves.AbstractCloudSlave;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.RetentionStrategy;
import java.io.IOException;
import java.util.Collections;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import lombok.Builder;
import lombok.Getter;

@Getter
public class ComputeEngineInstance extends AbstractCloudSlave {
    private static final long serialVersionUID = 1;
    private static final Logger LOGGER = Logger.getLogger(ComputeEngineInstance.class.getName());
    private static final long CREATE_SNAPSHOT_TIMEOUT_LINUX = 120000;
    private static final long CREATE_SNAPSHOT_TIMEOUT_WINDOWS = 600000;

    // TODO: https://issues.jenkins-ci.org/browse/JENKINS-55518
    private final String zone;
    private final String cloudName;
    private final String sshUser;
    private final WindowsConfiguration windowsConfig;
    private final SshConfiguration sshConfig;
    private final boolean terminateIdleDuringShutdown;
    private final boolean createSnapshot;
    private final boolean oneShot;
    private final boolean ignoreProxy;
    private final String javaExecPath;
    private final int sshPort;
    private final GoogleKeyCredential sshKeyCredential;
    // Carried from InstanceConfiguration at provision time because the launcher has no access to whether
    // startup script exit reporting was configured
    private final boolean waitForStartupScript;
    private Integer launchTimeout; // Seconds
    private Boolean connected;
    private transient ComputeEngineCloud cloud;

    @Builder
    private ComputeEngineInstance(
            String cloudName,
            String name,
            String zone,
            String nodeDescription,
            String sshUser,
            String remoteFS,
            // NOTE(stephenashank): Could not use optional due to serialization req.
            @Nullable WindowsConfiguration windowsConfig,
            @Nullable SshConfiguration sshConfig,
            boolean createSnapshot,
            boolean oneShot,
            boolean ignoreProxy,
            boolean terminateIdleDuringShutdown,
            int numExecutors,
            Mode mode,
            String labelString,
            ComputerLauncher launcher,
            RetentionStrategy retentionStrategy,
            Integer launchTimeout,
            int sshPort,
            // NOTE(craigatgoogle): Could not use Optional due to serialization req.
            @Nullable String javaExecPath,
            @Nullable GoogleKeyCredential sshKeyCredential,
            boolean waitForStartupScript,
            @Nullable ComputeEngineCloud cloud)
            throws Descriptor.FormException, IOException {
        super(
                name,
                nodeDescription,
                remoteFS,
                numExecutors,
                mode,
                labelString,
                launcher,
                retentionStrategy,
                Collections.emptyList());
        this.launchTimeout = launchTimeout;
        this.zone = zone;
        this.cloudName = cloudName;
        this.sshUser = sshUser;
        this.windowsConfig = windowsConfig;
        this.sshConfig = sshConfig;
        this.createSnapshot = createSnapshot;
        this.oneShot = oneShot;
        this.ignoreProxy = ignoreProxy;
        this.terminateIdleDuringShutdown = terminateIdleDuringShutdown;
        this.sshPort = sshPort;
        this.javaExecPath = javaExecPath;
        this.sshKeyCredential = sshKeyCredential;
        this.waitForStartupScript = waitForStartupScript;
        this.cloud = cloud;
    }

    @Override
    public AbstractCloudComputer createComputer() {
        return new ComputeEngineComputer(this);
    }

    @Override
    protected void _terminate(TaskListener listener) throws IOException, InterruptedException {
        try {
            ComputeEngineCloud cloud = getCloud();

            Computer computer = this.toComputer();
            if (this.oneShot
                    && this.createSnapshot
                    && computer != null
                    && !computer.getBuilds().failureOnly().isEmpty()) {
                LOGGER.log(Level.INFO, "Creating snapshot for node ... " + this.getNodeName());
                long createSnapshotTimeout =
                        (windowsConfig != null) ? CREATE_SNAPSHOT_TIMEOUT_WINDOWS : CREATE_SNAPSHOT_TIMEOUT_LINUX;
                cloud.getClient()
                        .createSnapshotSync(cloud.getProjectId(), this.zone, this.getNodeName(), createSnapshotTimeout);
            }

            boolean instanceExistsInCloud = instanceExistsInCloud(cloud);

            // If the instance exists in the cloud, attempt to terminate it. This is an async call and we
            // return immediately, hoping for the best.
            if (instanceExistsInCloud) {
                cloud.getClient().terminateInstanceAsync(cloud.getProjectId(), zone, name);
                LOGGER.finer(() -> "Termination request was made for " + this.getNodeName());
            } else {
                LOGGER.fine(() -> "Instance " + name + " not found in GCP, nothing to terminate");
            }
        } catch (CloudNotFoundException cnfe) {
            listener.error(cnfe.getMessage());
        } catch (OperationException oe) {
            listener.error(oe.getError().toPrettyString());
        }
    }

    public void onConnected() {
        this.connected = true;
    }

    public long getLaunchTimeoutMillis() {
        return launchTimeout * 1000L;
    }

    /** @return The configured SSH port, defaulting to 22 for backward compatibility. */
    public int getSshPort() {
        return sshPort > 0 ? sshPort : InstanceConfiguration.DEFAULT_SSH_PORT;
    }

    /** @return The configured Java executable path, or else the default Java binary. */
    public String getJavaExecPathOrDefault() {
        return !Strings.isNullOrEmpty(javaExecPath) ? javaExecPath : "java";
    }

    /** @return The configured Linux SSH key pair for this {@link ComputeEngineInstance}. */
    public Optional<GoogleKeyCredential> getSSHKeyCredential() {
        return Optional.ofNullable(sshKeyCredential);
    }

    private boolean instanceExistsInCloud(ComputeEngineCloud cloud) {
        try {
            return cloud.getClient().getInstance(cloud.getProjectId(), zone, name) != null;
        } catch (GoogleJsonResponseException gjre) {
            if (gjre.getStatusCode() == 404) {
                return false;
            }
            LOGGER.log(Level.WARNING, "Error checking instance " + name, gjre);
            return false;
        } catch (IOException ioe) {
            LOGGER.log(Level.WARNING, "Error checking instance " + name, ioe);
            return false;
        }
    }

    public ComputeEngineCloud getCloud() throws CloudNotFoundException {
        ComputeEngineCloud cloud = (ComputeEngineCloud) Jenkins.get().getCloud(cloudName);
        if (cloud == null)
            throw new CloudNotFoundException(
                    String.format("Could not find cloud %s in Jenkins configuration", cloudName));
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
