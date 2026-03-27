/*
 * Copyright 2026 CloudBees, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.jenkins.plugins.computeengine;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Computer;
import hudson.model.Label;
import hudson.model.Queue;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import jenkins.model.Jenkins;

public class MinimumInstanceChecker {
    private static final Logger LOGGER = Logger.getLogger(MinimumInstanceChecker.class.getName());

    /** Clock used for time-range checks. Can be overridden in tests. */
    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(
            value = "MS_SHOULD_BE_FINAL",
            justification = "Mutable for test clock injection")
    public static Clock clock = Clock.systemDefaultZone();

    /** Guard against re-entrant calls (e.g. addNode triggers SaveableListener on the same thread). */
    private static boolean checking = false;

    private MinimumInstanceChecker() {}

    static Stream<ComputeEngineComputer> agentsForConfig(@NonNull InstanceConfiguration config) {
        return Arrays.stream(Jenkins.get().getComputers())
                .filter(ComputeEngineComputer.class::isInstance)
                .map(ComputeEngineComputer.class::cast)
                .filter(computer -> {
                    var node = computer.getNode();
                    return node != null && Objects.equals(node.getNodeDescription(), config.getDescription());
                });
    }

    private static Stream<ComputeEngineComputer> idleAgents(@NonNull InstanceConfiguration config) {
        return agentsForConfig(config).filter(Computer::isIdle);
    }

    static int countCurrentNumberOfAgents(@NonNull InstanceConfiguration config) {
        return (int) agentsForConfig(config).count();
    }

    static int countCurrentNumberOfSpareAgents(@NonNull InstanceConfiguration config) {
        return (int) idleAgents(config).filter(Computer::isOnline).count();
    }

    static int countCurrentNumberOfProvisioningAgents(@NonNull InstanceConfiguration config) {
        return (int) idleAgents(config)
                .filter(Computer::isOffline)
                .filter(Computer::isConnecting)
                .count();
    }

    static int countQueueItemsForConfig(@NonNull InstanceConfiguration config) {
        return (int) Queue.getInstance().getBuildableItems().stream()
                .map(Queue.Item::getAssignedLabel)
                .filter(Objects::nonNull)
                .filter((Label label) -> label.matches(config.getLabelSet()))
                .count();
    }

    /**
     * Checks whether minimum instance requirements should be active based on the configured time
     * range. Returns true if no time range is configured (always active) or if the current time
     * falls within the configured half-open range [from, to) on an active day. For ranges that
     * cross midnight (e.g., 22:00-06:00), the post-midnight portion is attributed to the day the
     * range started.
     *
     * @param timeRangeConfig the time range configuration, or null if not configured
     * @return true if minimum instances should be enforced
     */
    public static boolean isActiveTimeRange(MinimumNumberOfInstancesTimeRangeConfig timeRangeConfig) {
        if (timeRangeConfig == null) {
            return true;
        }
        LocalTime fromTime = timeRangeConfig.getActiveFromAsTime();
        LocalTime toTime = timeRangeConfig.getActiveToAsTime();

        if (fromTime == null || toTime == null) {
            return true;
        }

        LocalDateTime now = LocalDateTime.now(clock);
        LocalTime nowTime = LocalTime.from(now);

        boolean passingMidnight = toTime.isBefore(fromTime);
        DayOfWeek today = now.getDayOfWeek();

        if (passingMidnight) {
            if (!nowTime.isBefore(fromTime)) {
                return timeRangeConfig.isDayActive(today);
            } else if (nowTime.isBefore(toTime)) {
                return timeRangeConfig.isDayActive(today.minus(1));
            }
        } else {
            if (!nowTime.isBefore(fromTime) && nowTime.isBefore(toTime)) {
                return timeRangeConfig.isDayActive(today);
            }
        }
        return false;
    }

    /**
     * Checks all GCE cloud configurations and provisions agents to meet minimum instance
     * requirements. Synchronized to prevent concurrent provisioning decisions.
     */
    public static synchronized void checkForMinimumInstances() {
        if (checking) {
            return;
        }
        checking = true;
        try {
            ComputeEngineRetentionStrategy.resetPendingTerminations();
            performCheck();
        } finally {
            checking = false;
        }
    }

    private static void performCheck() {
        Jenkins jenkins = Jenkins.get();

        if (jenkins.isQuietingDown() || jenkins.isTerminating()) {
            return;
        }

        jenkins.clouds.stream()
                .filter(ComputeEngineCloud.class::isInstance)
                .map(ComputeEngineCloud.class::cast)
                .forEach(cloud -> cloud.getConfigurations().forEach(config -> {
                    int requiredMinAgents = config.getMinimumNumberOfInstances();
                    int requiredMinSpareAgents = config.getMinimumNumberOfSpareInstances();

                    if (requiredMinAgents <= 0 && requiredMinSpareAgents <= 0) {
                        return;
                    }

                    if (!isActiveTimeRange(config.getMinimumNumberOfInstancesTimeRangeConfig())) {
                        return;
                    }

                    int currentNumberOfAgents = countCurrentNumberOfAgents(config);
                    int currentNumberOfSpareAgents = countCurrentNumberOfSpareAgents(config);
                    int currentNumberOfProvisioningAgents = countCurrentNumberOfProvisioningAgents(config);
                    int currentBuildsWaiting = countQueueItemsForConfig(config);

                    int provisionForMinAgents =
                            requiredMinAgents > 0 ? Math.max(0, requiredMinAgents - currentNumberOfAgents) : 0;

                    int provisionForMinSpareAgents = requiredMinSpareAgents > 0
                            ? Math.max(
                                    0,
                                    (requiredMinSpareAgents + currentBuildsWaiting)
                                            - (currentNumberOfSpareAgents
                                                    + provisionForMinAgents
                                                    + currentNumberOfProvisioningAgents))
                            : 0;

                    int numberToProvision = provisionForMinAgents + provisionForMinSpareAgents;

                    LOGGER.log(
                            Level.FINE,
                            "MinimumInstanceChecker for config {0}: currentAgents={1}, currentSpare={2}, "
                                    + "provisioning={3}, queuedBuilds={4}, toProvision={5}",
                            new Object[] {
                                config.getDescription(),
                                currentNumberOfAgents,
                                currentNumberOfSpareAgents,
                                currentNumberOfProvisioningAgents,
                                currentBuildsWaiting,
                                numberToProvision
                            });

                    if (numberToProvision > 0) {
                        cloud.provisionSpares(config, numberToProvision);
                    }
                }));
    }
}
