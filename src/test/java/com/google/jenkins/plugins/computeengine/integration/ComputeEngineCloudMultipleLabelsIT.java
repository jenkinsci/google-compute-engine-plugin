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
import java.util.logging.Logger;
import java.util.logging.StreamHandler;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class ComputeEngineCloudMultipleLabelsIT {
  private static Logger log = Logger.getLogger(ComputeEngineCloudMultipleLabelsIT.class.getName());

  private static final String MULTIPLE_LABEL = "integration test";

  @ClassRule public static JenkinsRule r = new JenkinsRule();

  private static ByteArrayOutputStream logOutput = new ByteArrayOutputStream();
  private static StreamHandler sh;
  private static ComputeClient client;
  private static Map<String, String> label =
      ITUtil.getLabel(ComputeEngineCloudMultipleLabelsIT.class);
  private static Collection<PlannedNode> planned;

  @BeforeClass
  public static void init() throws Exception {
    log.info("init");
    ITUtil.initCredentials(r);
    ComputeEngineCloud cloud = ITUtil.initCloud(r);
    sh = ITUtil.initLogging(logOutput);
    client = ITUtil.initClient(r, label, log);

    InstanceConfiguration ic =
        ITUtil.instanceConfiguration(
            ITUtil.DEB_JAVA_STARTUP_SCRIPT,
            ITUtil.NUM_EXECUTORS,
            MULTIPLE_LABEL,
            label,
            false,
            false,
            ITUtil.NULL_TEMPLATE);
    cloud.addConfiguration(ic);
    // Add a new node
    planned = cloud.provision(new LabelAtom(ITUtil.LABEL), 1);
  }

  @AfterClass
  public static void teardown() throws IOException {
    ITUtil.teardown(sh, logOutput, client, label, log);
  }

  @Test(timeout = 300000)
  public void testMultipleLabelsForJob() {
    // For a configuration with multiple labels, test if job label matches one of the
    // configuration's labels

    // There should be a planned node
    assertEquals(ITUtil.logs(sh, logOutput), 1, planned.size());
  }

  @Test(timeout = 300000)
  public void testMultipleLabelsInConfig() throws Exception {
    // For a configuration with multiple labels, test if job label matches one of the
    // configuration's labels

    String name = planned.iterator().next().displayName;
    planned.iterator().next().future.get();
    String provisionedLabels = r.jenkins.getNode(name).getLabelString();
    // There should be the proper labels provisioned
    assertEquals(ITUtil.logs(sh, logOutput), MULTIPLE_LABEL, provisionedLabels);
  }
}
