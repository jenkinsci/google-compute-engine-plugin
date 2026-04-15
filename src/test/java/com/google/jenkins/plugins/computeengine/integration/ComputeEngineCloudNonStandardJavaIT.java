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

import static com.google.jenkins.plugins.computeengine.integration.ITUtil.BOOT_DISK_IMAGE_NAME;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.LABEL;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.NULL_TEMPLATE;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.NUM_EXECUTORS;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.PROJECT_ID;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.ZONE;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.getLabel;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initClient;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initCloud;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initCredentials;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.instanceConfigurationBuilder;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.teardownResources;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.windows;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeFalse;

import com.google.cloud.graphite.platforms.plugin.client.ComputeClient;
import com.google.common.collect.ImmutableList;
import com.google.jenkins.plugins.computeengine.ComputeEngineCloud;
import com.google.jenkins.plugins.computeengine.InstanceConfiguration;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Integration test suite for {@link ComputeEngineCloudNonStandardJavaIT}. Verifies that instances
 * can be created using a non-standard Java executable path.
 */
public class ComputeEngineCloudNonStandardJavaIT {

    private static final String NON_STANDARD_JAVA_PATH = "/usr/bin/non-standard-java";

    private static final Logger log = Logger.getLogger(ComputeEngineCloudNonStandardJavaIT.class.getName());

    @ClassRule
    public static Timeout timeout = new Timeout(5, TimeUnit.MINUTES);

    @ClassRule
    public static JenkinsRule jenkinsRule = new JenkinsRule();

    @ClassRule
    public static BuildWatcher bw = new BuildWatcher();

    private static ComputeClient client;
    private static final Map<String, String> label = getLabel(ComputeEngineCloudNonStandardJavaIT.class);

    @BeforeClass
    public static void init() throws Exception {
        assumeFalse(windows);
        log.info("init");
        initCredentials(jenkinsRule);
        ComputeEngineCloud cloud = initCloud(jenkinsRule);
        client = initClient(jenkinsRule, label, log);
        InstanceConfiguration instanceConfiguration = instanceConfigurationBuilder()
                .bootDiskSourceImageName(BOOT_DISK_IMAGE_NAME + "-non-standard-java")
                .numExecutorsStr(NUM_EXECUTORS)
                .labels(LABEL)
                .oneShot(false)
                .createSnapshot(false)
                .template(NULL_TEMPLATE)
                .javaExecPath(NON_STANDARD_JAVA_PATH)
                .googleLabels(label)
                .build();
        cloud.setConfigurations(ImmutableList.of(instanceConfiguration));
    }

    @AfterClass
    public static void teardown() throws IOException {
        teardownResources(client, label, log);
    }

    @Test
    public void testBuildOnNonStandardJavaAgent() throws Exception {
        var p = jenkinsRule.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("node('" + LABEL + "') { sh 'date' }", true));
        var r = jenkinsRule.buildAndAssertSuccess(p);
        assertEquals(1, jenkinsRule.jenkins.getNodes().size());
        var instance = client.getInstance(
                PROJECT_ID, ZONE, jenkinsRule.jenkins.getNodes().get(0).getNodeName());
        jenkinsRule.assertLogContains("Running on " + instance.getName(), r);
        jenkinsRule.waitUntilNoActivity();
    }
}
