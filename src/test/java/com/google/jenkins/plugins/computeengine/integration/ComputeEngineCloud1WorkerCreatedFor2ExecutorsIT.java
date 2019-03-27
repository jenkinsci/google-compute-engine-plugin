package com.google.jenkins.plugins.computeengine.integration;

import static org.junit.Assert.assertEquals;

import com.google.jenkins.plugins.computeengine.ComputeEngineCloud;
import com.google.jenkins.plugins.computeengine.InstanceConfiguration;
import com.google.jenkins.plugins.computeengine.client.ComputeClient;
import hudson.model.labels.LabelAtom;
import hudson.slaves.NodeProvisioner.PlannedNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.logging.StreamHandler;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class ComputeEngineCloud1WorkerCreatedFor2ExecutorsIT {
  private static Logger log = Logger.getLogger(ComputeEngineCloud1WorkerCreatedFor2ExecutorsIT.class.getName());

  private static final String MULTIPLE_NUM_EXECUTORS = "2";

  @ClassRule
  public static JenkinsRule r = new JenkinsRule();

  private static ByteArrayOutputStream logOutput = new ByteArrayOutputStream();
  private static StreamHandler sh = new StreamHandler(logOutput, new SimpleFormatter());
  private static ComputeClient client;
  private static Map<String, String> label = ITUtil.getLabel(ComputeEngineCloud1WorkerCreatedFor2ExecutorsIT.class);

  @BeforeClass
  public static void init() throws Exception {
    client = ITUtil.init(r, sh, label, log);
  }

  @AfterClass
  public static void teardown() throws IOException {
    ITUtil.teardown(sh, logOutput, client, label, log);
  }


  @Test(timeout = 300000)
  public void test1WorkerCreatedFor2Executors() {
    ComputeEngineCloud cloud = (ComputeEngineCloud) r.jenkins.clouds.get(0);
    cloud.addConfiguration(validInstanceConfigurationWithExecutors(MULTIPLE_NUM_EXECUTORS));
    // Add a new node
    Collection<PlannedNode> planned = cloud.provision(new LabelAtom(ITUtil.LABEL), 2);

    // There should be a planned node
    assertEquals(ITUtil.logs(sh, logOutput), 1, planned.size());
  }

  private static InstanceConfiguration validInstanceConfigurationWithExecutors(String numExecutors) {
    return ITUtil.instanceConfiguration(ITUtil.DEB_JAVA_STARTUP_SCRIPT, numExecutors, ITUtil.LABEL, label, false, false, ITUtil.NULL_TEMPLATE);
  }
}
