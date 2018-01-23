package com.google.jenkins.plugins.computeengine;

import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.Operation;
import com.google.jenkins.plugins.computeengine.client.ComputeClient;
import hudson.model.TaskListener;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.SlaveComputer;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class ComputeEngineComputerLauncher extends ComputerLauncher {
    private static final Logger LOGGER = Logger.getLogger(ComputeEngineComputerLauncher.class.getName());
    private final Operation insertOperation;
    private final String cloudName;

    public ComputeEngineComputerLauncher(String cloudName, Operation insertOperation) {
        super();
        this.cloudName = cloudName;
        this.insertOperation = insertOperation;
    }

    @Override
    public void launch(SlaveComputer slaveComputer, TaskListener listener) {
        ComputeEngineComputer computer = (ComputeEngineComputer) slaveComputer;
        ComputeEngineCloud cloud = computer.getCloud();
        ComputeClient client = cloud.client;
        if (cloud == null || client == null) {
            LOGGER.warning(String.format("Could not get compute client from cloud {0}", cloud.getCloudName()));
            return;
        }

        // Wait until the Operation from the Instance insert is complete or fails
        Operation.Error opError = new Operation.Error();
        try {
            LOGGER.info(String.format("Launch will wait {0}s for operation {1} to complete...", computer.getNode().launchTimeout / 1000, insertOperation.getId()));
            // This call will a null error when the operation is complete, or a relevant error if it fails.
            opError = cloud.client.waitForOperationCompletion(cloud.projectId, insertOperation, computer.getNode().launchTimeout);
            if (opError != null) {
                LOGGER.info(String.format("Launch failed while waiting for operation {0} to complete. Operation error was {1}", insertOperation.getId(), opError.getErrors().get(0).getMessage()));
                return;
            }
        } catch (Exception e) {
            LOGGER.info(String.format("Launch failed while waiting for operation {0} to complete. Operation error was {1}", insertOperation.getId(), opError.getErrors().get(0).getMessage()));
            return;
        }

        try {
            // The operation succeeded. Now wait for the Instance status to be RUNNING
            OUTER:
            while (true) {
                switch (computer.getInstanceStatus()) {
                    case "PROVISIONING":
                    case "STAGING":
                        cloud.log(LOGGER, Level.FINEST, listener, String.format("Instance {0} is being prepared...", computer.getName()));
                        break;
                    case "RUNNING":
                        cloud.log(LOGGER, Level.FINEST, listener, String.format("Instance {0} is running and ready...", computer.getName()));
                        break OUTER;
                    case "STOPPING":
                    case "SUSPENDING":
                        cloud.log(LOGGER, Level.FINEST, listener, String.format("Instance {0} is being shut down...", computer.getName()));
                        break;
                    //TODO: Although the plugin doesn't put instances in the STOPPED or SUSPENDED states, it should handle them if they are placed in that state out-of-band.
                    case "STOPPED":
                    case "SUSPENDED":
                        cloud.log(LOGGER, Level.FINEST, listener, String.format("Instance {0} was unexpectedtly stopped or suspended...", computer.getName()));
                        return;
                    case "TERMINATED":
                        cloud.log(LOGGER, Level.FINEST, listener, String.format("Instance {0} is being shut down...", computer.getName()));
                        return;
                }
                Thread.sleep(5000);
            }

            // Initiate the next launch phase. This is likely an SSH-based process for Linux hosts.
            launch(computer, listener, null);
        } catch (IOException ioe) {
           ioe.printStackTrace(listener.error(ioe.getMessage()));
           ComputeEngineInstance node = (ComputeEngineInstance)slaveComputer.getNode();
           if(node != null) {
               try {
                   node.terminate();
               } catch(Exception e) {
                    listener.error(String.format("Failed to terminate node {0}", node.getDisplayName()));
               }
           }
        } catch (InterruptedException ie) {

        }

    }

    protected abstract void launch(ComputeEngineComputer computer, TaskListener listener, Instance inst)
            throws IOException, InterruptedException;
}
