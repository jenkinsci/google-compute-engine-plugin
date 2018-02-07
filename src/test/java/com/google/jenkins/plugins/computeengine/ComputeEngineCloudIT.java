package com.google.jenkins.plugins.computeengine;

import com.cloudbees.plugins.credentials.*;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.google.jenkins.plugins.credentials.oauth.GoogleOAuth2Credentials;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotPrivateKeyCredentials;
import com.google.jenkins.plugins.credentials.oauth.ServiceAccountConfig;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;
import java.security.PrivateKey;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class ComputeEngineCloudIT {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Before
    public void init() throws Exception {
        Credentials c = (Credentials) new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL,java.util.UUID.randomUUID().toString(), "description", "user", "password");

        CredentialsStore store = new SystemCredentialsProvider.ProviderImpl().getStore(r.jenkins);
        store.addCredentials(Domain.global(), c);
    }

    @Test
    public void testCredentialsCreated() {
        List<Credentials> creds = new SystemCredentialsProvider.ProviderImpl().getStore(r.jenkins).getCredentials(Domain.global());
        assertEquals(1, creds.size());
    }
}
