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

import static com.google.jenkins.plugins.computeengine.integration.ITUtil.NULL_TEMPLATE;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.NUM_EXECUTORS;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.PROJECT_ID;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.SNAPSHOT_LABEL;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.SNAPSHOT_TIMEOUT;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.TEST_TIMEOUT_MULTIPLIER;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.ZONE;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.execute;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.getLabel;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initClient;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initCloud;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initCredentials;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.teardownResources;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.cloud.graphite.platforms.plugin.client.ComputeClient;
import com.google.common.collect.ImmutableList;
import com.google.jenkins.plugins.computeengine.ComputeEngineCloud;
import com.google.jenkins.plugins.computeengine.InstanceConfiguration;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Node;
import hudson.model.labels.LabelAtom;
import hudson.tasks.Builder;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.awaitility.Awaitility;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Integration test suite for {@link ComputeEngineCloud}. Verifies that when the cloud is configured
 * to use one shot instances and create snapshots, upon successful build completion the one shot
 * instance is terminated but no snapshot is created.
 */
public class ComputeEngineCloudNoSnapshotCreatedIT {
  private static Logger log =
      Logger.getLogger(ComputeEngineCloudNoSnapshotCreatedIT.class.getName());

  @ClassRule
  public static Timeout timeout = new Timeout(7 * TEST_TIMEOUT_MULTIPLIER, TimeUnit.MINUTES);

  @ClassRule public static JenkinsRule jenkinsRule = new JenkinsRule();

  private static ComputeClient client;
  private static Map<String, String> label = getLabel(ComputeEngineCloudNoSnapshotCreatedIT.class);
  private static String name;

  @BeforeClass
  public static void init() throws Exception {
    log.info("init");
    initCredentials(jenkinsRule);
    ComputeEngineCloud cloud = initCloud(jenkinsRule);
    client = initClient(jenkinsRule, label, log);

    InstanceConfiguration instanceConfiguration =
        ITUtil.instanceConfigurationBuilder()
            .numExecutorsStr(NUM_EXECUTORS)
            .labels(SNAPSHOT_LABEL)
            .oneShot(true)
            .createSnapshot(true)
            .template(NULL_TEMPLATE)
            .googleLabels(label)
            .build();

    cloud.setConfigurations(ImmutableList.of(instanceConfiguration));
    assertTrue(
        cloud
            .getInstanceConfigurationByDescription(instanceConfiguration.getDescription())
            .isCreateSnapshot());

    FreeStyleProject project = jenkinsRule.createFreeStyleProject();
    Builder step = execute(Commands.ECHO, "works");
    project.getBuildersList().add(step);
    project.setAssignedLabel(new LabelAtom(SNAPSHOT_LABEL));

    FreeStyleBuild build = jenkinsRule.buildAndAssertSuccess(project);
    Node worker = build.getBuiltOn();
    assertNotNull(worker);

    name = worker.getNodeName();
  }

  @AfterClass
  public static void teardown() throws IOException {
    teardownResources(client, label, log);
  }

  /** Makes sure that the instance is being stopped after completing the job. */
  @Test
  public void testNoSnapshotCreatedInstanceStopping() {
    Awaitility.await()
        .timeout(1, TimeUnit.MINUTES)
        .until(() -> "STOPPING".equals(client.getInstance(PROJECT_ID, ZONE, name).getStatus()));
  }

  /** Tests that no snapshot is created when we only have successful builds for given node. */
  @Test(expected = IOException.class)
  public void testNoSnapshotCreatedSnapshotNull() throws Exception {
    // Wait for one-shot instance to terminate and create the snapshot
    Awaitility.await()
        .timeout(SNAPSHOT_TIMEOUT, TimeUnit.SECONDS)
        .pollInterval(10, TimeUnit.SECONDS)
        .until(() -> jenkinsRule.jenkins.getNode(name) == null);
    client.getSnapshot(PROJECT_ID, name);
  }
}
