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

package com.google.jenkins.plugins.computeengine;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.RetentionStrategy;
import hudson.slaves.SlaveComputer;
import java.lang.reflect.Field;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class ComputeEngineInstanceNeverOnlineTest {

    private static final int LAUNCH_TIMEOUT_SECONDS = 30;
    private static final long TIMEOUT_MILLIS = LAUNCH_TIMEOUT_SECONDS * 1000L;

    private JenkinsRule r;

    @BeforeEach
    void init(JenkinsRule rule) {
        r = rule;
    }

    @Test
    void launchTimeoutSecondsMatchConfigMillis() throws Exception {
        InstanceConfiguration config = InstanceConfigurationTest.instanceConfigurationBuilder()
                .launchTimeoutSecondsStr(String.valueOf(LAUNCH_TIMEOUT_SECONDS))
                .build();
        ComputeEngineInstance node = createNode(LAUNCH_TIMEOUT_SECONDS);

        assertEquals(config.getLaunchTimeoutMillis(), node.getLaunchTimeoutMillis());
        assertEquals(TIMEOUT_MILLIS, node.getLaunchTimeoutMillis());
        assertEquals(Integer.valueOf(LAUNCH_TIMEOUT_SECONDS), node.getLaunchTimeout());
    }

    @Test
    void hasEverConnected_falseUntilOnConnected() throws Exception {
        ComputeEngineInstance node = createNode(LAUNCH_TIMEOUT_SECONDS);
        assertFalse(node.hasEverConnected());

        node.onConnected();
        assertTrue(node.hasEverConnected());
    }

    @Test
    void isPastLaunchTimeout_respectsAge() throws Exception {
        ComputeEngineInstance node = createNode(LAUNCH_TIMEOUT_SECONDS);
        long provisionedAt = node.getProvisionedAtMillis();

        assertFalse(node.isPastLaunchTimeout(provisionedAt + node.getLaunchTimeoutMillis()));
        assertTrue(node.isPastLaunchTimeout(provisionedAt + node.getLaunchTimeoutMillis() + 1));
    }

    @Test
    void isPastLaunchTimeout_unknownAgeIsNotPastTimeout() throws Exception {
        // Nodes serialized before this field existed deserialize with provisionedAtMillis == 0.
        ComputeEngineInstance node = createNode(LAUNCH_TIMEOUT_SECONDS);
        setProvisionedAtMillis(node, 0);

        assertFalse(node.isPastLaunchTimeout(System.currentTimeMillis()));
    }

    @Test
    void terminateNeverOnline_removesJenkinsNode() throws Exception {
        ComputeEngineInstance node = createNode(LAUNCH_TIMEOUT_SECONDS);
        r.jenkins.addNode(node);

        node.terminateNeverOnline("test");

        assertNull(r.jenkins.getNode(node.getNodeName()), "never-online node should be removed from Jenkins");
    }

    @Test
    void shouldTerminateNeverOnline_onlyWhenNeverConnectedAndPastTimeout() throws Exception {
        ComputeEngineInstance node = createNode(LAUNCH_TIMEOUT_SECONDS);
        long provisionedAt = node.getProvisionedAtMillis();
        long pastTimeout = provisionedAt + node.getLaunchTimeoutMillis() + 1;

        assertFalse(ComputeEngineRetentionStrategy.shouldTerminateNeverOnline(null, pastTimeout));
        assertFalse(ComputeEngineRetentionStrategy.shouldTerminateNeverOnline(
                node, provisionedAt + node.getLaunchTimeoutMillis()));
        assertTrue(ComputeEngineRetentionStrategy.shouldTerminateNeverOnline(node, pastTimeout));

        node.onConnected();
        assertFalse(ComputeEngineRetentionStrategy.shouldTerminateNeverOnline(node, pastTimeout));
    }

    @Test
    void shouldTerminateNeverOnline_skipsWhenComputerAlreadyOnline() throws Exception {
        ComputeEngineInstance node = spy(createNode(LAUNCH_TIMEOUT_SECONDS));
        long pastTimeout = node.getProvisionedAtMillis() + node.getLaunchTimeoutMillis() + 1;

        Computer online = mock(Computer.class);
        when(online.isOnline()).thenReturn(true);
        doReturn(online).when(node).toComputer();

        assertFalse(ComputeEngineRetentionStrategy.shouldTerminateNeverOnline(node, pastTimeout));
    }

    private static void setProvisionedAtMillis(ComputeEngineInstance node, long value) throws Exception {
        Field field = ComputeEngineInstance.class.getDeclaredField("provisionedAtMillis");
        field.setAccessible(true);
        field.set(node, value);
    }

    private static ComputeEngineInstance createNode(int launchTimeoutSeconds) throws Exception {
        return ComputeEngineInstance.builder()
                .cloudName("test-cloud")
                .name("never-online-" + System.nanoTime())
                .zone("us-west1-a")
                .nodeDescription("test")
                .sshUser("jenkins")
                .remoteFS("/tmp")
                .windowsConfig(null)
                .sshConfig(null)
                .createSnapshot(false)
                .oneShot(false)
                .ignoreProxy(false)
                .terminateIdleDuringShutdown(false)
                .numExecutors(1)
                .mode(Node.Mode.NORMAL)
                .labelString("test")
                .launcher(new NoOpLauncher())
                .retentionStrategy(RetentionStrategy.NOOP)
                .launchTimeout(launchTimeoutSeconds)
                .sshPort(22)
                .javaExecPath(null)
                .sshKeyCredential(null)
                .waitForStartupScript(false)
                .cloud(null)
                .build();
    }

    private static final class NoOpLauncher extends ComputerLauncher {
        @Override
        public void launch(SlaveComputer computer, TaskListener listener) {
            // never connects
        }
    }
}
