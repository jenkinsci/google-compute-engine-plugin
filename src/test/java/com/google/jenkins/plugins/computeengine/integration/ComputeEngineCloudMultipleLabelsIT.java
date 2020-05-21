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
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.getLabel;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initClient;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initCloud;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initCredentials;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.instanceConfigurationBuilder;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.teardownResources;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.google.cloud.graphite.platforms.plugin.client.ComputeClient;
import com.google.common.collect.ImmutableList;
import com.google.jenkins.plugins.computeengine.ComputeEngineCloud;
import hudson.model.Node;
import hudson.model.labels.LabelAtom;
import hudson.slaves.NodeProvisioner.PlannedNode;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Integration test suite for {@link ComputeEngineCloud}. Verifies that instances can be created
 * with multiple Jenkins labels and that these labels are properly provisioned.
 */
public class ComputeEngineCloudMultipleLabelsIT {
  private static Logger log = Logger.getLogger(ComputeEngineCloudMultipleLabelsIT.class.getName());

  private static final String MULTIPLE_LABEL = "integration test";

  @ClassRule
  public static Timeout timeout = new Timeout(5 * TEST_TIMEOUT_MULTIPLIER, TimeUnit.MINUTES);

  @ClassRule public static JenkinsRule jenkinsRule = new JenkinsRule();

  private static ComputeClient client;
  private static Map<String, String> label = getLabel(ComputeEngineCloudMultipleLabelsIT.class);
  private static String name;

  @BeforeClass
  public static void init() throws Exception {
    log.info("init");
    initCredentials(jenkinsRule);
    ComputeEngineCloud cloud = initCloud(jenkinsRule);
    client = initClient(jenkinsRule, label, log);

    cloud.setConfigurations(
        ImmutableList.of(
            instanceConfigurationBuilder()
                .numExecutorsStr(NUM_EXECUTORS)
                .labels(MULTIPLE_LABEL)
                .oneShot(false)
                .createSnapshot(false)
                .template(NULL_TEMPLATE)
                .googleLabels(label)
                .build()));

    Collection<PlannedNode> planned = cloud.provision(new LabelAtom(LABEL), 1);
    name = planned.iterator().next().displayName;
    planned.iterator().next().future.get();
  }

  @AfterClass
  public static void teardown() throws IOException {
    teardownResources(client, label, log);
  }

  @Test
  public void testMultipleLabelsProvisionedWithLabels() {
    Node node = jenkinsRule.jenkins.getNode(name);
    assertNotNull(node);
    String provisionedLabels = node.getLabelString();
    assertEquals(MULTIPLE_LABEL, provisionedLabels);
  }

  @Test
  public void testMultipleLabelsWorkerStatusRunning() throws Exception {
    assertEquals("RUNNING", client.getInstance(PROJECT_ID, ZONE, name).getStatus());
  }
}
