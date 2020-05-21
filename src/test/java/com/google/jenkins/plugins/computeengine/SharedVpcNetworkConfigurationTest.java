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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import hudson.util.FormValidation;
import org.junit.Test;

public class SharedVpcNetworkConfigurationTest {
  public final String PROJECT_ID = "test-project";
  public final String SUBNETWORK_NAME = "test-subnetwork";
  public final String BAD_SUBNETWORK_NAME = "projects/project/subnetworks/test-subnetwork";
  public final String REGION = "test-region";

  @Test
  public void construction() {
    // Empty constructor
    SharedVpcNetworkConfiguration anc =
        new SharedVpcNetworkConfiguration(PROJECT_ID, REGION, SUBNETWORK_NAME);
    assertTrue(anc.getNetwork().isEmpty());
    assertEquals(PROJECT_ID, anc.getProjectId());
    assertNotEquals(SUBNETWORK_NAME, anc.getSubnetwork());
    assertEquals(SUBNETWORK_NAME, anc.getSubnetworkShortName());
    assertEquals(REGION, anc.getRegion());
  }

  @Test
  public void descriptorSubnetwork() {
    SharedVpcNetworkConfiguration.DescriptorImpl d =
        new SharedVpcNetworkConfiguration.DescriptorImpl();

    // Subnetwork with slash returns an error
    FormValidation fv = d.doCheckSubnetworkName(BAD_SUBNETWORK_NAME);
    assertEquals(FormValidation.Kind.ERROR, fv.kind);

    // Empty Subnetwork returns an error
    fv = d.doCheckSubnetworkName("");
    assertEquals(FormValidation.Kind.ERROR, fv.kind);

    // Null Subnetwork returns an error
    fv = d.doCheckSubnetworkName(null);
    assertEquals(FormValidation.Kind.ERROR, fv.kind);

    // Good Subnetwork returns ok
    fv = d.doCheckSubnetworkName(SUBNETWORK_NAME);
    assertEquals(FormValidation.Kind.OK, fv.kind);
  }

  @Test
  public void descriptorProjectId() {
    SharedVpcNetworkConfiguration.DescriptorImpl d =
        new SharedVpcNetworkConfiguration.DescriptorImpl();

    // Empty project returns an error
    FormValidation fv = d.doCheckProjectId("");
    assertEquals(FormValidation.Kind.ERROR, fv.kind);

    // Null project returns an error
    fv = d.doCheckProjectId(null);
    assertEquals(FormValidation.Kind.ERROR, fv.kind);

    // Good project returns ok
    fv = d.doCheckProjectId(PROJECT_ID);
    assertEquals(FormValidation.Kind.OK, fv.kind);
  }

  @Test
  public void descriptorRegion() {
    SharedVpcNetworkConfiguration.DescriptorImpl d =
        new SharedVpcNetworkConfiguration.DescriptorImpl();

    // All empty params returns an error
    FormValidation fv = d.doCheckRegion("region-textbox", "");
    assertEquals(FormValidation.Kind.ERROR, fv.kind);

    // Empty region dropdown returns an error
    fv = d.doCheckRegion("region-textbox", "");
    assertEquals(FormValidation.Kind.ERROR, fv.kind);

    // Empty region returns an error
    fv = d.doCheckRegion("", "region-dropdown");
    assertEquals(FormValidation.Kind.ERROR, fv.kind);

    // Mismatched regions return an error
    fv = d.doCheckRegion("region-textbox", "region-dropdown");
    assertEquals(FormValidation.Kind.ERROR, fv.kind);

    // Matching regions return no error
    fv = d.doCheckRegion("region", "https://selflink/project/region");
    assertEquals(FormValidation.Kind.OK, fv.kind);
  }
}
