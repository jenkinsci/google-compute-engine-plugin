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
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.TEST_TIMEOUT_MULTIPLIER;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.execute;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.getLabel;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initClient;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initCloud;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initCredentials;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.instanceConfigurationBuilder;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.teardownResources;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.cloud.graphite.platforms.plugin.client.ComputeClient;
import com.google.common.collect.ImmutableList;
import com.google.jenkins.plugins.computeengine.ComputeEngineCloud;
import com.google.jenkins.plugins.computeengine.ComputeEngineInstance;
import com.google.jenkins.plugins.computeengine.InstanceConfiguration;
import hudson.ProxyConfiguration;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Node;
import hudson.model.labels.LabelAtom;
import hudson.tasks.Builder;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import jenkins.model.Jenkins;
import lombok.extern.java.Log;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Integration test suite for {@link ComputeEngineCloud}. Verifies that when the ignore proxy flag
 * is set to true, connections to GCE VMs bypass the proxy, and when set to false the proxy is used.
 */
@Log
public class ComputeEngineCloudIgnoreProxyIT {
  @ClassRule
  public static Timeout timeout = new Timeout(10 * TEST_TIMEOUT_MULTIPLIER, TimeUnit.MINUTES);

  @ClassRule public static JenkinsRule jenkinsRule = new JenkinsRule();

  private static ComputeClient client;
  private static Map<String, String> label = getLabel(ComputeEngineCloudIgnoreProxyIT.class);
  private static ComputeEngineCloud cloud;
  private static FreeStyleProject project;
  private static Node worker;

  @BeforeClass
  public static void init() throws Exception {
    log.info("init");
    initCredentials(jenkinsRule);
    cloud = initCloud(jenkinsRule);
    client = initClient(jenkinsRule, label, log);

    InstanceConfiguration instanceConfiguration =
        instanceConfigurationBuilder()
            .numExecutorsStr(NUM_EXECUTORS)
            .labels(LABEL)
            .template(NULL_TEMPLATE)
            .googleLabels(label)
            .oneShot(true)
            .ignoreProxy(true)
            .build();

    Jenkins jenkins = jenkinsRule.getInstance();
    jenkins.proxy = new ProxyConfiguration("127.0.0.1", 8080);
    jenkins.proxy.save();
    jenkins.save();
    cloud.setConfigurations(ImmutableList.of(instanceConfiguration));
    assertTrue(
        cloud
            .getInstanceConfigurationByDescription(instanceConfiguration.getDescription())
            .isIgnoreProxy());

    project = jenkinsRule.createFreeStyleProject();
    Builder step = execute(Commands.ECHO, "works");
    project.getBuildersList().add(step);
    project.setAssignedLabel(new LabelAtom(LABEL));

    FreeStyleBuild build = jenkinsRule.buildAndAssertSuccess(project);
    worker = build.getBuiltOn();
    assertNotNull(worker);
  }

  @AfterClass
  public static void teardown() throws IOException {
    teardownResources(client, label, log);
  }

  @Test
  public void testWorkerIgnoringProxy() {
    ComputeEngineInstance node = (ComputeEngineInstance) worker;
    assertTrue(node.isIgnoreProxy());
  }

  // Due to the proxy configured for Jenkins, the build fails because it is not able to connect to
  // the node through the proxy.
  @Test(expected = TimeoutException.class)
  public void testWorkerNotIgnoringProxyFails() throws Exception {
    cloud.getConfigurations().get(0).setIgnoreProxy(false);
    Future<FreeStyleBuild> build = project.scheduleBuild2(0);
    build.get(100, TimeUnit.SECONDS);
  }
}
