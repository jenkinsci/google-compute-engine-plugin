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
import com.google.common.base.Strings;

import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Client for communicating with the Google Compute API
 *
 * @see <a href="https://cloud.google.com/compute/">Cloud Engine</a>
 */
public class ComputeClient {
    private static final Logger LOGGER = Logger.getLogger(ComputeClient.class.getName());
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
        if (regions == null) {
            regions = new ArrayList<Region>();
        }

        // No deprecated regions
        Iterator it = regions.iterator();
        while (it.hasNext()) {
            Region o = (Region) it.next();
            if (o.getDeprecated() != null && o.getDeprecated().getState().equalsIgnoreCase("DEPRECATED")) {
                it.remove();
            }
        }

        // Sort by name
        Collections.sort(regions, new Comparator<Region>() {
            @Override
            public int compare(Region o1, Region o2) {
                return o1.getName().compareTo(o2.getName());
            }
        });

        return regions;
    }

    public List<Zone> getZones(String projectId, String region) throws IOException {
        List<Zone> zones = compute
                .zones()
                .list(projectId)
                .execute()
                .getItems();
        if (zones == null) {
            zones = new ArrayList<Zone>();
        }

        // Only zones for the region
        Iterator it = zones.iterator();
        while (it.hasNext()) {
            Zone o = (Zone) it.next();
            if (!o.getRegion().equals(region)) {
                it.remove();
            }
        }

        // Sort by name
        Collections.sort(zones, new Comparator<Zone>() {
            @Override
            public int compare(Zone o1, Zone o2) {
                return o1.getName().compareTo(o2.getName());
            }
        });

        return zones;
    }

    public List<MachineType> getMachineTypes(String projectId, String zone) throws IOException {
        zone = zoneFromSelfLink(zone);
        List<MachineType> machineTypes = compute
                .machineTypes()
                .list(projectId, zone)
                .execute()
                .getItems();
        if (machineTypes == null) {
            machineTypes = new ArrayList<MachineType>();
        }

        // No deprecated items
        Iterator it = machineTypes.iterator();
        while (it.hasNext()) {
            MachineType o = (MachineType) it.next();
            if (o.getDeprecated() != null && o.getDeprecated().getState().equalsIgnoreCase("DEPRECATED")) {
                it.remove();
            }
        }

        // Sort by name
        Collections.sort(machineTypes, new Comparator<MachineType>() {
            @Override
            public int compare(MachineType o1, MachineType o2) {
                return o1.getName().compareTo(o2.getName());
            }
        });

        return machineTypes;
    }

    public List<String> cpuPlatforms(String projectId, String zone) throws IOException {
        List<String> cpuPlatforms = new ArrayList<String>();
        zone = zoneFromSelfLink(zone);
        Zone zoneObject = compute.zones()
                .get(projectId,zone)
                .execute();
        if (zoneObject == null) {
            return cpuPlatforms;
        }
        return zoneObject.getAvailableCpuPlatforms();
    }

    public List<DiskType> getDiskTypes(String projectId, String zone) throws IOException {
        zone = zoneFromSelfLink(zone);
        List<DiskType> diskTypes = compute
                .diskTypes()
                .list(projectId, zone)
                .execute()
                .getItems();
        if (diskTypes == null) {
            diskTypes = new ArrayList<DiskType>();
        }

        // No deprecated items
        Iterator it = diskTypes.iterator();
        while (it.hasNext()) {
            DiskType o = (DiskType) it.next();
            if (o.getDeprecated() != null && o.getDeprecated().getState().equalsIgnoreCase("DEPRECATED")) {
                it.remove();
            }
        }

        // Sort by name
        Collections.sort(diskTypes, new Comparator<DiskType>() {
            @Override
            public int compare(DiskType o1, DiskType o2) {
                return o1.getName().compareTo(o2.getName());
            }
        });

        return diskTypes;
    }

    public List<DiskType> getBootDiskTypes(String projectId, String zone) throws IOException {
        zone = zoneFromSelfLink(zone);
        List<DiskType> diskTypes = this.getDiskTypes(projectId, zone);

        // No local disks
        Iterator it = diskTypes.iterator();
        while (it.hasNext()) {
            DiskType o = (DiskType) it.next();
            if (o.getName().startsWith("local-")) {
                it.remove();
            }
        }
        return diskTypes;
    }

    public List<Image> getImages(String projectId) throws IOException {
        List<Image> images = compute
                .images()
                .list(projectId)
                .execute()
                .getItems();
        if (images == null) {
            images = new ArrayList<Image>();
        }

        // No deprecated items
        Iterator it = images.iterator();
        while (it.hasNext()) {
            Image o = (Image) it.next();
            if (o.getDeprecated() != null && o.getDeprecated().getState().equalsIgnoreCase("DEPRECATED")) {
                it.remove();
            }
        }

        // Sort by name
        Collections.sort(images, new Comparator<Image>() {
            @Override
            public int compare(Image o1, Image o2) {
                return o1.getName().compareTo(o2.getName());
            }
        });

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
            // No deprecated items
            Iterator it = acceleratorTypes.iterator();
            while (it.hasNext()) {
                AcceleratorType o = (AcceleratorType) it.next();
                if (o.getDeprecated() != null && o.getDeprecated().getState().equalsIgnoreCase("DEPRECATED")) {
                    it.remove();
                }
            }

            // Sort by name
            Collections.sort(acceleratorTypes, new Comparator<AcceleratorType>() {
                @Override
                public int compare(AcceleratorType o1, AcceleratorType o2) {
                    return o1.getName().compareTo(o2.getName());
                }
            });
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
        if (subnetworks == null) {
            subnetworks = new ArrayList<Subnetwork>();
        }

        // Only subnetworks in the parent network
        Iterator it = subnetworks.iterator();
        while (it.hasNext()) {
            Subnetwork o = (Subnetwork) it.next();
            if (!o.getNetwork().equals(networkSelfLink)) {
                it.remove();
            }
        }

        // Sort by name
        Collections.sort(subnetworks, new Comparator<Subnetwork>() {
            @Override
            public int compare(Subnetwork o1, Subnetwork o2) {
                return o1.getName().compareTo(o2.getName());
            }
        });

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
        return waitForOperationCompletion(projectId, op.getName(), op.getZone(), 60 * 1000);
    }

    /**
     * Blocks until an existing operation completes.
     *
     * @param projectId
     * @param operationId
     * @param zone
     * @param timeout
     * @return
     * @throws IOException
     * @throws InterruptedException
     */
    public Operation.Error waitForOperationCompletion(String projectId, String operationId, String zone, long timeout)
            throws IOException, InterruptedException {
        if (Strings.isNullOrEmpty(operationId)) {
            throw new IllegalArgumentException("Operation ID can not be null");
        }
        if (Strings.isNullOrEmpty(zone)) {
            throw new IllegalArgumentException("Zone can not be null");
        }
        if (zone != null) {
            String[] bits = zone.split("/");
            zone = bits[bits.length - 1];
        }

        Operation operation = compute.zoneOperations().get(projectId, zone, operationId).execute();
        long start = System.currentTimeMillis();
        final long POLL_INTERVAL = 5 * 1000;

        String status = operation.getStatus();
        while (!status.equals("DONE")) {
            Thread.sleep(POLL_INTERVAL);
            long elapsed = System.currentTimeMillis() - start;
            if (elapsed >= timeout) {
                throw new InterruptedException("Timed out waiting for operation to complete");
            }
            LOGGER.log(Level.FINE, "Waiting for operation " + operationId + " to complete..");
            if (zone != null) {
                Compute.ZoneOperations.Get get = compute.zoneOperations().get(projectId, zone, operationId);
                operation = get.execute();
            } else {
                Compute.GlobalOperations.Get get = compute.globalOperations().get(projectId, operationId);
                operation = get.execute();
            }
            if (operation != null) {
                status = operation.getStatus();
            }
        }
        return operation.getError();
    }
}