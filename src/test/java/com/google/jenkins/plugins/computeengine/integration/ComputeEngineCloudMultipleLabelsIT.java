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
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.LABEL;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.NULL_TEMPLATE;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.NUM_EXECUTORS;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.getLabel;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.handleClassLogs;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initClient;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initCloud;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initCredentials;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.instanceConfiguration;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.logs;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.google.jenkins.plugins.computeengine.ComputeEngineCloud;
import com.google.jenkins.plugins.computeengine.client.ComputeClient;
import hudson.model.Node;
import hudson.model.labels.LabelAtom;
import hudson.slaves.NodeProvisioner.PlannedNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.logging.StreamHandler;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.jvnet.hudson.test.JenkinsRule;

public class ComputeEngineCloudMultipleLabelsIT {
  private static Logger log = Logger.getLogger(ComputeEngineCloudMultipleLabelsIT.class.getName());

  private static final String MULTIPLE_LABEL = "integration test";

  @ClassRule public static Timeout timeout = new Timeout(5, TimeUnit.MINUTES);
  @ClassRule public static JenkinsRule jenkinsRule = new JenkinsRule();

  private static ByteArrayOutputStream logOutput = new ByteArrayOutputStream();
  private static StreamHandler streamHandler = new StreamHandler(logOutput, new SimpleFormatter());
  private static ComputeClient client;
  private static Map<String, String> label = getLabel(ComputeEngineCloudMultipleLabelsIT.class);
  private static Collection<PlannedNode> planned;

  @BeforeClass
  public static void init() throws Exception {
    log.info("init");
    initCredentials(jenkinsRule);
    ComputeEngineCloud cloud = initCloud(jenkinsRule);
    handleClassLogs(streamHandler, ComputeEngineCloud.class.getName());
    client = initClient(jenkinsRule, label, log);
    handleClassLogs(streamHandler, ComputeClient.class.getName());

    cloud.addConfiguration(
        instanceConfiguration(
            DEB_JAVA_STARTUP_SCRIPT,
            NUM_EXECUTORS,
            MULTIPLE_LABEL,
            label,
            false,
            false,
            NULL_TEMPLATE));
    // Add a new node
    planned = cloud.provision(new LabelAtom(LABEL), 1);
    planned.iterator().next().future.get();
  }

  @AfterClass
  public static void teardown() throws IOException {
    ITUtil.teardown(streamHandler, logOutput, client, label, log);
  }

  @Test
  public void testMultipleLabelsForJob() {
    // For a configuration with multiple labels, test if job label matches one of the
    // configuration's labels

    // There should be a planned node
    assertEquals(logs(streamHandler, logOutput), 1, planned.size());
  }

  @Test
  public void testMultipleLabelsInConfig() throws Exception {
    // For a configuration with multiple labels, test if job label matches one of the
    // configuration's labels

    String name = planned.iterator().next().displayName;
    Node node = jenkinsRule.jenkins.getNode(name);
    assertNotNull(node);
    String provisionedLabels = node.getLabelString();
    // There should be the proper labels provisioned
    assertEquals(logs(streamHandler, logOutput), MULTIPLE_LABEL, provisionedLabels);
  }
}
