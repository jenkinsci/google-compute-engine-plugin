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

import static com.google.cloud.graphite.platforms.plugin.client.util.ClientUtil.nameFromSelfLink;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.LABEL;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.NUM_EXECUTORS;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.PROJECT_ID;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.ZONE;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.createTemplate;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.format;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.getLabel;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initClient;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initCloud;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initCredentials;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.instanceConfigurationBuilder;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.teardownResources;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.InstanceTemplate;
import com.google.cloud.graphite.platforms.plugin.client.ComputeClient;
import com.google.common.collect.ImmutableList;
import com.google.jenkins.plugins.computeengine.ComputeEngineCloud;
import hudson.model.labels.LabelAtom;
import hudson.slaves.NodeProvisioner.PlannedNode;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Integration test suite for {@link ComputeEngineCloud}. Verifies that an instance can be created
 * from a template without any labels configured for it, and that the ComputeEngineCloud label is
 * still present on the created instance.
 */
@WithJenkins
@Timeout(value = 45, unit = TimeUnit.MINUTES)
class ComputeEngineCloudTemplateNoGoogleLabelsIT {
    private static final Logger log = Logger.getLogger(ComputeEngineCloudTemplateNoGoogleLabelsIT.class.getName());

    private static final String TEMPLATE = format("projects/%s/global/instanceTemplates/test-template-no-labels");

    private static JenkinsRule jenkinsRule;

    private static ComputeClient client;
    private static final Map<String, String> label = getLabel(ComputeEngineCloudTemplateNoGoogleLabelsIT.class);
    private static ComputeEngineCloud cloud;
    private static Instance instance;

    @BeforeAll
    @Timeout(value = 45, unit = TimeUnit.MINUTES)
    static void init(JenkinsRule rule) throws Exception {
        log.info("init");
        jenkinsRule = rule;
        initCredentials(jenkinsRule);
        cloud = initCloud(jenkinsRule);
        client = initClient(jenkinsRule, label, log);

        cloud.setConfigurations(ImmutableList.of(instanceConfigurationBuilder()
                .numExecutorsStr(NUM_EXECUTORS)
                .labels(LABEL)
                .oneShot(false)
                .createSnapshot(false)
                .template(TEMPLATE)
                .googleLabels(label)
                .build()));

        InstanceTemplate instanceTemplate = createTemplate(null, TEMPLATE);
        client.insertTemplateAsync(cloud.getProjectId(), instanceTemplate);
        Collection<PlannedNode> planned = cloud.provision(new LabelAtom(LABEL), 1);

        String name = planned.iterator().next().displayName;
        planned.iterator().next().future.get();
        instance = client.getInstance(PROJECT_ID, ZONE, name);
    }

    @AfterAll
    static void teardown() throws IOException {
        try {
            client.deleteTemplateAsync(cloud.getProjectId(), nameFromSelfLink(TEMPLATE));
        } catch (Exception e) {
            // noop
        }

        teardownResources(client, label, log);
    }

    @Test
    void testTemplateNoGoogleLabelsCloudIdLabelKeyAndValue() {
        assertEquals(cloud.getInstanceId(), instance.getLabels().get(ComputeEngineCloud.CLOUD_ID_LABEL_KEY));
    }

    @Test
    void testTemplateNoGoogleLabelsStatusRunning() {
        assertEquals("RUNNING", instance.getStatus());
    }
}
