package com.google.jenkins.plugins.computeengine;

import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.google.api.services.compute.model.MachineType;
import com.google.api.services.compute.model.Region;
import com.google.api.services.compute.model.Zone;
import com.google.jenkins.plugins.computeengine.client.ClientFactory;
import com.google.jenkins.plugins.computeengine.client.ComputeClient;
import hudson.Extension;
import hudson.RelativePath;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.labels.LabelAtom;
import hudson.util.FormValidation;
import hudson.Util;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class InstanceConfiguration implements Describable<InstanceConfiguration> {
    public final String region;
    public final String zone;
    public final String machineType;
    public final String labels;
    public final Node.Mode mode;
    public transient Set<LabelAtom> labelSet;
    public final AcceleratorConfiguration acceleratorConfiguration;

    @DataBoundConstructor
    public InstanceConfiguration(String region, String zone, String machineType, String labelString,
                                 Node.Mode mode, AcceleratorConfiguration acceleratorConfiguration) {
        this.region = region;
        this.zone = zone;
        this.machineType = machineType;
        this.acceleratorConfiguration = acceleratorConfiguration;
        this.mode = mode;
        this.labels = Util.fixNull(labelString);

        readResolve();
    }

    public Descriptor<InstanceConfiguration> getDescriptor() {
        return Jenkins.getInstance().getDescriptor(getClass());
    }

    public String getLabelString() {
        return labels;
    }

    public Set<LabelAtom> getLabelSet() {
        return labelSet;
    }

    public Node.Mode getMode() {
        return mode;
    }

    /**
     * Initializes transient properties
     */
    protected Object readResolve() {
        Jenkins.getInstance().checkPermission(Jenkins.RUN_SCRIPTS);

        labelSet = Label.parse(labels);
        return this;
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<InstanceConfiguration> {
        @Override
        public String getDisplayName() {
            return null;
        }

        @Override
        public String getHelpFile(String fieldName) {
            String p = super.getHelpFile(fieldName);
            if (p == null)
                p = Jenkins.getInstance().getDescriptor(ComputeEngineAgent.class).getHelpFile(fieldName);
            return p;
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

        public FormValidation doCheckLabelString(@QueryParameter String value, @QueryParameter Node.Mode mode) {
            if (mode == Node.Mode.EXCLUSIVE && (value == null || value.trim().isEmpty())) {
                return FormValidation.warning("You may want to assign labels to this node;"
                        + " it's marked to only run jobs that are exclusively tied to itself or a label.");
            }
            return FormValidation.ok();
        }
    }
}
