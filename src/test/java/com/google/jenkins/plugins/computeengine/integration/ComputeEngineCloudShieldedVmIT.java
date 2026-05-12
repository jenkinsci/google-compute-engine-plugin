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
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.PROJECT_ID;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.TEST_TIMEOUT_MULTIPLIER;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.ZONE;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initCredentials;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import com.google.cloud.graphite.platforms.plugin.client.ComputeClient;
import com.google.jenkins.plugins.computeengine.client.ClientUtil;
import hudson.model.Node;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.LoggerRule;

public class ComputeEngineCloudShieldedVmIT {
    private static final Logger log = Logger.getLogger(ComputeEngineCloudShieldedVmIT.class.getName());

    @ClassRule
    public static Timeout timeout = new Timeout(15L * TEST_TIMEOUT_MULTIPLIER, TimeUnit.MINUTES);

    @Rule
    public JenkinsConfiguredWithCodeRule j = new JenkinsConfiguredWithCodeRule();

    @ClassRule
    public static LoggerRule logRule =
            new LoggerRule().record("com.google.jenkins.plugins.computeengine", Level.FINEST);

    @ClassRule
    public static BuildWatcher bw = new BuildWatcher();

    private ComputeClient client;

    @Before
    public void init() throws Exception {
        log.info("init");
        initCredentials(j);
        client = ClientUtil.getClientFactory(j.jenkins, PROJECT_ID).computeClient();
    }

    @After
    public void teardown() throws IOException {
        log.info("teardown");
        if (client != null) {
            for (var node : j.jenkins.getNodes()) {
                try {
                    log.info("deleting instance: " + node.getNodeName());
                    client.terminateInstanceAsync(PROJECT_ID, ZONE, node.getNodeName());
                } catch (Exception e) {
                    log.warning("Error deleting instance " + node.getNodeName() + ": " + e.getMessage());
                }
            }
        }
    }

    @Test
    @ConfiguredWithCode("shielded-vm-casc.yml")
    public void testShieldedVmOnInstance() throws Exception {
        var pipelineScript = "node('" + LABEL + "') { semaphore 'shieldedVm' }";
        var p = j.createProject(WorkflowJob.class, "shielded-vm-test");
        p.setDefinition(new CpsFlowDefinition(pipelineScript, true));

        var build = p.scheduleBuild2(0).waitForStart();
        SemaphoreStep.waitForStart("shieldedVm/1", build);

        var buildLog = j.getLog(build);
        var workerNodeName = j.jenkins.getNodes().stream()
                .map(Node::getNodeName)
                .filter(name -> buildLog.contains("Running on " + name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Build log does not contain 'Running on <agent>'"));
        log.info("Build paused on node: " + workerNodeName);

        var instance = client.getInstance(PROJECT_ID, ZONE, workerNodeName);
        var shieldedConfig = instance.getShieldedInstanceConfig();
        assertThat("shieldedInstanceConfig should be set on the provisioned instance", shieldedConfig, notNullValue());
        assertThat("Secure Boot should be enabled", shieldedConfig.getEnableSecureBoot(), is(true));
        assertThat("vTPM should be enabled", shieldedConfig.getEnableVtpm(), is(true));
        assertThat("Integrity Monitoring should be enabled", shieldedConfig.getEnableIntegrityMonitoring(), is(true));

        SemaphoreStep.success("shieldedVm/1", null);
        j.waitForCompletion(build);
        j.assertBuildStatusSuccess(build);
    }
}
