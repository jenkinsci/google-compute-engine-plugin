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

package com.google.jenkins.plugins.computeengine.integration;

import static com.google.jenkins.plugins.computeengine.integration.ITUtil.CLOUD_NAME;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.LABEL;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.NULL_TEMPLATE;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.NUM_EXECUTORS;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.PROJECT_ID;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.SNAPSHOT_LABEL;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.ZONE;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initCredentials;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

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
import com.google.cloud.graphite.platforms.plugin.client.ClientFactory;
import com.google.cloud.graphite.platforms.plugin.client.ComputeClient;
import com.google.common.collect.ImmutableList;
import com.google.jenkins.plugins.computeengine.ComputeEngineCloud;
import com.google.jenkins.plugins.computeengine.InstanceConfiguration;
import com.google.jenkins.plugins.computeengine.WindowsConfiguration;
import com.google.jenkins.plugins.computeengine.client.ClientUtil;
import com.google.jenkins.plugins.computeengine.ssh.GoogleKeyPair;
import hudson.ProxyConfiguration;
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
import java.util.Optional;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.logging.StreamHandler;
import jenkins.model.Jenkins;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.jvnet.hudson.test.JenkinsRule;

/** Integration test for launching windows VM. Tests that agents can be created properly. */
public class ComputeEngineCloudWindowsIT {
  private static Logger log = Logger.getLogger(ComputeEngineCloudWindowsIT.class.getName());

  private static final String BOOT_DISK_SIZE_GB_STR = "50";
  private static final String RUN_AS_USER = "jenkins";

  private static Map<String, String> INTEGRATION_LABEL;

  static {
    INTEGRATION_LABEL = new HashMap<>();
    INTEGRATION_LABEL.put("integration", "delete");
  }

  private static final String RETENTION_TIME_MINUTES_STR = "600";
  private static final String LAUNCH_TIMEOUT_SECONDS_STR = "3000";

  private static Logger cloudLogger;
  private static Logger clientLogger;
  private static StreamHandler sh;
  private static ByteArrayOutputStream logOutput;

  private static ComputeClient client;
  private static String bootDiskProjectId;
  private static String bootDiskImageName;

  private static String publicKey;
  private static String windowsPrivateKeyCredentialId;

  @ClassRule public static JenkinsRule r = new JenkinsRule();
  @ClassRule public static Timeout timeout = new Timeout(20, TimeUnit.MINUTES);

  @BeforeClass
  public static void init() throws Exception {
    log.info("init");
    logOutput = new ByteArrayOutputStream();
    sh = new StreamHandler(logOutput, new SimpleFormatter());

    bootDiskProjectId = System.getenv("GOOGLE_BOOT_DISK_PROJECT_ID");
    assertNotNull("GOOGLE_BOOT_DISK_PROJECT_ID env var must be set", bootDiskProjectId);

    bootDiskImageName = System.getenv("GOOGLE_BOOT_DISK_IMAGE_NAME");
    assertNotNull("GOOGLE_BOOT_DISK_IMAGE_NAME env var must be set", bootDiskImageName);

    Credentials c = initCredentials(r);

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
    CredentialsStore store = new SystemCredentialsProvider.ProviderImpl().getStore(r.jenkins);
    store.addCredentials(Domain.global(), windowsPrivateKeyCredential);
    windowsPrivateKeyCredentialId = windowsPrivateKeyCredential.getId();

    // Add Cloud plugin
    ComputeEngineCloud gcp = new ComputeEngineCloud(CLOUD_NAME, PROJECT_ID, PROJECT_ID, "10");

    // Capture log output to make sense of most failures
    cloudLogger =
        LogManager.getLogManager()
            .getLogger("com.google.jenkins.plugins.computeengine.ComputeEngineCloud");
    if (cloudLogger != null) cloudLogger.addHandler(sh);

    assertEquals(0, r.jenkins.clouds.size());
    r.jenkins.clouds.add(gcp);
    assertEquals(1, r.jenkins.clouds.size());

    // Get a compute client for out-of-band calls to GCE
    ClientFactory clientFactory = ClientUtil.getClientFactory(r.jenkins, PROJECT_ID);
    client = clientFactory.computeClient();
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

  @After
  public void after() throws IOException {
    Jenkins jenkins = r.getInstance();
    jenkins.proxy = null;
    jenkins.save();
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

    Instance instance = cloud.getClient().getInstance(PROJECT_ID, ZONE, name);

    // The created instance should have 3 labels
    assertEquals(logs(), 3, instance.getLabels().size());

    // Instance should have a label with key CONFIG_LABEL_KEY and value equal to the config's name
    // prefix
    assertEquals(
        logs(), ic.getNamePrefix(), instance.getLabels().get(ComputeEngineCloud.CONFIG_LABEL_KEY));
    // proper id label to properly count instances
    assertEquals(
        logs(),
        cloud.getInstanceId(),
        instance.getLabels().get(ComputeEngineCloud.CLOUD_ID_LABEL_KEY));

    Optional<String> guestAttributes =
        instance.getMetadata().getItems().stream()
            .filter(
                item -> item.getKey().equals(InstanceConfiguration.GUEST_ATTRIBUTES_METADATA_KEY))
            .map(item -> item.getValue())
            .findFirst();
    assertTrue(guestAttributes.isPresent());
    assertEquals(guestAttributes.get(), "TRUE");
  }

  // Tests snapshot is created when we have failure builds for given node
  // Snapshot creation is longer for windows one-shot vm's.
  @Test(timeout = 800000)
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

      Snapshot createdSnapshot = client.getSnapshot(PROJECT_ID, worker.getNodeName());
      assertNotNull(logs(), createdSnapshot);
      assertEquals(logs(), createdSnapshot.getStatus(), "READY");
    } finally {
      try {
        // cleanup
        client.deleteSnapshotAsync(PROJECT_ID, worker.getNodeName());
      } catch (Exception e) {
      }
    }
  }

  // TODO(google-compute-engine-plugin/issues/49): Remove this test when refactoring windows tests.
  @Test(timeout = 500000)
  public void testIgnoreProxy() throws Exception {
    logOutput.reset();

    ComputeEngineCloud cloud = (ComputeEngineCloud) r.jenkins.clouds.get(0);
    Jenkins jenkins = r.getInstance();
    jenkins.proxy = new ProxyConfiguration("127.0.0.1", 8080);
    jenkins.proxy.save();
    jenkins.save();
    InstanceConfiguration instanceConfiguration = validInstanceConfiguration1(LABEL, false, true);
    instanceConfiguration.setIgnoreProxy(true);
    cloud.setConfigurations(ImmutableList.of(instanceConfiguration));

    FreeStyleProject project = r.createFreeStyleProject();
    Builder step = new BatchFile("echo works");
    project.getBuildersList().add(step);
    project.setAssignedLabel(new LabelAtom(LABEL));

    r.buildAndAssertSuccess(project);

    cloud.getConfigurations().get(0).setIgnoreProxy(false);
    Future<FreeStyleBuild> buildFuture = project.scheduleBuild2(0);
    FreeStyleBuild build;
    try {
      build = buildFuture.get(120, TimeUnit.SECONDS);
    } catch (TimeoutException e) {
      e.printStackTrace();
      return;
    }
    assertNotNull(build);
    assertEquals(Result.FAILURE, build.getResult());
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
            + "icacls $env:PROGRAMDATA\\ssh\\administrators_authorized_keys /inheritance:r\n"
            + "icacls $env:PROGRAMDATA\\ssh\\administrators_authorized_keys /grant SYSTEM:`(F`)\n"
            + "icacls $env:PROGRAMDATA\\ssh\\administrators_authorized_keys /grant BUILTIN\\Administrators:`(F`)\n"
            + "Restart-Service sshd";

    return ITUtil.instanceConfigurationBuilder()
        .numExecutorsStr(NUM_EXECUTORS)
        .startupScript(startupScript)
        .labels(labels)
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
        .retentionTimeMinutesStr(RETENTION_TIME_MINUTES_STR)
        .launchTimeoutSecondsStr(LAUNCH_TIMEOUT_SECONDS_STR)
        .oneShot(oneShot)
        .template(NULL_TEMPLATE)
        .googleLabels(INTEGRATION_LABEL)
        .build();
  }

  private static void deleteIntegrationInstances(boolean waitForCompletion) throws IOException {
    List<Instance> instances = client.listInstancesWithLabel(PROJECT_ID, INTEGRATION_LABEL);
    for (Instance i : instances) {
      safeDelete(i.getName(), waitForCompletion);
    }
  }

  private static void safeDelete(String instanceId, boolean waitForCompletion) {
    try {
      Operation op = client.terminateInstanceAsync(PROJECT_ID, ZONE, instanceId);
      if (waitForCompletion)
        client.waitForOperationCompletion(PROJECT_ID, op.getName(), op.getZone(), 3 * 60 * 1000);
    } catch (Exception e) {
      log.warning(String.format("Error deleting instance %s: %s", instanceId, e.getMessage()));
    }
  }

  private static String logs() {
    sh.flush();
    return logOutput.toString();
  }
}
