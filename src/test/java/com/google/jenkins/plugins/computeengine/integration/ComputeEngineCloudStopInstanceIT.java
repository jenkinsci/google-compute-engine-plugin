/*
 * Copyright 2026 AI Fabrics LLC
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
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.TEST_TIMEOUT_MULTIPLIER;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.ZONE;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.getLabel;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initClient;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initCloud;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initCredentials;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.instanceConfigurationBuilder;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.teardownResources;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.cloud.graphite.platforms.plugin.client.ComputeClient;
import com.google.common.collect.ImmutableList;
import com.google.jenkins.plugins.computeengine.ComputeEngineCloud;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.labels.LabelAtom;
import hudson.slaves.SlaveComputer;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import lombok.extern.java.Log;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;

/**
 * Integration test suite for {@link ComputeEngineCloud}. Provisions an agent and then terminates its
 * VM with the GCE {@code instances.stop} call -- as opposed to deleting it, which is what the plugin
 * itself does -- to verify how the plugin reacts to an agent VM that is stopped out of band.
 *
 * <p>A stopped instance still exists in GCE, it just moves to {@code TERMINATED}. The agent is
 * therefore unreachable while the {@link hudson.model.Node} remains registered with Jenkins, and the
 * test asserts that Jenkins eventually drops that node.
 */
@Log
public class ComputeEngineCloudStopInstanceIT {

    private static final String RUNNING = "RUNNING";
    private static final String TERMINATED = "TERMINATED";
    private static final String DELETED = "DELETED";

    @ClassRule
    public static Timeout timeout = new Timeout(15L * TEST_TIMEOUT_MULTIPLIER, TimeUnit.MINUTES);

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsRule();

    @ClassRule
    public static BuildWatcher bw = new BuildWatcher();

    @ClassRule
    public static LoggerRule logRule =
            new LoggerRule().record("com.google.jenkins.plugins.computeengine", Level.FINEST);

    private static ComputeClient client;
    private static ComputeEngineCloud cloud;
    private static final Map<String, String> label = getLabel(ComputeEngineCloudStopInstanceIT.class);

    @Before
    public void init() throws Exception {
        log.info("init");
        initCredentials(jenkinsRule);
        cloud = initCloud(jenkinsRule);
        client = initClient(jenkinsRule, label, log);

        cloud.setConfigurations(ImmutableList.of(instanceConfigurationBuilder()
                .numExecutorsStr(NUM_EXECUTORS)
                .labels(LABEL)
                .oneShot(false)
                .createSnapshot(false)
                .template(NULL_TEMPLATE)
                .googleLabels(label)
                .cloud(cloud)
                .build()));
    }

    @After
    public void teardown() throws IOException {
        teardownResources(client, label, log);
    }

    @Test
    public void testNodeRemovedAfterInstanceStopped() throws Exception {
        var planned = cloud.provision(new LabelAtom(LABEL), 1);
        assertEquals("one instance should be provisioned", 1, planned.size());
        var plannedNode = planned.iterator().next();
        String name = plannedNode.displayName;
        plannedNode.future.get();

        assertNotNull("agent should be registered with Jenkins", jenkinsRule.jenkins.getNode(name));
        assertEquals("GCP VM should be running once provisioned", RUNNING, instanceStatus(name));

        // Terminate the VM out of band, with stop rather than delete: the instance stays around.
        log.info("stopping instance " + name);
        cloud.getClientV2()
                .getCompute()
                .instances()
                .stop(PROJECT_ID, ZONE, name)
                .execute();

        Awaitility.await("instance reaches TERMINATED")
                .timeout(5L * TEST_TIMEOUT_MULTIPLIER, TimeUnit.MINUTES)
                .pollInterval(10, TimeUnit.SECONDS)
                .until(() -> TERMINATED.equals(instanceStatus(name)));

        // The agent is now unreachable, so Jenkins should not keep the node registered.
        try {
            Awaitility.await("Jenkins drops the node whose VM was stopped")
                    .timeout(5L * TEST_TIMEOUT_MULTIPLIER, TimeUnit.MINUTES)
                    .pollInterval(10, TimeUnit.SECONDS)
                    .until(() -> jenkinsRule.jenkins.getNode(name) == null);
        } catch (ConditionTimeoutException e) {
            dumpDiagnostics(name);
            throw e;
        }
    }

    /**
     * Dumps why the node outlived its VM: whether the node is still registered, whether its computer
     * still resolves back to a node (a null there sends {@code terminateNode} down its no-op branch),
     * and the agent launch log, which is where {@code listener.error} output lands.
     */
    private void dumpDiagnostics(String name) {
        Node node = jenkinsRule.jenkins.getNode(name);
        log.warning("DIAG node still registered: " + (node != null));
        log.warning("DIAG all nodes: " + jenkinsRule.jenkins.getNodes());
        if (node != null) {
            Computer computer = node.toComputer();
            log.warning("DIAG computer: " + computer);
            if (computer != null) {
                log.warning("DIAG computer.isOffline=" + computer.isOffline() + ", offlineCause="
                        + computer.getOfflineCause() + ", computer.getNode()=" + computer.getNode());
                if (computer instanceof SlaveComputer) {
                    try {
                        log.warning("DIAG agent launch log:\n" + ((SlaveComputer) computer).getLog());
                    } catch (IOException ioe) {
                        log.warning("DIAG could not read agent launch log: " + ioe);
                    }
                }
            }
        }
        try {
            log.warning("DIAG instance status: " + instanceStatus(name));
        } catch (IOException ioe) {
            log.warning("DIAG could not read instance status: " + ioe);
        }
    }

    /** Returns the instance status, or {@code DELETED} once the instance no longer exists. */
    private String instanceStatus(String name) throws IOException {
        try {
            return client.getInstance(PROJECT_ID, ZONE, name).getStatus();
        } catch (GoogleJsonResponseException e) {
            if (e.getStatusCode() == 404) {
                return DELETED;
            }
            throw e;
        }
    }
}
