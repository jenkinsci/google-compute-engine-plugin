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

package com.google.jenkins.plugins.computeengine.integration;

import static com.google.jenkins.plugins.computeengine.integration.ITUtil.getLabel;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initClient;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initCredentials;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.teardownResources;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.api.services.compute.model.Image;
import com.google.cloud.graphite.platforms.plugin.client.ComputeClient;
import java.io.IOException;
import java.util.Map;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/** Integration test suite for {@link ComputeClient}. */
@WithJenkins
class ComputeClientIT {
    private static final Logger log = Logger.getLogger(ComputeClientIT.class.getName());

    private static final Map<String, String> label = getLabel(ComputeClientIT.class);
    private static ComputeClient client;

    private static JenkinsRule jenkinsRule;

    @BeforeAll
    static void init(JenkinsRule rule) throws Exception {
        log.info("init");
        jenkinsRule = rule;
        initCredentials(jenkinsRule);
        client = initClient(jenkinsRule, label, log);
    }

    @AfterAll
    static void teardown() throws IOException {
        teardownResources(client, label, log);
    }

    @Test
    void testGetImage() throws Exception {
        Image image = client.getImage("debian-cloud", "debian-12-bookworm-v20241210");
        assertNotNull(image);
        assertEquals("READY", image.getStatus());
    }
}
