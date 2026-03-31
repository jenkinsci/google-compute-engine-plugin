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

import static org.junit.Assert.assertTrue;

import com.google.jenkins.plugins.computeengine.MinimumInstanceChecker.MinCheckerInput;
import org.junit.Test;

/**
 * Tests for {@link MinimumInstanceChecker#shouldPreserveAgent} — the pure retention decision
 * that determines whether an agent should be preserved to meet minimum instance requirements.
 */
public class MinimumInstanceCheckerRetentionTest {

    @Test
    public void minInstances_atThreshold_preserve() {
        // 2 total = minInstances (2) → preserve
        var input = new MinCheckerInput(2, 2, 0, 0);
        assertTrue(MinimumInstanceChecker.shouldPreserveAgent(2, 0, input, 0, true, true)
                .isEmpty());
    }

    @Test
    public void minInstances_aboveThreshold_notPreserve() {
        // 3 total > minInstances (2) → not preserve
        var input = new MinCheckerInput(3, 2, 0, 0);
        assertTrue(MinimumInstanceChecker.shouldPreserveAgent(2, 0, input, 0, true, true)
                .isPresent());
    }

    @Test
    public void minInstances_belowThreshold_preserve() {
        // 1 total < minInstances (2) → preserve
        var input = new MinCheckerInput(1, 1, 0, 0);
        assertTrue(MinimumInstanceChecker.shouldPreserveAgent(2, 0, input, 0, true, true)
                .isEmpty());
    }

    @Test
    public void minSpare_idleOnline_preserve() {
        // 2 spare = minSpare (2), agent is idle+online → preserve
        var input = new MinCheckerInput(5, 2, 0, 0);
        assertTrue(MinimumInstanceChecker.shouldPreserveAgent(0, 2, input, 0, true, true)
                .isEmpty());
    }

    @Test
    public void minSpare_notIdle_notPreserve() {
        // 2 spare = minSpare (2), but agent is busy (idle=false) → not preserve
        var input = new MinCheckerInput(5, 2, 0, 0);
        assertTrue(MinimumInstanceChecker.shouldPreserveAgent(0, 2, input, 0, false, true)
                .isPresent());
    }

    @Test
    public void minSpare_offline_notPreserve() {
        // 2 spare = minSpare (2), but agent is offline (online=false) → not preserve
        var input = new MinCheckerInput(5, 2, 0, 0);
        assertTrue(MinimumInstanceChecker.shouldPreserveAgent(0, 2, input, 0, true, false)
                .isPresent());
    }

    @Test
    public void minSpare_aboveThreshold_notPreserve() {
        // 3 spare > minSpare (2) → not preserve
        var input = new MinCheckerInput(5, 3, 0, 0);
        assertTrue(MinimumInstanceChecker.shouldPreserveAgent(0, 2, input, 0, true, true)
                .isPresent());
    }

    @Test
    public void pendingTerminations_pushBelowThreshold_preserve() {
        // 3 total, min=2, 1 pending → effective = 2 → preserve
        var input = new MinCheckerInput(3, 3, 0, 0);
        assertTrue(MinimumInstanceChecker.shouldPreserveAgent(2, 0, input, 1, true, true)
                .isEmpty());
    }

    @Test
    public void pendingTerminations_stillAboveThreshold_notPreserve() {
        // 4 total, min=2, 1 pending → effective = 3 → not preserve
        var input = new MinCheckerInput(4, 4, 0, 0);
        assertTrue(MinimumInstanceChecker.shouldPreserveAgent(2, 0, input, 1, true, true)
                .isPresent());
    }

    @Test
    public void pendingTerminations_spareCheck() {
        // 3 spare, minSpare=2, 1 pending → effective spare = 2 → preserve
        var input = new MinCheckerInput(5, 3, 0, 0);
        assertTrue(MinimumInstanceChecker.shouldPreserveAgent(0, 2, input, 1, true, true)
                .isEmpty());
    }

    @Test
    public void neitherRequired_notPreserve() {
        // minInstances=0, minSpare=0 → no requirements to satisfy → not preserve
        var input = new MinCheckerInput(1, 1, 0, 0);
        assertTrue(MinimumInstanceChecker.shouldPreserveAgent(0, 0, input, 0, true, true)
                .isPresent());
    }

    @Test
    public void minInstances_takesPriority_busyAgentPreserved() {
        // 2 total = minInstances → preserve (even though agent is busy: idle=false)
        var input = new MinCheckerInput(2, 0, 0, 0);
        assertTrue(MinimumInstanceChecker.shouldPreserveAgent(2, 2, input, 0, false, true)
                .isEmpty());
    }

    @Test
    public void minInstances_takesPriority_offlineAgentPreserved() {
        // 2 total = minInstances → preserve (even though agent is offline: online=false)
        var input = new MinCheckerInput(2, 0, 0, 0);
        assertTrue(MinimumInstanceChecker.shouldPreserveAgent(2, 0, input, 0, true, false)
                .isEmpty());
    }

    @Test
    public void bothChecks_minInstancesSatisfied_spareFallback() {
        // 3 total (min=2 satisfied), 2 spare (minSpare=2) → preserve via spare
        var input = new MinCheckerInput(3, 2, 0, 0);
        assertTrue(MinimumInstanceChecker.shouldPreserveAgent(2, 2, input, 0, true, true)
                .isEmpty());
    }

    @Test
    public void bothChecks_minInstancesSatisfied_spareExceeded() {
        // 4 total (min=2 exceeded), 3 spare (minSpare=2 exceeded) → not preserve
        var input = new MinCheckerInput(4, 3, 0, 0);
        assertTrue(MinimumInstanceChecker.shouldPreserveAgent(2, 2, input, 0, true, true)
                .isPresent());
    }
}
