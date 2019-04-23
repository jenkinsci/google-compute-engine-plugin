package com.google.jenkins.plugins.computeengine.integration;

import static com.google.jenkins.plugins.computeengine.integration.ITUtil.PROJECT_ID;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.ZONE;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.getLabel;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initClient;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initCredentials;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.teardownResources;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.google.api.services.compute.model.Instance;
import com.google.jenkins.plugins.computeengine.ComputeEngineCloud;
import com.google.jenkins.plugins.computeengine.client.ComputeClient;
import hudson.model.labels.LabelAtom;
import hudson.slaves.NodeProvisioner;
import io.jenkins.plugins.casc.ConfigurationAsCode;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.logging.Logger;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class ConfigAsCodeTestIT {
  private static Logger log = Logger.getLogger(ConfigAsCodeTestIT.class.getName());
  @ClassRule public static JenkinsRule jenkinsRule = new JenkinsRule();
  private static ComputeClient client;
  private static Map<String, String> label = getLabel(ComputeEngineCloudMultipleLabelsIT.class);

  @BeforeClass
  public static void init() throws Exception {
    log.info("init");
    initCredentials(jenkinsRule);
    client = initClient(jenkinsRule, label, log);
  }

  @AfterClass
  public static void teardown() throws IOException {
    teardownResources(client, label, log);
  }

  @Test(timeout = 300000)
  public void testWorkerCreated() throws Exception {
    ConfigurationAsCode.get()
        .configure(this.getClass().getResource("configuration-as-code-it.yml").toString());
    ComputeEngineCloud cloud =
        (ComputeEngineCloud) jenkinsRule.jenkins.clouds.getByName("gce-integration");

    // Should be 1 configuration
    assertEquals(1, cloud.getConfigurations().size());

    cloud.getConfigurations().get(0).googleLabels = label;

    // Add a new node
    Collection<NodeProvisioner.PlannedNode> planned =
        cloud.provision(new LabelAtom("integration"), 1);

    // There should be a planned node
    assertEquals(1, planned.size());

    String name = planned.iterator().next().displayName;

    // Wait for the node creation to finish
    planned.iterator().next().future.get();

    Instance instance = client.getInstance(PROJECT_ID, ZONE, name);

    assertNotNull(instance);
  }
}
