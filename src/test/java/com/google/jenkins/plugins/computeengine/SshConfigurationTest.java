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

import static org.junit.jupiter.api.Assertions.*;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import hudson.util.ListBoxModel;
import java.io.IOException;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/** Test suite to test functionality of SshConfiguration */
@WithJenkins
class SshConfigurationTest {

    private JenkinsRule r;

    @BeforeEach
    void init(JenkinsRule rule) {
        r = rule;
    }

    @Test
    void testCustomSshPrivateKey() throws IOException {
        SshConfiguration.DescriptorImpl descriptor = new SshConfiguration.DescriptorImpl();
        assertNotNull(descriptor);

        ListBoxModel m = descriptor.doFillCustomPrivateKeyCredentialsIdItems(Jenkins.get(), "key1");
        assertEquals(1, m.size());
        BasicSSHUserPrivateKey customPrivateKeyCredentials = new BasicSSHUserPrivateKey(
                CredentialsScope.SYSTEM,
                "test-key",
                "user1",
                new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource("private key: ssh"),
                "",
                "a key for testing");
        for (CredentialsStore credentialsStore : CredentialsProvider.lookupStores(r.jenkins)) {
            if (credentialsStore instanceof SystemCredentialsProvider.StoreImpl) {
                credentialsStore.addCredentials(Domain.global(), customPrivateKeyCredentials);
            }
        }

        // Test that new key has been added
        m = descriptor.doFillCustomPrivateKeyCredentialsIdItems(Jenkins.get(), "key2");
        assertEquals(2, m.size());

        // Test that new key can be retrieved
        SSHUserPrivateKey privateKey = SshConfiguration.getCustomPrivateKeyCredentials("test-key");
        assertTrue(privateKey.getPrivateKeys().get(0).startsWith("private key:"));
        assertNotNull(privateKey);
    }
}
