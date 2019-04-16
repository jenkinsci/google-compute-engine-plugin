/*
 * Copyright 2018 Google LLC
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

package com.google.jenkins.plugins.computeengine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.google.api.services.compute.model.Image;
import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.Operation;
import com.google.jenkins.plugins.computeengine.client.ClientFactory;
import com.google.jenkins.plugins.computeengine.client.ComputeClient;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotPrivateKeyCredentials;
import com.google.jenkins.plugins.credentials.oauth.ServiceAccountConfig;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.logging.StreamHandler;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class ComputeEngineCloudIT {
  private static Logger log = Logger.getLogger(ComputeEngineCloudIT.class.getName());

  private static final String CLOUD_NAME = "integration";
  private static final String ZONE = "us-west1-a";

  private static Map<String, String> INTEGRATION_LABEL;

  static {
    INTEGRATION_LABEL = new HashMap<>();
    INTEGRATION_LABEL.put("integration", "delete");
  }

  private static Logger cloudLogger;
  private static Logger clientLogger;
  private static StreamHandler sh;
  private static ByteArrayOutputStream logOutput;

  private static ComputeClient client;
  private static String projectId;

  @ClassRule public static JenkinsRule r = new JenkinsRule();

  @BeforeClass
  public static void init() throws Exception {
    log.info("init");
    logOutput = new ByteArrayOutputStream();
    sh = new StreamHandler(logOutput, new SimpleFormatter());

    // Add a service account credential
    projectId = System.getenv("GOOGLE_PROJECT_ID");
    assertNotNull("GOOGLE_PROJECT_ID env var must be set", projectId);

    String serviceAccountKeyJson = System.getenv("GOOGLE_CREDENTIALS");
    assertNotNull("GOOGLE_CREDENTIALS env var must be set", serviceAccountKeyJson);

    ServiceAccountConfig sac = new StringJsonServiceAccountConfig(serviceAccountKeyJson);
    Credentials c = (Credentials) new GoogleRobotPrivateKeyCredentials(projectId, sac, null);

    CredentialsStore store = new SystemCredentialsProvider.ProviderImpl().getStore(r.jenkins);
    store.addCredentials(Domain.global(), c);

    // Add Cloud plugin
    ComputeEngineCloud gcp =
        new ComputeEngineCloud(null, CLOUD_NAME, projectId, projectId, "10", null);

    // Capture log output to make sense of most failures
    cloudLogger =
        LogManager.getLogManager()
            .getLogger("com.google.jenkins.plugins.computeengine.ComputeEngineCloud");
    if (cloudLogger != null) cloudLogger.addHandler(sh);

    assertEquals(0, r.jenkins.clouds.size());
    r.jenkins.clouds.add(gcp);
    assertEquals(1, r.jenkins.clouds.size());

    // Get a compute client for out-of-band calls to GCE
    ClientFactory clientFactory =
        new ClientFactory(r.jenkins, new ArrayList<DomainRequirement>(), projectId);
    client = clientFactory.compute();
    assertNotNull("ComputeClient can not be null", client);

    // Other logging
    clientLogger =
        LogManager.getLogManager()
            .getLogger("com.google.jenkins.plugins.computeengine.ComputeClient");
    if (clientLogger != null) clientLogger.addHandler(sh);

    deleteIntegrationInstances(true);
  }

  @AfterClass
  public static void teardown() throws Exception {
    log.info("teardown");
    deleteIntegrationInstances(false);
    sh.close();
    log.info(logOutput.toString());
  }

  @After
  public void after() {
    ComputeEngineCloud cloud = (ComputeEngineCloud) r.jenkins.clouds.get(0);
    cloud.configurations.clear();
  }

  @Test
  public void testCredentialsCreated() {
    List<Credentials> creds =
        new SystemCredentialsProvider.ProviderImpl()
            .getStore(r.jenkins)
            .getCredentials(Domain.global());
    assertEquals(1, creds.size());
  }

  @Test // TODO: Group client tests into their own test class
  public void testGetImage() throws Exception {
    ComputeEngineCloud cloud = (ComputeEngineCloud) r.jenkins.clouds.get(0);
    Image i = cloud.client.getImage("debian-cloud", "debian-9-stretch-v20180820");
    assertNotNull(i);
  }

  private static void deleteIntegrationInstances(boolean waitForCompletion) throws IOException {
    List<Instance> instances = client.getInstancesWithLabel(projectId, INTEGRATION_LABEL);
    for (Instance i : instances) {
      safeDelete(i.getName(), waitForCompletion);
    }
  }

  private static void safeDelete(String instanceId, boolean waitForCompletion) {
    try {
      Operation op = client.terminateInstance(projectId, ZONE, instanceId);
      if (waitForCompletion)
        client.waitForOperationCompletion(projectId, op.getName(), op.getZone(), 3 * 60 * 1000);
    } catch (Exception e) {
      log.warning(String.format("Error deleting instance %s: %s", instanceId, e.getMessage()));
    }
  }
}
