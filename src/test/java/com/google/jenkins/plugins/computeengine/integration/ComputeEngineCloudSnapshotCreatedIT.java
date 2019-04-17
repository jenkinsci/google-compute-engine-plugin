/*
 * Copyright 2019 Google LLC
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

import static com.google.jenkins.plugins.computeengine.integration.ITUtil.DEB_JAVA_STARTUP_SCRIPT;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.NULL_TEMPLATE;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.NUM_EXECUTORS;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.PROJECT_ID;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.SNAPSHOT_LABEL;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.getLabel;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initClient;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initCloud;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initCredentials;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.instanceConfiguration;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.teardownResources;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.api.services.compute.model.Snapshot;
import com.google.jenkins.plugins.computeengine.ComputeEngineCloud;
import com.google.jenkins.plugins.computeengine.InstanceConfiguration;
import com.google.jenkins.plugins.computeengine.client.ComputeClient;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Node;
import hudson.model.Result;
import hudson.model.labels.LabelAtom;
import hudson.tasks.Builder;
import hudson.tasks.Shell;
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
 * Integration test suite for {@link ComputeEngineCloud}. Verifies that when configured to use one
 * shot instances and create snapshots, a snapshot will be created upon instance termination when a
 * build fails.
 */
public class ComputeEngineCloudSnapshotCreatedIT {
  private static Logger log = Logger.getLogger(ComputeEngineCloudSnapshotCreatedIT.class.getName());

  private static final int SNAPSHOT_TEST_TIMEOUT = 120;

  @ClassRule public static Timeout timeout = new Timeout(5, TimeUnit.MINUTES);
  @ClassRule public static JenkinsRule jenkinsRule = new JenkinsRule();

  private static ComputeClient client;
  private static Map<String, String> label = getLabel(ComputeEngineCloudSnapshotCreatedIT.class);
  private static Snapshot createdSnapshot = null;

  @BeforeClass
  public static void init() throws Exception {
    log.info("init");
    initCredentials(jenkinsRule);
    ComputeEngineCloud cloud = initCloud(jenkinsRule);
    client = initClient(jenkinsRule, label, log);

    assertTrue(cloud.configurations.isEmpty());
    InstanceConfiguration instanceConfiguration =
        instanceConfiguration(
            new InstanceConfiguration.Builder()
                .startupScript(DEB_JAVA_STARTUP_SCRIPT)
                .numExecutorsStr(NUM_EXECUTORS)
                .labels(SNAPSHOT_LABEL)
                .oneShot(true)
                .createSnapshot(true)
                .template(NULL_TEMPLATE),
            label);

    cloud.addConfiguration(instanceConfiguration);
    assertTrue(cloud.getInstanceConfig(instanceConfiguration.getDescription()).isCreateSnapshot());

    FreeStyleProject project = jenkinsRule.createFreeStyleProject();
    Builder step = new Shell("exit 1");
    project.getBuildersList().add(step);
    project.setAssignedLabel(new LabelAtom(SNAPSHOT_LABEL));

    FreeStyleBuild build = jenkinsRule.assertBuildStatus(Result.FAILURE, project.scheduleBuild2(0));
    Node worker = build.getBuiltOn();
    assertNotNull(worker);

    // Need time for one-shot instance to terminate and create the snapshot
    Awaitility.await()
        .timeout(SNAPSHOT_TEST_TIMEOUT, TimeUnit.SECONDS)
        .until(() -> jenkinsRule.jenkins.getNode(worker.getNodeName()) == null);

    createdSnapshot = client.getSnapshot(PROJECT_ID, worker.getNodeName());
  }

  @AfterClass
  public static void teardown() throws IOException {
    if (createdSnapshot != null) {
      client.deleteSnapshot(PROJECT_ID, createdSnapshot.getName());
    }
    teardownResources(client, label, log);
  }

  /** Tests snapshot is created when we have failure builds for given node */
  @Test
  public void testSnapshotCreatedNotNull() {
    assertNotNull(createdSnapshot);
  }

  @Test
  public void testSnapshotCreatedStatusReady() {
    assertEquals("READY", createdSnapshot.getStatus());
  }

  @Test
  public void testSnapshotCreatedOneShotInstanceDeleted() {
    Awaitility.await()
        .timeout(SNAPSHOT_TEST_TIMEOUT, TimeUnit.SECONDS)
        .until(() -> client.getInstancesWithLabel(PROJECT_ID, label).isEmpty());
  }
}
