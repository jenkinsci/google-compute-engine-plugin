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

import static org.junit.Assert.assertEquals;

import com.google.jenkins.plugins.computeengine.MinimumInstanceChecker.MinCheckerInput;
import org.junit.Test;

/**
 * Tests for {@link MinimumInstanceChecker#computeProvisionCount} — the pure provisioning math
 * that determines how many agents to spin up for a given config's minimum instance requirements.
 */
public class MinimumInstanceCheckerProvisionTest {

    @Test
    public void alreadySatisfied() {
        var input = new MinCheckerInput(2, 2, 0, 0);
        assertEquals(0, MinimumInstanceChecker.computeProvisionCount(2, 2, input));
    }

    @Test
    public void minAgentsDeficit() {
        // One agent short of minimum total - provision one more (which also becomes spare)
        var input = new MinCheckerInput(1, 1, 0, 0);
        assertEquals(1, MinimumInstanceChecker.computeProvisionCount(2, 2, input));
    }

    @Test
    public void spareDeficitOnly() {
        // Minimum total met but no spare agents available - provision 2 spares
        var input = new MinCheckerInput(2, 0, 0, 0);
        assertEquals(2, MinimumInstanceChecker.computeProvisionCount(2, 2, input));
    }

    @Test
    public void provisioningOffsetsSpare() {
        // Agent already provisioning counts toward spare requirement
        var input = new MinCheckerInput(2, 0, 1, 0);
        assertEquals(1, MinimumInstanceChecker.computeProvisionCount(2, 2, input));
    }

    @Test
    public void queuedBuildsIncreaseDemand() {
        // Queued builds increase demand on top of spare deficit
        var input = new MinCheckerInput(2, 1, 0, 2);
        assertEquals(3, MinimumInstanceChecker.computeProvisionCount(2, 2, input));
    }

    @Test
    public void bothDeficits_minCoversSpare() {
        // No agents exist - minimum total requirement covers spare need
        var input = new MinCheckerInput(0, 0, 0, 0);
        assertEquals(3, MinimumInstanceChecker.computeProvisionCount(3, 2, input));
    }

    @Test
    public void bothDeficits_spareExceedsMin() {
        // No agents exist - spare requirement exceeds minimum total
        var input = new MinCheckerInput(0, 0, 0, 0);
        assertEquals(3, MinimumInstanceChecker.computeProvisionCount(2, 3, input));
    }

    @Test
    public void minAgentsZero_onlySpare() {
        // No minimum total configured - only enforce spare requirement
        var input = new MinCheckerInput(5, 1, 0, 0);
        assertEquals(1, MinimumInstanceChecker.computeProvisionCount(0, 2, input));
    }

    @Test
    public void spareZero_onlyMinAgents() {
        // No spare configured - only enforce minimum total
        var input = new MinCheckerInput(1, 0, 0, 0);
        assertEquals(1, MinimumInstanceChecker.computeProvisionCount(2, 0, input));
    }

    @Test
    public void neitherRequired() {
        // Neither minimum configured - no provisioning needed
        var input = new MinCheckerInput(0, 0, 0, 0);
        assertEquals(0, MinimumInstanceChecker.computeProvisionCount(0, 0, input));
    }

    @Test
    public void minAgentsProvisionOffsetsSpareCalculation() {
        // Provisioning for minimum total also counts toward spare requirement
        var input = new MinCheckerInput(1, 0, 0, 0);
        assertEquals(2, MinimumInstanceChecker.computeProvisionCount(3, 2, input));
    }

    @Test
    public void excessAgents_noProvisioning() {
        // More agents than required - no provisioning needed
        var input = new MinCheckerInput(10, 5, 0, 0);
        assertEquals(0, MinimumInstanceChecker.computeProvisionCount(2, 2, input));
    }

    @Test
    public void queuedBuildsWithExistingSpare() {
        // Enough spare capacity to handle queued builds - no additional provisioning
        var input = new MinCheckerInput(5, 3, 0, 1);
        assertEquals(0, MinimumInstanceChecker.computeProvisionCount(2, 2, input));
    }

    @Test
    public void provisioningAndQueuedCombined() {
        // Both in-progress provisioning and queued builds affect spare calculation
        var input = new MinCheckerInput(3, 0, 1, 2);
        assertEquals(3, MinimumInstanceChecker.computeProvisionCount(3, 2, input));
    }
}
