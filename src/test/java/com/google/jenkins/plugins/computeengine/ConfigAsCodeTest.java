package com.google.jenkins.plugins.computeengine;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import com.google.jenkins.plugins.computeengine.config.PreemptibleVm;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotPrivateKeyCredentials;
import hudson.model.Node;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class ConfigAsCodeTest {

    @Rule
    public JenkinsRule jenkinsRule = new JenkinsConfiguredWithCodeRule();

    @Test
    @ConfiguredWithCode("configuration-as-code.yml")
    public void shouldCreateCloudInstanceFromCode() {
        assertEquals("Zero clouds found", jenkinsRule.jenkins.clouds.size(), 1);
        ComputeEngineCloud cloud = (ComputeEngineCloud) jenkinsRule.jenkins.clouds.getByName("gce-jenkins-build");
        assertNotNull("Cloud by name not found", cloud);
        assertEquals("Project id is wrong", "gce-jenkins", cloud.getProjectId());
        assertEquals("Wrong instance cap str", "53", cloud.getInstanceCapStr());
        assertEquals("Wrong instance cap", 53, cloud.getInstanceCap());
        assertEquals("Wrong credentials", "gce-jenkins", cloud.getCredentialsId());

        assertEquals("Configurations number wrong", 1, cloud.getConfigurations().size());
        InstanceConfiguration configuration = cloud.getConfigurations().get(0);
        assertEquals("Wrong configurations prefix", "jenkins-agent-image", configuration.getNamePrefix());
        assertEquals("Wrong configurations description", "Jenkins agent", configuration.getDescription());
        assertEquals("Wrong configurations launchTimeoutSecondsStr", "6", configuration.getLaunchTimeoutSecondsStr());
        assertEquals("Wrong configurations getLaunchTimeoutMillis", 6000, configuration.getLaunchTimeoutMillis());
        assertEquals("Wrong configurations mode", Node.Mode.EXCLUSIVE, configuration.getMode());
        assertEquals("Wrong configurations labelString", "jenkins-agent", configuration.getLabelString());
        assertEquals("Wrong configurations numExecutors", "1", configuration.getNumExecutorsStr());
        assertEquals("Wrong configurations runAsUser", "jenkins", configuration.getRunAsUser());
        assertEquals("Wrong configurations remoteFs", "agent", configuration.getRemoteFs());
        assertEquals("Wrong configurations javaExecPath", "java", configuration.getJavaExecPath());
        assertNull("Wrong configuration provisioningType non null", configuration.getProvisioningType());
    }

    @Test
    @ConfiguredWithCode("configuration-as-code.yml")
    public void shouldCreateGCEClientFromCode() throws Exception {

        ComputeEngineCloud cloud = (ComputeEngineCloud) jenkinsRule.jenkins.clouds.getByName("gce-jenkins-build");
        assertNotNull("Cloud by name not found", cloud);
        // Ensure correct exception is thrown
        assertThrows(GoogleRobotPrivateKeyCredentials.PrivateKeyNotSetException.class, cloud::getClient);
    }

    @Test
    @ConfiguredWithCode("casc-preemptible-compatibility.yml")
    public void provisioningTypeShouldBePreemptible() {
        ComputeEngineCloud cloud = (ComputeEngineCloud) jenkinsRule.jenkins.clouds.getByName("gce-jenkins-build");
        assertThat(cloud.getConfigurations().get(0).getProvisioningType(), is(instanceOf(PreemptibleVm.class)));
    }
}
