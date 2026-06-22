package com.google.jenkins.plugins.computeengine.integration;

import static com.google.cloud.graphite.platforms.plugin.client.util.ClientUtil.nameFromSelfLink;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.LABEL;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.PROJECT_ID;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.TEST_TIMEOUT_MULTIPLIER;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initCredentials;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import com.google.cloud.graphite.platforms.plugin.client.ComputeClient;
import com.google.jenkins.plugins.computeengine.ComputeEngineComputerLauncher;
import com.google.jenkins.plugins.computeengine.client.ClientUtil;
import hudson.model.Node;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.LoggerRule;

public class ComputeEngineCloudFallbackZoneIT {
    private static final String PRIMARY_ZONE = "us-east1-b";
    private static final String FIRST_FALLBACK_ZONE = "us-east1-c";
    private static final String SECOND_FALLBACK_ZONE = "us-east1-d";
    private static final String FALLBACK_CONFIG_ZONE = "us-west1-a";
    private static final String FALLBACK_CLOUD_ZONE = "us-central1-a";

    private static final Logger log = Logger.getLogger(ComputeEngineCloudFallbackZoneIT.class.getName());

    @ClassRule
    public static Timeout timeout = new Timeout(15L * TEST_TIMEOUT_MULTIPLIER, TimeUnit.MINUTES);

    @Rule
    public JenkinsConfiguredWithCodeRule j = new JenkinsConfiguredWithCodeRule();

    @ClassRule
    public static LoggerRule logRule =
            new LoggerRule().record("com.google.jenkins.plugins.computeengine", Level.FINEST);

    @ClassRule
    public static BuildWatcher bw = new BuildWatcher();

    private ComputeClient client;

    @Before
    public void init() throws Exception {
        log.info("init");
        initCredentials(j);
        client = ClientUtil.getClientFactory(j.jenkins, PROJECT_ID).computeClient();
    }

    @After
    public void teardown() throws IOException {
        log.info("teardown");
        ComputeEngineComputerLauncher.setSimulateExhaustedZones();
        if (client != null) {
            for (Node node : j.jenkins.getNodes()) {
                for (var zone : new String[] {
                    PRIMARY_ZONE, FIRST_FALLBACK_ZONE, SECOND_FALLBACK_ZONE, FALLBACK_CONFIG_ZONE, FALLBACK_CLOUD_ZONE
                }) {
                    try {
                        client.terminateInstanceAsync(PROJECT_ID, zone, node.getNodeName());
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }

    @Test
    @ConfiguredWithCode("fallback-zone-casc.yml")
    public void testProvisioningFallsBackToThirdZoneOnCapacityExhaustion() throws Exception {
        ComputeEngineComputerLauncher.setSimulateExhaustedZones(PRIMARY_ZONE, FIRST_FALLBACK_ZONE);
        var build = startBuildAndWaitForAgent();
        var workerNodeName = getWorkerNodeName(build);
        var instance = client.getInstance(PROJECT_ID, SECOND_FALLBACK_ZONE, workerNodeName);
        assertThat(
                "instance should have landed in the second fallback zone after two exhausted zones",
                nameFromSelfLink(instance.getZone()),
                is(SECOND_FALLBACK_ZONE));
        completeBuild(build);
    }

    @Test
    @ConfiguredWithCode("fallback-config-casc.yml")
    public void testProvisioningFallsBackToSecondInstanceConfigOnCapacityExhaustion() throws Exception {
        ComputeEngineComputerLauncher.setSimulateExhaustedZones(PRIMARY_ZONE);
        var build = startBuildAndWaitForAgent();
        var workerNodeName = getWorkerNodeName(build);
        var instance = client.getInstance(PROJECT_ID, FALLBACK_CONFIG_ZONE, workerNodeName);
        assertThat(
                "instance should have landed in the second instance configuration's zone",
                nameFromSelfLink(instance.getZone()),
                is(FALLBACK_CONFIG_ZONE));
        completeBuild(build);
    }

    @Test
    @ConfiguredWithCode("fallback-cloud-casc.yml")
    public void testProvisioningFallsBackToSecondCloudOnCapacityExhaustion() throws Exception {
        ComputeEngineComputerLauncher.setSimulateExhaustedZones(PRIMARY_ZONE);
        var build = startBuildAndWaitForAgent();
        var workerNodeName = getWorkerNodeName(build);
        var instance = client.getInstance(PROJECT_ID, FALLBACK_CLOUD_ZONE, workerNodeName);
        assertThat(
                "instance should have landed in the fallback cloud's zone",
                nameFromSelfLink(instance.getZone()),
                is(FALLBACK_CLOUD_ZONE));
        completeBuild(build);
    }

    private WorkflowRun startBuildAndWaitForAgent() throws Exception {
        var p = j.createProject(WorkflowJob.class, "fallback-test");
        p.setDefinition(new CpsFlowDefinition("node('" + LABEL + "') { semaphore 'fallback' }", true));
        var build = p.scheduleBuild2(0).waitForStart();
        SemaphoreStep.waitForStart("fallback/1", build);
        return build;
    }

    private String getWorkerNodeName(WorkflowRun build) throws Exception {
        var buildLog = j.getLog(build);
        var workerNodeName = j.jenkins.getNodes().stream()
                .map(Node::getNodeName)
                .filter(name -> buildLog.contains("Running on " + name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Build log does not contain 'Running on <agent>'"));
        log.info("Agent provisioned: " + workerNodeName);
        return workerNodeName;
    }

    private void completeBuild(WorkflowRun build) throws Exception {
        SemaphoreStep.success("fallback/1", null);
        j.waitForCompletion(build);
        j.assertBuildStatusSuccess(build);
    }
}
