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

import static com.google.jenkins.plugins.computeengine.client.ComputeClient.nameFromSelfLink;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.DEB_JAVA_STARTUP_SCRIPT;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.LABEL;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.NUM_EXECUTORS;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.PROJECT_ID;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.ZONE;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.createTemplate;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.format;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.getLabel;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initClient;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initCloud;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initCredentials;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initLogging;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.instanceConfiguration;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.logs;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.InstanceTemplate;
import com.google.jenkins.plugins.computeengine.ComputeEngineCloud;
import com.google.jenkins.plugins.computeengine.client.ComputeClient;
import hudson.model.labels.LabelAtom;
import hudson.slaves.NodeProvisioner.PlannedNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.logging.StreamHandler;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.jvnet.hudson.test.JenkinsRule;

public class ComputeEngineCloudTemplateNoGoogleLabelsIT {
  private static Logger log =
      Logger.getLogger(ComputeEngineCloudTemplateNoGoogleLabelsIT.class.getName());

  private static final String TEMPLATE =
      format("projects/%s/global/instanceTemplates/test-template-no-labels");

  @ClassRule public static Timeout timeout = new Timeout(5, TimeUnit.MINUTES);
  @ClassRule public static JenkinsRule jenkinsRule = new JenkinsRule();

  private static ByteArrayOutputStream logOutput = new ByteArrayOutputStream();
  private static StreamHandler streamHandler;
  private static ComputeClient client;
  private static Map<String, String> label =
      getLabel(ComputeEngineCloudTemplateNoGoogleLabelsIT.class);
  private static ComputeEngineCloud cloud;
  private static Instance instance;

  @BeforeClass
  public static void init() throws Exception {
    log.info("init");
    initCredentials(jenkinsRule);
    cloud = initCloud(jenkinsRule);
    streamHandler = initLogging(logOutput);
    client = initClient(jenkinsRule, label, log);

    cloud.addConfiguration(
        instanceConfiguration(
            DEB_JAVA_STARTUP_SCRIPT, NUM_EXECUTORS, LABEL, label, false, false, TEMPLATE));
    InstanceTemplate instanceTemplate = createTemplate(null, TEMPLATE);
    client.insertTemplate(cloud.projectId, instanceTemplate);
    // Add a new node
    Collection<PlannedNode> planned = cloud.provision(new LabelAtom(LABEL), 1);
    // There should be a successful planned node even without google labels
    assertEquals(logs(streamHandler, logOutput), 1, planned.size());

    String name = planned.iterator().next().displayName;
    // Wait for the node creation to finish
    planned.iterator().next().future.get();
    instance = client.getInstance(PROJECT_ID, ZONE, name);
  }

  @AfterClass
  public static void teardown() throws IOException {
    try {
      client.deleteTemplate(cloud.projectId, nameFromSelfLink(TEMPLATE));
    } catch (Exception e) {
      // noop
    }

    ITUtil.teardown(streamHandler, logOutput, client, label, log);
  }

  @Test
  public void testTemplateNoGoogleLabelsNoWarningLogs() {
    // There should be no warning logs even without Google labels
    assertFalse(logs(streamHandler, logOutput), logs(streamHandler, logOutput).contains("WARNING"));
  }

  @Test
  public void testTemplateNoGoogleLabelsCloudIdLabelKeyAndValue() {
    assertEquals(
        logs(streamHandler, logOutput),
        cloud.getInstanceId(),
        instance.getLabels().get(ComputeEngineCloud.CLOUD_ID_LABEL_KEY));
  }
}
