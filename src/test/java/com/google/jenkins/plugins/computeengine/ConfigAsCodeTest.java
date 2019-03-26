package com.google.jenkins.plugins.computeengine;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotPrivateKeyCredentials;
import com.google.jenkins.plugins.credentials.oauth.ServiceAccountConfig;
import hudson.slaves.Cloud;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ConfigAsCodeTest {

    @Rule
    public JenkinsConfiguredWithCodeRule r = new JenkinsConfiguredWithCodeRule();

    @Test
    @ConfiguredWithCode("configuration-as-code.yml")
    public void shouldCreateCloudInstanceFromCode() throws Exception {
        assertEquals("Zero clouds found", r.jenkins.clouds.size(), 1);
        ComputeEngineCloud cloud = (ComputeEngineCloud) r.jenkins.clouds.getByName("gce-jenkins-build");
        assertNotNull("Cloud by name not found", cloud);
    }
    
    @Test
    @ConfiguredWithCode("configuration-as-code.yml")
    public void shouldCreateGCEClientFromCode() throws Exception {
        String projectId = System.getenv("GOOGLE_PROJECT_ID");
        assertNotNull("GOOGLE_PROJECT_ID env var must be set", projectId);

        String serviceAccountKeyJson = System.getenv("GOOGLE_CREDENTIALS");
        assertNotNull("GOOGLE_CREDENTIALS env var must be set", serviceAccountKeyJson);

        ServiceAccountConfig sac = new StringJsonServiceAccountConfig(serviceAccountKeyJson);
        Credentials c = (Credentials) new GoogleRobotPrivateKeyCredentials(projectId, sac, null);

        CredentialsStore store = new SystemCredentialsProvider.ProviderImpl().getStore(r.jenkins);
        store.addCredentials(Domain.global(), c);
        
        ComputeEngineCloud cloud = (ComputeEngineCloud) r.jenkins.clouds.getByName("gce-jenkins-build");
        assertNotNull("Cloud by name not found", cloud);
        
    }
}
