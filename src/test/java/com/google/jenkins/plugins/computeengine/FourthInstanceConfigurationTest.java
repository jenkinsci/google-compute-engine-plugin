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

import static org.mockito.ArgumentMatchers.anyString;

import com.google.api.services.compute.model.AcceleratorType;
import com.google.api.services.compute.model.DiskType;
import com.google.api.services.compute.model.Image;
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
import hudson.model.Node;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.MockitoRule;
import org.mockito.quality.Strictness;

@RunWith(MockitoJUnitRunner.Silent.class)
public class FourthInstanceConfigurationTest {

  @Rule public MockitoRule experimentRule = MockitoJUnit.rule().strictness(Strictness.LENIENT);

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

  @Mock public ComputeEngineCloud cloud;

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
    InstanceTemplate instanceTemplate =
        new InstanceTemplate()
            .setName(TEMPLATE_NAME)
            .setProperties(
                new InstanceProperties()
                    .setMetadata(
                        new Metadata()
                            .setItems(
                                Stream.of(
                                        new Metadata.Items()
                                            .set("key", "ssh-keys")
                                            .set("value", "TEST"))
                                    .collect(Collectors.toList()))));
    Mockito.when(computeClient.listRegions(anyString())).thenReturn(ImmutableList.copyOf(regions));
    Mockito.when(computeClient.listZones(anyString(), anyString()))
        .thenReturn(ImmutableList.copyOf(zones));
  }

  @Test
  public void testClient() throws Exception {
    List<Region> regions = computeClient.listRegions(anyString());
    assert (regions.size() == 2);
    assert (regions.get(1).getName().equals(REGION));
    List<Zone> zones = computeClient.listZones(PROJECT_ID, REGION);
    assert (zones.size() == 2);
    assert (zones.get(1).getName().equals(ZONE));
    assert (zones.get(1).getSelfLink().equals(ZONE));
  }

  public static InstanceConfiguration.Builder instanceConfigurationBuilder() {
    return InstanceConfiguration.builder()
        .namePrefix(NAME_PREFIX)
        .region(REGION)
        .zone(ZONE)
        .machineType(MACHINE_TYPE)
        .numExecutorsStr(NUM_EXECUTORS)
        .startupScript(STARTUP_SCRIPT)
        .preemptible(PREEMPTIBLE)
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
        .externalAddress(EXTERNAL_ADDR)
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
        .template(null);
  }
}
