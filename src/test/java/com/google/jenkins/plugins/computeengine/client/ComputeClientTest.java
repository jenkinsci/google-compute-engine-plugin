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
package com.google.jenkins.plugins.computeengine.client;

import static org.junit.Assert.assertEquals;

import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.*;
import com.google.jenkins.plugins.computeengine.InstanceConfigurationTest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ComputeClientTest {
  @Mock public Compute compute;

  @Mock public Compute.Regions regions;

  @Mock public Compute.Regions.List regionsListCall;

  @Mock public RegionList regionList;

  @Mock public Compute.Zones zones;

  @Mock public Compute.Zones.List zonesListCall;

  @Mock public ZoneList zoneList;

  @Mock public Compute.MachineTypes machineTypes;

  @Mock Compute.MachineTypes.List machineTypesListCall;

  @Mock public MachineTypeList machineTypeList;

  @Mock public Compute.DiskTypes diskTypes;

  @Mock Compute.DiskTypes.List diskTypesListCall;

  @Mock public DiskTypeList diskTypeList;

  @InjectMocks ComputeClient computeClient;

  List<Region> listOfRegions;
  List<Zone> listOfZones;
  List<MachineType> listOfMachineTypes;
  List<DiskType> listOfDiskTypes;

  @Before
  public void init() throws Exception {
    listOfRegions = new ArrayList<>();
    listOfZones = new ArrayList<>();
    listOfMachineTypes = new ArrayList<>();
    listOfDiskTypes = new ArrayList<>();

    // Mock regions
    Mockito.when(regionList.getItems()).thenReturn(listOfRegions);
    Mockito.when(regionsListCall.execute()).thenReturn(regionList);
    Mockito.when(regions.list(InstanceConfigurationTest.PROJECT_ID)).thenReturn(regionsListCall);
    Mockito.when(compute.regions()).thenReturn(regions);

    // Mock zones
    Mockito.when(zoneList.getItems()).thenReturn(listOfZones);
    Mockito.when(zonesListCall.execute()).thenReturn(zoneList);
    Mockito.when(zones.list(InstanceConfigurationTest.PROJECT_ID)).thenReturn(zonesListCall);
    Mockito.when(compute.zones()).thenReturn(zones);

    // Mock machine types
    Mockito.when(machineTypeList.getItems()).thenReturn(listOfMachineTypes);
    Mockito.when(machineTypesListCall.execute()).thenReturn(machineTypeList);
    Mockito.when(machineTypes.list(Mockito.anyString(), Mockito.anyString()))
        .thenReturn(machineTypesListCall);
    Mockito.when(compute.machineTypes()).thenReturn(machineTypes);

    // Mock disk types
    Mockito.when(diskTypeList.getItems()).thenReturn(listOfDiskTypes);
    Mockito.when(diskTypesListCall.execute()).thenReturn(diskTypeList);
    Mockito.when(diskTypes.list(Mockito.anyString(), Mockito.anyString()))
        .thenReturn(diskTypesListCall);
    Mockito.when(compute.diskTypes()).thenReturn(diskTypes);
  }

  @Test
  public void getRegions() throws IOException {
    listOfRegions.clear();
    listOfRegions.add(new Region().setName("us-west1"));
    listOfRegions.add(new Region().setName("eu-central1"));
    listOfRegions.add(new Region().setName("us-central1"));
    listOfRegions.add(
        new Region()
            .setName("us-east1")
            .setDeprecated(new DeprecationStatus().setState("DEPRECATED")));

    assertEquals(3, computeClient.getRegions(InstanceConfigurationTest.PROJECT_ID).size());
    assertEquals(
        "eu-central1",
        computeClient.getRegions(InstanceConfigurationTest.PROJECT_ID).get(0).getName());
  }

  @Test
  public void getZones() throws IOException {
    listOfZones.clear();
    listOfZones.add(new Zone().setRegion("us-west1").setName("us-west1-b"));
    listOfZones.add(new Zone().setRegion("eu-central1").setName("eu-central1-a"));
    listOfZones.add(new Zone().setRegion("us-west1").setName("us-west1-a"));

    assertEquals(
        2, computeClient.getZones(InstanceConfigurationTest.PROJECT_ID, "us-west1").size());
    assertEquals(
        "us-west1-a",
        computeClient.getZones(InstanceConfigurationTest.PROJECT_ID, "us-west1").get(0).getName());

    listOfZones.clear();
    assertEquals(
        0, computeClient.getZones(InstanceConfigurationTest.PROJECT_ID, "us-west1").size());
  }

  @Test
  public void getMachineTypes() throws IOException {
    listOfMachineTypes.clear();
    listOfMachineTypes.add(new MachineType().setName("b"));
    listOfMachineTypes.add(new MachineType().setName("a"));
    listOfMachineTypes.add(new MachineType().setName("z"));
    listOfMachineTypes.add(
        new MachineType()
            .setName("d")
            .setDeprecated(new DeprecationStatus().setState("DEPRECATED")));

    assertEquals(3, computeClient.getMachineTypes("", "test").size());
    assertEquals("a", computeClient.getMachineTypes("", "test").get(0).getName());
  }

  @Test
  public void getDiskTypes() throws IOException {
    listOfDiskTypes.clear();
    listOfDiskTypes.add(new DiskType().setName("b"));
    listOfDiskTypes.add(new DiskType().setName("a"));
    listOfDiskTypes.add(new DiskType().setName("z"));
    listOfDiskTypes.add(new DiskType().setName("local-d"));
    listOfDiskTypes.add(
        new DiskType().setName("d").setDeprecated(new DeprecationStatus().setState("DEPRECATED")));

    assertEquals(3, computeClient.getBootDiskTypes("", "test").size());
    assertEquals("a", computeClient.getBootDiskTypes("", "test").get(0).getName());
  }

  @Test
  public void zoneSelfLink() {
    String zone;

    zone = "https://www.googleapis.com/compute/v1/projects/evandbrown17/zones/asia-east1-a";
    assertEquals("asia-east1-a", ComputeClient.zoneFromSelfLink(zone));

    zone = "asia-east1-a";
    assertEquals("asia-east1-a", ComputeClient.zoneFromSelfLink(zone));
  }

  @Test
  public void labelsToFilterString() {
    Map<String, String> labels = new LinkedHashMap<String, String>();
    labels.put("key1", "value1");
    labels.put("key2", "value2");
    String expect = "(labels.key1 eq value1) (labels.key2 eq value2)";

    String got = ComputeClient.buildLabelsFilterString(labels);
    assertEquals(expect, got);
  }

  @Test
  public void mergeMetadataItemsTest() {
    List<Metadata.Items> newItems = new ArrayList<>();
    newItems.add(new Metadata.Items().setKey("ssh-keys").setValue("new"));

    List<Metadata.Items> existingItems = new ArrayList<>();
    existingItems.add(new Metadata.Items().setKey("ssh-keys").setValue("old"));
    existingItems.add(new Metadata.Items().setKey("no-overwrite").setValue("no-overwrite"));

    List<Metadata.Items> merged = ComputeClient.mergeMetadataItems(newItems, existingItems);

    assertEquals(existingItems.size(), merged.size());
  }
}
