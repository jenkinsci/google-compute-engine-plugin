/*
 * Copyright 2020 Elastic, and a number of other of contributors
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

import hudson.Extension;
import hudson.model.Label;
import hudson.model.LoadStatistics;
import hudson.slaves.Cloud;
import hudson.slaves.CloudProvisioningListener;
import hudson.slaves.NodeProvisioner;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;

/**
 * Implementation of {@link NodeProvisioner.Strategy} which will provision a new node immediately as
 * a task enter the queue.
 */
@Extension(ordinal = 100)
public class NoDelayProvisionerStrategy extends NodeProvisioner.Strategy {
  private static final Logger LOGGER = Logger.getLogger(NoDelayProvisionerStrategy.class.getName());

  private static final boolean DISABLE_NODELAY_PROVISING =
      Boolean.valueOf(
          System.getProperty(
              "com.google.jenkins.plugins.computeengine.disableNoDelayProvisioning"));

  /** {@inheritDoc} */
  @Override
  public NodeProvisioner.StrategyDecision apply(NodeProvisioner.StrategyState strategyState) {
    if (DISABLE_NODELAY_PROVISING) {
      LOGGER.log(Level.FINE, "Provisioning not complete, NoDelayProvisionerStrategy is disabled");
      return NodeProvisioner.StrategyDecision.CONSULT_REMAINING_STRATEGIES;
    }
    final Label label = strategyState.getLabel();

    LoadStatistics.LoadStatisticsSnapshot snapshot = strategyState.getSnapshot();
    int availableCapacity =
        snapshot.getAvailableExecutors() // live executors
            + snapshot.getConnectingExecutors() // executors present but not yet connected
            + strategyState
                .getPlannedCapacitySnapshot() // capacity added by previous strategies from previous
            // rounds
            + strategyState
                .getAdditionalPlannedCapacity(); // capacity added by previous strategies _this
    // round_
    int currentDemand = snapshot.getQueueLength();
    LOGGER.log(
        Level.FINE,
        "Available capacity={0}, currentDemand={1}",
        new Object[] {availableCapacity, currentDemand});
    if (availableCapacity < currentDemand) {
      List<Cloud> jenkinsClouds = new ArrayList<>(Jenkins.get().clouds);
      Collections.shuffle(jenkinsClouds);
      for (Cloud cloud : jenkinsClouds) {
        int workloadToProvision = currentDemand - availableCapacity;
        if (!(cloud instanceof ComputeEngineCloud)) continue;
        if (!cloud.canProvision(label)) continue;
        ComputeEngineCloud gcp = (ComputeEngineCloud) cloud;
        if (!gcp.isNoDelayProvisioning()) continue;
        for (CloudProvisioningListener cl : CloudProvisioningListener.all()) {
          if (cl.canProvision(cloud, strategyState.getLabel(), workloadToProvision) != null) {
            continue;
          }
        }
        Collection<NodeProvisioner.PlannedNode> plannedNodes =
            cloud.provision(label, workloadToProvision);
        LOGGER.log(Level.FINE, "Planned {0} new nodes", plannedNodes.size());
        fireOnStarted(cloud, strategyState.getLabel(), plannedNodes);
        strategyState.recordPendingLaunches(plannedNodes);
        availableCapacity += plannedNodes.size();
        LOGGER.log(
            Level.FINE,
            "After provisioning, available capacity={0}, currentDemand={1}",
            new Object[] {availableCapacity, currentDemand});
        break;
      }
    }
    if (availableCapacity >= currentDemand) {
      LOGGER.log(Level.FINE, "Provisioning completed");
      return NodeProvisioner.StrategyDecision.PROVISIONING_COMPLETED;
    } else {
      LOGGER.log(Level.FINE, "Provisioning not complete, consulting remaining strategies");
      return NodeProvisioner.StrategyDecision.CONSULT_REMAINING_STRATEGIES;
    }
  }

  private static void fireOnStarted(
      final Cloud cloud,
      final Label label,
      final Collection<NodeProvisioner.PlannedNode> plannedNodes) {
    for (CloudProvisioningListener cl : CloudProvisioningListener.all()) {
      try {
        cl.onStarted(cloud, label, plannedNodes);
      } catch (Error e) {
        throw e;
      } catch (Throwable e) {
        LOGGER.log(
            Level.SEVERE,
            "Unexpected uncaught exception encountered while "
                + "processing onStarted() listener call in "
                + cl
                + " for label "
                + label.toString(),
            e);
      }
    }
  }
}
