package com.google.jenkins.plugins.computeengine;

import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.*;
import com.google.jenkins.plugins.computeengine.client.ClientFactory;
import com.google.jenkins.plugins.computeengine.client.ComputeClient;
import hudson.Extension;
import hudson.RelativePath;
import hudson.model.*;
import hudson.model.labels.LabelAtom;
import hudson.slaves.NodeProperty;
import hudson.util.FormValidation;
import hudson.Util;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class InstanceConfiguration implements Describable<InstanceConfiguration> {
    public final String region;
    public final String zone;
    public final String machineType;
    public final String description;
    public final String labels;
    public final String bootDiskType;
    public final Boolean bootDiskAutoDelete;
    public final String bootDiskSourceImageName;
    public final String bootDiskSourceImageProject;
    public Integer bootDiskSizeGb;
    public final Node.Mode mode;
    public final AcceleratorConfiguration acceleratorConfiguration;

    public transient Set<LabelAtom> labelSet;

    protected transient ComputeEngineCloud cloud;

    public static final Integer DEFAULT_BOOT_DISK_SIZE_GB = 10;

    static final List<String> KNOW_LINUX_IMAGE_PROJECTS = new ArrayList<String>() {{
        add("centos-cloud");
        add("coreos-cloud");
        add("cos-cloud");
        add("debian-cloud");
        add("rhel-cloud");
        add("suse-cloud");
        add("suse-sap-cloud");
        add("ubuntu-os-cloud");
    }};

    @DataBoundConstructor
    public InstanceConfiguration(String region, String zone, String machineType, String labelString,
                                 String description,
                                 String bootDiskType,
                                 String bootDiskAutoDeleteStr,
                                 String bootDiskSourceImageName,
                                 String bootDiskSourceImageProject,
                                 String bootDiskSizeGbStr,
                                 Node.Mode mode,
                                 AcceleratorConfiguration acceleratorConfiguration) {
        this.region = region;
        this.zone = zone;
        this.machineType = machineType;
        this.description = description;

        // Boot disk
        this.bootDiskType = bootDiskType;
        this.bootDiskAutoDelete = Boolean.parseBoolean(bootDiskAutoDeleteStr);
        this.bootDiskSourceImageName = bootDiskSourceImageName;
        this.bootDiskSourceImageProject = bootDiskSourceImageProject;

        try {
            this.bootDiskSizeGb = Integer.parseInt(bootDiskSizeGbStr);
        } catch (NumberFormatException nfe) {
            this.bootDiskSizeGb = DEFAULT_BOOT_DISK_SIZE_GB;
        }


        // Network
        //TODO

        // IAM
        //TODO

        // Other
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

    public ComputeEngineInstance provision(TaskListener listener, Label requiredLabel) throws IOException {
        PrintStream logger = listener.getLogger();
        try {
            ComputeEngineInstance agent = new ComputeEngineInstance(
                    "name", "desc", "remoteFS", 1, mode, labels, new ComputeEngineLinuxLauncher(),
                    null);
            return agent;
        } catch (Descriptor.FormException fe) {
            //TODO: log
            return null;
        }
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
                p = Jenkins.getInstance().getDescriptor(ComputeEngineInstance.class).getHelpFile(fieldName);
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
                return FormValidation.warning("Please select a region...");
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
                return FormValidation.warning("Please select a zone...");
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
                return FormValidation.warning("Please select a machine type...");
            }
            return FormValidation.ok();
        }

        public ListBoxModel doFillBootDiskTypeItems(@AncestorInPath Jenkins context,
                                                    @QueryParameter("zone") String zone,
                                                    @QueryParameter("credentialsId") @RelativePath("..") final String credentialsId) {
            ListBoxModel items = new ListBoxModel();
            items.add("");
            if (zone == null || zone.isEmpty() || credentialsId == null || credentialsId.isEmpty()) {
                return items;
            }
            try {
                ClientFactory clientFactory = new ClientFactory(context, new ArrayList<DomainRequirement>(), credentialsId);
                ComputeClient compute = clientFactory.compute();
                List<DiskType> diskTypes = compute.getBootDiskTypes(zone);

                for (DiskType dt : diskTypes) {
                    items.add(dt.getName(), dt.getSelfLink());
                }
                return items;
            } catch (IOException ioe) {
                items.clear();
                items.add("Error retrieving disk types");
                return items;
            }
        }

        public ListBoxModel doFillBootDiskSourceImageProjectItems(@AncestorInPath Jenkins context,
                                                                  @QueryParameter("projectId") @RelativePath("..") final String projectId) {
            ListBoxModel items = new ListBoxModel();
            items.add("");
            items.add(projectId);
            for (String v : KNOW_LINUX_IMAGE_PROJECTS) {
                items.add(v);
            }
            return items;
        }

        public FormValidation doCheckBootDiskSourceImageProject(@QueryParameter String value) {
            if (value.equals("")) {
                return FormValidation.warning("Please select source image project...");
            }
            return FormValidation.ok();
        }

        public ListBoxModel doFillBootDiskSourceImageNameItems(@AncestorInPath Jenkins context,
                                                               @QueryParameter("bootDiskSourceImageProject") final String projectId,
                                                               @QueryParameter("credentialsId") @RelativePath("..") final String credentialsId) {
            ListBoxModel items = new ListBoxModel();
            if(projectId == null || projectId.isEmpty()) {
                return items;
            }

            try {
                ClientFactory clientFactory = new ClientFactory(context, new ArrayList<DomainRequirement>(), credentialsId);
                ComputeClient compute = clientFactory.compute();
                List<Image> images = compute.getImages(projectId);

                for (Image i : images) {
                    items.add(i.getName(), i.getSelfLink());
                }
            } catch (IOException ioe) {
                items.clear();
                items.add("Error retrieving images for project");
            }
            return items;
        }

        public FormValidation doCheckBootDiskType(@QueryParameter String value) {
            if (value.equals("")) {
                return FormValidation.warning("Please select a disk type...");
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
