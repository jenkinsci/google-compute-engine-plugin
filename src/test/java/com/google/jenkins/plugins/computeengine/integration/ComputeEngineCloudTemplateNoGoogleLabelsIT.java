package com.google.jenkins.plugins.computeengine.integration;

import static com.google.jenkins.plugins.computeengine.client.ComputeClient.nameFromSelfLink;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.InstanceTemplate;
import com.google.jenkins.plugins.computeengine.ComputeEngineCloud;
import com.google.jenkins.plugins.computeengine.client.ComputeClient;
import hudson.model.labels.LabelAtom;
import hudson.slaves.NodeProvisioner.PlannedNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.logging.StreamHandler;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.jvnet.hudson.test.JenkinsRule;

public class ComputeEngineCloudTemplateNoGoogleLabelsIT {
  private static Logger log =
      Logger.getLogger(ComputeEngineCloudTemplateNoGoogleLabelsIT.class.getName());

  private static final String TEMPLATE =
      ITUtil.format("projects/%s/global/instanceTemplates/test-template-no-labels");

  @ClassRule public static Timeout timeout = new Timeout(5, TimeUnit.MINUTES);
  @ClassRule public static JenkinsRule r = new JenkinsRule();

  private static ByteArrayOutputStream logOutput = new ByteArrayOutputStream();
  private static StreamHandler sh;
  private static ComputeClient client;
  private static Map<String, String> label =
      ITUtil.getLabel(ComputeEngineCloudTemplateNoGoogleLabelsIT.class);
  private static ComputeEngineCloud cloud;
  private static Instance instance;

  @BeforeClass
  public static void init() throws Exception {
    log.info("init");
    ITUtil.initCredentials(r);
    cloud = ITUtil.initCloud(r);
    sh = ITUtil.initLogging(logOutput);
    client = ITUtil.initClient(r, label, log);

    cloud.addConfiguration(
        ITUtil.instanceConfiguration(
            ITUtil.DEB_JAVA_STARTUP_SCRIPT,
            ITUtil.NUM_EXECUTORS,
            ITUtil.LABEL,
            label,
            false,
            false,
            TEMPLATE));
    InstanceTemplate instanceTemplate = ITUtil.createTemplate(null, TEMPLATE);
    client.insertTemplate(cloud.projectId, instanceTemplate);
    // Add a new node
    Collection<PlannedNode> planned = cloud.provision(new LabelAtom(ITUtil.LABEL), 1);
    // There should be a successful planned node even without google labels
    assertEquals(ITUtil.logs(sh, logOutput), 1, planned.size());

    String name = planned.iterator().next().displayName;
    // Wait for the node creation to finish
    planned.iterator().next().future.get();
    instance = client.getInstance(ITUtil.PROJECT_ID, ITUtil.ZONE, name);
  }

  @AfterClass
  public static void teardown() throws IOException {
    try {
      client.deleteTemplate(cloud.projectId, nameFromSelfLink(TEMPLATE));
    } catch (Exception e) {
      // noop
    }

    ITUtil.teardown(sh, logOutput, client, label, log);
  }

  @Test
  public void testTemplateNoGoogleLabelsNoWarningLogs() {
    // There should be no warning logs even without Google labels
    assertFalse(ITUtil.logs(sh, logOutput), ITUtil.logs(sh, logOutput).contains("WARNING"));
  }

  @Test
  public void testTemplateNoGoogleLabelsCloudIdLabelKeyAndValue() {
    assertEquals(
        ITUtil.logs(sh, logOutput),
        cloud.getInstanceId(),
        instance.getLabels().get(ComputeEngineCloud.CLOUD_ID_LABEL_KEY));
  }
}
