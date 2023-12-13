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

import static com.google.jenkins.plugins.computeengine.ComputeEngineCloud.CLOUD_ID_LABEL_KEY;
import static java.util.Collections.emptyList;

import com.google.api.services.compute.model.Instance;
import com.google.common.collect.ImmutableMap;
import hudson.Extension;
import hudson.model.PeriodicWork;
import hudson.model.Slave;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;

/** Periodically checks if there are no lost nodes in GCP. If it finds any they are deleted. */
@Extension
@Symbol("cleanLostNodesWork")
public class CleanLostNodesWork extends PeriodicWork {
    protected final Logger logger = Logger.getLogger(getClass().getName());

    /** {@inheritDoc} */
    @Override
    public long getRecurrencePeriod() {
        return HOUR;
    }

    /** {@inheritDoc} */
    @Override
    protected void doRun() {
        logger.log(Level.FINEST, "Starting clean lost nodes worker");
        getClouds().forEach(this::cleanCloud);
    }

    private void cleanCloud(ComputeEngineCloud cloud) {
        logger.log(Level.FINEST, "Cleaning cloud " + cloud.getCloudName());
        List<Instance> remoteInstances = findRemoteInstances(cloud);
        Set<String> localInstances = findLocalInstances(cloud);
        remoteInstances.stream()
                .filter(remote -> isOrphaned(remote, localInstances))
                .forEach(remote -> terminateInstance(remote, cloud));
    }

    private boolean isOrphaned(Instance remote, Set<String> localInstances) {
        String instanceName = remote.getName();
        logger.log(Level.FINEST, "Checking instance " + instanceName);
        return !localInstances.contains(instanceName);
    }

    private void terminateInstance(Instance remote, ComputeEngineCloud cloud) {
        String instanceName = remote.getName();
        logger.log(Level.INFO, "Remote instance " + instanceName + " not found locally, removing it");
        try {
            cloud.getClient().terminateInstanceAsync(cloud.getProjectId(), remote.getZone(), instanceName);
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Error terminating remote instance " + instanceName, ex);
        }
    }

    private List<ComputeEngineCloud> getClouds() {
        return Jenkins.get().clouds.stream()
                .filter(cloud -> cloud instanceof ComputeEngineCloud)
                .map(cloud -> (ComputeEngineCloud) cloud)
                .collect(Collectors.toList());
    }

    private Set<String> findLocalInstances(ComputeEngineCloud cloud) {
        return Jenkins.get().getNodes().stream()
                .filter(node -> node instanceof ComputeEngineInstance)
                .map(node -> (ComputeEngineInstance) node)
                .filter(node -> node.getCloud().equals(cloud))
                .map(Slave::getNodeName)
                .collect(Collectors.toSet());
    }

    private List<Instance> findRemoteInstances(ComputeEngineCloud cloud) {
        Map<String, String> filterLabel = ImmutableMap.of(CLOUD_ID_LABEL_KEY, cloud.getInstanceId());
        try {
            return cloud.getClient().listInstancesWithLabel(cloud.getProjectId(), filterLabel).stream()
                    .filter(instance -> shouldTerminateStatus(instance.getStatus()))
                    .collect(Collectors.toList());
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Error finding remote instances", ex);
            return emptyList();
        }
    }

    private boolean shouldTerminateStatus(String status) {
        return !status.equals("STOPPING");
    }
}
