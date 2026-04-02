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
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import com.google.api.services.compute.model.Metadata;
import com.google.cloud.graphite.platforms.plugin.client.ComputeClient;
import com.google.jenkins.plugins.computeengine.client.ClientUtil;
import hudson.model.Node;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.LoggerRule;

public class ComputeEngineCloudCustomMetadataIT {
    private static final Logger log = Logger.getLogger(ComputeEngineCloudCustomMetadataIT.class.getName());

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
            for (Node node : j.jenkins.getNodes()) {
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
    @ConfiguredWithCode("custom-metadata-casc.yml")
    public void testCustomMetadataOnInstance() throws Exception {
        var p = j.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("node('" + LABEL + "') { sh 'sleep 60' }", true));
        var build = p.scheduleBuild2(0);
        await("agent should be created").timeout(Duration.ofMinutes(5)).until(j.jenkins::getNodes, hasSize(1));
        var node = j.jenkins.getNodes().get(0);
        log.info("Agent provisioned: " + node.getNodeName());
        await("agent online").timeout(Duration.ofMinutes(5)).until(() -> {
            var computer = node.toComputer();
            return computer != null && computer.isOnline();
        });
        var instance = client.getInstance(PROJECT_ID, ZONE, node.getNodeName());

        // Assert simple custom metadata
        var simpleValue = instance.getMetadata().getItems().stream()
                .filter(item -> item.getKey().equals("test-key"))
                .map(Metadata.Items::getValue)
                .findFirst();
        assertThat("simple custom metadata 'test-key' should be present", simpleValue.isPresent(), is(true));
        assertThat(simpleValue.get(), is("simple-value"));

        // Assert multiline custom metadata
        var multilineValue = instance.getMetadata().getItems().stream()
                .filter(item -> item.getKey().equals("test-multiline"))
                .map(Metadata.Items::getValue)
                .findFirst();
        assertThat(
                "multiline custom metadata 'test-multiline' should be present", multilineValue.isPresent(), is(true));
        assertThat(multilineValue.get(), is("line1\nline2\nline3"));

        j.assertBuildStatusSuccess(build);
    }
}
