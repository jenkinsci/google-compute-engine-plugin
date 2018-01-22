package com.google.jenkins.plugins.computeengine;

import com.google.api.services.compute.model.Operation;
import hudson.model.TaskListener;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.SlaveComputer;

public class ComputeEngineComputerLauncher extends ComputerLauncher {
    private final Operation insertOperation;
    public ComputeEngineComputerLauncher(Operation insertOperation) {
        super();
        this.insertOperation = insertOperation;
    }

    @Override
    public void launch(SlaveComputer slaveComputer, TaskListener listener) {
        try {
            ComputeEngineComputer computer = (ComputeEngineComputer)slaveComputer;
        }catch (Exception e) {
        }
    }
}
