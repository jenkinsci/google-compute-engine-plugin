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

import static org.junit.Assert.assertNotNull;

import com.google.cloud.graphite.platforms.plugin.client.ComputeClient;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.MockitoRule;
import org.mockito.quality.Strictness;

@RunWith(MockitoJUnitRunner.Silent.class)
public class SecondCleanLostNodesWorkTest {

  @Rule public MockitoRule experimentRule = MockitoJUnit.rule().strictness(Strictness.LENIENT);

  private static final String TEST_PROJECT_ID = "test_project_id";

  @Rule public JenkinsRule r = new JenkinsRule();

  @Mock public ComputeEngineCloud cloud;

  @Mock public ComputeClient client;

  private CleanLostNodesWork getWorker() {
    return r.jenkins.getExtensionList(CleanLostNodesWork.class).get(0);
  }

  @Before
  public void setup() {}

  @Test
  public void shouldRegisterCleanNodeWorker() {
    assertNotNull(getWorker());
  }

  @Test
  public void shouldRunWithoutClouds() {
    getWorker().doRun();
  }
}
