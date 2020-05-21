/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.jenkins.plugins.computeengine;

import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.Scheduling;
import hudson.model.Executor;
import hudson.model.TaskListener;
import hudson.slaves.AbstractCloudComputer;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import lombok.extern.java.Log;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.HttpRedirect;
import org.kohsuke.stapler.HttpResponse;

@Log
public class ComputeEngineComputer extends AbstractCloudComputer<ComputeEngineInstance> {

  private volatile Instance instance;
  private CompletableFuture<Boolean> preemptedFuture;

  public ComputeEngineComputer(ComputeEngineInstance slave) {
    super(slave);
  }

  void onConnected(TaskListener listener) {
    ComputeEngineInstance node = getNode();
    if (node != null) {
      node.onConnected();
      if (getPreemptible()) {
        String nodeName = node.getNodeName();
        final String msg =
            "Instance " + nodeName + " is preemptive, setting up preemption listener";
        log.log(Level.INFO, msg);
        listener.getLogger().println(msg);
        preemptedFuture =
            CompletableFuture.supplyAsync(
                () -> getPreemptedStatus(listener, nodeName), threadPoolForRemoting);
      }
    }
  }

  private Boolean getPreemptedStatus(TaskListener listener, String nodeName) {
    try {
      boolean value = getChannel().call(new PreemptedCheckCallable(listener));
      log.log(Level.FINE, "Got information that node was preempted with value [" + value + "]");
      if (value) {
        log.log(Level.FINE, "Preempted node was preempted, terminating all executors");
        getChannel().close();
        getExecutors().forEach(executor -> interruptExecutor(executor, nodeName));
      }
      return value;
    } catch (InterruptedException | IOException e) {
      throw new RuntimeException(e);
    }
  }

  private void interruptExecutor(Executor executor, String nodeName) {
    log.log(Level.INFO, "Terminating executor " + executor + " node " + nodeName);
    executor.abortResult();
  }

  /**
   * Check if instance is preemptible.
   *
   * @return true if instance was set as preemptible.
   */
  public boolean getPreemptible() {
    try {
      Scheduling scheduling = getInstance().getScheduling();
      return scheduling != null && scheduling.getPreemptible();
    } catch (IOException e) {
      log.log(Level.WARNING, "Error when getting preemptible status", e);
      return false;
    }
  }

  /**
   * Check if instance was actually preempted.
   *
   * @return true if instance was preempted (we can use it to reschedule job in this case).
   */
  public boolean getPreempted() {
    try {
      return preemptedFuture != null && preemptedFuture.isDone() && preemptedFuture.get();
    } catch (InterruptedException | ExecutionException e) {
      log.log(Level.WARNING, "Error when getting preempted status", e);
      return false;
    }
  }

  public String getNumExecutorsStr() {
    return String.valueOf(super.getNumExecutors());
  }

  @DataBoundSetter
  public void setNumExecutorsStr(String value) {
    Integer v =
        InstanceConfiguration.intOrDefault(value, InstanceConfiguration.DEFAULT_NUM_EXECUTORS);
    ComputeEngineInstance node = getNode();
    if (node != null) {
      node.setNumExecutors(v);
    }
  }

  /**
   * Returns a cached representation of the Instance
   *
   * @return
   * @throws IOException
   */
  public Instance getInstance() throws IOException {
    if (instance == null) instance = _getInstance();
    return instance;
  }

  public Instance refreshInstance() throws IOException {
    instance = _getInstance();
    return instance;
  }

  /**
   * Returns the most current status of the Instance as reported by the GCE API
   *
   * @return
   * @throws IOException
   */
  public String getInstanceStatus() throws IOException {
    instance = _getInstance();
    return instance.getStatus();
  }

  private Instance _getInstance() throws IOException {
    try {
      ComputeEngineInstance node = getNode();
      ComputeEngineCloud cloud = getCloud();

      if (node != null) {
        return cloud
            .getClient()
            .getInstance(cloud.getProjectId(), node.getZone(), node.getNodeName());
      } else {
        return null;
      }
    } catch (CloudNotFoundException cnfe) {
      return null;
    }
  }

  protected ComputeEngineCloud getCloud() {
    ComputeEngineInstance node = getNode();
    if (node == null) throw new CloudNotFoundException("Could not retrieve cloud from empty node");

    return node.getCloud();
  }

  /** When the slave is deleted, terminate the instance. */
  @Override
  public HttpResponse doDoDelete() throws IOException {
    checkPermission(DELETE);
    ComputeEngineInstance node = getNode();
    if (node != null) {
      try {
        node.terminate();
      } catch (InterruptedException ie) {
        // Termination Exception
        log.log(Level.WARNING, "Node Termination Error", ie);
      }
    }
    return new HttpRedirect("..");
  }
}
