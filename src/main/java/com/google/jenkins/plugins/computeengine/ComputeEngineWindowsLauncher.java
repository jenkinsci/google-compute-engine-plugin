/*
 * Copyright 2020 Google LLC
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
import com.google.api.services.compute.model.Operation;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.trilead.ssh2.Connection;
import hudson.model.TaskListener;
import java.io.IOException;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Launcher for Windows agents
 *
 * <p>Launches Compute Engine Windows instances
 */
public class ComputeEngineWindowsLauncher extends ComputeEngineComputerLauncher {
  private static final Logger LOGGER =
      Logger.getLogger(ComputeEngineWindowsLauncher.class.getName());

  private static int bootstrapAuthTries = 30;
  private static int bootstrapAuthSleepMs = 15000;

  public ComputeEngineWindowsLauncher(
      String cloudName, Operation insertOperation, boolean useInternalAddress) {
    super(cloudName, insertOperation.getName(), insertOperation.getZone(), useInternalAddress);
  }

  protected Logger getLogger() {
    return LOGGER;
  }

  @Override
  protected Optional<Connection> setupConnection(
      ComputeEngineInstance node, ComputeEngineComputer computer, TaskListener listener)
      throws Exception {
    if (node.getWindowsConfig() == null) {
      logWarning(computer, listener, "Non-windows node provided");
      return Optional.empty();
    }
    Optional<Connection> bootstrapConn = bootstrap(computer, listener);
    if (!bootstrapConn.isPresent()) {
      logWarning(computer, listener, "bootstrapresult failed");
      return Optional.empty();
    }
    return bootstrapConn;
  }

  @VisibleForTesting
  public static boolean authenticateSSH(
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

  private Optional<Connection> bootstrap(ComputeEngineComputer computer, TaskListener listener) {
    Preconditions.checkNotNull(computer, "A null ComputeEngineComputer was provided");
    logInfo(computer, listener, "bootstrap");

    ComputeEngineInstance node = computer.getNode();
    if (node == null) {
      throw new IllegalArgumentException("A ComputeEngineComputer with no node was provided");
    } else if (node.getWindowsConfig() == null) {
      throw new IllegalArgumentException("A non-windows ComputeEngineComputer was provided.");
    }
    WindowsConfiguration windowsConfig = node.getWindowsConfig();
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
        return Optional.empty();
      }
    } catch (Exception e) {
      logException(computer, listener, "Failed to authenticate with exception: ", e);
      if (bootstrapConn != null) {
        bootstrapConn.close();
      }
      return Optional.empty();
    }
    return Optional.ofNullable(bootstrapConn);
  }

  @Override
  protected String getPathSeparator() {
    return "\\";
  }
}
