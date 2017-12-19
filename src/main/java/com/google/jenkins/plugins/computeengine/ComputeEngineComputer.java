package com.google.jenkins.plugins.computeengine;

import hudson.slaves.AbstractCloudComputer;

public class ComputeEngineComputer extends AbstractCloudComputer<ComputeEngineAgent> {

  public ComputeEngineComputer(ComputeEngineAgent slave) {
    super(slave);
  }
}
