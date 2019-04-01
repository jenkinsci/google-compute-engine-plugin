package com.google.jenkins.plugins.computeengine;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotPrivateKeyCredentials;
import com.google.jenkins.plugins.credentials.oauth.ServiceAccountConfig;
import io.jenkins.plugins.casc.ConfigurationAsCode;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import org.apache.commons.io.IOUtils;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ConfigAsCodeTest {

    @Rule
//    public JenkinsRule r = new JenkinsConfiguredWithCodeRule();
    public JenkinsRule r = new JenkinsRule();

    @Test
//    @ConfiguredWithCode("configuration-as-code.yml")
    public void shouldCreateCloudInstanceFromCode() throws Exception {
        ConfigurationAsCode.get().configure(this.getClass().getResource("configuration-as-code.yml").toString());

        assertEquals("Zero clouds found", r.jenkins.clouds.size(), 1);
        ComputeEngineCloud cloud = (ComputeEngineCloud) r.jenkins.clouds.getByName("gce-jenkins-build");
        assertNotNull("Cloud by name not found", cloud);
    }

    @Test
//    @ConfiguredWithCode("configuration-as-code.yml")
    public void shouldCreateGCEClientFromCode() throws Exception {
        ConfigurationAsCode.get().configure(this.getClass().getResource("configuration-as-code.yml").toString());

        String testKey = IOUtils.toString(this.getClass().getResourceAsStream("gce-test-key.json"));
        ServiceAccountConfig sac = new StringJsonServiceAccountConfig(testKey);
        Credentials credentials = new GoogleRobotPrivateKeyCredentials("gce-jenkins", sac, null);

        CredentialsStore store = new SystemCredentialsProvider.ProviderImpl().getStore(r.jenkins);
        store.addCredentials(Domain.global(), credentials);

        ComputeEngineCloud cloud = (ComputeEngineCloud) r.jenkins.clouds.getByName("gce-jenkins-build");
        assertNotNull("Cloud by name not found", cloud);
        assertNotNull("GCE client not created", cloud.getClient());
    }
}
