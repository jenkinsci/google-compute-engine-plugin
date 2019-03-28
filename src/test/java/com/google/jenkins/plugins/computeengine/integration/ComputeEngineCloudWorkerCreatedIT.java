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
import static org.junit.Assert.assertFalse;

import com.google.api.services.compute.model.Instance;
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

public class ComputeEngineCloudWorkerCreatedIT {
  private static Logger log = Logger.getLogger(ComputeEngineCloudWorkerCreatedIT.class.getName());

  @ClassRule public static JenkinsRule r = new JenkinsRule();

  private static ByteArrayOutputStream logOutput = new ByteArrayOutputStream();
  private static StreamHandler sh;
  private static ComputeClient client;
  private static ComputeEngineCloud cloud;
  private static Map<String, String> label =
      ITUtil.getLabel(ComputeEngineCloudWorkerCreatedIT.class);
  private static InstanceConfiguration ic;
  private static Instance i;

  @BeforeClass
  public static void init() throws Exception {
    log.info("init");
    ITUtil.initCredentials(r);
    cloud = ITUtil.initCloud(r);
    sh = ITUtil.initLogging(logOutput);
    client = ITUtil.initClient(r, label, log);

    ic = ITUtil.instanceConfiguration(
            ITUtil.DEB_JAVA_STARTUP_SCRIPT,
            ITUtil.NUM_EXECUTORS,
            ITUtil.LABEL,
            label,
            false,
            false,
            ITUtil.NULL_TEMPLATE);
    cloud.addConfiguration(ic);
    // Add a new node
    Collection<PlannedNode> planned = cloud.provision(new LabelAtom(ITUtil.LABEL), 1);

    // There should be a planned node
    assertEquals(ITUtil.logs(sh, logOutput), 1, planned.size());

    String name = planned.iterator().next().displayName;

    // Wait for the node creation to finish
    planned.iterator().next().future.get();

    i = cloud.getClient().getInstance(ITUtil.PROJECT_ID, ITUtil.ZONE, name);
  }

  @AfterClass
  public static void teardown() throws IOException {
    ITUtil.teardown(sh, logOutput, client, label, log);
  }

  @Test
  public void testWorkerCreatedNoWarningLogs() {
    // There should be no warning logs
    assertFalse(ITUtil.logs(sh, logOutput), ITUtil.logs(sh, logOutput).contains("WARNING"));
  }

  @Test
  public void testWorkerCreatedNumberOfLabels(){
    // The created instance should have 3 labels
    assertEquals(ITUtil.logs(sh, logOutput), 3, i.getLabels().size());
  }

  @Test
  public void testWorkerCreatedConfigLabelKeyAndValue() {
    // Instance should have a label with key CONFIG_LABEL_KEY and value equal to the config's name
    // prefix
    assertEquals(
        ITUtil.logs(sh, logOutput),
        ic.getNamePrefix(),
        i.getLabels().get(ComputeEngineCloud.CONFIG_LABEL_KEY));
  }

  @Test
  public void testWorkerCreatedCloudIdKeyAndValue() {
    // Instance should have a label with key CLOUD_ID_LABEL_KEY and value equal to the instance ID
    assertEquals(
        ITUtil.logs(sh, logOutput),
        cloud.getInstanceId(),
        i.getLabels().get(ComputeEngineCloud.CLOUD_ID_LABEL_KEY));
  }
}
