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

package com.google.jenkins.plugins.computeengine;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.jenkins.plugins.computeengine.ssh.GoogleKeyPair;
import hudson.util.XStream2;
import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.WithoutJenkins;

import java.io.File;
import java.nio.charset.StandardCharsets;

public class GoogleKeyPairTest {

  @Rule public JenkinsRule r = new JenkinsRule();

  @WithoutJenkins
  @Test
  public void KeyPairGeneration() {
    GoogleKeyPair gkp = GoogleKeyPair.generate("user");
    assertNotNull(gkp.toString());
    assert (gkp.getPublicKey().contains("user"));
  }

  @Issue("SECURITY-2045")
  @Test
  public void privateKeyNotStoredAsPlainTextOnDisk() throws Exception {
    GoogleKeyPair sshKeyPair = GoogleKeyPair.generate("test-user");
    File configFile = new File(r.jenkins.getRootDir(), sshKeyPair.getClass().getName() + ".xml");
    FileUtils.write(configFile, new XStream2().toXML(sshKeyPair), StandardCharsets.UTF_8);

    String configAsString = FileUtils.readFileToString(configFile, StandardCharsets.UTF_8);
    assertTrue(configAsString.contains(sshKeyPair.getPrivateKey().getEncryptedValue()));
    assertFalse(configAsString.contains(sshKeyPair.getPrivateKey().getPlainText()));
  }
}
