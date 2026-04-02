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

package com.google.jenkins.plugins.computeengine.integration;

import static com.google.jenkins.plugins.computeengine.integration.ITUtil.NULL_TEMPLATE;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.NUM_EXECUTORS;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.PROJECT_ID;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.TEST_TIMEOUT_MULTIPLIER;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.ZONE;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.format;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.getLabel;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initClient;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initCloud;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initCredentials;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.instanceConfigurationBuilder;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.teardownResources;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.Disk;
import com.google.api.services.compute.model.Operation;
import com.google.api.services.compute.model.Snapshot;
import com.google.cloud.graphite.platforms.plugin.client.ComputeClient;
import com.google.common.collect.ImmutableList;
import com.google.jenkins.plugins.computeengine.ComputeEngineCloud;
import hudson.model.Node;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.awaitility.Awaitility;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.support.steps.input.InputAction;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Integration tests for the disk mapping feature, covering all three disk attachment modes:
 * <ol>
 *   <li>Create disks from snapshots ({@code --create-disk} with {@code source-snapshot})</li>
 *   <li>Create blank disks ({@code --create-disk} with {@code size} only)</li>
 *   <li>Attach existing disks ({@code --disk} semantics with {@code name})</li>
 * </ol>
 *
 * <p><b>Limitation:</b> Snapshot-based tests create snapshots from blank disks, so the startup
 * script formats them before writing marker files. This verifies that the disk mapping plumbing
 * works (parsing, GCP API call, device attachment, auto-delete) but not that pre-existing
 * snapshot data is used by the build &mdash; which would require a prep instance to write data before
 * snapshotting.
 */
public class ComputeEngineCloudDiskMappingIT {
    private static final Logger log = Logger.getLogger(ComputeEngineCloudDiskMappingIT.class.getName());

    private static final String GCE_LABEL = "gce";

    private static final String TEST_SNAPSHOT_A = "it-disk-mapping-snap-a";
    private static final String TEST_SNAPSHOT_B = "it-disk-mapping-snap-b";
    private static final String SNAPSHOT_SOURCE_DISK = "it-disk-mapping-snap-source";

    private static final String TEST_EXISTING_DISK = "it-disk-mapping-existing";

    private static final String INPUT_MESSAGE = "disk-mapping-assertions-checkpoint";

    /** Timeout in seconds for waiting on GCP resource cleanup (instance/disk deletion). */
    private static final int CLEANUP_TIMEOUT_SECONDS = 300;

    @ClassRule
    public static Timeout timeout = new Timeout(25L * TEST_TIMEOUT_MULTIPLIER, TimeUnit.MINUTES);

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @ClassRule
    public static BuildWatcher bw = new BuildWatcher();

    private ComputeClient client;
    private ComputeEngineCloud cloud;
    private Compute compute;
    private final Map<String, String> label = getLabel(ComputeEngineCloudDiskMappingIT.class);
    private final List<String> snapshotsToDelete = new ArrayList<>();
    private final List<String> disksToDelete = new ArrayList<>();

    @Before
    public void init() throws Exception {
        log.info("init");
        initCredentials(j);
        cloud = initCloud(j);
        client = initClient(j, label, log);
        compute = cloud.getClientV2().getCompute();
    }

    @After
    public void teardown() throws IOException {
        log.info("teardown");
        teardownResources(client, label, log);
        snapshotsToDelete.forEach(this::deleteTestSnapshot);
        disksToDelete.forEach(this::deleteTestDisk);
    }

    /**
     * Verifies that multiple disks (2 during the test) created from snapshots are attached to a one-shot instance,
     * mountable via startup script, readable from a build step, and auto-deleted with the instance.
     */
    @Test
    public void testSnapshotDisksAttachedMountedAndAutoDeleted() throws Exception {
        createTestSnapshots();

        var diskMapping = String.format(
                "source-snapshot=%s,auto-delete=yes\nsource-snapshot=%s,auto-delete=yes",
                snapshotSelfLink(TEST_SNAPSHOT_A), snapshotSelfLink(TEST_SNAPSHOT_B));

        var startupScript = """
                #!/bin/bash
                mkfs.ext4 -F /dev/sdb
                mkdir -p /mnt/disk-a
                mount /dev/sdb /mnt/disk-a
                echo 'snapshot-disk-a' > /mnt/disk-a/marker.txt
                mkfs.ext4 -F /dev/sdc
                mkdir -p /mnt/disk-b
                mount /dev/sdc /mnt/disk-b
                echo 'snapshot-disk-b' > /mnt/disk-b/marker.txt""";

        cloud.setConfigurations(ImmutableList.of(instanceConfigurationBuilder()
                .numExecutorsStr(NUM_EXECUTORS)
                .labels(GCE_LABEL)
                .oneShot(true)
                .createSnapshot(false)
                .template(NULL_TEMPLATE)
                .googleLabels(label)
                .diskMapping(diskMapping)
                .startupScript(startupScript)
                .build()));

        var p = j.createProject(WorkflowJob.class, "snapshot-disk-test");
        p.setDefinition(new CpsFlowDefinition(
                "node('" + GCE_LABEL + "') {\n"
                        + "  sh 'cat /mnt/disk-a/marker.txt'\n"
                        + "  sh 'cat /mnt/disk-b/marker.txt'\n"
                        + "  input message: '" + INPUT_MESSAGE + "'\n"
                        + "}",
                true));

        var build = p.scheduleBuild2(0).waitForStart();
        j.waitForMessage(INPUT_MESSAGE, build);
        var buildLog = JenkinsRule.getLog(build);

        var workerNodeName = extractWorkerNodeName(buildLog);
        log.info("Build paused on node: " + workerNodeName);

        j.assertLogContains("snapshot-disk-a", build);
        j.assertLogContains("snapshot-disk-b", build);

        var instance = client.getInstance(PROJECT_ID, ZONE, workerNodeName);
        assertThat("Instance should exist while build is paused", instance, is(notNullValue()));
        assertThat("Instance should have 3 disks (boot + 2 snapshot)", instance.getDisks(), hasSize(3));
        assertThat("First disk should be boot", instance.getDisks().get(0).getBoot(), is(true));

        var snapshotDiskNames = new ArrayList<String>();
        for (int i = 1; i <= 2; i++) {
            var diskSource = instance.getDisks().get(i).getSource();
            assertThat("Snapshot disk " + i + " should have a source", diskSource, is(notNullValue()));
            var diskName = diskSource.substring(diskSource.lastIndexOf("/") + 1);
            snapshotDiskNames.add(diskName);
            log.info("Snapshot disk " + i + " name: " + diskName);
        }

        build.getAction(InputAction.class).getExecutions().get(0).proceed(null);
        j.waitForCompletion(build);
        j.assertBuildStatusSuccess(build);

        waitForOneShotCleanup(workerNodeName);

        for (var diskName : snapshotDiskNames) {
            waitForDiskDeletion(diskName);
            log.info("Snapshot disk auto-deleted: " + diskName);
        }
    }

    /**
     * Verifies that a blank disk (no snapshot, no existing disk) is created from just
     * {@code size} and {@code device-name}, formatted and mounted via startup script,
     * readable from a build step, and auto-deleted with the instance.
     */
    @Test
    public void testBlankDiskCreatedAndMounted() throws Exception {
        var diskMapping = "size=10,type=pd-standard,device-name=scratch,auto-delete=yes";

        var startupScript = """
                #!/bin/bash
                mkfs.ext4 -F /dev/disk/by-id/google-scratch
                mkdir -p /mnt/scratch
                mount /dev/disk/by-id/google-scratch /mnt/scratch
                echo 'blank-disk-ok' > /mnt/scratch/marker.txt""";

        cloud.setConfigurations(ImmutableList.of(instanceConfigurationBuilder()
                .numExecutorsStr(NUM_EXECUTORS)
                .labels(GCE_LABEL)
                .oneShot(true)
                .createSnapshot(false)
                .template(NULL_TEMPLATE)
                .googleLabels(label)
                .diskMapping(diskMapping)
                .startupScript(startupScript)
                .build()));

        var p = j.createProject(WorkflowJob.class, "blank-disk-test");
        p.setDefinition(new CpsFlowDefinition(
                "node('" + GCE_LABEL + "') {\n"
                        + "  sh 'cat /mnt/scratch/marker.txt'\n"
                        + "  input message: '" + INPUT_MESSAGE + "'\n"
                        + "}",
                true));

        var build = p.scheduleBuild2(0).waitForStart();
        j.waitForMessage(INPUT_MESSAGE, build);
        var buildLog = JenkinsRule.getLog(build);

        var workerNodeName = extractWorkerNodeName(buildLog);
        log.info("Build paused on node: " + workerNodeName);

        j.assertLogContains("blank-disk-ok", build);

        var instance = client.getInstance(PROJECT_ID, ZONE, workerNodeName);
        assertThat("Instance should exist while build is paused", instance, is(notNullValue()));
        assertThat("Instance should have 2 disks (boot + blank)", instance.getDisks(), hasSize(2));

        var blankDiskSource = instance.getDisks().get(1).getSource();
        assertThat("Blank disk should have a source", blankDiskSource, is(notNullValue()));
        var blankDiskName = blankDiskSource.substring(blankDiskSource.lastIndexOf("/") + 1);
        log.info("Blank disk name: " + blankDiskName);

        build.getAction(InputAction.class).getExecutions().get(0).proceed(null);
        j.waitForCompletion(build);
        j.assertBuildStatusSuccess(build);

        waitForOneShotCleanup(workerNodeName);

        waitForDiskDeletion(blankDiskName);
        log.info("Blank disk auto-deleted: " + blankDiskName);
    }

    /**
     * Verifies that an existing disk is attached to a one-shot instance using {@code --disk}
     * semantics ({@code name=...}), mountable via startup script, and readable from a build step.
     */
    @Test
    public void testAttachExistingDiskMountedAndReadable() throws Exception {
        createTestExistingDisk();

        var diskMapping = String.format("name=%s,device-name=data,auto-delete=no", existingDiskSelfLink());

        var startupScript = """
                #!/bin/bash
                mkfs.ext4 -F /dev/disk/by-id/google-data
                mkdir -p /mnt/data
                mount /dev/disk/by-id/google-data /mnt/data
                echo 'existing-disk-ok' > /mnt/data/marker.txt""";

        cloud.setConfigurations(ImmutableList.of(instanceConfigurationBuilder()
                .numExecutorsStr(NUM_EXECUTORS)
                .labels(GCE_LABEL)
                .oneShot(true)
                .createSnapshot(false)
                .template(NULL_TEMPLATE)
                .googleLabels(label)
                .diskMapping(diskMapping)
                .startupScript(startupScript)
                .build()));

        var p = j.createProject(WorkflowJob.class, "attach-existing-disk-test");
        p.setDefinition(new CpsFlowDefinition(
                "node('" + GCE_LABEL + "') {\n"
                        + "  sh 'cat /mnt/data/marker.txt'\n"
                        + "  input message: '" + INPUT_MESSAGE + "'\n"
                        + "}",
                true));

        var build = p.scheduleBuild2(0).waitForStart();
        j.waitForMessage(INPUT_MESSAGE, build);
        var buildLog = JenkinsRule.getLog(build);

        var workerNodeName = extractWorkerNodeName(buildLog);
        log.info("Build paused on node: " + workerNodeName);

        j.assertLogContains("existing-disk-ok", build);

        var instance = client.getInstance(PROJECT_ID, ZONE, workerNodeName);
        assertThat("Instance should exist while build is paused", instance, is(notNullValue()));
        assertThat("Instance should have 2 disks (boot + existing)", instance.getDisks(), hasSize(2));

        build.getAction(InputAction.class).getExecutions().get(0).proceed(null);
        j.waitForCompletion(build);
        j.assertBuildStatusSuccess(build);

        waitForOneShotCleanup(workerNodeName);

        // Verify the existing disk was NOT deleted (auto-delete=no)
        var disk = compute.disks().get(PROJECT_ID, ZONE, TEST_EXISTING_DISK).execute();
        assertThat("Existing disk should survive instance deletion", disk, is(notNullValue()));
        log.info("Existing disk survived instance deletion: " + TEST_EXISTING_DISK);
    }

    private String extractWorkerNodeName(String buildLog) {
        return j.jenkins.getNodes().stream()
                .map(Node::getNodeName)
                .filter(name -> buildLog.contains("Running on " + name))
                .findFirst()
                .orElseThrow(
                        () -> new AssertionError("Build log does not contain 'Running on <agent>'. Log:\n" + buildLog));
    }

    private void waitForOneShotCleanup(String workerNodeName) {
        Awaitility.await()
                .timeout(CLEANUP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .pollInterval(10, TimeUnit.SECONDS)
                .until(() -> j.jenkins.getNode(workerNodeName) == null);
        log.info("One-shot node removed from Jenkins: " + workerNodeName);

        Awaitility.await()
                .timeout(CLEANUP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .pollInterval(10, TimeUnit.SECONDS)
                .until(() -> {
                    try {
                        client.getInstance(PROJECT_ID, ZONE, workerNodeName);
                        return false;
                    } catch (IOException e) {
                        return true;
                    }
                });
        log.info("GCP instance deleted: " + workerNodeName);
    }

    private void waitForDiskDeletion(String diskName) {
        Awaitility.await()
                .timeout(CLEANUP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .pollInterval(10, TimeUnit.SECONDS)
                .until(() -> {
                    try {
                        compute.disks().get(PROJECT_ID, ZONE, diskName).execute();
                        return false;
                    } catch (IOException e) {
                        return true;
                    }
                });
    }

    private void createTestSnapshots() throws Exception {
        // Clean up stale resources from previous failed runs
        deleteTestSnapshot(TEST_SNAPSHOT_A);
        deleteTestSnapshot(TEST_SNAPSHOT_B);
        deleteTestDisk(SNAPSHOT_SOURCE_DISK);

        log.info("Creating source disk " + SNAPSHOT_SOURCE_DISK);
        var disk = new Disk();
        disk.setName(SNAPSHOT_SOURCE_DISK);
        disk.setSizeGb(10L);

        var diskOp = compute.disks().insert(PROJECT_ID, ZONE, disk).execute();
        disksToDelete.add(SNAPSHOT_SOURCE_DISK);
        waitForZoneOperation(diskOp);
        log.info("Source disk created");

        for (var snapshotName : new String[] {TEST_SNAPSHOT_A, TEST_SNAPSHOT_B}) {
            log.info("Creating snapshot " + snapshotName);
            var snapshot = new Snapshot();
            snapshot.setName(snapshotName);
            var snapOp = compute.disks()
                    .createSnapshot(PROJECT_ID, ZONE, SNAPSHOT_SOURCE_DISK, snapshot)
                    .execute();
            snapshotsToDelete.add(snapshotName);
            waitForZoneOperation(snapOp);
            log.info("Snapshot created: " + snapshotName);
        }

        log.info("Deleting source disk " + SNAPSHOT_SOURCE_DISK);
        var deleteOp =
                compute.disks().delete(PROJECT_ID, ZONE, SNAPSHOT_SOURCE_DISK).execute();
        waitForZoneOperation(deleteOp);
        disksToDelete.remove(SNAPSHOT_SOURCE_DISK);
        log.info("Source disk deleted");
    }

    private void createTestExistingDisk() throws Exception {
        // Clean up stale resource from previous failed runs
        deleteTestDisk(TEST_EXISTING_DISK);

        log.info("Creating existing disk " + TEST_EXISTING_DISK);
        var disk = new Disk();
        disk.setName(TEST_EXISTING_DISK);
        disk.setSizeGb(10L);

        var diskOp = compute.disks().insert(PROJECT_ID, ZONE, disk).execute();
        disksToDelete.add(TEST_EXISTING_DISK);
        waitForZoneOperation(diskOp);
        log.info("Existing disk created: " + TEST_EXISTING_DISK);
    }

    private void deleteTestSnapshot(String snapshotName) {
        try {
            compute.snapshots().get(PROJECT_ID, snapshotName).execute();
        } catch (IOException e) {
            return;
        }
        try {
            log.info("Deleting snapshot " + snapshotName);
            compute.snapshots().delete(PROJECT_ID, snapshotName).execute();
        } catch (IOException e) {
            log.warning("Failed to delete snapshot: " + e.getMessage());
        }
    }

    private void deleteTestDisk(String diskName) {
        try {
            compute.disks().get(PROJECT_ID, ZONE, diskName).execute();
        } catch (IOException e) {
            return;
        }
        try {
            log.info("Deleting disk " + diskName);
            compute.disks().delete(PROJECT_ID, ZONE, diskName).execute();
        } catch (IOException e) {
            log.warning("Failed to delete disk: " + e.getMessage());
        }
    }

    private static String snapshotSelfLink(String snapshotName) {
        return format("projects/%s/global/snapshots/" + snapshotName);
    }

    private static String existingDiskSelfLink() {
        return format("projects/%s/zones/" + ZONE + "/disks/" + TEST_EXISTING_DISK);
    }

    private void waitForZoneOperation(Operation operation) throws Exception {
        client.waitForOperationCompletion(PROJECT_ID, operation.getName(), operation.getZone(), 5 * 60 * 1000);
    }
}
