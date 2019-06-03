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

import com.google.common.collect.Lists;
import com.google.jenkins.plugins.computeengine.ComputeEngineCloud;
import com.google.jenkins.plugins.computeengine.InstanceConfiguration;
import com.google.jenkins.plugins.computeengine.client.ComputeClient;
import hudson.model.Node;
import hudson.model.labels.LabelAtom;
import hudson.slaves.NodeProvisioner;
import hudson.slaves.NodeProvisioner.PlannedNode;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static com.google.jenkins.plugins.computeengine.integration.ITUtil.DEB_JAVA_STARTUP_SCRIPT;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.LABEL;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.NULL_TEMPLATE;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.NUM_EXECUTORS;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.TEST_TIMEOUT_MULTIPLIER;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.getLabel;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initClient;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initCloud;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initCredentials;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.instanceConfiguration;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.teardownResources;
import static org.junit.Assert.assertEquals;

/**
 * Integration test suite for {@link ComputeEngineCloud}. Verifies that instances can be created
 * with multiple matching {@link InstanceConfiguration} and that these instances are properly provisioned.
 */
public class ComputeEngineCloudMultipeMatchingConfigurationsIT {
  private static Logger log = Logger.getLogger(ComputeEngineCloudMultipeMatchingConfigurationsIT.class.getName());

  private static final String DESC_1 = "type_1";
  private static final String DESC_2 = "type_2";

  @ClassRule
  public static Timeout timeout = new Timeout(5 * TEST_TIMEOUT_MULTIPLIER, TimeUnit.MINUTES);

  @ClassRule public static JenkinsRule jenkinsRule = new JenkinsRule();

  private static ComputeClient client;
  private static Map<String, String> label = getLabel(ComputeEngineCloudMultipeMatchingConfigurationsIT.class);
  private static Collection<PlannedNode> planned;

  @BeforeClass
  public static void init() throws Exception {
    log.info("init");
    initCredentials(jenkinsRule);
    ComputeEngineCloud cloud = initCloud(jenkinsRule);
    client = initClient(jenkinsRule, label, log);

    InstanceConfiguration.Builder builder1 = new InstanceConfiguration.Builder()
            .startupScript(DEB_JAVA_STARTUP_SCRIPT)
            .numExecutorsStr(NUM_EXECUTORS)
            .labels(LABEL)
            .template(NULL_TEMPLATE);

    InstanceConfiguration configuration1 = instanceConfiguration(builder1, label);
    configuration1.setDescription(DESC_1);

    InstanceConfiguration.Builder builder2 = new InstanceConfiguration.Builder()
            .startupScript(DEB_JAVA_STARTUP_SCRIPT)
            .numExecutorsStr(NUM_EXECUTORS)
            .labels(LABEL)
            .template(NULL_TEMPLATE);

    InstanceConfiguration configuration2 = instanceConfiguration(builder2, label);
    configuration2.setDescription(DESC_2);
    cloud.setConfigurations(
            Lists.newArrayList(
                    configuration1,
                    configuration2
            ));
    
    planned = cloud.provision(new LabelAtom(LABEL), 2);
  }

  @AfterClass
  public static void teardown() throws IOException {
    teardownResources(client, label, log);
  }

  @Test
  public void testMultipleLabelsProvisionedWithLabels() throws Exception {
    assertEquals(2, planned.size());

    final Iterator<PlannedNode> iterator = planned.iterator();
    PlannedNode plannedNode = iterator.next();
    checkOneNode(plannedNode, DESC_1);
    plannedNode = iterator.next();
    checkOneNode(plannedNode, DESC_2);
  }

  private void checkOneNode(PlannedNode plannedNode, String desc) throws InterruptedException, java.util.concurrent.ExecutionException {
    String name = plannedNode.displayName;
    plannedNode.future.get();
    Node node = jenkinsRule.jenkins.getNode(name);
    assertEquals(desc, node.getNodeDescription());
  }
}
