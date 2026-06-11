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
        assertEquals("Wrong minimumNumberOfInstances", 2, configuration.getMinimumNumberOfInstances());
        assertEquals("Wrong minimumNumberOfSpareInstances", 1, configuration.getMinimumNumberOfSpareInstances());
        assertNotNull("customMetadata should not be null", configuration.getCustomMetadata());
        assertEquals(
                "Wrong customMetadata size",
                2,
                configuration.getCustomMetadata().size());
        assertEquals("my-custom-key", configuration.getCustomMetadata().get(0).getKey());
        assertEquals("my-custom-value", configuration.getCustomMetadata().get(0).getValue());
        assertEquals(
                "my-multiline-key", configuration.getCustomMetadata().get(1).getKey());
        assertEquals(
                "line1\nline2\nline3", configuration.getCustomMetadata().get(1).getValue());
        assertNotNull("customLabels should not be null", configuration.getCustomLabels());
        assertEquals(
                "Wrong customLabels size", 2, configuration.getCustomLabels().size());
        assertEquals("team", configuration.getCustomLabels().get(0).getKey());
        assertEquals("jenkins", configuration.getCustomLabels().get(0).getValue());
        assertEquals("cost-center", configuration.getCustomLabels().get(1).getKey());
        assertEquals("ci-1234", configuration.getCustomLabels().get(1).getValue());
        var shieldedVm = configuration.getShieldedVmConfiguration();
        assertNotNull("shieldedVmConfiguration should not be null", shieldedVm);
        assertEquals(true, shieldedVm.isEnableSecureBoot());
        assertEquals(true, shieldedVm.isEnableVtpm());
        assertEquals(false, shieldedVm.isEnableIntegrityMonitoring());
        var timeRangeConfig = configuration.getMinimumNumberOfInstancesTimeRangeConfig();
        assertNotNull("minimumNumberOfInstancesTimeRangeConfig should not be null", timeRangeConfig);
        assertEquals("09:00", timeRangeConfig.getActiveFrom());
        assertEquals("17:00", timeRangeConfig.getActiveTo());
        assertEquals(true, timeRangeConfig.getMonday());
        assertEquals(true, timeRangeConfig.getTuesday());
        assertEquals(true, timeRangeConfig.getWednesday());
        assertEquals(true, timeRangeConfig.getThursday());
        assertEquals(true, timeRangeConfig.getFriday());
        assertEquals(false, timeRangeConfig.getSaturday());
        assertEquals(false, timeRangeConfig.getSunday());
        assertNotNull("fallbackCandidates should not be null", configuration.getFallbackCandidates());
        assertEquals(
                "Wrong fallbackCandidates size",
                2,
                configuration.getFallbackCandidates().size());
        assertEquals("us-west1-b", configuration.getFallbackCandidates().get(0).getZone());
        assertEquals(
                "n4d-standard-32", configuration.getFallbackCandidates().get(0).getMachineType());
        assertEquals(
                "us-central1-a", configuration.getFallbackCandidates().get(1).getZone());
        assertEquals(
                "n2d-standard-32", configuration.getFallbackCandidates().get(1).getMachineType());
        assertEquals("us-central1", configuration.getFallbackCandidates().get(1).getRegion());
        assertEquals(
                "gce-jenkins-central",
                configuration.getFallbackCandidates().get(1).getSubnetwork());
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
