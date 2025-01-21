/*
 * Copyright 2024 CloudBees, Inc.
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

package com.google.jenkins.plugins.computeengine.config;

import com.google.api.services.compute.model.Scheduling;
import hudson.Extension;
import hudson.util.FormValidation;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

public class SpotVm extends ProvisioningType {

    private long maxRunDurationSeconds;

    // required for casc
    @DataBoundConstructor
    public SpotVm(long maxRunDurationSeconds) {
        this.maxRunDurationSeconds = maxRunDurationSeconds;
    }

    @SuppressWarnings("unused") // jelly
    @DataBoundSetter
    public void setMaxRunDurationSeconds(long maxRunDurationSeconds) {
        this.maxRunDurationSeconds = maxRunDurationSeconds;
    }

    @SuppressWarnings("unused") // jelly
    public long getMaxRunDurationSeconds() {
        return maxRunDurationSeconds;
    }

    @Override
    public void configure(Scheduling scheduling) {
        scheduling.setProvisioningModel("SPOT");
        super.configureMaxRunDuration(scheduling, maxRunDurationSeconds);
    }

    @Extension
    public static class DescriptorImpl extends ProvisioningTypeDescriptor {
        @Override
        public String getDisplayName() {
            return "Spot VM";
        }

        @SuppressWarnings("unused") // jelly
        public FormValidation doCheckMaxRunDurationSeconds(@QueryParameter String value) {
            return Utils.doCheckMaxRunDurationSeconds(value);
        }

        @Override
        public boolean isMaxRunDurationSupported() {
            return true;
        }
    }
}
