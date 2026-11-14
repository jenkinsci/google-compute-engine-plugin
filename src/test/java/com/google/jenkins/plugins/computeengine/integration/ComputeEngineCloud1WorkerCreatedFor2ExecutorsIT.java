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

import static com.google.jenkins.plugins.computeengine.integration.ITUtil.LABEL;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.NULL_TEMPLATE;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.PROJECT_ID;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.getLabel;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initClient;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initCloud;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initCredentials;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.instanceConfigurationBuilder;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.teardownResources;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.cloud.graphite.platforms.plugin.client.ComputeClient;
import com.google.common.collect.ImmutableList;
import com.google.jenkins.plugins.computeengine.ComputeEngineCloud;
import hudson.model.labels.LabelAtom;
import hudson.slaves.NodeProvisioner.PlannedNode;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
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
 * Integration test suite for {@link ComputeEngineCloud}. Verifies that only 1 worker is created
 * when configured to support multiple executors if the second executor is not required.
 */
@WithJenkins
@Timeout(value = 15, unit = TimeUnit.MINUTES)
class ComputeEngineCloud1WorkerCreatedFor2ExecutorsIT {
    private static final Logger log = Logger.getLogger(ComputeEngineCloud1WorkerCreatedFor2ExecutorsIT.class.getName());

    private static final String MULTIPLE_NUM_EXECUTORS = "2";

    private static JenkinsRule jenkinsRule;

    private static ComputeClient client;
    private static final Map<String, String> label = getLabel(ComputeEngineCloud1WorkerCreatedFor2ExecutorsIT.class);

    @BeforeAll
    static void init(JenkinsRule rule) throws Exception {
        log.info("init");
        jenkinsRule = rule;
        initCredentials(jenkinsRule);
        ComputeEngineCloud cloud = initCloud(jenkinsRule);
        client = initClient(jenkinsRule, label, log);

        cloud.setConfigurations(ImmutableList.of(instanceConfigurationBuilder()
                .numExecutorsStr(MULTIPLE_NUM_EXECUTORS)
                .labels(LABEL)
                .oneShot(false)
                .createSnapshot(false)
                .template(NULL_TEMPLATE)
                .googleLabels(label)
                .build()));

        Collection<PlannedNode> planned = cloud.provision(new LabelAtom(LABEL), 2);
        planned.iterator().next().future.get();
    }

    @AfterAll
    static void teardown() throws IOException {
        teardownResources(client, label, log);
    }

    @Test
    void test1WorkerCreatedFor2ExecutorsStatusRunning() throws IOException {
        // assert on jenkins side
        assertEquals(1, jenkinsRule.jenkins.getNodes().size());
        assertEquals(2, jenkinsRule.jenkins.getNodes().get(0).getNumExecutors());

        // assert on gcp side
        assertEquals(1, new ArrayList<>(client.listInstancesWithLabel(PROJECT_ID, label)).size());
        await().timeout(Duration.ofMinutes(2))
                .until(
                        () -> client.listInstancesWithLabel(PROJECT_ID, label)
                                .get(0)
                                .getStatus(),
                        is("RUNNING"));
    }
}
