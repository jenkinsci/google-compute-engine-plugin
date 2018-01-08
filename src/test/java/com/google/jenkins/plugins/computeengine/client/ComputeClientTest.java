package com.google.jenkins.plugins.computeengine.client;

import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson.JacksonFactory;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.DeprecationStatus;
import com.google.api.services.compute.model.Region;
import com.google.api.services.compute.model.RegionList;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.MockitoRule;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

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

    @Test
    public void getRegions() throws IOException {
        List<Region> listOfRegions = new ArrayList<Region>();
        listOfRegions.add(new Region().setName("us-west1"));
        listOfRegions.add(new Region().setName("eu-central1"));
        listOfRegions.add(new Region().setName("us-central1"));
        listOfRegions.add(new Region().setName("us-east1")
                .setDeprecated(new DeprecationStatus().setDeprecated("DEPRECATED")));

        Mockito.when(regionList.getItems()).thenReturn(listOfRegions);
        Mockito.when(regionsListCall.execute()).thenReturn(regionList);
        Mockito.when(regions.list("test-project")).thenReturn(regionsListCall);
        Mockito.when(compute.regions()).thenReturn(regions);

        ComputeClient computeClient = new ComputeClient(compute, "test-project");

        assertEquals(3, computeClient.getRegions().size());
        assertEquals("eu-central1", computeClient.getRegions().get(0).getName());
    }

    @Test
    public void zoneSelfLink() {
        ComputeClient computeClient = new ComputeClient(compute, "test-project");
        String zone;

        zone = "https://www.googleapis.com/compute/v1/projects/evandbrown17/zones/asia-east1-a";
        assertEquals("asia-east1-a", computeClient.zoneFromSelfLink(zone));

        zone = "asia-east1-a";
        assertEquals("asia-east1-a", computeClient.zoneFromSelfLink(zone));
    }
}
