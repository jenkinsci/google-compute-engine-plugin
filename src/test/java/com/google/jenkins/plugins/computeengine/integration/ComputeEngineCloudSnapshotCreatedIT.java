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

import static com.google.jenkins.plugins.computeengine.integration.ITUtil.NULL_TEMPLATE;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.NUM_EXECUTORS;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.PROJECT_ID;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.SNAPSHOT_LABEL;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.SNAPSHOT_TIMEOUT;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.execute;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.getLabel;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initClient;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initCloud;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initCredentials;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.instanceConfigurationBuilder;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.teardownResources;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import com.google.api.services.compute.model.Snapshot;
import com.google.cloud.graphite.platforms.plugin.client.ComputeClient;
import com.google.common.collect.ImmutableList;
import com.google.jenkins.plugins.computeengine.ComputeEngineCloud;
import com.google.jenkins.plugins.computeengine.InstanceConfiguration;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Node;
import hudson.model.Result;
import hudson.model.labels.LabelAtom;
import hudson.tasks.Builder;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.BuildWatcherExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Integration test suite for {@link ComputeEngineCloud}. Verifies that when configured to use one
 * shot instances and create snapshots, a snapshot will be created upon instance termination when a
 * build fails.
 */
@WithJenkins
@Timeout(value = 45, unit = TimeUnit.MINUTES)
class ComputeEngineCloudSnapshotCreatedIT {
    private static final Logger log = Logger.getLogger(ComputeEngineCloudSnapshotCreatedIT.class.getName());

    private static JenkinsRule jenkinsRule;

    @RegisterExtension
    private static final BuildWatcherExtension bw = new BuildWatcherExtension();

    private static ComputeClient client;
    private static final Map<String, String> label = getLabel(ComputeEngineCloudSnapshotCreatedIT.class);
    private static Snapshot createdSnapshot = null;

    @BeforeAll
    @Timeout(value = 45, unit = TimeUnit.MINUTES)
    static void init(JenkinsRule rule) throws Exception {
        log.info("init");
        jenkinsRule = rule;
        initCredentials(jenkinsRule);
        ComputeEngineCloud cloud = initCloud(jenkinsRule);
        client = initClient(jenkinsRule, label, log);

        InstanceConfiguration instanceConfiguration = instanceConfigurationBuilder()
                .numExecutorsStr(NUM_EXECUTORS)
                .labels(SNAPSHOT_LABEL)
                .oneShot(true)
                .createSnapshot(true)
                .template(NULL_TEMPLATE)
                .googleLabels(label)
                .build();

        cloud.setConfigurations(ImmutableList.of(instanceConfiguration));
        assertTrue(cloud.getInstanceConfigurationByDescription(instanceConfiguration.getDescription())
                .isCreateSnapshot());

        FreeStyleProject project = jenkinsRule.createFreeStyleProject();
        Builder step = execute(Commands.EXIT, "1");
        project.getBuildersList().add(step);
        project.setAssignedLabel(new LabelAtom(SNAPSHOT_LABEL));

        FreeStyleBuild build = jenkinsRule.assertBuildStatus(Result.FAILURE, project.scheduleBuild2(0));
        Node worker = build.getBuiltOn();
        assertNotNull(worker);

        // Need time for one-shot instance to terminate and create the snapshot
        await().timeout(SNAPSHOT_TIMEOUT, TimeUnit.SECONDS)
                .pollInterval(10, TimeUnit.SECONDS)
                .until(() -> jenkinsRule.jenkins.getNode(worker.getNodeName()) == null);

        createdSnapshot = client.getSnapshot(PROJECT_ID, worker.getNodeName());
    }

    @AfterAll
    static void teardown() throws IOException {
        if (createdSnapshot != null) {
            client.deleteSnapshotAsync(PROJECT_ID, createdSnapshot.getName());
        }
        teardownResources(client, label, log);
    }

    /** Tests snapshot is created when we have failure builds for given node */
    @Test
    void testSnapshotCreatedNotNull() {
        assertNotNull(createdSnapshot);
    }

    @Test
    void testSnapshotCreatedStatusReady() {
        assertEquals("READY", createdSnapshot.getStatus());
    }

    @Test
    void testSnapshotCreatedOneShotInstanceDeleted() {
        await().timeout(SNAPSHOT_TIMEOUT, TimeUnit.SECONDS)
                .until(() -> client.listInstancesWithLabel(PROJECT_ID, label).isEmpty());
    }
}
