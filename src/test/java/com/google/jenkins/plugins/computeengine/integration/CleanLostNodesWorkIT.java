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
import static org.junit.Assert.assertNotEquals;

import com.google.common.collect.ImmutableList;
import com.google.jenkins.plugins.computeengine.CleanLostNodesWork;
import com.google.jenkins.plugins.computeengine.ComputeEngineCloud;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.PrefixedOutputStream;
import org.jvnet.hudson.test.RealJenkinsRule;
import org.jvnet.hudson.test.TailLog;

public class CleanLostNodesWorkIT {
    private static final Logger LOGGER = Logger.getLogger(CleanLostNodesWorkIT.class.getName());
    private static final String RECORDER_CLASS_NAME = CleanLostNodesWork.class.getName();
    /*
    Provisioning a Cloud VM encounters two primary delays:
    1. Initiation Delay: The plugin's initiation of the request is slow and requires optimization.
    2. Creation Time: The VM undergoes creation and must reach a 'running' state.

    During these phases, the VM's timestamp label retains its initial request value.
    This can lead to scenarios where, before the current controller's CleanLostNodesWork can update the label,
    another controller may mistakenly identify and delete the VM as an orphan.

    To solve this race condition from happening, we need to set sufficiently higher value for the recurrence period,
    which prevents the race condition as well as doesn't make the tests too slow (which are already slow).

    Based on tests conducted with intervals of 30s, 45s, and 60s, a 60s delay currently proves most effective.
    */
    private static final long CLEAN_LOST_NODES_WORK_RECURRENCE_PERIOD = 60 * 1000;
    private static final Map<String, String> GOOGLE_LABELS = getLabel(CleanLostNodesWorkIT.class);

    @Rule
    public RealJenkinsRule rj1 = new RealJenkinsRule().withColor(PrefixedOutputStream.Color.BLUE);

    @Rule
    public RealJenkinsRule rj2 = new RealJenkinsRule().withColor(PrefixedOutputStream.Color.RED);

    @Before
    public void init() throws Throwable {
        for (var rj : List.of(rj1, rj2)) {
            rj.javaOptions("-D" + CleanLostNodesWork.class.getName() + ".recurrencePeriod="
                            + CLEAN_LOST_NODES_WORK_RECURRENCE_PERIOD)
                    .withLogger(CleanLostNodesWork.class, Level.FINEST);
            rj.startJenkins();
            rj.runRemotely(r -> {
                initCredentials(r);
                var cloud = initCloud(r);
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
        rj1.runRemotely(j -> {
            var p1 = createPipeline(j);
            try (var tail = new TailLog(j, "p1", 1).withColor(PrefixedOutputStream.Color.MAGENTA)) {
                j.buildAndAssertSuccess(p1);
                tail.waitForCompletion();
                RealJenkinsLogUtil.assertLogContains(
                        RECORDER_CLASS_NAME,
                        "Found 1 running remote instances",
                        "Found 1 local instances",
                        "Updated label for instance");
                RealJenkinsLogUtil.assertLogDoesNotContain(
                        RECORDER_CLASS_NAME, "isOrphan: true", "Removing orphaned instance");
            }
        });
        rj2.runRemotely(j -> {
            RealJenkinsLogUtil.assertLogContains(
                    RECORDER_CLASS_NAME, "Found 1 running remote instances", "Found 0 local instances");
            RealJenkinsLogUtil.assertLogDoesNotContain(
                    RECORDER_CLASS_NAME,
                    "Found 1 local instances",
                    "Updated label for instance",
                    "isOrphan: true",
                    "Removing orphaned instance");
        });
    }

    @Test
    public void testLostNodeCleanedUpBySecondController() throws Throwable {
        rj1.runRemotely(j -> {
            var p1 = createPipeline(j);
            try (var tail = new TailLog(j, "p1", 1).withColor(PrefixedOutputStream.Color.MAGENTA)) {
                var run = p1.scheduleBuild2(0).waitForStart();
                await().timeout(4, TimeUnit.MINUTES).until(() -> run.getLog().contains("first sleep done"));
                LOGGER.info("Build is already running, can proceed to stopping jenkins to make the agent a lost VM");
                RealJenkinsLogUtil.assertLogContains(
                        RECORDER_CLASS_NAME,
                        "Found 1 running remote instances",
                        "Found 1 local instances",
                        "Updated label for instance");
                RealJenkinsLogUtil.assertLogDoesNotContain(
                        RECORDER_CLASS_NAME, "isOrphan: true", "Removing orphaned instance");
            }
        });
        rj1.stopJenkins();

        rj2.runRemotely(j -> {
            var cloud = (ComputeEngineCloud) j.jenkins.clouds.getByName("gce-integration");
            assertEquals(
                    "VM is still there",
                    1,
                    cloud.getClient()
                            .listInstancesWithLabel(cloud.getProjectId(), GOOGLE_LABELS)
                            .size());

            LOGGER.info("test sleeps for " + getSleepSeconds() + " seconds; so that the lost VM is detected by the "
                    + "second controller and it is deleted");
            TimeUnit.SECONDS.sleep(getSleepSeconds());
            LOGGER.info("proceeding after sleep");

            var instances = cloud.getClient().listInstancesWithLabel(cloud.getProjectId(), GOOGLE_LABELS);
            if (!instances.isEmpty()) {
                assertNotEquals(
                        "VM is not running",
                        "RUNNING",
                        cloud.getClient()
                                .listInstancesWithLabel(cloud.getProjectId(), GOOGLE_LABELS)
                                .get(0)
                                .getStatus());
            }
            await("VM didn't get removed even after waiting 2 minutes after it was stopped")
                    .timeout(2, TimeUnit.MINUTES)
                    .until(() -> cloud.getClient()
                            .listInstancesWithLabel(cloud.getProjectId(), GOOGLE_LABELS)
                            .isEmpty());
            RealJenkinsLogUtil.assertLogContains(
                    RECORDER_CLASS_NAME,
                    "Found 1 running remote instances",
                    "Found 0 local instances",
                    "isOrphan: true",
                    "Removing orphaned instance");
            RealJenkinsLogUtil.assertLogDoesNotContain(
                    RECORDER_CLASS_NAME, "Found 1 local instances", "Updated label for instance");
        });
    }

    private static WorkflowJob createPipeline(JenkinsRule j) throws IOException {
        var p1 = j.createProject(WorkflowJob.class, "p1");
        /* Sleep the pipeline for a duration that ensures the periodic task runs `CleanLostNodesWork.LOST_MULTIPLIER
        + 1` times. This guarantees that if the VM is to be deleted, it will be deleted. The sleep is split into two
        parts so that the same pipeline can be used in both tests as of now. */
        String pipelineDefinition = "node('integration') {\n" + "echo 'do first sleep'\n" + "sleep "
                + CLEAN_LOST_NODES_WORK_RECURRENCE_PERIOD / 1000 + "\n"
                + "echo 'first sleep done'\n" + "sleep "
                + (getSleepSeconds() - CLEAN_LOST_NODES_WORK_RECURRENCE_PERIOD / 1000) + "\n" + "echo 'sleep done'\n"
                + "}";
        p1.setDefinition(new CpsFlowDefinition(pipelineDefinition, true));
        return p1;
    }

    private static long getSleepSeconds() {
        return CLEAN_LOST_NODES_WORK_RECURRENCE_PERIOD * (CleanLostNodesWork.LOST_MULTIPLIER + 1) / 1000;
    }
}
