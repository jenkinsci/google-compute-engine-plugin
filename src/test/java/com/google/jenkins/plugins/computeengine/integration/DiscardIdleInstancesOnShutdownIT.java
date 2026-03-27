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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import com.google.jenkins.plugins.computeengine.ComputeEngineCloud;
import com.google.jenkins.plugins.computeengine.ComputeEngineInstance;
import com.google.jenkins.plugins.computeengine.DiscardIdleInstancesTerminator;
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
import org.jvnet.hudson.test.PrefixedOutputStream;
import org.jvnet.hudson.test.RealJenkinsRule;
import org.jvnet.hudson.test.TailLog;

/**
 * Integration test verifying that idle GCE instances are terminated during Jenkins shutdown
 * when {@code terminateIdleDuringShutdown} is enabled on the instance configuration.
 * <p>
 * Uses {@link RealJenkinsRule} so that stopping Jenkins triggers the {@link DiscardIdleInstancesTerminator#discardIdleInstances()}
 * via the {@code @Terminator} lifecycle hook.
 */
public class DiscardIdleInstancesOnShutdownIT {
    private static final Logger LOGGER = Logger.getLogger(DiscardIdleInstancesOnShutdownIT.class.getName());
    private static final Map<String, String> GOOGLE_LABELS = getLabel(DiscardIdleInstancesOnShutdownIT.class);

    @Rule
    public RealJenkinsRule rj = new RealJenkinsRule().withColor(PrefixedOutputStream.Color.BLUE);

    @Before
    public void init() throws Throwable {
        rj.withLogger(DiscardIdleInstancesTerminator.class, Level.FINE);
        rj.startJenkins();
        rj.run(r -> {
            initCredentials(r);
            var cloud = initCloud(r);
            var instanceConfig = instanceConfigurationBuilder()
                    .numExecutorsStr(NUM_EXECUTORS)
                    .labels(LABEL)
                    .oneShot(false)
                    .createSnapshot(false)
                    .template(NULL_TEMPLATE)
                    .googleLabels(GOOGLE_LABELS)
                    .terminateIdleDuringShutdown(true)
                    .cloud(cloud)
                    .build();
            cloud.setConfigurations(List.of(instanceConfig));
        });
    }

    @After
    public void tearDown() throws Throwable {
        rj.runRemotely(j -> {
            var cloud = (ComputeEngineCloud) j.jenkins.clouds.getByName("gce-integration");
            // In the success case, idle instances are already deleted during RealJenkinsRule#stopJenkins.
            // This tearDown exists as a safety net to clean up any VMs left behind if the test fails mid-way.
            teardownResources(cloud.getClient(), GOOGLE_LABELS, LOGGER);
        });
    }

    /**
     * Runs a build that triggers VM provisioning, waits for it to complete (agent becomes idle),
     * agent becomes idle due to oneShot:false, stops Jenkins, restarts it, then verifies both that Jenkins
     * has no GCE agent nodes and that the VM was deleted from GCP.
     */
    @Test
    public void testIdleInstanceTerminatedOnShutdown() throws Throwable {
        rj.runRemotely(j -> {
            var p = j.createProject(WorkflowJob.class, "p1");
            p.setDefinition(new CpsFlowDefinition("node('integration') { echo 'hello' }", true));
            try (var tail = new TailLog(j, "p1", 1).withColor(PrefixedOutputStream.Color.MAGENTA)) {
                j.buildAndAssertSuccess(p);
                tail.waitForCompletion();
            }
            var cloud = (ComputeEngineCloud) j.jenkins.clouds.getByName("gce-integration");
            assertThat(
                    "VM should exist before shutdown",
                    cloud.getClient().listInstancesWithLabel(cloud.getProjectId(), GOOGLE_LABELS),
                    hasSize(1));
        });
        // Stop Jenkins - this triggers the @Terminator which should delete idle instances
        rj.stopJenkins();
        // Restart the same Jenkins and verify the instance is gone
        rj.startJenkins();
        rj.runRemotely(j -> {
            // Jenkins should have no GCE agent nodes after restart
            assertThat(
                    "Jenkins should have no GCE agent nodes after restart",
                    j.jenkins.getNodes().stream()
                            .filter(ComputeEngineInstance.class::isInstance)
                            .toList(),
                    is(empty()));
            // The VM should be gone from GCP
            var cloud = (ComputeEngineCloud) j.jenkins.clouds.getByName("gce-integration");
            await("VM should be deleted from GCP after shutdown")
                    .timeout(2, TimeUnit.MINUTES)
                    .until(
                            () -> cloud.getClient().listInstancesWithLabel(cloud.getProjectId(), GOOGLE_LABELS),
                            is(empty()));
        });
        LOGGER.info("Test completed successfully - idle instance was terminated on shutdown");
    }
}
