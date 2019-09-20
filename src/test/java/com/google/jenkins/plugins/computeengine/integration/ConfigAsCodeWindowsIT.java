package com.google.jenkins.plugins.computeengine.integration;

import static com.google.jenkins.plugins.computeengine.integration.ITUtil.TEST_TIMEOUT_MULTIPLIER;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.getLabel;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initClient;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initCredentials;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.teardownResources;
import static org.junit.Assert.assertEquals;

import com.google.cloud.graphite.platforms.plugin.client.ComputeClient;
import com.google.jenkins.plugins.computeengine.ComputeEngineCloud;
import hudson.model.FreeStyleProject;
import hudson.model.labels.LabelAtom;
import hudson.tasks.BatchFile;
import hudson.tasks.Builder;
import io.jenkins.plugins.casc.ConfigurationAsCode;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.jvnet.hudson.test.JenkinsRule;

public class ConfigAsCodeWindowsIT {

  private static Logger log = Logger.getLogger(ConfigAsCodeWindowsIT.class.getName());
  @ClassRule public static JenkinsRule jenkinsRule = new JenkinsRule();

  @ClassRule
  public static Timeout timeout = new Timeout(10 * TEST_TIMEOUT_MULTIPLIER, TimeUnit.MINUTES);

  private static ComputeClient client;
  private static Map<String, String> label = getLabel(ConfigAsCodeWindowsIT.class);

  @BeforeClass
  public static void init() throws Exception {
    log.info("init");
    ConfigurationAsCode.get()
        .configure(
            ConfigAsCodeWindowsIT.class
                .getResource("configuration-as-code-windows-it.yml")
                .toString());
    initCredentials(jenkinsRule);
    client = initClient(jenkinsRule, label, log);
  }

  @AfterClass
  public static void teardown() throws IOException {
    teardownResources(client, label, log);
  }

  @Test
  public void testWindowsWorkerCreated() throws Exception {
    ComputeEngineCloud cloud =
        (ComputeEngineCloud) jenkinsRule.jenkins.clouds.getByName("gce-integration");

    // Should be 1 configuration
    assertEquals(1, cloud.getConfigurations().size());
    cloud.getConfigurations().get(0).setGoogleLabels(label);

    FreeStyleProject project = jenkinsRule.createFreeStyleProject();
    Builder step = new BatchFile("echo works");
    project.getBuildersList().add(step);
    project.setAssignedLabel(new LabelAtom("integration-windows"));

    jenkinsRule.buildAndAssertSuccess(project);
  }
}
