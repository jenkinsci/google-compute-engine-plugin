/*
 * Copyright 2026 CloudBees, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class ShieldedVmConfigurationTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Test
    public void defaultConstructorEnablesAllThree() {
        var c = new ShieldedVmConfiguration();
        assertTrue(c.isEnableSecureBoot());
        assertTrue(c.isEnableVtpm());
        assertTrue(c.isEnableIntegrityMonitoring());
    }

    @Test
    public void allArgsConstructorRoundTripsPartial() {
        var c = new ShieldedVmConfiguration(true, false, false);
        assertTrue(c.isEnableSecureBoot());
        assertFalse(c.isEnableVtpm());
        assertFalse(c.isEnableIntegrityMonitoring());
    }

    @Test
    public void descriptorIsRegistered() {
        assertNotNull(r.jenkins.getDescriptor(ShieldedVmConfiguration.class));
    }

    @Test
    public void equalsAndHashCode() {
        var a = new ShieldedVmConfiguration(true, true, false);
        var b = new ShieldedVmConfiguration(true, true, false);
        var c = new ShieldedVmConfiguration(true, false, false);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertFalse(a.equals(c));
    }
}
