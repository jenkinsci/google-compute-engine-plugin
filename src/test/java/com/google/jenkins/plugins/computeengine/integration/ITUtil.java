package com.google.jenkins.plugins.computeengine.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.Operation;
import com.google.common.collect.ImmutableMap;
import com.google.jenkins.plugins.computeengine.AcceleratorConfiguration;
import com.google.jenkins.plugins.computeengine.AutofilledNetworkConfiguration;
import com.google.jenkins.plugins.computeengine.ComputeEngineCloud;
import com.google.jenkins.plugins.computeengine.InstanceConfiguration;
import com.google.jenkins.plugins.computeengine.StringJsonServiceAccountConfig;
import com.google.jenkins.plugins.computeengine.client.ClientFactory;
import com.google.jenkins.plugins.computeengine.client.ComputeClient;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotPrivateKeyCredentials;
import com.google.jenkins.plugins.credentials.oauth.ServiceAccountConfig;
import hudson.model.Node;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.logging.StreamHandler;
import org.jvnet.hudson.test.JenkinsRule;

class ITUtil {

  static final String DEB_JAVA_STARTUP_SCRIPT = "#!/bin/bash\n" +
      "/etc/init.d/ssh stop\n" +
      "echo \"deb http://http.debian.net/debian stretch-backports main\" | \\\n" +
      "      sudo tee --append /etc/apt/sources.list > /dev/null\n" +
      "apt-get -y update\n" +
      "apt-get -y install -t stretch-backports openjdk-8-jdk\n" +
      "update-java-alternatives -s java-1.8.0-openjdk-amd64\n" +
      "/etc/init.d/ssh start";

  private static final String CLOUD_NAME = "integration";
  private static final String NAME_PREFIX = "integration";
  private static final String REGION = format("projects/%s/regions/us-west1");
  static final String ZONE = "us-west1-a";
  private static final String ZONE_BASE = format("projects/%s/zones/" + ZONE);
  static final String LABEL = "integration";
  private static final String MACHINE_TYPE = ZONE_BASE + "/machineTypes/n1-standard-1";
  static final String NUM_EXECUTORS = "1";
  private static final boolean PREEMPTIBLE = false;
  //  TODO: Write a test to see if min cpu platform worked by picking a higher version?
  private static final String MIN_CPU_PLATFORM = "Intel Broadwell";
  private static final String CONFIG_DESC = "integration";
  private static final String BOOT_DISK_TYPE = ZONE_BASE + "/diskTypes/pd-ssd";
  private static final boolean BOOT_DISK_AUTODELETE = true;
  private static final String BOOT_DISK_PROJECT_ID = "debian-cloud";
  private static final String BOOT_DISK_IMAGE_NAME = "projects/debian-cloud/global/images/family/debian-9";
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

  static String projectId = format("%s");

  private static String format(String s) {
    String projectId = System.getenv("GOOGLE_PROJECT_ID");
    if (projectId == null) {
      throw new RuntimeException("GOOGLE_PROJECT_ID env var must be set");
    }
    return String.format(s, projectId);
  }

  static ComputeClient init(JenkinsRule r, StreamHandler sh, Map<String, String> label, Logger log) throws Exception {
    log.info("init");

    assertNotNull("GOOGLE_PROJECT_ID env var must be set", projectId);

    String serviceAccountKeyJson = System.getenv("GOOGLE_CREDENTIALS");
    assertNotNull("GOOGLE_CREDENTIALS env var must be set", serviceAccountKeyJson);

    ServiceAccountConfig sac = new StringJsonServiceAccountConfig(serviceAccountKeyJson);
    Credentials c = new GoogleRobotPrivateKeyCredentials(projectId, sac, null);

    CredentialsStore store = new SystemCredentialsProvider.ProviderImpl().getStore(r.jenkins);
    assertNotNull("Credentials store can not be null", store);
    store.addCredentials(Domain.global(), c);

    // Add Cloud plugin
    ComputeEngineCloud gcp = new ComputeEngineCloud(null, CLOUD_NAME, projectId, projectId, "10", null);

    // Capture log output to make sense of most failures
    Logger cloudLogger = LogManager.getLogManager().getLogger("com.google.jenkins.plugins.computeengine.ComputeEngineCloud");
    if (cloudLogger != null) {
      cloudLogger.addHandler(sh);
    }

    assertEquals(0, r.jenkins.clouds.size());
    r.jenkins.clouds.add(gcp);
    assertEquals(1, r.jenkins.clouds.size());

    // Get a compute client for out-of-band calls to GCE
    ClientFactory clientFactory = new ClientFactory(r.jenkins, new ArrayList<>(), projectId);
    ComputeClient client = clientFactory.compute();
    assertNotNull("ComputeClient can not be null", client);

    // Other logging
    Logger clientLogger = LogManager.getLogManager().getLogger("com.google.jenkins.plugins.computeengine.ComputeClient");
    if (clientLogger != null) {
      clientLogger.addHandler(sh);
    }

    deleteIntegrationInstances(true, client, label, log);
    return client;
  }

  static void teardown(StreamHandler sh, ByteArrayOutputStream logOutput, ComputeClient client, Map<String, String> label, Logger log) throws IOException {
    log.info("teardown");
    deleteIntegrationInstances(false, client, label, log);
    sh.close();
    log.info(logOutput.toString());
  }

  static InstanceConfiguration instanceConfiguration(String startupScript, String numExecutors, String jenkinsLabels, Map<String, String> label,
      boolean createSnapshot, boolean oneShot, String template) {
    InstanceConfiguration ic =
        new InstanceConfiguration.Builder()
            .namePrefix(NAME_PREFIX)
            .region(REGION)
            .zone(ZONE)
            .machineType(MACHINE_TYPE)
            .numExecutorsStr(numExecutors)
            .startupScript(startupScript)
            .preemptible(PREEMPTIBLE)
            .minCpuPlatform(MIN_CPU_PLATFORM)
            .labels(jenkinsLabels)
            .description(CONFIG_DESC)
            .bootDiskType(BOOT_DISK_TYPE)
            .bootDiskAutoDelete(BOOT_DISK_AUTODELETE)
            .bootDiskSourceImageName(BOOT_DISK_IMAGE_NAME)
            .bootDiskSourceImageProject(BOOT_DISK_PROJECT_ID)
            .bootDiskSizeGbStr(BOOT_DISK_SIZE_GB_STR)
            .windows(false)
            .windowsPasswordCredentialsId("")
            .windowsPrivateKeyCredentialsId("")
            .createSnapshot(createSnapshot)
            .remoteFs(null)
            .networkConfiguration(new AutofilledNetworkConfiguration(NETWORK_NAME, SUBNETWORK_NAME))
            .externalAddress(EXTERNAL_ADDR)
            .useInternalAddress(false)
            .networkTags(NETWORK_TAGS)
            .serviceAccountEmail(SERVICE_ACCOUNT_EMAIL)
            .retentionTimeMinutesStr(RETENTION_TIME_MINUTES_STR)
            .launchTimeoutSecondsStr(LAUNCH_TIMEOUT_SECONDS_STR)
            .mode(NODE_MODE)
            .acceleratorConfiguration(
                new AcceleratorConfiguration(ACCELERATOR_NAME, ACCELERATOR_COUNT))
            .runAsUser(RUN_AS_USER)
            .oneShot(oneShot)
            .template(template)
            .build();
    ic.appendLabels(label);
    return ic;
  }

  static Map<String, String> getLabel(Class testClass) {
    // GCE labels can only be lower case letters, numbers, or dashes
    return ImmutableMap.of(testClass.getSimpleName().toLowerCase(), "delete");
  }

  private static void deleteIntegrationInstances(boolean waitForCompletion, ComputeClient client, Map<String, String> label, Logger log) throws IOException {
    List<Instance> instances = client.getInstancesWithLabel(projectId, label);
    for (Instance i : instances) {
      safeDelete(i.getName(), waitForCompletion, client, log);
    }
  }

  private static void safeDelete(String instanceId, boolean waitForCompletion, ComputeClient client, Logger log) {
    try {
      Operation op = client.terminateInstance(projectId, ZONE, instanceId);
      if (waitForCompletion)
        client.waitForOperationCompletion(projectId, op.getName(), op.getZone(), 3 * 60 * 1000);
    } catch (Exception e) {
      log.warning(String.format("Error deleting instance %s: %s", instanceId, e.getMessage()));
    }
  }

  static String logs(StreamHandler sh, ByteArrayOutputStream logOutput) {
    sh.flush();
    return logOutput.toString();
  }
}
