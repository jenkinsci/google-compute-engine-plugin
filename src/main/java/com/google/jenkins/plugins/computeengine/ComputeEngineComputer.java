/*
 * Copyright 2018 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.jenkins.plugins.computeengine;

import com.google.api.services.compute.model.Instance;
import hudson.model.Executor;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.remoting.Channel;
import hudson.slaves.AbstractCloudComputer;
import jenkins.model.CauseOfInterruption;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.HttpRedirect;
import org.kohsuke.stapler.HttpResponse;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ComputeEngineComputer extends AbstractCloudComputer<ComputeEngineInstance> {
    private static final Logger LOGGER = Logger.getLogger(ComputeEngineComputerListener.class.getName());

    private volatile Instance instance;
    private Future<Boolean> preemptedFuture;

    public ComputeEngineComputer(ComputeEngineInstance slave) {
        super(slave);
    }

    void onConnected(TaskListener listener) throws IOException {
        ComputeEngineInstance node = getNode();
        if (node != null) {
            node.onConnected();
            if (getPreemptible()) {
                String nodeName = node.getNodeName();
                LOGGER.log(Level.INFO, "Instance " + nodeName + " is preemptive, setting up preemption listener");
                preemptedFuture = getChannel().callAsync(new PreemptedCheckCallable(listener));
                getChannel().addListener(new Channel.Listener() {
                    @Override
                    public void onClosed(Channel channel, IOException cause) {
                        LOGGER.log(Level.INFO, "Goc channel close event");
                        if (getPreempted()) {
                            LOGGER.log(Level.INFO, "Goc channel close and its preempied");
                            getExecutors().forEach(executor -> interruptExecutor(executor, nodeName));
                        }
                    }
                });
            }
        }
    }

    private void interruptExecutor(Executor executor, String nodeName) {
        LOGGER.log(Level.INFO, "Terminating executor " + executor + " node " + nodeName);
        executor.interrupt(Result.FAILURE, new CauseOfInterruption() {
                @Override
                public String getShortDescription() {
                    return "Instance " + nodeName + " was preempted";
                }
            });
    }

    public String getNumExecutorsStr() {
        return String.valueOf(super.getNumExecutors());
    }

    @DataBoundSetter
    public void setNumExecutorsStr(String value) {
        Integer v = InstanceConfiguration.intOrDefault(value, InstanceConfiguration.DEFAULT_NUM_EXECUTORS);
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
        if (instance == null)
            instance = _getInstance();
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

    boolean getPreemptible() {
        try {
            return getInstance().getScheduling().getPreemptible();
        } catch (IOException | NullPointerException e) {
            return false;
        }
    }
    
    boolean getPreempted() {
        try {
            return preemptedFuture != null && preemptedFuture.isDone() && preemptedFuture.get();
        } catch (InterruptedException | ExecutionException e) {
            return false;
        }
    }

    private Instance _getInstance() throws IOException {
        try {
            ComputeEngineInstance node = getNode();
            ComputeEngineCloud cloud = getCloud();

            if (node != null) {
                return cloud.client.getInstance(cloud.projectId, node.zone, node.getNodeName());
            } else {
                return null;
            }
        } catch (CloudNotFoundException cnfe) {
            return null;
        }
    }

    protected ComputeEngineCloud getCloud() {
        ComputeEngineInstance node = getNode();
        if (node == null)
            throw new CloudNotFoundException("Could not retrieve cloud from empty node");

        return node.getCloud();
    }

    /**
     * When the slave is deleted, terminate the instance.
     */
    @Override
    public HttpResponse doDoDelete() throws IOException {
        checkPermission(DELETE);
        ComputeEngineInstance node = getNode();
        if (node != null) {
            try {
                ComputeEngineCloud cloud = getCloud();

                node.terminate();
            } catch (InterruptedException ie) {
                // Termination Exception
                LOGGER.log(Level.WARNING, "Node Termination Error", ie);
            }
        }
        return new HttpRedirect("..");
    }

}
