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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.api.services.compute.model.Operation;
import java.util.List;
import org.junit.Test;

public class ProvisioningErrorClassifierTest {

    private static Operation.Error error(String code, String message) {
        return new Operation.Error()
                .setErrors(List.of(new Operation.Error.Errors().setCode(code).setMessage(message)));
    }

    @Test
    public void capacityCodesAreRetryable() {
        assertTrue(ProvisioningErrorClassifier.isRetryable("ZONE_RESOURCE_POOL_EXHAUSTED"));
        assertTrue(ProvisioningErrorClassifier.isRetryable("ZONE_RESOURCE_POOL_EXHAUSTED_WITH_DETAILS"));
        assertTrue(ProvisioningErrorClassifier.isRetryable("RESOURCE_POOL_EXHAUSTED"));
        assertTrue(ProvisioningErrorClassifier.isRetryable("STOCKOUT"));
        assertTrue(ProvisioningErrorClassifier.isRetryable("RESOURCE_NOT_READY"));
    }

    @Test
    public void stockoutMessageIsRetryable() {
        assertTrue(
                ProvisioningErrorClassifier.isRetryable(
                        "The zone 'projects/p/zones/us-west1-a' does not have enough resources available to fulfill the request."));
    }

    @Test
    public void caseInsensitive() {
        assertTrue(ProvisioningErrorClassifier.isRetryable("zone_resource_pool_exhausted"));
        assertTrue(ProvisioningErrorClassifier.isRetryable("Stockout"));
        assertTrue(ProvisioningErrorClassifier.isRetryable("resource_not_ready"));
    }

    @Test
    public void quotaIsNotRetryable() {
        assertFalse(ProvisioningErrorClassifier.isRetryable("QUOTA_EXCEEDED"));
        assertFalse(ProvisioningErrorClassifier.isRetryable("CPUS_QUOTA_EXCEEDED"));
    }

    @Test
    public void nonCapacityErrorsAreNotRetryable() {
        assertFalse(ProvisioningErrorClassifier.isRetryable("PERMISSION_DENIED"));
        assertFalse(ProvisioningErrorClassifier.isRetryable("INVALID_FIELD_VALUE"));
        assertFalse(ProvisioningErrorClassifier.isRetryable("RESOURCE_NOT_FOUND"));
        assertFalse(ProvisioningErrorClassifier.isRetryable("UNSUPPORTED_OPERATION"));
    }

    @Test
    public void unknownErrorCodesAreNotRetryable() {
        assertFalse(ProvisioningErrorClassifier.isRetryable("SOME_FUTURE_ERROR_CODE"));
        assertFalse(ProvisioningErrorClassifier.isRetryable("UNEXPECTED_FAILURE"));
        assertFalse(ProvisioningErrorClassifier.isRetryable(""));
    }

    @Test
    public void nullIsNotRetryable() {
        assertFalse(ProvisioningErrorClassifier.isRetryable(null));
    }

    @Test
    public void hasErrorsHandlesNullAndEmpty() {
        assertFalse(ProvisioningErrorClassifier.hasErrors(null));
        assertFalse(ProvisioningErrorClassifier.hasErrors(new Operation.Error()));
        assertTrue(ProvisioningErrorClassifier.hasErrors(error("ZONE_RESOURCE_POOL_EXHAUSTED", "boom")));
    }

    @Test
    public void firstErrorCodeExtractsCode() {
        assertEquals(
                "ZONE_RESOURCE_POOL_EXHAUSTED",
                ProvisioningErrorClassifier.firstErrorCode(error("ZONE_RESOURCE_POOL_EXHAUSTED", "boom")));
        assertNull(ProvisioningErrorClassifier.firstErrorCode(null));
        assertNull(ProvisioningErrorClassifier.firstErrorCode(new Operation.Error()));
    }

    @Test
    public void errorSummaryIncludesCodeAndMessage() {
        String summary = ProvisioningErrorClassifier.errorSummary(error("ZONE_RESOURCE_POOL_EXHAUSTED", "no capacity"));
        assertTrue(summary.contains("ZONE_RESOURCE_POOL_EXHAUSTED"));
        assertTrue(summary.contains("no capacity"));
        assertEquals("unknown error", ProvisioningErrorClassifier.errorSummary(null));
    }
}
