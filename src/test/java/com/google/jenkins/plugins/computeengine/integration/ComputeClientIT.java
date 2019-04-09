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

import static com.google.jenkins.plugins.computeengine.integration.ITUtil.getLabel;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.handleClassLogs;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initClient;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initCredentials;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.teardownResources;
import static org.junit.Assert.assertNotNull;

import com.google.api.services.compute.model.Image;
import com.google.jenkins.plugins.computeengine.client.ComputeClient;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
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

  private static ByteArrayOutputStream logOutput = new ByteArrayOutputStream();
  private static StreamHandler streamHandler = new StreamHandler(logOutput, new SimpleFormatter());
  private static Map<String, String> label = getLabel(ComputeClientIT.class);
  private static ComputeClient client;

  @ClassRule public static JenkinsRule jenkinsRule = new JenkinsRule();

  @BeforeClass
  public static void init() throws Exception {
    log.info("init");
    initCredentials(jenkinsRule);

    // Get a compute client for calls to GCE
    client = initClient(jenkinsRule, label, log);

    // Logging
    handleClassLogs(streamHandler, ComputeClient.class.getName());
  }

  @AfterClass
  public static void teardown() throws IOException {
    teardownResources(streamHandler, logOutput, client, label, log);
  }

  @Test
  public void testGetImage() throws Exception {
    Image image = client.getImage("debian-cloud", "debian-9-stretch-v20180820");
    streamHandler.flush();
    assertNotNull(logOutput.toString(), image);
  }
}
