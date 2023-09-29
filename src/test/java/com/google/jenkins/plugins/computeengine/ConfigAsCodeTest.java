package com.google.jenkins.plugins.computeengine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.SecretBytes;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotPrivateKeyCredentials;
import com.google.jenkins.plugins.credentials.oauth.JsonServiceAccountConfig;
import hudson.model.Node;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import java.nio.charset.StandardCharsets;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class ConfigAsCodeTest {

  private static final byte[] PK_BYTES =
      "{\"client_email\": \"example@example.com\"}".getBytes(StandardCharsets.UTF_8);

  @Rule public JenkinsRule jenkinsRule = new JenkinsConfiguredWithCodeRule();

  @Test
  @ConfiguredWithCode("configuration-as-code.yml")
  public void shouldCreateCloudInstanceFromCode() {
    assertEquals("Zero clouds found", jenkinsRule.jenkins.clouds.size(), 1);
    ComputeEngineCloud cloud =
        (ComputeEngineCloud) jenkinsRule.jenkins.clouds.getByName("gce-jenkins-build");
    assertNotNull("Cloud by name not found", cloud);
    assertEquals("Project id is wrong", "gce-jenkins", cloud.getProjectId());
    assertEquals("Wrong instance cap str", "53", cloud.getInstanceCapStr());
    assertEquals("Wrong instance cap", 53, cloud.getInstanceCap());
    assertEquals("Wrong credentials", "gce-jenkins", cloud.getCredentialsId());

    assertEquals("Configurations number wrong", 1, cloud.getConfigurations().size());
    InstanceConfiguration configuration = cloud.getConfigurations().get(0);
    assertEquals(
        "Wrong configurations prefix", "jenkins-agent-image", configuration.getNamePrefix());
    assertEquals(
        "Wrong configurations description", "Jenkins agent", configuration.getDescription());
    assertEquals(
        "Wrong configurations launchTimeoutSecondsStr",
        "6",
        configuration.getLaunchTimeoutSecondsStr());
    assertEquals(
        "Wrong configurations getLaunchTimeoutMillis",
        6000,
        configuration.getLaunchTimeoutMillis());
    assertEquals("Wrong configurations mode", Node.Mode.EXCLUSIVE, configuration.getMode());
    assertEquals(
        "Wrong configurations labelString", "jenkins-agent", configuration.getLabelString());
    assertEquals("Wrong configurations numExecutors", "1", configuration.getNumExecutorsStr());
    assertEquals("Wrong configurations runAsUser", "jenkins", configuration.getRunAsUser());
    assertEquals("Wrong configurations remoteFs", "agent", configuration.getRemoteFs());
    assertEquals("Wrong configurations javaExecPath", "java", configuration.getJavaExecPath());
    assertEquals("Wrong configurations enableSecureBoot", true, configuration.isEnableSecureBoot());
  }

  @Test
  @ConfiguredWithCode("configuration-as-code.yml")
  public void shouldCreateGCEClientFromCode() throws Exception {
    SecretBytes bytes = SecretBytes.fromBytes(PK_BYTES);
    JsonServiceAccountConfig serviceAccountConfig = new JsonServiceAccountConfig();
    serviceAccountConfig.setSecretJsonKey(bytes);
    assertNotNull(serviceAccountConfig.getAccountId());
    Credentials credentials =
        new GoogleRobotPrivateKeyCredentials("gce-jenkins", serviceAccountConfig, null);

    CredentialsStore store =
        new SystemCredentialsProvider.ProviderImpl().getStore(jenkinsRule.jenkins);
    store.addCredentials(Domain.global(), credentials);

    ComputeEngineCloud cloud =
        (ComputeEngineCloud) jenkinsRule.jenkins.clouds.getByName("gce-jenkins-build");
    assertNotNull("Cloud by name not found", cloud);
    // Ensure correct exception is thrown
    assertThrows(
        GoogleRobotPrivateKeyCredentials.PrivateKeyNotSetException.class, cloud::getClient);
  }
}
