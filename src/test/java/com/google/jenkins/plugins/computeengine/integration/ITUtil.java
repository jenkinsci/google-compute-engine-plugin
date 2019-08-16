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

import static com.google.cloud.graphite.platforms.plugin.client.util.ClientUtil.nameFromSelfLink;
import static com.google.common.collect.ImmutableList.of;
import static com.google.jenkins.plugins.computeengine.InstanceConfiguration.METADATA_LINUX_STARTUP_SCRIPT_KEY;
import static com.google.jenkins.plugins.computeengine.InstanceConfiguration.NAT_NAME;
import static com.google.jenkins.plugins.computeengine.InstanceConfiguration.NAT_TYPE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.SecretBytes;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.google.api.services.compute.model.AccessConfig;
import com.google.api.services.compute.model.AttachedDisk;
import com.google.api.services.compute.model.AttachedDiskInitializeParams;
import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.InstanceProperties;
import com.google.api.services.compute.model.InstanceTemplate;
import com.google.api.services.compute.model.Metadata;
import com.google.api.services.compute.model.NetworkInterface;
import com.google.api.services.compute.model.Operation;
import com.google.api.services.compute.model.Tags;
import com.google.cloud.graphite.platforms.plugin.client.ClientFactory;
import com.google.cloud.graphite.platforms.plugin.client.ComputeClient;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.jenkins.plugins.computeengine.AcceleratorConfiguration;
import com.google.jenkins.plugins.computeengine.AutofilledNetworkConfiguration;
import com.google.jenkins.plugins.computeengine.ComputeEngineCloud;
import com.google.jenkins.plugins.computeengine.InstanceConfiguration;
import com.google.jenkins.plugins.computeengine.client.ClientUtil;
import com.google.jenkins.plugins.computeengine.ssh.GoogleKeyPair;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotPrivateKeyCredentials;
import com.google.jenkins.plugins.credentials.oauth.JsonServiceAccountConfig;
import hudson.model.Node;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.apache.commons.lang.SystemUtils;
import org.jvnet.hudson.test.JenkinsRule;

/** Common logic and constants used throughout the integration tests. */
class ITUtil {
  static final String DEB_JAVA_STARTUP_SCRIPT =
      "#!/bin/bash\n"
          + "/etc/init.d/ssh stop\n"
          + "echo \"deb http://http.debian.net/debian stretch-backports main\" | \\\n"
          + "      sudo tee --append /etc/apt/sources.list > /dev/null\n"
          + "apt-get -y update\n"
          + "apt-get -y install -t stretch-backports openjdk-8-jdk\n"
          + "update-java-alternatives -s java-1.8.0-openjdk-amd64\n"
          + "/etc/init.d/ssh start";

  static final String PROJECT_ID = System.getenv("GOOGLE_PROJECT_ID");
  private static final String CREDENTIALS = loadCredentialsString();
  static final String CLOUD_NAME = "integration";
  private static final String NAME_PREFIX = "integration";
  private static final String REGION = format("projects/%s/regions/us-west1");
  static final String ZONE = "us-west1-a";
  private static final String ZONE_BASE = format("projects/%s/zones/" + ZONE);
  static final String LABEL = "integration";
  static final String SNAPSHOT_LABEL = "snapshot";
  private static final String MACHINE_TYPE = ZONE_BASE + "/machineTypes/n1-standard-1";
  static final String NUM_EXECUTORS = "1";
  private static final boolean PREEMPTIBLE = false;
  //  TODO: Write a test to see if min cpu platform worked by picking a higher version?
  private static final String MIN_CPU_PLATFORM = "Intel Broadwell";
  private static final String CONFIG_DESC = "integration";
  private static final String BOOT_DISK_TYPE = ZONE_BASE + "/diskTypes/pd-ssd";
  private static final boolean BOOT_DISK_AUTODELETE = true;
  private static final String BOOT_DISK_PROJECT_ID = "debian-cloud";
  private static final String BOOT_DISK_IMAGE_NAME =
      "projects/debian-cloud/global/images/family/debian-9";
  private static final String BOOT_DISK_SIZE_GB_STR = "10";
  private static final Node.Mode NODE_MODE = Node.Mode.EXCLUSIVE;
  private static final String ACCELERATOR_NAME = "";
  private static final String ACCELERATOR_COUNT = "";
  private static final String RUN_AS_USER = "jenkins";
  static final String NULL_TEMPLATE = null;
  private static final String NETWORK_NAME = format("projects/%s/global/networks/default");
  private static final String SUBNETWORK_NAME = "default";
  private static final boolean EXTERNAL_ADDR = true;
  private static final String NETWORK_TAGS = "ssh";
  private static final String SERVICE_ACCOUNT_EMAIL = "";
  private static final String RETENTION_TIME_MINUTES_STR = "";
  private static final String LAUNCH_TIMEOUT_SECONDS_STR = "";
  static final String SSH_USER = "test-user";
  private static final GoogleKeyPair SSH_KEY = GoogleKeyPair.generate(SSH_USER);
  static final String SSH_PRIVATE_KEY = SSH_KEY.getPrivateKey();
  static final int TEST_TIMEOUT_MULTIPLIER = SystemUtils.IS_OS_WINDOWS ? 3 : 1;

  static String format(String s) {
    assertNotNull("GOOGLE_PROJECT_ID env var must be set", PROJECT_ID);
    return String.format(s, PROJECT_ID);
  }

  private static String loadCredentialsString() {
    String creds = System.getenv("GOOGLE_CREDENTIALS");
    if (!Strings.isNullOrEmpty(creds)) {
      return creds;
    }
    // On windows, credentials must be read from a file
    String credsPath = System.getenv("GOOGLE_CREDENTIALS_FILE");
    assertFalse(
        "Credentials must be provided through environment variable or file path.",
        Strings.isNullOrEmpty(credsPath));
    try {
      creds = String.join("", Files.readAllLines(Paths.get(credsPath)));
    } catch (IOException ioe) {
      ioe.printStackTrace();
      fail("Could not read credentials from file path " + credsPath);
    }
    assertFalse(
        "GOOGLE_CREDENTIALS or GOOGLE_CREDENTIALS_FILE env vars must be set",
        Strings.isNullOrEmpty(creds));
    return creds;
  }

  static Credentials initCredentials(JenkinsRule r) throws Exception {
    SecretBytes bytes = SecretBytes.fromBytes(CREDENTIALS.getBytes(StandardCharsets.UTF_8));
    JsonServiceAccountConfig sac = new JsonServiceAccountConfig();
    sac.setSecretJsonKey(bytes);
    assertNotNull(sac.getAccountId());
    Credentials credentials = new GoogleRobotPrivateKeyCredentials(PROJECT_ID, sac, null);

    CredentialsStore store = new SystemCredentialsProvider.ProviderImpl().getStore(r.jenkins);
    assertNotNull("Credentials store can not be null", store);
    store.addCredentials(Domain.global(), credentials);
    return credentials;
  }

  // Add Cloud plugin
  static ComputeEngineCloud initCloud(JenkinsRule jenkinsRule) {
    ComputeEngineCloud gcp = new ComputeEngineCloud(CLOUD_NAME, PROJECT_ID, PROJECT_ID, "10");
    assertEquals(0, jenkinsRule.jenkins.clouds.size());
    jenkinsRule.jenkins.clouds.add(gcp);
    assertEquals(1, jenkinsRule.jenkins.clouds.size());
    return gcp;
  }

  // Get a compute client for out-of-band calls to GCE
  static ComputeClient initClient(JenkinsRule jenkinsRule, Map<String, String> label, Logger log)
      throws IOException {
    ClientFactory clientFactory = ClientUtil.getClientFactory(jenkinsRule.jenkins, PROJECT_ID);
    ComputeClient client = clientFactory.computeClient();
    assertNotNull("ComputeClient can not be null", client);
    deleteIntegrationInstances(true, client, label, log);
    return client;
  }

  static void teardownResources(ComputeClient client, Map<String, String> label, Logger log)
      throws IOException {
    log.info("teardown");
    // Client can be null if there is an exception in the initialization before it's set
    if (client != null) {
      deleteIntegrationInstances(false, client, label, log);
    }
  }

  static InstanceTemplate createTemplate(Map<String, String> googleLabels, String template) {
    InstanceTemplate instanceTemplate = new InstanceTemplate();
    instanceTemplate.setName(nameFromSelfLink(template));
    InstanceProperties instanceProperties = new InstanceProperties();
    instanceProperties.setMachineType(nameFromSelfLink(MACHINE_TYPE));
    instanceProperties.setLabels(googleLabels);
    AttachedDisk boot = new AttachedDisk();
    boot.setBoot(true);
    boot.setAutoDelete(BOOT_DISK_AUTODELETE);
    boot.setInitializeParams(
        new AttachedDiskInitializeParams()
            .setDiskSizeGb(Long.parseLong(BOOT_DISK_SIZE_GB_STR))
            .setDiskType(nameFromSelfLink(BOOT_DISK_TYPE))
            .setSourceImage(BOOT_DISK_IMAGE_NAME));
    instanceProperties.setDisks(of(boot));
    instanceProperties.setTags(new Tags().setItems(of(NETWORK_TAGS)));
    instanceProperties.setMetadata(
        new Metadata()
            .setItems(
                of(
                    new Metadata.Items()
                        .setKey(METADATA_LINUX_STARTUP_SCRIPT_KEY)
                        .setValue(DEB_JAVA_STARTUP_SCRIPT),
                    new Metadata.Items()
                        .setKey(InstanceConfiguration.SSH_METADATA_KEY)
                        .setValue(SSH_KEY.getPublicKey()))));
    instanceProperties.setNetworkInterfaces(
        of(
            new NetworkInterface()
                .setName(NETWORK_NAME)
                .setAccessConfigs(of(new AccessConfig().setType(NAT_TYPE).setName(NAT_NAME)))));
    instanceTemplate.setProperties(instanceProperties);
    return instanceTemplate;
  }

  static InstanceConfiguration.Builder instanceConfigurationBuilder() {
    return InstanceConfiguration.builder()
        .namePrefix(NAME_PREFIX)
        .region(REGION)
        .zone(ZONE)
        .machineType(MACHINE_TYPE)
        .preemptible(PREEMPTIBLE)
        .minCpuPlatform(MIN_CPU_PLATFORM)
        .description(CONFIG_DESC)
        .bootDiskType(BOOT_DISK_TYPE)
        .bootDiskAutoDelete(BOOT_DISK_AUTODELETE)
        .bootDiskSourceImageName(BOOT_DISK_IMAGE_NAME)
        .bootDiskSourceImageProject(BOOT_DISK_PROJECT_ID)
        .bootDiskSizeGbStr(BOOT_DISK_SIZE_GB_STR)
        .windowsConfiguration(null)
        .remoteFs(null)
        .networkConfiguration(new AutofilledNetworkConfiguration(NETWORK_NAME, SUBNETWORK_NAME))
        .externalAddress(EXTERNAL_ADDR)
        .useInternalAddress(false)
        .ignoreProxy(false)
        .networkTags(NETWORK_TAGS)
        .serviceAccountEmail(SERVICE_ACCOUNT_EMAIL)
        .retentionTimeMinutesStr(RETENTION_TIME_MINUTES_STR)
        .launchTimeoutSecondsStr(LAUNCH_TIMEOUT_SECONDS_STR)
        .mode(NODE_MODE)
        .acceleratorConfiguration(new AcceleratorConfiguration(ACCELERATOR_NAME, ACCELERATOR_COUNT))
        .runAsUser(RUN_AS_USER);
  }

  /*
   * GCE labels can only be lower case letters, numbers, or dashes.
   * Used to label the nodes created in a given testClass for deletion
   */
  static Map<String, String> getLabel(Class testClass) {
    return ImmutableMap.of(testClass.getSimpleName().toLowerCase(), "delete");
  }

  private static void deleteIntegrationInstances(
      boolean waitForCompletion, ComputeClient client, Map<String, String> label, Logger log)
      throws IOException {
    List<Instance> instances = client.listInstancesWithLabel(PROJECT_ID, label);
    for (Instance i : instances) {
      safeDelete(i.getName(), waitForCompletion, client, log);
    }
  }

  private static void safeDelete(
      String instanceId, boolean waitForCompletion, ComputeClient client, Logger log) {
    try {
      Operation operation = client.terminateInstanceAsync(PROJECT_ID, ZONE, instanceId);
      if (waitForCompletion) {
        client.waitForOperationCompletion(
            PROJECT_ID, operation.getName(), operation.getZone(), 3 * 60 * 1000);
      }
    } catch (Exception e) {
      log.warning(String.format("Error deleting instance %s: %s", instanceId, e.getMessage()));
    }
  }
}
