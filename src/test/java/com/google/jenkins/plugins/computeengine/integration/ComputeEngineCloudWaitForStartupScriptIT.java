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
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.windows;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeFalse;

import com.google.api.services.compute.model.Instance;
import com.google.cloud.graphite.platforms.plugin.client.ComputeClient;
import com.google.common.collect.ImmutableList;
import com.google.jenkins.plugins.computeengine.ComputeEngineCloud;
import com.google.jenkins.plugins.computeengine.InstanceConfiguration;
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
 * Integration test suite for {@link ComputeEngineCloudWaitForStartupScriptIT}. Verifies that
 * instances wait for their startup script to finish before launching the agent.
 */
public class ComputeEngineCloudWaitForStartupScriptIT {
  /**
   * We can check that the launcher waited to launch the agent if we move java, wait, and then move
   * it back. If the agent tries to launch, it should fail unless it waits.
   */
  private static final String SLOW_STARTUP_SCRIPT =
      "#!/bin/bash\n"
          + "ORIGINAL_JAVA_PATH=$(which java)\n"
          + "sudo mv $ORIGINAL_JAVA_PATH $ORIGINAL_JAVA_PATH.hidden\n"
          + "sleep 30\n"
          + "sudo mv $ORIGINAL_JAVA_PATH.hidden $ORIGINAL_JAVA_PATH";

  private static final Logger log =
      Logger.getLogger(ComputeEngineCloudWaitForStartupScriptIT.class.getName());

  @ClassRule
  public static Timeout timeout = new Timeout(5L * TEST_TIMEOUT_MULTIPLIER, TimeUnit.MINUTES);

  @ClassRule public static JenkinsRule jenkinsRule = new JenkinsRule();

  private static ComputeClient client;
  private static final Map<String, String> label =
      getLabel(ComputeEngineCloudWaitForStartupScriptIT.class);
  private static Collection<PlannedNode> planned;
  private static Instance instance;

  @BeforeClass
  public static void init() throws Exception {
    assumeFalse(windows);
    log.info("init");
    initCredentials(jenkinsRule);
    ComputeEngineCloud cloud = initCloud(jenkinsRule);
    client = initClient(jenkinsRule, label, log);

    InstanceConfiguration instanceConfiguration =
        instanceConfigurationBuilder()
            .startupScript(SLOW_STARTUP_SCRIPT)
            .numExecutorsStr(NUM_EXECUTORS)
            .labels(LABEL)
            .oneShot(false)
            .createSnapshot(false)
            .template(NULL_TEMPLATE)
            .googleLabels(label)
            .waitForStartupScript(true)
            .build();

    cloud.setConfigurations(ImmutableList.of(instanceConfiguration));
    planned = cloud.provision(new LabelAtom(LABEL), 1);
    planned.iterator().next().future.get();
    instance =
        cloud.getClient().getInstance(PROJECT_ID, ZONE, planned.iterator().next().displayName);
  }

  @AfterClass
  public static void teardown() throws IOException {
    teardownResources(client, label, log);
  }

  @Test
  public void testWorkerCreatedOnePlannedNode() {
    assertEquals(1, planned.size());
  }

  @Test
  public void testInstanceStatusRunning() {
    assertEquals("RUNNING", instance.getStatus());
  }
}
