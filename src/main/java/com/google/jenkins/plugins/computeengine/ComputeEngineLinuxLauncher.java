package com.google.jenkins.plugins.computeengine;

import com.google.api.services.compute.model.Operation;

public class ComputeEngineLinuxLauncher extends ComputeEngineComputerLauncher {
    public ComputeEngineLinuxLauncher(Operation insertOperation) {
        super(insertOperation);
    }
}
