package com.google.jenkins.plugins.computeengine.client;

import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.DeprecationStatus;
import com.google.api.services.compute.model.Metadata;
import com.google.api.services.compute.model.Region;
import com.google.api.services.compute.model.RegionList;
import com.google.jenkins.plugins.computeengine.InstanceConfigurationTest;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

@RunWith(MockitoJUnitRunner.class)
public class ComputeClientTest {
    @Mock
    public Compute compute;

    @Mock
    public Compute.Regions regions;

    @Mock
    public Compute.Regions.List regionsListCall;

    @Mock
    public RegionList regionList;

    @InjectMocks
    ComputeClient computeClient;

    List<Region> listOfRegions;

    @Before
    public void init() throws Exception {
        listOfRegions = new ArrayList<Region>();

        // Mock regions
        Mockito.when(regionList.getItems()).thenReturn(listOfRegions);
        Mockito.when(regionsListCall.execute()).thenReturn(regionList);
        Mockito.when(regions.list(InstanceConfigurationTest.PROJECT_ID)).thenReturn(regionsListCall);
        Mockito.when(compute.regions()).thenReturn(regions);
    }

    @Test
    public void getRegions() throws IOException {
        listOfRegions.clear();
        listOfRegions.add(new Region().setName("us-west1"));
        listOfRegions.add(new Region().setName("eu-central1"));
        listOfRegions.add(new Region().setName("us-central1"));
        listOfRegions.add(new Region().setName("us-east1")
                .setDeprecated(new DeprecationStatus().setDeprecated("DEPRECATED")));

        assertEquals(3, computeClient.getRegions(InstanceConfigurationTest.PROJECT_ID).size());
        assertEquals("eu-central1", computeClient.getRegions(InstanceConfigurationTest.PROJECT_ID).get(0).getName());
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
