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

import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.google.api.services.compute.model.*;
import com.google.common.base.Strings;
import com.google.jenkins.plugins.computeengine.client.ClientFactory;
import com.google.jenkins.plugins.computeengine.client.ComputeClient;
import hudson.Extension;
import hudson.RelativePath;
import hudson.Util;
import hudson.model.*;
import hudson.model.labels.LabelAtom;
import hudson.slaves.CloudRetentionStrategy;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.apache.commons.text.RandomStringGenerator;
import org.jenkinsci.plugins.durabletask.executors.OnceRetentionStrategy;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.IOException;
import java.io.PrintStream;
import java.util.*;

public class InstanceConfiguration implements Describable<InstanceConfiguration> {
    public static final Long DEFAULT_BOOT_DISK_SIZE_GB = 10L;
    public static final Integer DEFAULT_NUM_EXECUTORS = 1;
    public static final Integer DEFAULT_LAUNCH_TIMEOUT_SECONDS = 300;
    public static final Integer DEFAULT_RETENTION_TIME_MINUTES = (DEFAULT_LAUNCH_TIMEOUT_SECONDS / 60) + 1;
    public static final String DEFAULT_RUN_AS_USER = "jenkins";
    public static final String ERROR_NO_SUBNETS = "No subnetworks exist in the given network and region.";
    public static final String METADATA_STARTUP_SCRIPT_KEY = "startup-script";
    public static final String NAT_TYPE = "ONE_TO_ONE_NAT";
    public static final String NAT_NAME = "External NAT";
    public static final List<String> KNOWN_LINUX_IMAGE_PROJECTS = Collections.unmodifiableList(new ArrayList<String>() {{
        add("centos-cloud");
        add("coreos-cloud");
        add("cos-cloud");
        add("debian-cloud");
        add("rhel-cloud");
        add("suse-cloud");
        add("suse-sap-cloud");
        add("ubuntu-os-cloud");
    }});
    public final String description;
    public final String namePrefix;
    public final String region;
    public final String zone;
    public final String machineType;
    public final String numExecutorsStr;
    public final String startupScript;
    public final boolean preemptible;
    public final String minCpuPlatform;
    public final String labels;
    public final String runAsUser;
    public final String bootDiskType;
    public final boolean bootDiskAutoDelete;
    public final String bootDiskSourceImageName;
    public final String bootDiskSourceImageProject;
    public final NetworkConfiguration networkConfiguration;
    public final boolean externalAddress;
    public final boolean useInternalAddress;
    public final String networkTags;
    public final String serviceAccountEmail;
    public final Node.Mode mode;
    public final AcceleratorConfiguration acceleratorConfiguration;
    public final String retentionTimeMinutesStr;
    public final String launchTimeoutSecondsStr;
    public final String bootDiskSizeGbStr;
    public final boolean oneShot;
    public Map<String, String> googleLabels;
    public Integer numExecutors;
    public Integer retentionTimeMinutes;
    public Integer launchTimeoutSeconds;
    public Long bootDiskSizeGb;
    public transient Set<LabelAtom> labelSet;
    protected transient ComputeEngineCloud cloud;

    @DataBoundConstructor
    public InstanceConfiguration(String namePrefix,
                                 String region,
                                 String zone,
                                 String machineType,
                                 String numExecutorsStr,
                                 String startupScript,
                                 boolean preemptible,
                                 String minCpuPlatform,
                                 String labelString,
                                 String description,
                                 String bootDiskType,
                                 boolean bootDiskAutoDelete,
                                 String bootDiskSourceImageName,
                                 String bootDiskSourceImageProject,
                                 String bootDiskSizeGbStr,
                                 NetworkConfiguration networkConfiguration,
                                 boolean externalAddress,
                                 boolean useInternalAddress,
                                 String networkTags,
                                 String serviceAccountEmail,
                                 String retentionTimeMinutesStr,
                                 String launchTimeoutSecondsStr,
                                 Node.Mode mode,
                                 AcceleratorConfiguration acceleratorConfiguration,
                                 String runAsUser,
                                 boolean oneShot) {
        this.namePrefix = namePrefix;
        this.region = region;
        this.zone = zone;
        this.machineType = machineType;
        this.description = description;
        this.startupScript = startupScript;
        this.preemptible = preemptible;
        this.minCpuPlatform = minCpuPlatform;
        this.numExecutors = intOrDefault(numExecutorsStr, DEFAULT_NUM_EXECUTORS);
        this.oneShot = oneShot;
        this.numExecutorsStr = numExecutors.toString();
        this.retentionTimeMinutes = intOrDefault(retentionTimeMinutesStr, DEFAULT_RETENTION_TIME_MINUTES);
        this.retentionTimeMinutesStr = retentionTimeMinutes.toString();
        this.launchTimeoutSeconds = intOrDefault(launchTimeoutSecondsStr, DEFAULT_LAUNCH_TIMEOUT_SECONDS);
        this.launchTimeoutSecondsStr = launchTimeoutSeconds.toString();

        // Boot disk
        this.bootDiskType = bootDiskType;
        this.bootDiskAutoDelete = bootDiskAutoDelete;
        this.bootDiskSourceImageName = bootDiskSourceImageName;
        this.bootDiskSourceImageProject = bootDiskSourceImageProject;
        this.bootDiskSizeGb = longOrDefault(bootDiskSizeGbStr, DEFAULT_BOOT_DISK_SIZE_GB);
        this.bootDiskSizeGbStr = bootDiskSizeGb.toString();

        // Network
        this.networkConfiguration = networkConfiguration;
        this.externalAddress = externalAddress;
        this.useInternalAddress = useInternalAddress;
        this.networkTags = Util.fixNull(networkTags).trim();

        // IAM
        this.serviceAccountEmail = serviceAccountEmail;

        // Other
        this.acceleratorConfiguration = acceleratorConfiguration;
        this.mode = mode;
        this.labels = Util.fixNull(labelString);
        this.runAsUser = runAsUser;

        readResolve();
    }

    public static Integer intOrDefault(String toParse, Integer defaultTo) {
        Integer toReturn;
        try {
            toReturn = Integer.parseInt(toParse);
        } catch (NumberFormatException nfe) {
            toReturn = defaultTo;
        }
        return toReturn;
    }

    public static Long longOrDefault(String toParse, Long defaultTo) {
        Long toReturn;
        try {
            toReturn = Long.parseLong(toParse);
        } catch (NumberFormatException nfe) {
            toReturn = defaultTo;
        }
        return toReturn;
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

    public int getLaunchTimeoutMillis() {
        return launchTimeoutSeconds * 1000;
    }

    public void appendLabels(Map<String, String> labels) {
        if (googleLabels == null) {
            googleLabels = new HashMap<>();
        }
        googleLabels.putAll(labels);
    }

    public void appendLabel(String key, String value) {
        if (googleLabels == null) {
            googleLabels = new HashMap<>();
        }
        googleLabels.put(key, value);
    }

    public ComputeEngineInstance provision(TaskListener listener, Label requiredLabel) throws IOException {
        PrintStream logger = listener.getLogger();
        try {
            Instance i = instance();
            Operation operation = cloud.client.insertInstance(cloud.projectId, i);
            logger.println("Sent insert request");
            ComputeEngineComputerLauncher launcher = new ComputeEngineLinuxLauncher(cloud.getCloudName(), operation, this.useInternalAddress);
            
            ComputeEngineInstance instance = new ComputeEngineInstance(
                    cloud.name,
                    i.getName(),
                    i.getZone(), 
                    i.getDescription(),
                    runAsUser,
                    "./.jenkins-slave",
                    numExecutors, 
                    mode, 
                    requiredLabel == null ? "" : requiredLabel.getName(),
                    launcher,
                    (oneShot ? new OnceRetentionStrategy(retentionTimeMinutes) : new CloudRetentionStrategy(retentionTimeMinutes)),
                    getLaunchTimeoutMillis(),
                    oneShot);
            return instance;
        } catch (Descriptor.FormException fe) {
            logger.printf("Error provisioning instance: %s", fe.getMessage());
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

        //optional
        if (notNullOrEmpty(minCpuPlatform)) {
            i.setMinCpuPlatform(minCpuPlatform);
        }
        return i;
    }

    private String uniqueName() {
        char[][] pairs = {{'a', 'z'}, {'0', '9'}};
        RandomStringGenerator generator = new RandomStringGenerator.Builder()
                .withinRange(pairs)
                .build();
        String suffix = generator.generate(6);

        String prefix = namePrefix;
        if (!prefix.endsWith(("-"))) {
            prefix += "-";
        }

        return prefix + suffix;
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
                .setAccessConfigs(accessConfigs);

        // Don't include subnetwork name if using default
        if (!networkConfiguration.getSubnetwork().equals("default")) {
            nic.setSubnetwork(stripSelfLinkPrefix(networkConfiguration.getSubnetwork()));
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

    @Extension
    public static final class DescriptorImpl extends Descriptor<InstanceConfiguration> {
        private static ComputeClient computeClient;

        public static void setComputeClient(ComputeClient client) {
            computeClient = client;
        }

        public static String defaultRetentionTimeMinutes() {
            return DEFAULT_RETENTION_TIME_MINUTES.toString();
        }

        public static String defaultLaunchTimeoutSeconds() {
            return DEFAULT_LAUNCH_TIMEOUT_SECONDS.toString();
        }

        public static String defaultBootDiskSizeGb() {
            return DEFAULT_BOOT_DISK_SIZE_GB.toString();
        }

        public static String defaultBootDiskAutoDelete() {
            return "true";
        }

        public static String defaultRunAsUser() {
            return DEFAULT_RUN_AS_USER.toString();
        }

        public static NetworkConfiguration defaultNetworkConfiguration() {
            return new AutofilledNetworkConfiguration();
        }

        private static ComputeClient computeClient(Jenkins context, String credentialsId) throws IOException {
            if (computeClient != null) {
                return computeClient;
            }
            ClientFactory clientFactory = new ClientFactory(context, new ArrayList<DomainRequirement>(), credentialsId);
            return clientFactory.compute();
        }

        @Override
        public String getHelpFile(String fieldName) {
            String p = super.getHelpFile(fieldName);
            if (p == null) {
                Descriptor d = Jenkins.getInstance().getDescriptor(ComputeEngineInstance.class);
                if (d != null)
                    p = d.getHelpFile(fieldName);
            }
            return p;
        }

        public List<NetworkConfiguration.NetworkConfigurationDescriptor> getNetworkConfigurationDescriptors() {
            List<NetworkConfiguration.NetworkConfigurationDescriptor> d = Jenkins.getInstance().getDescriptorList(NetworkConfiguration.class);
            // No deprecated regions
            Iterator it = d.iterator();
            while (it.hasNext()) {
                NetworkConfiguration.NetworkConfigurationDescriptor o = (NetworkConfiguration.NetworkConfigurationDescriptor) it.next();
                if (o.clazz.getName().equals("NetworkConfiguration")) {
                    it.remove();
                }
            }
            return d;
        }

        public FormValidation doCheckNetworkTags(@QueryParameter String value) {
            if (value == null || value.isEmpty()) {
                return FormValidation.ok();
            }

            String re = "[a-z]([-a-z0-9]*[a-z0-9])?";
            for (String tag : value.split(" ")) {
                if (!tag.matches(re)) {
                    return FormValidation.error("Tags must be space-delimited and each tag must match regex" + re);
                }
            }

            return FormValidation.ok();
        }

        public FormValidation doCheckNamePrefix(@QueryParameter String value) {
            if (value == null || value.isEmpty()) {
                return FormValidation.error("A prefix is required");
            }

            String re = "[a-z]([-a-z0-9]*[a-z0-9])?";
            if (!value.matches(re)) {
                return FormValidation.error("Prefix must match regex " + re);
            }

            Integer maxLen = 50;
            if (value.length() > maxLen) {
                return FormValidation.error("Maximum length is " + maxLen);
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckDescription(@QueryParameter String value) {
            if (value == null || value.isEmpty()) {
                return FormValidation.error("A description is required");
            }
            return FormValidation.ok();
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
                return FormValidation.error("Please select a region...");
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
                return FormValidation.error("Please select a zone...");
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
                return FormValidation.error("Please select a machine type...");
            }
            return FormValidation.ok();
        }

        public ListBoxModel doFillMinCpuPlatformItems(@AncestorInPath Jenkins context,
                                               @QueryParameter("projectId") @RelativePath("..") final String projectId,
                                               @QueryParameter("zone") final String zone,
                                               @QueryParameter("credentialsId") @RelativePath("..") final String credentialsId)
        {
            ListBoxModel items = new ListBoxModel();
            items.add("");
            try {
                ComputeClient compute = computeClient(context, credentialsId);
                List<String> cpuPlatforms = compute.cpuPlatforms(projectId, zone);

                for (String cpuPlatform : cpuPlatforms) {
                    items.add(cpuPlatform);
                }
                return items;
            } catch (IOException ioe) {
                items.clear();
                items.add("Error retrieving cpu Platforms");
                return items;
            } catch (IllegalArgumentException iae) {
                //TODO log
                return null;
            }
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
            for (String v : KNOWN_LINUX_IMAGE_PROJECTS) {
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
            items.add("");
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

        public FormValidation doCheckBootDiskSourceImageName(@QueryParameter String value) {
            if (value.equals("")) {
                return FormValidation.warning("Please select source image...");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckBootDiskSizeGbStr(@AncestorInPath Jenkins context,
                                                       @QueryParameter String value,
                                                       @QueryParameter("bootDiskSourceImageProject") final String projectId,
                                                       @QueryParameter("bootDiskSourceImageName") final String imageName,
                                                       @QueryParameter("credentialsId") @RelativePath("..") final String credentialsId) {
            if (Strings.isNullOrEmpty(credentialsId) || Strings.isNullOrEmpty(projectId) || Strings.isNullOrEmpty(imageName))
                return FormValidation.ok();

            try {
                ComputeClient compute = computeClient(context, credentialsId);
                Image i = compute.getImage(ComputeClient.lastParam(projectId), ComputeClient.lastParam(imageName));
                if (i == null)
                    return FormValidation.error("Could not find image " + imageName);
                Long bootDiskSizeGb = Long.parseLong(value);
                if (bootDiskSizeGb < i.getDiskSizeGb()) {
                    return FormValidation.error(String.format("The disk image you have chosen requires a minimum of %dGB. Please increase boot disk size to accommodate.", i.getDiskSizeGb()));
                }
            } catch (IOException ioe) {
                return FormValidation.error(ioe, "Error validating boot disk size");
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
