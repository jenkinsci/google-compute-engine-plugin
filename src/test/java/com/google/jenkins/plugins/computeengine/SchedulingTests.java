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

package com.google.jenkins.plugins.computeengine;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.api.client.json.GenericJson;
import com.google.api.services.compute.model.Scheduling;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;

public class SchedulingTests {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    private Scheduling getScheduling() {
        ComputeEngineCloud cloud = (ComputeEngineCloud) j.jenkins.clouds.getByName("gce-unit-tests");
        assertEquals(1, cloud.getConfigurations().size());
        return cloud.getConfigurations().get(0).scheduling();
    }

    /**
     * When the previously used {@code preemptible} configuration is loaded, there should be no error in initializing
     * of the new field {@code provisioningType} as well as with building effective `scheduling` configurations. No
     * other provisioning model should be set.
     */
    @Test
    @LocalData
    public void testPreemptibleCompatibility() {
        Scheduling sch = getScheduling();
        assertTrue(sch.getPreemptible());
        assertNull(sch.getProvisioningModel());
        assertNull(sch.get("maxRunDuration"));
        assertNull(sch.getInstanceTerminationAction());
    }

    /**
     * Without any scheduling configuration as well the `scheduling` should still build correctly.
     * Effectively results in Standard VM on GCP side.
     */
    @Test
    @LocalData
    public void testNoProvisioningTypeCompatibility() {
        Scheduling sch = getScheduling();
        assertNull(sch.getPreemptible());
        assertNull(sch.getProvisioningModel());
        assertNull(sch.get("maxRunDuration"));
        assertNull(sch.getInstanceTerminationAction());
    }

    /**
     * When the new {@code provisioningType} is updated by the user, the old {@code preemptible} configuration should
     * be ignored. Even for a given {@code provisioningType} the {@code maxRunDurationSeconds} is still optional.
     */
    @Test
    @LocalData
    public void testProvisioningTypeOverridesPreemptible() {
        Scheduling sch = getScheduling();
        assertNull(sch.getPreemptible());
        assertEquals("SPOT", sch.getProvisioningModel());
        assertNull(sch.get("maxRunDuration"));
        assertNull(sch.getInstanceTerminationAction());
    }

    /**
     * When the standard type vm is being provisioned, the {@code maxRunDurationSeconds} should be applicable if
     * existing. No value should be set for the provisioning model, and GCP will choose the Standard as the
     * default model.
     */
    @Test
    @LocalData
    public void testMaxRunDurationStandard() {
        Scheduling sch = getScheduling();
        GenericJson maxRunDuration = (GenericJson) sch.get("maxRunDuration");
        assertEquals(100L, maxRunDuration.get("seconds"));
        assertNull(sch.getProvisioningModel());
        assertNull(sch.getPreemptible());
        assertEquals("DELETE", sch.getInstanceTerminationAction());
    }

    @Test
    @LocalData
    public void testMaxRunDurationSpot() {
        Scheduling sch = getScheduling();
        GenericJson maxRunDuration = (GenericJson) sch.get("maxRunDuration");
        assertEquals(120L, maxRunDuration.get("seconds"));
        // the `SPOT` word here is the one that is accepted by the GCP API, not an internal representation in the plugin
        assertThat(sch.getProvisioningModel(), is("SPOT"));
        assertNull(sch.getPreemptible());
        assertEquals("DELETE", sch.getInstanceTerminationAction());
    }

    /**
     * When the {@code preemptible} provisioning is configured, the getter and setters of the SDK should be used, and
     * not the generic {@code provisioningModel} field.
     * Also {@code maxRunDuration} should not be set as that will result in error (i.e. preemptible vm doesn't support)
     * even if setting to 0.
     */
    @Test
    @LocalData
    public void testNoMaxRunDurationPreemptible() {
        Scheduling sch = getScheduling();
        assertNull(sch.get("maxRunDuration"));
        assertNull(sch.getProvisioningModel());
        assertTrue(sch.getPreemptible());
        assertNull(sch.getInstanceTerminationAction());
    }
}
