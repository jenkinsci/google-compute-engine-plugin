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
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.TEST_TIMEOUT_MULTIPLIER;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.ZONE;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.createTemplate;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.format;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.getLabel;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initClient;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initCloud;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initCredentials;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.instanceConfigurationBuilder;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.teardownResources;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.api.services.compute.model.AccessConfig;
import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.InstanceTemplate;
import com.google.api.services.compute.model.NetworkInterface;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.jenkins.plugins.computeengine.ComputeEngineCloud;
import com.google.jenkins.plugins.computeengine.InstanceConfiguration;
import com.google.jenkins.plugins.computeengine.client.ComputeClient;
import com.trilead.ssh2.Connection;
import com.trilead.ssh2.ServerHostKeyVerifier;
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
 * Integration test suite for {@link ComputeEngineCloud}. Verifies that instances can be created
 * using an instance template, and are provisioned with the labels configured on that template.
 */
public class ComputeEngineCloudTemplateIT {
  private static Logger log = Logger.getLogger(ComputeEngineCloudTemplateIT.class.getName());

  private static final String TEMPLATE =
      format("projects/%s/global/instanceTemplates/test-template");
  private static final String GOOGLE_LABEL_KEY = "test-label";
  private static final String GOOGLE_LABEL_VALUE = "test-value";
  private static final Integer SSH_PORT = 22;
  private static final Integer SSH_TIMEOUT = 10000;

  @ClassRule
  public static Timeout timeout = new Timeout(5 * TEST_TIMEOUT_MULTIPLIER, TimeUnit.MINUTES);

  @ClassRule public static JenkinsRule jenkinsRule = new JenkinsRule();

  private static ComputeClient client;
  private static Map<String, String> label = getLabel(ComputeEngineCloudTemplateIT.class);
  private static ComputeEngineCloud cloud;
  private static Instance instance;

  @BeforeClass
  public static void init() throws Exception {
    log.info("init");
    initCredentials(jenkinsRule);
    cloud = initCloud(jenkinsRule);
    client = initClient(jenkinsRule, label, log);

    cloud.setConfigurations(
        ImmutableList.of(
            instanceConfigurationBuilder()
                .startupScript(DEB_JAVA_STARTUP_SCRIPT)
                .numExecutorsStr(NUM_EXECUTORS)
                .labels(LABEL)
                .oneShot(false)
                .createSnapshot(false)
                .template(TEMPLATE)
                .googleLabels(label)
                .build()));

    InstanceTemplate instanceTemplate =
        createTemplate(ImmutableMap.of(GOOGLE_LABEL_KEY, GOOGLE_LABEL_VALUE), TEMPLATE);
    // ensure an existing template by the same name doesn't already exist
    try {
      client.deleteTemplate(cloud.getProjectId(), instanceTemplate.getName());
    } catch (IOException ioe) {
      // noop
    }
    client.insertTemplate(cloud.getProjectId(), instanceTemplate);
    Collection<PlannedNode> planned = cloud.provision(new LabelAtom(LABEL), 1);
    String name = planned.iterator().next().displayName;

    planned.iterator().next().future.get();
    instance = client.getInstance(PROJECT_ID, ZONE, name);
  }

  @AfterClass
  public static void teardown() throws IOException {
    try {
      client.deleteTemplate(cloud.getProjectId(), nameFromSelfLink(TEMPLATE));
    } catch (Exception e) {
      // noop
    }

    teardownResources(client, label, log);
  }

  @Test
  public void testTemplateGoogleLabelKeyAndValue() {
    assertEquals(GOOGLE_LABEL_VALUE, instance.getLabels().get(GOOGLE_LABEL_KEY));
  }

  @Test
  public void testTemplateCloudIdLabelKeyAndValue() {
    assertEquals(
        cloud.getInstanceId(), instance.getLabels().get(ComputeEngineCloud.CLOUD_ID_LABEL_KEY));
  }

  @Test
  public void testConnectionWithTemplateSshKey() throws IOException, Exception {
    Connection conn = null;
    try {
      NetworkInterface nic = instance.getNetworkInterfaces().get(0);
      String host = "";
      if (nic.getAccessConfigs() != null) {
        for (AccessConfig ac : nic.getAccessConfigs()) {
          if (ac.getType().equals(InstanceConfiguration.NAT_TYPE)) {
            host = ac.getNatIP();
          }
        }
      }
      int port = SSH_PORT;
      conn = new Connection(host, port);
      conn.connect(
          new ServerHostKeyVerifier() {
            public boolean verifyServerHostKey(
                String hostname, int port, String serverHostKeyAlgorithm, byte[] serverHostKey)
                throws Exception {
              return true;
            }
          },
          SSH_TIMEOUT,
          SSH_TIMEOUT);
      assertTrue(
          conn.authenticateWithPublicKey(
              ITUtil.SSH_USER, ITUtil.SSH_PRIVATE_KEY.toCharArray(), ""));
    } finally {
      if (conn != null) {
        conn.close();
      }
    }
  }
}
