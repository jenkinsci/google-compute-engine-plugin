package com.google.jenkins.plugins.computeengine;

import hudson.Extension;
import hudson.model.Label;
import hudson.slaves.NodeProvisioner.PlannedNode;
import hudson.slaves.Cloud;
import hudson.model.Descriptor;
import javax.annotation.Nonnull;
import java.util.Collection;

public class ComputeEngineCloud extends Cloud {

  public ComputeEngineCloud(String name) {
    super(name);
  }

  @Override
  public Collection<PlannedNode> provision(Label label, int excessWorkload) {
    return null;
  }

  @Override
  public boolean canProvision(Label label) {
    return true;
  }

  @Extension
  public static class GoogleCloudDescriptor extends Descriptor<Cloud> {
    @Nonnull
    @Override
    public String getDisplayName() {
      return Messages.ComputeEngineCloud_DisplayName();
    }
  }
}
