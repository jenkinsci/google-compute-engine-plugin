/*
 * Copyright 2018 Google LLC
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

import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.Operation;
import com.trilead.ssh2.Connection;
import com.trilead.ssh2.SCPClient;
import hudson.model.TaskListener;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.SlaveComputer;
import java.io.IOException;
import java.io.PrintStream;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import jenkins.model.Jenkins;

public abstract class ComputeEngineComputerLauncher extends ComputerLauncher {
  private static final Logger LOGGER =
      Logger.getLogger(ComputeEngineComputerLauncher.class.getName());
  private static final SimpleFormatter sf = new SimpleFormatter();
  private static final String AGENT_JAR = "agent.jar";

  private final String insertOperationId;
  private final String zone;
  private final String cloudName;
  protected String pathSeparator = "/";

  public ComputeEngineComputerLauncher(String cloudName, String insertOperationId, String zone) {
    super();
    this.cloudName = cloudName;
    this.insertOperationId = insertOperationId;
    this.zone = zone;
  }

  public static void log(Logger logger, Level level, TaskListener listener, String message) {
    log(logger, level, listener, message, null);
  }

  public static void log(
      Logger logger, Level level, TaskListener listener, String message, Throwable exception) {
    logger.log(level, message, exception);
    if (listener != null) {
      if (exception != null) message += " Exception: " + exception;
      LogRecord lr = new LogRecord(level, message);
      PrintStream printStream = listener.getLogger();
      printStream.print(sf.format(lr));
    }
  }

  protected void log(
      Level level, ComputeEngineComputer computer, TaskListener listener, String message) {
    try {
      ComputeEngineCloud cloud = computer.getCloud();
      cloud.log(LOGGER, level, listener, message);
    } catch (CloudNotFoundException cnfe) {
      log(LOGGER, Level.SEVERE, listener, "FATAL: Could not get cloud");
    }
  }

  protected void logException(
      ComputeEngineComputer computer, TaskListener listener, String message, Throwable exception) {
    try {
      ComputeEngineCloud cloud = computer.getCloud();
      cloud.log(LOGGER, Level.WARNING, listener, message, exception);
    } catch (CloudNotFoundException cnfe) {
      log(LOGGER, Level.SEVERE, listener, "FATAL: Could not get cloud");
    }
  }

  protected void logInfo(ComputeEngineComputer computer, TaskListener listener, String message) {
    log(Level.INFO, computer, listener, message);
  }

  protected void logWarning(ComputeEngineComputer computer, TaskListener listener, String message) {
    log(Level.WARNING, computer, listener, message);
  }

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
      LOGGER.info(
          String.format(
              "Launch will wait %d for operation %s to complete...",
              node.launchTimeout, insertOperationId));
      // This call will a null error when the operation is complete, or a relevant error if it
      // fails.
      opError =
          cloud
              .getClient()
              .waitForOperationCompletion(
                  cloud.projectId, insertOperationId, zone, node.getLaunchTimeoutMillis());
      if (opError != null) {
        LOGGER.info(
            String.format(
                "Launch failed while waiting for operation %s to complete. Operation error was %s",
                insertOperationId, opError.getErrors().get(0).getMessage()));
        return;
      }
    } catch (IOException ioe) {
      LOGGER.info(
          String.format(
              "Launch failed while waiting for operation %s to complete. Operation error was %s",
              insertOperationId, opError.getErrors().get(0).getMessage()));
      return;
    } catch (InterruptedException ie) {
      LOGGER.info(
          String.format(
              "Launch failed while waiting for operation %s to complete. Operation error was %s",
              insertOperationId, opError.getErrors().get(0).getMessage()));
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
          case "TERMINATED":
            cloud.log(
                LOGGER,
                Level.FINEST,
                listener,
                String.format("Instance %s is being shut down...", computer.getName()));
            return;
        }
        Thread.sleep(5000);
      }

      // Initiate the next launch phase. This is likely an SSH-based process for Linux hosts.
      launch(computer, listener, computer.refreshInstance());
    } catch (IOException ioe) {
      ioe.printStackTrace(listener.error(ioe.getMessage()));
      node = (ComputeEngineInstance) slaveComputer.getNode();
      if (node != null) {
        try {
          node.terminate();
        } catch (Exception e) {
          listener.error(String.format("Failed to terminate node %s", node.getDisplayName()));
        }
      }
    } catch (InterruptedException ie) {

    }
  }

  protected String prepareJavaLaunchString(
      ComputeEngineInstance node,
      ComputeEngineComputer computer,
      Connection conn,
      PrintStream logger,
      TaskListener listener)
      throws IOException, InterruptedException {
    String javaExecPath = node.getJavaExecPathOrDefault();
    if (!testCommand(
        computer, conn, String.format("%s -fullversion", javaExecPath), logger, listener)) {
      logWarning(computer, listener, String.format("Java is not installed at %s", javaExecPath));
    }

    SCPClient scp = conn.createSCPClient();
    String jenkinsDir = node.getRemoteFS();
    logInfo(computer, listener, "Copying agent.jar to: " + jenkinsDir);
    scp.put(Jenkins.get().getJnlpJars(AGENT_JAR).readFully(), AGENT_JAR, jenkinsDir);

    // TODO: allow jvmopt configuration
    return String.format("%s -jar ", javaExecPath) + jenkinsDir + pathSeparator + AGENT_JAR;
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

  protected abstract void launch(
      ComputeEngineComputer computer, TaskListener listener, Instance inst)
      throws IOException, InterruptedException;
}
