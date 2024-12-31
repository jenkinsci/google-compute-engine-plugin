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
import com.google.api.services.compute.model.AccessConfig;
import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.NetworkInterface;
import com.google.api.services.compute.model.Operation;
import com.google.cloud.graphite.platforms.plugin.client.ComputeClient;
import com.google.cloud.graphite.platforms.plugin.client.ComputeClient.OperationException;
import com.google.cloud.graphite.platforms.plugin.client.model.GuestAttribute;
import com.google.cloud.graphite.platforms.plugin.client.model.InstanceResourceData;
import com.google.cloud.graphite.platforms.plugin.client.util.ClientUtil;
import com.google.common.collect.ImmutableList;
import com.trilead.ssh2.Connection;
import com.trilead.ssh2.HTTPProxyData;
import com.trilead.ssh2.SCPClient;
import com.trilead.ssh2.Session;
import hudson.ProxyConfiguration;
import hudson.Util;
import hudson.model.TaskListener;
import hudson.remoting.Channel;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.SlaveComputer;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.SocketTimeoutException;
import java.util.Base64;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import jenkins.model.Jenkins;
import lombok.Getter;

public abstract class ComputeEngineComputerLauncher extends ComputerLauncher {
    private static final Logger LOGGER = Logger.getLogger(ComputeEngineComputerLauncher.class.getName());
    private static final SimpleFormatter sf = new SimpleFormatter();
    private static final String AGENT_JAR = "agent.jar";
    private static final String GUEST_ATTRIBUTE_HOST_KEY_NAMESPACE = "hostkeys";

    // TODO(google-compute-engine-plugin/issues/134): make this configurable
    private static final int SSH_PORT = 22;
    private static final int SSH_TIMEOUT_MILLIS = 10000;
    private static final int SSH_SLEEP_MILLIS = 5000;

    private final String insertOperationId;
    private final String zone;
    private final String cloudName;

    @Getter
    protected final boolean useInternalAddress;

    public ComputeEngineComputerLauncher(
            String cloudName, String insertOperationId, String zone, boolean useInternalAddress) {
        super();
        this.cloudName = cloudName;
        this.insertOperationId = insertOperationId;
        this.zone = zone;
        this.useInternalAddress = useInternalAddress;
    }

    public static void log(Logger logger, Level level, TaskListener listener, String message) {
        log(logger, level, listener, message, null);
    }

    public static void log(Logger logger, Level level, TaskListener listener, String message, Throwable exception) {
        logger.log(level, message, exception);
        if (listener != null) {
            if (exception != null) message += " Exception: " + exception;
            LogRecord lr = new LogRecord(level, message);
            PrintStream printStream = listener.getLogger();
            printStream.print(sf.format(lr));
        }
    }

    private void log(
            Level level, ComputeEngineComputer computer, TaskListener listener, String message, Throwable exception) {
        try {
            ComputeEngineCloud cloud = computer.getCloud();
            cloud.log(getLogger(), level, listener, message, exception);
        } catch (CloudNotFoundException cnfe) {
            log(getLogger(), Level.SEVERE, listener, "FATAL: Could not get cloud", cnfe);
        }
    }

    protected void logException(
            ComputeEngineComputer computer, TaskListener listener, String message, Throwable exception) {
        log(Level.WARNING, computer, listener, message, exception);
    }

    protected void logInfo(ComputeEngineComputer computer, TaskListener listener, String message) {
        log(Level.INFO, computer, listener, message, null);
    }

    protected void logWarning(ComputeEngineComputer computer, TaskListener listener, String message) {
        log(Level.WARNING, computer, listener, message, null);
    }

    protected void logSevere(ComputeEngineComputer computer, TaskListener listener, String message) {
        log(Level.SEVERE, computer, listener, message, null);
    }

    protected abstract Logger getLogger();

    @Override
    public void launch(SlaveComputer slaveComputer, TaskListener listener) {
        ComputeEngineComputer computer = (ComputeEngineComputer) slaveComputer;
        ComputeEngineCloud cloud;

        try {
            cloud = computer.getCloud();
        } catch (CloudNotFoundException cnfe) {
            log(LOGGER, Level.SEVERE, listener, String.format("Could not get cloud %s", cloudName));
            return;
        }

        ComputeEngineInstance node = computer.getNode();
        if (node == null) {
            log(LOGGER, Level.SEVERE, listener, String.format("Could not get node from computer"));
            return;
        }

        // Wait until the Operation from the Instance insert is complete or fails
        Operation.Error opError = new Operation.Error();
        try {
            LOGGER.info(String.format(
                    "Launch will wait %d for operation %s to complete...", node.getLaunchTimeout(), insertOperationId));
            /* This call will log a null error when the operation is complete, or a relevant error if it
             * fails. */
            try {
                Operation operation = cloud.getClient()
                        .waitForOperationCompletion(
                                cloud.getProjectId(), insertOperationId, zone, node.getLaunchTimeoutMillis());
                opError = operation.getError();
            } catch (OperationException e) {
                opError = e.getError();
            }
            if (opError != null) {
                LOGGER.info(String.format(
                        "Launch failed while waiting for operation %s to complete. Operation error was %s. Terminating instance.",
                        insertOperationId, opError.getErrors().get(0).getMessage()));
                terminateNode(computer, listener);
                return;
            }
        } catch (InterruptedException e) {
            LOGGER.info(String.format(
                    "Launch failed while waiting for operation %s to complete. Operation error was %s. Terminating instance",
                    insertOperationId, opError.getErrors().get(0).getMessage()));
            terminateNode(computer, listener);
            return;
        }

        try {
            // The operation succeeded. Now wait for the Instance status to be RUNNING
            OUTER:
            while (true) {
                switch (computer.getInstanceStatus()) {
                    case "PROVISIONING":
                    case "STAGING":
                        cloud.log(
                                LOGGER,
                                Level.FINEST,
                                listener,
                                String.format("Instance %s is being prepared...", computer.getName()));
                        break;
                    case "RUNNING":
                        cloud.log(
                                LOGGER,
                                Level.FINEST,
                                listener,
                                String.format("Instance %s is running and ready...", computer.getName()));
                        break OUTER;
                    case "STOPPING":
                    case "SUSPENDING":
                    case "TERMINATED":
                        cloud.log(
                                LOGGER,
                                Level.FINEST,
                                listener,
                                String.format("Instance %s is being shut down...", computer.getName()));
                        break;
                        // TODO: Although the plugin doesn't put instances in the STOPPED or SUSPENDED states,
                        // it should handle them if they are placed in that state out-of-band.
                    case "STOPPED":
                    case "SUSPENDED":
                        cloud.log(
                                LOGGER,
                                Level.FINEST,
                                listener,
                                String.format(
                                        "Instance %s was unexpectedly stopped or suspended...", computer.getName()));
                        return;
                }
                Thread.sleep(5000);
            }

            // Initiate the next launch phase. This is likely an SSH-based process for Linux hosts.
            computer.refreshInstance();
            launch(computer, listener);
        } catch (IOException ioe) {
            ioe.printStackTrace(listener.error(ioe.getMessage()));
            terminateNode(slaveComputer, listener);
        } catch (InterruptedException ie) {

        }
    }

    private static void terminateNode(SlaveComputer slaveComputer, TaskListener listener) {
        ComputeEngineInstance node = (ComputeEngineInstance) slaveComputer.getNode();
        if (node != null) {
            try {
                node.terminate();
            } catch (Exception e) {
                listener.error(String.format("Failed to terminate node %s", node.getDisplayName()));
            }
        } else {
            LOGGER.fine(
                    String.format("Tried to terminate unknown node from computer %s", slaveComputer.getDisplayName()));
        }
    }

    private boolean testCommand(
            ComputeEngineComputer computer,
            Connection conn,
            String checkCommand,
            PrintStream logger,
            TaskListener listener)
            throws IOException, InterruptedException {
        logInfo(computer, listener, "Verifying: " + checkCommand);
        return conn.exec(checkCommand, logger) == 0;
    }

    protected abstract Optional<Connection> setupConnection(
            ComputeEngineInstance node, ComputeEngineComputer computer, TaskListener listener) throws Exception;

    protected abstract String getPathSeparator();

    private boolean checkJavaInstalled(
            ComputeEngineComputer computer,
            Connection conn,
            PrintStream logger,
            TaskListener listener,
            String javaExecPath) {
        try {
            if (testCommand(computer, conn, String.format("%s -fullversion", javaExecPath), logger, listener)) {
                return true;
            }
        } catch (IOException | InterruptedException ex) {
            logException(computer, listener, "Failed to check java: ", ex);
            return false;
        }

        logWarning(computer, listener, String.format("Java is not installed at %s", javaExecPath));
        return false;
    }

    private void copyAgentJar(ComputeEngineComputer computer, Connection conn, TaskListener listener, String jenkinsDir)
            throws IOException {
        SCPClient scp = conn.createSCPClient();
        logInfo(computer, listener, "Copying agent.jar to: " + jenkinsDir);
        scp.put(Jenkins.get().getJnlpJars(AGENT_JAR).readFully(), AGENT_JAR, jenkinsDir);
    }

    private String getJavaLaunchString(String javaExecPath, String jenkinsDir) {
        return String.format("%s -jar %s%s%s", javaExecPath, jenkinsDir, getPathSeparator(), AGENT_JAR);
    }

    private void launch(ComputeEngineComputer computer, TaskListener listener) {
        ComputeEngineInstance node = computer.getNode();
        if (node == null) {
            logWarning(computer, listener, "Could not get node from computer");
            return;
        }

        Connection conn = null;
        Optional<Connection> cleanupConn;
        PrintStream logger = listener.getLogger();
        logInfo(computer, listener, "Launching instance: " + node.getNodeName());
        Session sess = null;
        try {
            cleanupConn = setupConnection(node, computer, listener);
            if (!cleanupConn.isPresent()) {
                return;
            }
            conn = cleanupConn.get();
            String javaExecPath = node.getJavaExecPathOrDefault();
            if (!checkJavaInstalled(computer, conn, logger, listener, javaExecPath)) {
                return;
            }
            String jenkinsDir = node.getRemoteFS();
            copyAgentJar(computer, conn, listener, jenkinsDir);
            String launchString = getJavaLaunchString(javaExecPath, jenkinsDir);
            logInfo(computer, listener, "Launching Jenkins agent via plugin SSH: " + launchString);
            sess = conn.openSession();
            sess.execCommand(launchString);
            Session finalSess = sess;
            Connection finalConn = conn;
            computer.setChannel(sess.getStdout(), sess.getStdin(), logger, new Channel.Listener() {
                @Override
                public void onClosed(Channel channel, IOException cause) {
                    finalSess.close();
                    finalConn.close();
                }
            });
        } catch (Exception e) {
            if (sess != null) {
                sess.close();
            }
            if (conn != null) {
                conn.close();
            }
            logException(computer, listener, "Error: ", e);
        }
    }

    protected Connection connectToSsh(ComputeEngineComputer computer, TaskListener listener) throws Exception {
        ComputeEngineInstance node = computer.getNode();
        if (node == null) {
            throw new IllegalArgumentException("A ComputeEngineComputer with no node was provided");
        }

        ComputeClient client = node.getCloud().getClient();
        final long timeout = node.getLaunchTimeoutMillis();
        final long startTime = System.currentTimeMillis();
        Connection conn = null;
        while (true) {
            try {
                long waitTime = System.currentTimeMillis() - startTime;
                if (timeout > 0 && waitTime > timeout) {
                    // TODO(google-compute-engine-plugin/issues/135): better exception
                    throw new Exception("Timed out after "
                            + (waitTime / 1000)
                            + " seconds of waiting for ssh to become available. (maximum timeout configured is "
                            + (timeout / 1000)
                            + ")");
                }
                Instance instance = computer.refreshInstance();
                // the instance will be null when the node is terminated
                if (instance == null) {
                    return null;
                }

                String host = "";

                // TODO(google-compute-engine-plugin/issues/136): handle multiple NICs
                NetworkInterface nic = instance.getNetworkInterfaces().get(0);

                if (this.useInternalAddress) {
                    host = nic.getNetworkIP();
                } else {
                    // Look for a public IPv4 address
                    if (nic.getAccessConfigs() != null) {
                        for (AccessConfig ac : nic.getAccessConfigs()) {
                            if (ac.getType().equals(NetworkInterfaceIpStackMode.NAT_TYPE)) {
                                host = ac.getNatIP();
                            }
                        }
                    }
                    // Look for a public IPv6 address
                    // TODO: IPv6 address is preferred compared to IPv4, we could let the user select
                    //  his preferences to prioritize them.
                    if (nic.getIpv6AccessConfigs() != null) {
                        for (AccessConfig ac : nic.getIpv6AccessConfigs()) {
                            if (ac.getType().equals(NetworkInterfaceDualStack.IPV6_TYPE)) {
                                host = ac.getExternalIpv6();
                            }
                        }
                    }
                    // No public address found. Fall back to internal address
                    if (host.isEmpty()) {
                        host = nic.getNetworkIP();
                        logInfo(computer, listener, "No public address found. Fall back to internal address.");
                    }
                }

                int port = SSH_PORT;
                logInfo(
                        computer,
                        listener,
                        "Connecting to " + host + " on port " + port + ", with timeout " + SSH_TIMEOUT_MILLIS + ".");
                conn = new Connection(host, port);
                ProxyConfiguration proxyConfig = Jenkins.get().proxy;
                Proxy proxy = proxyConfig == null ? Proxy.NO_PROXY : proxyConfig.createProxy(host);
                if (!node.isIgnoreProxy()
                        && !proxy.equals(Proxy.NO_PROXY)
                        && proxy.address() instanceof InetSocketAddress) {
                    InetSocketAddress address = (InetSocketAddress) proxy.address();
                    HTTPProxyData proxyData = null;
                    if (proxyConfig.getUserName() != null && proxyConfig.getPassword() != null) {
                        proxyData = new HTTPProxyData(
                                address.getHostName(),
                                address.getPort(),
                                proxyConfig.getUserName(),
                                proxyConfig.getPassword());
                    } else {
                        proxyData = new HTTPProxyData(address.getHostName(), address.getPort());
                    }
                    conn.setProxyData(proxyData);
                    logInfo(computer, listener, "Using HTTP Proxy Configuration");
                }

                conn.connect(
                        (hostname, portNum, serverHostKeyAlgorithm, serverHostKey) -> verifyServerHostKey(
                                client, computer, listener, instance, serverHostKeyAlgorithm, serverHostKey),
                        SSH_TIMEOUT_MILLIS,
                        SSH_TIMEOUT_MILLIS);
                logInfo(computer, listener, "Connected via SSH.");
                return conn;
            } catch (GoogleJsonResponseException e) {
                if (e.getStatusCode() == 404) {
                    log(
                            LOGGER,
                            Level.SEVERE,
                            listener,
                            String.format("Instance %s not found. Terminating instance.", computer.getName()));
                    terminateNode(computer, listener);
                }
            } catch (SocketTimeoutException e) {
                // keep retrying until SSH comes up
                logInfo(computer, listener, String.format("Failed to connect via ssh: %s", e.getMessage()));
                logInfo(
                        computer,
                        listener,
                        String.format("Waiting for SSH to come up. Sleeping %d.", SSH_SLEEP_MILLIS / 1000));
                Thread.sleep(SSH_SLEEP_MILLIS);
            } catch (IOException e) {
                logWarning(computer, listener, String.format("An error occured: %s", e.getMessage()));
                Thread.sleep(SSH_SLEEP_MILLIS);
            }
        }
    }

    private boolean verifyServerHostKey(
            ComputeClient client,
            ComputeEngineComputer computer,
            TaskListener listener,
            Instance instance,
            String serverHostKeyAlgorithm,
            byte[] serverHostKey)
            throws IOException {
        Optional<InstanceResourceData> instanceResourceData =
                ClientUtil.parseInstanceResourceData(instance.getSelfLink());
        if (!instanceResourceData.isPresent()) {
            throw new IOException(String.format(
                    "Failed to retrieve instance resource data for instance: %s", instance.getSelfLink()));
        }

        ImmutableList<GuestAttribute> guestAttrList;
        try {
            guestAttrList = client.getGuestAttributesSync(
                    instanceResourceData.get().getProjectId(),
                    instanceResourceData.get().getZone(),
                    instanceResourceData.get().getName(),
                    Util.rawEncode(GUEST_ATTRIBUTE_HOST_KEY_NAMESPACE + "/"));
        } catch (IOException e) {
            logWarning(
                    computer,
                    listener,
                    String.format(
                            "Failed to verify server host key because no host key metadata was available: %s",
                            e.getMessage()));
            return true;
        }

        Optional<GuestAttribute> hostKeyAttr = guestAttrList.stream()
                .filter(attr -> attr.getNamespace().equals(GUEST_ATTRIBUTE_HOST_KEY_NAMESPACE)
                        && attr.getKey().equals(serverHostKeyAlgorithm.toLowerCase()))
                .findFirst();

        if (!hostKeyAttr.isPresent()) {
            logWarning(
                    computer,
                    listener,
                    String.format(
                            "Failed to verify server host key: host key guest attribute doesn't exist for instance: %s",
                            instance.getSelfLink()));
            return true;
        }

        if (!hostKeyAttr.get().getValue().equals(Base64.getEncoder().encodeToString(serverHostKey))) {
            logWarning(
                    computer,
                    listener,
                    String.format(
                            "Failed to verify server host key: server host key didn't match for instance: %s",
                            instance.getSelfLink()));
            return false;
        }

        return true;
    }
}
