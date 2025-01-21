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

package com.google.jenkins.plugins.computeengine.integration;

import static com.google.jenkins.plugins.computeengine.integration.ITUtil.LABEL;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.NULL_TEMPLATE;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.NUM_EXECUTORS;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.PROJECT_ID;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.TEST_TIMEOUT_MULTIPLIER;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.ZONE;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.execute;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.getLabel;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initClient;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initCloud;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initCredentials;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.instanceConfigurationBuilder;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.teardownResources;
import static hudson.model.Result.FAILURE;
import static hudson.model.Result.SUCCESS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.cloud.graphite.platforms.plugin.client.ComputeClient;
import com.google.common.collect.Lists;
import com.google.jenkins.plugins.computeengine.ComputeEngineCloud;
import com.google.jenkins.plugins.computeengine.ComputeEngineComputer;
import com.google.jenkins.plugins.computeengine.InstanceConfiguration;
import com.google.jenkins.plugins.computeengine.config.PreemptibleVm;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Node;
import hudson.model.labels.LabelAtom;
import hudson.model.queue.QueueTaskFuture;
import hudson.slaves.NodeProvisioner.PlannedNode;
import hudson.tasks.Builder;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.extern.java.Log;
import org.awaitility.Awaitility;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Integration test suite for {@link ComputeEngineCloud}. Verifies that the build is rescheduled and
 * completed successfully, when the agent was provisioned with Preemptible Vm, and the agent is preempted during an
 * ongoing build. See {@code PreemptedCheckCallable} being attached in {@link ComputeEngineComputer} and
 * {@code ComputeEngineRetentionStrategy#rescheduleTask} usages.
 */
@Log
public class ComputeEngineCloudRestartPreemptedIT {

    @ClassRule
    public static Timeout timeout = new Timeout(10L * TEST_TIMEOUT_MULTIPLIER, TimeUnit.MINUTES);

    @ClassRule
    public static JenkinsRule jenkinsRule = new JenkinsRule();

    @ClassRule
    public static BuildWatcher bw = new BuildWatcher();

    private static ComputeClient client;
    private static final Map<String, String> label = getLabel(ComputeEngineCloudRestartPreemptedIT.class);
    private static ComputeEngineCloud cloud;

    @BeforeClass
    public static void init() throws Exception {
        log.info("init");
        initCredentials(jenkinsRule);
        cloud = initCloud(jenkinsRule);
        client = initClient(jenkinsRule, label, log);

        InstanceConfiguration configuration = instanceConfigurationBuilder()
                .numExecutorsStr(NUM_EXECUTORS)
                .labels(LABEL)
                .template(NULL_TEMPLATE)
                .provisioningType(new PreemptibleVm())
                .googleLabels(label)
                .oneShot(false)
                .build();

        cloud.setConfigurations(Lists.newArrayList(configuration));
    }

    @AfterClass
    public static void teardown() throws IOException {
        teardownResources(client, label, log);
    }

    /**
     * This test works, the logs are also clear until the preemption event occurs. The {@code ComputeEngineCloud}
     * launch logs may seem confusing as they differ when we run the test multiple times.
     * <p>
     * After the preemption event occurred, even though the executors in the preempted agent are terminated
     * by the {@code ComputeEngineComputer#getPreemptedStatus}, but for some reason the {@code ComputeEngineCloud} logs
     * (sometimes and not always) show that Jenkins still attempts to connect to that stopping VM. Due to this attempt
     * to connect to the stopping VM, you might see some IOException, or host null because no network interface found
     * etc.
     * <p>
     * However note that these errors have nothing to do with the task being rescheduled, a new VM agent does get
     * provisioned and build is rescheduled on there, and succeeds.
     * <p>
     * It is just that in the test logs, the logs get mixed up and may seem confusing.
     */
    @Test
    public void testIfNodeWasPreempted() throws Exception {
        Collection<PlannedNode> planned = cloud.provision(new LabelAtom(LABEL), 1);
        Iterator<PlannedNode> iterator = planned.iterator();
        PlannedNode plannedNode = iterator.next();
        String name = plannedNode.displayName;
        plannedNode.future.get();
        Node node = jenkinsRule.jenkins.getNode(name);

        ComputeEngineComputer computer = (ComputeEngineComputer) node.toComputer();
        assertTrue("Configuration was set as preemptible but saw as not", computer.getPreemptible());

        FreeStyleProject project = jenkinsRule.createFreeStyleProject("p");
        Builder step = execute(Commands.SLEEP, "60");
        project.getBuildersList().add(step);
        project.setAssignedLabel(new LabelAtom(LABEL));
        FreeStyleBuild freeStyleBuild;
        // build1 that gets failed due to preemption
        QueueTaskFuture<FreeStyleBuild> taskFuture = project.scheduleBuild2(0);
        Awaitility.await().timeout(7, TimeUnit.MINUTES).until(() -> computer.getLog()
                .contains("listening to metadata for preemption event"));

        client.simulateMaintenanceEvent(PROJECT_ID, ZONE, name);
        Awaitility.await().timeout(8, TimeUnit.MINUTES).until(computer::getPreempted);

        freeStyleBuild = taskFuture.get();
        assertEquals(FAILURE, freeStyleBuild.getResult());

        Awaitility.await().timeout(5, TimeUnit.MINUTES).until(() -> freeStyleBuild.getNextBuild() != null);

        // build2 gets automatically scheduled and succeeds
        FreeStyleBuild nextBuild = freeStyleBuild.getNextBuild();
        Awaitility.await().timeout(5, TimeUnit.MINUTES).until(() -> nextBuild.getResult() != null);
        assertEquals(SUCCESS, nextBuild.getResult());
    }
}
