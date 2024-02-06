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

import static com.google.cloud.graphite.platforms.plugin.client.util.ClientUtil.nameFromSelfLink;
import static com.google.jenkins.plugins.computeengine.ComputeEngineCloud.checkPermissions;

import com.google.api.services.compute.model.AcceleratorConfig;
import com.google.api.services.compute.model.AttachedDisk;
import com.google.api.services.compute.model.AttachedDiskInitializeParams;
import com.google.api.services.compute.model.DiskType;
import com.google.api.services.compute.model.Image;
import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.InstanceTemplate;
import com.google.api.services.compute.model.MachineType;
import com.google.api.services.compute.model.Metadata;
import com.google.api.services.compute.model.NetworkInterface;
import com.google.api.services.compute.model.Operation;
import com.google.api.services.compute.model.Region;
import com.google.api.services.compute.model.Scheduling;
import com.google.api.services.compute.model.ServiceAccount;
import com.google.api.services.compute.model.Tags;
import com.google.api.services.compute.model.Zone;
import com.google.cloud.graphite.platforms.plugin.client.ClientFactory;
import com.google.cloud.graphite.platforms.plugin.client.ComputeClient;
import com.google.common.base.Strings;
import com.google.jenkins.plugins.computeengine.client.ClientUtil;
import com.google.jenkins.plugins.computeengine.ssh.GoogleKeyCredential;
import com.google.jenkins.plugins.computeengine.ssh.GoogleKeyPair;
import com.google.jenkins.plugins.computeengine.ssh.GooglePrivateKey;
import edu.umd.cs.findbugs.annotations.Nullable;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.RelativePath;
import hudson.Util;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.labels.LabelAtom;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import jenkins.model.Jenkins;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.java.Log;
import org.apache.commons.lang.NotImplementedException;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.text.RandomStringGenerator;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

@Getter
@Setter(onMethod = @__(@DataBoundSetter))
/* TODO(rzwitserloot/lombok#2050): Prevent duplicate methods called "build" for custom build method
 *   until lombok 1.18.8 is released. */
@Builder(builderClassName = "Builder", buildMethodName = "notbuild")
@AllArgsConstructor
@Log
public class InstanceConfiguration implements Describable<InstanceConfiguration> {
    public static final String GUEST_ATTRIBUTES_METADATA_KEY = "enable-guest-attributes";
    public static final String SSH_METADATA_KEY = "ssh-keys";
    public static final Long DEFAULT_BOOT_DISK_SIZE_GB = 10L;
    public static final Integer DEFAULT_NUM_EXECUTORS = 1;
    public static final Integer DEFAULT_LAUNCH_TIMEOUT_SECONDS = 300;
    public static final Integer DEFAULT_RETENTION_TIME_MINUTES = (DEFAULT_LAUNCH_TIMEOUT_SECONDS / 60) + 1;
    public static final String DEFAULT_RUN_AS_USER = "jenkins";
    public static final String METADATA_LINUX_STARTUP_SCRIPT_KEY = "startup-script";
    public static final String METADATA_WINDOWS_STARTUP_SCRIPT_KEY = "windows-startup-script-ps1";
    public static final List<String> KNOWN_IMAGE_PROJECTS = Collections.unmodifiableList(new ArrayList<String>() {
        {
            add("centos-cloud");
            add("coreos-cloud");
            add("cos-cloud");
            add("debian-cloud");
            add("rhel-cloud");
            add("suse-cloud");
            add("suse-sap-cloud");
            add("ubuntu-os-cloud");
            add("windows-cloud");
            add("windows-sql-cloud");
        }
    });

    private String description;
    private String namePrefix;
    private String region;
    private String zone;
    private String machineType;
    private String numExecutorsStr;
    private String startupScript;
    private boolean preemptible;
    private String minCpuPlatform;
    private String labels;
    private String runAsUser;
    private String bootDiskType;
    private boolean bootDiskAutoDelete;
    private String bootDiskSourceImageName;
    private String bootDiskSourceImageProject;
    private NetworkConfiguration networkConfiguration;
    private NetworkInterfaceIpStackMode networkInterfaceIpStackMode;

    @Deprecated
    private Boolean externalAddress;

    private boolean useInternalAddress;
    private boolean ignoreProxy;
    private String networkTags;
    private String serviceAccountEmail;
    private Node.Mode mode;
    private AcceleratorConfiguration acceleratorConfiguration;
    private String retentionTimeMinutesStr;
    private String launchTimeoutSecondsStr;
    private String bootDiskSizeGbStr;
    private boolean oneShot;
    private String template;
    // Optional not possible due to serialization requirement
    @Nullable
    private WindowsConfiguration windowsConfiguration;

    @Nullable
    private SshConfiguration sshConfiguration;

    private boolean createSnapshot;
    private String remoteFs;
    private String javaExecPath;
    private GoogleKeyCredential sshKeyCredential;
    private Map<String, String> googleLabels;
    private Integer numExecutors;
    private Integer retentionTimeMinutes;
    private Integer launchTimeoutSeconds;
    private Long bootDiskSizeGb;
    private transient Set<LabelAtom> labelSet;

    @Getter(AccessLevel.PROTECTED)
    @Setter(AccessLevel.PROTECTED)
    protected transient ComputeEngineCloud cloud;

    private static List<Metadata.Items> mergeMetadataItems(List<Metadata.Items> winner, List<Metadata.Items> loser) {
        if (loser == null) {
            loser = new ArrayList<Metadata.Items>();
        }

        for (Metadata.Items existing : loser) {
            String existingKey = existing.getKey();
            Metadata.Items duplicate = winner.stream()
                    .filter(m -> m.getKey().equals(existingKey))
                    .findFirst()
                    .orElse(null);
            if (duplicate == null) {
                winner.add(existing);
            } else if (existingKey.equals(SSH_METADATA_KEY)) {
                duplicate.setValue(duplicate.getValue() + "\n" + existing.getValue());
            }
        }
        return winner;
    }

    @DataBoundConstructor
    public InstanceConfiguration() {}

    @DataBoundSetter
    public void setNumExecutorsStr(String numExecutorsStr) {
        this.numExecutors = intOrDefault(numExecutorsStr, DEFAULT_NUM_EXECUTORS);
        this.numExecutorsStr = numExecutors.toString();
    }

    @DataBoundSetter
    public void setLabelString(String labelString) {
        this.labels = Util.fixNull(labelString);
        readResolve();
    }

    @DataBoundSetter
    public void setNetworkTags(String networkTags) {
        this.networkTags = Util.fixNull(networkTags).trim();
    }

    @DataBoundSetter
    public void setRetentionTimeMinutesStr(String retentionTimeMinutesStr) {
        this.retentionTimeMinutes = intOrDefault(retentionTimeMinutesStr, DEFAULT_RETENTION_TIME_MINUTES);
        this.retentionTimeMinutesStr = this.retentionTimeMinutes.toString();
    }

    @DataBoundSetter
    public void setLaunchTimeoutSecondsStr(String launchTimeoutSecondsStr) {
        this.launchTimeoutSeconds = intOrDefault(launchTimeoutSecondsStr, DEFAULT_LAUNCH_TIMEOUT_SECONDS);
        this.launchTimeoutSecondsStr = this.launchTimeoutSeconds.toString();
    }

    @DataBoundSetter
    public void setBootDiskSizeGbStr(String bootDiskSizeGbStr) {
        this.bootDiskSizeGb = longOrDefault(bootDiskSizeGbStr, DEFAULT_BOOT_DISK_SIZE_GB);
        this.bootDiskSizeGbStr = this.bootDiskSizeGb.toString();
    }

    @DataBoundSetter
    public void setOneShot(boolean oneShot) {
        this.oneShot = oneShot;
        this.createSnapshot &= oneShot;
    }

    @DataBoundSetter
    public void setCreateSnapshot(boolean createSnapshot) {
        this.createSnapshot = createSnapshot && this.oneShot;
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
            return s.substring(s.indexOf("/projects/") + 1);
        }
        return s;
    }

    @SuppressWarnings("unchecked")
    public Descriptor<InstanceConfiguration> getDescriptor() {
        return Jenkins.get().getDescriptor(getClass());
    }

    public String getLabelString() {
        return labels;
    }

    public Set<LabelAtom> getLabelSet() {
        return labelSet;
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

    public ComputeEngineInstance provision() throws IOException {
        try {
            Instance instance = instance();
            // TODO: JENKINS-55285
            Operation operation =
                    cloud.getClient().insertInstance(cloud.getProjectId(), Optional.ofNullable(template), instance);
            log.info("Sent insert request for instance configuration [" + description + "]");
            String targetRemoteFs = this.remoteFs;
            ComputeEngineComputerLauncher launcher;
            if (this.windowsConfiguration != null) {
                launcher = new ComputeEngineWindowsLauncher(cloud.getCloudName(), operation, this.useInternalAddress);
                if (Strings.isNullOrEmpty(targetRemoteFs)) {
                    targetRemoteFs = "C:\\";
                }
            } else {
                launcher = new ComputeEngineLinuxLauncher(cloud.getCloudName(), operation, this.useInternalAddress);
                if (Strings.isNullOrEmpty(targetRemoteFs)) {
                    targetRemoteFs = "/tmp";
                }
            }
            return ComputeEngineInstance.builder()
                    .cloud(cloud)
                    .cloudName(cloud.name)
                    .name(instance.getName())
                    .zone(instance.getZone())
                    .nodeDescription(instance.getDescription())
                    .sshUser(runAsUser)
                    .remoteFS(targetRemoteFs)
                    .windowsConfig(windowsConfiguration)
                    .sshConfig(sshConfiguration)
                    .createSnapshot(createSnapshot)
                    .oneShot(oneShot)
                    .ignoreProxy(ignoreProxy)
                    .numExecutors(numExecutors)
                    .mode(mode)
                    .labelString(labels)
                    .launcher(launcher)
                    .retentionStrategy(new ComputeEngineRetentionStrategy(retentionTimeMinutes, oneShot))
                    .launchTimeout(getLaunchTimeoutMillis())
                    .javaExecPath(javaExecPath)
                    .sshKeyCredential(sshKeyCredential)
                    .build();
        } catch (Descriptor.FormException fe) {
            log.log(Level.WARNING, "Error provisioning instance: " + fe.getMessage(), fe);
            return null;
        }
    }

    /** Initializes transient properties */
    protected Object readResolve() {
        labelSet = Label.parse(labels);
        if (externalAddress != null) {
            this.networkInterfaceIpStackMode = new NetworkInterfaceSingleStack(externalAddress);
            this.externalAddress = null;
        }
        return this;
    }

    public Instance instance() throws IOException {
        Instance instance = new Instance();
        instance.setName(uniqueName());
        instance.setDescription(description);
        instance.setZone(nameFromSelfLink(zone));
        instance.setMetadata(newMetadata());

        if (windowsConfiguration == null) {
            if (sshConfiguration != null) {
                log.info("User selected to use a custom ssh private key");
                sshKeyCredential =
                        configureSSHPrivateKey(sshConfiguration.getCustomPrivateKeyCredentialsId(), runAsUser);
            } else {
                log.info("User selected to use an autogenerated ssh key pair");
                sshKeyCredential = configureSSHKeyPair(instance, runAsUser);
            }
        }

        if (StringUtils.isNotEmpty(template)) {
            InstanceTemplate instanceTemplate =
                    cloud.getClient().getTemplate(nameFromSelfLink(cloud.getProjectId()), nameFromSelfLink(template));
            /* Since we have to set the metadata to include the autogenerated SSH keypair,
            we need to ensure we include metadata properties which might be set in the template. */
            if (instanceTemplate.getProperties() != null
                    && instanceTemplate.getProperties().getMetadata() != null
                    && instanceTemplate.getProperties().getMetadata().getItems() != null) {
                List<Metadata.Items> instanceTemplateItems =
                        instanceTemplate.getProperties().getMetadata().getItems();
                List<Metadata.Items> instanceItems = instance.getMetadata().getItems();
                instance.getMetadata().setItems(mergeMetadataItems(instanceItems, instanceTemplateItems));
            }

            Map<String, String> mergedLabels = new HashMap<>(googleLabels);
            if (instanceTemplate.getProperties().getLabels() != null) {
                Map<String, String> templateLabels =
                        instanceTemplate.getProperties().getLabels();
                mergedLabels.putAll(templateLabels);
            }
            instance.setLabels(mergedLabels);
        } else {
            configureStartupScript(instance);
            instance.setLabels(googleLabels);
            instance.setMachineType(stripSelfLinkPrefix(machineType));
            instance.setTags(tags());
            instance.setScheduling(scheduling());
            instance.setDisks(disks());
            instance.setGuestAccelerators(accelerators());
            instance.setNetworkInterfaces(networkInterfaces());
            instance.setServiceAccounts(serviceAccounts());

            // optional
            if (notNullOrEmpty(minCpuPlatform)) {
                instance.setMinCpuPlatform(minCpuPlatform);
            }
        }

        return instance;
    }

    private String uniqueName() {
        char[][] pairs = {{'a', 'z'}, {'0', '9'}};
        RandomStringGenerator generator =
                new RandomStringGenerator.Builder().withinRange(pairs).build();
        String suffix = generator.generate(6);

        String prefix = namePrefix;
        if (!prefix.endsWith(("-"))) {
            prefix += "-";
        }

        return prefix + suffix;
    }

    private Metadata newMetadata() {
        Metadata metadata = new Metadata();
        metadata.setItems(new ArrayList<>());
        metadata.getItems()
                .add(new Metadata.Items().setKey(GUEST_ATTRIBUTES_METADATA_KEY).setValue("TRUE"));
        return metadata;
    }

    /**
     * Called when user selects to use autogenerated ssh key pair
     *
     * @param instance current instance object
     * @param sshUser user selected during configuration of cloud
     * @return autogenerated ssh key pair
     */
    private GoogleKeyPair configureSSHKeyPair(Instance instance, String sshUser) {
        GoogleKeyPair sshKeyPair = GoogleKeyPair.generate(sshUser);
        instance.getMetadata()
                .getItems()
                .add(new Metadata.Items().setKey(SSH_METADATA_KEY).setValue(sshKeyPair.getPublicKey()));
        return sshKeyPair;
    }

    /**
     * Called when user selectes to use custom ssh private key
     *
     * @param credentialId the name of the private key the user has selected
     * @param sshUser user selected during configuration of cloud
     * @return custom ssh private key
     */
    private GooglePrivateKey configureSSHPrivateKey(String credentialId, String sshUser) {
        GooglePrivateKey sshPrivateKey = GooglePrivateKey.generate(credentialId, sshUser);
        return sshPrivateKey;
    }

    private void configureStartupScript(Instance instance) {
        if (notNullOrEmpty(startupScript)) {
            List<Metadata.Items> items = instance.getMetadata().getItems();
            if (windowsConfiguration != null) {
                items.add(new Metadata.Items()
                        .setKey(METADATA_WINDOWS_STARTUP_SCRIPT_KEY)
                        .setValue(startupScript));
            } else {
                items.add(new Metadata.Items()
                        .setKey(METADATA_LINUX_STARTUP_SCRIPT_KEY)
                        .setValue(startupScript));
            }
        }
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
                .setSourceImage(bootDiskSourceImageName));

        List<AttachedDisk> disks = new ArrayList<>();
        disks.add(boot);
        return disks;
    }

    private List<AcceleratorConfig> accelerators() {
        if (acceleratorConfiguration != null
                && notNullOrEmpty(acceleratorConfiguration.getGpuCount())
                && notNullOrEmpty(acceleratorConfiguration.getGpuType())) {
            List<AcceleratorConfig> accelerators = new ArrayList<>();
            accelerators.add(new AcceleratorConfig()
                    .setAcceleratorType(acceleratorConfiguration.getGpuType())
                    .setAcceleratorCount(acceleratorConfiguration.gpuCount()));
            return accelerators;
        }
        return null;
    }

    private List<NetworkInterface> networkInterfaces() {
        List<NetworkInterface> networkInterfaces = new ArrayList<>();

        NetworkInterface networkInterface = networkInterfaceIpStackMode.getNetworkInterface();

        // Don't include subnetwork name if using default
        if (!networkConfiguration.getSubnetwork().equals("default")) {
            networkInterface.setSubnetwork(stripSelfLinkPrefix(networkConfiguration.getSubnetwork()));
        }

        networkInterfaces.add(networkInterface);
        return networkInterfaces;
    }

    private List<ServiceAccount> serviceAccounts() {
        if (notNullOrEmpty(serviceAccountEmail)) {
            List<ServiceAccount> serviceAccounts = new ArrayList<>();
            serviceAccounts.add(new ServiceAccount()
                    .setEmail(serviceAccountEmail)
                    .setScopes(Arrays.asList(new String[] {"https://www.googleapis.com/auth/cloud-platform"})));
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
            return DEFAULT_RUN_AS_USER;
        }

        public static WindowsConfiguration defaultWindowsConfiguration() {
            return WindowsConfiguration.builder()
                    .passwordCredentialsId("")
                    .privateKeyCredentialsId("")
                    .build();
        }

        public static SshConfiguration defaultSshConfiguration() {
            return SshConfiguration.builder().customPrivateKeyCredentialsId("").build();
        }

        public static NetworkConfiguration defaultNetworkConfiguration() {
            return new AutofilledNetworkConfiguration();
        }

        private static ComputeClient computeClient(Jenkins context, String credentialsId) throws IOException {
            if (computeClient != null) {
                return computeClient;
            }
            ClientFactory clientFactory = ClientUtil.getClientFactory(context, credentialsId);
            return clientFactory.computeClient();
        }

        @Override
        public String getHelpFile(String fieldName) {
            String p = super.getHelpFile(fieldName);
            if (p == null) {
                Descriptor d = Jenkins.get().getDescriptor(ComputeEngineInstance.class);
                if (d != null) p = d.getHelpFile(fieldName);
            }
            return p;
        }

        public List<NetworkConfiguration.NetworkConfigurationDescriptor> getNetworkConfigurationDescriptors() {
            List<NetworkConfiguration.NetworkConfigurationDescriptor> d =
                    Jenkins.get().getDescriptorList(NetworkConfiguration.class);
            // No deprecated regions
            Iterator it = d.iterator();
            while (it.hasNext()) {
                NetworkConfiguration.NetworkConfigurationDescriptor o =
                        (NetworkConfiguration.NetworkConfigurationDescriptor) it.next();
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

        public ListBoxModel doFillRegionItems(
                @AncestorInPath Jenkins context,
                @QueryParameter("projectId") @RelativePath("..") final String projectId,
                @QueryParameter("credentialsId") @RelativePath("..") final String credentialsId) {
            checkPermissions(Jenkins.get(), Jenkins.ADMINISTER);
            ListBoxModel items = new ListBoxModel();
            items.add("");
            try {
                ComputeClient compute = computeClient(context, credentialsId);
                List<Region> regions = compute.listRegions(projectId);

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

        public ListBoxModel doFillTemplateItems(
                @AncestorInPath Jenkins context,
                @QueryParameter("projectId") @RelativePath("..") final String projectId,
                @QueryParameter("credentialsId") @RelativePath("..") final String credentialsId) {
            checkPermissions(Jenkins.get(), Jenkins.ADMINISTER);
            ListBoxModel items = new ListBoxModel();
            items.add("");
            try {
                ComputeClient compute = computeClient(context, credentialsId);
                List<InstanceTemplate> instanceTemplates = compute.listTemplates(projectId);

                for (InstanceTemplate instanceTemplate : instanceTemplates) {
                    items.add(instanceTemplate.getName(), instanceTemplate.getSelfLink());
                }
                return items;
            } catch (IOException ioe) {
                items.clear();
                items.add("Error retrieving instanceTemplates");
                return items;
            }
        }

        public FormValidation doCheckRegion(@QueryParameter String value) {
            if (StringUtils.isEmpty(value)) {
                return FormValidation.error("Please select a region...");
            }
            return FormValidation.ok();
        }

        public ListBoxModel doFillZoneItems(
                @AncestorInPath Jenkins context,
                @QueryParameter("projectId") @RelativePath("..") final String projectId,
                @QueryParameter("region") final String region,
                @QueryParameter("credentialsId") @RelativePath("..") final String credentialsId) {
            checkPermissions(Jenkins.get(), Jenkins.ADMINISTER);
            ListBoxModel items = new ListBoxModel();
            items.add("");
            try {
                ComputeClient compute = computeClient(context, credentialsId);
                List<Zone> zones = compute.listZones(projectId, region);

                for (Zone z : zones) {
                    items.add(z.getName(), z.getSelfLink());
                }
                return items;
            } catch (IOException ioe) {
                items.clear();
                items.add("Error retrieving zones");
                return items;
            } catch (IllegalArgumentException iae) {
                // TODO log
                return null;
            }
        }

        public FormValidation doCheckZone(@QueryParameter String value) {
            if (StringUtils.isEmpty(value)) {
                return FormValidation.error("Please select a zone...");
            }
            return FormValidation.ok();
        }

        public ListBoxModel doFillMachineTypeItems(
                @AncestorInPath Jenkins context,
                @QueryParameter("projectId") @RelativePath("..") final String projectId,
                @QueryParameter("zone") final String zone,
                @QueryParameter("credentialsId") @RelativePath("..") final String credentialsId) {
            checkPermissions(Jenkins.get(), Jenkins.ADMINISTER);
            ListBoxModel items = new ListBoxModel();
            items.add("");
            try {
                ComputeClient compute = computeClient(context, credentialsId);
                List<MachineType> machineTypes = compute.listMachineTypes(projectId, zone);

                for (MachineType m : machineTypes) {
                    items.add(m.getName(), m.getSelfLink());
                }
                return items;
            } catch (IOException ioe) {
                items.clear();
                items.add("Error retrieving machine types");
                return items;
            } catch (IllegalArgumentException iae) {
                // TODO log
                return null;
            }
        }

        public FormValidation doCheckMachineType(@QueryParameter String value) {
            if (StringUtils.isEmpty(value)) {
                return FormValidation.error("Please select a machine type...");
            }
            return FormValidation.ok();
        }

        public ListBoxModel doFillMinCpuPlatformItems(
                @AncestorInPath Jenkins context,
                @QueryParameter("projectId") @RelativePath("..") final String projectId,
                @QueryParameter("zone") final String zone,
                @QueryParameter("credentialsId") @RelativePath("..") final String credentialsId) {
            checkPermissions(Jenkins.get(), Jenkins.ADMINISTER);
            ListBoxModel items = new ListBoxModel();
            items.add("");
            try {
                ComputeClient compute = computeClient(context, credentialsId);
                List<String> cpuPlatforms = compute.listCpuPlatforms(projectId, zone);

                for (String cpuPlatform : cpuPlatforms) {
                    items.add(cpuPlatform);
                }
                return items;
            } catch (IOException ioe) {
                items.clear();
                items.add("Error retrieving cpu Platforms");
                return items;
            } catch (IllegalArgumentException iae) {
                // TODO log
                return null;
            }
        }

        public ListBoxModel doFillBootDiskTypeItems(
                @AncestorInPath Jenkins context,
                @QueryParameter("projectId") @RelativePath("..") final String projectId,
                @QueryParameter("zone") String zone,
                @QueryParameter("credentialsId") @RelativePath("..") final String credentialsId) {
            checkPermissions(Jenkins.get(), Jenkins.ADMINISTER);
            ListBoxModel items = new ListBoxModel();
            try {
                ComputeClient compute = computeClient(context, credentialsId);
                List<DiskType> diskTypes = compute.listBootDiskTypes(projectId, zone);

                for (DiskType dt : diskTypes) {
                    items.add(dt.getName(), dt.getSelfLink());
                }
                return items;
            } catch (IOException ioe) {
                items.clear();
                items.add("Error retrieving disk types");
                return items;
            } catch (IllegalArgumentException iae) {
                // TODO: log
                return null;
            }
        }

        public ListBoxModel doFillBootDiskSourceImageProjectItems(
                @AncestorInPath Jenkins context,
                @QueryParameter("projectId") @RelativePath("..") final String projectId) {
            checkPermissions(Jenkins.get(), Jenkins.ADMINISTER);
            ListBoxModel items = new ListBoxModel();
            items.add("");
            items.add(projectId);
            for (String v : KNOWN_IMAGE_PROJECTS) {
                items.add(v);
            }
            return items;
        }

        public FormValidation doCheckBootDiskSourceImageProject(@QueryParameter String value) {
            if (StringUtils.isEmpty(value)) {
                return FormValidation.warning("Please select source image project...");
            }
            return FormValidation.ok();
        }

        public ListBoxModel doFillBootDiskSourceImageNameItems(
                @AncestorInPath Jenkins context,
                @QueryParameter("bootDiskSourceImageProject") final String projectId,
                @QueryParameter("credentialsId") @RelativePath("..") final String credentialsId) {
            checkPermissions(Jenkins.get(), Jenkins.ADMINISTER);
            ListBoxModel items = new ListBoxModel();
            items.add("");
            try {
                ComputeClient compute = computeClient(context, credentialsId);
                List<Image> images = compute.listImages(projectId);

                for (Image i : images) {
                    items.add(i.getName(), i.getSelfLink());
                }
            } catch (IOException ioe) {
                items.clear();
                items.add("Error retrieving images for project");
            } catch (IllegalArgumentException iae) {
                // TODO: log
                return null;
            }
            return items;
        }

        public FormValidation doCheckBootDiskSourceImageName(@QueryParameter String value) {
            if (StringUtils.isEmpty(value)) {
                return FormValidation.warning("Please select source image...");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckBootDiskSizeGbStr(
                @AncestorInPath Jenkins context,
                @QueryParameter String value,
                @QueryParameter("bootDiskSourceImageProject") final String projectId,
                @QueryParameter("bootDiskSourceImageName") final String imageName,
                @QueryParameter("credentialsId") @RelativePath("..") final String credentialsId) {
            checkPermissions(Jenkins.get(), Jenkins.ADMINISTER);
            if (Strings.isNullOrEmpty(credentialsId)
                    || Strings.isNullOrEmpty(projectId)
                    || Strings.isNullOrEmpty(imageName)) return FormValidation.ok();

            try {
                ComputeClient compute = computeClient(context, credentialsId);
                Image i = compute.getImage(nameFromSelfLink(projectId), nameFromSelfLink(imageName));
                if (i == null) return FormValidation.error("Could not find image " + imageName);
                Long bootDiskSizeGb = Long.parseLong(value);
                if (bootDiskSizeGb < i.getDiskSizeGb()) {
                    return FormValidation.error(String.format(
                            "The disk image you have chosen requires a minimum of %dGB. Please increase boot disk size to accommodate.",
                            i.getDiskSizeGb()));
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

        public FormValidation doCheckCreateSnapshot(
                @AncestorInPath Jenkins context,
                @QueryParameter boolean value,
                @QueryParameter("oneShot") boolean oneShot) {
            if (!oneShot && value) {
                return FormValidation.error(Messages.InstanceConfiguration_SnapshotConfigError());
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckNumExecutorsStr(
                @AncestorInPath Jenkins context,
                @QueryParameter String value,
                @QueryParameter("oneShot") boolean oneShot) {
            int numExecutors = intOrDefault(value, DEFAULT_NUM_EXECUTORS);
            if (numExecutors < 1) {
                return FormValidation.error(Messages.InstanceConfiguration_NumExecutorsLessThanOneConfigError());
            } else if (numExecutors > 1 && oneShot) {
                return FormValidation.error(Messages.InstanceConfiguration_NumExecutorsOneShotError());
            }
            return FormValidation.ok();
        }

        public List<NetworkInterfaceIpStackMode.Descriptor> getNetworkInterfaceIpStackModeDescriptors() {
            return ExtensionList.lookup(NetworkInterfaceIpStackMode.Descriptor.class);
        }
    }

    public static class Builder {
        public InstanceConfiguration build() {
            InstanceConfiguration instanceConfiguration = new InstanceConfiguration();
            instanceConfiguration.setDescription(this.description);
            instanceConfiguration.setNamePrefix(this.namePrefix);
            instanceConfiguration.setRegion(this.region);
            instanceConfiguration.setZone(this.zone);
            instanceConfiguration.setMachineType(this.machineType);
            instanceConfiguration.setNumExecutorsStr(this.numExecutorsStr);
            instanceConfiguration.setStartupScript(this.startupScript);
            instanceConfiguration.setPreemptible(this.preemptible);
            instanceConfiguration.setMinCpuPlatform(this.minCpuPlatform);
            instanceConfiguration.setLabelString(this.labels);
            instanceConfiguration.setRunAsUser(this.runAsUser);
            instanceConfiguration.setWindowsConfiguration(this.windowsConfiguration);
            instanceConfiguration.setSshConfiguration(this.sshConfiguration);
            instanceConfiguration.setBootDiskType(this.bootDiskType);
            instanceConfiguration.setBootDiskAutoDelete(this.bootDiskAutoDelete);
            instanceConfiguration.setBootDiskSourceImageName(this.bootDiskSourceImageName);
            instanceConfiguration.setBootDiskSourceImageProject(this.bootDiskSourceImageProject);
            instanceConfiguration.setNetworkConfiguration(this.networkConfiguration);
            instanceConfiguration.setNetworkInterfaceIpStackMode(this.networkInterfaceIpStackMode);
            instanceConfiguration.setUseInternalAddress(this.useInternalAddress);
            instanceConfiguration.setIgnoreProxy(this.ignoreProxy);
            instanceConfiguration.setNetworkTags(this.networkTags);
            instanceConfiguration.setServiceAccountEmail(this.serviceAccountEmail);
            instanceConfiguration.setMode(this.mode);
            instanceConfiguration.setAcceleratorConfiguration(this.acceleratorConfiguration);
            instanceConfiguration.setRetentionTimeMinutesStr(this.retentionTimeMinutesStr);
            instanceConfiguration.setLaunchTimeoutSecondsStr(this.launchTimeoutSecondsStr);
            instanceConfiguration.setBootDiskSizeGbStr(this.bootDiskSizeGbStr);
            instanceConfiguration.setOneShot(this.oneShot);
            instanceConfiguration.setTemplate(this.template);
            instanceConfiguration.setCreateSnapshot(this.createSnapshot);
            instanceConfiguration.setRemoteFs(this.remoteFs);
            instanceConfiguration.setJavaExecPath(this.javaExecPath);
            instanceConfiguration.setCloud(this.cloud);
            if (googleLabels != null) {
                instanceConfiguration.appendLabels(this.googleLabels);
            }
            return instanceConfiguration;
        }

        // Private methods defined to exclude these from the builder and skip Lombok generating them.
        @SuppressFBWarnings(value = "UPM_UNCALLED_PRIVATE_METHOD", justification = "for Lombok")
        private Builder numExecutors(Integer numExecutors) {
            throw new NotImplementedException();
        }

        private Builder retentionTimeMinutes(Integer retentionTimeMinutes) {
            throw new NotImplementedException();
        }

        private Builder launchTimeoutSeconds(Integer launchTimeoutSeconds) {
            throw new NotImplementedException();
        }

        private Builder bootDiskSizeGb(Long bootDiskSizeGb) {
            throw new NotImplementedException();
        }

        private Builder labelSet(Set<LabelAtom> labelSet) {
            throw new NotImplementedException();
        }
    }
}
