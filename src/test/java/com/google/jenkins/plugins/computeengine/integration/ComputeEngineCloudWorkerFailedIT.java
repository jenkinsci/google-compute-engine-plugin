package com.google.jenkins.plugins.computeengine.integration;

import static org.junit.Assert.assertEquals;

import com.google.jenkins.plugins.computeengine.ComputeEngineCloud;
import com.google.jenkins.plugins.computeengine.client.ComputeClient;
import hudson.model.labels.LabelAtom;
import hudson.slaves.NodeProvisioner.PlannedNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.logging.StreamHandler;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.jvnet.hudson.test.JenkinsRule;

public class ComputeEngineCloudWorkerFailedIT {
  private static Logger log = Logger.getLogger(ComputeEngineCloudWorkerFailedIT.class.getName());

  @ClassRule public static Timeout timeout = new Timeout(5, TimeUnit.MINUTES);
  @ClassRule public static JenkinsRule r = new JenkinsRule();

  private static ByteArrayOutputStream logOutput = new ByteArrayOutputStream();
  private static StreamHandler sh;
  private static ComputeClient client;
  private static Map<String, String> label =
      ITUtil.getLabel(ComputeEngineCloudWorkerFailedIT.class);
  private static Collection<PlannedNode> planned;

  @BeforeClass
  public static void init() throws Exception {
    log.info("init");
    ITUtil.initCredentials(r);
    ComputeEngineCloud cloud = ITUtil.initCloud(r);
    sh = ITUtil.initLogging(logOutput);
    client = ITUtil.initClient(r, label, log);

    // This configuration creates an instance with no Java installed.
    cloud.addConfiguration(
        ITUtil.instanceConfiguration(
            "", ITUtil.NUM_EXECUTORS, ITUtil.LABEL, label, false, false, ITUtil.NULL_TEMPLATE));

    // Add a new node
    planned = cloud.provision(new LabelAtom(ITUtil.LABEL), 1);
  }

  @AfterClass
  public static void teardown() throws IOException {
    ITUtil.teardown(sh, logOutput, client, label, log);
  }

  @Test
  public void testWorkerFailedNodePlanned() {
    // There should be a planned node
    assertEquals(ITUtil.logs(sh, logOutput), 1, planned.size());
  }

  @Test(expected = ExecutionException.class)
  public void testWorkerFailedCreationFails() throws Exception {
    // Wait for the node creation to fail
    planned.iterator().next().future.get();
  }
}
