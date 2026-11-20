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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.google.api.services.compute.model.AcceleratorType;
import com.google.api.services.compute.model.DiskType;
import com.google.api.services.compute.model.Image;
import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.InstanceProperties;
import com.google.api.services.compute.model.InstanceTemplate;
import com.google.api.services.compute.model.MachineType;
import com.google.api.services.compute.model.Metadata;
import com.google.api.services.compute.model.Network;
import com.google.api.services.compute.model.Region;
import com.google.api.services.compute.model.Subnetwork;
import com.google.api.services.compute.model.Zone;
import com.google.cloud.graphite.platforms.plugin.client.ComputeClient;
import com.google.common.collect.ImmutableList;
import com.google.jenkins.plugins.computeengine.config.PreemptibleVm;
import com.google.jenkins.plugins.computeengine.config.Standard;
import hudson.model.Node;
import hudson.util.FormValidation;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LogRecorder;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.LocalData;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@WithJenkins
class InstanceConfigurationTest {
    public static final String NAME_PREFIX = "test";
    public static final String PROJECT_ID = "test-project";
    public static final String REGION = "us-west1";
    public static final String ZONE = "us-west1-a";
    public static final String LABEL = "LABEL1 LABEL2";
    public static final String A_LABEL = "LABEL1";
    public static final String MACHINE_TYPE = "n1-standard-1";
    public static final String STARTUP_SCRIPT = "#!/bin/bash";
    public static final String NUM_EXECUTORS = "1";
    public static final boolean PREEMPTIBLE = true;
    public static final String MIN_CPU_PLATFORM = "Intel Haswell";
    public static final String CONFIG_DESC = "test-config";
    public static final String BOOT_DISK_TYPE = "pd-standard";
    public static final boolean BOOT_DISK_AUTODELETE = true;
    public static final String BOOT_DISK_IMAGE_NAME = "test-image";
    public static final String BOOT_DISK_PROJECT_ID = PROJECT_ID;
    public static final Long BOOT_DISK_SIZE_GB = 10L;
    public static final String TEMPLATE_NAME = "test-template";
    public static final Node.Mode NODE_MODE = Node.Mode.EXCLUSIVE;
    public static final String ACCELERATOR_NAME = "test-gpu";
    public static final String ACCELERATOR_COUNT = "1";
    public static final String RUN_AS_USER = "jenkins";
    public static final String NETWORK_NAME = "test-network";
    public static final String SUBNETWORK_NAME = "test-subnetwork";
    public static final boolean EXTERNAL_ADDR = true;
    public static final String NETWORK_TAGS = "tag1 tag2";
    public static final String SERVICE_ACCOUNT_EMAIL = "test-service-account";
    public static final String RETENTION_TIME_MINUTES_STR = "1";
    public static final String LAUNCH_TIMEOUT_SECONDS_STR = "100";

    @Mock
    private ComputeEngineCloud cloud;

    @Mock
    private ComputeClient computeClient;

    private JenkinsRule r;

    @AutoClose
    private final LogRecorder logs =
            new LogRecorder().record(InstanceConfiguration.class, Level.INFO).capture(20);

    @BeforeEach
    void init(JenkinsRule rule) throws Exception {
        r = rule;
        List<Region> regions = new ArrayList<>();
        regions.add(new Region().setName("").setSelfLink(""));
        regions.add(new Region().setName(REGION).setSelfLink(REGION));

        List<Zone> zones = new ArrayList<>();
        zones.add(new Zone().setName("").setSelfLink(""));
        zones.add(new Zone().setName(ZONE).setSelfLink(ZONE));

        List<MachineType> machineTypes = new ArrayList<>();
        machineTypes.add(new MachineType().setName("").setSelfLink(""));
        machineTypes.add(new MachineType().setName(MACHINE_TYPE).setSelfLink(MACHINE_TYPE));

        List<String> cpuPlatforms = new ArrayList<>();
        cpuPlatforms.add("");
        cpuPlatforms.add("Intel Skylake");
        cpuPlatforms.add("Intel Haswell");

        List<DiskType> diskTypes = new ArrayList<>();
        diskTypes.add(new DiskType().setName("").setSelfLink(""));
        diskTypes.add(new DiskType().setName(BOOT_DISK_TYPE).setSelfLink(BOOT_DISK_TYPE));

        List<Image> imageTypes = new ArrayList<>();
        imageTypes.add(new Image().setName("").setSelfLink(""));
        imageTypes.add(new Image().setName(BOOT_DISK_IMAGE_NAME).setSelfLink(BOOT_DISK_IMAGE_NAME));

        Image image = new Image();
        image.setName(BOOT_DISK_IMAGE_NAME).setSelfLink(BOOT_DISK_IMAGE_NAME).setDiskSizeGb(BOOT_DISK_SIZE_GB);

        List<Network> networks = new ArrayList<>();
        networks.add(new Network().setName("").setSelfLink(""));
        networks.add(new Network().setName(NETWORK_NAME).setSelfLink(NETWORK_NAME));

        List<Subnetwork> subnetworks = new ArrayList<>();
        subnetworks.add(new Subnetwork().setName("").setSelfLink(""));
        subnetworks.add(new Subnetwork().setName(SUBNETWORK_NAME).setSelfLink(SUBNETWORK_NAME));

        List<AcceleratorType> acceleratorTypes = new ArrayList<>();
        acceleratorTypes.add(new AcceleratorType().setName("").setSelfLink("").setMaximumCardsPerInstance(0));
        acceleratorTypes.add(new AcceleratorType()
                .setName(ACCELERATOR_NAME)
                .setSelfLink(ACCELERATOR_NAME)
                .setMaximumCardsPerInstance(Integer.parseInt(ACCELERATOR_COUNT)));

        InstanceTemplate instanceTemplate = new InstanceTemplate()
                .setName(TEMPLATE_NAME)
                .setProperties(new InstanceProperties()
                        .setMetadata(new Metadata()
                                .setItems(Stream.of(new Metadata.Items()
                                                .set("key", "ssh-keys")
                                                .set("value", "TEST"))
                                        .collect(Collectors.toList()))));

        when(computeClient.listRegions(anyString())).thenReturn(ImmutableList.copyOf(regions));
        when(computeClient.listZones(anyString(), anyString())).thenReturn(ImmutableList.copyOf(zones));
        when(computeClient.listMachineTypes(anyString(), anyString())).thenReturn(ImmutableList.copyOf(machineTypes));
        when(computeClient.listCpuPlatforms(anyString(), anyString())).thenReturn(ImmutableList.copyOf(cpuPlatforms));
        when(computeClient.listBootDiskTypes(anyString(), anyString())).thenReturn(ImmutableList.copyOf(diskTypes));
        when(computeClient.getImage(anyString(), anyString())).thenReturn(image);
        when(computeClient.listImages(anyString())).thenReturn(ImmutableList.copyOf(imageTypes));
        when(computeClient.listAcceleratorTypes(anyString(), anyString()))
                .thenReturn(ImmutableList.copyOf(acceleratorTypes));
        when(computeClient.listNetworks(anyString())).thenReturn(ImmutableList.copyOf(networks));
        when(computeClient.listSubnetworks(anyString(), anyString(), anyString()))
                .thenReturn(ImmutableList.copyOf(subnetworks));
        when(cloud.getProjectId()).thenReturn(PROJECT_ID);
        when(cloud.getClient()).thenReturn(computeClient);
        when(computeClient.getTemplate(anyString(), anyString())).thenReturn(instanceTemplate);
    }

    @Test
    void testClient() throws Exception {
        List<Region> regions = computeClient.listRegions(anyString());
        assert (regions.size() == 2);
        assert (regions.get(1).getName().equals(REGION));

        List<Zone> zones = computeClient.listZones(PROJECT_ID, REGION);
        assert (zones.size() == 2);
        assert (zones.get(1).getName().equals(ZONE));
        assert (zones.get(1).getSelfLink().equals(ZONE));
    }

    @Test
    void testConfigRoundtrip() throws Exception {
        InstanceConfiguration want = instanceConfigurationBuilder()
                .minCpuPlatform("")
                .customLabels(List.of(new CustomLabelItem("team", "jenkins"), new CustomLabelItem("cost-center", "ci")))
                .build();

        InstanceConfiguration.DescriptorImpl.setComputeClient(computeClient);
        AcceleratorConfiguration.DescriptorImpl.setComputeClient(computeClient);
        NetworkConfiguration.NetworkConfigurationDescriptor.setComputeClient(computeClient);

        List<InstanceConfiguration> configs = new ArrayList<>();
        configs.add(want);

        ComputeEngineCloud gcp = new ComputeEngineCloud("test", PROJECT_ID, "testCredentialsId", "1");
        gcp.setConfigurations(configs);
        r.jenkins.clouds.add(gcp);

        final HtmlPage configure = r.createWebClient().goTo("configure");
        r.submit(configure.getFormByName("config"));
        InstanceConfiguration got = ((ComputeEngineCloud)
                        r.jenkins.clouds.iterator().next())
                .getInstanceConfigurationByDescription(CONFIG_DESC);
        r.assertEqualBeans(
                want,
                got,
                "namePrefix,region,zone,machineType,preemptible,windowsConfiguration,minCpuPlatform,startupScript,bootDiskType,bootDiskSourceImageName,bootDiskSourceImageProject,bootDiskSizeGb,diskMapping,acceleratorConfiguration,networkConfiguration,networkInterfaceIpStackMode,networkTags,serviceAccountEmail,minimumNumberOfInstances,minimumNumberOfSpareInstances,shieldedVmConfiguration,sshPort,customLabels");
    }

    @Test
    void testInstanceModel() throws Exception {
        Instance instance = instanceConfigurationBuilder()
                .minCpuPlatform(MIN_CPU_PLATFORM)
                .build()
                .instance();
        // General
        assertTrue(instance.getName().startsWith(NAME_PREFIX));
        assertEquals(CONFIG_DESC, instance.getDescription());
        assertEquals(ZONE, instance.getZone());
        assertEquals(MACHINE_TYPE, instance.getMachineType());
        assertEquals(MIN_CPU_PLATFORM, instance.getMinCpuPlatform());

        // Accelerators
        assertEquals(ACCELERATOR_NAME, instance.getGuestAccelerators().get(0).getAcceleratorType());
        // NOTE(craigatgoogle): Cast is required for disambiguation.
        assertEquals(Integer.parseInt(ACCELERATOR_COUNT), (int)
                instance.getGuestAccelerators().get(0).getAcceleratorCount());

        // Metadata
        Optional<String> startupScript = instance.getMetadata().getItems().stream()
                .filter(item -> item.getKey().equals(InstanceConfiguration.METADATA_LINUX_STARTUP_SCRIPT_KEY))
                .map(Metadata.Items::getValue)
                .findFirst();
        assertTrue(startupScript.isPresent());
        assertEquals(STARTUP_SCRIPT, startupScript.get());

        Optional<String> sshKey = instance.getMetadata().getItems().stream()
                .filter(item -> item.getKey().equals(InstanceConfiguration.SSH_METADATA_KEY))
                .map(Metadata.Items::getValue)
                .findFirst();
        assertTrue(sshKey.isPresent());
        assertFalse(sshKey.get().isEmpty());

        Optional<String> guestAttributes = instance.getMetadata().getItems().stream()
                .filter(item -> item.getKey().equals(InstanceConfiguration.GUEST_ATTRIBUTES_METADATA_KEY))
                .map(Metadata.Items::getValue)
                .findFirst();
        assertTrue(guestAttributes.isPresent());
        assertEquals("TRUE", guestAttributes.get());

        // Network
        assertEquals(SUBNETWORK_NAME, instance.getNetworkInterfaces().get(0).getSubnetwork());
        assertEquals(
                "ONE_TO_ONE_NAT",
                instance.getNetworkInterfaces().get(0).getAccessConfigs().get(0).getType());
        assertEquals(
                "External NAT",
                instance.getNetworkInterfaces().get(0).getAccessConfigs().get(0).getName());

        // Tags
        assertEquals(instance.getTags().getItems().size(), NETWORK_TAGS.split(" ").length);
        assertEquals(NETWORK_TAGS.split(" ")[0], instance.getTags().getItems().get(0));
        assertEquals(NETWORK_TAGS.split(" ")[1], instance.getTags().getItems().get(1));

        // IAM
        assertEquals(SERVICE_ACCOUNT_EMAIL, instance.getServiceAccounts().get(0).getEmail());

        // Disks
        assertEquals(1, instance.getDisks().size());
        assertEquals(BOOT_DISK_AUTODELETE, instance.getDisks().get(0).getAutoDelete());
        assertTrue(instance.getDisks().get(0).getBoot());
        assertEquals(
                BOOT_DISK_TYPE, instance.getDisks().get(0).getInitializeParams().getDiskType());
        assertEquals(
                BOOT_DISK_SIZE_GB,
                instance.getDisks().get(0).getInitializeParams().getDiskSizeGb());
        assertEquals(
                BOOT_DISK_IMAGE_NAME,
                instance.getDisks().get(0).getInitializeParams().getSourceImage());

        InstanceConfiguration instanceConfiguration =
                instanceConfigurationBuilder().build();
        assertFalse(instanceConfiguration.isUseInternalAddress());
        assertNull(instanceConfiguration.instance().getMinCpuPlatform());
        assertNull(instanceConfiguration.getWindowsConfiguration());
    }

    @Test
    void testInstanceMetadata() throws Exception {
        InstanceConfiguration instanceConfiguration =
                instanceConfigurationBuilder().template(TEMPLATE_NAME).build();
        instanceConfiguration.appendLabel("test", "test");
        instanceConfiguration.cloud = cloud;

        Instance instance = instanceConfiguration.instance();

        Object[] sshKeys = instance.getMetadata().getItems().stream()
                .filter(item -> item.getKey().equals(InstanceConfiguration.SSH_METADATA_KEY))
                .map(Metadata.Items::getValue)
                .toArray();
        assertEquals(1, sshKeys.length);
    }

    @Test
    void testInstanceCustomMetadata() throws Exception {
        List<CustomMetadataItem> metadata = List.of(
                new CustomMetadataItem("test-key", "test-value"),
                new CustomMetadataItem("test-multiline", "line1\nline2\nline3"));
        var instanceConfiguration =
                instanceConfigurationBuilder().customMetadata(metadata).build();

        var instance = instanceConfiguration.instance();

        Optional<String> simpleValue = instance.getMetadata().getItems().stream()
                .filter(item -> item.getKey().equals("test-key"))
                .map(Metadata.Items::getValue)
                .findFirst();
        assertTrue(simpleValue.isPresent(), "custom metadata 'test-key' should be present");
        assertEquals("test-value", simpleValue.get());

        Optional<String> multilineValue = instance.getMetadata().getItems().stream()
                .filter(item -> item.getKey().equals("test-multiline"))
                .map(Metadata.Items::getValue)
                .findFirst();
        assertTrue(multilineValue.isPresent(), "custom metadata 'test-multiline' should be present");
        assertEquals("line1\nline2\nline3", multilineValue.get());

        // Verify custom metadata coexists with programmatic metadata
        Optional<String> guestAttributes = instance.getMetadata().getItems().stream()
                .filter(item -> item.getKey().equals(InstanceConfiguration.GUEST_ATTRIBUTES_METADATA_KEY))
                .map(Metadata.Items::getValue)
                .findFirst();
        assertTrue(guestAttributes.isPresent(), "guest attributes should still be present");

        Optional<String> startupScript = instance.getMetadata().getItems().stream()
                .filter(item -> item.getKey().equals(InstanceConfiguration.METADATA_LINUX_STARTUP_SCRIPT_KEY))
                .map(Metadata.Items::getValue)
                .findFirst();
        assertTrue(startupScript.isPresent(), "startup script should still be present");
    }

    @Test
    void testInstanceCustomLabels() throws Exception {
        List<CustomLabelItem> labels = List.of(
                new CustomLabelItem("team", "jenkins"),
                new CustomLabelItem("cost-center", "ci-1234"),
                new CustomLabelItem("", "skip-me"));
        var instanceConfiguration =
                instanceConfigurationBuilder().customLabels(labels).build();

        var instance = instanceConfiguration.instance();

        // Custom labels should be applied to the instance
        assertEquals("jenkins", instance.getLabels().get("team"));
        assertEquals("ci-1234", instance.getLabels().get("cost-center"));

        // Entries with an empty key are skipped
        assertFalse(instance.getLabels().containsValue("skip-me"), "empty-key label should be skipped");

        // The system 'in use' label must always survive alongside custom labels
        assertTrue(
                instance.getLabels().containsKey(CleanLostNodesWork.NODE_IN_USE_LABEL_KEY),
                "system NODE_IN_USE label should still be present");
    }

    public static InstanceConfiguration.Builder instanceConfigurationBuilder() {
        return InstanceConfiguration.builder()
                .namePrefix(NAME_PREFIX)
                .region(REGION)
                .zone(ZONE)
                .machineType(MACHINE_TYPE)
                .numExecutorsStr(NUM_EXECUTORS)
                .startupScript(STARTUP_SCRIPT)
                .provisioningType(PREEMPTIBLE ? new PreemptibleVm() : new Standard(0))
                .labels(LABEL)
                .description(CONFIG_DESC)
                .bootDiskType(BOOT_DISK_TYPE)
                .bootDiskAutoDelete(BOOT_DISK_AUTODELETE)
                .bootDiskSourceImageName(BOOT_DISK_IMAGE_NAME)
                .bootDiskSourceImageProject(BOOT_DISK_PROJECT_ID)
                .bootDiskSizeGbStr(String.valueOf(BOOT_DISK_SIZE_GB))
                .createSnapshot(false)
                .remoteFs(null)
                .networkConfiguration(new AutofilledNetworkConfiguration(NETWORK_NAME, SUBNETWORK_NAME))
                .networkInterfaceIpStackMode(new NetworkInterfaceSingleStack(true))
                .useInternalAddress(false)
                .ignoreProxy(false)
                .networkTags(NETWORK_TAGS)
                .serviceAccountEmail(SERVICE_ACCOUNT_EMAIL)
                .retentionTimeMinutesStr(RETENTION_TIME_MINUTES_STR)
                .launchTimeoutSecondsStr(LAUNCH_TIMEOUT_SECONDS_STR)
                .mode(NODE_MODE)
                .acceleratorConfiguration(new AcceleratorConfiguration(ACCELERATOR_NAME, ACCELERATOR_COUNT))
                .runAsUser(RUN_AS_USER)
                .oneShot(false)
                .shieldedVmConfiguration(new ShieldedVmConfiguration(true, true, true))
                .template(null);
    }

    @Test
    void testDiskMappingAttachesDisks() throws Exception {
        var instance = instanceConfigurationBuilder()
                .cloud(cloud)
                .diskMapping("source-snapshot=snap-a,size=50,type=pd-ssd,auto-delete=yes\n"
                        + "source-snapshot=snap-b,size=100,auto-delete=no")
                .build()
                .instance();

        assertEquals(3, instance.getDisks().size());
        assertTrue(instance.getDisks().get(0).getBoot());

        var diskA = instance.getDisks().get(1);
        assertFalse(diskA.getBoot());
        assertTrue(diskA.getAutoDelete());
        assertEquals(
                "projects/" + PROJECT_ID + "/global/snapshots/snap-a",
                diskA.getInitializeParams().getSourceSnapshot());
        assertEquals(
                "zones/" + ZONE + "/diskTypes/pd-ssd",
                diskA.getInitializeParams().getDiskType());
        assertEquals(Long.valueOf(50), diskA.getInitializeParams().getDiskSizeGb());

        var diskB = instance.getDisks().get(2);
        assertFalse(diskB.getBoot());
        assertFalse(diskB.getAutoDelete());
        assertEquals(
                "projects/" + PROJECT_ID + "/global/snapshots/snap-b",
                diskB.getInitializeParams().getSourceSnapshot());
        assertEquals(Long.valueOf(100), diskB.getInitializeParams().getDiskSizeGb());
    }

    @Test
    void testNoDiskMappingOnlyBootDisk() throws Exception {
        var instance = instanceConfigurationBuilder().build().instance();
        assertEquals(1, instance.getDisks().size());
        assertTrue(instance.getDisks().get(0).getBoot());
    }

    @Test
    void testShieldedInstanceConfigNotSet() throws Exception {
        var instance = instanceConfigurationBuilder()
                .shieldedVmConfiguration(null)
                .build()
                .instance();
        assertNull(instance.getShieldedInstanceConfig());
    }

    @Test
    void testShieldedInstanceConfigAllOn() throws Exception {
        var instance = instanceConfigurationBuilder()
                .shieldedVmConfiguration(new ShieldedVmConfiguration(true, true, true))
                .build()
                .instance();

        var shielded = instance.getShieldedInstanceConfig();
        assertNotNull(shielded);
        assertTrue(shielded.getEnableSecureBoot());
        assertTrue(shielded.getEnableVtpm());
        assertTrue(shielded.getEnableIntegrityMonitoring());
    }

    @Test
    void testShieldedInstanceConfigPartialExplicitDisable() throws Exception {
        var instance = instanceConfigurationBuilder()
                .shieldedVmConfiguration(new ShieldedVmConfiguration(true, false, false))
                .build()
                .instance();

        var shielded = instance.getShieldedInstanceConfig();
        assertNotNull(shielded);
        assertTrue(shielded.getEnableSecureBoot());
        assertFalse(shielded.getEnableVtpm());
        assertFalse(shielded.getEnableIntegrityMonitoring());
    }

    @Test
    void testWrapLinuxStartupScriptWithShebang() {
        var script = "#!/bin/sh\napt-get update\napt-get install -y nginx";
        var reporter = "curl -s -X PUT http://example.com -d \"$1\"";
        var wrapped = InstanceConfiguration.wrapLinuxStartupScript(script, reporter);

        assertThat(wrapped, startsWith("#!/bin/sh\n"));
        assertThat(wrapped, containsString("trap '"));
        assertThat(wrapped, containsString("__gce_plugin_report_status()"));
        assertThat(wrapped, containsString(reporter));
        assertThat(wrapped, containsString("apt-get update"));
        assertThat(wrapped, containsString("apt-get install -y nginx"));
        assertEquals(1, wrapped.split("#!").length - 1, "Shebang should appear exactly once");
    }

    @Test
    void testWrapLinuxStartupScriptWithLeadingComments() {
        var script = "# This is a setup script\n# Author: team\napt-get update";
        var reporter = "curl -s -X PUT http://example.com -d \"$1\"";
        var wrapped = InstanceConfiguration.wrapLinuxStartupScript(script, reporter);

        assertThat(wrapped, not(startsWith("#!")));
        assertThat(
                "Trap should come before original script",
                wrapped.indexOf("trap '"),
                is(lessThan(wrapped.indexOf("# This is a setup script"))));
        assertThat(wrapped, containsString("# Author: team"));
        assertThat(wrapped, containsString("apt-get update"));
    }

    @Test
    void testWrapLinuxStartupScriptNoShebang() {
        var script = "apt-get update\napt-get install -y nginx";
        var reporter = "curl -s -X PUT http://example.com -d \"$1\"";
        var wrapped = InstanceConfiguration.wrapLinuxStartupScript(script, reporter);

        assertThat(wrapped, not(startsWith("#!")));
        assertThat(wrapped, containsString("trap '"));
        assertThat(wrapped, containsString("apt-get update"));
    }

    @Test
    void testWrapLinuxStartupScriptCustomReporter() {
        var script = "echo hello";
        var reporter = "wget -q --method=PUT http://example.com --body-data=\"$1\" -O /dev/null";
        var wrapped = InstanceConfiguration.wrapLinuxStartupScript(script, reporter);

        assertThat(wrapped, containsString("wget -q"));
        assertThat(wrapped, not(containsString("curl")));
        assertThat(wrapped, containsString("echo hello"));
    }

    @Test
    void testWrapLinuxStartupScriptEmptyReporter() {
        var script = "echo hello";
        assertThat(InstanceConfiguration.wrapLinuxStartupScript(script, null), is(script));
        assertThat(InstanceConfiguration.wrapLinuxStartupScript(script, ""), is(script));
    }

    @Test
    void testWrapWindowsStartupScript() {
        var script = "Install-WindowsFeature -Name Web-Server";
        var reporter = "Invoke-RestMethod -Method PUT -Body \"$($args[0])\" -Uri http://example.com";
        var wrapped = InstanceConfiguration.wrapWindowsStartupScript(script, reporter);

        assertThat(wrapped, containsString("try {"));
        assertThat(wrapped, containsString("} catch {"));
        assertThat(wrapped, containsString("} finally {"));
        assertThat(wrapped, containsString("Invoke-RestMethod"));
        assertThat(wrapped, containsString("Install-WindowsFeature"));
        assertThat(wrapped, containsString("$LASTEXITCODE"));
    }

    @Test
    void testWrapWindowsStartupScriptEmptyReporter() {
        var script = "Install-WindowsFeature -Name Web-Server";
        assertThat(InstanceConfiguration.wrapWindowsStartupScript(script, null), is(script));
        assertThat(InstanceConfiguration.wrapWindowsStartupScript(script, ""), is(script));
    }

    @Test
    void testSshPortDefaultValue() {
        InstanceConfiguration config = new InstanceConfiguration();
        config.setSshPort(null);
        assertEquals(InstanceConfiguration.DEFAULT_SSH_PORT, config.getSshPort());
    }

    @Test
    void testSshPortCustomValue() {
        InstanceConfiguration config = new InstanceConfiguration();
        config.setSshPort(2222);
        assertEquals(Integer.valueOf(2222), config.getSshPort());
    }

    @Test
    void testSshPortInvalidValuesFallBackToDefault() {
        InstanceConfiguration config = new InstanceConfiguration();

        config.setSshPort(0);
        assertEquals(InstanceConfiguration.DEFAULT_SSH_PORT, config.getSshPort());

        config.setSshPort(-1);
        assertEquals(InstanceConfiguration.DEFAULT_SSH_PORT, config.getSshPort());

        config.setSshPort(70000);
        assertEquals(InstanceConfiguration.DEFAULT_SSH_PORT, config.getSshPort());
    }

    @Test
    void testSshPortBoundaryValues() {
        InstanceConfiguration config = new InstanceConfiguration();

        config.setSshPort(1);
        assertEquals(Integer.valueOf(1), config.getSshPort());

        config.setSshPort(65535);
        assertEquals(Integer.valueOf(65535), config.getSshPort());
    }

    @Test
    void descriptorSshPortValidation() {
        InstanceConfiguration.DescriptorImpl d = new InstanceConfiguration.DescriptorImpl();

        assertEquals(FormValidation.Kind.OK, d.doCheckSshPort(null).kind);
        assertEquals(FormValidation.Kind.OK, d.doCheckSshPort("").kind);
        assertEquals(FormValidation.Kind.OK, d.doCheckSshPort("22").kind);
        assertEquals(FormValidation.Kind.OK, d.doCheckSshPort("1").kind);
        assertEquals(FormValidation.Kind.OK, d.doCheckSshPort("65535").kind);
        assertEquals(FormValidation.Kind.OK, d.doCheckSshPort("2222").kind);

        assertEquals(FormValidation.Kind.ERROR, d.doCheckSshPort("0").kind);
        assertEquals(FormValidation.Kind.ERROR, d.doCheckSshPort("-1").kind);
        assertEquals(FormValidation.Kind.ERROR, d.doCheckSshPort("65536").kind);
        assertEquals(FormValidation.Kind.ERROR, d.doCheckSshPort("abc").kind);
    }

    @Test
    void descriptorBootDiskSizeValidation() {
        InstanceConfiguration.DescriptorImpl.setComputeClient(computeClient);
        InstanceConfiguration.DescriptorImpl d = new InstanceConfiguration.DescriptorImpl();

        // Empty project, image, and credentials should be OK
        FormValidation fv = d.doCheckBootDiskSizeGbStr(r.jenkins, String.valueOf(BOOT_DISK_SIZE_GB - 1L), "", "", "");
        assertEquals(FormValidation.Kind.OK, fv.kind);

        fv = d.doCheckBootDiskSizeGbStr(
                r.jenkins, String.valueOf(BOOT_DISK_SIZE_GB - 1L), PROJECT_ID, BOOT_DISK_IMAGE_NAME, PROJECT_ID);
        assertEquals(FormValidation.Kind.ERROR, fv.kind);

        fv = d.doCheckBootDiskSizeGbStr(r.jenkins, String.valueOf(BOOT_DISK_SIZE_GB), "", "", "");
        assertEquals(FormValidation.Kind.OK, fv.kind);

        fv = d.doCheckBootDiskSizeGbStr(r.jenkins, String.valueOf(BOOT_DISK_SIZE_GB + 1L), "", "", "");
        assertEquals(FormValidation.Kind.OK, fv.kind);
    }

    @Test
    void testRezoneSelfLink() {
        // full self-link URL
        assertEquals(
                "https://www.googleapis.com/compute/v1/projects/my-project/zones/us-east1-d/machineTypes/n1-standard-1",
                InstanceConfiguration.rezoneSelfLink(
                        "https://www.googleapis.com/compute/v1/projects/my-project/zones/us-east1-b/machineTypes/n1-standard-1",
                        "us-east1-d"));
        // relative path without leading slash
        assertEquals(
                "zones/us-east1-d/diskTypes/pd-ssd",
                InstanceConfiguration.rezoneSelfLink("zones/us-east1-b/diskTypes/pd-ssd", "us-east1-d"));
        // stripped self-link (projects/... without https prefix)
        assertEquals(
                "projects/my-project/zones/us-east1-d/diskTypes/pd-ssd",
                InstanceConfiguration.rezoneSelfLink(
                        "projects/my-project/zones/us-east1-b/diskTypes/pd-ssd", "us-east1-d"));
        // bare short name — no /zones/ segment, returned unchanged
        assertEquals("pd-ssd", InstanceConfiguration.rezoneSelfLink("pd-ssd", "us-east1-d"));
        // null input — returned as null
        assertNull(InstanceConfiguration.rezoneSelfLink(null, "us-east1-d"));
    }

    @Test
    void testCandidateZonesNormalizesToShortNames() {
        var config = instanceConfigurationBuilder()
                .zone("https://www.googleapis.com/compute/v1/projects/my-project/zones/us-west1-a")
                .fallbackZones("us-west1-b, https://www.googleapis.com/compute/v1/projects/my-project/zones/us-west1-c")
                .build();

        assertEquals(List.of("us-west1-a", "us-west1-b", "us-west1-c"), config.candidateZones());
    }

    @Test
    void testMarkExhaustedLogsMachineTypeForInstanceBasedConfiguration() {
        for (String template : new String[] {null, "", " "}) {
            var config = instanceConfigurationBuilder()
                    .machineType("projects/" + PROJECT_ID + "/zones/" + ZONE + "/machineTypes/" + MACHINE_TYPE)
                    .template(template)
                    .build();

            int messageCount = logs.getMessages().size();
            config.markExhausted(ZONE);

            assertTrue(config.isExhausted(ZONE));
            assertThat(
                    logs.getMessages().get(messageCount),
                    startsWith("Marking zone [" + ZONE + "] exhausted for machine type [" + MACHINE_TYPE
                            + "] in config [" + CONFIG_DESC + "] until "));
        }
        assertThat(logs.getMessages(), not(hasItem(containsString("instance template"))));
    }

    @Test
    void testMarkExhaustedLogsTemplateForTemplateBasedConfiguration() {
        for (String machineType : new String[] {null, "", MACHINE_TYPE}) {
            var config = instanceConfigurationBuilder()
                    .machineType(machineType)
                    .template("projects/" + PROJECT_ID + "/global/instanceTemplates/" + TEMPLATE_NAME)
                    .build();

            int messageCount = logs.getMessages().size();
            config.markExhausted(ZONE);

            assertTrue(config.isExhausted(ZONE));
            assertThat(
                    logs.getMessages().get(messageCount),
                    startsWith("Marking zone [" + ZONE + "] exhausted for instance template [" + TEMPLATE_NAME
                            + "] in config [" + CONFIG_DESC + "] until "));
        }
        assertThat(logs.getMessages(), not(hasItem(containsString("machine type"))));
    }

    @Test
    void testMarkExhaustedLogsUnknownForMissingProvisioningSource() {
        var config = instanceConfigurationBuilder().machineType(null).build();

        config.markExhausted(ZONE);

        assertTrue(config.isExhausted(ZONE));
        assertThat(
                logs.getMessages(),
                hasItem(startsWith("Marking zone [" + ZONE + "] exhausted for machine type [unknown] in config ["
                        + CONFIG_DESC + "] until ")));
    }

    @Test
    @LocalData
    void testZoneExhaustionStateTransientFieldInitializedAfterLoad() {
        var cloud = (ComputeEngineCloud) r.jenkins.clouds.getByName("gce-unit-tests");
        var config = cloud.getConfigurations().get(0);

        assertFalse(config.isExhausted(ZONE));
        config.markExhausted(ZONE);
        assertTrue(config.isExhausted(ZONE));
    }
}
