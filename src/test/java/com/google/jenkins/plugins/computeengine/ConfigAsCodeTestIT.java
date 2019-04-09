package com.google.jenkins.plugins.computeengine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.google.api.services.compute.model.Instance;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotPrivateKeyCredentials;
import com.google.jenkins.plugins.credentials.oauth.ServiceAccountConfig;
import hudson.model.labels.LabelAtom;
import hudson.slaves.NodeProvisioner;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import java.util.Collection;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class ConfigAsCodeTestIT extends ComputeEngineCloudIT {

  @Rule public JenkinsRule r = new JenkinsConfiguredWithCodeRule();

  @Test(timeout = 300000)
  @ConfiguredWithCode("configuration-as-code-it.yml")
  public void testWorkerCreated() throws Exception {
    // This method should be moved to some common class
    addCredentials();

    ComputeEngineCloud cloud = (ComputeEngineCloud) r.jenkins.clouds.getByName("gce-integration");
    // Add a new node
    Collection<NodeProvisioner.PlannedNode> planned = cloud.provision(new LabelAtom(LABEL), 1);

    // There should be a planned node
    assertEquals(logs(), 1, planned.size());

    String name = planned.iterator().next().displayName;

    // Wait for the node creation to finish
    planned.iterator().next().future.get();

    // There should be no warning logs
    assertFalse(logs(), logs().contains("WARNING"));

    Instance instance = cloud.getClient().getInstance(projectId, ZONE, name);

    assertNotNull(logs(), instance);
  }

  private void addCredentials() throws Exception {
    // Add a service account credential
    projectId = System.getenv("GOOGLE_PROJECT_ID");
    assertNotNull("GOOGLE_PROJECT_ID env var must be set", projectId);

    String serviceAccountKeyJson = System.getenv("GOOGLE_CREDENTIALS");
    assertNotNull("GOOGLE_CREDENTIALS env var must be set", serviceAccountKeyJson);

    ServiceAccountConfig sac = new StringJsonServiceAccountConfig(serviceAccountKeyJson);
    Credentials c = (Credentials) new GoogleRobotPrivateKeyCredentials(projectId, sac, null);

    CredentialsStore store = new SystemCredentialsProvider.ProviderImpl().getStore(r.jenkins);
    store.addCredentials(Domain.global(), c);
  }
}
