/*
 * Copyright 2020 Google LLC
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

import static com.google.jenkins.plugins.computeengine.InstanceConfigurationTest.ACCELERATOR_COUNT;
import static com.google.jenkins.plugins.computeengine.InstanceConfigurationTest.ACCELERATOR_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.google.api.services.compute.model.AcceleratorType;
import com.google.cloud.graphite.platforms.plugin.client.ComputeClient;
import com.google.common.collect.ImmutableList;
import hudson.util.ListBoxModel;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@WithJenkins
class AcceleratorConfigurationTest {
    private JenkinsRule r;

    @Mock
    private ComputeClient computeClient;

    @BeforeEach
    void init(JenkinsRule rule) {
        r = rule;
    }

    @Test
    void construction() {
        // Test string->int conversion
        AcceleratorConfiguration ac = new AcceleratorConfiguration("type", "2");
        assertEquals(2, (long) ac.gpuCount());

        // Test toString
        assertEquals("type (2)", ac.toString());

        // Test equality
        AcceleratorConfiguration ac2 = new AcceleratorConfiguration("type", "2");
        assertEquals(ac, ac2);
    }

    @Test
    void clientCalls() throws Exception {
        // Set up mock
        List<AcceleratorType> acceleratorTypes = new ArrayList<>();
        acceleratorTypes.add(new AcceleratorType()
                .setName(ACCELERATOR_NAME)
                .setSelfLink(ACCELERATOR_NAME)
                .setMaximumCardsPerInstance(Integer.parseInt(ACCELERATOR_COUNT)));
        acceleratorTypes.add(new AcceleratorType()
                .setName(ACCELERATOR_NAME + "2")
                .setSelfLink(ACCELERATOR_NAME + "2")
                .setMaximumCardsPerInstance(Integer.parseInt(ACCELERATOR_COUNT) + 1));
        when(computeClient.listAcceleratorTypes(anyString(), anyString()))
                .thenReturn(ImmutableList.copyOf(acceleratorTypes));
        AcceleratorConfiguration.DescriptorImpl.setComputeClient(computeClient);

        // Test items returned by dropdown filler
        AcceleratorConfiguration.DescriptorImpl d = new AcceleratorConfiguration.DescriptorImpl();
        ListBoxModel got = d.doFillGpuTypeItems(r.jenkins, "", "", "");
        assertEquals(2, got.size());
    }
}
