package com.google.jenkins.plugins.computeengine;

import hudson.slaves.CommandLauncher;

public class ComputeEngineCommandLauncher extends CommandLauncher {
  public ComputeEngineCommandLauncher(String command) {
    super(command);
  }
}
