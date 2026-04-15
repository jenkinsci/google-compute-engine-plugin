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

import com.google.common.annotations.VisibleForTesting;
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
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import jenkins.model.Jenkins;

/**
 * Enforces minimum instance requirements for {@link InstanceConfiguration}s.
 *
 * <ul>
 *   <li><b>Provisioning</b> — {@link #checkForMinimumInstances()} iterates all GCE clouds/configs
 *       and provisions agents to meet {@code minimumNumberOfInstances} and
 *       {@code minimumNumberOfSpareInstances}. Called on startup, on config save, on build events and periodically.
 *   <li><b>Retention protection</b> — {@link #shouldPreserve(ComputeEngineComputer)} prevents the
 *       idle-timeout from terminating agents that are needed to satisfy minimums.
 *   <li><b>Time-range gating</b> — {@link #isActiveTimeRange} checks whether minimums should be
 *       enforced based on configured time-of-day and day-of-week windows.
 *   <li><b>Instance failure handling</b> — when a oneShot agent completes (including preemption),
 *       {@link ComputeEngineRetentionStrategy} triggers immediate replenishment. For non-oneShot
 *       agents that go offline (VM preempted, crashed, or disconnected), replenishment currently
 *       relies on the periodic check (up to 10-min delay). Adding
 *       {@link hudson.slaves.ComputerListener#onOffline} to {@link ComputeEngineComputerListener}
 *       would close this gap.
 *   <li><b>Pure decision methods</b> — {@link #computeProvisionCount} and
 *       {@link #shouldPreserveAgent} contain the core math/logic, decoupled from Jenkins state
 *       for unit testing via {@link MinCheckerInput}.
 * </ul>
 */
public class MinimumInstanceChecker {
    private static final Logger LOGGER = Logger.getLogger(MinimumInstanceChecker.class.getName());

    @VisibleForTesting
    static Clock clock = Clock.systemDefaultZone();

    /**
     * Tracks how many spare agents we have already declined to protect (i.e., allowed the delegate
     * to terminate) per config instance. This prevents a TOCTOU race where multiple agents all
     * see the same currentSpare count before any async termination completes, causing all of them
     * to be terminated simultaneously.
     */
    private static final ConcurrentHashMap<InstanceConfiguration, AtomicInteger> pendingTerminations =
            new ConcurrentHashMap<>();

    /** Guard against concurrent calls (e.g. periodic checker and SaveableListener firing simultaneously). */
    private static final AtomicBoolean checking = new AtomicBoolean(false);

    private MinimumInstanceChecker() {}

    record MinCheckerInput(int totalAgents, int spareAgents, int provisioningAgents, int queuedBuilds) {
        static MinCheckerInput of(InstanceConfiguration config) {
            return new MinCheckerInput(
                    countCurrentNumberOfAgents(config),
                    countCurrentNumberOfSpareAgents(config),
                    countCurrentNumberOfProvisioningAgents(config),
                    countQueueItemsForConfig(config));
        }
    }

    /** Called by {@link ComputeEngineRetentionStrategy#check} before delegating to the {@code OnceRetentionStrategy} */
    public static boolean shouldPreserve(ComputeEngineComputer c) {
        var node = c.getNode();
        if (node == null) {
            return false;
        }

        var cloud = node.getCloud();
        var config = cloud.getInstanceConfigurationByDescription(node.getNodeDescription());
        if (config == null) {
            return false;
        }

        if (isMinimumInstancesInactive(config)) {
            return false;
        }

        int minInstances = config.getMinimumNumberOfInstances();
        int minSpare = config.getMinimumNumberOfSpareInstances();
        var input = MinCheckerInput.of(config);
        int pending = getPendingTerminations(config);

        var preserve = shouldPreserveAgent(minInstances, minSpare, input, pending, c.isIdle(), c.isOnline());

        if (preserve.isEmpty()) {
            LOGGER.log(Level.FINE, "Preserving {0}: {1}, pending={2}, minInstances={3}, minSpare={4}", new Object[] {
                c.getName(), input, pending, minInstances, minSpare
            });
            return true;
        }

        LOGGER.log(
                Level.FINE,
                "Not preserving {0}, allowing delegate retention strategy to decide. Why: {1}",
                new Object[] {c.getName(), preserve.get()});
        incrementPendingTerminations(config);
        return false;
    }

    /**
     * Provisions agents as needed to satisfy minimum instance requirements across all GCE cloud
     * configurations. Concurrent calls are skipped rather than queued to avoid double-provisioning.
     */
    public static void checkForMinimumInstances() {
        if (!checking.compareAndSet(false, true)) {
            LOGGER.log(Level.FINER, "Concurrent checkForMinimumInstances call, skipping");
            return;
        }
        try {
            resetPendingTerminations();
            performCheck();
        } finally {
            checking.set(false);
        }
    }

    /**
     * Determines whether an agent should be preserved to meet minimum instance requirements.
     * Returns {@link Optional#empty()} if the agent should be preserved, or a reason string
     * explaining why it should not be preserved.
     * Pure function — no Jenkins access, no side effects.
     */
    @VisibleForTesting
    static Optional<String> shouldPreserveAgent(
            int minInstances,
            int minSpare,
            MinCheckerInput input,
            int pendingTerminations,
            boolean idle,
            boolean online) {
        if (minInstances > 0 && input.totalAgents() - pendingTerminations <= minInstances) {
            return Optional.empty();
        }
        if (minSpare > 0 && idle && online && input.spareAgents() - pendingTerminations <= minSpare) {
            return Optional.empty();
        }
        if (!online) {
            return Optional.of("agent is offline");
        }
        if (!idle) {
            return Optional.of("agent is busy");
        }
        if (minInstances > 0 && input.totalAgents() - pendingTerminations > minInstances) {
            return Optional.of("totalAgents(" + input.totalAgents() + ")-pending(" + pendingTerminations
                    + ") > minInstances(" + minInstances + ")");
        }
        return Optional.of("spareAgents(" + input.spareAgents() + ")-pending(" + pendingTerminations + ") > minSpare("
                + minSpare + ")");
    }

    /**
     * Computes how many agents to provision for a config to meet minimum instance requirements.
     * Pure function — no Jenkins access, no side effects.
     */
    @VisibleForTesting
    static int computeProvisionCount(int requiredMin, int requiredMinSpare, MinCheckerInput input) {
        int forMin = requiredMin > 0 ? Math.max(0, requiredMin - input.totalAgents()) : 0;
        int forSpare = requiredMinSpare > 0
                ? Math.max(
                        0,
                        (requiredMinSpare + input.queuedBuilds())
                                - (input.spareAgents() + forMin + input.provisioningAgents()))
                : 0;
        return forMin + forSpare;
    }

    private static boolean isMinimumInstancesInactive(InstanceConfiguration config) {
        if (config.getMinimumNumberOfInstances() <= 0 && config.getMinimumNumberOfSpareInstances() <= 0) {
            LOGGER.log(
                    Level.FINER, "Config {0} has no minimum instance requirements, skipping", config.getDescription());
            return true;
        }
        if (!isActiveTimeRange(config.getMinimumNumberOfInstancesTimeRangeConfig())) {
            LOGGER.log(
                    Level.FINE, "Minimum instances time range is not active for config {0}", config.getDescription());
            return true;
        }
        return false;
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
    @VisibleForTesting
    static boolean isActiveTimeRange(MinimumNumberOfInstancesTimeRangeConfig timeRangeConfig) {
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

    private static void performCheck() {
        Jenkins jenkins = Jenkins.get();

        if (jenkins.isQuietingDown() || jenkins.isTerminating()) {
            LOGGER.log(Level.FINER, "Jenkins is quieting down or terminating, skipping minimum instance check");
            return;
        }

        jenkins.clouds.stream()
                .filter(ComputeEngineCloud.class::isInstance)
                .map(ComputeEngineCloud.class::cast)
                .forEach(cloud -> cloud.getConfigurations().forEach(config -> {
                    if (isMinimumInstancesInactive(config)) {
                        return;
                    }

                    int requiredMinAgents = config.getMinimumNumberOfInstances();
                    int requiredMinSpareAgents = config.getMinimumNumberOfSpareInstances();
                    var input = MinCheckerInput.of(config);
                    int numberToProvision = computeProvisionCount(requiredMinAgents, requiredMinSpareAgents, input);

                    LOGGER.log(Level.FINE, "Config {0}: {1}, toProvision={2}", new Object[] {
                        config.getDescription(), input, numberToProvision
                    });

                    if (numberToProvision > 0) {
                        cloud.provisionSpares(config, numberToProvision);
                    }
                }));
    }

    private static int getPendingTerminations(InstanceConfiguration config) {
        var counter = pendingTerminations.get(config);
        return counter == null ? 0 : counter.get();
    }

    private static void incrementPendingTerminations(InstanceConfiguration config) {
        int newValue = pendingTerminations
                .computeIfAbsent(config, k -> new AtomicInteger(0))
                .incrementAndGet();
        LOGGER.log(Level.FINEST, "pendingTerminations for {0} incremented to {1}", new Object[] {
            config.getDescription(), newValue
        });
    }

    private static void resetPendingTerminations() {
        if (!pendingTerminations.isEmpty()) {
            LOGGER.log(Level.FINEST, "Resetting pendingTerminations: {0}", pendingTerminations);
        }
        pendingTerminations.clear();
    }

    private static Stream<ComputeEngineComputer> agentsForConfig(@NonNull InstanceConfiguration config) {
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

    private static int countCurrentNumberOfAgents(@NonNull InstanceConfiguration config) {
        return (int) agentsForConfig(config).count();
    }

    private static int countCurrentNumberOfSpareAgents(@NonNull InstanceConfiguration config) {
        return (int) idleAgents(config).filter(Computer::isOnline).count();
    }

    private static int countCurrentNumberOfProvisioningAgents(@NonNull InstanceConfiguration config) {
        return (int) idleAgents(config)
                .filter(Computer::isOffline)
                .filter(Computer::isConnecting)
                .count();
    }

    private static int countQueueItemsForConfig(@NonNull InstanceConfiguration config) {
        return (int) Queue.getInstance().getBuildableItems().stream()
                .map(Queue.Item::getAssignedLabel)
                .filter(Objects::nonNull)
                .filter((Label label) -> label.matches(config.getLabelSet()))
                .count();
    }
}
