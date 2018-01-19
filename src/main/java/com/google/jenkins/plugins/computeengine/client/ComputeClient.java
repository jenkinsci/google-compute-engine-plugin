package com.google.jenkins.plugins.computeengine.client;

import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Client for communicating with the Google Compute API
 *
 * @see <a href="https://cloud.google.com/compute/">Cloud Engine</a>
 */
public class ComputeClient {
    private Compute compute;
    private String projectId;

    public void setCompute(Compute compute) {
        this.compute = compute;
    }

    public void setProjectId(String projectId) {
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

        // No deprecated items
        machineTypes.removeIf(z -> z.getDeprecated() != null);

        // Sort by name
        machineTypes.sort(Comparator.comparing(MachineType::getName));
        return machineTypes;
    }

    public List<DiskType> getDiskTypes(String zone) throws IOException {
        zone = zoneFromSelfLink(zone);
        List<DiskType> diskTypes = compute
                .diskTypes()
                .list(projectId, zone)
                .execute()
                .getItems();

        // No deprecated items
        diskTypes.removeIf(z -> z.getDeprecated() != null);

        // Sort by name
        diskTypes.sort(Comparator.comparing(DiskType::getName));
        return diskTypes;
    }

    public List<DiskType> getBootDiskTypes(String zone) throws IOException {
        List<DiskType> diskTypes = this.getDiskTypes(zone);

        // No local disks
        diskTypes.removeIf(z -> z.getName().startsWith("local-"));

        return diskTypes;
    }

    public List<Image> getImages() throws IOException {
        return getImages(projectId);
    }

    public List<Image> getImages(String projectId) throws IOException {
        List<Image> images = compute
                .images()
                .list(projectId)
                .execute()
                .getItems();

        // No deprecated items
        images.removeIf(z -> z.getDeprecated() != null);

        // Sort by name
        images.sort(Comparator.comparing(Image::getName));
        return images;
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

        //TODO: do we need to check size?
        if (acceleratorTypes.size() > 0) {
            // Only zones for the region
            acceleratorTypes.removeIf(z -> z.getDeprecated() != null);

            // Sort by name
            acceleratorTypes.sort(Comparator.comparing(AcceleratorType::getName));
        }
        return acceleratorTypes;
    }

    public List<Network> getNetworks(String projectId) throws IOException {
        List<Network> networks = compute
                .networks()
                .list(projectId)
                .execute()
                .getItems();

        if (networks == null) {
            networks = new ArrayList<Network>();
        }
        return networks;
    }

    public List<Network> getNetworks() throws IOException {
        return getNetworks(projectId);
    }

    public List<Subnetwork> getSubnetworks(String projectId, String networkSelfLink, String region) throws IOException {
        region = regionFromSelfLink(region);
        List<Subnetwork> subnetworks = compute
                .subnetworks()
                .list(projectId, region)
                .execute()
                .getItems();

        // Only subnetworks in the parent network
        subnetworks.removeIf(z -> !z.getNetwork().equals(networkSelfLink));

        // Sort by name
        subnetworks.sort(Comparator.comparing(Subnetwork::getName));

        return subnetworks;
    }

    public List<Subnetwork> getSubnetworks(String networkSelfLink, String region) throws IOException {
        return getSubnetworks(projectId, networkSelfLink, region);
    }

    public static String zoneFromSelfLink(String zoneSelfLink) {
        return zoneSelfLink.substring(zoneSelfLink.lastIndexOf("/") + 1, zoneSelfLink.length());
    }

    public static String regionFromSelfLink(String regionSelfLink) {
        return regionSelfLink.substring(regionSelfLink.lastIndexOf("/") + 1, regionSelfLink.length());
    }
}