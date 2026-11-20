package com.google.jenkins.plugins.computeengine;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;

import com.google.jenkins.plugins.computeengine.config.PreemptibleVm;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotPrivateKeyCredentials;
import hudson.model.Node;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import io.jenkins.plugins.casc.misc.junit.jupiter.WithJenkinsConfiguredWithCode;
import org.junit.jupiter.api.Test;

@WithJenkinsConfiguredWithCode
class ConfigAsCodeTest {

    @Test
    @ConfiguredWithCode("configuration-as-code.yml")
    void shouldCreateCloudInstanceFromCode(JenkinsConfiguredWithCodeRule jenkinsRule) {
        assertEquals(1, jenkinsRule.jenkins.clouds.size(), "Zero clouds found");
        ComputeEngineCloud cloud = (ComputeEngineCloud) jenkinsRule.jenkins.clouds.getByName("gce-jenkins-build");
        assertNotNull(cloud, "Cloud by name not found");
        assertEquals("gce-jenkins", cloud.getProjectId(), "Project id is wrong");
        assertEquals("53", cloud.getInstanceCapStr(), "Wrong instance cap str");
        assertEquals(53, cloud.getInstanceCap(), "Wrong instance cap");
        assertEquals("gce-jenkins", cloud.getCredentialsId(), "Wrong credentials");

        assertEquals(1, cloud.getConfigurations().size(), "Configurations number wrong");
        InstanceConfiguration configuration = cloud.getConfigurations().get(0);
        assertEquals("jenkins-agent-image", configuration.getNamePrefix(), "Wrong configurations prefix");
        assertEquals("Jenkins agent", configuration.getDescription(), "Wrong configurations description");
        assertEquals("6", configuration.getLaunchTimeoutSecondsStr(), "Wrong configurations launchTimeoutSecondsStr");
        assertEquals(6000, configuration.getLaunchTimeoutMillis(), "Wrong configurations getLaunchTimeoutMillis");
        assertEquals(Node.Mode.EXCLUSIVE, configuration.getMode(), "Wrong configurations mode");
        assertEquals("jenkins-agent", configuration.getLabelString(), "Wrong configurations labelString");
        assertEquals("1", configuration.getNumExecutorsStr(), "Wrong configurations numExecutors");
        assertEquals("jenkins", configuration.getRunAsUser(), "Wrong configurations runAsUser");
        assertEquals("agent", configuration.getRemoteFs(), "Wrong configurations remoteFs");
        assertEquals("java", configuration.getJavaExecPath(), "Wrong configurations javaExecPath");
        assertNull(configuration.getProvisioningType(), "Wrong configuration provisioningType non null");
        assertEquals(2, configuration.getMinimumNumberOfInstances(), "Wrong minimumNumberOfInstances");
        assertEquals(1, configuration.getMinimumNumberOfSpareInstances(), "Wrong minimumNumberOfSpareInstances");
        assertNotNull(configuration.getCustomMetadata(), "customMetadata should not be null");
        assertEquals(2, configuration.getCustomMetadata().size(), "Wrong customMetadata size");
        assertEquals("my-custom-key", configuration.getCustomMetadata().get(0).getKey());
        assertEquals("my-custom-value", configuration.getCustomMetadata().get(0).getValue());
        assertEquals(
                "my-multiline-key", configuration.getCustomMetadata().get(1).getKey());
        assertEquals(
                "line1\nline2\nline3", configuration.getCustomMetadata().get(1).getValue());
        assertNotNull(configuration.getCustomLabels(), "customLabels should not be null");
        assertEquals(2, configuration.getCustomLabels().size(), "Wrong customLabels size");
        assertEquals("team", configuration.getCustomLabels().get(0).getKey());
        assertEquals("jenkins", configuration.getCustomLabels().get(0).getValue());
        assertEquals("cost-center", configuration.getCustomLabels().get(1).getKey());
        assertEquals("ci-1234", configuration.getCustomLabels().get(1).getValue());
        var shieldedVm = configuration.getShieldedVmConfiguration();
        assertNotNull(shieldedVm, "shieldedVmConfiguration should not be null");
        assertTrue(shieldedVm.isEnableSecureBoot());
        assertTrue(shieldedVm.isEnableVtpm());
        assertFalse(shieldedVm.isEnableIntegrityMonitoring());
        var timeRangeConfig = configuration.getMinimumNumberOfInstancesTimeRangeConfig();
        assertNotNull(timeRangeConfig, "minimumNumberOfInstancesTimeRangeConfig should not be null");
        assertEquals("09:00", timeRangeConfig.getActiveFrom());
        assertEquals("17:00", timeRangeConfig.getActiveTo());
        assertEquals(true, timeRangeConfig.getMonday());
        assertEquals(true, timeRangeConfig.getTuesday());
        assertEquals(true, timeRangeConfig.getWednesday());
        assertEquals(true, timeRangeConfig.getThursday());
        assertEquals(true, timeRangeConfig.getFriday());
        assertEquals(false, timeRangeConfig.getSaturday());
        assertEquals(false, timeRangeConfig.getSunday());
    }

    @Test
    @ConfiguredWithCode("configuration-as-code.yml")
    void shouldCreateGCEClientFromCode(JenkinsConfiguredWithCodeRule jenkinsRule) {
        ComputeEngineCloud cloud = (ComputeEngineCloud) jenkinsRule.jenkins.clouds.getByName("gce-jenkins-build");
        assertNotNull(cloud, "Cloud by name not found");
        // Ensure correct exception is thrown
        assertThrows(GoogleRobotPrivateKeyCredentials.PrivateKeyNotSetException.class, cloud::getClient);
    }

    @Test
    @ConfiguredWithCode("casc-preemptible-compatibility.yml")
    void provisioningTypeShouldBePreemptible(JenkinsConfiguredWithCodeRule jenkinsRule) {
        ComputeEngineCloud cloud = (ComputeEngineCloud) jenkinsRule.jenkins.clouds.getByName("gce-jenkins-build");
        assertThat(cloud.getConfigurations().get(0).getProvisioningType(), is(instanceOf(PreemptibleVm.class)));
    }
}
