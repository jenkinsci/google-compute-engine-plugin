/*
 * Copyright 2019 Google LLC
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

package com.google.jenkins.plugins.computeengine.client;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotPrivateKeyCredentials;
import com.google.jenkins.plugins.credentials.oauth.ServiceAccountConfig;
import org.junit.*;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.security.PrivateKey;
import java.util.ArrayList;

@RunWith(MockitoJUnitRunner.class)
public class ClientFactoryTest {
    public static final PrivateKey PRIVATE_KEY;
    public static final String ACCOUNT_ID = "test-account-id";
    public static final String PK_ALGO = "test";
    public static final String PK_FORMAT = "test";
    public static final byte[] PK_BYTES = new byte[0];
    static {
        PRIVATE_KEY = new PrivateKey() {
            @Override
            public String getAlgorithm() {
                return PK_ALGO;
            }

            @Override
            public String getFormat() {
                return PK_FORMAT;
            }

            @Override
            public byte[] getEncoded() {
                return PK_BYTES;
            }
        };
    }

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Mock
    public ServiceAccountConfig serviceAccountConfig;

    @Before
    public void init() {
        Mockito.when(serviceAccountConfig.getAccountId()).thenReturn(ACCOUNT_ID);
        Mockito.when(serviceAccountConfig.getPrivateKey()).thenReturn(PRIVATE_KEY);
    }

    @Test
    public void defaultTransport() throws Exception {
        Credentials c = (Credentials) new GoogleRobotPrivateKeyCredentials(ACCOUNT_ID, serviceAccountConfig, null);
        CredentialsStore store = new SystemCredentialsProvider.ProviderImpl().getStore(r.jenkins);
        store.addCredentials(Domain.global(), c);

        ClientFactory cf = new ClientFactory(r.jenkins, new ArrayList<DomainRequirement>(), ACCOUNT_ID);
        Assert.assertNotNull(cf.compute());
    }
}
