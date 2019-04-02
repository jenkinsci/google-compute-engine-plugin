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

package com.google.jenkins.plugins.computeengine.integration;

import static org.junit.Assert.assertNotNull;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.google.api.services.compute.model.Image;
import com.google.jenkins.plugins.computeengine.StringJsonServiceAccountConfig;
import com.google.jenkins.plugins.computeengine.client.ClientFactory;
import com.google.jenkins.plugins.computeengine.client.ComputeClient;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotPrivateKeyCredentials;
import com.google.jenkins.plugins.credentials.oauth.ServiceAccountConfig;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.logging.StreamHandler;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class ComputeClientIT {
  private static Logger log = Logger.getLogger(ComputeClientIT.class.getName());

  private static StreamHandler sh;
  private static ByteArrayOutputStream logOutput;
  private static ComputeClient client;

  @ClassRule public static JenkinsRule r = new JenkinsRule();

  @BeforeClass
  public static void init() throws Exception {
    log.info("init");
    logOutput = new ByteArrayOutputStream();
    sh = new StreamHandler(logOutput, new SimpleFormatter());

    // Add a service account credential
    String projectId = System.getenv("GOOGLE_PROJECT_ID");
    assertNotNull("GOOGLE_PROJECT_ID env var must be set", projectId);

    String serviceAccountKeyJson = System.getenv("GOOGLE_CREDENTIALS");
    assertNotNull("GOOGLE_CREDENTIALS env var must be set", serviceAccountKeyJson);

    ServiceAccountConfig sac = new StringJsonServiceAccountConfig(serviceAccountKeyJson);
    Credentials c = new GoogleRobotPrivateKeyCredentials(projectId, sac, null);

    CredentialsStore store = new SystemCredentialsProvider.ProviderImpl().getStore(r.jenkins);
    assertNotNull("Credentials store can not be null", store);
    store.addCredentials(Domain.global(), c);

    // Get a compute client for calls to GCE
    ClientFactory clientFactory = new ClientFactory(r.jenkins, new ArrayList<>(), projectId);
    client = clientFactory.compute();
    assertNotNull("ComputeClient can not be null", client);

    // Logging
    Logger clientLogger =
        LogManager.getLogManager()
            .getLogger("com.google.jenkins.plugins.computeengine.ComputeClient");
    if (clientLogger != null) clientLogger.addHandler(sh);
  }

  @AfterClass
  public static void teardown() {
    log.info("teardown");
    sh.flush();
    sh.close();
    log.info(logOutput.toString());
  }

  @Test
  public void testGetImage() throws Exception {
    Image i = client.getImage("debian-cloud", "debian-9-stretch-v20180820");
    sh.flush();
    assertNotNull(logOutput.toString(), i);
  }
}
