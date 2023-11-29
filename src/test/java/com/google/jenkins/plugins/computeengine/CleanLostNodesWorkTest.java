/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.jenkins.plugins.computeengine;

import static com.google.common.collect.ImmutableList.of;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anyMap;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.api.services.compute.model.Instance;
import com.google.cloud.graphite.platforms.plugin.client.ComputeClient;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class CleanLostNodesWorkTest {

  private static final String TEST_PROJECT_ID = "test_project_id";

  @Rule public JenkinsRule r = new JenkinsRule();

  @Mock public ComputeEngineCloud cloud;

  @Mock public ComputeClient client;

  private CleanLostNodesWork getWorker() {
    return r.jenkins.getExtensionList(CleanLostNodesWork.class).get(0);
  }

  @Before
  public void setup() {
    when(cloud.getClient()).thenReturn(client);
    when(cloud.getProjectId()).thenReturn(TEST_PROJECT_ID);
    when(cloud.getInstanceId()).thenReturn("234234355");
  }

  @Test
  public void shouldNotCleanAnyInstance() throws Exception {
    final String instanceName = "inst-1";
    Instance remoteInstance = new Instance().setName(instanceName).setStatus("RUNNING");
    when(client.listInstancesWithLabel(eq(TEST_PROJECT_ID), anyMap()))
        .thenReturn(of(remoteInstance));

    ComputeEngineInstance localInstance = Mockito.mock(ComputeEngineInstance.class);
    when(localInstance.getCloud()).thenReturn(cloud);
    when(localInstance.getNodeName()).thenReturn(instanceName);
    when(localInstance.getNumExecutors()).thenReturn(0);

    r.jenkins.clouds.add(cloud);
    r.jenkins.addNode(localInstance);

    getWorker().doRun();
    verify(client).listInstancesWithLabel(eq(TEST_PROJECT_ID), anyMap());
    verifyNoMoreInteractions(client);
  }

  @Test
  public void shouldCleanLostInstance() throws Exception {
    final String instanceName = "inst-2";
    final String zone = "test-zone";
    Instance remoteInstance =
        new Instance().setName(instanceName).setZone(zone).setStatus("RUNNING");
    when(client.listInstancesWithLabel(eq(TEST_PROJECT_ID), anyMap()))
        .thenReturn(of(remoteInstance));

    r.jenkins.clouds.add(cloud);

    getWorker().doRun();
    verify(client).listInstancesWithLabel(eq(TEST_PROJECT_ID), anyMap());
    verify(client).terminateInstanceAsync(eq(TEST_PROJECT_ID), eq(zone), eq(instanceName));
  }

  @Test
  public void shouldNotCleanStoppingInstance() throws Exception {
    final String instanceName = "inst-2";
    final String zone = "test-zone";
    Instance remoteInstance =
        new Instance().setName(instanceName).setZone(zone).setStatus("STOPPING");
    when(client.listInstancesWithLabel(eq(TEST_PROJECT_ID), anyMap()))
        .thenReturn(of(remoteInstance));

    r.jenkins.clouds.add(cloud);

    getWorker().doRun();
    verify(client).listInstancesWithLabel(eq(TEST_PROJECT_ID), anyMap());
    verifyNoMoreInteractions(client);
  }
}
