package com.google.jenkins.plugins.computeengine;

import com.google.api.services.compute.model.Instance;
import com.google.jenkins.plugins.computeengine.client.ComputeClient;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import static com.google.common.collect.ImmutableList.of;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anyMap;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class CleanLostNodesWorkTest {

    private static final String TEST_PROJECT_ID = "test_project_id";

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Mock
    public ComputeEngineCloud cloud;

    @Mock
    public ComputeClient client;

    private CleanLostNodesWork getWorker() {
        return r.jenkins.getExtensionList(CleanLostNodesWork.class).get(0);
    }

    @Before
    public void setup() {
        when(cloud.getClient()).thenReturn(client);
        when(cloud.getProjectId()).thenReturn(TEST_PROJECT_ID);
        when(cloud.getInstanceUniqueId()).thenReturn("234234355");
    }

    @Test
    public void shouldRegisterCleanNodeWorker() {
        assertNotNull(getWorker());
    }

    @Test
    public void shouldRunWithoutClouds() {
        getWorker().doRun();
    }

    @Test
    public void shouldNotCleanAnyInstance() throws Exception {
        final String instanceName = "inst-1";
        Instance remoteInstance = new Instance().setName(instanceName);
        when(client.getInstancesWithLabel(eq(TEST_PROJECT_ID), anyMap())).thenReturn(of(remoteInstance));

        ComputeEngineInstance localInstance = Mockito.mock(ComputeEngineInstance.class);
        when(localInstance.getCloud()).thenReturn(cloud);
        when(localInstance.getNodeName()).thenReturn(instanceName);
        when(localInstance.getNumExecutors()).thenReturn(0);

        r.jenkins.clouds.add(cloud);
        r.jenkins.addNode(localInstance);

        getWorker().doRun();
        verify(client).getInstancesWithLabel(eq(TEST_PROJECT_ID), anyMap());
        verifyZeroInteractions(client);
    }

    @Test
    public void shouldCleanLostInstance() throws Exception {
        final String instanceName = "inst-2";
        final String zone = "test-zone";
        Instance remoteInstance = new Instance().setName(instanceName).setZone(zone);
        when(client.getInstancesWithLabel(eq(TEST_PROJECT_ID), anyMap())).thenReturn(of(remoteInstance));

        r.jenkins.clouds.add(cloud);

        getWorker().doRun();
        verify(client).getInstancesWithLabel(eq(TEST_PROJECT_ID), anyMap());
        verify(client).terminateInstance(eq(TEST_PROJECT_ID), eq(zone), eq(instanceName));
    }
}
