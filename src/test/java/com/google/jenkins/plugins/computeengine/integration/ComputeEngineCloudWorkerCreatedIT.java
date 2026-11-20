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
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.NUM_EXECUTORS;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.PROJECT_ID;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.ZONE;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.getLabel;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initClient;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initCloud;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initCredentials;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.instanceConfigurationBuilder;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.teardownResources;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.Metadata;
import com.google.cloud.graphite.platforms.plugin.client.ComputeClient;
import com.google.common.collect.ImmutableList;
import com.google.jenkins.plugins.computeengine.CleanLostNodesWork;
import com.google.jenkins.plugins.computeengine.ComputeEngineCloud;
import com.google.jenkins.plugins.computeengine.InstanceConfiguration;
import hudson.model.Node;
import hudson.model.labels.LabelAtom;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.BuildWatcherExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Integration test suite for {@link ComputeEngineCloud}. This verifies the default case for an
 * instance provisioned with the {@link ComputeEngineCloud}, and that all expected default labels
 * are provisioned properly.
 */
@WithJenkins
@Timeout(value = 45, unit = TimeUnit.MINUTES)
class ComputeEngineCloudWorkerCreatedIT {
    private static final Logger log = Logger.getLogger(ComputeEngineCloudWorkerCreatedIT.class.getName());

    private JenkinsRule jenkinsRule;

    @RegisterExtension
    private static final BuildWatcherExtension bw = new BuildWatcherExtension();

    private static ComputeClient client;
    private static ComputeEngineCloud cloud;
    private static final Map<String, String> label = getLabel(ComputeEngineCloudWorkerCreatedIT.class);
    private static InstanceConfiguration instanceConfiguration;

    @BeforeEach
    void init(JenkinsRule rule) throws Exception {
        log.info("init");
        jenkinsRule = rule;
        initCredentials(jenkinsRule);
        cloud = initCloud(jenkinsRule);
        client = initClient(jenkinsRule, label, log);

        instanceConfiguration = instanceConfigurationBuilder()
                .numExecutorsStr(NUM_EXECUTORS)
                .labels(LABEL)
                .oneShot(false)
                .createSnapshot(false)
                .template(NULL_TEMPLATE)
                .googleLabels(label)
                .cloud(cloud)
                .build();

        cloud.setConfigurations(ImmutableList.of(instanceConfiguration));
    }

    @AfterEach
    void teardown() throws IOException {
        teardownResources(client, label, log);
    }

    @Test
    void smokes() throws IOException, ExecutionException, InterruptedException {
        var planned = cloud.provision(new LabelAtom(LABEL), 1);
        planned.iterator().next().future.get();
        var instance = cloud.getClient()
                .getInstance(PROJECT_ID, ZONE, planned.iterator().next().displayName);

        assertEquals(1, planned.size(), "one instance should be provisioned");
        /* There are 7 labels,
         * actual code: jenkins_cloud_id, jenkins_config_name, jenkins_node_last_refresh
         *  jenkins_server_url, jenkins_cloud_name
         * test code: <current class name>, user
         * Total 7 labels here.
         * */
        assertEquals(7, instance.getLabels().size(), "GCP VM should have 7 labels");
        assertEquals(
                instanceConfiguration.getNamePrefix(),
                instance.getLabels().get(ComputeEngineCloud.CONFIG_LABEL_KEY),
                "GCP VM name starts with the prefix configured");
        // This is an important label to be present on the VM as it helps in `CleanLostNodesWork`
        assertEquals(
                cloud.getInstanceId(),
                instance.getLabels().get(ComputeEngineCloud.CLOUD_ID_LABEL_KEY),
                "GCP VM label has the cloud instanceId as value");
        assertEquals("RUNNING", instance.getStatus(), "GCP VM is running ");

        // Verify that the guest-attributes are enabled on the VM
        Optional<String> guestAttributes = instance.getMetadata().getItems().stream()
                .filter(item -> item.getKey().equals(InstanceConfiguration.GUEST_ATTRIBUTES_METADATA_KEY))
                .map(Metadata.Items::getValue)
                .findFirst();
        assertTrue(guestAttributes.isPresent());
        assertEquals("TRUE", guestAttributes.get());
    }

    @Test
    void testWorkerCanExecuteBuild() throws Exception {
        var p = jenkinsRule.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("node('" + LABEL + "') { sh 'date' }", true));
        var r = jenkinsRule.buildAndAssertSuccess(p);
        assertEquals(1, jenkinsRule.jenkins.getNodes().size());
        Node node = jenkinsRule.jenkins.getNodes().get(0);
        var instance = client.getInstance(PROJECT_ID, ZONE, node.getNodeName());
        jenkinsRule.assertLogContains("Running on " + instance.getName(), r);
    }

    @Issue("https://github.com/jenkinsci/google-compute-engine-plugin/issues/512")
    @Test
    void testWorkerCreatedWithLatestLastRefresh() throws Exception {
        // create node without running any job
        var planned = cloud.provision(new LabelAtom(LABEL), 1);
        planned.iterator().next().future.get();
        var instance = client.getInstance(PROJECT_ID, ZONE, planned.iterator().next().displayName);
        var lastRefreshInstance1 = getLastRefresh(instance);
        // remove the node from jenkins
        Jenkins.get().removeNode(Jenkins.get().getNode(instance.getName()));

        // run a job, creates another instance
        var p = jenkinsRule.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("node('" + LABEL + "') { sh 'date' }", true));
        var r = jenkinsRule.buildAndAssertSuccess(p);
        assertEquals(1, jenkinsRule.jenkins.getNodes().size());
        var instance2 = client.getInstance(
                PROJECT_ID, ZONE, jenkinsRule.jenkins.getNodes().get(0).getNodeName());
        assertNotEquals(instance.getName(), instance2.getName());
        var lastRefreshInstance2 = getLastRefresh(instance2);
        assertThat(lastRefreshInstance2, isAfter(lastRefreshInstance1));

        // stop the instance
        Jenkins.get().removeNode(Jenkins.get().getNode(instance2.getName()));

        // create one more instance manually
        planned = cloud.provision(new LabelAtom(LABEL), 1);
        planned.iterator().next().future.get();
        var instance3 = client.getInstance(PROJECT_ID, ZONE, planned.iterator().next().displayName);
        var lastRefreshInstance3 = getLastRefresh(instance3);
        assertThat(lastRefreshInstance3, isAfter(lastRefreshInstance2));
    }

    private OffsetDateTime getLastRefresh(Instance instance) {
        return LocalDateTime.parse(
                        instance.getLabels().get(CleanLostNodesWork.NODE_IN_USE_LABEL_KEY),
                        CleanLostNodesWork.LAST_REFRESH_FORMATTER)
                .atOffset(ZoneOffset.UTC);
    }

    // Custom isAfter matcher for OffsetDateTime
    public static TypeSafeMatcher<OffsetDateTime> isAfter(OffsetDateTime expected) {
        return new TypeSafeMatcher<>() {
            @Override
            protected boolean matchesSafely(OffsetDateTime actual) {
                return actual.isAfter(expected);
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("a date-time that is after ").appendValue(expected);
            }

            @Override
            protected void describeMismatchSafely(OffsetDateTime actual, Description mismatchDescription) {
                mismatchDescription.appendText("was ").appendValue(actual);
            }
        };
    }
}
