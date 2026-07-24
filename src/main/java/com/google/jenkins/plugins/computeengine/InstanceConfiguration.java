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
import com.google.api.services.compute.model.ShieldedInstanceConfig;
import com.google.api.services.compute.model.Tags;
import com.google.api.services.compute.model.Zone;
import com.google.cloud.graphite.platforms.plugin.client.ClientFactory;
import com.google.cloud.graphite.platforms.plugin.client.ComputeClient;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.jenkins.plugins.computeengine.client.ClientUtil;
import com.google.jenkins.plugins.computeengine.config.PreemptibleVm;
import com.google.jenkins.plugins.computeengine.config.ProvisioningType;
import com.google.jenkins.plugins.computeengine.config.Standard;
import com.google.jenkins.plugins.computeengine.ssh.GoogleKeyCredential;
import com.google.jenkins.plugins.computeengine.ssh.GoogleKeyPair;
import com.google.jenkins.plugins.computeengine.ssh.GooglePrivateKey;
import com.google.jenkins.plugins.computeengine.util.DiskMappingParser;
import edu.umd.cs.findbugs.annotations.Nullable;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.RelativePath;
import hudson.Util;
import hudson.XmlFile;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.Saveable;
import hudson.model.labels.LabelAtom;
import hudson.model.listeners.SaveableListener;
import hudson.util.ComboBoxModel;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import jenkins.model.Jenkins;
import jenkins.util.SystemProperties;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.java.Log;
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
    public static final Integer DEFAULT_SSH_PORT = 22;
    public static final Integer DEFAULT_RETENTION_TIME_MINUTES = (DEFAULT_LAUNCH_TIMEOUT_SECONDS / 60) + 1;
    public static final String DEFAULT_RUN_AS_USER = "jenkins";
    public static final String METADATA_LINUX_STARTUP_SCRIPT_KEY = "startup-script";
    public static final String METADATA_WINDOWS_STARTUP_SCRIPT_KEY = "windows-startup-script-ps1";
    public static final String GUEST_ATTRIBUTE_STARTUP_SCRIPT_NAMESPACE = "startup-script";
    public static final String GUEST_ATTRIBUTE_STARTUP_SCRIPT_STATUS_KEY = "status";
    public static final String DEFAULT_LINUX_EXIT_REPORTER = """
            curl -s -X PUT -H "Metadata-Flavor: Google" \\
              "http://metadata.google.internal/computeMetadata/v1/instance/guest-attributes/startup-script/status" \\
              -d "$1\"""";
    public static final String DEFAULT_WINDOWS_EXIT_REPORTER = """
            Invoke-RestMethod -Method PUT -Body "$($args[0])" `
              -Headers @{'Metadata-Flavor'='Google'} `
              -Uri "http://metadata.google.internal/computeMetadata/v1/instance/guest-attributes/startup-script/status\"""";
    /**
     * Upper bound on images returned from {@code images.list} when populating the
     * boot-disk image dropdown. Avoids GCE's default server-side cap of 500 while
     * staying well under the kind of payload size that would overwhelm Jetty or
     * the browser for pathological projects.
     * <p>
     * Configurable via system property
     * {@code com.google.jenkins.plugins.computeengine.InstanceConfiguration.listImagesMaxResults}.
     * Default: {@code 10000}.
     */
    public static final int LIST_IMAGES_MAX_RESULTS =
            SystemProperties.getInteger(InstanceConfiguration.class.getName() + ".listImagesMaxResults", 10_000);

    public static final List<String> KNOWN_IMAGE_PROJECTS = List.of(
            "centos-cloud",
            "coreos-cloud",
            "cos-cloud",
            "debian-cloud",
            "rhel-cloud",
            "suse-cloud",
            "suse-sap-cloud",
            "ubuntu-os-cloud",
            "windows-cloud",
            "windows-sql-cloud");

    private String description;
    private String namePrefix;
    private String region;
    private String zone;
    private String machineType;
    private String numExecutorsStr;
    private String startupScript;
    private String startupScriptExitReporterLinux;
    private String startupScriptExitReporterWindows;
    private ProvisioningType provisioningType;
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
    private int minimumNumberOfInstances;
    private int minimumNumberOfSpareInstances;

    @Nullable
    private MinimumNumberOfInstancesTimeRangeConfig minimumNumberOfInstancesTimeRangeConfig;

    private String template;

    @Nullable
    private String fallbackZones;

    /**
     * How long to skip a zone after capacity-exhaustion error before it is retried. GCP gives no
     * recommended interval for {@code ZONE_RESOURCE_POOL_EXHAUSTED}, a conservative default.
     */
    static final Duration ZONE_EXHAUSTION_COOLDOWN_DURATION = SystemProperties.getDuration(
            InstanceConfiguration.class.getName() + ".zoneExhaustionCooldownDuration", Duration.ofMinutes(2));

    /**
     * Records the instant each zone was last exhausted. Transient — lost on restart, which is acceptable.
     * Reinitialized in {@link #readResolve()} since field initializers don't run on the deserialization path.
     */
    @Getter(AccessLevel.NONE)
    private transient ConcurrentHashMap<String, Instant> exhaustedAt = new ConcurrentHashMap<>();

    @Getter(AccessLevel.NONE)
    private transient AtomicReference<Instant> allZonesExhaustedLoggedAt = new AtomicReference<>();

    // Optional not possible due to serialization requirement
    @Nullable
    private WindowsConfiguration windowsConfiguration;

    @Nullable
    private SshConfiguration sshConfiguration;

    @Nullable
    private ShieldedVmConfiguration shieldedVmConfiguration;

    @Nullable
    private List<CustomMetadataItem> customMetadata;

    @Nullable
    private List<CustomLabelItem> customLabels;

    private boolean createSnapshot;
    private String diskMapping;
    private String remoteFs;
    private String javaExecPath;
    private Integer sshPort;
    private GoogleKeyCredential sshKeyCredential;
    private Map<String, String> googleLabels;
    private Integer numExecutors;
    private boolean terminateIdleDuringShutdown;
    private Integer retentionTimeMinutes;
    private Integer launchTimeoutSeconds;
    private Long bootDiskSizeGb;
    private transient Set<LabelAtom> labelSet;

    @Getter(AccessLevel.PROTECTED)
    @Setter(AccessLevel.PROTECTED)
    protected transient ComputeEngineCloud cloud;

    /** @deprecated Use {@link #provisioningType} instead. */
    @SuppressWarnings("DeprecatedIsStillUsed")
    @Deprecated
    private transient boolean preemptible;

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
    public void setSshPort(Integer sshPort) {
        this.sshPort = (sshPort != null && sshPort >= 1 && sshPort <= 65535) ? sshPort : DEFAULT_SSH_PORT;
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

    /**
     * This setter is kept only to provide JCasC compatibility, don't use for any other.
     * Although JCasC is not "required" to keep compatibility, but in this case,
     * as it is very low effort to keep the compatibility, we have decided to keep it.
     * <p>
     * Previously, JCasC syntax would be {@code preemptible: true}, going forward instead should be done as,
     * {@code provisioningType: preemptibleVm}
     * <p>
     * Currently only caller is, JCasC configurators if the bundle is having `preemptible` field defined in it.
     * Consider deleting it in future (perhaps after a year or so)
     */
    @DataBoundSetter
    public void setPreemptible(boolean preemptible) {
        if (preemptible) {
            this.provisioningType = new PreemptibleVm();
        }
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
            var zones = candidateZones();
            var primaryZone = zones.get(0);
            var selectedZone =
                    zones.stream().filter(z -> !isExhausted(z)).findFirst().orElse(null);
            if (selectedZone == null) {
                if (shouldLogAllZonesExhausted()) {
                    log.info("All zones " + zones + " are exhausted for [" + description
                            + "]; skipping provisioning until a zone cooldown (" + ZONE_EXHAUSTION_COOLDOWN_DURATION
                            + ") lapses. Ideas: add more fallback zones, or define another GCP cloud (e.g. a different "
                            + "project or region) sharing the same label so Jenkins can provision there instead");
                }
                return null;
            }
            if (!selectedZone.equals(primaryZone)) {
                log.info("Primary zone [" + primaryZone + "] is exhausted for [" + description
                        + "]; provisioning in fallback zone [" + selectedZone + "]");
                rezoneInstance(instance, selectedZone);
            }
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
                    .terminateIdleDuringShutdown(terminateIdleDuringShutdown)
                    .numExecutors(numExecutors)
                    .mode(mode)
                    .labelString(labels)
                    .launcher(launcher)
                    .retentionStrategy(new ComputeEngineRetentionStrategy(retentionTimeMinutes, oneShot))
                    .launchTimeout(getLaunchTimeoutMillis())
                    .sshPort(sshPort)
                    .javaExecPath(javaExecPath)
                    .sshKeyCredential(sshKeyCredential)
                    .waitForStartupScript(notNullOrEmpty(startupScript) && notNullOrEmpty(resolveExitReporter()))
                    .build();
        } catch (Descriptor.FormException fe) {
            log.log(Level.WARNING, "Error provisioning instance: " + fe.getMessage(), fe);
            return null;
        }
    }

    /**
     * Returns the ordered zone list to attempt: the primary zone first, then each configured fallback zone.
     * With no fallback zones configured this is just the primary zone.
     */
    List<String> candidateZones() {
        var zones = new ArrayList<String>();
        zones.add(nameFromSelfLink(zone));
        if (fallbackZones != null) {
            for (var z : fallbackZones.split("[,\\s]+")) {
                // normalize so tokens match the short-name keys used by markExhausted() and rezoneInstance()
                if (!z.isBlank()) zones.add(nameFromSelfLink(z.trim()));
            }
        }
        return zones;
    }

    void markExhausted(String zone) {
        exhaustedAt.put(zone, Instant.now());
        log.info(String.format(
                "Marking zone [%s] exhausted for machine type [%s] in config [%s] until %s; "
                        + "the next provisioning cycle will skip this zone and try the next candidate zone",
                zone, machineTypeDisplayName(), description, Instant.now().plus(ZONE_EXHAUSTION_COOLDOWN_DURATION)));
    }

    /**
     * Human-readable machine type for log messages. Template-based configurations leave {@code machineType}
     * unset (it is only used when no template is configured), so fall back to the template name there;
     * {@code ClientUtil.nameFromSelfLink} rejects empty input.
     */
    private String machineTypeDisplayName() {
        if (!Strings.isNullOrEmpty(machineType)) {
            return nameFromSelfLink(machineType);
        }
        if (!Strings.isNullOrEmpty(template)) {
            return "template " + nameFromSelfLink(template);
        }
        return "unknown";
    }

    boolean isExhausted(String zone) {
        var at = exhaustedAt.get(zone);
        if (at == null) {
            return false;
        }
        if (Instant.now().isBefore(at.plus(ZONE_EXHAUSTION_COOLDOWN_DURATION))) {
            return true;
        }
        exhaustedAt.remove(zone, at);
        return false;
    }

    private boolean shouldLogAllZonesExhausted() {
        var now = Instant.now();
        var last = allZonesExhaustedLoggedAt.get();
        if (last != null && now.isBefore(last.plus(ZONE_EXHAUSTION_COOLDOWN_DURATION))) {
            return false;
        }
        return allZonesExhaustedLoggedAt.compareAndSet(last, now);
    }

    /** Initializes transient properties */
    protected Object readResolve() {
        labelSet = Label.parse(labels);
        if (externalAddress != null) {
            this.networkInterfaceIpStackMode = new NetworkInterfaceSingleStack(externalAddress);
            this.externalAddress = null;
        }
        /* deprecating `preemptible` in favor of extensible `provisioningType` */
        if (preemptible && provisioningType == null) {
            provisioningType = new PreemptibleVm();
        }
        if (sshPort == null) {
            sshPort = DEFAULT_SSH_PORT;
        }
        if (exhaustedAt == null) {
            exhaustedAt = new ConcurrentHashMap<>();
        }
        if (allZonesExhaustedLoggedAt == null) {
            allZonesExhaustedLoggedAt = new AtomicReference<>();
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

        Map<String, String> effectiveGoogleLabels = new HashMap<>();
        if (googleLabels != null) { // some tests don't set the labels, but comes as null
            effectiveGoogleLabels.putAll(googleLabels);
        }
        if (customLabels != null) {
            for (CustomLabelItem item : customLabels) {
                if (item.getKey() != null && !item.getKey().isEmpty()) {
                    effectiveGoogleLabels.put(item.getKey(), item.getValue());
                }
            }
        }
        effectiveGoogleLabels.put(
                CleanLostNodesWork.NODE_IN_USE_LABEL_KEY, CleanLostNodesWork.getLastRefreshLabelVal());

        if (template != null && !template.isBlank()) {
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

            if (instanceTemplate.getProperties().getLabels() != null) {
                Map<String, String> templateLabels =
                        instanceTemplate.getProperties().getLabels();
                effectiveGoogleLabels.putAll(templateLabels);
            }
            instance.setLabels(effectiveGoogleLabels);
        } else {
            configureStartupScript(instance);
            instance.setLabels(effectiveGoogleLabels);
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

            if (shieldedVmConfiguration != null) {
                instance.setShieldedInstanceConfig(new ShieldedInstanceConfig()
                        .setEnableSecureBoot(shieldedVmConfiguration.isEnableSecureBoot())
                        .setEnableVtpm(shieldedVmConfiguration.isEnableVtpm())
                        .setEnableIntegrityMonitoring(shieldedVmConfiguration.isEnableIntegrityMonitoring()));
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
        if (customMetadata != null) {
            for (CustomMetadataItem item : customMetadata) {
                if (item.getKey() != null && !item.getKey().isEmpty()) {
                    metadata.getItems()
                            .add(new Metadata.Items().setKey(item.getKey()).setValue(item.getValue()));
                }
            }
        }
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

    /**
     * Returns the exit reporter script as-is. A null or empty value means no exit reporting:
     * the startup script will not be wrapped and the launcher will not wait for completion.
     * <p>
     * New configurations get the platform default populated by the form field's default attribute.
     * Existing configurations upgraded from older plugin versions have null here, so they
     * keep their previous behaviour (no wrapping, no waiting) until the user saves the config
     * with the default-filled value, a custom reporter, or blank (which disables waiting).
     */
    private String resolveExitReporter() {
        if (windowsConfiguration != null) {
            return startupScriptExitReporterWindows;
        }
        return startupScriptExitReporterLinux;
    }

    private void configureStartupScript(Instance instance) {
        if (notNullOrEmpty(startupScript)) {
            var items = instance.getMetadata().getItems();
            var reporter = resolveExitReporter();
            if (windowsConfiguration != null) {
                var effectiveScript = wrapWindowsStartupScript(startupScript, reporter);
                log.info("Startup script configured"
                        + (notNullOrEmpty(reporter) ? " with completion reporting" : " without completion reporting"));
                log.finer("Effective Windows startup script:\n" + effectiveScript);
                items.add(new Metadata.Items()
                        .setKey(METADATA_WINDOWS_STARTUP_SCRIPT_KEY)
                        .setValue(effectiveScript));
            } else {
                var effectiveScript = wrapLinuxStartupScript(startupScript, reporter);
                log.info("Startup script configured"
                        + (notNullOrEmpty(reporter) ? " with completion reporting" : " without completion reporting"));
                log.finer("Effective Linux startup script:\n" + effectiveScript);
                items.add(new Metadata.Items()
                        .setKey(METADATA_LINUX_STARTUP_SCRIPT_KEY)
                        .setValue(effectiveScript));
            }
        }
    }

    @VisibleForTesting
    static String wrapLinuxStartupScript(String script, String completionScript) {
        if (!notNullOrEmpty(completionScript)) {
            return script;
        }
        var sb = new StringBuilder();
        var scriptBody = script;
        if (script.startsWith("#!")) {
            var newlineIdx = script.indexOf('\n');
            if (newlineIdx >= 0) {
                sb.append(script, 0, newlineIdx + 1);
                scriptBody = script.substring(newlineIdx + 1);
            } else {
                sb.append(script).append('\n');
                scriptBody = "";
            }
        }
        sb.append("# --- GCE plugin: exit reporter begin ---\n");
        sb.append("__gce_plugin_report_status() {\n");
        sb.append(completionScript).append('\n');
        sb.append("}\n");
        sb.append(
                "trap '__gce_plugin_ec=$?; __gce_plugin_report_status \"$__gce_plugin_ec\" || true; exit $__gce_plugin_ec' EXIT\n");
        sb.append("# --- GCE plugin: exit reporter end ---\n");
        sb.append("# --- GCE plugin: user startup script begin ---\n");
        sb.append(scriptBody);
        if (!scriptBody.endsWith("\n")) {
            sb.append('\n');
        }
        sb.append("# --- GCE plugin: user startup script end ---\n");
        return sb.toString();
    }

    @VisibleForTesting
    static String wrapWindowsStartupScript(String script, String completionScript) {
        if (!notNullOrEmpty(completionScript)) {
            return script;
        }
        var sb = new StringBuilder();
        sb.append("$__gce_plugin_ec = 0\n");
        sb.append("try {\n");
        sb.append("# --- GCE plugin: user startup script begin ---\n");
        sb.append(script);
        if (!script.endsWith("\n")) {
            sb.append('\n');
        }
        sb.append("# --- GCE plugin: user startup script end ---\n");
        sb.append("    $__gce_plugin_ec = $LASTEXITCODE\n");
        sb.append("    if ($null -eq $__gce_plugin_ec) { $__gce_plugin_ec = 0 }\n");
        sb.append("} catch {\n");
        sb.append("    $__gce_plugin_ec = 1\n");
        sb.append("} finally {\n");
        sb.append("    # --- GCE plugin: exit reporter begin ---\n");
        sb.append("    $__gce_plugin_exit_args = @($__gce_plugin_ec)\n");
        sb.append("    try {\n");
        sb.append("        & {\n");
        sb.append("            ").append(completionScript).append('\n');
        sb.append("        } $__gce_plugin_exit_args\n");
        sb.append("    } catch {}\n");
        sb.append("    # --- GCE plugin: exit reporter end ---\n");
        sb.append("    exit $__gce_plugin_ec\n");
        sb.append("}\n");
        return sb.toString();
    }

    private Tags tags() {
        if (notNullOrEmpty(networkTags)) {
            Tags tags = new Tags();
            tags.setItems(Arrays.asList(networkTags.split(" ")));
            return tags;
        }
        return null;
    }

    @VisibleForTesting
    Scheduling scheduling() {
        Scheduling scheduling = new Scheduling();
        if (provisioningType == null) {
            return scheduling;
        }
        provisioningType.configure(scheduling);
        return scheduling;
    }

    /** Builds the list of disks for the instance: the boot disk followed by any additional
     *  disks parsed from the {@link #diskMapping} field. */
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

        var zoneName = nameFromSelfLink(zone);
        var projectId = cloud != null ? nameFromSelfLink(cloud.getProjectId()) : null;
        for (var mapped : DiskMappingParser.parse(diskMapping)) {
            if (mapped.getInitializeParams() != null) {
                var params = mapped.getInitializeParams();
                params.setDiskType(DiskMappingParser.normalizeDiskType(params.getDiskType(), zoneName));
                if (projectId != null) {
                    params.setSourceSnapshot(
                            DiskMappingParser.normalizeSnapshotSource(params.getSourceSnapshot(), projectId));
                }
            } else if (projectId != null) {
                mapped.setSource(DiskMappingParser.normalizeDiskSource(mapped.getSource(), projectId, zoneName));
            }
            disks.add(mapped);
        }

        return disks;
    }

    @VisibleForTesting
    static String rezoneSelfLink(String selfLink, String targetZone) {
        if (selfLink == null) return null;
        return selfLink.replaceFirst("(?<=/|^)zones/[^/]+/", "zones/" + targetZone + "/");
    }

    void rezoneInstance(Instance instance, String targetZone) {
        var projectId = cloud != null ? nameFromSelfLink(cloud.getProjectId()) : null;
        instance.setZone(targetZone);
        // machineType is a stripped self-link (projects/p/zones/z/machineTypes/…);
        // replace the zone segment so it matches the target zone
        if (instance.getMachineType() != null) {
            instance.setMachineType(rezoneSelfLink(instance.getMachineType(), targetZone));
        }
        if (instance.getDisks() != null) {
            for (var disk : instance.getDisks()) {
                var params = disk.getInitializeParams();
                if (params != null) {
                    // new disk: rezone the diskType self-link, then normalize bare short names
                    params.setDiskType(DiskMappingParser.normalizeDiskType(
                            rezoneSelfLink(params.getDiskType(), targetZone), targetZone));
                    // sourceSnapshot is global (not zone-scoped); project-qualify bare names only
                    if (projectId != null) {
                        params.setSourceSnapshot(
                                DiskMappingParser.normalizeSnapshotSource(params.getSourceSnapshot(), projectId));
                    }
                } else if (disk.getSource() != null) {
                    disk.setSource(rezoneSelfLink(disk.getSource(), targetZone));
                }
            }
        }
        if (instance.getGuestAccelerators() != null) {
            for (var acc : instance.getGuestAccelerators()) {
                if (acc.getAcceleratorType() != null) {
                    acc.setAcceleratorType(rezoneSelfLink(acc.getAcceleratorType(), targetZone));
                }
            }
        }
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

        public static Integer defaultSshPort() {
            return DEFAULT_SSH_PORT;
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

        @SuppressWarnings("unused") // jelly
        public ProvisioningType defaultProvisioningType() {
            return new Standard(0);
        }

        public static NetworkConfiguration defaultNetworkConfiguration() {
            return new AutofilledNetworkConfiguration();
        }

        @SuppressWarnings("unused") // jelly
        public static String defaultStartupScriptExitReporterLinux() {
            return DEFAULT_LINUX_EXIT_REPORTER;
        }

        @SuppressWarnings("unused") // jelly
        public static String defaultStartupScriptExitReporterWindows() {
            return DEFAULT_WINDOWS_EXIT_REPORTER;
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

        public FormValidation doCheckSshPort(@QueryParameter String value) {
            if (value == null || value.isEmpty()) {
                return FormValidation.ok();
            }
            return FormValidation.validateIntegerInRange(value, 1, 65535);
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
            if (value == null || value.isBlank()) {
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
            if (value == null || value.isBlank()) {
                return FormValidation.error("Please select a zone...");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckFallbackZones(@QueryParameter String value, @QueryParameter String region) {
            if (value == null || value.isBlank()) {
                return FormValidation.ok();
            }
            var regionName = nameFromSelfLink(region);
            if (regionName == null || regionName.isBlank()) {
                return FormValidation.ok();
            }
            for (var z : value.split("[,\\s]+")) {
                if (z.isBlank()) continue;
                var zoneName = nameFromSelfLink(z.trim());
                if (!zoneName.startsWith(regionName + "-")) {
                    return FormValidation.warning("Zone [" + zoneName + "] is not in region [" + regionName
                            + "]. Fallback zones are recommended to be in the same region; it may or may not provision"
                            + " depending on the subnetwork configuration. To reliably provision in a different"
                            + " region, create a separate instance configuration instead.");
                }
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
            if (value == null || value.isBlank()) {
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

        public ComboBoxModel doFillBootDiskSourceImageProjectItems(
                @QueryParameter("projectId") @RelativePath("..") final String projectId) {
            checkPermissions(Jenkins.get(), Jenkins.ADMINISTER);
            ComboBoxModel items = new ComboBoxModel();
            items.add(projectId);
            items.addAll(KNOWN_IMAGE_PROJECTS);
            return items;
        }

        public FormValidation doCheckBootDiskSourceImageProject(@QueryParameter String value) {
            if (value == null || value.isBlank()) {
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
                var clientV2 = ClientUtil.createComputeClientV2(projectId, credentialsId);
                List<Image> images = clientV2.listImages(projectId, LIST_IMAGES_MAX_RESULTS);
                log.fine(() -> "listImages(maxResults=" + LIST_IMAGES_MAX_RESULTS + ") returned " + images.size()
                        + " images for project " + projectId);
                for (Image i : images) {
                    items.add(i.getName(), i.getSelfLink());
                }
            } catch (IOException | GeneralSecurityException e) {
                items.clear();
                items.add("Error retrieving images for project");
            } catch (IllegalArgumentException iae) {
                // TODO: log
                return null;
            }
            return items;
        }

        public FormValidation doCheckBootDiskSourceImageName(@QueryParameter String value) {
            if (value == null || value.isBlank()) {
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

        private FormValidation validateMinimumInstances(String fieldName, String value, String instanceCapStr) {
            if (value == null || value.isBlank()) {
                return FormValidation.ok();
            }
            int minimumInstances;
            try {
                minimumInstances = Integer.parseInt(value);
            } catch (NumberFormatException e) {
                return FormValidation.error("%s must be a non-negative integer", fieldName);
            }
            if (minimumInstances < 0) {
                return FormValidation.error("%s must be a non-negative integer", fieldName);
            }
            if (instanceCapStr == null || instanceCapStr.isBlank()) {
                return FormValidation.ok();
            }
            int instanceCap;
            try {
                instanceCap = Integer.parseInt(instanceCapStr);
            } catch (NumberFormatException e) {
                return FormValidation.error("Instance Cap must be a valid integer");
            }
            if (minimumInstances > instanceCap) {
                return FormValidation.error("%s must not be larger than Instance Cap %d", fieldName, instanceCap);
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckMinimumNumberOfInstances(
                @QueryParameter String value,
                @QueryParameter("instanceCapStr") @RelativePath("..") String instanceCapStr) {
            return validateMinimumInstances("Minimum number of instances", value, instanceCapStr);
        }

        public FormValidation doCheckMinimumNumberOfSpareInstances(
                @QueryParameter String value,
                @QueryParameter("instanceCapStr") @RelativePath("..") String instanceCapStr) {
            return validateMinimumInstances("Minimum number of spare instances", value, instanceCapStr);
        }

        private FormValidation validateTimeRange(String value) {
            try {
                MinimumNumberOfInstancesTimeRangeConfig.validateLocalTimeString(value);
                return FormValidation.ok();
            } catch (IllegalArgumentException e) {
                return FormValidation.error("Please enter value in format 'h:mm a' or 'HH:mm'");
            }
        }

        public FormValidation doCheckActiveFrom(@QueryParameter String value) {
            return validateTimeRange(value);
        }

        public FormValidation doCheckActiveTo(@QueryParameter String value) {
            return validateTimeRange(value);
        }

        public FormValidation doCheckMonday(
                @QueryParameter boolean monday,
                @QueryParameter boolean tuesday,
                @QueryParameter boolean wednesday,
                @QueryParameter boolean thursday,
                @QueryParameter boolean friday,
                @QueryParameter boolean saturday,
                @QueryParameter boolean sunday) {
            if (!(monday || tuesday || wednesday || thursday || friday || saturday || sunday)) {
                return FormValidation.warning(
                        "At least one day should be checked or minimum number of instances won't be active");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckStartupScriptExitReporterLinux(@QueryParameter String value) {
            if (value == null || value.isEmpty()) {
                return FormValidation.ok();
            }
            if (!value.contains("$1")) {
                return FormValidation.warning("Linux exit reporter should include $1 as the exit code placeholder");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckStartupScriptExitReporterWindows(@QueryParameter String value) {
            if (value == null || value.isEmpty()) {
                return FormValidation.ok();
            }
            if (!value.contains("$args[0]")) {
                return FormValidation.warning(
                        "Windows exit reporter should include $args[0] as the exit code placeholder");
            }
            return FormValidation.ok();
        }

        @SuppressWarnings("unused") // jelly
        public List<ProvisioningType.ProvisioningTypeDescriptor> getProvisioningTypes() {
            return ExtensionList.lookup(ProvisioningType.ProvisioningTypeDescriptor.class);
        }

        public List<NetworkInterfaceIpStackMode.Descriptor> getNetworkInterfaceIpStackModeDescriptors() {
            return ExtensionList.lookup(NetworkInterfaceIpStackMode.Descriptor.class);
        }
    }

    /** Triggers minimum instance check when Jenkins configuration is saved. This ensures any updates to the
     * values of minimum instances are immediately taken into effect.
     */
    @Extension
    public static final class OnSaveListener extends SaveableListener {
        @Override
        public void onChange(Saveable o, XmlFile file) {
            if (o instanceof Jenkins) {
                MinimumInstanceChecker.checkForMinimumInstances();
            }
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
            instanceConfiguration.setStartupScriptExitReporterLinux(this.startupScriptExitReporterLinux);
            instanceConfiguration.setStartupScriptExitReporterWindows(this.startupScriptExitReporterWindows);
            instanceConfiguration.setProvisioningType(this.provisioningType);
            instanceConfiguration.setMinCpuPlatform(this.minCpuPlatform);
            instanceConfiguration.setLabelString(this.labels);
            instanceConfiguration.setRunAsUser(this.runAsUser);
            instanceConfiguration.setWindowsConfiguration(this.windowsConfiguration);
            instanceConfiguration.setSshConfiguration(this.sshConfiguration);
            instanceConfiguration.setShieldedVmConfiguration(this.shieldedVmConfiguration);
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
            instanceConfiguration.setMinimumNumberOfInstances(this.minimumNumberOfInstances);
            instanceConfiguration.setMinimumNumberOfSpareInstances(this.minimumNumberOfSpareInstances);
            instanceConfiguration.setMinimumNumberOfInstancesTimeRangeConfig(
                    this.minimumNumberOfInstancesTimeRangeConfig);
            instanceConfiguration.setTemplate(this.template);
            instanceConfiguration.setFallbackZones(this.fallbackZones);
            instanceConfiguration.setCreateSnapshot(this.createSnapshot);
            instanceConfiguration.setDiskMapping(this.diskMapping);
            instanceConfiguration.setTerminateIdleDuringShutdown(this.terminateIdleDuringShutdown);
            instanceConfiguration.setCustomMetadata(this.customMetadata);
            instanceConfiguration.setCustomLabels(this.customLabels);
            instanceConfiguration.setRemoteFs(this.remoteFs);
            instanceConfiguration.setJavaExecPath(this.javaExecPath);
            instanceConfiguration.setSshPort(this.sshPort);
            instanceConfiguration.setCloud(this.cloud);
            if (googleLabels != null) {
                instanceConfiguration.appendLabels(this.googleLabels);
            }
            return instanceConfiguration;
        }

        // Private methods defined to exclude these from the builder and skip Lombok generating them.
        @SuppressFBWarnings(value = "UPM_UNCALLED_PRIVATE_METHOD", justification = "for Lombok")
        private Builder numExecutors(Integer numExecutors) {
            throw new UnsupportedOperationException();
        }

        private Builder retentionTimeMinutes(Integer retentionTimeMinutes) {
            throw new UnsupportedOperationException();
        }

        private Builder launchTimeoutSeconds(Integer launchTimeoutSeconds) {
            throw new UnsupportedOperationException();
        }

        private Builder bootDiskSizeGb(Long bootDiskSizeGb) {
            throw new UnsupportedOperationException();
        }

        private Builder labelSet(Set<LabelAtom> labelSet) {
            throw new UnsupportedOperationException();
        }
    }
}
