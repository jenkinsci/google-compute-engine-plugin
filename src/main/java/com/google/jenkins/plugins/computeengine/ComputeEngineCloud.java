package com.google.jenkins.plugins.computeengine;

import hudson.Extension;
import hudson.model.Label;
import hudson.slaves.AbstractCloudImpl;
import hudson.slaves.NodeProvisioner.PlannedNode;
import hudson.slaves.Cloud;
import hudson.model.Descriptor;
import javax.annotation.Nonnull;
import java.util.Collection;
import org.kohsuke.stapler.DataBoundConstructor;

public class ComputeEngineCloud extends AbstractCloudImpl {



  /**
   * The Google Cloud Platform project ID for this cloud instance
   */
  private String projectId;

  /**
   * The Google Service Account key or name as specified in the Jenkins credentials store
   */
  private String credentialsId;

  @DataBoundConstructor
  public ComputeEngineCloud(String name, String projectId, String credentialsId, String instanceCapStr) {
    super(name, instanceCapStr);
    setCredentialsId(credentialsId);
    setProjectId(projectId);
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

  public String getProjectId() {
    return projectId;
  }

  public void setProjectId(String projectId) {
    this.projectId = projectId;
  }

  public String getCredentialsId() {
    return credentialsId;
  }

  public void setCredentialsId(String credentialsId) {
    this.credentialsId = credentialsId;
  }

}
