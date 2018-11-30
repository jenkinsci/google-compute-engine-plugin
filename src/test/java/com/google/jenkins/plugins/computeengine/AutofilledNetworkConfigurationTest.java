/*
 * Copyright 2018 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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
import com.google.jenkins.plugins.computeengine.client.ComputeClient;
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
public class AutofilledNetworkConfigurationTest {
  public final String NETWORK_NAME = "test-network";
  public final String SUBNETWORK_NAME = "test-subnetwork";
  public final String REGION = "test-region";

  @Rule public JenkinsRule r = new JenkinsRule();

  @Mock public ComputeClient computeClient;

  @Before
  public void init() {}

  @Test
  public void construction() {
    // Empty constructor
    AutofilledNetworkConfiguration anc = new AutofilledNetworkConfiguration();
    Assert.assertEquals(anc.getNetwork(), "");
    Assert.assertEquals(anc.getSubnetwork(), "");

    anc = new AutofilledNetworkConfiguration(NETWORK_NAME, SUBNETWORK_NAME);
    Assert.assertEquals(anc.getNetwork(), NETWORK_NAME);
    Assert.assertEquals(anc.getSubnetwork(), SUBNETWORK_NAME);
  }

  @Test
  public void descriptorNetwork() throws Exception {
    // Set up mock
    List<Network> networks = new ArrayList<Network>();
    networks.add(new Network().setName(NETWORK_NAME).setSelfLink(NETWORK_NAME));
    Mockito.when(computeClient.getNetworks(anyString())).thenReturn(networks);
    AutofilledNetworkConfiguration.DescriptorImpl.setComputeClient(computeClient);

    // Test items returned by network dropdown filler
    AutofilledNetworkConfiguration.DescriptorImpl d =
        new AutofilledNetworkConfiguration.DescriptorImpl();
    ListBoxModel got = d.doFillNetworkItems(r.jenkins, "", "");
    Assert.assertEquals(
        networks.size() + 1, got.size()); // Add 1 to expected as callee inserts 1 empty item
    Assert.assertEquals(NETWORK_NAME, got.get(1).name);
  }

  @Test
  public void descriptorNetworkValidation() {
    AutofilledNetworkConfiguration.DescriptorImpl d =
        new AutofilledNetworkConfiguration.DescriptorImpl();
    FormValidation fv = d.doCheckNetwork("");
    Assert.assertEquals(FormValidation.Kind.ERROR, fv.kind);

    fv = d.doCheckNetwork(NETWORK_NAME);
    Assert.assertEquals(FormValidation.Kind.OK, fv.kind);
  }

  @Test
  public void descriptorSubnetwork() throws Exception {
    // Set up mock
    List<Subnetwork> subnetworks = new ArrayList<Subnetwork>();
    subnetworks.add(new Subnetwork().setName(SUBNETWORK_NAME).setSelfLink(SUBNETWORK_NAME));
    Mockito.when(computeClient.getSubnetworks(anyString(), anyString(), anyString()))
        .thenReturn(subnetworks);
    AutofilledNetworkConfiguration.DescriptorImpl.setComputeClient(computeClient);

    // Test items returned by subnetwork dropdown filler
    AutofilledNetworkConfiguration.DescriptorImpl d =
        new AutofilledNetworkConfiguration.DescriptorImpl();

    // No subnetworks returned if region is empty
    ListBoxModel got = d.doFillSubnetworkItems(r.jenkins, "", "", "", "");
    Assert.assertEquals(0, got.size());

    // Default network should return default subnetwork
    got = d.doFillSubnetworkItems(r.jenkins, "default", REGION, "", "");
    Assert.assertEquals(1, got.size());
    Assert.assertEquals("default", got.get(0).name);

    // Network should return a subnetwork
    got = d.doFillSubnetworkItems(r.jenkins, NETWORK_NAME, REGION, "", "");
    Assert.assertEquals(1, got.size());
    Assert.assertEquals(SUBNETWORK_NAME, got.get(0).name);

    // No subnetworks should return a specific error
    subnetworks.clear();
    got = d.doFillSubnetworkItems(r.jenkins, NETWORK_NAME, REGION, "", "");
    Assert.assertEquals(1, got.size());
    Assert.assertEquals(InstanceConfiguration.ERROR_NO_SUBNETS, got.get(0).name);
  }

  @Test
  public void descriptorSubnetworkValidation() {
    AutofilledNetworkConfiguration.DescriptorImpl d =
        new AutofilledNetworkConfiguration.DescriptorImpl();

    // No network returns an error
    FormValidation fv = d.doCheckSubnetwork(InstanceConfiguration.ERROR_NO_SUBNETS);
    Assert.assertEquals(FormValidation.Kind.ERROR, fv.kind);

    // No subnetwork returns an error
    fv = d.doCheckSubnetwork("");
    Assert.assertEquals(FormValidation.Kind.ERROR, fv.kind);

    fv = d.doCheckSubnetwork(NETWORK_NAME);
    Assert.assertEquals(FormValidation.Kind.OK, fv.kind);
  }
}
