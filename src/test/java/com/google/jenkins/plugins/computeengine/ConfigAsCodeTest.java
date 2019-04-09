package com.google.jenkins.plugins.computeengine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotPrivateKeyCredentials;
import hudson.model.Node;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mockito;

public class ConfigAsCodeTest {

  @Rule public JenkinsRule r = new JenkinsConfiguredWithCodeRule();

  @Test
  @ConfiguredWithCode("configuration-as-code.yml")
  public void shouldCreateCloudInstanceFromCode() throws Exception {
    assertEquals("Zero clouds found", r.jenkins.clouds.size(), 1);
    ComputeEngineCloud cloud = (ComputeEngineCloud) r.jenkins.clouds.getByName("gce-jenkins-build");
    assertNotNull("Cloud by name not found", cloud);
    assertEquals("Project id is wrong", "gce-jenkins", cloud.getProjectId());
    assertEquals("Wrong instance cap str", "53", cloud.getInstanceCapStr());
    assertEquals("Wrong instance cap", 53, cloud.getInstanceCap());
    assertEquals("Wrong credentials", "gce-jenkins", cloud.credentialsId);

    assertEquals("Configurations number wrong", 1, cloud.getConfigurations().size());
    InstanceConfiguration configuration = cloud.getConfigurations().get(0);
    assertEquals("Wrong configurations prefix", "jenkins-agent-image", configuration.namePrefix);
    assertEquals("Wrong configurations description", "Jenkins agent", configuration.description);
    assertEquals(
        "Wrong configurations launchTimeoutSecondsStr", "6", configuration.launchTimeoutSecondsStr);
    assertEquals(
        "Wrong configurations getLaunchTimeoutMillis",
        6000,
        configuration.getLaunchTimeoutMillis());
    assertEquals("Wrong configurations mode", Node.Mode.EXCLUSIVE, configuration.getMode());
    assertEquals(
        "Wrong configurations labelString", "jenkins-agent", configuration.getLabelString());
    assertEquals("Wrong configurations numExecutors", "1", configuration.numExecutorsStr);
    assertEquals("Wrong configurations runAsUser", "jenkins", configuration.runAsUser);
    assertEquals("Wrong configurations remoteFs", "agent", configuration.remoteFs);
  }

  @Test
  @ConfiguredWithCode("configuration-as-code.yml")
  public void shouldCreateGCEClientFromCode() throws Exception {
    GoogleRobotPrivateKeyCredentials credentials =
        Mockito.mock(GoogleRobotPrivateKeyCredentials.class);
    Mockito.when(credentials.getId()).thenReturn("gce-jenkins");

    CredentialsStore store = new SystemCredentialsProvider.ProviderImpl().getStore(r.jenkins);
    store.addCredentials(Domain.global(), credentials);

    ComputeEngineCloud cloud = (ComputeEngineCloud) r.jenkins.clouds.getByName("gce-jenkins-build");
    assertNotNull("Cloud by name not found", cloud);
    assertNotNull("GCE client not created", cloud.getClient());
  }
}
