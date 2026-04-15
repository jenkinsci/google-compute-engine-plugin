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

package com.google.jenkins.plugins.computeengine.integration;

import static com.google.jenkins.plugins.computeengine.integration.ITUtil.LABEL;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.PROJECT_ID;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.TEST_TIMEOUT_MULTIPLIER;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.ZONE;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initCredentials;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import com.google.cloud.graphite.platforms.plugin.client.ComputeClient;
import com.google.jenkins.plugins.computeengine.ComputeEngineCloud;
import com.google.jenkins.plugins.computeengine.MinimumNumberOfInstancesTimeRangeConfig;
import com.google.jenkins.plugins.computeengine.client.ClientUtil;
import hudson.model.Node;
import hudson.model.listeners.SaveableListener;
import hudson.slaves.OfflineCause;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.FlagRule;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;

public class ComputeEngineCloudMinimumInstancesIT {
    private static final Logger log = Logger.getLogger(ComputeEngineCloudMinimumInstancesIT.class.getName());

    @ClassRule
    public static Timeout timeout = new Timeout(25L * TEST_TIMEOUT_MULTIPLIER, TimeUnit.MINUTES);

    @Rule
    public JenkinsConfiguredWithCodeRule j = new JenkinsConfiguredWithCodeRule();

    @ClassRule
    public static LoggerRule logRule =
            new LoggerRule().record("com.google.jenkins.plugins.computeengine", Level.FINEST);

    @ClassRule
    public static BuildWatcher bw = new BuildWatcher();

    @ClassRule
    public static FlagRule<String> minimumCheckPeriodPropRule = FlagRule.systemProperty(
            "com.google.jenkins.plugins.computeengine.ComputeEngineMonitor.minimumInstanceCheckPeriod", "PT2M");

    private ComputeClient client;

    @Before
    public void init() throws Exception {
        log.info("init");
        initCredentials(j);
        client = ClientUtil.getClientFactory(j.jenkins, PROJECT_ID).computeClient();
        // CasC loaded min/spare values but credentials weren't available yet so fails; trigger provisioning now
        SaveableListener.fireOnChange(j.jenkins, null);
    }

    @After
    public void teardown() throws IOException {
        log.info("teardown");
        if (client != null) {
            for (Node node : j.jenkins.getNodes()) {
                try {
                    log.info("deleting instance: " + node.getNodeName());
                    client.terminateInstanceAsync(PROJECT_ID, ZONE, node.getNodeName());
                } catch (Exception e) {
                    log.warning("Error deleting instance " + node.getNodeName() + ": " + e.getMessage());
                }
            }
        }
    }

    private int countOnlineIdleNodes() {
        return (int) j.jenkins.getNodes().stream()
                .map(Node::toComputer)
                .filter(c -> c != null && c.isOnline() && c.isIdle())
                .count();
    }

    private Set<String> currentNodeNames() {
        return j.jenkins.getNodes().stream().map(Node::getNodeName).collect(Collectors.toSet());
    }

    private void waitForSpareAgents(int expected) {
        await(expected + " spare agents online")
                .timeout(Duration.ofMinutes(5))
                .until(this::countOnlineIdleNodes, is(expected));
    }

    @Test
    @ConfiguredWithCode("minimum-instances-oneshot-casc.yml")
    public void testSpareInstancesProvisionedAndReplenished() throws Exception {
        waitForSpareAgents(2);

        var initialNames = currentNodeNames();
        log.info("Initial spare instances: " + initialNames);

        var p = j.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("node('" + LABEL + "') { sh 'echo ok' }", true));
        var r = j.buildAndAssertSuccess(p);

        // Determine the node name the build ran on from the build log
        var buildLog = JenkinsRule.getLog(r);
        var consumedAgent = initialNames.stream()
                .filter(name -> buildLog.contains("Running on " + name))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Build log does not contain 'Running on <agent>' for any initial agent. Log:\n" + buildLog));
        log.info("Build ran on agent: " + consumedAgent);

        // Wait for the consumed oneShot agent to be terminated
        await("consumed oneShot agent terminated after build")
                .timeout(Duration.ofMinutes(5))
                .until(() -> !currentNodeNames().contains(consumedAgent));

        // A replacement spare should be provisioned to maintain spare_min=2
        waitForSpareAgents(2);

        var finalNames = currentNodeNames();
        log.info("Final spare instances: " + finalNames);
        assertThat("consumed agent should be gone", finalNames, not(hasItem(consumedAgent)));
    }

    @Test
    @ConfiguredWithCode("minimum-instances-retention-casc.yml")
    public void testRetentionAgentsPreservedAcrossBuilds() throws Exception {
        waitForSpareAgents(2);

        var initialNames = currentNodeNames();
        log.info("Initial agents: " + initialNames);

        // Parallel pipeline forces both node blocks to run concurrently on separate agents
        var p = j.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                "parallel a: { node('" + LABEL + "') { sh 'echo ok' } }, b: { node('" + LABEL + "') { sh 'echo ok' } }",
                true));
        var b = p.scheduleBuild2(0);
        var r = j.assertBuildStatusSuccess(b);

        // Both parallel branches should have run on different initial agents
        var buildLog = JenkinsRule.getLog(r);
        var usedAgents = initialNames.stream()
                .filter(name -> buildLog.contains("Running on " + name))
                .collect(Collectors.toSet());
        log.info("Parallel branches ran on: " + usedAgents);
        assertThat("both agents were used by parallel branches", usedAgents, is(initialNames));

        // Agents should still be the same — preserved by minimumNumberOfInstances=2 and no new agents created
        waitForSpareAgents(2);
        assertThat("agents unchanged after two builds", currentNodeNames(), is(initialNames));
    }

    @Test
    @ConfiguredWithCode("minimum-instances-retention-spare-casc.yml")
    public void testVmTerminated_periodicCheckReprovisions() throws Exception {
        waitForSpareAgents(2);
        var initialNames = currentNodeNames();
        log.info("Initial agents: " + initialNames);

        for (String name : initialNames) {
            log.info("Terminating GCE instance: " + name);
            client.terminateInstanceAsync(PROJECT_ID, ZONE, name);
        }

        await("agents go offline after VM termination")
                .timeout(Duration.ofMinutes(3))
                .until(this::countOnlineIdleNodes, is(0));

        await("new spare agents provisioned by periodic checker - runs PT2M")
                .timeout(Duration.ofMinutes(5))
                .until(this::countOnlineIdleNodes, is(2));
        var newAgentNames = currentNodeNames();
        assertThat("new agents should have different names than initial agents", newAgentNames, not(is(initialNames)));
    }

    /**
     * Marks one agent temporarily offline and verifies:
     * <ol>
     *   <li>The offline agent is no longer counted as a spare</li>
     *   <li>A replacement spare is provisioned to meet minimumNumberOfSpareInstances=2</li>
     *   <li>After bringing the agent back online, the excess agent (3 spare vs 2 required)
     *       is terminated by the retention strategy's idle-timeout</li>
     * </ol>
     */
    @Test
    @ConfiguredWithCode("minimum-instances-offline-spare-casc.yml")
    public void testAgentTemporarilyOffline_notCountedAsSpare() throws Exception {
        waitForSpareAgents(2);

        var node = j.jenkins.getNodes().get(0);
        var computer = node.toComputer();
        log.info("Marking agent temporarily offline: " + node.getNodeName());
        computer.setTemporarilyOffline(true, new OfflineCause.UserCause(null, "test"));

        assertThat("total nodes unchanged (offline agent still registered)", j.jenkins.getNodes(), hasSize(2));
        assertThat("online idle nodes decremented", countOnlineIdleNodes(), is(1));

        await("replacement spare provisioned for offline agent by periodic checker - runs PT2M")
                .timeout(Duration.ofMinutes(5))
                .until(this::countOnlineIdleNodes, is(2));

        var namesAt3 = currentNodeNames();
        assertThat("total nodes after spare replenishment (includes temporarily offline one)", namesAt3, hasSize(3));

        log.info("Bringing agent back online: " + node.getNodeName());
        computer.setTemporarilyOffline(false, null);

        await("agent back online").timeout(Duration.ofMinutes(1)).until(computer::isOnline, is(true));

        // 3 spare but spare_min=2 — retention strategy (idle-timeout=4 min) terminates 1 excess
        await("retention strategy terminates exactly 1 excess agent")
                .timeout(Duration.ofMinutes(6))
                .until(() -> j.jenkins.getNodes(), hasSize(2));

        assertThat("spare agents after excess terminated", countOnlineIdleNodes(), is(2));
        // Verify survivors are from the 3-agent group, not newly provisioned (no race condition)
        assertThat(
                "remaining agents are a subset of the 3-agent group",
                namesAt3.containsAll(currentNodeNames()),
                is(true));
    }

    /**
     * Increases spare requirement from 2→3, waits for 3 agents, then reduces to 2.
     * Verifies that exactly 1 excess agent is terminated by the retention strategy,
     * and the surviving 2 agents are from the original 3 (not newly spawned).
     */
    @Test
    @ConfiguredWithCode("minimum-instances-retention-spare-casc.yml")
    public void testConfigReduction_spareReduced() throws Exception {
        waitForSpareAgents(2);

        log.info("Increasing minimumNumberOfSpareInstances from 2 to 3");
        var cloud = (ComputeEngineCloud) j.jenkins.clouds.getByName("gce-integration");
        cloud.getConfigurations().get(0).setMinimumNumberOfSpareInstances(3);
        SaveableListener.fireOnChange(j.jenkins, null);

        waitForSpareAgents(3);

        var namesBeforeReduction = currentNodeNames();
        log.info("Agents before reduction: " + namesBeforeReduction);
        assertThat("3 agents before reduction", namesBeforeReduction, hasSize(3));

        log.info("Reducing minimumNumberOfSpareInstances from 3 to 2");
        cloud.getConfigurations().get(0).setMinimumNumberOfSpareInstances(2);
        SaveableListener.fireOnChange(j.jenkins, null);

        await("excess agent terminated by retention strategy")
                .timeout(Duration.ofMinutes(4))
                .until(() -> j.jenkins.getNodes(), hasSize(2));

        var namesAfterReduction = currentNodeNames();
        log.info("Agents after reduction: " + namesAfterReduction);
        assertThat(
                "surviving agents are a subset of the original agents",
                namesBeforeReduction.containsAll(namesAfterReduction),
                is(true));
        assertThat("spare agents after reduction", countOnlineIdleNodes(), is(2));
    }

    /**
     * Verifies that minimum instance provisioning respects the time range configuration.
     * Starts with all days inactive (no provisioning), then enables the current day
     * and verifies agents are provisioned.
     */
    @Test
    @ConfiguredWithCode("minimum-instances-timerange-casc.yml")
    public void testTimeRange_provisioningOnlyDuringActiveRange() throws Exception {
        var cloud = (ComputeEngineCloud) j.jenkins.clouds.getByName("gce-integration");
        var config = cloud.getConfigurations().get(0);
        var timeRangeConfig = config.getMinimumNumberOfInstancesTimeRangeConfig();

        // Verify CasC loaded the time range with all days inactive
        assertThat("activeFrom loaded from CasC", timeRangeConfig.getActiveFrom(), is("00:00"));
        assertThat("activeTo loaded from CasC", timeRangeConfig.getActiveTo(), is("23:59"));
        assertThat("monday inactive", timeRangeConfig.getMonday(), is(false));
        assertThat("tuesday inactive", timeRangeConfig.getTuesday(), is(false));
        assertThat("wednesday inactive", timeRangeConfig.getWednesday(), is(false));
        assertThat("thursday inactive", timeRangeConfig.getThursday(), is(false));
        assertThat("friday inactive", timeRangeConfig.getFriday(), is(false));
        assertThat("saturday inactive", timeRangeConfig.getSaturday(), is(false));
        assertThat("sunday inactive", timeRangeConfig.getSunday(), is(false));

        // Trigger provisioning — should be blocked by inactive time range
        SaveableListener.fireOnChange(j.jenkins, null);
        Thread.sleep(Duration.ofSeconds(30).toMillis());
        assertThat("no agents provisioned outside active time range", j.jenkins.getNodes(), hasSize(0));

        // Enable the current day so the time range becomes active
        var today = LocalDateTime.now().getDayOfWeek();
        log.info("Enabling time range for current day: " + today);
        setDayActive(timeRangeConfig, today, true);

        // Verify the config update took effect
        var updatedTimeRange = cloud.getConfigurations().get(0).getMinimumNumberOfInstancesTimeRangeConfig();
        assertThat("current day is now active", updatedTimeRange.isDayActive(today), is(true));

        // Trigger provisioning — should now provision agents
        SaveableListener.fireOnChange(j.jenkins, null);
        waitForSpareAgents(2);

        log.info("Agents provisioned during active time range: " + currentNodeNames());
        assertThat("agents provisioned during active time range", j.jenkins.getNodes(), hasSize(2));
    }

    private static void setDayActive(MinimumNumberOfInstancesTimeRangeConfig config, DayOfWeek day, boolean active) {
        switch (day) {
            case MONDAY -> config.setMonday(active);
            case TUESDAY -> config.setTuesday(active);
            case WEDNESDAY -> config.setWednesday(active);
            case THURSDAY -> config.setThursday(active);
            case FRIDAY -> config.setFriday(active);
            case SATURDAY -> config.setSaturday(active);
            case SUNDAY -> config.setSunday(active);
        }
    }
}
