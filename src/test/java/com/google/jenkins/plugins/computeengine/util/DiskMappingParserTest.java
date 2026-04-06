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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DiskMappingParserTest {

    @Test
    public void testNullInput() {
        assertTrue(DiskMappingParser.parse(null).isEmpty());
    }

    @Test
    public void testEmptyInput() {
        assertTrue(DiskMappingParser.parse("").isEmpty());
    }

    @Test
    public void testBlankInput() {
        assertTrue(DiskMappingParser.parse("   ").isEmpty());
    }

    @Test
    public void testMultipleDisksWithBlankLines() {
        var disks = DiskMappingParser.parse("\nsource-snapshot=snap-a\n\nname=existing-disk\n");
        assertEquals(2, disks.size());
    }

    @Test
    public void testUnknownKeysIgnored() {
        var disk = DiskMappingParser.parse("source-snapshot=snap,unknown-key=value,foo=bar")
                .get(0);
        assertEquals("snap", disk.getInitializeParams().getSourceSnapshot());
    }

    @Test
    public void testWhitespaceAroundKeysAndValues() {
        var disk = DiskMappingParser.parse(" source-snapshot = my-snapshot , size = 50 , type = pd-ssd ")
                .get(0);
        assertEquals("my-snapshot", disk.getInitializeParams().getSourceSnapshot());
        assertThat(disk.getInitializeParams().getDiskSizeGb(), is(50L));
        assertEquals("pd-ssd", disk.getInitializeParams().getDiskType());
    }

    @Test
    public void testCreateDiskAllFields() {
        var disks = DiskMappingParser.parse(
                "source-snapshot=my-snapshot,size=100,type=pd-ssd,name=my-disk,device-name=data,description=test disk,mode=ro,interface=NVME,auto-delete=no");
        assertEquals(1, disks.size());
        var disk = disks.get(0);
        assertFalse(disk.getBoot());
        assertFalse(disk.getAutoDelete());
        assertEquals("READ_ONLY", disk.getMode());
        assertEquals("data", disk.getDeviceName());
        assertEquals("NVME", disk.getInterface());
        var params = disk.getInitializeParams();
        assertNotNull(params);
        assertEquals("my-snapshot", params.getSourceSnapshot());
        assertEquals(Long.valueOf(100), params.getDiskSizeGb());
        assertEquals("pd-ssd", params.getDiskType());
        assertEquals("my-disk", params.getDiskName());
        assertEquals("test disk", params.getDescription());
    }

    @Test
    public void testCreateDiskSnapshotOnly() {
        var disk = DiskMappingParser.parse("source-snapshot=my-snapshot").get(0);
        assertFalse(disk.getBoot());
        assertTrue(disk.getAutoDelete());
        assertNotNull(disk.getInitializeParams());
        assertEquals("my-snapshot", disk.getInitializeParams().getSourceSnapshot());
        assertNull(disk.getInitializeParams().getDiskSizeGb());
        assertNull(disk.getInitializeParams().getDiskType());
    }

    @Test
    public void testCreateDiskWithFullSnapshotUrl() {
        var fullUrl = "https://compute.googleapis.com/compute/v1/projects/my-project/global/snapshots/my-snapshot";
        var disk = DiskMappingParser.parse("source-snapshot=" + fullUrl).get(0);
        assertEquals(fullUrl, disk.getInitializeParams().getSourceSnapshot());
    }

    @Test
    public void testCreateDiskWithRelativeSnapshotPath() {
        var relativePath = "projects/other-project/global/snapshots/my-snapshot";
        var disk = DiskMappingParser.parse("source-snapshot=" + relativePath).get(0);
        assertEquals(relativePath, disk.getInitializeParams().getSourceSnapshot());
    }

    @Test
    public void testCreateBlankDiskWithSizeOnly() {
        var disk = DiskMappingParser.parse("size=100").get(0);
        assertFalse(disk.getBoot());
        assertTrue(disk.getAutoDelete());
        assertNotNull(disk.getInitializeParams());
        assertEquals(Long.valueOf(100), disk.getInitializeParams().getDiskSizeGb());
        assertNull(disk.getInitializeParams().getSourceSnapshot());
    }

    @Test
    public void testCreateBlankDiskWithSizeAndType() {
        var disk = DiskMappingParser.parse("size=200,type=pd-ssd").get(0);
        assertEquals(Long.valueOf(200), disk.getInitializeParams().getDiskSizeGb());
        assertEquals("pd-ssd", disk.getInitializeParams().getDiskType());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateDiskMissingRequiredKeys() {
        DiskMappingParser.parse("type=pd-ssd,auto-delete=no");
    }

    @Test
    public void testCreateDiskAutoDeleteDefaultsToTrue() {
        var disk = DiskMappingParser.parse("source-snapshot=snap,size=50").get(0);
        assertTrue(disk.getAutoDelete());
    }

    @Test
    public void testCreateDiskAutoDeleteYes() {
        var disk =
                DiskMappingParser.parse("source-snapshot=snap,auto-delete=yes").get(0);
        assertTrue(disk.getAutoDelete());
    }

    @Test
    public void testCreateDiskAutoDeleteNo() {
        var disk =
                DiskMappingParser.parse("source-snapshot=snap,auto-delete=no").get(0);
        assertFalse(disk.getAutoDelete());
    }

    @Test
    public void testCreateDiskModeReadOnly() {
        var disk = DiskMappingParser.parse("source-snapshot=snap,mode=ro").get(0);
        assertEquals("READ_ONLY", disk.getMode());
    }

    @Test
    public void testCreateDiskModeReadWrite() {
        var disk = DiskMappingParser.parse("source-snapshot=snap,mode=rw").get(0);
        assertEquals("READ_WRITE", disk.getMode());
    }

    @Test
    public void testCreateDiskModeDefaultNull() {
        var disk = DiskMappingParser.parse("source-snapshot=snap").get(0);
        assertNull(disk.getMode());
    }

    @Test
    public void testCreateDiskInterfaceNvme() {
        var disk =
                DiskMappingParser.parse("source-snapshot=snap,interface=NVME").get(0);
        assertEquals("NVME", disk.getInterface());
    }

    @Test
    public void testCreateDiskInterfaceScsi() {
        var disk =
                DiskMappingParser.parse("source-snapshot=snap,interface=SCSI").get(0);
        assertEquals("SCSI", disk.getInterface());
    }

    @Test
    public void testCreateDiskDeviceName() {
        var disk = DiskMappingParser.parse("source-snapshot=snap,device-name=data-disk")
                .get(0);
        assertEquals("data-disk", disk.getDeviceName());
    }

    @Test
    public void testCreateDiskDescription() {
        var disk = DiskMappingParser.parse("source-snapshot=snap,description=cache volume")
                .get(0);
        assertEquals("cache volume", disk.getInitializeParams().getDescription());
    }

    @Test
    public void testMultipleCreateDisks() {
        var disks = DiskMappingParser.parse(
                "source-snapshot=snap-a,size=50,type=pd-ssd\nsource-snapshot=snap-b,size=100,auto-delete=no");
        assertEquals(2, disks.size());
        assertEquals("snap-a", disks.get(0).getInitializeParams().getSourceSnapshot());
        assertEquals("pd-ssd", disks.get(0).getInitializeParams().getDiskType());
        assertEquals("snap-b", disks.get(1).getInitializeParams().getSourceSnapshot());
        assertFalse(disks.get(1).getAutoDelete());
    }

    @Test
    public void testAttachExistingDiskByName() {
        var disk = DiskMappingParser.parse("name=my-data-disk").get(0);
        assertFalse(disk.getBoot());
        assertTrue(disk.getAutoDelete());
        assertNull(disk.getInitializeParams());
        assertEquals("my-data-disk", disk.getSource());
    }

    @Test
    public void testAttachExistingDiskAllFields() {
        var disk = DiskMappingParser.parse("name=my-data-disk,mode=ro,device-name=data,interface=NVME,auto-delete=no")
                .get(0);
        assertFalse(disk.getBoot());
        assertFalse(disk.getAutoDelete());
        assertEquals("my-data-disk", disk.getSource());
        assertEquals("READ_ONLY", disk.getMode());
        assertEquals("data", disk.getDeviceName());
        assertEquals("NVME", disk.getInterface());
        assertNull(disk.getInitializeParams());
    }

    @Test
    public void testAttachExistingDiskByFullUrl() {
        var fullUrl = "projects/myproject/zones/us-east1-b/disks/shared-data";
        var disk = DiskMappingParser.parse("name=" + fullUrl + ",mode=ro").get(0);
        assertEquals(fullUrl, disk.getSource());
        assertEquals("READ_ONLY", disk.getMode());
        assertNull(disk.getInitializeParams());
    }

    @Test
    public void testAttachExistingDiskRegionalUri() {
        var uri = "projects/myproject/regions/us-central1/disks/my-regional-disk";
        var disk = DiskMappingParser.parse("name=" + uri + ",mode=ro").get(0);
        assertEquals(uri, disk.getSource());
    }

    @Test
    public void testAttachExistingDiskAutoDeleteDefaultsToTrue() {
        var disk = DiskMappingParser.parse("name=my-disk").get(0);
        assertTrue(disk.getAutoDelete());
    }

    @Test
    public void testAttachExistingDiskAutoDeleteNo() {
        var disk = DiskMappingParser.parse("name=my-disk,auto-delete=no").get(0);
        assertFalse(disk.getAutoDelete());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAttachExistingDiskMissingName() {
        DiskMappingParser.parse("mode=ro,auto-delete=no");
    }

    @Test
    public void testMixedCreateAndAttachDisks() {
        var disks = DiskMappingParser.parse("source-snapshot=snap-a,size=50,type=pd-ssd\nname=existing-disk,mode=ro");
        assertEquals(2, disks.size());

        var created = disks.get(0);
        assertNotNull(created.getInitializeParams());
        assertEquals("snap-a", created.getInitializeParams().getSourceSnapshot());

        var attached = disks.get(1);
        assertNull(attached.getInitializeParams());
        assertEquals("existing-disk", attached.getSource());
        assertEquals("READ_ONLY", attached.getMode());
    }

    @Test
    public void testNormalizeDiskTypeShortName() {
        assertEquals("zones/us-east1-b/diskTypes/pd-ssd", DiskMappingParser.normalizeDiskType("pd-ssd", "us-east1-b"));
    }

    @Test
    public void testNormalizeDiskTypeRelativePath() {
        var relativePath = "zones/us-east1-b/diskTypes/pd-balanced";
        assertEquals(relativePath, DiskMappingParser.normalizeDiskType(relativePath, "us-west1-a"));
    }

    @Test
    public void testNormalizeDiskTypeFullUrl() {
        var fullUrl = "https://www.googleapis.com/compute/v1/projects/my-project/zones/us-east1-b/diskTypes/pd-ssd";
        assertEquals(fullUrl, DiskMappingParser.normalizeDiskType(fullUrl, "us-west1-a"));
    }

    @Test
    public void testNormalizeDiskTypeNull() {
        assertNull(DiskMappingParser.normalizeDiskType(null, "us-east1-b"));
    }

    @Test
    public void testNormalizeDiskSourceShortName() {
        assertEquals(
                "projects/my-project/zones/us-east1-b/disks/my-disk",
                DiskMappingParser.normalizeDiskSource("my-disk", "my-project", "us-east1-b"));
    }

    @Test
    public void testNormalizeDiskSourceRelativePath() {
        var relativePath = "projects/myproject/zones/us-east1-b/disks/my-disk";
        assertEquals(relativePath, DiskMappingParser.normalizeDiskSource(relativePath, "other-project", "us-west1-a"));
    }

    @Test
    public void testNormalizeDiskSourceFullUrl() {
        var fullUrl = "https://compute.googleapis.com/compute/v1/projects/myproject/zones/us-east1-b/disks/my-disk";
        assertEquals(fullUrl, DiskMappingParser.normalizeDiskSource(fullUrl, "other-project", "us-west1-a"));
    }

    @Test
    public void testNormalizeDiskSourceNull() {
        assertNull(DiskMappingParser.normalizeDiskSource(null, "my-project", "us-east1-b"));
    }

    @Test
    public void testNormalizeSnapshotSourceShortName() {
        assertEquals(
                "projects/my-project/global/snapshots/my-snapshot",
                DiskMappingParser.normalizeSnapshotSource("my-snapshot", "my-project"));
    }

    @Test
    public void testNormalizeSnapshotSourceRelativePath() {
        var relativePath = "projects/other-project/global/snapshots/my-snapshot";
        assertEquals(relativePath, DiskMappingParser.normalizeSnapshotSource(relativePath, "my-project"));
    }

    @Test
    public void testNormalizeSnapshotSourceFullUrl() {
        var fullUrl = "https://compute.googleapis.com/compute/v1/projects/other-project/global/snapshots/my-snapshot";
        assertEquals(fullUrl, DiskMappingParser.normalizeSnapshotSource(fullUrl, "my-project"));
    }

    @Test
    public void testNormalizeSnapshotSourceNull() {
        assertNull(DiskMappingParser.normalizeSnapshotSource(null, "my-project"));
    }
}
