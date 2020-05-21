/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.jenkins.plugins.computeengine;

import static org.mockito.ArgumentMatchers.anyString;

import com.google.api.services.compute.model.Network;
import com.google.api.services.compute.model.Subnetwork;
import com.google.cloud.graphite.platforms.plugin.client.ComputeClient;
import com.google.common.collect.ImmutableList;
import com.google.jenkins.plugins.computeengine.AutofilledNetworkConfiguration.DescriptorImpl;
import hudson.util.FormValidation;
import hudson.util.FormValidation.Kind;
import hudson.util.ListBoxModel;
import java.io.IOException;
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

// TODO(#49): Parallelize all unit tests
@RunWith(MockitoJUnitRunner.class)
public class AutofilledNetworkConfigurationTest {
  private static final String NETWORK_NAME = "test-network";
  private static final String SUBNETWORK_NAME = "test-subnetwork";
  private static final String REGION = "test-region";
  private static final String PROJECT_ID = "test-project";
  private static final String CREDENTIALS_ID = "test-credentials";

  @Rule public JenkinsRule r = new JenkinsRule();

  @Mock public ComputeClient computeClient;

  @Before
  public void init() {}

  @Test
  public void testConstructor() {
    // Empty constructor
    AutofilledNetworkConfiguration anc = new AutofilledNetworkConfiguration();
    Assert.assertEquals(anc.getNetwork(), "");
    Assert.assertEquals(anc.getSubnetwork(), "");

    anc = new AutofilledNetworkConfiguration(NETWORK_NAME, SUBNETWORK_NAME);
    Assert.assertEquals(anc.getNetwork(), NETWORK_NAME);
    Assert.assertEquals(anc.getSubnetwork(), SUBNETWORK_NAME);
  }

  @Test
  public void testDoFillNetworkItems() throws IOException {
    AutofilledNetworkConfiguration.DescriptorImpl d =
        networkFillDescriptorSetup(ImmutableList.of(NETWORK_NAME));
    ListBoxModel got = d.doFillNetworkItems(r.jenkins, "", "");
    Assert.assertEquals(2, got.size()); // One item from client, one empty item inserted by callee
    Assert.assertTrue(got.get(0).value.isEmpty());
    Assert.assertEquals(NETWORK_NAME, got.get(1).name);
  }

  @Test
  public void testDoCheckNetworkItemsEmpty() {
    AutofilledNetworkConfiguration.DescriptorImpl d =
        new AutofilledNetworkConfiguration.DescriptorImpl();
    FormValidation fv = d.doCheckNetwork("");
    Assert.assertEquals(FormValidation.Kind.ERROR, fv.kind);
  }

  @Test
  public void testDoCheckNetworkItemsValid() {
    DescriptorImpl descriptor = new AutofilledNetworkConfiguration.DescriptorImpl();
    FormValidation result = descriptor.doCheckNetwork(NETWORK_NAME);
    Assert.assertEquals(Kind.OK, result.kind);
  }

  @Test
  public void testDoFillSubnetworkItemsEmptyRegion() throws IOException {
    DescriptorImpl descriptor = subnetworkFillDescriptorSetup(ImmutableList.of(SUBNETWORK_NAME));
    ListBoxModel got =
        descriptor.doFillSubnetworkItems(r.jenkins, "", "", PROJECT_ID, CREDENTIALS_ID);
    Assert.assertEquals(0, got.size());
  }

  @Test
  public void testDoFillSubnetworkItemsValid() throws IOException {
    DescriptorImpl descriptor =
        subnetworkFillDescriptorSetup(ImmutableList.of("default", SUBNETWORK_NAME));
    ListBoxModel got =
        descriptor.doFillSubnetworkItems(r.jenkins, "default", REGION, PROJECT_ID, CREDENTIALS_ID);
    Assert.assertEquals(2, got.size());
    Assert.assertEquals("default", got.get(0).name);
  }

  @Test
  public void testDoFillSubnetworkItemsNoSubnetworks() throws IOException {
    DescriptorImpl descriptorImpl = subnetworkFillDescriptorSetup(ImmutableList.of());
    ListBoxModel got =
        descriptorImpl.doFillSubnetworkItems(
            r.jenkins, "default", REGION, PROJECT_ID, CREDENTIALS_ID);
    Assert.assertEquals(2, got.size());
    Assert.assertEquals("", got.get(0).name);
    Assert.assertEquals("default", got.get(1).name);
  }

  @Test
  public void testDoCheckSubnetworkItemsEmpty() {
    AutofilledNetworkConfiguration.DescriptorImpl d =
        new AutofilledNetworkConfiguration.DescriptorImpl();
    // An empty value will be selected for error cases and for missing region
    FormValidation fv = d.doCheckSubnetwork("");
    Assert.assertEquals(FormValidation.Kind.ERROR, fv.kind);
  }

  @Test
  public void testDoCheckSubnetworkItemsValid() {
    AutofilledNetworkConfiguration.DescriptorImpl d =
        new AutofilledNetworkConfiguration.DescriptorImpl();
    FormValidation fv = d.doCheckSubnetwork(SUBNETWORK_NAME);
    Assert.assertEquals(FormValidation.Kind.OK, fv.kind);
  }

  private DescriptorImpl networkFillDescriptorSetup(List<String> networkNames) throws IOException {
    List<Network> networks = new ArrayList<>();
    networkNames.forEach(
        network -> networks.add(new Network().setName(network).setSelfLink(network)));
    ComputeClient computeClient = Mockito.mock(ComputeClient.class);
    Mockito.when(computeClient.listNetworks(anyString()))
        .thenReturn(ImmutableList.copyOf(networks));
    DescriptorImpl.setComputeClient(computeClient);
    return new DescriptorImpl();
  }

  private DescriptorImpl subnetworkFillDescriptorSetup(List<String> subnetworkNames)
      throws IOException {
    List<Subnetwork> subnetworks = new ArrayList<>();
    subnetworkNames.forEach(
        subnet -> subnetworks.add(new Subnetwork().setName(subnet).setSelfLink(subnet)));
    ComputeClient computeClient = Mockito.mock(ComputeClient.class);
    Mockito.when(computeClient.listSubnetworks(anyString(), anyString(), anyString()))
        .thenReturn(ImmutableList.copyOf(subnetworks));
    DescriptorImpl.setComputeClient(computeClient);
    return new DescriptorImpl();
  }
}
