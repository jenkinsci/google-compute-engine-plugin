package com.google.jenkins.plugins.computeengine;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import hudson.slaves.AbstractCloudComputer;
import hudson.slaves.AbstractCloudSlave;
import hudson.model.Node;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.NodeProperty;
import hudson.slaves.RetentionStrategy;
import java.io.IOException;
import java.util.List;

public class ComputeEngineAgent extends AbstractCloudSlave {

  public ComputeEngineAgent(String name,
      String nodeDescription,
      String remoteFS,
      int numExecutors,
      Node.Mode mode,
      String labelString,
      ComputerLauncher launcher,
      RetentionStrategy retentionStrategy,
      List<? extends NodeProperty<?>> nodeProperties)
      throws Descriptor.FormException,
      IOException {
    super(name, nodeDescription, remoteFS, numExecutors, mode, labelString, launcher, retentionStrategy, nodeProperties);
  }

  @Override
  public AbstractCloudComputer createComputer() {
    return null;
  }

  @Override
  protected void _terminate(TaskListener listener) throws IOException, InterruptedException {

  }

  @Extension
  public static final class DescriptorImpl extends SlaveDescriptor {
    @Override
    public String getDisplayName() {
      return Messages.ComputeEngineAgent_DisplayName();
    }
  }
}
