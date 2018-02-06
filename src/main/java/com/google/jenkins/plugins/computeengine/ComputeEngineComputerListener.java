package com.google.jenkins.plugins.computeengine;

import hudson.Extension;
import hudson.model.Computer;
import hudson.model.TaskListener;
import hudson.slaves.ComputerListener;

@Extension
public class ComputeEngineComputerListener extends ComputerListener {
    @Override
    public void onOnline(Computer c, TaskListener listener) {
        if (c instanceof ComputeEngineComputer) {
            ((ComputeEngineComputer) c).onConnected();
        }
    }
}
