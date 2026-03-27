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

package com.google.jenkins.plugins.computeengine.integration;

import static com.google.jenkins.plugins.computeengine.integration.ITUtil.LABEL;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.NULL_TEMPLATE;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.NUM_EXECUTORS;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.PROJECT_ID;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.TEST_TIMEOUT_MULTIPLIER;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.getLabel;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initClient;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initCloud;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initCredentials;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.instanceConfigurationBuilder;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.teardownResources;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import com.google.cloud.graphite.platforms.plugin.client.ComputeClient;
import com.google.common.collect.ImmutableList;
import com.google.jenkins.plugins.computeengine.ComputeEngineCloud;
import com.google.jenkins.plugins.computeengine.InstanceConfiguration;
import com.google.jenkins.plugins.computeengine.MinimumInstanceChecker;
import hudson.model.Node;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Integration test for minimum instances and spare instances. Verifies that:
 * <ol>
 *   <li>Spare instances are provisioned on startup</li>
 *   <li>A new spare is provisioned when a build consumes one</li>
 *   <li>The replacement spare has a different instance name</li>
 * </ol>
 */
public class ComputeEngineCloudMinimumInstancesIT {
    private static final Logger log = Logger.getLogger(ComputeEngineCloudMinimumInstancesIT.class.getName());

    @ClassRule
    public static Timeout timeout = new Timeout(15L * TEST_TIMEOUT_MULTIPLIER, TimeUnit.MINUTES);

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    @ClassRule
    public static BuildWatcher bw = new BuildWatcher();

    private static ComputeClient client;
    private static ComputeEngineCloud cloud;
    private static final Map<String, String> label = getLabel(ComputeEngineCloudMinimumInstancesIT.class);
    private static InstanceConfiguration instanceConfiguration;

    @Before
    public void init() throws Exception {
        log.info("init");
        initCredentials(jenkinsRule);
        cloud = initCloud(jenkinsRule);
        client = initClient(jenkinsRule, label, log);
        instanceConfiguration = instanceConfigurationBuilder()
                .numExecutorsStr(NUM_EXECUTORS)
                .labels(LABEL)
                .oneShot(true)
                .createSnapshot(false)
                .template(NULL_TEMPLATE)
                .minimumNumberOfInstances(2)
                .minimumNumberOfSpareInstances(2)
                .terminateIdleDuringShutdown(true)
                .googleLabels(label)
                .cloud(cloud)
                .build();
        cloud.setConfigurations(ImmutableList.of(instanceConfiguration));
    }

    @After
    public void teardown() throws IOException {
        teardownResources(client, label, log);
    }

    @Test
    public void testSpareInstancesProvisionedAndReplenished() throws Exception {
        assertThat(jenkinsRule.jenkins.getNodes(), hasSize(0));
        MinimumInstanceChecker.checkForMinimumInstances();
        await("Expected 2 nodes for minimumNumberOfSpareInstances=2")
                .timeout(Duration.ofMinutes(2))
                .until(() -> jenkinsRule.jenkins.getNodes(), hasSize(2));
        assertThat("Expected 2 GCE instances", client.listInstancesWithLabel(PROJECT_ID, label), hasSize(2));
        var initialNames =
                jenkinsRule.jenkins.getNodes().stream().map(Node::getNodeName).collect(Collectors.toSet());
        log.info("Initial spare instances: " + initialNames);

        // Run a short build — this consumes a spare, triggering provisioning of a replacement
        var p = jenkinsRule.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("node('" + LABEL + "') { sh 'echo ok' }", true));
        jenkinsRule.buildAndAssertSuccess(p);
        // wait until the old instance is gone and new instance appears
        // TODO assert that the new instance created is not the one used by the build
        await().timeout(Duration.ofMinutes(5))
                .until(
                        () -> jenkinsRule.jenkins.getNodes().stream()
                                .map(Node::getNodeName)
                                .collect(Collectors.toSet()),
                        is(not(initialNames)));
    }
}
