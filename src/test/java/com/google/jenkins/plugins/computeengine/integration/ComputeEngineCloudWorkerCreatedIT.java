package com.google.jenkins.plugins.computeengine.integration;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.google.api.services.compute.model.Instance;
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

public class ComputeEngineCloudWorkerCreatedIT {
  private static Logger log = Logger.getLogger(ComputeEngineCloudWorkerCreatedIT.class.getName());

  @ClassRule
  public static JenkinsRule r = new JenkinsRule();

  private static ByteArrayOutputStream logOutput = new ByteArrayOutputStream();
  private static StreamHandler sh = new StreamHandler(logOutput, new SimpleFormatter());
  private static ComputeClient client;
  private static Map<String, String> label = ITUtil.getLabel(ComputeEngineCloudWorkerCreatedIT.class);

  @BeforeClass
  public static void init() throws Exception {
     client = ITUtil.init(r, sh, label, log);
  }

  @AfterClass
  public static void teardown() throws IOException {
    ITUtil.teardown(sh, logOutput, client, label, log);
  }

  @Test(timeout = 300000)
  public void testWorkerCreated() throws Exception {
    ComputeEngineCloud cloud = (ComputeEngineCloud) r.jenkins.clouds.get(0);
    InstanceConfiguration ic = validInstanceConfiguration();
    cloud.addConfiguration(ic);
    // Add a new node
    Collection<PlannedNode> planned = cloud.provision(new LabelAtom(ITUtil.LABEL), 1);

    // There should be a planned node
    assertEquals(ITUtil.logs(sh, logOutput), 1, planned.size());

    String name = planned.iterator().next().displayName;

    // Wait for the node creation to finish
    planned.iterator().next().future.get();

    // There should be no warning logs
    assertFalse(ITUtil.logs(sh, logOutput), ITUtil.logs(sh, logOutput).contains("WARNING"));

    Instance i = cloud.getClient().getInstance(ITUtil.projectId, ITUtil.ZONE, name);

    // The created instance should have 3 labels
    assertEquals(ITUtil.logs(sh, logOutput), 3, i.getLabels().size());

    // Instance should have a label with key CONFIG_LABEL_KEY and value equal to the config's name prefix
    assertEquals(ITUtil.logs(sh, logOutput), ic.getNamePrefix(), i.getLabels().get(ComputeEngineCloud.CONFIG_LABEL_KEY));
    assertEquals(ITUtil.logs(sh, logOutput), cloud.getInstanceId(), i.getLabels().get(ComputeEngineCloud.CLOUD_ID_LABEL_KEY));
  }

  private static InstanceConfiguration validInstanceConfiguration() {
    return ITUtil.instanceConfiguration(ITUtil.DEB_JAVA_STARTUP_SCRIPT, ITUtil.NUM_EXECUTORS, ITUtil.LABEL, label, false, false, ITUtil.NULL_TEMPLATE);
  }
}
