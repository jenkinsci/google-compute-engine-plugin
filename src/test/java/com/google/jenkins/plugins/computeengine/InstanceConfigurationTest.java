/*
 * Copyright 2018 Google LLC
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;

import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.google.api.services.compute.model.AcceleratorType;
import com.google.api.services.compute.model.DiskType;
import com.google.api.services.compute.model.Image;
import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.MachineType;
import com.google.api.services.compute.model.Network;
import com.google.api.services.compute.model.Region;
import com.google.api.services.compute.model.Subnetwork;
import com.google.api.services.compute.model.Zone;
import com.google.jenkins.plugins.computeengine.client.ComputeClient;
import hudson.model.Node;
import hudson.util.FormValidation;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class InstanceConfigurationTest {
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
  public static final boolean WINDOWS = false;

  @Mock public ComputeClient computeClient;

  @Rule public JenkinsRule r = new JenkinsRule();

  @Before
  public void init() throws Exception {
    List<Region> regions = new ArrayList<Region>();
    regions.add(new Region().setName("").setSelfLink(""));
    regions.add(new Region().setName(REGION).setSelfLink(REGION));

    List<Zone> zones = new ArrayList<Zone>();
    zones.add(new Zone().setName("").setSelfLink(""));
    zones.add(new Zone().setName(ZONE).setSelfLink(ZONE));

    List<MachineType> machineTypes = new ArrayList<MachineType>();
    machineTypes.add(new MachineType().setName("").setSelfLink(""));
    machineTypes.add(new MachineType().setName(MACHINE_TYPE).setSelfLink(MACHINE_TYPE));

    List<String> cpuPlatforms = new ArrayList<>();
    cpuPlatforms.add("");
    cpuPlatforms.add("Intel Skylake");
    cpuPlatforms.add("Intel Haswell");

    List<DiskType> diskTypes = new ArrayList<DiskType>();
    diskTypes.add(new DiskType().setName("").setSelfLink(""));
    diskTypes.add(new DiskType().setName(BOOT_DISK_TYPE).setSelfLink(BOOT_DISK_TYPE));

    List<Image> imageTypes = new ArrayList<Image>();
    imageTypes.add(new Image().setName("").setSelfLink(""));
    imageTypes.add(new Image().setName(BOOT_DISK_IMAGE_NAME).setSelfLink(BOOT_DISK_IMAGE_NAME));

    Image image = new Image();
    image
        .setName(BOOT_DISK_IMAGE_NAME)
        .setSelfLink(BOOT_DISK_IMAGE_NAME)
        .setDiskSizeGb(BOOT_DISK_SIZE_GB);

    List<Network> networks = new ArrayList<Network>();
    networks.add(new Network().setName("").setSelfLink(""));
    networks.add(new Network().setName(NETWORK_NAME).setSelfLink(NETWORK_NAME));

    List<Subnetwork> subnetworks = new ArrayList<Subnetwork>();
    subnetworks.add(new Subnetwork().setName("").setSelfLink(""));
    subnetworks.add(new Subnetwork().setName(SUBNETWORK_NAME).setSelfLink(SUBNETWORK_NAME));

    List<AcceleratorType> acceleratorTypes = new ArrayList<AcceleratorType>();
    acceleratorTypes.add(
        new AcceleratorType().setName("").setSelfLink("").setMaximumCardsPerInstance(0));
    acceleratorTypes.add(
        new AcceleratorType()
            .setName(ACCELERATOR_NAME)
            .setSelfLink(ACCELERATOR_NAME)
            .setMaximumCardsPerInstance(Integer.parseInt(ACCELERATOR_COUNT)));

    Mockito.when(computeClient.getRegions(anyString())).thenReturn(regions);
    Mockito.when(computeClient.getZones(anyString(), anyString())).thenReturn(zones);
    Mockito.when(computeClient.getMachineTypes(anyString(), anyString())).thenReturn(machineTypes);
    Mockito.when(computeClient.cpuPlatforms(anyString(), anyString())).thenReturn(cpuPlatforms);
    Mockito.when(computeClient.getBootDiskTypes(anyString(), anyString())).thenReturn(diskTypes);
    Mockito.when(computeClient.getImage(anyString(), anyString())).thenReturn(image);
    Mockito.when(computeClient.getImages(anyString())).thenReturn(imageTypes);
    Mockito.when(computeClient.getAcceleratorTypes(anyString(), anyString()))
        .thenReturn(acceleratorTypes);
    Mockito.when(computeClient.getNetworks(anyString())).thenReturn(networks);
    Mockito.when(computeClient.getSubnetworks(anyString(), anyString(), anyString()))
        .thenReturn(subnetworks);
  }

  @Test
  public void testClient() throws Exception {
    List<Region> regions = computeClient.getRegions(anyString());
    assert (regions.size() == 2);
    assert (regions.get(1).getName().equals(REGION));

    List<Zone> zones = computeClient.getZones(PROJECT_ID, REGION);
    assert (zones.size() == 2);
    assert (zones.get(1).getName().equals(ZONE));
    assert (zones.get(1).getSelfLink().equals(ZONE));
  }

  @Test
  public void testConfigRoundtrip() throws Exception {
    InstanceConfiguration want = instanceConfiguration();

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
    InstanceConfiguration got =
        ((ComputeEngineCloud) r.jenkins.clouds.iterator().next())
            .getInstanceConfigurationByDescription(CONFIG_DESC);
    r.assertEqualBeans(
        want,
        got,
        "namePrefix,region,zone,machineType,preemptible,windows,minCpuPlatform,startupScript,bootDiskType,bootDiskSourceImageName,bootDiskSourceImageProject,bootDiskSizeGb,acceleratorConfiguration,networkConfiguration,externalAddress,networkTags,serviceAccountEmail");
  }

  @Test
  public void testInstanceModel() throws Exception {
    Instance instance = instanceConfiguration(MIN_CPU_PLATFORM).instance();
    // General
    assertTrue(instance.getName().startsWith(NAME_PREFIX));
    assertEquals(CONFIG_DESC, instance.getDescription());
    assertEquals(ZONE, instance.getZone());
    assertEquals(MACHINE_TYPE, instance.getMachineType());
    assertEquals(MIN_CPU_PLATFORM, instance.getMinCpuPlatform());

    // Accelerators
    assertEquals(ACCELERATOR_NAME, instance.getGuestAccelerators().get(0).getAcceleratorType());
    // NOTE(craigatgoogle): Cast is required for disambiguation.
    assertEquals(
        (int) Integer.parseInt(ACCELERATOR_COUNT),
        (int) instance.getGuestAccelerators().get(0).getAcceleratorCount());

    // Metadata
    Optional<String> startupScript =
        instance.getMetadata().getItems().stream()
            .filter(
                item ->
                    item.getKey().equals(InstanceConfiguration.METADATA_LINUX_STARTUP_SCRIPT_KEY))
            .map(item -> item.getValue())
            .findFirst();
    assertTrue(startupScript.isPresent());
    assertEquals(STARTUP_SCRIPT, startupScript.get());

    Optional<String> sshKey =
        instance.getMetadata().getItems().stream()
            .filter(item -> item.getKey().equals(InstanceConfiguration.SSH_METADATA_KEY))
            .map(item -> item.getValue())
            .findFirst();
    assertTrue(sshKey.isPresent());
    assertFalse(sshKey.get().isEmpty());

    // Network
    assertEquals(SUBNETWORK_NAME, instance.getNetworkInterfaces().get(0).getSubnetwork());
    assertEquals(
        "ONE_TO_ONE_NAT",
        instance.getNetworkInterfaces().get(0).getAccessConfigs().get(0).getType());
    assertEquals(
        "External NAT", instance.getNetworkInterfaces().get(0).getAccessConfigs().get(0).getName());

    // Tags
    assertTrue(instance.getTags().getItems().size() == NETWORK_TAGS.split(" ").length);
    assertEquals(NETWORK_TAGS.split(" ")[0], instance.getTags().getItems().get(0));
    assertEquals(NETWORK_TAGS.split(" ")[1], instance.getTags().getItems().get(1));

    // IAM
    assertEquals(SERVICE_ACCOUNT_EMAIL, instance.getServiceAccounts().get(0).getEmail());

    // Disks
    assertEquals(BOOT_DISK_AUTODELETE, instance.getDisks().get(0).getAutoDelete());
    assertTrue(instance.getDisks().get(0).getBoot());
    assertEquals(BOOT_DISK_TYPE, instance.getDisks().get(0).getInitializeParams().getDiskType());
    assertEquals(
        BOOT_DISK_SIZE_GB, instance.getDisks().get(0).getInitializeParams().getDiskSizeGb());
    assertEquals(
        BOOT_DISK_IMAGE_NAME, instance.getDisks().get(0).getInitializeParams().getSourceImage());

    InstanceConfiguration instanceConfiguration = instanceConfiguration();
    assertFalse(instanceConfiguration.isUseInternalAddress());
    assertNull(instanceConfiguration.instance().getMinCpuPlatform());
  }

  public static InstanceConfiguration instanceConfiguration() {
    return instanceConfiguration("");
  }

  public static InstanceConfiguration instanceConfiguration(String minCpuPlatform) {
    return new InstanceConfiguration.Builder()
        .namePrefix(NAME_PREFIX)
        .region(REGION)
        .zone(ZONE)
        .machineType(MACHINE_TYPE)
        .numExecutorsStr(NUM_EXECUTORS)
        .startupScript(STARTUP_SCRIPT)
        .preemptible(PREEMPTIBLE)
        .minCpuPlatform(minCpuPlatform)
        .labels(LABEL)
        .description(CONFIG_DESC)
        .bootDiskType(BOOT_DISK_TYPE)
        .bootDiskAutoDelete(BOOT_DISK_AUTODELETE)
        .bootDiskSourceImageName(BOOT_DISK_IMAGE_NAME)
        .bootDiskSourceImageProject(BOOT_DISK_PROJECT_ID)
        .bootDiskSizeGbStr(String.valueOf(BOOT_DISK_SIZE_GB))
        .windows(WINDOWS)
        .windowsPasswordCredentialsId("")
        .windowsPrivateKeyCredentialsId("")
        .createSnapshot(false)
        .remoteFs(null)
        .networkConfiguration(new AutofilledNetworkConfiguration(NETWORK_NAME, SUBNETWORK_NAME))
        .externalAddress(EXTERNAL_ADDR)
        .useInternalAddress(false)
        .networkTags(NETWORK_TAGS)
        .serviceAccountEmail(SERVICE_ACCOUNT_EMAIL)
        .retentionTimeMinutesStr(RETENTION_TIME_MINUTES_STR)
        .launchTimeoutSecondsStr(LAUNCH_TIMEOUT_SECONDS_STR)
        .mode(NODE_MODE)
        .acceleratorConfiguration(new AcceleratorConfiguration(ACCELERATOR_NAME, ACCELERATOR_COUNT))
        .runAsUser(RUN_AS_USER)
        .oneShot(false)
        .template(null)
        .build();
  }

  @Test
  public void descriptorBootDiskSizeValidation() throws Exception {
    InstanceConfiguration.DescriptorImpl.setComputeClient(computeClient);
    InstanceConfiguration.DescriptorImpl d = new InstanceConfiguration.DescriptorImpl();

    // Empty project, image, and credentials should be OK
    FormValidation fv =
        d.doCheckBootDiskSizeGbStr(r.jenkins, String.valueOf(BOOT_DISK_SIZE_GB - 1L), "", "", "");
    assertEquals(FormValidation.Kind.OK, fv.kind);

    fv =
        d.doCheckBootDiskSizeGbStr(
            r.jenkins,
            String.valueOf(BOOT_DISK_SIZE_GB - 1L),
            PROJECT_ID,
            BOOT_DISK_IMAGE_NAME,
            PROJECT_ID);
    assertEquals(FormValidation.Kind.ERROR, fv.kind);

    fv = d.doCheckBootDiskSizeGbStr(r.jenkins, String.valueOf(BOOT_DISK_SIZE_GB), "", "", "");
    assertEquals(FormValidation.Kind.OK, fv.kind);

    fv = d.doCheckBootDiskSizeGbStr(r.jenkins, String.valueOf(BOOT_DISK_SIZE_GB + 1L), "", "", "");
    assertEquals(FormValidation.Kind.OK, fv.kind);
  }
}
