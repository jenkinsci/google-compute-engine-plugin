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
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Integration test suite for {@link ComputeEngineCloud}. Verifies that instances with preempted
 * flag will be restarted when preempted.
 */
@Log
public class ComputeEngineCloudRestartPreemptedIT {
  @ClassRule
  public static Timeout timeout = new Timeout(20 * TEST_TIMEOUT_MULTIPLIER, TimeUnit.MINUTES);

  @ClassRule public static JenkinsRule jenkinsRule = new JenkinsRule();

  private static ComputeClient client;
  private static Map<String, String> label = getLabel(ComputeEngineCloudRestartPreemptedIT.class);
  private static ComputeEngineCloud cloud;

  @BeforeClass
  public static void init() throws Exception {
    log.info("init");
    initCredentials(jenkinsRule);
    cloud = initCloud(jenkinsRule);
    client = initClient(jenkinsRule, label, log);

    InstanceConfiguration configuration =
        instanceConfigurationBuilder()
            .numExecutorsStr(NUM_EXECUTORS)
            .labels(LABEL)
            .template(NULL_TEMPLATE)
            .preemptible(true)
            .googleLabels(label)
            .oneShot(false)
            .build();

    cloud.setConfigurations(Lists.newArrayList(configuration));
  }

  @AfterClass
  public static void teardown() throws IOException {
    teardownResources(client, label, log);
  }

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

    FreeStyleProject project = jenkinsRule.createFreeStyleProject();
    Builder step = execute(Commands.SLEEP, "60");
    project.getBuildersList().add(step);
    project.setAssignedLabel(new LabelAtom(LABEL));
    QueueTaskFuture<FreeStyleBuild> taskFuture = project.scheduleBuild2(0);

    Awaitility.await()
        .timeout(7, TimeUnit.MINUTES)
        .until(() -> computer.getLog().contains("listening to metadata for preemption event"));

    client.simulateMaintenanceEvent(PROJECT_ID, ZONE, name);
    Awaitility.await().timeout(8, TimeUnit.MINUTES).until(computer::getPreempted);

    FreeStyleBuild freeStyleBuild = taskFuture.get();
    assertEquals(FAILURE, freeStyleBuild.getResult());

    Awaitility.await()
        .timeout(5, TimeUnit.MINUTES)
        .until(() -> freeStyleBuild.getNextBuild() != null);
    FreeStyleBuild nextBuild = freeStyleBuild.getNextBuild();
    Awaitility.await().timeout(5, TimeUnit.MINUTES).until(() -> nextBuild.getResult() != null);
    assertEquals(SUCCESS, nextBuild.getResult());
  }
}
