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

import static com.google.cloud.graphite.platforms.plugin.client.util.ClientUtil.nameFromSelfLink;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.LABEL;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.PROJECT_ID;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.TEST_TIMEOUT_MULTIPLIER;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initCredentials;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import com.google.cloud.graphite.platforms.plugin.client.ComputeClient;
import com.google.jenkins.plugins.computeengine.InstanceConfiguration;
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

public class ComputeEngineCloudFallbackZoneIT {
    private static final String PRIMARY_ZONE = "us-east1-b";
    private static final String FALLBACK_ZONE = "us-east1-c";

    private static final Logger log = Logger.getLogger(ComputeEngineCloudFallbackZoneIT.class.getName());

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
        InstanceConfiguration.simulateCapacityExhaustion = false;
        log.info("teardown");
        if (client != null) {
            for (Node node : j.jenkins.getNodes()) {
                for (var zone : new String[] {PRIMARY_ZONE, FALLBACK_ZONE}) {
                    try {
                        client.terminateInstanceAsync(PROJECT_ID, zone, node.getNodeName());
                    } catch (Exception e) {
                        // instance may not be in this zone — ignore
                    }
                }
            }
        }
    }

    @Test
    @ConfiguredWithCode("fallback-zone-casc.yml")
    public void testProvisioningFallsBackToSecondZoneOnCapacityExhaustion() throws Exception {
        InstanceConfiguration.simulateCapacityExhaustion = true;

        var p = j.createProject(WorkflowJob.class, "fallback-zone-test");
        p.setDefinition(new CpsFlowDefinition("node('" + LABEL + "') { semaphore 'fallbackZone' }", true));

        var build = p.scheduleBuild2(0).waitForStart();
        SemaphoreStep.waitForStart("fallbackZone/1", build);

        var buildLog = j.getLog(build);
        var workerNodeName = j.jenkins.getNodes().stream()
                .map(Node::getNodeName)
                .filter(name -> buildLog.contains("Running on " + name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Build log does not contain 'Running on <agent>'"));
        log.info("Agent provisioned: " + workerNodeName);

        var instance = client.getInstance(PROJECT_ID, FALLBACK_ZONE, workerNodeName);
        assertThat(
                "instance should have landed in the fallback zone, not the primary",
                nameFromSelfLink(instance.getZone()),
                is(FALLBACK_ZONE));
        assertThat(nameFromSelfLink(instance.getZone()), is(not(PRIMARY_ZONE)));

        SemaphoreStep.success("fallbackZone/1", null);
        j.waitForCompletion(build);
        j.assertBuildStatusSuccess(build);
    }
}
