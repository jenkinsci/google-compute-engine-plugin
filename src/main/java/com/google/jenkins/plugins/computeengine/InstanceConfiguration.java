package com.google.jenkins.plugins.computeengine;

import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.google.api.services.compute.model.*;
import com.google.jenkins.plugins.computeengine.client.ClientFactory;
import com.google.jenkins.plugins.computeengine.client.ComputeClient;
import hudson.Extension;
import hudson.RelativePath;
import hudson.model.*;
import hudson.model.labels.LabelAtom;
import hudson.util.FormValidation;
import hudson.Util;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.IOException;
import java.io.PrintStream;
import java.util.*;

import org.apache.commons.text.RandomStringGenerator;


public class InstanceConfiguration implements Describable<InstanceConfiguration> {
    public final String description;
    public final String namePrefix;
    public final String region;
    public final String zone;
    public final String machineType;
    public Integer numExecutors;
    public final String numExecutorsStr;
    public final String startupScript;
    public final boolean preemptible;
    public final String labels;
    public Map<String, String> googleLabels;
    public final String bootDiskType;
    public final boolean bootDiskAutoDelete;
    public final String bootDiskSourceImageName;
    public final String bootDiskSourceImageProject;
    public Long bootDiskSizeGb;
    public final String network;
    public final String subnetwork;
    public final boolean externalAddress;
    public final String networkTags;
    public final String serviceAccountEmail;
    public final Node.Mode mode;
    public final AcceleratorConfiguration acceleratorConfiguration;

    public transient Set<LabelAtom> labelSet;

    protected transient ComputeEngineCloud cloud;

    public static final Long DEFAULT_BOOT_DISK_SIZE_GB = 10L;
    public static final Integer DEFAULT_NUM_EXECUTORS = 1;
    public static final String ERROR_NO_SUBNETS = "No subnetworks exist in the given network and region.";
    public static final String METADATA_STARTUP_SCRIPT_KEY = "startup-script";
    public static final String NAT_TYPE = "ONE_TO_ONE_NAT";
    public static final String NAT_NAME = "External NAT";

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
    public InstanceConfiguration(String namePrefix,
                                 String region,
                                 String zone,
                                 String machineType,
                                 String numExecutorsStr,
                                 String startupScript,
                                 boolean preemptible,
                                 String labelString,
                                 String description,
                                 String bootDiskType,
                                 boolean bootDiskAutoDelete,
                                 String bootDiskSourceImageName,
                                 String bootDiskSourceImageProject,
                                 String bootDiskSizeGbStr,
                                 String network,
                                 String subnetwork,
                                 boolean externalAddress,
                                 String networkTags,
                                 String serviceAccountEmail,
                                 Node.Mode mode,
                                 AcceleratorConfiguration acceleratorConfiguration) {
        // General
        if (!namePrefix.endsWith(("-"))) {
            namePrefix += "-";
        }

        this.namePrefix = namePrefix;
        this.region = region;
        this.zone = zone;
        this.machineType = machineType;
        this.description = description;
        this.startupScript = startupScript;
        this.preemptible = preemptible;
        this.numExecutorsStr = numExecutorsStr;
        try {
            numExecutors = Integer.parseInt(numExecutorsStr);
        } catch (Exception e) {
            numExecutors = DEFAULT_NUM_EXECUTORS;
        }

        // Boot disk
        this.bootDiskType = bootDiskType;
        this.bootDiskAutoDelete = bootDiskAutoDelete;
        this.bootDiskSourceImageName = bootDiskSourceImageName;
        this.bootDiskSourceImageProject = bootDiskSourceImageProject;
        try {
            this.bootDiskSizeGb = Long.parseLong(bootDiskSizeGbStr);
        } catch (Exception e) {
            this.bootDiskSizeGb = DEFAULT_BOOT_DISK_SIZE_GB;
        }


        // Network
        this.network = network;
        this.subnetwork = subnetwork;
        this.externalAddress = externalAddress;
        this.networkTags = Util.fixNull(networkTags);

        // IAM
        this.serviceAccountEmail = serviceAccountEmail;

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

    public String getDisplayName() {
        return description;
    }

    //TODO: Make configurable
    public int getLaunchTimeout() {
        return Integer.MAX_VALUE / 1000;
    }

    public ComputeEngineInstance provision(TaskListener listener, Label requiredLabel) throws IOException {
        PrintStream logger = listener.getLogger();
        try {
            Instance i = instance();
            Operation operation = cloud.client.insertInstance(cloud.projectId, i);
            logger.println("Sent insert request");
            ComputeEngineInstance instance = new ComputeEngineInstance(cloud.name, i.getName(), i.getZone(), i.getDescription(), "./.jenkins-slave", numExecutors, mode, requiredLabel.getName(), new ComputeEngineLinuxLauncher(cloud.getCloudName(), operation), null, getLaunchTimeout());
            return instance;
        } catch (Descriptor.FormException fe) {
            logger.printf("Error provisioning instance: %v\n", fe);
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

    public Instance instance() {
        Instance i = new Instance();
        i.setName(uniqueName());
        i.setLabels(googleLabels);
        i.setDescription(description);
        i.setZone(ComputeClient.zoneFromSelfLink(zone));
        i.setMachineType(stripSelfLinkPrefix(machineType));
        i.setMetadata(metadata());
        i.setTags(tags());
        i.setScheduling(scheduling());
        i.setDisks(disks());
        i.setGuestAccelerators(accelerators());
        i.setNetworkInterfaces(networkInterfaces());
        i.setServiceAccounts(serviceAccounts());
        return i;
    }

    private String uniqueName() {
        char[][] pairs = {{'a', 'z'}, {'0', '9'}};
        RandomStringGenerator generator = new RandomStringGenerator.Builder()
                .withinRange(pairs)
                .build();
        String suffix = generator.generate(6);

        return namePrefix + suffix;
    }

    private Metadata metadata() {
        if (notNullOrEmpty(startupScript)) {
            Metadata metadata = new Metadata();
            List<Metadata.Items> items = new ArrayList<>();
            items.add(new Metadata.Items().setKey(METADATA_STARTUP_SCRIPT_KEY).setValue(startupScript));
            metadata.setItems(items);
            return metadata;
        }
        return null;
    }

    private Tags tags() {
        if (notNullOrEmpty(networkTags)) {
            Tags tags = new Tags();
            tags.setItems(Arrays.asList(networkTags.split(" ")));
            return tags;
        }
        return null;
    }

    private Scheduling scheduling() {
        Scheduling scheduling = new Scheduling();
        scheduling.setPreemptible(preemptible);
        return scheduling;
    }

    private List<AttachedDisk> disks() {
        AttachedDisk boot = new AttachedDisk();
        boot.setBoot(true);
        boot.setAutoDelete(bootDiskAutoDelete);
        boot.setInitializeParams(new AttachedDiskInitializeParams()
                .setDiskSizeGb(bootDiskSizeGb)
                .setDiskType(bootDiskType)
                .setSourceImage(bootDiskSourceImageName)
        );

        List<AttachedDisk> disks = new ArrayList<>();
        disks.add(boot);
        return disks;
    }

    private List<AcceleratorConfig> accelerators() {
        if (acceleratorConfiguration != null && notNullOrEmpty(acceleratorConfiguration.gpuCount) && notNullOrEmpty(acceleratorConfiguration.gpuType)) {
            List<AcceleratorConfig> accelerators = new ArrayList<>();
            accelerators.add(new AcceleratorConfig()
                    .setAcceleratorType(acceleratorConfiguration.gpuType)
                    .setAcceleratorCount(acceleratorConfiguration.gpuCount())
            );
            return accelerators;
        }
        return null;
    }

    private List<NetworkInterface> networkInterfaces() {
        List<NetworkInterface> networkInterfaces = new ArrayList<>();
        List<AccessConfig> accessConfigs = new ArrayList<>();
        if (externalAddress) {
            accessConfigs.add(new AccessConfig()
                    .setType(NAT_TYPE)
                    .setName(NAT_NAME)
            );
        }
        NetworkInterface nic = new NetworkInterface()
                .setNetwork(stripSelfLinkPrefix(network))
                .setAccessConfigs(accessConfigs);

        // Don't include subnetwork name if using default
        if (!subnetwork.equals("default")) {
            nic.setSubnetwork(stripSelfLinkPrefix(subnetwork));
        }

        networkInterfaces.add(nic);
        return networkInterfaces;
    }

    private List<ServiceAccount> serviceAccounts() {
        if (notNullOrEmpty(serviceAccountEmail)) {
            List<ServiceAccount> serviceAccounts = new ArrayList<>();
            serviceAccounts.add(
                    new ServiceAccount()
                            .setEmail(serviceAccountEmail)
                            .setScopes(Arrays.asList(new String[]{"https://www.googleapis.com/auth/cloud-platform"}))
            );
            return serviceAccounts;
        } else {
            return null;
        }
    }

    private static boolean notNullOrEmpty(String s) {
        return s != null && !s.isEmpty();
    }

    private static String stripSelfLinkPrefix(String s) {
        if (s.contains("https://www.googleapis.com")) {
            return s.substring(s.indexOf("/projects/") + 1, s.length());
        }
        return s;
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<InstanceConfiguration> {
        @Override
        public String getDisplayName() {
            return null;
        }

        public static String defaultBootDiskSizeGb() {
            return DEFAULT_BOOT_DISK_SIZE_GB.toString();
        }

        private static ComputeClient computeClient;

        public static void setComputeClient(ComputeClient client) {
            computeClient = client;
        }

        @Override
        public String getHelpFile(String fieldName) {
            String p = super.getHelpFile(fieldName);
            if (p == null)
                p = Jenkins.getInstance().getDescriptor(ComputeEngineInstance.class).getHelpFile(fieldName);
            return p;
        }

        public ListBoxModel doFillRegionItems(@AncestorInPath Jenkins context,
                                              @QueryParameter("projectId") @RelativePath("..") final String projectId,
                                              @QueryParameter("credentialsId") @RelativePath("..") final String credentialsId) {
            ListBoxModel items = new ListBoxModel();
            items.add("");
            try {
                ComputeClient compute = computeClient(context, credentialsId);
                List<Region> regions = compute.getRegions(projectId);

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
                                            @QueryParameter("projectId") @RelativePath("..") final String projectId,
                                            @QueryParameter("region") final String region,
                                            @QueryParameter("credentialsId") @RelativePath("..") final String credentialsId) {
            ListBoxModel items = new ListBoxModel();
            items.add("");
            try {
                ComputeClient compute = computeClient(context, credentialsId);
                List<Zone> zones = compute.getZones(projectId, region);

                for (Zone z : zones) {
                    items.add(z.getName(), z.getSelfLink());
                }
                return items;
            } catch (IOException ioe) {
                items.clear();
                items.add("Error retrieving zones");
                return items;
            } catch (IllegalArgumentException iae) {
                //TODO log
                return null;
            }
        }

        public FormValidation doCheckZone(@QueryParameter String value) {
            if (value.equals("")) {
                return FormValidation.warning("Please select a zone...");
            }
            return FormValidation.ok();
        }

        public ListBoxModel doFillMachineTypeItems(@AncestorInPath Jenkins context,
                                                   @QueryParameter("projectId") @RelativePath("..") final String projectId,
                                                   @QueryParameter("zone") final String zone,
                                                   @QueryParameter("credentialsId") @RelativePath("..") final String credentialsId) {
            ListBoxModel items = new ListBoxModel();
            items.add("");
            try {
                ComputeClient compute = computeClient(context, credentialsId);
                List<MachineType> machineTypes = compute.getMachineTypes(projectId, zone);

                for (MachineType m : machineTypes) {
                    items.add(m.getName(), m.getSelfLink());
                }
                return items;
            } catch (IOException ioe) {
                items.clear();
                items.add("Error retrieving machine types");
                return items;
            } catch (IllegalArgumentException iae) {
                //TODO log
                return null;
            }
        }

        public FormValidation doCheckMachineType(@QueryParameter String value) {
            if (value.equals("")) {
                return FormValidation.warning("Please select a machine type...");
            }
            return FormValidation.ok();
        }

        public ListBoxModel doFillNetworkItems(@AncestorInPath Jenkins context,
                                               @QueryParameter("projectId") @RelativePath("..") final String projectId,
                                               @QueryParameter("credentialsId") @RelativePath("..") final String credentialsId) {
            ListBoxModel items = new ListBoxModel();
            items.add("");

            try {
                ComputeClient compute = computeClient(context, credentialsId);
                List<Network> networks = compute.getNetworks(projectId);

                for (Network n : networks) {
                    items.add(n.getName(), n.getSelfLink());
                }
                return items;
            } catch (IOException ioe) {
                items.clear();
                items.add("Error retrieving networks");
                return items;
            } catch (IllegalArgumentException iae) {
                //TODO: log
                return null;
            }
        }

        public FormValidation doCheckNetwork(@QueryParameter String value) {
            if (value.equals("")) {
                return FormValidation.warning("Please select a network...");
            }
            return FormValidation.ok();
        }

        public ListBoxModel doFillSubnetworkItems(@AncestorInPath Jenkins context,
                                                  @QueryParameter("region") final String region,
                                                  @QueryParameter("network") final String network,
                                                  @QueryParameter("projectId") @RelativePath("..") final String projectId,
                                                  @QueryParameter("credentialsId") @RelativePath("..") final String credentialsId) {
            ListBoxModel items = new ListBoxModel();

            if (network.endsWith("default")) {
                items.add(new ListBoxModel.Option("default", "default", true));
                return items;
            }

            try {
                ComputeClient compute = computeClient(context, credentialsId);
                List<Subnetwork> subnetworks = compute.getSubnetworks(projectId, network, region);

                if (subnetworks.size() == 0) {
                    items.add(new ListBoxModel.Option(ERROR_NO_SUBNETS, ERROR_NO_SUBNETS, true));
                    return items;
                }

                for (Subnetwork s : subnetworks) {
                    items.add(s.getName(), s.getSelfLink());
                }
                return items;
            } catch (IOException ioe) {
                items.clear();
                items.add("Error retrieving subnetworks");
                return items;
            } catch (IllegalArgumentException iae) {
                //TODO: log
                return null;
            }
        }

        public FormValidation doCheckSubnetwork(@QueryParameter String value) {
            if (value.equals(ERROR_NO_SUBNETS)) {

                return FormValidation.error(ERROR_NO_SUBNETS);
            }
            if (value.isEmpty()) {
                return FormValidation.warning("Please select a subnetwork...");
            }

            return FormValidation.ok();
        }

        public ListBoxModel doFillBootDiskTypeItems(@AncestorInPath Jenkins context,
                                                    @QueryParameter("projectId") @RelativePath("..") final String projectId,
                                                    @QueryParameter("zone") String zone,
                                                    @QueryParameter("credentialsId") @RelativePath("..") final String credentialsId) {
            ListBoxModel items = new ListBoxModel();
            try {
                ComputeClient compute = computeClient(context, credentialsId);
                List<DiskType> diskTypes = compute.getBootDiskTypes(projectId, zone);

                for (DiskType dt : diskTypes) {
                    items.add(dt.getName(), dt.getSelfLink());
                }
                return items;
            } catch (IOException ioe) {
                items.clear();
                items.add("Error retrieving disk types");
                return items;
            } catch (IllegalArgumentException iae) {
                //TODO: log
                return null;
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

            try {
                ComputeClient compute = computeClient(context, credentialsId);
                List<Image> images = compute.getImages(projectId);

                for (Image i : images) {
                    items.add(i.getName(), i.getSelfLink());
                }
            } catch (IOException ioe) {
                items.clear();
                items.add("Error retrieving images for project");
            } catch (IllegalArgumentException iae) {
                //TODO: log
                return null;
            }
            return items;
        }

        public FormValidation doCheckLabelString(@QueryParameter String value, @QueryParameter Node.Mode mode) {
            if (mode == Node.Mode.EXCLUSIVE && (value == null || value.trim().isEmpty())) {
                return FormValidation.warning("You may want to assign labels to this node;"
                        + " it's marked to only run jobs that are exclusively tied to itself or a label.");
            }
            return FormValidation.ok();
        }

        private static ComputeClient computeClient(Jenkins context, String credentialsId) throws IOException {
            if (computeClient != null) {
                return computeClient;
            }
            ClientFactory clientFactory = new ClientFactory(context, new ArrayList<DomainRequirement>(), credentialsId);
            return clientFactory.compute();
        }
    }
}
