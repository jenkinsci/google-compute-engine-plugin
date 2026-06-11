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

import com.google.api.services.compute.model.Operation;
import java.util.Locale;

/**
 * Classifies GCE provisioning failures into "retryable" (capacity-related) and "non-retryable"
 * (configuration/permission/quota) buckets, so that ordered fallback is only attempted for
 * transient capacity shortages.
 *
 * <p>Retryable conditions are zone/machine-family/machine-type capacity exhaustion, for example
 * {@code ZONE_RESOURCE_POOL_EXHAUSTED} or a stockout message such as
 * "does not have enough resources available to fulfill the request".
 *
 * <p>Explicitly non-retryable conditions include quota failures, authentication/permission
 * failures, invalid configuration, invalid network configuration and invalid image/instance
 * template — retrying these in another zone would not help.
 *
 * <h3>Unknown error policy</h3>
 * <p>Unrecognized error codes are treated as <b>non-retryable</b> (fallback is NOT attempted).
 * Rationale: a conservative policy avoids masking real configuration bugs behind fallback
 * retries. If GCP introduces new capacity-related error codes, they should be added to
 * {@link #RETRYABLE_MARKERS} after verifying the GCP documentation.
 *
 * <h3>GCP error code reference</h3>
 * <p>The authoritative list of GCE operation error codes is maintained at:
 * <a href="https://cloud.google.com/compute/docs/troubleshooting/troubleshooting-vm-creation">
 * Troubleshooting VM creation</a> and
 * <a href="https://cloud.google.com/compute/docs/reference/rest/v1/zone/operations">
 * Zone Operations REST reference</a>. New capacity-related codes should be added to
 * {@link #RETRYABLE_MARKERS} as they appear.
 *
 * @see <a href="https://cloud.google.com/compute/docs/troubleshooting/troubleshooting-vm-creation">
 *     GCP: Troubleshooting VM creation</a>
 */
public final class ProvisioningErrorClassifier {

    /**
     * Substrings (matched case-insensitively against an error code or message) that indicate a
     * capacity shortage where trying a different zone or machine type may succeed.
     *
     * <p>Sources:
     * <ul>
     *   <li>{@code ZONE_RESOURCE_POOL_EXHAUSTED} — zone lacks capacity for the requested resource</li>
     *   <li>{@code ZONE_RESOURCE_POOL_EXHAUSTED_WITH_DETAILS} — same with extended detail payload</li>
     *   <li>{@code RESOURCE_POOL_EXHAUSTED} — regional capacity shortage (less common)</li>
     *   <li>{@code STOCKOUT} — newer capacity error code observed in some regions</li>
     *   <li>{@code RESOURCE_NOT_READY} — transient resource readiness failure (e.g. host maintenance)</li>
     *   <li>"does not have enough resources" — free-text message accompanying capacity errors</li>
     * </ul>
     *
     * <p>Maintenance: if GCP introduces additional capacity-related codes, add them here after
     * confirming in the GCP documentation that retrying in another zone is appropriate.
     */
    private static final String[] RETRYABLE_MARKERS = {
        "ZONE_RESOURCE_POOL_EXHAUSTED",
        "RESOURCE_POOL_EXHAUSTED",
        "STOCKOUT",
        "RESOURCE_NOT_READY",
        "DOES NOT HAVE ENOUGH RESOURCES",
        "DOES_NOT_HAVE_ENOUGH_RESOURCES"
    };

    private ProvisioningErrorClassifier() {}

    /**
     * Classifies a GCE error code or message as retryable (capacity-related) or not.
     *
     * <p><b>Policy for unrecognized codes:</b> returns {@code false} (non-retryable). This is a
     * deliberate conservative choice — unknown errors abort immediately rather than silently
     * retrying all fallback candidates, which could mask real configuration bugs. If you observe
     * a new GCP capacity error code that should trigger fallback, add it to
     * {@link #RETRYABLE_MARKERS}.
     *
     * @param codeOrMessage a GCE operation error code (e.g. {@code ZONE_RESOURCE_POOL_EXHAUSTED}) or
     *     a free-text error message.
     * @return {@code true} if the failure looks like a transient capacity shortage that fallback
     *     should retry in another zone/machine type; {@code false} for {@code null}, unknown, or
     *     clearly non-capacity errors (including quota failures).
     */
    public static boolean isRetryable(String codeOrMessage) {
        if (codeOrMessage == null) {
            return false;
        }
        String normalized = codeOrMessage.toUpperCase(Locale.ROOT);
        if (normalized.contains("QUOTA")) {
            return false;
        }
        for (String marker : RETRYABLE_MARKERS) {
            if (normalized.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    /** @return {@code true} if the operation error carries at least one error entry. */
    public static boolean hasErrors(Operation.Error error) {
        return error != null && error.getErrors() != null && !error.getErrors().isEmpty();
    }

    /** @return the first error code on the operation error, or {@code null} if none. */
    public static String firstErrorCode(Operation.Error error) {
        if (!hasErrors(error)) {
            return null;
        }
        return error.getErrors().get(0).getCode();
    }

    /** @return a short {@code CODE: message} summary of the first error, for logging. */
    public static String errorSummary(Operation.Error error) {
        if (!hasErrors(error)) {
            return "unknown error";
        }
        Operation.Error.Errors first = error.getErrors().get(0);
        String code = first.getCode();
        String message = first.getMessage();
        StringBuilder sb = new StringBuilder();
        sb.append(code != null ? code : "ERROR");
        if (message != null && !message.isEmpty()) {
            sb.append(": ").append(message);
        }
        return sb.toString();
    }
}
