package com.google.jenkins.plugins.computeengine.client;

import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.AcceleratorType;
import com.google.api.services.compute.model.MachineType;
import com.google.api.services.compute.model.Region;
import com.google.api.services.compute.model.Zone;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Client for communicating with the Google Compute API
 *
 *  @see <a href="https://cloud.google.com/compute/">Cloud Engine</a>
 */
public class ComputeClient {
    private final Compute compute;
    private final String projectId;

    ComputeClient(Compute compute, String projectId) {
        this.compute = compute;
        this.projectId = projectId;
    }

    /**
     * @return
     * @throws IOException
     */
    public List<Region> getRegions() throws IOException {
        List<Region> regions = compute
                .regions()
                .list(projectId)
                .execute()
                .getItems();

        // Sort by name
        regions.sort(Comparator.comparing(Region::getName));

        // No deprecated regions
        regions.removeIf(r -> r.getDeprecated() != null);
        return regions;
    }

    public List<Zone> getZones(String region) throws IOException {
        List<Zone> zones = compute
                .zones()
                .list(projectId)
                .execute()
                .getItems();

        // Only zones for the region
        zones.removeIf(z -> !z.getRegion().equals(region));

        // Sort by name
        zones.sort(Comparator.comparing(Zone::getName));
        return zones;
    }

    public List<MachineType> getMachineTypes(String zone) throws IOException {
        zone = zoneFromSelfLink(zone);
        List<MachineType> machineTypes = compute
                .machineTypes()
                .list(projectId, zone)
                .execute()
                .getItems();

        // Only zones for the region
        machineTypes.removeIf(z -> z.getDeprecated() != null);

        // Sort by name
        machineTypes.sort(Comparator.comparing(MachineType::getName));
        return machineTypes;
    }

    public String zoneFromSelfLink(String zoneSelfLink) {
        return zoneSelfLink.substring(zoneSelfLink.lastIndexOf("/") + 1, zoneSelfLink.length());
    }

    public List<AcceleratorType> getAcceleratorTypes(String zone) throws IOException {
        zone = zoneFromSelfLink(zone);

        List<AcceleratorType> acceleratorTypes = compute
                .acceleratorTypes()
                .list(projectId, zone)
                .execute()
                .getItems();
        if (acceleratorTypes == null) {
            acceleratorTypes = new ArrayList<AcceleratorType>();
        }

        if (acceleratorTypes.size() > 0) {
            // Only zones for the region
            acceleratorTypes.removeIf(z -> z.getDeprecated() != null);

            // Sort by name
            acceleratorTypes.sort(Comparator.comparing(AcceleratorType::getName));
        }
        return acceleratorTypes;
    }
}