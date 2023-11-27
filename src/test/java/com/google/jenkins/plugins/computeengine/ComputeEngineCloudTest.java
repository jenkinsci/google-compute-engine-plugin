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
import static org.junit.Assert.assertThrows;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.SecretBytes;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.google.common.collect.Lists;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotPrivateKeyCredentials;
import com.google.jenkins.plugins.credentials.oauth.JsonServiceAccountConfig;
import hudson.model.Label;
import hudson.model.labels.LabelAtom;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class ComputeEngineCloudTest {
  private static final String ACCOUNT_ID = "test-account-id";
  private static final byte[] PK_BYTES =
      "{\"client_email\": \"example@example.com\"}".getBytes(StandardCharsets.UTF_8);

  private static final String INSTANCE_ID = "213123";
  private static final String CLOUD_NAME = "test-cloud";
  private static final String PROJECT_ID = ACCOUNT_ID;
  private static final String INSTANCE_CAP_STR = "1";

  @Rule public JenkinsRule r = new JenkinsRule();

  @Test
  public void construction() throws Exception {
    SecretBytes bytes = SecretBytes.fromBytes(PK_BYTES);
    JsonServiceAccountConfig serviceAccountConfig = new JsonServiceAccountConfig();
    serviceAccountConfig.setSecretJsonKey(bytes);
    assertNotNull(serviceAccountConfig.getAccountId());
    // Create a credential
    final String credentialId = "test-credential-id";
    GoogleRobotPrivateKeyCredentials c =
        new GoogleRobotPrivateKeyCredentials(
            CredentialsScope.GLOBAL, credentialId, ACCOUNT_ID, serviceAccountConfig, null);
    CredentialsStore store = new SystemCredentialsProvider.ProviderImpl().getStore(r.jenkins);
    assertNotNull(store);
    store.addCredentials(Domain.global(), c);

    // Add a few InstanceConfigurations
    List<InstanceConfiguration> ics = new ArrayList<>();
    ics.add(instanceConfigurationBuilder().build());
    ics.add(instanceConfigurationBuilder().build());
    ComputeEngineCloud cloud =
        new ComputeEngineCloud(CLOUD_NAME, PROJECT_ID, credentialId, INSTANCE_CAP_STR);
    cloud.setInstanceId(INSTANCE_ID);
    cloud.setConfigurations(ics);

    // Ensure names are set
    assertEquals(INSTANCE_ID, cloud.getInstanceId());
    assertEquals(ComputeEngineCloud.CLOUD_PREFIX + CLOUD_NAME, cloud.name);
    assertEquals(CLOUD_NAME, cloud.getCloudName());
    assertEquals(CLOUD_NAME, cloud.getDisplayName());
    Assert.assertNotEquals(CLOUD_NAME, cloud.name);

    // Ensure correct exception is thrown
    assertThrows(
        GoogleRobotPrivateKeyCredentials.PrivateKeyNotSetException.class, cloud::getClient);

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
    SecretBytes bytes = SecretBytes.fromBytes(PK_BYTES);
    JsonServiceAccountConfig serviceAccountConfig = new JsonServiceAccountConfig();
    serviceAccountConfig.setSecretJsonKey(bytes);
    assertNotNull(serviceAccountConfig.getAccountId());
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
