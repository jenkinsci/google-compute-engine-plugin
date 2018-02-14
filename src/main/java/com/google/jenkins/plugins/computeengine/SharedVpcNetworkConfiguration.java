package com.google.jenkins.plugins.computeengine;

import hudson.Extension;
import hudson.RelativePath;
import hudson.util.FormValidation;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

public class SharedVpcNetworkConfiguration extends NetworkConfiguration {
    public final String projectId;
    public final String subnetworkShortName;
    public final String region;

    public static final String SUBNETWORK_TEMPLATE = "projects/%s/regions/%s/subnetworks/%s";
    @DataBoundConstructor
    public SharedVpcNetworkConfiguration(String projectId, String region, String subnetworkShortName) {
       super("", String.format(SUBNETWORK_TEMPLATE, projectId, region, subnetworkShortName));
       this.projectId = projectId;
       this.subnetworkShortName = subnetworkShortName;
       this.region = region;
    }

    @Extension
    public static final class DescriptorImpl extends NetworkConfigurationDescriptor {
        @Override
        public String getDisplayName() {
            return "Shared VPC";
        }

        public FormValidation doCheckProjectId(@QueryParameter String value) {
            if (value == null || value.equals("")) {
                return FormValidation.error("Project ID required");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckSubnetworkName(@QueryParameter String value) {
            if (value == null || value.equals("")) {
                return FormValidation.error("Subnetwork name required");
            }

            if (value.contains("/")) {
                return FormValidation.error("Subnetwork name should not contain any '/' characters");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckRegion(@QueryParameter String value, @QueryParameter("region") @RelativePath("..") final String region) {
            if(region == null || value == null || ! region.contains(value)) {
                return FormValidation.error("The region you specify for a shared VPC should match the region selected in the 'Location' section above");
            }
            return FormValidation.ok();
        }
    }
}
