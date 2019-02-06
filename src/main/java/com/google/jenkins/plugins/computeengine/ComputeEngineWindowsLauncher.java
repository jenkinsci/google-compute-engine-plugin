/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.google.jenkins.plugins.computeengine;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHAuthenticator;
import com.google.api.services.compute.model.AccessConfig;
import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.NetworkInterface;
import com.google.api.services.compute.model.Operation;
import com.trilead.ssh2.Connection;
import com.trilead.ssh2.HTTPProxyData;
import com.trilead.ssh2.SCPClient;
import com.trilead.ssh2.ServerHostKeyVerifier;
import com.trilead.ssh2.Session;
import hudson.ProxyConfiguration;
import hudson.model.TaskListener;
import hudson.remoting.Channel;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Launcher for Windows agents
 *
 * Launches Compute Engine Windows instances
 *
 */
public class ComputeEngineWindowsLauncher extends ComputeEngineComputerLauncher {
    public final boolean useInternalAddress;

    //TODO: make this configurable
    public static final Integer SSH_PORT = 22;
    public static final Integer SSH_TIMEOUT = 10000;
    private static final Logger LOGGER = Logger.getLogger(ComputeEngineLinuxLauncher.class.getName());
    private static final String TMPDIR = "C:\\";
    //TODO: allow jvmopt configuration
    private static final String LAUNCHSTRING = "java -jar C:\\slave.jar";
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
            ComputeEngineComputerLauncher.log(LOGGER, Level.SEVERE, listener, "FATAL: Could not get cloud");
        }
    }

    protected void logException(ComputeEngineComputer computer, TaskListener listener, String message, Throwable exception) {
        try {
            ComputeEngineCloud cloud = computer.getCloud();
            cloud.log(LOGGER, Level.WARNING, listener, message, exception);
        } catch (CloudNotFoundException cnfe) {
            ComputeEngineComputerLauncher.log(LOGGER, Level.SEVERE, listener, "FATAL: Could not get cloud");
        }
    }

    protected void logInfo(ComputeEngineComputer computer, TaskListener listener, String message) {
        log(Level.INFO, computer, listener, message);
    }

    protected void logWarning(ComputeEngineComputer computer, TaskListener listener, String message) {
        log(Level.WARNING, computer, listener, message);
    }

    @Override
    protected void launch(ComputeEngineComputer computer, TaskListener listener, Instance inst)
            throws IOException, InterruptedException {
        ComputeEngineInstance node = computer.getNode();
        if (node == null) {
            log(Level.SEVERE, computer, listener, "Could not get node from computer");
            return;
        }

        final Connection bootstrapConn;
        final Connection conn;
        Connection cleanupConn = null; /** java's code path analysis for final
        doesn't work that well.
         **/
        boolean successful = false;
        PrintStream logger = listener.getLogger();
        logInfo(computer, listener, "Launching instance: " + node.getNodeName());
        try {
            boolean isBootstrapped = bootstrap(computer, listener);
            if (!isBootstrapped) {
                logWarning(computer, listener, "bootstrapresult failed");
                return;
            }

            // connect fresh as ROOT
            logInfo(computer, listener, "connect fresh as root");
            cleanupConn = connectToSsh(computer, listener);
            if (!authenticateSSH(node.windowsConfig.get(), cleanupConn, listener)) {
                logWarning(computer, listener, "Authentication failed");
                return; // failed to connect
            }

            conn = cleanupConn;

            SCPClient scp = conn.createSCPClient();

            logInfo(computer, listener, "Copying slave.jar to: " + TMPDIR);
            scp.put(Jenkins.getInstance().getJnlpJars("slave.jar").readFully(), "slave.jar", TMPDIR);

            // Confirm Java is installed
            if (!testCommand(computer, conn, "java -fullversion", logger, listener)) {
                logWarning(computer, listener, "Java is not installed.");
                return;
            }

            logInfo(computer, listener, "Launching Jenkins agent via plugin SSH: " + LAUNCHSTRING);
            final Session sess = conn.openSession();
            sess.execCommand(LAUNCHSTRING);
            computer.setChannel(sess.getStdout(), sess.getStdin(), logger, new Channel.Listener() {
                @Override
                public void onClosed(Channel channel, IOException cause) {
                    sess.close();
                    conn.close();
                }
            });
        } catch (Exception e) {
            logException(computer, listener, "Error: ", e);
        }
    }

    private boolean testCommand(ComputeEngineComputer computer, Connection conn, String checkCommand, PrintStream logger, TaskListener listener)
            throws IOException, InterruptedException {
        logInfo(computer, listener, "Verifying: " + checkCommand);
        return conn.exec(checkCommand, logger) == 0;

    }

    private boolean authenticateSSH(WindowsConfiguration windowsConfig, Connection sshConnection,
                                    TaskListener listener) throws Exception{
        boolean isAuthenticated;
        String windowsUsername = windowsConfig.getWindowsUsername();
        if (windowsConfig.getPrivateKeyCredentialsId().isPresent()) {
            isAuthenticated = SSHAuthenticator.newInstance(sshConnection, windowsConfig.getPrivateKeyCredentials(),
                    windowsUsername).authenticate(listener);
        } else {
            isAuthenticated = sshConnection.authenticateWithPassword(windowsUsername, windowsConfig.getPassword());
        }
        return isAuthenticated;
    }

    private boolean bootstrap(ComputeEngineComputer computer, TaskListener listener) throws IOException,
            Exception { //TODO(evanbrown): better exceptions
        if (computer == null) {
            throw new IllegalArgumentException("A null ComputeEngineComputer was provided");
        }
        logInfo(computer, listener, "bootstrap");

        ComputeEngineInstance node = computer.getNode();
        if (node == null) {
            throw new IllegalArgumentException("A ComputeEngineComputer with no node was provided");
        }
        WindowsConfiguration windowsConfig = node.windowsConfig.get();

        Connection bootstrapConn = null;
        try {
            int tries = bootstrapAuthTries;
            boolean isAuthenticated = false;
            while (tries-- > 0) {
                logInfo(computer, listener, "Authenticating as " + windowsConfig.getWindowsUsername());
                try {
                    bootstrapConn = connectToSsh(computer, listener);
                    isAuthenticated = authenticateSSH(windowsConfig, bootstrapConn, listener);
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
                    if (proxyConfig.getUserName() != null) {
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
