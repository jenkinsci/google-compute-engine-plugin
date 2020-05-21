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
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.execute;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.getLabel;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initClient;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initCloud;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initCredentials;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.instanceConfigurationBuilder;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.teardownResources;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;

import com.google.cloud.graphite.platforms.plugin.client.ComputeClient;
import com.google.common.collect.ImmutableList;
import com.google.jenkins.plugins.computeengine.ComputeEngineCloud;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Node;
import hudson.model.Result;
import hudson.model.labels.LabelAtom;
import hudson.tasks.Builder;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.Future;
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
 * Integration test suite for {@link ComputeEngineCloud}. Verifies that one shot instances are
 * terminated after finishing a build and removed from both GCP and the set of Jenkins nodes.
 */
public class ComputeEngineCloudOneShotInstanceIT {
  private static Logger log = Logger.getLogger(ComputeEngineCloudOneShotInstanceIT.class.getName());
  private static final int QUIET_PERIOD_SECS = 30;

  @ClassRule
  public static Timeout timeout = new Timeout(10 * TEST_TIMEOUT_MULTIPLIER, TimeUnit.MINUTES);

  @ClassRule public static JenkinsRule jenkinsRule = new JenkinsRule();

  private static ComputeClient client;
  private static Map<String, String> label = getLabel(ComputeEngineCloudOneShotInstanceIT.class);
  private static FreeStyleBuild build;
  private static String nodeName;
  private static Future<FreeStyleBuild> otherBuildFuture;
  private static Node otherNode;

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
                .labels(LABEL)
                .oneShot(true)
                .createSnapshot(false)
                .template(NULL_TEMPLATE)
                .googleLabels(label)
                .build()));
    jenkinsRule.jenkins.getNodesObject().setNodes(Collections.emptyList());

    FreeStyleProject project = jenkinsRule.createFreeStyleProject();
    Builder step = execute(Commands.ECHO, "works");
    project.getBuildersList().add(step);
    project.setAssignedLabel(new LabelAtom(LABEL));
    Future<FreeStyleBuild> buildFuture = project.scheduleBuild2(0);

    FreeStyleProject otherProject = jenkinsRule.createFreeStyleProject();
    Builder otherStep = execute(Commands.ECHO, "\"also works\"");
    otherProject.getBuildersList().add(otherStep);
    otherProject.setAssignedLabel(new LabelAtom(LABEL));
    // Wait for a bit to make sure that this build finishes second.
    otherBuildFuture = otherProject.scheduleBuild2(QUIET_PERIOD_SECS);

    build = buildFuture.get();
    assertNotNull(build);
    assertEquals(Result.SUCCESS, build.getResult());
    nodeName = build.getBuiltOn().getNodeName();
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
        .until(() -> jenkinsRule.jenkins.getNode(nodeName) == null);
  }

  @Test
  public void testOneShotInstanceDeletedFromGCP() {
    Awaitility.await()
        .timeout(3, TimeUnit.MINUTES)
        .pollInterval(10, TimeUnit.SECONDS)
        .until(() -> client.listInstancesWithLabel(PROJECT_ID, label).isEmpty());
  }

  @Test
  public void testOtherInstanceSuccessful() throws Exception {
    FreeStyleBuild otherBuild = otherBuildFuture.get();
    assertNotNull(otherBuild);
    assertEquals(Result.SUCCESS, otherBuild.getResult());
    otherNode = otherBuild.getBuiltOn();
    jenkinsRule.assertLogContains("also works", otherBuild);
  }

  @Test
  public void testOtherInstanceRanOnDifferentNode() throws Exception {
    assertNotNull(otherNode);
    assertNotEquals(nodeName, otherNode.getNodeName());
  }
}
