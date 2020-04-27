/*
 * Copyright 2020 Elastic, Google LLC
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

import static com.google.jenkins.plugins.computeengine.integration.ITUtil.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeFalse;

import com.google.api.services.compute.model.Instance;
import com.google.cloud.graphite.platforms.plugin.client.ComputeClient;
import com.google.jenkins.plugins.computeengine.ComputeEngineCloud;
import hudson.model.FreeStyleProject;
import hudson.model.labels.LabelAtom;
import hudson.slaves.NodeProvisioner;
import hudson.tasks.Builder;
import io.jenkins.plugins.casc.ConfigurationAsCode;
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
 * Integration tests for Jenkins agents without any java installation that are configured with
 * Configuration as Code with the install java before the provisionign happens.
 */
public class ConfigAsCodeInstallJavaTestIT {
  private static Logger log = Logger.getLogger(ConfigAsCodeNonStandardJavaIT.class.getName());
  @ClassRule public static JenkinsRule jenkinsRule = new JenkinsRule();

  @ClassRule
  public static Timeout timeout = new Timeout(5 * TEST_TIMEOUT_MULTIPLIER, TimeUnit.MINUTES);

  private static ComputeClient client;
  private static Map<String, String> label = getLabel(ConfigAsCodeNonStandardJavaIT.class);

  @BeforeClass
  public static void init() throws Exception {
    assumeFalse(windows);
    log.info("init");
    initCredentials(jenkinsRule);
    client = initClient(jenkinsRule, label, log);
  }

  @AfterClass
  public static void teardown() throws IOException {
    teardownResources(client, label, log);
  }

  @Test
  public void testNonStandardJavaWorkerCreated() throws Exception {
    assumeFalse(windows);
    ConfigurationAsCode.get()
        .configure(
            this.getClass().getResource("configuration-as-code-install-java-it.yml").toString());
    ComputeEngineCloud cloud =
        (ComputeEngineCloud) jenkinsRule.jenkins.clouds.getByName("gce-integration");

    // Should be 1 configuration
    assertEquals(1, cloud.getConfigurations().size());
    cloud.getConfigurations().get(0).setGoogleLabels(label);

    // Add a new node
    Collection<NodeProvisioner.PlannedNode> planned =
        cloud.provision(new LabelAtom("integration-install-java"), 1);

    // There should be a planned node
    assertEquals(1, planned.size());
    String name = planned.iterator().next().displayName;

    // Wait for the node creation to finish
    planned.iterator().next().future.get();
    Instance instance = client.getInstance(PROJECT_ID, ZONE, name);
    assertNotNull(instance);

    FreeStyleProject project = jenkinsRule.createFreeStyleProject();
    Builder step = execute(Commands.ECHO, "works");
    project.getBuildersList().add(step);
    jenkinsRule.buildAndAssertSuccess(project);
  }
}
