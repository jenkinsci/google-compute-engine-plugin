package com.google.jenkins.plugins.computeengine;

import hudson.model.TaskListener;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.SlaveComputer;

public class ComputeEngineComputerLauncher extends ComputerLauncher {

    @Override
    public void launch(SlaveComputer slaveComputer, TaskListener listener) {
        try {
            ComputeEngineComputer computer = (ComputeEngineComputer)slaveComputer;
        }catch (Exception e) {
        }
    }
}
