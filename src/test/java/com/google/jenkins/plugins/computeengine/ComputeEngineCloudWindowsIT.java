/*
 * Copyright 2018 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.jenkins.plugins.computeengine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.google.api.services.compute.model.Image;
import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.Operation;
import com.google.api.services.compute.model.Snapshot;
import com.google.common.collect.ImmutableList;
import com.google.graphite.platforms.plugin.client.ComputeClient;
import com.google.jenkins.plugins.computeengine.client.ClientFactory;
import com.google.jenkins.plugins.computeengine.ssh.GoogleKeyPair;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotPrivateKeyCredentials;
import com.google.jenkins.plugins.credentials.oauth.ServiceAccountConfig;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Node;
import hudson.model.Result;
import hudson.model.labels.LabelAtom;
import hudson.slaves.NodeProvisioner;
import hudson.tasks.BatchFile;
import hudson.tasks.Builder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.logging.StreamHandler;
import org.awaitility.Awaitility;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

/** Integration test for launching windows VM. Tests that agents can be created properly. */
public class ComputeEngineCloudWindowsIT {
  private static Logger log = Logger.getLogger(ComputeEngineCloudWindowsIT.class.getName());

  private static final String CLOUD_NAME = "integration";
  private static final String NAME_PREFIX = "integration";
  private static final String REGION = format("projects/%s/regions/us-west1");
  private static final String ZONE = "us-west1-a";
  private static final String ZONE_BASE = format("projects/%s/zones/" + ZONE);
  private static final String LABEL = "integration";
  private static final String SNAPSHOT_LABEL = "snapshot";
  private static final String MACHINE_TYPE = ZONE_BASE + "/machineTypes/n1-standard-1";
  private static final String NUM_EXECUTORS = "1";
  private static final boolean PREEMPTIBLE = false;
  private static final String CONFIG_DESC = "integration";
  private static final String MIN_CPU_PLATFORM = "Intel Broadwell";
  private static final String BOOT_DISK_TYPE = ZONE_BASE + "/diskTypes/pd-ssd"; // hmm
  private static final boolean BOOT_DISK_AUTODELETE = true;
  private static final String BOOT_DISK_SIZE_GB_STR = "50";
  private static final Node.Mode NODE_MODE = Node.Mode.EXCLUSIVE;
  private static final String ACCELERATOR_NAME = "";
  private static final String ACCELERATOR_COUNT = "";
  private static final String RUN_AS_USER = "jenkins";

  private static Map<String, String> INTEGRATION_LABEL;

  static {
    INTEGRATION_LABEL = new HashMap<String, String>();
    INTEGRATION_LABEL.put("integration", "delete");
  }

  private static final String NETWORK_NAME = format("projects/%s/global/networks/default");
  private static final String SUBNETWORK_NAME = "default";
  private static final boolean EXTERNAL_ADDR = true;
  private static final String NETWORK_TAGS = "ssh";
  private static final String SERVICE_ACCOUNT_EMAIL = "";
  private static final String RETENTION_TIME_MINUTES_STR = "600";
  private static final String LAUNCH_TIMEOUT_SECONDS_STR = "3000";

  private static Logger cloudLogger;
  private static Logger clientLogger;
  private static StreamHandler sh;
  private static ByteArrayOutputStream logOutput;

  private static ComputeClient client;
  private static String projectId;
  private static String bootDiskProjectId;
  private static String bootDiskImageName;

  private static String publicKey;
  private static String windowsPrivateKeyCredentialId;

  private static String format(String s) {
    String projectId = System.getenv("GOOGLE_PROJECT_ID");
    if (projectId == null) {
      throw new RuntimeException("GOOGLE_PROJECT_ID env var must be set");
    }
    return String.format(s, projectId);
  }

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

    bootDiskProjectId = System.getenv("GOOGLE_BOOT_DISK_PROJECT_ID");
    assertNotNull("GOOGLE_BOOT_DISK_PROJECT_ID env var must be set", bootDiskProjectId);

    bootDiskImageName = System.getenv("GOOGLE_BOOT_DISK_IMAGE_NAME");
    assertNotNull("GOOGLE_BOOT_DISK_IMAGE_NAME env var must be set", bootDiskImageName);

    ServiceAccountConfig sac = new StringJsonServiceAccountConfig(serviceAccountKeyJson);
    Credentials c = (Credentials) new GoogleRobotPrivateKeyCredentials(projectId, sac, null);

    CredentialsStore store = new SystemCredentialsProvider.ProviderImpl().getStore(r.jenkins);
    store.addCredentials(Domain.global(), c);

    // Create credentials for SSH
    GoogleKeyPair kp = GoogleKeyPair.generate("");
    // Have to reformat since GoogleKeyPair's format is for metadata server and not typical public
    // key format
    publicKey = kp.getPublicKey().trim().substring(1);

    StandardUsernameCredentials windowsPrivateKeyCredential =
        new BasicSSHUserPrivateKey(
            CredentialsScope.GLOBAL,
            null,
            RUN_AS_USER,
            new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource(kp.getPrivateKey()),
            null,
            "integration test private key for windows");
    store.addCredentials(Domain.global(), windowsPrivateKeyCredential);
    windowsPrivateKeyCredentialId = windowsPrivateKeyCredential.getId();

    // Add Cloud plugin
    ComputeEngineCloud gcp = new ComputeEngineCloud(CLOUD_NAME, projectId, projectId, "10");

    // Capture log output to make sense of most failures
    cloudLogger =
        LogManager.getLogManager()
            .getLogger("com.google.jenkins.plugins.computeengine.ComputeEngineCloud");
    if (cloudLogger != null) cloudLogger.addHandler(sh);

    assertEquals(0, r.jenkins.clouds.size());
    r.jenkins.clouds.add(gcp);
    assertEquals(1, r.jenkins.clouds.size());

    // Get a compute client for out-of-band calls to GCE
    ClientFactory clientFactory = new ClientFactory(r.jenkins, new ArrayList<>(), projectId);
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

  @Before
  public void before() {
    ComputeEngineCloud cloud = (ComputeEngineCloud) r.jenkins.clouds.get(0);
    cloud.setConfigurations(new ArrayList<>());
  }

  @Test
  public void testGoogleCredentialsCreated() {
    List<Credentials> creds =
        new SystemCredentialsProvider.ProviderImpl()
            .getStore(r.jenkins)
            .getCredentials(Domain.global());
    assertEquals(2, creds.size());
  }

  @Test // TODO: Group client tests into their own test class
  public void testGetImage() throws Exception {
    ComputeEngineCloud cloud = (ComputeEngineCloud) r.jenkins.clouds.get(0);
    Image i = cloud.getClient().getImage(this.bootDiskProjectId, this.bootDiskImageName);
    assertNotNull(i);
  }

  // TODO: JENKINS-56163 need to de-dupe integration tests
  @Test(timeout = 300000)
  public void testWorkerCreated() throws Exception {
    // TODO: each test method should probably have its own handler.
    logOutput.reset();

    InstanceConfiguration ic = validInstanceConfiguration1(LABEL, false, false);
    ComputeEngineCloud cloud = (ComputeEngineCloud) r.jenkins.clouds.get(0);
    cloud.setConfigurations(ImmutableList.of(ic));

    // Add a new node
    Collection<NodeProvisioner.PlannedNode> planned = cloud.provision(new LabelAtom(LABEL), 1);

    // There should be a planned node
    assertEquals(logs(), 1, planned.size());

    String name = planned.iterator().next().displayName;

    // Wait for the node creation to finish
    planned.iterator().next().future.get();

    // There should be no warning logs
    assertEquals(logs(), false, logs().contains("WARNING"));

    Instance i = cloud.getClient().getInstance(projectId, ZONE, name);

    // The created instance should have 3 labels
    assertEquals(logs(), 3, i.getLabels().size());

    // Instance should have a label with key CONFIG_LABEL_KEY and value equal to the config's name
    // prefix
    assertEquals(
        logs(), ic.getNamePrefix(), i.getLabels().get(ComputeEngineCloud.CONFIG_LABEL_KEY));
    // proper id label to properly count instances
    assertEquals(
        logs(), cloud.getInstanceId(), i.getLabels().get(ComputeEngineCloud.CLOUD_ID_LABEL_KEY));
  }

  // Tests snapshot is created when we have failure builds for given node
  // Snapshot creation is longer for windows one-shot vm's.
  @Test(timeout = 0)
  public void testSnapshotCreated() throws Exception {
    logOutput.reset();

    ComputeEngineCloud cloud = (ComputeEngineCloud) r.jenkins.clouds.get(0);
    cloud.setConfigurations(ImmutableList.of(snapshotInstanceConfiguration()));

    FreeStyleProject project = r.createFreeStyleProject();
    Builder step = new BatchFile("exit 1");
    project.getBuildersList().add(step);
    project.setAssignedLabel(new LabelAtom(SNAPSHOT_LABEL));

    FreeStyleBuild build = r.assertBuildStatus(Result.FAILURE, project.scheduleBuild2(0));
    Node worker = build.getBuiltOn();

    try {
      // Need time for one-shot instance to terminate and create the snapshot
      Awaitility.await()
          .timeout(15, TimeUnit.MINUTES)
          .until(
              () ->
                  r.jenkins.getNode(worker.getNodeName())
                      == null); // Assert that there is 0 nodes after job finished

      Snapshot createdSnapshot = client.getSnapshot(projectId, worker.getNodeName());
      assertNotNull(logs(), createdSnapshot);
      assertEquals(logs(), createdSnapshot.getStatus(), "READY");
    } finally {
      try {
        // cleanup
        client.deleteSnapshotAsync(projectId, worker.getNodeName());
      } catch (Exception e) {
      }
    }
  }

  /**
   * Creates an instance configuration for a node that will get a snapshot created upon deletion if
   * there is a build failure.
   *
   * @return InstanceConfiguration proper instance configuration to test snapshot creation.
   */
  private static InstanceConfiguration snapshotInstanceConfiguration() {
    return validInstanceConfiguration1(SNAPSHOT_LABEL, true, true);
  }

  /**
   * Given a job label and whether or not to create a snapshot upon deletion, gives working instance
   * configuration to launch an instance.
   *
   * @param labels What job label to run the instance on.
   * @param createSnapshot Whether or not to create a snapshot for the provisioned instance upon
   *     deletion.
   * @return InstanceConfiguration working instance configuration to provision an instance.
   */
  private static InstanceConfiguration validInstanceConfiguration1(
      String labels, boolean createSnapshot, boolean oneShot) {

    String startupScript =
        "Stop-Service sshd\n"
            + "$ConfiguredPublicKey = "
            + "\""
            + publicKey
            + "\"\n"
            + "Write-Output \"Second phase\"\n"
            + "# We are in the second phase of startup where we need to set up authorized_keys for the specified user.\n"
            + "# Create the .ssh folder and authorized_keys file.\n"
            + "Set-Content -Path $env:PROGRAMDATA\\ssh\\administrators_authorized_keys -Value $ConfiguredPublicKey\n"
            + "icacls $env:PROGRAMDATA\\ssh\\administrators_authorized_keys /inheritance:jenkinsRule\n"
            + "icacls $env:PROGRAMDATA\\ssh\\administrators_authorized_keys /grant SYSTEM:`(F`)\n"
            + "icacls $env:PROGRAMDATA\\ssh\\administrators_authorized_keys /grant BUILTIN\\Administrators:`(F`)\n"
            + "Restart-Service sshd";

    InstanceConfiguration ic =
        InstanceConfiguration.builder()
            .namePrefix(NAME_PREFIX)
            .region(REGION)
            .zone(ZONE)
            .machineType(MACHINE_TYPE)
            .numExecutorsStr(NUM_EXECUTORS)
            .startupScript(startupScript)
            .preemptible(PREEMPTIBLE)
            .minCpuPlatform(MIN_CPU_PLATFORM)
            .labels(labels)
            .description(CONFIG_DESC)
            .bootDiskType(BOOT_DISK_TYPE)
            .bootDiskAutoDelete(BOOT_DISK_AUTODELETE)
            .bootDiskSourceImageName(
                "projects/" + bootDiskProjectId + "/global/images/" + bootDiskImageName)
            .bootDiskSourceImageProject(bootDiskProjectId)
            .bootDiskSizeGbStr(BOOT_DISK_SIZE_GB_STR)
            .windowsConfiguration(
                WindowsConfiguration.builder()
                    .passwordCredentialsId("")
                    .privateKeyCredentialsId(windowsPrivateKeyCredentialId)
                    .build())
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
            .template(null)
            .build();
    ic.appendLabels(INTEGRATION_LABEL);
    return ic;
  }

  private static void deleteIntegrationInstances(boolean waitForCompletion) throws IOException {
    List<Instance> instances = client.listInstancesWithLabel(projectId, INTEGRATION_LABEL);
    for (Instance i : instances) {
      safeDelete(i.getName(), waitForCompletion);
    }
  }

  private static void safeDelete(String instanceId, boolean waitForCompletion) {
    try {
      Operation op = client.terminateInstanceAsync(projectId, ZONE, instanceId);
      if (waitForCompletion)
        client.waitForOperationCompletion(projectId, op.getName(), op.getZone(), 3 * 60 * 1000);
    } catch (Exception e) {
      log.warning(String.format("Error deleting instance %s: %s", instanceId, e.getMessage()));
    }
  }

  private static String logs() {
    sh.flush();
    return logOutput.toString();
  }
}
