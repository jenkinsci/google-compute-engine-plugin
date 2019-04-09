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
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.teardownResources;
import static org.junit.Assert.assertEquals;

import com.google.jenkins.plugins.computeengine.ComputeEngineCloud;
import com.google.jenkins.plugins.computeengine.InstanceConfiguration;
import com.google.jenkins.plugins.computeengine.client.ComputeClient;
import hudson.model.labels.LabelAtom;
import hudson.slaves.NodeProvisioner.PlannedNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ExecutionException;
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

public class ComputeEngineCloudWorkerFailedIT {
  private static Logger log = Logger.getLogger(ComputeEngineCloudWorkerFailedIT.class.getName());

  @ClassRule public static Timeout timeout = new Timeout(5, TimeUnit.MINUTES);
  @ClassRule public static JenkinsRule jenkinsRule = new JenkinsRule();

  private static ByteArrayOutputStream logOutput = new ByteArrayOutputStream();
  private static StreamHandler streamHandler = new StreamHandler(logOutput, new SimpleFormatter());
  private static ComputeClient client;
  private static Map<String, String> label = getLabel(ComputeEngineCloudWorkerFailedIT.class);
  private static Collection<PlannedNode> planned;

  @BeforeClass
  public static void init() throws Exception {
    log.info("init");
    initCredentials(jenkinsRule);
    ComputeEngineCloud cloud = initCloud(jenkinsRule);
    handleClassLogs(streamHandler, ComputeEngineCloud.class.getName());
    client = initClient(jenkinsRule, label, log);
    handleClassLogs(streamHandler, ComputeClient.class.getName());

    // This configuration creates an instance with no Java installed.
    cloud.addConfiguration(
        instanceConfiguration(
            new InstanceConfiguration.Builder()
                .startupScript("")
                .numExecutorsStr(NUM_EXECUTORS)
                .labels(LABEL)
                .oneShot(false)
                .createSnapshot(false)
                .template(NULL_TEMPLATE),
            label));

    planned = cloud.provision(new LabelAtom(LABEL), 1);
  }

  @AfterClass
  public static void teardown() throws IOException {
    teardownResources(streamHandler, logOutput, client, label, log);
  }

  @Test
  public void testWorkerFailedNodePlanned() {
    assertEquals(logs(streamHandler, logOutput), 1, planned.size());
  }

  @Test(expected = ExecutionException.class)
  public void testWorkerFailedCreationFails() throws Exception {
    planned.iterator().next().future.get();
  }
}
