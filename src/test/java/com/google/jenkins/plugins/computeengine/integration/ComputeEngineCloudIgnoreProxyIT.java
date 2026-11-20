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
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.execute;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.getLabel;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initClient;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initCloud;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initCredentials;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.instanceConfigurationBuilder;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.teardownResources;
import static org.junit.jupiter.api.Assertions.*;

import com.google.cloud.graphite.platforms.plugin.client.ComputeClient;
import com.google.common.collect.ImmutableList;
import com.google.jenkins.plugins.computeengine.ComputeEngineCloud;
import com.google.jenkins.plugins.computeengine.ComputeEngineInstance;
import com.google.jenkins.plugins.computeengine.InstanceConfiguration;
import hudson.ProxyConfiguration;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import hudson.model.labels.LabelAtom;
import hudson.tasks.Builder;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import jenkins.model.Jenkins;
import lombok.extern.java.Log;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.BuildWatcherExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Integration test suite for {@link ComputeEngineCloud}. Verifies that when the ignore proxy flag
 * is set to true, connections to GCE VMs bypass the proxy, and when set to false the proxy is used.
 */
@Log
@WithJenkins
@Timeout(value = 30, unit = TimeUnit.MINUTES)
class ComputeEngineCloudIgnoreProxyIT {

    private static JenkinsRule jenkinsRule;

    @RegisterExtension
    private static final BuildWatcherExtension bw = new BuildWatcherExtension();

    private static ComputeClient client;
    private static final Map<String, String> label = getLabel(ComputeEngineCloudIgnoreProxyIT.class);
    private static ComputeEngineCloud cloud;

    @BeforeAll
    static void init(JenkinsRule rule) throws Exception {
        log.info("init");
        jenkinsRule = rule;
        initCredentials(jenkinsRule);
        cloud = initCloud(jenkinsRule);
        client = initClient(jenkinsRule, label, log);
        Jenkins jenkins = jenkinsRule.getInstance();
        jenkins.proxy = new ProxyConfiguration("127.0.0.1", 8080);
        jenkins.proxy.save();
        jenkins.save();
        log.info("init completed");
    }

    @AfterAll
    static void teardown() throws IOException {
        teardownResources(client, label, log);
    }

    private static InstanceConfiguration getConfiguration(boolean ignoreProxy, String proxyLabel) {
        return instanceConfigurationBuilder()
                .description("IgnoreProxy=" + ignoreProxy)
                .numExecutorsStr(NUM_EXECUTORS)
                .labels(proxyLabel)
                .template(NULL_TEMPLATE)
                .googleLabels(label)
                .oneShot(true)
                .ignoreProxy(ignoreProxy)
                .build();
    }

    private static FreeStyleProject createProject(String nodeLabel) throws IOException {
        var project = jenkinsRule.createFreeStyleProject(nodeLabel);
        Builder step = execute(Commands.ECHO, "works");
        project.getBuildersList().add(step);
        project.setAssignedLabel(new LabelAtom(nodeLabel));
        return project;
    }

    @Test
    void testWorkerIgnoringProxy() throws Exception {
        String identifier = "ignoreProxy";
        var instanceConfig = getConfiguration(true, identifier);
        cloud.setConfigurations(ImmutableList.of(instanceConfig));
        jenkinsRule.getInstance().save();

        assertTrue(cloud.getInstanceConfigurationByDescription(instanceConfig.getDescription())
                .isIgnoreProxy());

        var project = createProject(identifier);
        Future<FreeStyleBuild> build = project.scheduleBuild2(0);
        ComputeEngineInstance node = (ComputeEngineInstance) build.get().getBuiltOn();
        assertTrue(node.isIgnoreProxy());
        jenkinsRule.assertBuildStatus(Result.SUCCESS, build.get());
    }

    // Due to the proxy configured for Jenkins, the build fails because it is not able to connect to
    // the node through the proxy.
    @Test
    void testWorkerNotIgnoringProxyFails() throws Exception {
        String identifier = "notIgnoreProxy";
        var instanceConfig = getConfiguration(false, identifier);
        cloud.setConfigurations(ImmutableList.of(instanceConfig));
        jenkinsRule.getInstance().save();
        assertFalse(cloud.getInstanceConfigurationByDescription(instanceConfig.getDescription())
                .isIgnoreProxy());
        var project = createProject(identifier);
        Future<FreeStyleBuild> build = project.scheduleBuild2(0);
        assertThrows(TimeoutException.class, () -> build.get(300, TimeUnit.SECONDS));
    }
}
