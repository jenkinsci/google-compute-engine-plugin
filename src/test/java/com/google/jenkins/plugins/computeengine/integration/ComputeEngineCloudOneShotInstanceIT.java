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
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.PROJECT_ID;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.getLabel;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initClient;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initCloud;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initCredentials;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.instanceConfiguration;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.teardownResources;
import static org.junit.Assert.assertTrue;

import com.google.jenkins.plugins.computeengine.ComputeEngineCloud;
import com.google.jenkins.plugins.computeengine.InstanceConfiguration;
import com.google.jenkins.plugins.computeengine.client.ComputeClient;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.labels.LabelAtom;
import hudson.tasks.Builder;
import hudson.tasks.Shell;
import java.io.IOException;
import java.util.Collections;
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
 * Integration test suite for {@link ComputeEngineCloud}.
 * Verifies that one shot instances are terminated after finishing a build
 * and removed from both GCP and the set of Jenkins nodes.
 */
public class ComputeEngineCloudOneShotInstanceIT {
  private static Logger log = Logger.getLogger(ComputeEngineCloudOneShotInstanceIT.class.getName());

  @ClassRule public static Timeout timeout = new Timeout(5, TimeUnit.MINUTES);
  @ClassRule public static JenkinsRule jenkinsRule = new JenkinsRule();

  private static ComputeClient client;
  private static Map<String, String> label = getLabel(ComputeEngineCloudOneShotInstanceIT.class);
  private static FreeStyleBuild build;

  @BeforeClass
  public static void init() throws Exception {
    log.info("init");
    initCredentials(jenkinsRule);
    ComputeEngineCloud cloud = initCloud(jenkinsRule);
    client = initClient(jenkinsRule, label, log);

    cloud.addConfiguration(
        instanceConfiguration(
            new InstanceConfiguration.Builder()
                .startupScript(DEB_JAVA_STARTUP_SCRIPT)
                .numExecutorsStr(NUM_EXECUTORS)
                .labels(LABEL)
                .oneShot(true)
                .createSnapshot(false)
                .template(NULL_TEMPLATE),
            label));
    jenkinsRule.jenkins.getNodesObject().setNodes(Collections.emptyList());
    assertTrue(jenkinsRule.jenkins.getNodes().isEmpty());

    FreeStyleProject project = jenkinsRule.createFreeStyleProject();
    Builder step = new Shell("echo works");
    project.getBuildersList().add(step);
    project.setAssignedLabel(new LabelAtom(LABEL));
    build = jenkinsRule.buildAndAssertSuccess(project);
  }

  @AfterClass
  public static void teardown() throws IOException {
    teardownResources(client, label, log);
  }

  @Test
  public void testOneShotInstanceLogContainsExpectedOutput() throws Exception {
    jenkinsRule.assertLogContains("works", build);
  }

  @Test
  public void testOneShotInstanceNodeDeletedFromJenkins() {
    Awaitility.await()
        .timeout(10, TimeUnit.SECONDS)
        .until(() -> jenkinsRule.jenkins.getNodes().isEmpty());
  }

  @Test
  public void testOneShotInstanceDeletedFromGCP() {
    Awaitility.await()
        .timeout(1, TimeUnit.MINUTES)
        .until(() -> client.getInstancesWithLabel(PROJECT_ID, label).isEmpty());
  }
}
