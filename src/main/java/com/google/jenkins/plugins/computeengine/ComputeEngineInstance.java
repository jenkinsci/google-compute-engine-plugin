package com.google.jenkins.plugins.computeengine;

import com.google.api.services.compute.model.Operation;
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
import java.util.Collections;
import java.util.List;

public class ComputeEngineInstance extends AbstractCloudSlave {
  public Integer launchTimeout;

  public ComputeEngineInstance(String name,
                               String nodeDescription,
                               String remoteFS,
                               int numExecutors,
                               Node.Mode mode,
                               String labelString,
                               ComputerLauncher launcher,
                               RetentionStrategy retentionStrategy,
                               Integer launchTimeout)
      throws Descriptor.FormException,
      IOException {
    super(name, nodeDescription, remoteFS, numExecutors, mode, labelString, launcher, retentionStrategy, Collections.<NodeProperty<?>> emptyList());
    this.launchTimeout = launchTimeout;
  }

  @Override
  public AbstractCloudComputer createComputer() {
    return new ComputeEngineComputer(this);
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
