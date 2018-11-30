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

import hudson.util.FormValidation;
import org.junit.Assert;
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
    Assert.assertEquals(anc.network, "");
    Assert.assertEquals(anc.projectId, PROJECT_ID);
    Assert.assertNotEquals(anc.subnetwork, SUBNETWORK_NAME);
    Assert.assertEquals(anc.subnetworkShortName, SUBNETWORK_NAME);
    Assert.assertEquals(anc.region, REGION);
  }

  @Test
  public void descriptorSubnetwork() {
    SharedVpcNetworkConfiguration.DescriptorImpl d =
        new SharedVpcNetworkConfiguration.DescriptorImpl();

    // Subnetwork with slash returns an error
    FormValidation fv = d.doCheckSubnetworkName(BAD_SUBNETWORK_NAME);
    Assert.assertEquals(FormValidation.Kind.ERROR, fv.kind);

    // Empty Subnetwork returns an error
    fv = d.doCheckSubnetworkName("");
    Assert.assertEquals(FormValidation.Kind.ERROR, fv.kind);

    // Null Subnetwork returns an error
    fv = d.doCheckSubnetworkName(null);
    Assert.assertEquals(FormValidation.Kind.ERROR, fv.kind);

    // Good Subnetwork returns ok
    fv = d.doCheckSubnetworkName(SUBNETWORK_NAME);
    Assert.assertEquals(FormValidation.Kind.OK, fv.kind);
  }

  @Test
  public void descriptorProjectId() {
    SharedVpcNetworkConfiguration.DescriptorImpl d =
        new SharedVpcNetworkConfiguration.DescriptorImpl();

    // Empty project returns an error
    FormValidation fv = d.doCheckProjectId("");
    Assert.assertEquals(FormValidation.Kind.ERROR, fv.kind);

    // Null project returns an error
    fv = d.doCheckProjectId(null);
    Assert.assertEquals(FormValidation.Kind.ERROR, fv.kind);

    // Good project returns ok
    fv = d.doCheckProjectId(PROJECT_ID);
    Assert.assertEquals(FormValidation.Kind.OK, fv.kind);
  }

  @Test
  public void descriptorRegion() {
    SharedVpcNetworkConfiguration.DescriptorImpl d =
        new SharedVpcNetworkConfiguration.DescriptorImpl();

    // All empty params returns an error
    FormValidation fv = d.doCheckRegion("region-textbox", "");
    Assert.assertEquals(FormValidation.Kind.ERROR, fv.kind);

    // Empty region dropdown returns an error
    fv = d.doCheckRegion("region-textbox", "");
    Assert.assertEquals(FormValidation.Kind.ERROR, fv.kind);

    // Empty region returns an error
    fv = d.doCheckRegion("", "region-dropdown");
    Assert.assertEquals(FormValidation.Kind.ERROR, fv.kind);

    // Mismatched regions return an error
    fv = d.doCheckRegion("region-textbox", "region-dropdown");
    Assert.assertEquals(FormValidation.Kind.ERROR, fv.kind);

    // Matching regions return no error
    fv = d.doCheckRegion("region", "https://selflink/project/region");
    Assert.assertEquals(FormValidation.Kind.OK, fv.kind);
  }
}
