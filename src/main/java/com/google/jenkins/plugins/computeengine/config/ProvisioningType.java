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

import com.google.api.client.json.GenericJson;
import com.google.api.services.compute.model.Scheduling;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;

/**
 * ProvisioningType represents the type of VM to be provisioned.
 */
public abstract class ProvisioningType extends AbstractDescribableImpl<ProvisioningType> {

    protected void configureMaxRunDuration(Scheduling scheduling, long maxRunDurationSeconds) {
        if (maxRunDurationSeconds > 0) {
            GenericJson j = new GenericJson();
            j.set("seconds", maxRunDurationSeconds);
            scheduling.set("maxRunDuration", j);
            /* Note: Only the instance is set to delete here, not the disk. Disk deletion is based on the
              `bootDiskAutoDelete` config value. For instance termination at `maxRunDuration`, GCP supports two
              termination actions: DELETE and STOP.
              For Jenkins agents, DELETE is more appropriate. If the agent instance is needed again, it can be
              recreated using the disk, which should have been anticipated and disk should be set to not delete in
              `bootDiskAutoDelete`.
            */
            scheduling.setInstanceTerminationAction("DELETE");
        }
    }

    public abstract void configure(Scheduling scheduling);

    public abstract static class ProvisioningTypeDescriptor extends Descriptor<ProvisioningType> {

        @SuppressWarnings("unused") // jelly
        public abstract boolean isMaxRunDurationSupported();
    }
}
