/*
 * Copyright 2018 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.jenkins.plugins.computeengine;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHAuthenticator;
import com.google.api.services.compute.model.AccessConfig;
import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.NetworkInterface;
import com.google.api.services.compute.model.Operation;
import com.trilead.ssh2.Connection;
import com.trilead.ssh2.HTTPProxyData;
import com.trilead.ssh2.ServerHostKeyVerifier;
import hudson.ProxyConfiguration;
import hudson.model.TaskListener;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import lombok.Getter;

/**
 * Launcher for Windows agents
 *
 * <p>Launches Compute Engine Windows instances
 */
public class ComputeEngineWindowsLauncher extends ComputeEngineComputerLauncher {
  private static final Logger LOGGER =
      Logger.getLogger(ComputeEngineWindowsLauncher.class.getName());
  @Getter private final boolean useInternalAddress;

  // TODO: make this configurable
  public static final Integer SSH_PORT = 22;
  public static final Integer SSH_TIMEOUT = 10000;
  private static int bootstrapAuthTries = 30;
  private static int bootstrapAuthSleepMs = 15000;

  public ComputeEngineWindowsLauncher(
      String cloudName, Operation insertOperation, boolean useInternalAddress) {
    super(cloudName, insertOperation.getName(), insertOperation.getZone());
    this.useInternalAddress = useInternalAddress;
  }

  protected Logger getLogger() {
    return LOGGER;
  }

  @Override
  protected Optional<Connection> setupConnection(
      ComputeEngineInstance node, ComputeEngineComputer computer, TaskListener listener)
      throws Exception {
    if (!node.getWindowsConfig().isPresent()) {
      logWarning(computer, listener, "Non-windows node provided");
      return Optional.empty();
    }
    boolean isBootstrapped = bootstrap(computer, listener);
    if (!isBootstrapped) {
      logWarning(computer, listener, "bootstrapresult failed");
      return Optional.empty();
    }

    // connect fresh as ROOT
    logInfo(computer, listener, "connect fresh as root");
    Connection cleanupConn = connectToSsh(computer, listener);
    if (!authenticateSSH(node.getSshUser(), node.getWindowsConfig().get(), cleanupConn, listener)) {
      logWarning(computer, listener, "Authentication failed");
      return Optional.empty(); // failed to connect
    }

    return Optional.of(cleanupConn);
  }

  private boolean authenticateSSH(
      String windowsUsername,
      WindowsConfiguration windowsConfig,
      Connection sshConnection,
      TaskListener listener)
      throws Exception {
    boolean isAuthenticated;
    if (!windowsConfig.getPrivateKeyCredentialsId().isEmpty()) {
      isAuthenticated =
          SSHAuthenticator.newInstance(
                  sshConnection, windowsConfig.getPrivateKeyCredentials(), windowsUsername)
              .authenticate(listener);
    } else {
      isAuthenticated =
          sshConnection.authenticateWithPassword(windowsUsername, windowsConfig.getPassword());
    }
    return isAuthenticated;
  }

  private boolean bootstrap(ComputeEngineComputer computer, TaskListener listener)
      throws IOException, Exception { // TODO(evanbrown): better exceptions
    if (computer == null) {
      throw new IllegalArgumentException("A null ComputeEngineComputer was provided");
    }
    logInfo(computer, listener, "bootstrap");

    ComputeEngineInstance node = computer.getNode();
    if (node == null) {
      throw new IllegalArgumentException("A ComputeEngineComputer with no node was provided");
    } else if (!node.getWindowsConfig().isPresent()) {
      throw new IllegalArgumentException("A non-windows ComputeEngineComputer was provided.");
    }
    WindowsConfiguration windowsConfig = node.getWindowsConfig().get();
    Connection bootstrapConn = null;
    try {
      int tries = bootstrapAuthTries;
      boolean isAuthenticated = false;
      while (tries-- > 0) {
        logInfo(computer, listener, "Authenticating as " + node.getSshUser());
        try {
          bootstrapConn = connectToSsh(computer, listener);
          isAuthenticated =
              authenticateSSH(node.getSshUser(), windowsConfig, bootstrapConn, listener);
        } catch (IOException e) {
          logException(computer, listener, "Exception trying to authenticate", e);
          if (bootstrapConn != null) {
            bootstrapConn.close();
          }
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

  private Connection connectToSsh(ComputeEngineComputer computer, TaskListener listener)
      throws Exception {
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
          // TODO: better exception
          throw new Exception(
              "Timed out after "
                  + (waitTime / 1000)
                  + " seconds of waiting for ssh to become available. (maximum timeout configured is "
                  + (timeout / 1000)
                  + ")");
        }
        Instance instance = computer.refreshInstance();

        String host = "";

        // TODO: handle multiple NICs
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
        logInfo(
            computer,
            listener,
            "Connecting to " + host + " on port " + port + ", with timeout " + SSH_TIMEOUT + ".");
        Connection conn = new Connection(host, port);
        ProxyConfiguration proxyConfig = Jenkins.get().proxy;
        Proxy proxy = proxyConfig == null ? Proxy.NO_PROXY : proxyConfig.createProxy(host);
        if (!proxy.equals(Proxy.NO_PROXY) && proxy.address() instanceof InetSocketAddress) {
          InetSocketAddress address = (InetSocketAddress) proxy.address();
          HTTPProxyData proxyData = null;
          if (proxyConfig.getUserName() != null) {
            proxyData =
                new HTTPProxyData(
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
        // TODO: verify host key
        conn.connect(
            new ServerHostKeyVerifier() {
              public boolean verifyServerHostKey(
                  String hostname, int port, String serverHostKeyAlgorithm, byte[] serverHostKey)
                  throws Exception {
                return true;
              }
            },
            SSH_TIMEOUT,
            SSH_TIMEOUT);
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

  @Override
  protected String getPathSeparator() {
    return "\\";
  }
}
