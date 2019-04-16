package com.google.jenkins.plugins.computeengine.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.api.services.compute.model.Snapshot;
import com.google.jenkins.plugins.computeengine.ComputeEngineCloud;
import com.google.jenkins.plugins.computeengine.ComputeEngineInstance;
import com.google.jenkins.plugins.computeengine.InstanceConfiguration;
import com.google.jenkins.plugins.computeengine.client.ComputeClient;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Node;
import hudson.model.Result;
import hudson.model.labels.LabelAtom;
import hudson.tasks.Builder;
import hudson.tasks.Shell;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.logging.StreamHandler;
import org.awaitility.Awaitility;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.jvnet.hudson.test.JenkinsRule;

public class ComputeEngineCloudSnapshotCreatedIT {
  private static Logger log = Logger.getLogger(ComputeEngineCloudSnapshotCreatedIT.class.getName());

  private static final String SNAPSHOT_LABEL = "snapshot";
  private static final int SNAPSHOT_TEST_TIMEOUT = 120;

  @ClassRule public static Timeout timeout = new Timeout(5, TimeUnit.MINUTES);
  @ClassRule public static JenkinsRule r = new JenkinsRule();

  private static ByteArrayOutputStream logOutput = new ByteArrayOutputStream();
  private static StreamHandler sh;
  private static ComputeClient client;
  private static Map<String, String> label =
      ITUtil.getLabel(ComputeEngineCloudSnapshotCreatedIT.class);
  private static Snapshot createdSnapshot = null;

  @BeforeClass
  public static void init() throws Exception {
    log.info("init");
    ITUtil.initCredentials(r);
    ComputeEngineCloud cloud = ITUtil.initCloud(r);
    client = ITUtil.initClient(r, label, log);
    sh = ITUtil.initLogging(logOutput);

    assertTrue(cloud.configurations.isEmpty());
    InstanceConfiguration ic =
        ITUtil.instanceConfiguration(
            ITUtil.DEB_JAVA_STARTUP_SCRIPT,
            ITUtil.NUM_EXECUTORS,
            SNAPSHOT_LABEL,
            label,
            true,
            true,
            ITUtil.NULL_TEMPLATE);
    cloud.addConfiguration(ic);
    assertTrue(
        ITUtil.logs(sh, logOutput),
        cloud.getInstanceConfig(ic.getDescription()).isCreateSnapshot());

    // Assert that there is 0 nodes
    assertTrue(r.jenkins.getNodes().isEmpty());

    FreeStyleProject project = r.createFreeStyleProject();
    Builder step = new Shell("exit 1");
    project.getBuildersList().add(step);
    project.setAssignedLabel(new LabelAtom(SNAPSHOT_LABEL));

    FreeStyleBuild build = r.assertBuildStatus(Result.FAILURE, project.scheduleBuild2(0));
    Node worker = build.getBuiltOn();
    assertNotNull(ITUtil.logs(sh, logOutput), worker);
    // Can not handle class logs for ComputeEngineInstance until an instance exists.
    ITUtil.handleClassLogs(sh, ComputeEngineInstance.class.getName());

    // Need time for one-shot instance to terminate and create the snapshot
    Awaitility.await()
        .timeout(SNAPSHOT_TEST_TIMEOUT, TimeUnit.SECONDS)
        .until(() -> r.jenkins.getNode(worker.getNodeName()) == null);

    createdSnapshot = client.getSnapshot(ITUtil.PROJECT_ID, worker.getNodeName());
  }

  @AfterClass
  public static void teardown() throws IOException {
    if (createdSnapshot != null) {
      client.deleteSnapshot(ITUtil.PROJECT_ID, createdSnapshot.getName());
    }
    ITUtil.teardown(sh, logOutput, client, label, log);
  }

  // Tests snapshot is created when we have failure builds for given node
  @Test
  public void testSnapshotCreatedNotNull() {
    assertNotNull(ITUtil.logs(sh, logOutput), createdSnapshot);
  }

  @Test
  public void testSnapshotCreatedStatusReady() {
    assertEquals(ITUtil.logs(sh, logOutput), "READY", createdSnapshot.getStatus());
  }

  @Test
  public void testSnapshotCreatedExpectedLogs() {
    assertTrue(ITUtil.logs(sh, logOutput), ITUtil.logs(sh, logOutput).contains(
        ComputeEngineInstance.CREATING_SNAPSHOT_FOR_NODE));
  }
}
