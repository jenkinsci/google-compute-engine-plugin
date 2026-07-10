package com.google.jenkins.plugins.computeengine.integration;

import static com.google.jenkins.plugins.computeengine.integration.ITUtil.LABEL;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.NULL_TEMPLATE;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.NUM_EXECUTORS;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.PROJECT_ID;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.ZONE;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.getLabel;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initCloud;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initCredentials;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.instanceConfigurationBuilder;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.teardownResources;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.common.collect.ImmutableList;
import com.google.jenkins.plugins.computeengine.CleanLostNodesWork;
import com.google.jenkins.plugins.computeengine.ComputeEngineCloud;
import com.google.jenkins.plugins.computeengine.ComputeEngineInstance;
import hudson.ExtensionList;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.PrefixedOutputStream;
import org.jvnet.hudson.test.RealJenkinsRule;

public class CleanLostNodesWorkIT {
    private static final Logger LOGGER = Logger.getLogger(CleanLostNodesWorkIT.class.getName());
    private static final String RECORDER_CLASS_NAME = CleanLostNodesWork.class.getName();
    private static final int CLEAN_LOST_NODE_WORK_RECURRENCE_SECONDS = 5;
    private static final String RECURRENCE_PERIOD_PROP = "-D" + CleanLostNodesWork.class.getName()
            + ".recurrencePeriod=" + CLEAN_LOST_NODE_WORK_RECURRENCE_SECONDS * 1000;
    private static final Duration PERIODIC_TASK_2_EXECUTIONS =
            Duration.ofSeconds(CLEAN_LOST_NODE_WORK_RECURRENCE_SECONDS * 2);
    // orphan multiplier is 3, so by 4 iterations, orphan is detected.
    private static final Duration PERIODIC_TASK_4_EXECUTIONS =
            Duration.ofSeconds(CLEAN_LOST_NODE_WORK_RECURRENCE_SECONDS * 4);

    private static final Map<String, String> GOOGLE_LABELS = getLabel(CleanLostNodesWorkIT.class);

    @Rule
    public RealJenkinsRule rj1 = new RealJenkinsRule().withColor(PrefixedOutputStream.Color.BLUE);

    @Rule
    public RealJenkinsRule rj2 = new RealJenkinsRule().withColor(PrefixedOutputStream.Color.RED);

    @Before
    public void init() throws Throwable {
        for (var rj : List.of(rj1, rj2)) {
            rj.javaOptions(RECURRENCE_PERIOD_PROP).withLogger(CleanLostNodesWork.class, Level.FINEST);
            rj.startJenkins();
            rj.runRemotely(r -> {
                ExtensionList.lookup(StepDescriptor.class).add(new SemaphoreStep.DescriptorImpl());
                initCredentials(r);
                var cloud = initCloud(r);
                cloud.setNoDelayProvisioning(true);
                var instanceConfig = instanceConfigurationBuilder()
                        .numExecutorsStr(NUM_EXECUTORS)
                        .labels(LABEL)
                        .oneShot(true)
                        .createSnapshot(false)
                        .template(NULL_TEMPLATE)
                        .googleLabels(GOOGLE_LABELS)
                        .cloud(cloud)
                        .build();
                cloud.setConfigurations(ImmutableList.of(instanceConfig));
                RealJenkinsLogUtil.setupLogRecorder(RECORDER_CLASS_NAME);
            });
        }
    }

    @After
    public void tearDown() throws Throwable {
        rj2.runRemotely(j -> {
            j.waitUntilNoActivity();
            var cloud = (ComputeEngineCloud) j.jenkins.clouds.getByName("gce-integration");
            teardownResources(cloud.getClient(), GOOGLE_LABELS, LOGGER);
        });
    }

    @Test
    public void testOrphanedVmDeletedByOwningController() throws Throwable {
        rj1.runRemotely(j -> {
            var p1 = createPipeline(j);
            var run = p1.scheduleBuild2(0).waitForStart();
            SemaphoreStep.waitForStart("agentRunning/1", run);
            await("CleanLostNodesWork should see and label the local instance")
                    .timeout(PERIODIC_TASK_2_EXECUTIONS)
                    .untilAsserted(CleanLostNodesWorkIT::assertLocalControllerKnowsAgent);

            assertEquals("Expecting only 1 agent node", 1, j.jenkins.getNodes().size());
            var agentNode = (ComputeEngineInstance) Jenkins.get().getNodes().get(0);
            var agentName = agentNode.getNodeName();

            // make the VM orphan
            agentNode.skipGcpTerminateForTesting = true;
            Jenkins.get().removeNode(agentNode);

            var cloud = (ComputeEngineCloud) Jenkins.get().clouds.getByName("gce-integration");
            var instance = cloud.getClient().getInstance(PROJECT_ID, ZONE, agentName);
            LOGGER.info("Labels on the VM are: " + instance.getLabels());

            TimeUnit.SECONDS.sleep(PERIODIC_TASK_4_EXECUTIONS.toSeconds());
            assertOwningControllerDeletedOrphan();

            // wait for max 2 minutes and verify instance is not there in gcp
            await().timeout(2, TimeUnit.MINUTES).until(() -> {
                try {
                    cloud.getClient().getInstance(PROJECT_ID, ZONE, agentName);
                    return false;
                } catch (GoogleJsonResponseException e) {
                    assertThat(e.getMessage(), containsString("404 Not Found"));
                    LOGGER.info("error message is: " + e.getMessage());
                    return true;
                }
            });
        });
    }

    @Test
    public void testNodeInUseWontDeleteByOtherController() throws Throwable {
        rj1.runRemotely(j -> {
            var p1 = createPipeline(j);
            var run = p1.scheduleBuild2(0).waitForStart();
            SemaphoreStep.waitForStart("agentRunning/1", run);
            LOGGER.info("Agent is running, waiting for CleanLostNodesWork to observe it");
            await("CleanLostNodesWork should see the local instance")
                    .timeout(PERIODIC_TASK_2_EXECUTIONS)
                    .untilAsserted(CleanLostNodesWorkIT::assertLocalControllerKnowsAgent);
        });
        rj2.runRemotely(j -> {
            await("rj2 CleanLostNodesWork should have seen the running VM")
                    .timeout(PERIODIC_TASK_2_EXECUTIONS)
                    .untilAsserted(CleanLostNodesWorkIT::assertOtherControllerDidNotFindOrDeleteVm);
        });

        // complete the build normally.
        rj1.runRemotely(j -> {
            var run = j.jenkins.getItemByFullName("p1", WorkflowJob.class).getBuildByNumber(1);
            SemaphoreStep.success("agentRunning/1", null);
            j.waitForCompletion(run);
            j.assertBuildStatusSuccess(run);
        });
    }

    @Test
    public void testOtherControllerDoesNotCleanOrphanedVm() throws Throwable {
        rj1.runRemotely(j -> {
            var p1 = createPipeline(j);
            var run = p1.scheduleBuild2(0).waitForStart();
            SemaphoreStep.waitForStart("agentRunning/1", run);
            LOGGER.info("Build is already running, can proceed to stopping jenkins to make the agent a lost VM");
            await("CleanLostNodesWork should see the local instance before stopping rj1")
                    .timeout(PERIODIC_TASK_2_EXECUTIONS)
                    .untilAsserted(CleanLostNodesWorkIT::assertLocalControllerKnowsAgent);
        });
        // forcefully kill rj1 to make the agent orphan
        rj1.stopJenkinsForcibly();

        // wait for more than 3x periodic work execution
        TimeUnit.SECONDS.sleep(PERIODIC_TASK_4_EXECUTIONS.toSeconds());

        rj2.runRemotely(j -> {
            var cloud = (ComputeEngineCloud) j.jenkins.clouds.getByName("gce-integration");
            assertEquals(
                    "rj2 does not see or delete the VM that belonged to rj1, VM still exists",
                    1,
                    cloud.getClient()
                            .listInstancesWithLabel(cloud.getProjectId(), GOOGLE_LABELS)
                            .size());
            assertOtherControllerDidNotFindOrDeleteVm();
        });
    }

    private static WorkflowJob createPipeline(JenkinsRule j) throws Exception {
        var p1 = j.createProject(WorkflowJob.class, "p1");
        p1.setDefinition(new CpsFlowDefinition("node('integration') { semaphore 'agentRunning' }", true));
        return p1;
    }

    private static void assertOwningControllerDeletedOrphan() {
        RealJenkinsLogUtil.assertLogContains(RECORDER_CLASS_NAME, "isOrphan: true", "Removing orphaned instance");
    }

    private static void assertLocalControllerKnowsAgent() {
        RealJenkinsLogUtil.assertLogContains(
                RECORDER_CLASS_NAME,
                "running remote instances",
                "Found 1 local instances",
                "Updated label for instance");
        RealJenkinsLogUtil.assertLogDoesNotContain(RECORDER_CLASS_NAME, "isOrphan: true", "Removing orphaned instance");
    }

    private static void assertOtherControllerDidNotFindOrDeleteVm() {
        RealJenkinsLogUtil.assertLogContains(
                RECORDER_CLASS_NAME, "Found 0 running remote instances", "Found 0 local instances");
        RealJenkinsLogUtil.assertLogDoesNotContain(
                RECORDER_CLASS_NAME,
                "Found 1 local instances",
                "Updated label for instance",
                "isOrphan: true",
                "Removing orphaned instance");
    }
}
