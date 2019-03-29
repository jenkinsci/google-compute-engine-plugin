package com.google.jenkins.plugins.computeengine.integration;

import static org.junit.Assert.assertTrue;

import com.google.jenkins.plugins.computeengine.ComputeEngineCloud;
import com.google.jenkins.plugins.computeengine.client.ComputeClient;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.labels.LabelAtom;
import hudson.tasks.Builder;
import hudson.tasks.Shell;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
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

public class ComputeEngineCloudOneShotInstanceIT {
  private static Logger log = Logger.getLogger(ComputeEngineCloudOneShotInstanceIT.class.getName());

  @ClassRule public static Timeout timeout = new Timeout(5, TimeUnit.MINUTES);
  @ClassRule public static JenkinsRule r = new JenkinsRule();

  private static ByteArrayOutputStream logOutput = new ByteArrayOutputStream();
  private static StreamHandler sh;
  private static ComputeClient client;
  private static Map<String, String> label =
      ITUtil.getLabel(ComputeEngineCloudOneShotInstanceIT.class);
  private static FreeStyleBuild build;

  @BeforeClass
  public static void init() throws Exception {
    log.info("init");
    ITUtil.initCredentials(r);
    ComputeEngineCloud cloud = ITUtil.initCloud(r);
    sh = ITUtil.initLogging(logOutput);
    client = ITUtil.initClient(r, label, log);
    cloud.addConfiguration(
        ITUtil.instanceConfiguration(
            ITUtil.DEB_JAVA_STARTUP_SCRIPT,
            ITUtil.NUM_EXECUTORS,
            ITUtil.LABEL,
            label,
            false,
            true,
            ITUtil.NULL_TEMPLATE));

    r.jenkins.getNodesObject().setNodes(Collections.emptyList());

    // Assert that there is 0 nodes
    assertTrue(r.jenkins.getNodes().isEmpty());

    FreeStyleProject project = r.createFreeStyleProject();
    Builder step = new Shell("echo works");
    project.getBuildersList().add(step);
    project.setAssignedLabel(new LabelAtom(ITUtil.LABEL));

    // Enqueue a build of the project, wait for it to complete, and assert success
    build = r.buildAndAssertSuccess(project);
  }

  @AfterClass
  public static void teardown() throws IOException {
    ITUtil.teardown(sh, logOutput, client, label, log);
  }

  @Test
  public void testOneShotInstancesLogContainsExpectedOutput() throws Exception {
    // Assert that the console log contains the output we expect
    r.assertLogContains("works", build);
  }

  @Test
  public void testOneShotInstanceNodeDeleted() {
    // Assert that there is 0 nodes after job finished
    Awaitility.await().timeout(10, TimeUnit.SECONDS).until(() -> r.jenkins.getNodes().isEmpty());
  }
}
