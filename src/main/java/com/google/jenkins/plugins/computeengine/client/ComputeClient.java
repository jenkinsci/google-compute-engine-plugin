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

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.compute.Compute;

import com.google.api.services.compute.model.AcceleratorType;
import com.google.api.services.compute.model.AttachedDisk;
import com.google.api.services.compute.model.DiskType;
import com.google.api.services.compute.model.Image;
import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.InstanceTemplate;
import com.google.api.services.compute.model.InstancesScopedList;
import com.google.api.services.compute.model.MachineType;
import com.google.api.services.compute.model.Metadata;
import com.google.api.services.compute.model.Network;
import com.google.api.services.compute.model.Operation;
import com.google.api.services.compute.model.Region;
import com.google.api.services.compute.model.Snapshot;
import com.google.api.services.compute.model.SnapshotList;
import com.google.api.services.compute.model.Subnetwork;
import com.google.api.services.compute.model.Zone;
import com.google.common.base.Strings;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.lang.StringUtils;

/**
 * Client for communicating with the Google Compute API
 *
 * @see <a href="https://cloud.google.com/compute/">Cloud Engine</a>
 */
public class ComputeClient {
    private Compute compute;

    private static final Logger LOGGER = Logger.getLogger(ComputeClient.class.getName());
    private static final long SNAPSHOT_TIMEOUT_MILLISECONDS = TimeUnit.MINUTES.toMillis(10);

    public static String nameFromSelfLink(String selfLink) { 
        return selfLink.substring(selfLink.lastIndexOf("/") + 1, selfLink.length());
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
        zone = nameFromSelfLink(zone);
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
        zone = nameFromSelfLink(zone);
        Zone zoneObject = compute.zones()
                .get(projectId, zone)
                .execute();
        if (zoneObject == null) {
            return cpuPlatforms;
        }
        return zoneObject.getAvailableCpuPlatforms();
    }

    public List<DiskType> getDiskTypes(String projectId, String zone) throws IOException {
        zone = nameFromSelfLink(zone);
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
        zone = nameFromSelfLink(zone);
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
        zone = nameFromSelfLink(zone);

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
        region = nameFromSelfLink(region);
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

    public Operation insertInstance(String projectId, String template, Instance instance) throws IOException {
        final Compute.Instances.Insert insert = compute.instances().insert(projectId, instance.getZone(), instance);
        if (!Strings.isNullOrEmpty(template)) {
            insert.setSourceInstanceTemplate(template);
        }
        return insert.execute();
    }

    public Operation terminateInstance(String projectId, String zone, String instanceId) throws IOException {
        zone = nameFromSelfLink(zone);
        return compute.instances().delete(projectId, zone, instanceId).execute();
    }

    public Operation terminateInstanceWithStatus(String projectId, String zone, String instanceId, String desiredStatus) throws IOException, InterruptedException {
        zone = nameFromSelfLink(zone);
        Instance i = getInstance(projectId, zone, instanceId);
        if (i.getStatus().equals(desiredStatus)) {
            return compute.instances().delete(projectId, zone, instanceId).execute();
        }
        return null;
    }

    public Instance getInstance(String projectId, String zone, String instanceId) throws IOException {
        zone = nameFromSelfLink(zone);
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

    public InstanceTemplate getTemplate(String projectId, String templateName) throws IOException {
        return compute.instanceTemplates()
                .get(projectId, templateName)
                .execute();
    }

    public void insertTemplate(String projectId, InstanceTemplate instanceTemplate) throws IOException {
        compute.instanceTemplates()
                .insert(projectId, instanceTemplate)
                .execute();
    }

    public void deleteTemplate(String projectId, String templateName) throws IOException {
        compute.instanceTemplates()
                .delete(projectId, templateName)
                .execute();
    }

    public List<InstanceTemplate> getTemplates(String projectId) throws IOException {
        List<InstanceTemplate> instanceTemplates = compute.instanceTemplates()
                .list(projectId)
                .execute()
                .getItems();
        if (instanceTemplates == null) {
            instanceTemplates = Collections.emptyList();
        }

        // Sort by name
        instanceTemplates.sort(Comparator.comparing(InstanceTemplate::getName));

        return instanceTemplates;
    }

    /**
     * Creates persistent disk snapshot for Compute Engine instance.
     * This method blocks until the operation completes.
     *
     * @param projectId Google cloud project id (e.g. my-project).
     * @param zone Instance's zone.
     * @param instanceId Name of the instance whose disks to take a snapshot of.
     *
     * @throws IOException If an error occured in snapshot creation.
     * @throws InterruptedException If snapshot creation is interrupted.
     */
    public void createSnapshot(String projectId, String zone, String instanceId) throws IOException, InterruptedException {
        try {
            zone = nameFromSelfLink(zone);
            Instance instance = compute.instances().get(projectId, zone, instanceId).execute();

            //TODO: JENKINS-56113 parallelize snapshot creation
            for (AttachedDisk disk : instance.getDisks()) {
                    String diskId = nameFromSelfLink(disk.getSource());
                    createSnapshotForDisk(projectId, zone, diskId);
            }
        } catch (InterruptedException ie) {
            // catching InterruptedException here because calling function also can throw InterruptedException from trying to terminate node
            LOGGER.log(Level.WARNING,"Error in creating snapshot.", ie);
            throw ie;
        } catch (IOException ioe) {
            LOGGER.log(Level.WARNING,"Interruption in creating snapshot.", ioe);
            throw ioe;
        }
    }

    /**
     * Given a disk's name, create a snapshot for said disk.
     *
     * @param projectId Google cloud project id.
     * @param zone Zone of disk.
     * @param diskId Name of disk to create a snapshot for.
     *
     * @throws IOException If an error occured in snapshot creation.
     * @throws InterruptedException If snapshot creation is interrupted.
     */
    public void createSnapshotForDisk(String projectId, String zone, String diskId) throws IOException, InterruptedException {
        Snapshot snapshot = new Snapshot();
        snapshot.setName(diskId);

        Operation op = compute.disks().createSnapshot(projectId, zone, diskId, snapshot).execute();
        // poll for result
        waitForOperationCompletion(projectId, op.getName(), op.getZone(), SNAPSHOT_TIMEOUT_MILLISECONDS);
    }

    /**
     * Deletes persistent disk snapshot. Does not block.
     *
     * @param projectId Google cloud project id.
     * @param snapshotName Name of the snapshot to be deleted.
     * @throws IOException If an error occurred in deleting the snapshot.
     */
    public void deleteSnapshot(String projectId, String snapshotName) throws IOException {
        compute.snapshots().delete(projectId, snapshotName).execute();
    }

    /**
     * Returns snapshot with name snapshotName
     *
     * @param projectId Google cloud project id.
     * @param snapshotName Name of the snapshot to get.
     * @return Snapshot object with given snapshotName. Null if not found.
     * @throws IOException If an error occurred in retrieving the snapshot.
     */
    public Snapshot getSnapshot(String projectId, String snapshotName) throws IOException {
        SnapshotList response;
        Compute.Snapshots.List request = compute.snapshots().list(projectId);

        do {
            response = request.execute();
            if (response.getItems() == null) {
                continue;
            }
            for (Snapshot snapshot : response.getItems()) {
                if (StringUtils.equals(snapshotName, snapshot.getName()))
                    return snapshot;
            }
            request.setPageToken(response.getNextPageToken());
        } while (response.getNextPageToken() != null);

        return null;
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
        zone = nameFromSelfLink(zone);
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
