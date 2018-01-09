package com.google.jenkins.plugins.computeengine;

import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.google.api.services.compute.model.AcceleratorType;
import com.google.api.services.compute.model.MachineType;
import com.google.api.services.compute.model.Region;
import com.google.api.services.compute.model.Zone;
import com.google.jenkins.plugins.computeengine.client.ClientFactory;
import com.google.jenkins.plugins.computeengine.client.ComputeClient;
import hudson.Extension;
import hudson.RelativePath;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class InstanceConfiguration implements Describable<InstanceConfiguration> {
    public final String region;
    public final String zone;
    public final String machineType;
    public final String gpuType;

    @DataBoundConstructor
    public InstanceConfiguration(String region, String zone, String machineType, String gpuType) {
        this.region = region;
        this.zone = zone;
        this.machineType = machineType;
        this.gpuType = gpuType;
    }

    public Descriptor<InstanceConfiguration> getDescriptor() {
        return Jenkins.getInstance().getDescriptor(getClass());
    }

    public String getRegion() {
        return region;
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<InstanceConfiguration> {
        @Override
        public String getDisplayName() {
            return null;
        }

        public ListBoxModel doFillRegionItems(@AncestorInPath Jenkins context,
                                              @QueryParameter("credentialsId") @RelativePath("..") final String credentialsId) {
            ListBoxModel items = new ListBoxModel();
            items.add("");
            try {
                ClientFactory clientFactory = new ClientFactory(context, new ArrayList<DomainRequirement>(), credentialsId);
                ComputeClient compute = clientFactory.compute();
                List<Region> regions = compute.getRegions();

                for (Region r : regions) {
                    items.add(r.getName(), r.getSelfLink());
                }
                return items;
            } catch (IOException ioe) {
                items.clear();
                items.add("Error retrieving regions");
                return items;
            }
        }

        public FormValidation doCheckRegion(@QueryParameter String value) {
            if (value.equals("")) {
                return FormValidation.error("Please select a region...");
            }
            return FormValidation.ok();
        }

        public ListBoxModel doFillZoneItems(@AncestorInPath Jenkins context,
                                            @QueryParameter("region") final String region,
                                            @QueryParameter("credentialsId") @RelativePath("..") final String credentialsId) {
            ListBoxModel items = new ListBoxModel();
            items.add("");
            if (region == null || region.isEmpty() || credentialsId == null || credentialsId.isEmpty()) {
                return items;
            }
            try {
                ClientFactory clientFactory = new ClientFactory(context, new ArrayList<DomainRequirement>(), credentialsId);
                ComputeClient compute = clientFactory.compute();
                List<Zone> zones = compute.getZones(region);

                for (Zone z : zones) {
                    items.add(z.getName(), z.getSelfLink());
                }
                return items;
            } catch (IOException ioe) {
                items.clear();
                items.add("Error retrieving zones");
                return items;
            }
        }

        public FormValidation doCheckZone(@QueryParameter String value) {
            if (value.equals("")) {
                return FormValidation.error("Please select a zone...");
            }
            return FormValidation.ok();
        }

        public ListBoxModel doFillMachineTypeItems(@AncestorInPath Jenkins context,
                                            @QueryParameter("zone") final String zone,
                                            @QueryParameter("credentialsId") @RelativePath("..") final String credentialsId) {
            ListBoxModel items = new ListBoxModel();
            items.add("");
            if (zone == null || zone.isEmpty() || credentialsId == null || credentialsId.isEmpty()) {
                return items;
            }
            try {
                ClientFactory clientFactory = new ClientFactory(context, new ArrayList<DomainRequirement>(), credentialsId);
                ComputeClient compute = clientFactory.compute();
                List<MachineType> machineTypes = compute.getMachineTypes(zone);

                for (MachineType m : machineTypes) {
                    items.add(m.getName(), m.getSelfLink());
                }
                return items;
            } catch (IOException ioe) {
                items.clear();
                items.add("Error retrieving machine types");
                return items;
            }
        }

        public FormValidation doCheckMachineType(@QueryParameter String value) {
            if (value.equals("")) {
                return FormValidation.error("Please select a machine type...");
            }
            return FormValidation.ok();
        }

        public ListBoxModel doFillGpuTypeItems(@AncestorInPath Jenkins context,
                                            @QueryParameter("zone") final String zone,
                                            @QueryParameter("credentialsId") @RelativePath("..") final String credentialsId) {
            ListBoxModel items = new ListBoxModel();
            items.add("");
            if (zone == null || zone.isEmpty() || credentialsId == null || credentialsId.isEmpty()) {
                return items;
            }
            try {
                ClientFactory clientFactory = new ClientFactory(context, new ArrayList<DomainRequirement>(), credentialsId);
                ComputeClient compute = clientFactory.compute();
                List<AcceleratorType> acceleratorTypes = compute.getAcceleratorTypes(zone);

                for (AcceleratorType a : acceleratorTypes) {
                    items.add(a.getName(), a.getSelfLink());
                }
                return items;
            } catch (IOException ioe) {
                items.clear();
                items.add("Error retrieving GPU types");
                return items;
            }
        }
    }
}
