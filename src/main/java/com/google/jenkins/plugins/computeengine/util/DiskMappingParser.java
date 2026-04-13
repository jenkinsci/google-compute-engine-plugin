/*
 * Copyright 2026 CloudBees, Inc.
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

package com.google.jenkins.plugins.computeengine.util;

import com.google.api.services.compute.model.AttachedDisk;
import com.google.api.services.compute.model.AttachedDiskInitializeParams;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parses disk mapping strings in GCP key=value format into {@link AttachedDisk} objects.
 *
 * <p>Supports two modes in the same textarea. Both modes support {@code device-name},
 * {@code mode}, {@code interface}, and {@code auto-delete}.
 * <ul>
 *   <li><b>Create disk</b>
 *       (<a href="https://docs.cloud.google.com/sdk/gcloud/reference/compute/instances/create#--create-disk">{@code --create-disk}</a>
 *       semantics) — when the line contains {@code source-snapshot}, {@code size}, or {@code type}.
 *       A new disk is created and attached.</li>
 *   <li><b>Attach existing disk</b>
 *       (<a href="https://docs.cloud.google.com/sdk/gcloud/reference/compute/instances/create#--disk">{@code --disk}</a>
 *       semantics) — when the line contains only {@code name} (without any create-disk keys).
 *       An existing disk is attached by reference.</li>
 * </ul>
 */
public class DiskMappingParser {

    private DiskMappingParser() {}

    /** Keys that indicate a create-disk line (as opposed to attach-existing). */
    private static final Set<String> CREATE_DISK_KEYS = Set.of("source-snapshot", "size", "type", "description");

    /**
     * Parses a multi-line disk mapping string into a list of {@link AttachedDisk} objects.
     * Each non-blank line is parsed as a separate disk specification.
     *
     * @param diskMapping the disk mapping string (one disk per line), or null/empty
     * @return a list of configured {@link AttachedDisk} objects, empty if input is null/blank
     * @throws IllegalArgumentException if a line fails validation
     */
    public static List<AttachedDisk> parse(@Nullable String diskMapping) {
        if (diskMapping == null || diskMapping.trim().isEmpty()) {
            return List.of();
        }

        var disks = new ArrayList<AttachedDisk>();
        for (var line : diskMapping.split("\n")) {
            if (line.trim().isEmpty()) {
                continue;
            }
            disks.add(parseLine(line.trim()));
        }
        return disks;
    }

    private static Map<String, String> parseKeyValues(String line) {
        var map = new LinkedHashMap<String, String>();
        for (var pair : line.split(",")) {
            var kv = pair.trim().split("=", 2);
            if (kv.length == 2) {
                map.put(kv[0].trim(), kv[1].trim());
            }
        }
        return map;
    }

    private static boolean isCreateDisk(Map<String, String> kv) {
        return kv.keySet().stream().anyMatch(CREATE_DISK_KEYS::contains);
    }

    private static AttachedDisk parseLine(String line) {
        var kv = parseKeyValues(line);

        if (isCreateDisk(kv)) {
            return buildCreateDisk(kv);
        }
        return buildAttachExistingDisk(kv);
    }

    /**
     * Builds an {@link AttachedDisk} that creates a new disk.
     *
     * <p>Mode-specific keys:
     * <ul>
     *   <li>{@code source-snapshot} — snapshot name, relative path, or full URL</li>
     *   <li>{@code size} — disk size in GB</li>
     *   <li>{@code type} — disk type (e.g. {@code pd-ssd})</li>
     *   <li>{@code name} — name for the new disk</li>
     *   <li>{@code description} — optional description</li>
     * </ul>
     *
     * <p>Also supports the common keys ({@code device-name}, {@code mode}, {@code interface},
     * {@code auto-delete}) described in the {@linkplain DiskMappingParser class javadoc}.
     *
     * <p>At least one of {@code source-snapshot} or {@code size} must be specified.
     *
     * @see <a href="https://docs.cloud.google.com/sdk/gcloud/reference/compute/instances/create#--create-disk">
     *     gcloud compute instances create --create-disk</a>
     */
    private static AttachedDisk buildCreateDisk(Map<String, String> kv) {
        var sourceSnapshot = kv.get("source-snapshot");
        var diskName = kv.get("name");
        var deviceName = kv.get("device-name");
        var description = kv.get("description");
        var sizeStr = kv.get("size");
        var diskType = kv.get("type");
        var modeStr = kv.get("mode");
        var diskInterface = kv.get("interface");
        var autoDeleteStr = kv.get("auto-delete");

        boolean hasSource = sourceSnapshot != null && !sourceSnapshot.isEmpty();
        Long sizeGb = sizeStr != null ? Long.parseLong(sizeStr) : null;

        if (!hasSource && sizeGb == null) {
            throw new IllegalArgumentException(
                    "Create-disk mapping must include at least one of: source-snapshot or size");
        }

        var params = new AttachedDiskInitializeParams();
        if (hasSource) {
            params.setSourceSnapshot(sourceSnapshot);
        }
        if (diskName != null && !diskName.isEmpty()) {
            params.setDiskName(diskName);
        }
        if (description != null && !description.isEmpty()) {
            params.setDescription(description);
        }
        if (sizeGb != null) {
            params.setDiskSizeGb(sizeGb);
        }
        if (diskType != null && !diskType.isEmpty()) {
            params.setDiskType(diskType);
        }

        var disk = new AttachedDisk();
        disk.setBoot(false);
        disk.setAutoDelete(autoDeleteStr == null || "yes".equalsIgnoreCase(autoDeleteStr));
        disk.setInitializeParams(params);
        if (deviceName != null && !deviceName.isEmpty()) {
            disk.setDeviceName(deviceName);
        }
        if (modeStr != null && !modeStr.isEmpty()) {
            disk.setMode(normalizeMode(modeStr));
        }
        if (diskInterface != null && !diskInterface.isEmpty()) {
            disk.setInterface(diskInterface);
        }
        return disk;
    }

    /**
     * Builds an {@link AttachedDisk} that attaches an existing disk.
     *
     * <p>Mode-specific keys:
     * <ul>
     *   <li>{@code name} — existing disk name, relative path, or full URL (required)</li>
     * </ul>
     *
     * <p>Also supports the common keys ({@code device-name}, {@code mode}, {@code interface},
     * {@code auto-delete}) described in the {@linkplain DiskMappingParser class javadoc}.
     *
     * @see <a href="https://docs.cloud.google.com/sdk/gcloud/reference/compute/instances/create#--disk">
     *     gcloud compute instances create --disk</a>
     */
    private static AttachedDisk buildAttachExistingDisk(Map<String, String> kv) {
        var name = kv.get("name");
        var deviceName = kv.get("device-name");
        var modeStr = kv.get("mode");
        var diskInterface = kv.get("interface");
        var autoDeleteStr = kv.get("auto-delete");

        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Attach-disk mapping must include 'name' referring to an existing disk");
        }

        var disk = new AttachedDisk();
        disk.setSource(name);
        disk.setBoot(false);
        disk.setAutoDelete(autoDeleteStr == null || "yes".equalsIgnoreCase(autoDeleteStr));
        if (deviceName != null && !deviceName.isEmpty()) {
            disk.setDeviceName(deviceName);
        }
        if (modeStr != null && !modeStr.isEmpty()) {
            disk.setMode(normalizeMode(modeStr));
        }
        if (diskInterface != null && !diskInterface.isEmpty()) {
            disk.setInterface(diskInterface);
        }
        return disk;
    }

    /**
     * Normalizes short mode values ({@code ro}, {@code rw}) to GCP API constants
     * ({@code READ_ONLY}, {@code READ_WRITE}).
     */
    private static String normalizeMode(String mode) {
        return switch (mode.toLowerCase()) {
            case "ro" -> "READ_ONLY";
            case "rw" -> "READ_WRITE";
            default -> mode;
        };
    }

    /**
     * Expands short disk type names (e.g. {@code pd-ssd}) to zone-qualified resource paths
     * ({@code zones/{zone}/diskTypes/pd-ssd}). Full URLs and relative paths that already
     * contain a {@code /} are returned unchanged.
     *
     * @param diskType the disk type value from the mapping, or null
     * @param zone the zone name (e.g. {@code us-east1-b})
     * @return the qualified disk type, or null if input is null
     */
    @Nullable
    public static String normalizeDiskType(@Nullable String diskType, String zone) {
        if (diskType == null || diskType.contains("/")) {
            return diskType;
        }
        return "zones/" + zone + "/diskTypes/" + diskType;
    }

    /**
     * Expands short disk names to project- and zone-qualified resource paths
     * ({@code projects/{project}/zones/{zone}/disks/{name}}). Full URLs and relative paths
     * that already contain a {@code /} are returned unchanged.
     *
     * @param diskSource the disk name or path from the mapping, or null
     * @param project the project ID (e.g. {@code my-project})
     * @param zone the zone name (e.g. {@code us-east1-b})
     * @return the qualified disk source, or null if input is null
     */
    @Nullable
    public static String normalizeDiskSource(@Nullable String diskSource, String project, String zone) {
        if (diskSource == null || diskSource.contains("/")) {
            return diskSource;
        }
        return "projects/" + project + "/zones/" + zone + "/disks/" + diskSource;
    }

    /**
     * Expands short snapshot names to project-qualified resource paths
     * ({@code projects/{project}/global/snapshots/{name}}). Full URLs and relative paths
     * that already contain a {@code /} are returned unchanged.
     *
     * @param snapshotSource the snapshot name or path from the mapping, or null
     * @param project the project ID (e.g. {@code my-project})
     * @return the qualified snapshot source, or null if input is null
     */
    @Nullable
    public static String normalizeSnapshotSource(@Nullable String snapshotSource, String project) {
        if (snapshotSource == null || snapshotSource.contains("/")) {
            return snapshotSource;
        }
        return "projects/" + project + "/global/snapshots/" + snapshotSource;
    }
}
