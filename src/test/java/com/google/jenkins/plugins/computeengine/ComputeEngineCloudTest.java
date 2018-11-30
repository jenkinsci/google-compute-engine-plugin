package com.google.jenkins.plugins.computeengine;

import static com.google.jenkins.plugins.computeengine.client.ClientFactoryTest.ACCOUNT_ID;
import static com.google.jenkins.plugins.computeengine.client.ClientFactoryTest.PRIVATE_KEY;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotPrivateKeyCredentials;
import com.google.jenkins.plugins.credentials.oauth.ServiceAccountConfig;
import hudson.model.Label;
import hudson.model.labels.LabelAtom;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.util.ArrayList;
import java.util.List;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ComputeEngineCloudTest {

  private static final String CLOUD_NAME = "test-cloud";
  private static final String PROJECT_ID = ACCOUNT_ID;
  private static final String INSTANCE_CAP_STR = "1";

  @Rule public JenkinsRule r = new JenkinsRule();

  @Mock public ServiceAccountConfig serviceAccountConfig;

  @Before
  public void init() {
    Mockito.when(serviceAccountConfig.getAccountId()).thenReturn(ACCOUNT_ID);
    Mockito.when(serviceAccountConfig.getPrivateKey()).thenReturn(PRIVATE_KEY);
  }

  @Test
  public void construction() throws Exception {
    // Create a credential
    Credentials c =
        (Credentials) new GoogleRobotPrivateKeyCredentials(ACCOUNT_ID, serviceAccountConfig, null);
    CredentialsStore store = new SystemCredentialsProvider.ProviderImpl().getStore(r.jenkins);
    store.addCredentials(Domain.global(), c);

    // Add a few InstanceConfigurations
    List<InstanceConfiguration> ics = new ArrayList<>();
    ics.add(InstanceConfigurationTest.instanceConfiguration());
    ics.add(InstanceConfigurationTest.instanceConfiguration());
    ComputeEngineCloud cloud =
        new ComputeEngineCloud(CLOUD_NAME, PROJECT_ID, PROJECT_ID, INSTANCE_CAP_STR, ics);

    // Ensure names are set
    Assert.assertEquals(ComputeEngineCloud.CLOUD_PREFIX + CLOUD_NAME, cloud.name);
    Assert.assertEquals(CLOUD_NAME, cloud.getCloudName());
    Assert.assertEquals(CLOUD_NAME, cloud.getDisplayName());
    Assert.assertNotEquals(CLOUD_NAME, cloud.name);

    // Ensure ComputeClient is created
    Assert.assertNotNull("ComputeClient was not initialized", cloud.client);

    // Ensure transient properties were initialized
    for (InstanceConfiguration ic : ics) {
      Assert.assertEquals(
          "Cloud reference was not set on child InstanceConfiguration", ic.cloud, cloud);
    }

    // Add another InstanceConfiguration and ensure properties are initialized
    cloud.addConfiguration(InstanceConfigurationTest.instanceConfiguration());
    for (InstanceConfiguration ic : ics) {
      Assert.assertEquals(
          "Cloud reference was not set on child InstanceConfiguration", ic.cloud, cloud);
    }
  }

  @Test
  public void labels() throws Exception {
    // Create a credential
    Credentials c =
        (Credentials) new GoogleRobotPrivateKeyCredentials(ACCOUNT_ID, serviceAccountConfig, null);
    CredentialsStore store = new SystemCredentialsProvider.ProviderImpl().getStore(r.jenkins);
    store.addCredentials(Domain.global(), c);

    // Add a few InstanceConfigurations
    List<InstanceConfiguration> ics = new ArrayList<>();
    ics.add(InstanceConfigurationTest.instanceConfiguration());
    ComputeEngineCloud cloud =
        new ComputeEngineCloud(CLOUD_NAME, PROJECT_ID, PROJECT_ID, INSTANCE_CAP_STR, ics);

    // Should be able to provision a label
    Label l = new LabelAtom(InstanceConfigurationTest.A_LABEL);
    Assert.assertTrue(
        "Should be able to provision for label " + InstanceConfigurationTest.A_LABEL,
        cloud.canProvision(l));

    // Configuration for label should match
    Assert.assertEquals(ics.get(0), cloud.getInstanceConfig(l));

    // Configuration for description should match
    Assert.assertEquals(ics.get(0), cloud.getInstanceConfig(ics.get(0).description));
  }

  @Test
  public void descriptorFillCredentials() throws Exception {
    // Create a credential
    Credentials c =
        (Credentials) new GoogleRobotPrivateKeyCredentials(ACCOUNT_ID, serviceAccountConfig, null);
    CredentialsStore store = new SystemCredentialsProvider.ProviderImpl().getStore(r.jenkins);
    store.addCredentials(Domain.global(), c);

    ComputeEngineCloud.GoogleCloudDescriptor d = new ComputeEngineCloud.GoogleCloudDescriptor();
    ListBoxModel got = d.doFillCredentialsIdItems(r.jenkins, PROJECT_ID);
    Assert.assertEquals("Should have returned 1 credential", 2, got.size());
    Assert.assertEquals("First credential should be - none -", "- none -", got.get(0).name);
    Assert.assertEquals("Second credential should be " + PROJECT_ID, PROJECT_ID, got.get(1).name);
  }

  @Test
  public void descriptorProjectValidation() {
    ComputeEngineCloud.GoogleCloudDescriptor d = new ComputeEngineCloud.GoogleCloudDescriptor();

    // Validate project ID validation
    FormValidation fv = d.doCheckProjectId(null);
    Assert.assertEquals(FormValidation.Kind.ERROR, fv.kind);
    fv = d.doCheckProjectId("");
    Assert.assertEquals(FormValidation.Kind.ERROR, fv.kind);
    fv = d.doCheckProjectId(PROJECT_ID);
    Assert.assertEquals(FormValidation.Kind.OK, fv.kind);
  }
}
