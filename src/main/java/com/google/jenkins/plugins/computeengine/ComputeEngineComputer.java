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
import com.google.api.services.compute.model.Operation;
import com.google.jenkins.plugins.computeengine.client.ComputeClient;
import hudson.slaves.AbstractCloudComputer;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.HttpRedirect;
import org.kohsuke.stapler.HttpResponse;

import java.io.IOException;
import java.util.logging.Logger;

public class ComputeEngineComputer extends AbstractCloudComputer<ComputeEngineInstance> {

    private volatile Instance instance;

    public ComputeEngineComputer(ComputeEngineInstance slave) {
        super(slave);
    }

    private static final Logger LOGGER = Logger.getLogger(ComputeEngineCloud.class.getName());

    @Override
    public ComputeEngineInstance getNode() {
        return (ComputeEngineInstance) super.getNode();
    }

    public void onConnected() {
        ComputeEngineInstance node = getNode();
        if (node != null) {
            node.onConnected();
        }
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



    private Operation.Error createSnapshot(ComputeEngineCloud cloud) throws IOException, InterruptedException {
        ComputeEngineInstance node = getNode();
        ComputeClient client = cloud.client;
        return client.createSnapshot(cloud.projectId, node.zone, node.getNodeName());
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

                // Checks for failed jobs for this computer's node
                if (!this.getBuilds().failureOnly().isEmpty()) {
                    createSnapshot(cloud);
                }

                node.terminate();
            } catch (InterruptedException ie) {
                LOGGER.info("Interrupted exception for snapshot: " + ie);
            } catch (IOException ioe) {
                LOGGER.info("IOException for snapshot: " + ioe);
            }
        }
        return new HttpRedirect("..");
    }
}
