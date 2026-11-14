package com.google.jenkins.plugins.computeengine.integration;

import static com.google.jenkins.plugins.computeengine.integration.ITUtil.CONFIG_AS_CODE_PATH;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.PROJECT_ID;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.ZONE;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.getLabel;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initClient;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initCredentials;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.teardownResources;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.api.services.compute.model.Instance;
import com.google.cloud.graphite.platforms.plugin.client.ComputeClient;
import com.google.jenkins.plugins.computeengine.ComputeEngineCloud;
import hudson.model.labels.LabelAtom;
import hudson.slaves.NodeProvisioner;
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
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
@Timeout(value = 30, unit = TimeUnit.MINUTES)
class ConfigAsCodeTestIT {
    private static final Logger log = Logger.getLogger(ConfigAsCodeTestIT.class.getName());

    private static JenkinsRule jenkinsRule;

    private static ComputeClient client;
    private static Map<String, String> label = getLabel(ConfigAsCodeTestIT.class);
    private static final String DISABLE_NO_DELAY_SYSTEM_PROPERTY =
            "com.google.jenkins.plugins.computeengine.disableNoDelayProvisioning";

    @BeforeAll
    static void init(JenkinsRule rule) throws Exception {
        log.info("init");
        jenkinsRule = rule;
        ConfigurationAsCode.get()
                .configure(ConfigAsCodeTestIT.class
                        .getResource(CONFIG_AS_CODE_PATH)
                        .toString());
        initCredentials(jenkinsRule);
        client = initClient(jenkinsRule, label, log);
    }

    @AfterAll
    static void teardown() throws IOException {
        teardownResources(client, label, log);
    }

    @Test
    void testWorkerCreatedWithoutNoDelayProvision() throws Exception {
        // Disable NoDelayProvision with the environment variable flag
        System.setProperty(DISABLE_NO_DELAY_SYSTEM_PROPERTY, "true");
        testWorkerCreated();
    }

    @Test
    void testWorkerCreatedWithNoDelayProvision() throws Exception {
        // Enable NoDelayProvision with the environment variable flag
        System.setProperty(DISABLE_NO_DELAY_SYSTEM_PROPERTY, "false");
        testWorkerCreated();
    }

    private void testWorkerCreated() throws Exception {
        ComputeEngineCloud cloud = (ComputeEngineCloud) jenkinsRule.jenkins.clouds.getByName("gce-integration");

        // Should be 1 configuration
        assertEquals(1, cloud.getConfigurations().size());
        cloud.getConfigurations().get(0).setGoogleLabels(label);

        // Add a new node
        Collection<NodeProvisioner.PlannedNode> planned = cloud.provision(new LabelAtom("integration"), 1);

        // There should be a planned node
        await().timeout(2, TimeUnit.MINUTES).until(() -> planned.size() == 1);
        String name = planned.iterator().next().displayName;

        // Wait for the node creation to finish
        planned.iterator().next().future.get();
        Instance instance = client.getInstance(PROJECT_ID, ZONE, name);
        assertNotNull(instance);
    }
}
