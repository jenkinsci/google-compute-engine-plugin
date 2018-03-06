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

import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Client for communicating with the Google Compute API
 *
 * @see <a href="https://cloud.google.com/compute/">Cloud Engine</a>
 */
public class ComputeClient {
    private Compute compute;

    public static String zoneFromSelfLink(String zoneSelfLink) {
        return zoneSelfLink.substring(zoneSelfLink.lastIndexOf("/") + 1, zoneSelfLink.length());
    }

    public static String regionFromSelfLink(String regionSelfLink) {
        return regionSelfLink.substring(regionSelfLink.lastIndexOf("/") + 1, regionSelfLink.length());
    }

    public static String lastParam(String value) {
        if (value.contains("/"))
            value = value.substring(value.lastIndexOf("/") + 1, value.length());
        return value;
    }

    public static String buildLabelsFilterString(Map<String, String> labels) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> l : labels.entrySet()) {
            sb.append("(labels." + l.getKey() + " eq " + l.getValue() + ") ");
        }
        return sb.toString().trim();
    }

    public static List<Metadata.Items> mergeMetadataItems(List<Metadata.Items> winner, List<Metadata.Items> loser) {
        if (loser == null) {
            loser = new ArrayList<Metadata.Items>();
        }

        // Remove any existing metadata that has the same key(s) as what we're trying to update/append
        for (Metadata.Items existing : loser) {
            boolean duplicate = false;
            for (Metadata.Items newItem : winner) { // Items to append
                if (existing.getKey().equals(newItem.getKey())) {
                    duplicate = true;
                }
            }
            if (!duplicate) {
                winner.add(existing);
            }
        }
        return winner;
    }

    public void setCompute(Compute compute) {
        this.compute = compute;
    }

    /**
     * @return
     * @throws IOException
     */
    public List<Region> getRegions(String projectId) throws IOException {
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

    public List<Zone> getZones(String projectId, String region) throws IOException {
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

    public List<MachineType> getMachineTypes(String projectId, String zone) throws IOException {
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

    public List<DiskType> getDiskTypes(String projectId, String zone) throws IOException {
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

    public List<DiskType> getBootDiskTypes(String projectId, String zone) throws IOException {
        zone = zoneFromSelfLink(zone);
        List<DiskType> diskTypes = this.getDiskTypes(projectId, zone);

        // No local disks
        diskTypes.removeIf(z -> z.getName().startsWith("local-"));

        return diskTypes;
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

    public Image getImage(String projectId, String name) throws IOException {
        Image image = compute
                .images()
                .get(projectId, name)
                .execute();

        return image;
    }

    public List<AcceleratorType> getAcceleratorTypes(String projectId, String zone) throws IOException {
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

    public Operation insertInstance(String projectId, Instance i) throws IOException {
        return compute.instances().insert(projectId, i.getZone(), i).execute();
    }

    public Operation terminateInstance(String projectId, String zone, String InstanceId) throws IOException {
        zone = zoneFromSelfLink(zone);
        return compute.instances().delete(projectId, zone, InstanceId).execute();
    }

    public Operation terminateInstanceWithStatus(String projectId, String zone, String instanceId, String desiredStatus) throws IOException, InterruptedException {
        zone = zoneFromSelfLink(zone);
        Instance i = getInstance(projectId, zone, instanceId);
        if (i.getStatus().equals(desiredStatus)) {
            return compute.instances().delete(projectId, zone, instanceId).execute();
        }
        return null;
    }

    public Instance getInstance(String projectId, String zone, String instanceId) throws IOException {
        zone = zoneFromSelfLink(zone);
        return compute.instances().get(projectId, zone, instanceId).execute();
    }

    /**
     * Return all instances that contain the given labels
     *
     * @param projectId
     * @param labels
     * @return
     * @throws IOException
     */
    public List<Instance> getInstancesWithLabel(String projectId, Map<String, String> labels) throws IOException {
        Compute.Instances.AggregatedList request = compute.instances().aggregatedList(projectId);
        request.setFilter(buildLabelsFilterString(labels));
        Map<String, InstancesScopedList> result = request.execute().getItems();
        List<Instance> instances = new ArrayList<>();
        for (InstancesScopedList instancesInZone : result.values()) {
            if (instancesInZone.getInstances() != null) {
                instances.addAll(instancesInZone.getInstances());
            }
        }
        return instances;
    }

    /**
     * Appends metadata to an instance. Any metadata items with existing keys will be overwritten. Otherwise, metadata
     * is preserved. This method blocks until the operation completes.
     *
     * @param projectId
     * @param zone
     * @param instanceId
     * @param items
     * @throws IOException
     * @throws InterruptedException
     */
    public Operation.Error appendInstanceMetadata(String projectId, String zone, String instanceId, List<Metadata.Items> items) throws IOException, InterruptedException {
        zone = zoneFromSelfLink(zone);
        Instance instance = getInstance(projectId, zone, instanceId);
        Metadata existingMetadata = instance.getMetadata();

        List<Metadata.Items> newMetadataItems = mergeMetadataItems(items, existingMetadata.getItems());
        existingMetadata.setItems(newMetadataItems);

        Operation op = compute.instances().setMetadata(projectId, zone, instanceId, existingMetadata).execute();
        return waitForOperationCompletion(projectId, op, 60 * 1000);
    }

    /**
     * Blocks until an existing operation completes.
     *
     * @param projectId
     * @param operation
     * @param timeout
     * @return
     * @throws IOException
     * @throws InterruptedException
     */
    public Operation.Error waitForOperationCompletion(String projectId, Operation operation, long timeout)
            throws IOException, InterruptedException {
        if (operation == null) {
            throw new IllegalArgumentException("Operation can not be null");
        }

        long start = System.currentTimeMillis();
        final long POLL_INTERVAL = 5 * 1000;
        String zone = operation.getZone();  // null for global/regional operations
        if (zone != null) {
            String[] bits = zone.split("/");
            zone = bits[bits.length - 1];
        }
        String status = operation.getStatus();
        String opId = operation.getName();
        while (!status.equals("DONE")) {
            Thread.sleep(POLL_INTERVAL);
            long elapsed = System.currentTimeMillis() - start;
            if (elapsed >= timeout) {
                throw new InterruptedException("Timed out waiting for operation to complete");
            }
            System.out.println("waiting...");
            if (zone != null) {
                Compute.ZoneOperations.Get get = compute.zoneOperations().get(projectId, zone, opId);
                operation = get.execute();
            } else {
                Compute.GlobalOperations.Get get = compute.globalOperations().get(projectId, opId);
                operation = get.execute();
            }
            if (operation != null) {
                status = operation.getStatus();
            }
        }
        return operation.getError();
    }
}