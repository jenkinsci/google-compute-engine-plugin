package com.google.jenkins.plugins.computeengine.integration;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.jenkins.plugins.computeengine.ComputeEngineCloud;
import com.google.jenkins.plugins.computeengine.ComputeEngineInstance;
import com.google.jenkins.plugins.computeengine.InstanceConfiguration;
import com.google.jenkins.plugins.computeengine.client.ComputeClient;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Node;
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

public class ComputeEngineCloudNoSnapshotCreatedIT {
  private static Logger log =
      Logger.getLogger(ComputeEngineCloudNoSnapshotCreatedIT.class.getName());

  private static final String SNAPSHOT_LABEL = "snapshot";
  private static final int SNAPSHOT_TEST_TIMEOUT = 120;

  @ClassRule public static Timeout timeout = new Timeout(5, TimeUnit.MINUTES);
  @ClassRule public static JenkinsRule r = new JenkinsRule();

  private static ByteArrayOutputStream logOutput = new ByteArrayOutputStream();
  private static StreamHandler sh;
  private static ComputeClient client;
  private static Map<String, String> label =
      ITUtil.getLabel(ComputeEngineCloudNoSnapshotCreatedIT.class);
  private static String name;

  @BeforeClass
  public static void init() throws Exception {
    log.info("init");
    ITUtil.initCredentials(r);
    ComputeEngineCloud cloud = ITUtil.initCloud(r);
    sh = ITUtil.initLogging(logOutput);
    client = ITUtil.initClient(r, label, log);

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
    Builder step = new Shell("echo works");
    project.getBuildersList().add(step);
    project.setAssignedLabel(new LabelAtom(SNAPSHOT_LABEL));

    FreeStyleBuild build = r.buildAndAssertSuccess(project);
    Node worker = build.getBuiltOn();
    assertNotNull(ITUtil.logs(sh, logOutput), worker);
    // Can not handle class logs for ComputeEngineInstance until an instance exists.
    ITUtil.handleClassLogs(sh, ComputeEngineInstance.class.getName());

    name = worker.getNodeName();

    // Need time for one-shot instance to terminate and create the snapshot
    Awaitility.await()
        .timeout(SNAPSHOT_TEST_TIMEOUT, TimeUnit.SECONDS)
        .until(() -> r.jenkins.getNode(worker.getNodeName()) == null);
  }

  @AfterClass
  public static void teardown() throws IOException {
    ITUtil.teardown(sh, logOutput, client, label, log);
  }

  // Tests that no snapshot is created when we only have successful builds for given node
  @Test
  public void testNoSnapshotCreatedSnapshotNull() throws Exception {
    assertNull(ITUtil.logs(sh, logOutput), client.getSnapshot(ITUtil.PROJECT_ID, name));
  }

  @Test
  public void testNoSnapshotCreatedExpectedLogs() {
    assertFalse(
        ITUtil.logs(sh, logOutput),
        ITUtil.logs(sh, logOutput).contains(ComputeEngineInstance.CREATING_SNAPSHOT_FOR_NODE));
  }
}
