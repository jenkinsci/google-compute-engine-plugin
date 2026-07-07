package com.google.jenkins.plugins.computeengine.integration;

import static com.google.jenkins.plugins.computeengine.integration.ITUtil.LABEL;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.NULL_TEMPLATE;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.NUM_EXECUTORS;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.getLabel;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initCloud;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initCredentials;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.instanceConfigurationBuilder;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.teardownResources;
import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertEquals;

import com.google.common.collect.ImmutableList;
import com.google.jenkins.plugins.computeengine.CleanLostNodesWork;
import com.google.jenkins.plugins.computeengine.ComputeEngineCloud;
import hudson.ExtensionList;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.After;
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

    private static final String RESTRICT_TO_THIS_CONTROLLER_PROP =
            "-D" + CleanLostNodesWork.class.getName() + ".restrictToThisController=true";
    private static final Map<String, String> GOOGLE_LABELS = getLabel(CleanLostNodesWorkIT.class);

    @Rule
    public RealJenkinsRule rj1 = new RealJenkinsRule().withColor(PrefixedOutputStream.Color.BLUE);

    @Rule
    public RealJenkinsRule rj2 = new RealJenkinsRule().withColor(PrefixedOutputStream.Color.RED);

    private void init(String... extraProps) throws Throwable {
        var allProps = Stream.concat(Stream.of(RECURRENCE_PERIOD_PROP), Arrays.stream(extraProps))
                .toArray(String[]::new);
        for (var rj : List.of(rj1, rj2)) {
            rj.javaOptions(allProps).withLogger(CleanLostNodesWork.class, Level.FINEST);
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
    public void testNodeInUseWontDeleteByOtherController() throws Throwable {
        init();
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
                    .untilAsserted(CleanLostNodesWorkIT::assertOtherControllerDidNotDeleteVm);
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
    public void testSecondControllerDoesNotCleanUpLostNodeWhenRestrictionEnabled() throws Throwable {
        init(RESTRICT_TO_THIS_CONTROLLER_PROP);
        rj1.runRemotely(j -> {
            var p1 = createPipeline(j);
            var run = p1.scheduleBuild2(0).waitForStart();
            SemaphoreStep.waitForStart("agentRunning/1", run);
            LOGGER.info("Agent is running, waiting for CleanLostNodesWork to recognize it as this controller's own");
            await("rj1 CleanLostNodesWork should recognize the instance as its own")
                    .timeout(PERIODIC_TASK_2_EXECUTIONS)
                    .untilAsserted(CleanLostNodesWorkIT::assertLocalControllerOwnsAgentUnderRestriction);
        });

        // second controller ignores the orphan evaluation for the agents of other controller,
        // the agent is still in use in a build
        assertRj2SkipsRestrictedVm();

        // kill rj1 forcefully, make the agent orphan
        rj1.stopJenkinsForcibly();

        // wait for 4 cycles of CleanLostNodesWork, the orphan is never touched by rj2 as restriction is enabled
        TimeUnit.SECONDS.sleep(PERIODIC_TASK_4_EXECUTIONS.toSeconds());
        assertRj2SkipsRestrictedVm();
    }

    /** rj2 sees the instance but the restriction marks it as not its own, so the VM survives untouched. */
    private void assertRj2SkipsRestrictedVm() throws Throwable {
        rj2.runRemotely(j -> {
            await("rj2 should have evaluated the restriction and skipped the VM")
                    .timeout(PERIODIC_TASK_2_EXECUTIONS)
                    .untilAsserted(CleanLostNodesWorkIT::assertOtherControllerSkippedRestrictedVm);
            var cloud = (ComputeEngineCloud) j.jenkins.clouds.getByName("gce-integration");
            assertEquals(
                    "VM should not be removed when owned by a different controller",
                    1,
                    cloud.getClient()
                            .listInstancesWithLabel(cloud.getProjectId(), GOOGLE_LABELS)
                            .size());
        });
    }

    @Test
    public void testLostNodeCleanedUpBySecondController() throws Throwable {
        init();
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

        // VM is still in the cloud
        rj2.runRemotely(j -> {
            var cloud = (ComputeEngineCloud) j.jenkins.clouds.getByName("gce-integration");
            assertEquals(
                    "VM is still there",
                    1,
                    cloud.getClient()
                            .listInstancesWithLabel(cloud.getProjectId(), GOOGLE_LABELS)
                            .size());
        });

        // wait for 4 cycle of clean lost node work, agent should be marked as orphan and deleted by then
        TimeUnit.SECONDS.sleep(PERIODIC_TASK_4_EXECUTIONS.toSeconds());
        rj2.runRemotely(j -> {
            assertOtherControllerCleanedUpOrphan();

            var cloud = (ComputeEngineCloud) j.jenkins.clouds.getByName("gce-integration");
            // deletion of VM in cloud may take time, so wait for 2 minutes max.
            await("No VMs in the cloud").timeout(Duration.ofMinutes(2)).until(() -> cloud.getClient()
                    .listInstancesWithLabel(cloud.getProjectId(), GOOGLE_LABELS)
                    .isEmpty());
        });
    }

    private static WorkflowJob createPipeline(JenkinsRule j) throws Exception {
        var p1 = j.createProject(WorkflowJob.class, "p1");
        p1.setDefinition(new CpsFlowDefinition("node('integration') { semaphore 'agentRunning' }", true));
        return p1;
    }

    /** rj1 (or the controller running the agent) sees its own instance and labels it; no orphan logic fires. */
    private static void assertLocalControllerKnowsAgent() {
        RealJenkinsLogUtil.assertLogContains(
                RECORDER_CLASS_NAME,
                "running remote instances",
                "Found 1 local instances",
                "Updated label for instance",
                "Cleanup lost node restriction is disabled");
        RealJenkinsLogUtil.assertLogDoesNotContain(RECORDER_CLASS_NAME, "isOrphan: true", "Removing orphaned instance");
    }

    /** Same as {@link #assertLocalControllerKnowsAgent}, but with the restriction enabled and matching this controller. */
    private static void assertLocalControllerOwnsAgentUnderRestriction() {
        RealJenkinsLogUtil.assertLogContains(
                RECORDER_CLASS_NAME,
                "running remote instances",
                "Found 1 local instances",
                "Updated label for instance",
                "Cleanup lost node restriction is enabled",
                "is ours=true");
        RealJenkinsLogUtil.assertLogDoesNotContain(RECORDER_CLASS_NAME, "isOrphan: true", "Removing orphaned instance");
    }

    /** The other controller sees the instance but the restriction marks it as not its own, so it's left alone. */
    private static void assertOtherControllerSkippedRestrictedVm() {
        RealJenkinsLogUtil.assertLogContains(
                RECORDER_CLASS_NAME, "Cleanup lost node restriction is enabled", "is ours=false");
        RealJenkinsLogUtil.assertLogDoesNotContain(RECORDER_CLASS_NAME, "isOrphan: true", "Removing orphaned instance");
    }

    /** The other controller has no local node for the instance, but it's still in use, so it doesn't delete it. */
    private static void assertOtherControllerDidNotDeleteVm() {
        RealJenkinsLogUtil.assertLogContains(
                RECORDER_CLASS_NAME,
                "running remote instances",
                "Found 0 local instances",
                "Cleanup lost node restriction is disabled");
        RealJenkinsLogUtil.assertLogDoesNotContain(
                RECORDER_CLASS_NAME,
                "Found 1 local instances",
                "Updated label for instance",
                "isOrphan: true",
                "Removing orphaned instance");
    }

    /** The other controller has no local node for the instance, and it's stale, so it deletes it as an orphan. */
    private static void assertOtherControllerCleanedUpOrphan() {
        RealJenkinsLogUtil.assertLogContains(
                RECORDER_CLASS_NAME,
                "running remote instances",
                "Found 0 local instances",
                "Cleanup lost node restriction is disabled",
                "isOrphan: true",
                "Removing orphaned instance");
        RealJenkinsLogUtil.assertLogDoesNotContain(
                RECORDER_CLASS_NAME, "Found 1 local instances", "Updated label for instance");
    }
}
