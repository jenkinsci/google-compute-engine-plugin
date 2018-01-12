package com.google.jenkins.plugins.computeengine;

import hudson.slaves.AbstractCloudComputer;
import org.kohsuke.stapler.HttpRedirect;
import org.kohsuke.stapler.HttpResponse;

import java.io.IOException;

public class ComputeEngineComputer extends AbstractCloudComputer<ComputeEngineInstance> {

  public ComputeEngineComputer(ComputeEngineInstance slave) {
    super(slave);
  }

  @Override
  public ComputeEngineInstance getNode() {
    return (ComputeEngineInstance)super.getNode();
  }

  /**
   * When the slave is deleted, terminate the instance.
   */
  @Override
  public HttpResponse doDoDelete() throws IOException {
    checkPermission(DELETE);
    if (getNode() != null) {
      try {
        getNode().terminate();
      } catch(InterruptedException ie){
        //TODO: log
      }
    }
    return new HttpRedirect("..");
  }
}
