/*
 * Copyright 2026 CloudBees, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.jenkins.plugins.computeengine;

import static com.google.jenkins.plugins.computeengine.InstanceConfigurationTest.instanceConfigurationBuilder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.Operation;
import com.google.cloud.graphite.platforms.plugin.client.ComputeClient;
import com.google.cloud.graphite.platforms.plugin.client.ComputeClient.OperationException;
import java.io.IOException;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/** Unit tests for the ordered zone/machine-type fallback logic in {@link InstanceConfiguration#provision()}. */
@RunWith(MockitoJUnitRunner.class)
public class InstanceConfigurationFallbackTest {

    private static final String PROJECT_ID = "test-project";

    private static final String PRIMARY_ZONE = "us-west1-a";
    private static final String PRIMARY_MACHINE_TYPE = "n1-standard-1";
    private static final String F1_ZONE = "us-west1-b";
    private static final String F1_MACHINE_TYPE = "n4d-standard-32";
    private static final String F2_ZONE = "us-central1-a";
    private static final String F2_MACHINE_TYPE = "n2d-standard-32";

    private static final String CAPACITY_CODE = "ZONE_RESOURCE_POOL_EXHAUSTED";

    @Mock
    public ComputeEngineCloud cloud;

    @Mock
    public ComputeClient computeClient;

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Before
    public void init() {
        when(cloud.getProjectId()).thenReturn(PROJECT_ID);
        when(cloud.getClient()).thenReturn(computeClient);
        when(cloud.getCloudName()).thenReturn("test");
    }

    private InstanceConfiguration configWithFallbacks(FallbackCandidate... candidates) {
        return instanceConfigurationBuilder()
                .zone(PRIMARY_ZONE)
                .machineType(PRIMARY_MACHINE_TYPE)
                .fallbackCandidates(List.of(candidates))
                .cloud(cloud)
                .build();
    }

    private static FallbackCandidate candidate(String zone, String machineType) {
        return new FallbackCandidate(zone, machineType);
    }

    private static Operation op(String name, String zone) {
        return new Operation().setName(name).setZone(zone);
    }

    private static Operation opWithError(String name, String zone, String code) {
        return op(name, zone)
                .setError(new Operation.Error()
                        .setErrors(List.of(
                                new Operation.Error.Errors().setCode(code).setMessage("simulated " + code))));
    }

    @Test
    public void firstCandidateSucceeds_noFallbackUsed() throws Exception {
        InstanceConfiguration config = configWithFallbacks(candidate(F1_ZONE, F1_MACHINE_TYPE));

        when(computeClient.insertInstance(eq(PROJECT_ID), any(), any(Instance.class)))
                .thenReturn(op("op-primary", PRIMARY_ZONE));
        when(computeClient.waitForOperationCompletion(eq(PROJECT_ID), any(Operation.class), anyLong()))
                .thenReturn(op("op-primary", PRIMARY_ZONE)); // no error

        ComputeEngineInstance node = config.provision();

        assertNotNull(node);
        assertEquals(PRIMARY_ZONE, node.getZone());
        verify(computeClient, times(1)).insertInstance(anyString(), any(), any(Instance.class));
        verify(computeClient, never()).terminateInstanceAsync(anyString(), anyString(), anyString());
    }

    @Test
    public void capacityFailureFallsBackToNextCandidate() throws Exception {
        InstanceConfiguration config = configWithFallbacks(candidate(F1_ZONE, F1_MACHINE_TYPE));

        ArgumentCaptor<Instance> instanceCaptor = ArgumentCaptor.forClass(Instance.class);
        when(computeClient.insertInstance(eq(PROJECT_ID), any(), any(Instance.class)))
                .thenReturn(op("op-primary", PRIMARY_ZONE), op("op-fallback", F1_ZONE));
        when(computeClient.waitForOperationCompletion(eq(PROJECT_ID), any(Operation.class), anyLong()))
                .thenReturn(
                        opWithError("op-primary", PRIMARY_ZONE, CAPACITY_CODE), // primary exhausted
                        op("op-fallback", F1_ZONE)); // fallback succeeds

        ComputeEngineInstance node = config.provision();

        assertNotNull(node);
        // The winning node should be in the fallback candidate's zone.
        assertEquals(F1_ZONE, node.getZone());

        // Two insert attempts, in order: primary then fallback.
        verify(computeClient, times(2)).insertInstance(anyString(), any(), instanceCaptor.capture());
        List<Instance> attempted = instanceCaptor.getAllValues();
        assertEquals(PRIMARY_ZONE, attempted.get(0).getZone());
        assertEquals(PRIMARY_MACHINE_TYPE, attempted.get(0).getMachineType());
        assertEquals(F1_ZONE, attempted.get(1).getZone());
        assertEquals(F1_MACHINE_TYPE, attempted.get(1).getMachineType());

        // The failed primary VM must be cleaned up exactly once, in the primary zone.
        verify(computeClient, times(1)).terminateInstanceAsync(eq(PROJECT_ID), eq(PRIMARY_ZONE), anyString());
    }

    @Test
    public void walksMultipleCandidatesInOrder() throws Exception {
        InstanceConfiguration config =
                configWithFallbacks(candidate(F1_ZONE, F1_MACHINE_TYPE), candidate(F2_ZONE, F2_MACHINE_TYPE));

        when(computeClient.insertInstance(eq(PROJECT_ID), any(), any(Instance.class)))
                .thenReturn(op("op-primary", PRIMARY_ZONE), op("op-f1", F1_ZONE), op("op-f2", F2_ZONE));
        when(computeClient.waitForOperationCompletion(eq(PROJECT_ID), any(Operation.class), anyLong()))
                .thenReturn(
                        opWithError("op-primary", PRIMARY_ZONE, CAPACITY_CODE),
                        opWithError("op-f1", F1_ZONE, CAPACITY_CODE),
                        op("op-f2", F2_ZONE));

        ComputeEngineInstance node = config.provision();

        assertNotNull(node);
        assertEquals(F2_ZONE, node.getZone());
        verify(computeClient, times(3)).insertInstance(anyString(), any(), any(Instance.class));
        verify(computeClient, times(2)).terminateInstanceAsync(anyString(), anyString(), anyString());
    }

    @Test
    public void nonRetryableErrorAbortsImmediately() throws Exception {
        InstanceConfiguration config = configWithFallbacks(candidate(F1_ZONE, F1_MACHINE_TYPE));

        when(computeClient.insertInstance(eq(PROJECT_ID), any(), any(Instance.class)))
                .thenReturn(op("op-primary", PRIMARY_ZONE), op("op-fallback", F1_ZONE));
        when(computeClient.waitForOperationCompletion(eq(PROJECT_ID), any(Operation.class), anyLong()))
                .thenReturn(opWithError("op-primary", PRIMARY_ZONE, "QUOTA_EXCEEDED"));

        IOException ex = assertThrows(IOException.class, config::provision);
        assertEquals(true, ex.getMessage().contains("Non-retryable"));

        // Must NOT try the fallback candidate on a non-retryable error.
        verify(computeClient, times(1)).insertInstance(anyString(), any(), any(Instance.class));
        // Still cleans up the failed VM.
        verify(computeClient, times(1)).terminateInstanceAsync(eq(PROJECT_ID), eq(PRIMARY_ZONE), anyString());
    }

    @Test
    public void allCandidatesExhaustedThrows() throws Exception {
        InstanceConfiguration config = configWithFallbacks(candidate(F1_ZONE, F1_MACHINE_TYPE));

        when(computeClient.insertInstance(eq(PROJECT_ID), any(), any(Instance.class)))
                .thenReturn(op("op-primary", PRIMARY_ZONE), op("op-fallback", F1_ZONE));
        when(computeClient.waitForOperationCompletion(eq(PROJECT_ID), any(Operation.class), anyLong()))
                .thenReturn(
                        opWithError("op-primary", PRIMARY_ZONE, CAPACITY_CODE),
                        opWithError("op-fallback", F1_ZONE, CAPACITY_CODE));

        IOException ex = assertThrows(IOException.class, config::provision);
        assertEquals(true, ex.getMessage().contains("Exhausted all fallback candidates"));

        verify(computeClient, times(2)).insertInstance(anyString(), any(), any(Instance.class));
        verify(computeClient, times(2)).terminateInstanceAsync(anyString(), anyString(), anyString());
    }

    @Test
    public void operationExceptionIsTreatedAsFailure() throws Exception {
        InstanceConfiguration config = configWithFallbacks(candidate(F1_ZONE, F1_MACHINE_TYPE));

        when(computeClient.insertInstance(eq(PROJECT_ID), any(), any(Instance.class)))
                .thenReturn(op("op-primary", PRIMARY_ZONE), op("op-fallback", F1_ZONE));
        when(computeClient.waitForOperationCompletion(eq(PROJECT_ID), any(Operation.class), anyLong()))
                .thenThrow(new OperationException(new Operation.Error()
                        .setErrors(List.of(new Operation.Error.Errors()
                                .setCode(CAPACITY_CODE)
                                .setMessage("thrown")))))
                .thenReturn(op("op-fallback", F1_ZONE));

        ComputeEngineInstance node = config.provision();

        assertNotNull(node);
        assertEquals(F1_ZONE, node.getZone());
        verify(computeClient, times(2)).insertInstance(anyString(), any(), any(Instance.class));
    }

    @Test
    public void noFallbackConfigured_behaviorUnchanged() throws Exception {
        // No fallback candidates: provision() must NOT wait on the operation and must return the
        // node immediately, leaving the launcher to handle the operation (legacy behavior).
        InstanceConfiguration config = instanceConfigurationBuilder()
                .zone(PRIMARY_ZONE)
                .machineType(PRIMARY_MACHINE_TYPE)
                .cloud(cloud)
                .build();

        when(computeClient.insertInstance(eq(PROJECT_ID), any(), any(Instance.class)))
                .thenReturn(op("op-primary", PRIMARY_ZONE));

        ComputeEngineInstance node = config.provision();

        assertNotNull(node);
        assertEquals(PRIMARY_ZONE, node.getZone());
        verify(computeClient, times(1)).insertInstance(anyString(), any(), any(Instance.class));
        // Legacy path does not pre-wait on the operation inside provision().
        verify(computeClient, never()).waitForOperationCompletion(anyString(), any(Operation.class), anyLong());
        verify(computeClient, never()).terminateInstanceAsync(anyString(), anyString(), anyString());
    }
}
