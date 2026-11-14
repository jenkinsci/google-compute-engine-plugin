package com.google.jenkins.plugins.computeengine.integration;

import static com.google.jenkins.plugins.computeengine.integration.ITUtil.PROJECT_ID;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.ZONE;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.execute;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.getLabel;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initClient;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initCredentials;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.teardownResources;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.windows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.google.api.services.compute.model.Instance;
import com.google.cloud.graphite.platforms.plugin.client.ComputeClient;
import com.google.jenkins.plugins.computeengine.ComputeEngineCloud;
import hudson.model.FreeStyleProject;
import hudson.model.labels.LabelAtom;
import hudson.slaves.NodeProvisioner.PlannedNode;
import hudson.tasks.Builder;
import io.jenkins.plugins.casc.ConfigurationAsCode;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.BuildWatcherExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Integration tests for Jenkins agents configured with Configuration as Code with a non-standard
 * java path.
 */
@WithJenkins
@Timeout(value = 15, unit = TimeUnit.MINUTES)
class ConfigAsCodeNonStandardJavaIT {
    private static final Logger log = Logger.getLogger(ConfigAsCodeNonStandardJavaIT.class.getName());

    private static JenkinsRule jenkinsRule;

    @RegisterExtension
    private static final BuildWatcherExtension bw = new BuildWatcherExtension();

    private static ComputeClient client;
    private static final Map<String, String> label = getLabel(ConfigAsCodeNonStandardJavaIT.class);

    @BeforeAll
    static void init(JenkinsRule rule) throws Exception {
        assumeFalse(windows);
        log.info("init");
        jenkinsRule = rule;
        initCredentials(jenkinsRule);
        client = initClient(jenkinsRule, label, log);
    }

    @AfterAll
    static void teardown() throws IOException {
        teardownResources(client, label, log);
    }

    @Test
    void testNonStandardJavaWorkerCreated() throws Exception {
        assumeFalse(windows);
        ConfigurationAsCode.get()
                .configure(this.getClass()
                        .getResource("configuration-as-code-non-standard-java-it.yml")
                        .toString());
        ComputeEngineCloud cloud = (ComputeEngineCloud) jenkinsRule.jenkins.clouds.getByName("gce-integration");

        // Should be 1 configuration
        assertEquals(1, cloud.getConfigurations().size());
        cloud.getConfigurations().get(0).setGoogleLabels(label);

        // Add a new node
        Collection<PlannedNode> planned = cloud.provision(new LabelAtom("integration-non-standard-java"), 1);

        // There should be a planned node
        assertEquals(1, planned.size());
        String name = planned.iterator().next().displayName;

        // Wait for the node creation to finish
        planned.iterator().next().future.get();
        Instance instance = client.getInstance(PROJECT_ID, ZONE, name);
        assertNotNull(instance);

        FreeStyleProject project = jenkinsRule.createFreeStyleProject();
        Builder step = execute(Commands.ECHO, "works");
        project.getBuildersList().add(step);
        project.setAssignedLabel(new LabelAtom("integration-non-standard-java"));
        jenkinsRule.buildAndAssertSuccess(project);
    }
}
