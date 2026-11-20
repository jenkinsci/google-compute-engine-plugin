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
import io.jenkins.plugins.casc.misc.junit.jupiter.WithJenkinsConfiguredWithCode;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.LogRecorder;
import org.jvnet.hudson.test.junit.jupiter.BuildWatcherExtension;

@WithJenkinsConfiguredWithCode
@Timeout(value = 45, unit = TimeUnit.MINUTES)
class ComputeEngineCloudShieldedVmIT {
    private static final Logger log = Logger.getLogger(ComputeEngineCloudShieldedVmIT.class.getName());

    private JenkinsConfiguredWithCodeRule j;

    @AutoClose
    private static final LogRecorder logRule =
            new LogRecorder().record("com.google.jenkins.plugins.computeengine", Level.FINEST);

    @RegisterExtension
    private static final BuildWatcherExtension bw = new BuildWatcherExtension();

    private ComputeClient client;

    @BeforeEach
    void init(JenkinsConfiguredWithCodeRule rule) throws Exception {
        log.info("init");
        j = rule;
        initCredentials(j);
        client = ClientUtil.getClientFactory(j.jenkins, PROJECT_ID).computeClient();
    }

    @AfterEach
    void teardown() {
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
    void testShieldedVmOnInstance() throws Exception {
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
