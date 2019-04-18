package com.google.jenkins.plugins.computeengine.integration;

import com.google.api.services.compute.model.Instance;
import com.google.jenkins.plugins.computeengine.ComputeEngineCloud;
import com.google.jenkins.plugins.computeengine.client.ComputeClient;
import hudson.model.labels.LabelAtom;
import hudson.slaves.NodeProvisioner;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.logging.Logger;

import static com.google.jenkins.plugins.computeengine.integration.ITUtil.PROJECT_ID;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.ZONE;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.getLabel;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initClient;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initCredentials;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.teardownResources;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ConfigAsCodeTestIT {
    private static Logger log = Logger.getLogger(ConfigAsCodeTestIT.class.getName());
    @ClassRule
    public static JenkinsRule jenkinsRule = new JenkinsConfiguredWithCodeRule();
    private static ComputeClient client;
    private static Map<String, String> label = getLabel(ComputeEngineCloudMultipleLabelsIT.class);

    @BeforeClass
    public static void init() throws Exception {
        log.info("init");
        client = initClient(jenkinsRule, label, log);
    }

    @AfterClass
    public static void teardown() throws IOException {
        teardownResources(client, label, log);
    }

    @Test(timeout = 300000)
    @ConfiguredWithCode("configuration-as-code-it.yml")
    public void testWorkerCreated() throws Exception {
        initCredentials(jenkinsRule);

        ComputeEngineCloud cloud = (ComputeEngineCloud) jenkinsRule.jenkins.clouds.getByName("gce-integration");
        // Add a new node
        Collection<NodeProvisioner.PlannedNode> planned = cloud.provision(new LabelAtom("jenkins-agent"), 1);

        // There should be a planned node
        assertEquals(1, planned.size());

        String name = planned.iterator().next().displayName;

        // Wait for the node creation to finish
        planned.iterator().next().future.get();

        Instance instance = client.getInstance(PROJECT_ID, ZONE, name);

        assertNotNull(instance);
    }
}
