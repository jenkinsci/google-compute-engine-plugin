/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.jenkins.plugins.computeengine;

import static com.google.jenkins.plugins.computeengine.InstanceConfigurationTest.A_LABEL;
import static com.google.jenkins.plugins.computeengine.InstanceConfigurationTest.instanceConfigurationBuilder;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.google.common.collect.Lists;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotPrivateKeyCredentials;
import com.google.jenkins.plugins.credentials.oauth.ServiceAccountConfig;
import hudson.model.Label;
import hudson.model.labels.LabelAtom;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.security.PrivateKey;
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
  private static final PrivateKey PRIVATE_KEY;
  private static final String ACCOUNT_ID = "test-account-id";
  private static final String PK_ALGO = "test";
  private static final String PK_FORMAT = "test";
  private static final byte[] PK_BYTES = new byte[0];

  static {
    PRIVATE_KEY =
        new PrivateKey() {
          @Override
          public String getAlgorithm() {
            return PK_ALGO;
          }

          @Override
          public String getFormat() {
            return PK_FORMAT;
          }

          @Override
          public byte[] getEncoded() {
            return PK_BYTES;
          }
        };
  }

  private static final String INSTANCE_ID = "213123";
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
    ics.add(instanceConfigurationBuilder().build());
    ics.add(instanceConfigurationBuilder().build());
    ComputeEngineCloud cloud =
        new ComputeEngineCloud(CLOUD_NAME, PROJECT_ID, PROJECT_ID, INSTANCE_CAP_STR);
    cloud.setInstanceId(INSTANCE_ID);
    cloud.setConfigurations(ics);

    // Ensure names are set
    assertEquals(INSTANCE_ID, cloud.getInstanceId());
    assertEquals(ComputeEngineCloud.CLOUD_PREFIX + CLOUD_NAME, cloud.name);
    assertEquals(CLOUD_NAME, cloud.getCloudName());
    assertEquals(CLOUD_NAME, cloud.getDisplayName());
    Assert.assertNotEquals(CLOUD_NAME, cloud.name);

    // Ensure ComputeClient is created
    assertNotNull("ComputeClient was not initialized", cloud.getClient());

    // Ensure transient properties were initialized
    for (InstanceConfiguration ic : ics) {
      assertEquals(
          "Cloud reference was not set on child InstanceConfiguration", ic.getCloud(), cloud);
    }
  }

  @Test
  public void instanceIdWasGenerated() {
    ComputeEngineCloud cloud =
        new ComputeEngineCloud(CLOUD_NAME, PROJECT_ID, PROJECT_ID, INSTANCE_CAP_STR);
    Assert.assertThat(
        "Instance ID was not generated in constructor",
        cloud.getInstanceId(),
        not(isEmptyOrNullString()));
  }

  @Test
  public void getConfigurationsByLabelSimple() throws Exception {
    // Add a few InstanceConfigurations
    List<InstanceConfiguration> ics = Lists.newArrayList(instanceConfigurationBuilder().build());
    ComputeEngineCloud cloud =
        new ComputeEngineCloud(CLOUD_NAME, PROJECT_ID, PROJECT_ID, INSTANCE_CAP_STR);
    cloud.setConfigurations(ics);

    // Configuration for description should match
    assertEquals(
        ics.get(0), cloud.getInstanceConfigurationByDescription(ics.get(0).getDescription()));

    // Should be able to provision a label
    Label label = new LabelAtom(A_LABEL);
    Assert.assertTrue(
        "Should be able to provision for label " + A_LABEL, cloud.canProvision(label));

    // Configuration for label should match
    Assert.assertEquals(ics, cloud.getInstanceConfigurations(label));
  }

  @Test
  public void getConfigurationsByLabelMulti() throws Exception {
    // Add a few InstanceConfigurations
    List<InstanceConfiguration> ics =
        Lists.newArrayList(
            instanceConfigurationBuilder().build(), instanceConfigurationBuilder().build());
    ComputeEngineCloud cloud =
        new ComputeEngineCloud(CLOUD_NAME, PROJECT_ID, PROJECT_ID, INSTANCE_CAP_STR);
    cloud.setConfigurations(ics);

    // Should be able to provision a label
    Label label = new LabelAtom(A_LABEL);
    Assert.assertTrue(
        "Should be able to provision for label " + A_LABEL, cloud.canProvision(label));

    // Configuration for label should match
    assertEquals(ics, cloud.getInstanceConfigurations(label));
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
    assertEquals("Should have returned 1 credential", 2, got.size());
    assertEquals("First credential should be - none -", "- none -", got.get(0).name);
    assertEquals("Second credential should be " + PROJECT_ID, PROJECT_ID, got.get(1).name);
  }

  @Test
  public void descriptorProjectValidation() {
    ComputeEngineCloud.GoogleCloudDescriptor d = new ComputeEngineCloud.GoogleCloudDescriptor();

    // Validate project ID validation
    FormValidation fv = d.doCheckProjectId(null);
    assertEquals(FormValidation.Kind.ERROR, fv.kind);
    fv = d.doCheckProjectId("");
    assertEquals(FormValidation.Kind.ERROR, fv.kind);
    fv = d.doCheckProjectId(PROJECT_ID);
    assertEquals(FormValidation.Kind.OK, fv.kind);
  }
}
