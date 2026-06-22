package com.google.jenkins.plugins.computeengine;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.services.compute.model.Instance;
import com.google.cloud.graphite.platforms.plugin.client.ComputeClient;
import com.google.jenkins.plugins.computeengine.client.ComputeClientV2;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class CleanLostNodesWorkTest {
    private static final String PROJECT_ID = "test-project";
    private static final String REMOTE_INSTANCE_NAME = "agent-1";
    private static final String ZONE = "us-central1-a";
    private static final String CLEANUP_LABEL = "controller-a";

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void restrictionEnabled_mismatchingLabel_doesNotTerminateOrphan() throws Exception {
        ComputeEngineCloud cloud = mockCloud(true, CLEANUP_LABEL);
        Instance remote = remoteOrphanedInstance(CLEANUP_LABEL + "-other");
        ComputeClientV2 clientV2 = mockComputeClientV2WithRemoteInstances(List.of(remote));
        when(cloud.getClientV2()).thenReturn(clientV2);
        ComputeClient computeClient = mockComputeClient();
        when(cloud.getClient()).thenReturn(computeClient);

        j.jenkins.clouds.add(cloud);

        new CleanLostNodesWork().doRun();

        verify(computeClient, never()).terminateInstanceAsync(anyString(), anyString(), anyString());
    }

    @Test
    public void restrictionEnabled_matchingLabel_terminatesOrphan() throws Exception {
        ComputeEngineCloud cloud = mockCloud(true, CLEANUP_LABEL);
        Instance remote = remoteOrphanedInstance(CLEANUP_LABEL);
        ComputeClientV2 clientV2 = mockComputeClientV2WithRemoteInstances(List.of(remote));
        when(cloud.getClientV2()).thenReturn(clientV2);
        ComputeClient computeClient = mockComputeClient();
        when(cloud.getClient()).thenReturn(computeClient);

        j.jenkins.clouds.add(cloud);

        new CleanLostNodesWork().doRun();

        verify(computeClient).terminateInstanceAsync(eq(PROJECT_ID), eq(ZONE), eq(REMOTE_INSTANCE_NAME));
    }

    @Test
    public void restrictionDisabled_terminatesOrphanWithoutCleanupLabel() throws Exception {
        ComputeEngineCloud cloud = mockCloud(false, CLEANUP_LABEL);
        Instance remote = remoteOrphanedInstance(null);
        ComputeClientV2 clientV2 = mockComputeClientV2WithRemoteInstances(List.of(remote));
        when(cloud.getClientV2()).thenReturn(clientV2);
        ComputeClient computeClient = mockComputeClient();
        when(cloud.getClient()).thenReturn(computeClient);

        j.jenkins.clouds.add(cloud);

        new CleanLostNodesWork().doRun();

        verify(computeClient).terminateInstanceAsync(eq(PROJECT_ID), eq(ZONE), eq(REMOTE_INSTANCE_NAME));
    }

    private static ComputeEngineCloud mockCloud(boolean cleanupRestriction, String cleanupLabel) {
        ComputeEngineCloud cloud = mock(ComputeEngineCloud.class);
        when(cloud.getCloudName()).thenReturn("mock-cloud");
        when(cloud.isLostNodeCleanupRestriction()).thenReturn(cleanupRestriction);
        when(cloud.getLostNodeCleanupLabel()).thenReturn(cleanupLabel);
        when(cloud.getProjectId()).thenReturn(PROJECT_ID);
        return cloud;
    }

    private static ComputeClientV2 mockComputeClientV2WithRemoteInstances(List<Instance> instances) throws Exception {
        ComputeClientV2 clientV2 = mock(ComputeClientV2.class);
        when(clientV2.retrieveInstanceByLabelKeyAndStatus(CleanLostNodesWork.NODE_IN_USE_LABEL_KEY, "RUNNING"))
                .thenReturn(instances);
        return clientV2;
    }

    private static ComputeClient mockComputeClient() {
        return mock(ComputeClient.class);
    }

    private static Instance remoteOrphanedInstance(String cleanupLabel) {
        Map<String, String> labels = new HashMap<>();
        labels.put(CleanLostNodesWork.NODE_IN_USE_LABEL_KEY, orphanedLastRefreshLabel());
        if (cleanupLabel != null) {
            labels.put(CleanLostNodesWork.LOST_NODE_CLEANUP_KEY, cleanupLabel);
        }
        return new Instance().setName(REMOTE_INSTANCE_NAME).setZone(ZONE).setLabels(labels);
    }

    private static String orphanedLastRefreshLabel() {
        long staleByMillis = CleanLostNodesWork.RECURRENCE_PERIOD * CleanLostNodesWork.LOST_MULTIPLIER + 1_000;
        OffsetDateTime staleRefresh = OffsetDateTime.now(ZoneOffset.UTC).minus(staleByMillis, ChronoUnit.MILLIS);
        return CleanLostNodesWork.LAST_REFRESH_FORMATTER.format(staleRefresh);
    }
}
