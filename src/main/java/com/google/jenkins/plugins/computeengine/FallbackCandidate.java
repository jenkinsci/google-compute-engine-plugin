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

import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

/**
 * A single ordered fallback candidate for a {@link InstanceConfiguration}.
 *
 * <p>When the primary location/machine of an instance configuration cannot be provisioned because
 * of a capacity-related error (for example {@code ZONE_RESOURCE_POOL_EXHAUSTED}), the plugin walks
 * the configured list of fallback candidates in order, trying each one until provisioning succeeds
 * or the list is exhausted. Each candidate may change the zone and machine type (and, for
 * cross-region fallbacks, the subnetwork) while keeping the same logical Jenkins label.
 *
 * <p>{@code zone} is required; when an instance template is configured, {@code machineType} may be
 * left blank (the template provides it). {@code region} is auto-derived from the zone when left
 * blank but can be overridden for cross-region subnetwork routing. {@code subnetwork} and
 * {@code template} are optional; when a field is left blank the value from the parent
 * {@link InstanceConfiguration} is used.
 *
 * <p>The maximum number of fallback candidates per {@link InstanceConfiguration} is
 * {@value #MAX_FALLBACK_CANDIDATES}. This bounds worst-case provisioner thread hold time.
 */
@Getter
@EqualsAndHashCode
public class FallbackCandidate implements Describable<FallbackCandidate> {

    /**
     * Upper bound on fallback candidates per configuration. Prevents unbounded retry chains that
     * could hold a provisioner thread for too long on a shared controller.
     */
    public static final int MAX_FALLBACK_CANDIDATES = 10;

    private final String zone;
    private final String machineType;

    private String region;
    private String subnetwork;
    private String template;

    @DataBoundConstructor
    public FallbackCandidate(String zone, String machineType) {
        this.zone = zone != null ? zone.trim() : "";
        this.machineType = machineType != null ? machineType.trim() : "";
    }

    @DataBoundSetter
    public void setRegion(String region) {
        this.region = region;
    }

    @DataBoundSetter
    public void setSubnetwork(String subnetwork) {
        this.subnetwork = subnetwork;
    }

    @DataBoundSetter
    public void setTemplate(String template) {
        this.template = template;
    }

    /**
     * Derives the region from the zone name. GCE zone names encode the region as the prefix
     * before the last hyphen-letter segment (e.g. "us-west1-a" → "us-west1").
     *
     * @return the derived region, or empty string if the zone is blank or has no recognizable suffix.
     */
    public String getEffectiveRegion() {
        if (region != null && !region.isEmpty()) {
            return region;
        }
        return deriveRegionFromZone(zone);
    }

    static String deriveRegionFromZone(String zoneName) {
        if (zoneName == null || zoneName.isEmpty()) {
            return "";
        }
        String name = zoneName.contains("/") ? zoneName.substring(zoneName.lastIndexOf('/') + 1) : zoneName;
        int lastDash = name.lastIndexOf('-');
        if (lastDash > 0 && lastDash < name.length() - 1) {
            return name.substring(0, lastDash);
        }
        return "";
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("zone=").append(zone).append(" machineType=").append(machineType);
        if (region != null && !region.isEmpty()) {
            sb.append(" region=").append(region);
        }
        if (subnetwork != null && !subnetwork.isEmpty()) {
            sb.append(" subnetwork=").append(subnetwork);
        }
        if (template != null && !template.isEmpty()) {
            sb.append(" template=").append(template);
        }
        return sb.toString();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Descriptor<FallbackCandidate> getDescriptor() {
        return Jenkins.get().getDescriptorOrDie(getClass());
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<FallbackCandidate> {
        @Override
        public String getDisplayName() {
            return "Fallback Candidate";
        }

        public FormValidation doCheckZone(@QueryParameter String value) {
            if (value == null || value.trim().isEmpty()) {
                return FormValidation.error("Zone is required for a fallback candidate.");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckMachineType(
                @QueryParameter String value, @QueryParameter("template") String template) {
            if ((value == null || value.trim().isEmpty())
                    && (template == null || template.trim().isEmpty())) {
                return FormValidation.warning(
                        "Machine type is required unless an instance template is specified.");
            }
            return FormValidation.ok();
        }

        public FormValidation doCheckRegion(
                @QueryParameter String value, @QueryParameter("zone") String zone) {
            if (value != null && !value.trim().isEmpty()) {
                return FormValidation.ok();
            }
            if (zone != null && !zone.trim().isEmpty()) {
                String derived = deriveRegionFromZone(zone);
                if (!derived.isEmpty()) {
                    return FormValidation.ok("Region will be auto-derived as: " + derived);
                }
            }
            return FormValidation.ok();
        }
    }
}
