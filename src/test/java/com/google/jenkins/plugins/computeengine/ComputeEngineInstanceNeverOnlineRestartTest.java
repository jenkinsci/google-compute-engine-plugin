/*
 * Copyright 2026 Google LLC
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

package com.google.jenkins.plugins.computeengine;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.SlaveComputer;
import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.JenkinsSessionExtension;

/** Launch-timeout cleanup across a controller restart, see #571. */
class ComputeEngineInstanceNeverOnlineRestartTest {

    private static final String NODE_NAME = "gce-571-agent";
    private static final int LAUNCH_TIMEOUT_SECONDS = 30;
    private static final long PROVISIONED_HOURS_AGO = TimeUnit.HOURS.toMillis(3);

    @RegisterExtension
    private final JenkinsSessionExtension sessions = new JenkinsSessionExtension();

    @Test
    void agentThatConnectedBeforeRestartIsNotTerminatedAsNeverOnline() throws Throwable {
        sessions.then(r -> {
            ComputeEngineInstance node = addOneShotNode(r);
            // Comes online, as ComputeEngineComputerListener#onOnline does for a real agent.
            node.onConnected();
            assertTrue(node.hasEverConnected());
        });
        sessions.then(r -> {
            // Controller restarted: the agent has not reconnected yet, so its computer is offline,
            // and it was provisioned long before the launch timeout.
            ComputeEngineInstance node = (ComputeEngineInstance) r.jenkins.getNode(NODE_NAME);
            assertNotNull(node);
            node.skipGcpTerminateForTesting = true;
            ComputeEngineComputer computer = (ComputeEngineComputer) node.toComputer();
            assertNotNull(computer);
            assertFalse(computer.isOnline());

            assertTrue(node.hasEverConnected(), "connected state should survive a restart");
            long longAfterRestart = System.currentTimeMillis() + node.getLaunchTimeoutMillis() + 1;
            assertFalse(ComputeEngineRetentionStrategy.shouldTerminateNeverOnline(node, longAfterRestart));

            ((ComputeEngineRetentionStrategy) node.getRetentionStrategy()).check(computer);
            assertNotNull(r.jenkins.getNode(NODE_NAME), "previously connected agent should be kept for resume");
        });
    }

    @Test
    void agentStillLaunchingAtRestartGetsFreshLaunchTimeoutAndIsThenCleanedUp() throws Throwable {
        sessions.then(r -> {
            // Never connects: e.g. a launch interrupted by the restart, or a node saved by a release
            // that did not persist the connected state.
            ComputeEngineInstance node = addOneShotNode(r);
            assertFalse(node.hasEverConnected());
        });
        sessions.then(r -> {
            ComputeEngineInstance node = (ComputeEngineInstance) r.jenkins.getNode(NODE_NAME);
            assertNotNull(node);
            long now = System.currentTimeMillis();

            assertFalse(
                    ComputeEngineRetentionStrategy.shouldTerminateNeverOnline(node, now),
                    "the launch timeout should restart when the node is loaded");
            assertTrue(
                    ComputeEngineRetentionStrategy.shouldTerminateNeverOnline(
                            node, now + node.getLaunchTimeoutMillis() + 1),
                    "a node that still never connects should be cleaned up after the launch timeout");
        });
    }

    private static ComputeEngineInstance addOneShotNode(JenkinsRule r) throws Exception {
        ComputeEngineCloud cloud = new ComputeEngineCloud("test", "test-project", "test-credentials", "10");
        r.jenkins.clouds.add(cloud);
        ComputeEngineInstance node = ComputeEngineInstance.builder()
                .cloudName(cloud.name)
                .name(NODE_NAME)
                .zone("us-west1-a")
                .nodeDescription("test")
                .sshUser("jenkins")
                .remoteFS("/tmp")
                .windowsConfig(null)
                .sshConfig(null)
                .createSnapshot(false)
                .oneShot(true)
                .ignoreProxy(false)
                .terminateIdleDuringShutdown(false)
                .numExecutors(1)
                .mode(Node.Mode.NORMAL)
                .labelString("test")
                .launcher(new NoOpLauncher())
                .retentionStrategy(new ComputeEngineRetentionStrategy(60, true))
                .launchTimeout(LAUNCH_TIMEOUT_SECONDS)
                .sshPort(22)
                .javaExecPath(null)
                .sshKeyCredential(null)
                .waitForStartupScript(false)
                .cloud(cloud)
                .build();
        setProvisionedAtMillis(node, System.currentTimeMillis() - PROVISIONED_HOURS_AGO);
        r.jenkins.addNode(node);
        return node;
    }

    private static void setProvisionedAtMillis(ComputeEngineInstance node, long value) throws Exception {
        Field field = ComputeEngineInstance.class.getDeclaredField("provisionedAtMillis");
        field.setAccessible(true);
        field.set(node, value);
    }

    /** Never connects, like an agent whose controller has just restarted and not reconnected yet. */
    private static final class NoOpLauncher extends ComputerLauncher {
        @Override
        public void launch(SlaveComputer computer, TaskListener listener) {}
    }
}
