/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.google.jenkins.plugins.computeengine;

import com.google.api.services.compute.model.AccessConfig;
import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.Metadata;
import com.google.api.services.compute.model.NetworkInterface;
import com.google.api.services.compute.model.Operation;
import com.google.jenkins.plugins.computeengine.client.ComputeClient;
import com.google.jenkins.plugins.computeengine.ssh.GoogleKeyPair;
import com.trilead.ssh2.Connection;
import com.trilead.ssh2.HTTPProxyData;
import com.trilead.ssh2.SCPClient;
import com.trilead.ssh2.ServerHostKeyVerifier;
import com.trilead.ssh2.Session;
import hudson.ProxyConfiguration;
import hudson.model.TaskListener;
import hudson.remoting.Channel;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;

public class ComputeEngineWindowsLauncher extends ComputeEngineComputerLauncher {
    public final boolean useInternalAddress;

    //TODO: make this configurable
    public static final Integer SSH_PORT = 22;
    public static final Integer SSH_TIMEOUT = 10000;
    private static final Logger LOGGER = Logger.getLogger(ComputeEngineLinuxLauncher.class.getName());
    private static int bootstrapAuthTries = 30;
    private static int bootstrapAuthSleepMs = 15000;

    public ComputeEngineWindowsLauncher(String cloudName, Operation insertOperation, boolean useInternalAddress) {
        super(cloudName, insertOperation.getName(), insertOperation.getZone());
        this.useInternalAddress = useInternalAddress;
    }

    protected void log(Level level, ComputeEngineComputer computer, TaskListener listener, String message) {
        try {
            ComputeEngineCloud cloud = computer.getCloud();
            cloud.log(LOGGER, level, listener, message);
        } catch (CloudNotFoundException cnfe) {
            //TODO: figure out how to log without a handle to the cloud
        }
    }

    protected void logException(ComputeEngineComputer computer, TaskListener listener, String message, Throwable exception) {
        try {
            ComputeEngineCloud cloud = computer.getCloud();
            cloud.log(LOGGER, Level.WARNING, listener, message, exception);
        } catch (CloudNotFoundException cnfe) {
            //TODO: figure out how to log without a handle to the cloud
        }
    }

    protected void logInfo(ComputeEngineComputer computer, TaskListener listener, String message) {
        log(Level.INFO, computer, listener, message);
    }

    protected void logWarning(ComputeEngineComputer computer, TaskListener listener, String message) {
        log(Level.WARNING, computer, listener, message);
    }

    protected void launch(ComputeEngineComputer computer, TaskListener listener, Instance inst)
            throws IOException, InterruptedException {
        ComputeEngineInstance node = computer.getNode();
        if (node == null) {
            logWarning(computer, listener, "Could not get node from computer");
            return;
        }

        final Connection bootstrapConn;
        final Connection conn;
        Connection cleanupConn = null; // java's code path analysis for final
        // doesn't work that well.
        boolean successful = false;
        PrintStream logger = listener.getLogger();
        logInfo(computer, listener, "Launching instance: " + node.getNodeName());
        try {
            boolean isBootstrapped = bootstrap(computer, listener);
            if (isBootstrapped) {
                // connect fresh as ROOT
                logInfo(computer, listener, "connect fresh as root");
                cleanupConn = connectToSsh(computer, listener);
                if (!cleanupConn.authenticateWithPassword(node.windowsUsername, node.windowsPassword)) {
                    logWarning(computer, listener, "Authentication failed");
                    return; // failed to connect
                }
            } else {
                logWarning(computer, listener, "bootstrapresult failed");
                return;
            }
            conn = cleanupConn;

            SCPClient scp = conn.createSCPClient();
            String tmpDir = "C:\\";
            logInfo(computer, listener, "Copying slave.jar to: " + tmpDir);
            scp.put(Jenkins.getInstance().getJnlpJars("slave.jar").readFully(), "slave.jar", tmpDir);

            // Confirm Java is installed
            if (!testCommand(computer, conn, "java -fullversion", logger, listener)) {
                logWarning(computer, listener, "Java is not installed.");
            }


            //TODO: allow jvmopt configuration
            String launchString = "java -jar C:\\slave.jar";

            logInfo(computer, listener, "Launching Jenkins agent via plugin SSH: " + launchString);
            final Session sess = conn.openSession();
            sess.execCommand(launchString);
            computer.setChannel(sess.getStdout(), sess.getStdin(), logger, new Channel.Listener() {
                @Override
                public void onClosed(Channel channel, IOException cause) {
                    sess.close();
                    conn.close();
                }
            });
        } catch (Exception e) {
            logException(computer, listener, "Error getting exception", e);
        }
    }

    private boolean testCommand(ComputeEngineComputer computer, Connection conn, String checkCommand, PrintStream logger, TaskListener listener)
            throws IOException, InterruptedException {
        logInfo(computer, listener, "Verifying: " + checkCommand);
        return conn.exec(checkCommand, logger) == 0;

    }

    private boolean bootstrap(ComputeEngineComputer computer, TaskListener listener) throws IOException,
            Exception { //TODO: better exceptions
        logInfo(computer, listener, "bootstrap");
        ComputeEngineInstance node = computer.getNode();
        if (node == null) {
            throw new IllegalArgumentException("A ComputeEngineComputer with no node was provided");
        }
        Connection bootstrapConn = null;
        try {
            int tries = bootstrapAuthTries;
            boolean isAuthenticated = false;
            logInfo(computer, listener, "Getting keypair...");
            logInfo(computer, listener, "Using autogenerated keypair");
            while (tries-- > 0) {
                logInfo(computer, listener, "Authenticating as " + node.windowsUsername);
                try {
                    bootstrapConn = connectToSsh(computer, listener);
                    isAuthenticated = bootstrapConn.authenticateWithPassword(node.windowsUsername, node.windowsPassword);
                } catch (IOException e) {
                    logException(computer, listener, "Exception trying to authenticate", e);
                    bootstrapConn.close();
                }
                if (isAuthenticated) {
                    break;
                }
                logWarning(computer, listener, "Authentication failed. Trying again...");
                Thread.sleep(bootstrapAuthSleepMs);
            }
            if (!isAuthenticated) {
                logWarning(computer, listener, "Authentication failed");
                return false;
            }
        } finally {
            if (bootstrapConn != null) {
                bootstrapConn.close();
            }
        }
        return true;
    }

    private Connection connectToSsh(ComputeEngineComputer computer, TaskListener listener) throws Exception {
        ComputeEngineInstance node = computer.getNode();
        if (node == null) {
            throw new IllegalArgumentException("A ComputeEngineComputer with no node was provided");
        }

        final long timeout = node.getLaunchTimeoutMillis();
        final long startTime = System.currentTimeMillis();
        while (true) {
            try {
                long waitTime = System.currentTimeMillis() - startTime;
                if (timeout > 0 && waitTime > timeout) {
                    //TODO: better exception
                    throw new Exception("Timed out after " + (waitTime / 1000)
                            + " seconds of waiting for ssh to become available. (maximum timeout configured is "
                            + (timeout / 1000) + ")");
                }
                Instance instance = computer.refreshInstance();

                String host = "";

                //TODO: handle multiple NICs
                NetworkInterface nic = instance.getNetworkInterfaces().get(0);

                if (this.useInternalAddress) {
                    host = nic.getNetworkIP();
                } else {
                    // Look for a public IP address
                    if (nic.getAccessConfigs() != null) {
                        for (AccessConfig ac : nic.getAccessConfigs()) {
                            if (ac.getType().equals(InstanceConfiguration.NAT_TYPE)) {
                                host = ac.getNatIP();
                            }
                        }
                    }
                    // No public address found. Fall back to internal address
                    if (host.isEmpty()) {
                        host = nic.getNetworkIP();
                    }
                }

                int port = SSH_PORT;
                logInfo(computer, listener, "Connecting to " + host + " on port " + port + ", with timeout " + SSH_TIMEOUT
                        + ".");
                Connection conn = new Connection(host, port);
                ProxyConfiguration proxyConfig = Jenkins.getInstance().proxy;
                Proxy proxy = proxyConfig == null ? Proxy.NO_PROXY : proxyConfig.createProxy(host);
                if (!proxy.equals(Proxy.NO_PROXY) && proxy.address() instanceof InetSocketAddress) {
                    InetSocketAddress address = (InetSocketAddress) proxy.address();
                    HTTPProxyData proxyData = null;
                    if (null != proxyConfig.getUserName()) {
                        proxyData = new HTTPProxyData(address.getHostName(), address.getPort(), proxyConfig.getUserName(), proxyConfig.getPassword());
                    } else {
                        proxyData = new HTTPProxyData(address.getHostName(), address.getPort());
                    }
                    conn.setProxyData(proxyData);
                    logInfo(computer, listener, "Using HTTP Proxy Configuration");
                }
                //TODO: verify host key
                conn.connect(new ServerHostKeyVerifier() {
                    public boolean verifyServerHostKey(String hostname, int port, String serverHostKeyAlgorithm, byte[] serverHostKey)
                            throws Exception {
                        return true;
                    }
                }, SSH_TIMEOUT, SSH_TIMEOUT);
                logInfo(computer, listener, "Connected via SSH.");
                return conn;
            } catch (IOException e) {
                // keep retrying until SSH comes up
                logInfo(computer, listener, "Failed to connect via ssh: " + e.getMessage());
                logInfo(computer, listener, "Waiting for SSH to come up. Sleeping 5.");
                Thread.sleep(5000);
            }
        }
    }
}
