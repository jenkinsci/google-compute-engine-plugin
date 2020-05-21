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

import static com.google.jenkins.plugins.computeengine.ComputeEngineCloud.checkPermissions;

import com.google.common.base.Strings;
import hudson.Extension;
import hudson.RelativePath;
import hudson.util.FormValidation;
import lombok.Getter;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

@Getter
public class SharedVpcNetworkConfiguration extends NetworkConfiguration {
  public static final String SUBNETWORK_TEMPLATE = "projects/%s/regions/%s/subnetworks/%s";
  private final String projectId;
  private final String subnetworkShortName;
  private final String region;

  @DataBoundConstructor
  public SharedVpcNetworkConfiguration(
      String projectId, String region, String subnetworkShortName) {
    super("", String.format(SUBNETWORK_TEMPLATE, projectId, region, subnetworkShortName));
    this.projectId = projectId;
    this.subnetworkShortName = subnetworkShortName;
    this.region = region;
  }

  @Extension
  public static final class DescriptorImpl extends NetworkConfigurationDescriptor {
    public String getDisplayName() {
      return "Shared VPC";
    }

    public FormValidation doCheckProjectId(@QueryParameter String value) {
      checkPermissions();
      if (Strings.isNullOrEmpty(value)) {
        return FormValidation.error("Project ID required");
      }
      return FormValidation.ok();
    }

    public FormValidation doCheckSubnetworkName(@QueryParameter String value) {
      checkPermissions();
      if (Strings.isNullOrEmpty(value)) {
        return FormValidation.error("Subnetwork name required");
      }

      if (value.contains("/")) {
        return FormValidation.error("Subnetwork name should not contain any '/' characters");
      }
      return FormValidation.ok();
    }

    public FormValidation doCheckRegion(
        @QueryParameter String value,
        @QueryParameter("region") @RelativePath("..") final String region) {
      checkPermissions();
      if (Strings.isNullOrEmpty(region)
          || Strings.isNullOrEmpty(value)
          || !region.endsWith(value)) {
        return FormValidation.error(
            "The region you specify for a shared VPC should match the region selected in the 'Location' section above");
      }
      return FormValidation.ok();
    }
  }
}
