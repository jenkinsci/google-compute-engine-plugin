/*
 * Copyright 2018 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.jenkins.plugins.computeengine;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.google.api.services.compute.model.AccessConfig;
import com.google.api.services.compute.model.AttachedDisk;
import com.google.api.services.compute.model.AttachedDiskInitializeParams;
import com.google.api.services.compute.model.Image;
import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.InstanceProperties;
import com.google.api.services.compute.model.InstanceTemplate;
import com.google.api.services.compute.model.Metadata;
import com.google.api.services.compute.model.NetworkInterface;
import com.google.api.services.compute.model.Operation;
import com.google.api.services.compute.model.Tags;
import com.google.common.collect.ImmutableMap;
import com.google.jenkins.plugins.computeengine.client.ClientFactory;
import com.google.jenkins.plugins.computeengine.client.ComputeClient;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotPrivateKeyCredentials;
import com.google.jenkins.plugins.credentials.oauth.ServiceAccountConfig;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Node;
import hudson.model.labels.LabelAtom;
import hudson.slaves.NodeProvisioner;
import hudson.tasks.Builder;
import hudson.tasks.Shell;
import org.awaitility.Awaitility;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.logging.StreamHandler;

import static com.google.common.collect.ImmutableList.of;
import static com.google.jenkins.plugins.computeengine.InstanceConfiguration.METADATA_LINUX_STARTUP_SCRIPT_KEY;
import static com.google.jenkins.plugins.computeengine.InstanceConfiguration.NAT_NAME;
import static com.google.jenkins.plugins.computeengine.InstanceConfiguration.NAT_TYPE;
import static com.google.jenkins.plugins.computeengine.client.ComputeClient.nameFromSelfLink;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ComputeEngineCloudIT {
    private static Logger log = Logger.getLogger(ComputeEngineCloudIT.class.getName());

    private static final String DEB_JAVA_STARTUP_SCRIPT = "#!/bin/bash\n" +
            "/etc/init.d/ssh stop\n" +
            "echo \"deb http://http.debian.net/debian stretch-backports main\" | \\\n" +
            "      sudo tee --append /etc/apt/sources.list > /dev/null\n" +
            "apt-get -y update\n" +
            "apt-get -y install -t stretch-backports openjdk-8-jdk\n" +
            "update-java-alternatives -s java-1.8.0-openjdk-amd64\n" +
            "/etc/init.d/ssh start";

    private static final String CLOUD_NAME = "integration";
    private static final String NAME_PREFIX = "integration";
    private static final String REGION = format("projects/%s/regions/us-west1");
    private static final String ZONE = "us-west1-a";
    private static final String ZONE_BASE = format("projects/%s/zones/" + ZONE);
    private static final String LABEL = "integration";
    private static final String MULTIPLE_LABEL = "integration test";
    private static final String MACHINE_TYPE = ZONE_BASE + "/machineTypes/n1-standard-1";
    private static final String NUM_EXECUTORS = "1";
    private static final String MULTIPLE_NUM_EXECUTORS = "2";
    private static final boolean PREEMPTIBLE = false;
    //  TODO: Write a test to see if min cpu platform worked by picking a higher version?
    private static final String MIN_CPU_PLATFORM = "Intel Broadwell";
    private static final String CONFIG_DESC = "integration";
    private static final String BOOT_DISK_TYPE = ZONE_BASE + "/diskTypes/pd-ssd";
    private static final boolean BOOT_DISK_AUTODELETE = true;
    private static final String BOOT_DISK_PROJECT_ID = "debian-cloud";
    private static final String BOOT_DISK_IMAGE_NAME = "projects/debian-cloud/global/images/family/debian-9";
    private static final String BOOT_DISK_SIZE_GB_STR = "10";
    private static final Node.Mode NODE_MODE = Node.Mode.EXCLUSIVE;
    private static final String ACCELERATOR_NAME = "";
    private static final String ACCELERATOR_COUNT = "";
    private static final String RUN_AS_USER = "jenkins";
    private static final String NULL_TEMPLATE = null;
    private static final String TEMPLATE = format("projects/%s/global/instanceTemplates/test-template");

    private static Map<String, String> INTEGRATION_LABEL;

    static {
        INTEGRATION_LABEL = new HashMap<>();
        INTEGRATION_LABEL.put("integration", "delete");
    }

    private static final String NETWORK_NAME = format("projects/%s/global/networks/default");
    private static final String SUBNETWORK_NAME = "default";
    private static final boolean EXTERNAL_ADDR = true;
    private static final String NETWORK_TAGS = "ssh";
    private static final String SERVICE_ACCOUNT_EMAIL = "";
    private static final String RETENTION_TIME_MINUTES_STR = "";
    private static final String LAUNCH_TIMEOUT_SECONDS_STR = "";

    private static Logger cloudLogger;
    private static Logger clientLogger;
    private static StreamHandler sh;
    private static ByteArrayOutputStream logOutput;

    private static ComputeClient client;
    private static String projectId;


    private static String format(String s) {
        String projectId = System.getenv("GOOGLE_PROJECT_ID");
        if (projectId == null) {
            throw new RuntimeException("GOOGLE_PROJECT_ID env var must be set");
        }
        return String.format(s, projectId);
    }

    @ClassRule
    public static JenkinsRule r = new JenkinsRule();

    @BeforeClass
    public static void init() throws Exception {
        log.info("init");
        logOutput = new ByteArrayOutputStream();
        sh = new StreamHandler(logOutput, new SimpleFormatter());

        // Add a service account credential
        projectId = System.getenv("GOOGLE_PROJECT_ID");
        assertNotNull("GOOGLE_PROJECT_ID env var must be set", projectId);

        String serviceAccountKeyJson = System.getenv("GOOGLE_CREDENTIALS");
        assertNotNull("GOOGLE_CREDENTIALS env var must be set", serviceAccountKeyJson);

        ServiceAccountConfig sac = new StringJsonServiceAccountConfig(serviceAccountKeyJson);
        Credentials c = (Credentials) new GoogleRobotPrivateKeyCredentials(projectId, sac, null);

        CredentialsStore store = new SystemCredentialsProvider.ProviderImpl().getStore(r.jenkins);
        store.addCredentials(Domain.global(), c);

        // Add Cloud plugin
        ComputeEngineCloud gcp = new ComputeEngineCloud(CLOUD_NAME, projectId, projectId, "10", null);

        // Capture log output to make sense of most failures
        cloudLogger = LogManager.getLogManager().getLogger("com.google.jenkins.plugins.computeengine.ComputeEngineCloud");
        if (cloudLogger != null)
            cloudLogger.addHandler(sh);

        assertEquals(0, r.jenkins.clouds.size());
        r.jenkins.clouds.add(gcp);
        assertEquals(1, r.jenkins.clouds.size());

        // Get a compute client for out-of-band calls to GCE
        ClientFactory clientFactory = new ClientFactory(r.jenkins, new ArrayList<DomainRequirement>(), projectId);
        client = clientFactory.compute();
        assertNotNull("ComputeClient can not be null", client);

        // Other logging
        clientLogger = LogManager.getLogManager().getLogger("com.google.jenkins.plugins.computeengine.ComputeClient");
        if (clientLogger != null)
            clientLogger.addHandler(sh);

        deleteIntegrationInstances(true);
    }

    @AfterClass
    public static void teardown() throws Exception {
        log.info("teardown");
        deleteIntegrationInstances(false);
        sh.close();
        log.info(logOutput.toString());
    }

    @After
    public void after() {
        ComputeEngineCloud cloud = (ComputeEngineCloud) r.jenkins.clouds.get(0);
        cloud.configurations.clear();
    }

    @Test
    public void testCredentialsCreated() {
        List<Credentials> creds = new SystemCredentialsProvider.ProviderImpl().getStore(r.jenkins).getCredentials(Domain.global());
        assertEquals(1, creds.size());
    }

    @Test //TODO: Group client tests into their own test class
    public void testGetImage() throws Exception {
        ComputeEngineCloud cloud = (ComputeEngineCloud) r.jenkins.clouds.get(0);
        Image i = cloud.client.getImage("debian-cloud", "debian-9-stretch-v20180820");
        assertNotNull(i);
    }

    @Test(timeout = 300000)
    public void testWorkerCreated() throws Exception {
        //TODO: each test method should probably have its own handler.
        logOutput.reset();

        ComputeEngineCloud cloud = (ComputeEngineCloud) r.jenkins.clouds.get(0);
        InstanceConfiguration ic = validInstanceConfiguration();
        cloud.addConfiguration(ic);
        // Add a new node
        Collection<NodeProvisioner.PlannedNode> planned = cloud.provision(new LabelAtom(LABEL), 1);

        // There should be a planned node
        assertEquals(logs(), 1, planned.size());

        String name = planned.iterator().next().displayName;

        // Wait for the node creation to finish
        planned.iterator().next().future.get();

        // There should be no warning logs
        assertFalse(logs(), logs().contains("WARNING"));

        Instance i = cloud.client.getInstance(projectId, ZONE, name);

        // The created instance should have 3 labels
        assertEquals(logs(), 3, i.getLabels().size());

        // Instance should have a label with key CONFIG_LABEL_KEY and value equal to the config's name prefix
        assertEquals(logs(), ic.namePrefix, i.getLabels().get(ComputeEngineCloud.CONFIG_LABEL_KEY));
        assertEquals(logs(), cloud.getInstanceUniqueId(), i.getLabels().get(ComputeEngineCloud.CLOUD_ID_LABEL_KEY));
    }

    @Test(timeout = 300000)
    public void test1WorkerCreatedFor2Executors() throws Exception {
        logOutput.reset();

        ComputeEngineCloud cloud = (ComputeEngineCloud) r.jenkins.clouds.get(0);
        cloud.addConfiguration(validInstanceConfigurationWithExecutors(MULTIPLE_NUM_EXECUTORS));
        // Add a new node
        Collection<NodeProvisioner.PlannedNode> planned = cloud.provision(new LabelAtom(LABEL), 2);

        // There should be a planned node
        assertEquals(logs(), 1, planned.size());
    }

    @Test(timeout = 300000)
    public void testMultipleLabelsForJob() throws Exception {
        // For a configuration with multiple labels, test if job label matches one of the configuration's labels
        logOutput.reset();

        ComputeEngineCloud cloud = (ComputeEngineCloud) r.jenkins.clouds.get(0);
        InstanceConfiguration ic = validInstanceConfigurationWithLabels(MULTIPLE_LABEL);
        cloud.addConfiguration(ic);
        // Add a new node
        Collection<NodeProvisioner.PlannedNode> planned = cloud.provision(new LabelAtom(LABEL), 1);

        // There should be a planned node
        assertEquals(logs(), 1, planned.size());
    }

    @Test(timeout = 300000)
    public void testMultipleLabelsInConfig() throws Exception {
        // For a configuration with multiple labels, test if job label matches one of the configuration's labels
        logOutput.reset();

        ComputeEngineCloud cloud = (ComputeEngineCloud) r.jenkins.clouds.get(0);
        InstanceConfiguration ic = validInstanceConfigurationWithLabels(MULTIPLE_LABEL);
        cloud.addConfiguration(ic);
        // Add a new node
        Collection<NodeProvisioner.PlannedNode> planned = cloud.provision(new LabelAtom(LABEL), 1);

        String name = planned.iterator().next().displayName;

        planned.iterator().next().future.get();

        String provisionedLabels = r.jenkins.getNode(name).getLabelString();
        // There should be a planned node TODO
        assertEquals(logs(), MULTIPLE_LABEL, provisionedLabels);
    }

    @Test(timeout = 300000, expected = ExecutionException.class)
    public void testWorkerFailed() throws Exception {
        //TODO: each test method should probably have its own handler.
        logOutput.reset();

        ComputeEngineCloud cloud = (ComputeEngineCloud) r.jenkins.clouds.get(0);
        cloud.addConfiguration(invalidInstanceConfiguration());

        // Add a new node
        Collection<NodeProvisioner.PlannedNode> planned = cloud.provision(new LabelAtom(LABEL), 1);

        // There should be a planned node
        assertEquals(logs(), 1, planned.size());

        // Wait for the node creation to fail
        planned.iterator().next().future.get();
    }

    @Test(timeout = 500000)
    public void testOneShotInstances() throws Exception {
        ComputeEngineCloud cloud = (ComputeEngineCloud) r.jenkins.clouds.get(0);
        cloud.addConfiguration(validInstanceConfigurationWithOneShot());

        r.jenkins.getNodesObject().setNodes(Collections.emptyList());

        // Assert that there is 0 nodes
        assertTrue(r.jenkins.getNodes().isEmpty());

        FreeStyleProject project = r.createFreeStyleProject();
        Builder step = new Shell("echo works");
        project.getBuildersList().add(step);
        project.setAssignedLabel(new LabelAtom(LABEL));

        // Enqueue a build of the project, wait for it to complete, and assert success
        FreeStyleBuild build = r.buildAndAssertSuccess(project);

        // Assert that the console log contains the output we expect
        r.assertLogContains("works", build);

        // Assert that there is 0 nodes after job finished
        Awaitility.await().timeout(10, TimeUnit.SECONDS).until(() -> r.jenkins.getNodes().isEmpty());
    }

    @Test(timeout = 300000)
    public void testTemplate() throws Exception {
        ComputeEngineCloud cloud = (ComputeEngineCloud) r.jenkins.clouds.get(0);
        cloud.addConfiguration(validInstanceConfigurationWithTemplate(TEMPLATE));
        try {
            InstanceTemplate instanceTemplate = createTemplate(ImmutableMap.of("test-label", "label-value"));
            client.insertTemplate(cloud.projectId, instanceTemplate);

            // Add a new node
            Collection<NodeProvisioner.PlannedNode> planned = cloud.provision(new LabelAtom(LABEL), 1);

            // There should be a planned node
            assertEquals(logs(), 1, planned.size());

            String name = planned.iterator().next().displayName;

            // Wait for the node creation to finish
            planned.iterator().next().future.get();

            // There should be no warning logs
            assertFalse(logs(), logs().contains("WARNING"));

            Instance instance = client.getInstance(projectId, ZONE, name);
            assertTrue(logs(), instance.getLabels().containsKey("test-label"));
            assertEquals(logs(), String.valueOf(cloud.name.hashCode()), instance.getLabels().get(ComputeEngineCloud.CLOUD_ID_LABEL_KEY));
        } finally {
            try {
                client.deleteTemplate(cloud.projectId, nameFromSelfLink(TEMPLATE));
            } catch (Exception e) {
                // noop
            }
        }
    }

    @Test(timeout = 300000)
    public void testTemplateNoGoogleLabels() throws Exception {
        ComputeEngineCloud cloud = (ComputeEngineCloud) r.jenkins.clouds.get(0);
        cloud.addConfiguration(validInstanceConfigurationWithTemplate(TEMPLATE));
        try {
            InstanceTemplate instanceTemplate = createTemplate(null);
            client.insertTemplate(cloud.projectId, instanceTemplate);

            // Add a new node
            Collection<NodeProvisioner.PlannedNode> planned = cloud.provision(new LabelAtom(LABEL), 1);

            // There should be a successful planned node even without google labels
            assertEquals(logs(), 1, planned.size());
        } finally {
            try {
                client.deleteTemplate(cloud.projectId, nameFromSelfLink(TEMPLATE));
            } catch (Exception e) {
            }
        }
    }

    private static InstanceTemplate createTemplate(Map<String, String> googleLabels) {
        InstanceTemplate instanceTemplate = new InstanceTemplate();
        instanceTemplate.setName(nameFromSelfLink(TEMPLATE));
        InstanceProperties instanceProperties = new InstanceProperties();
        instanceProperties.setMachineType(nameFromSelfLink(MACHINE_TYPE));
        instanceProperties.setLabels(googleLabels);
        AttachedDisk boot = new AttachedDisk();
        boot.setBoot(true);
        boot.setAutoDelete(BOOT_DISK_AUTODELETE);
        boot.setInitializeParams(new AttachedDiskInitializeParams()
                .setDiskSizeGb(Long.parseLong(BOOT_DISK_SIZE_GB_STR))
                .setDiskType(nameFromSelfLink(BOOT_DISK_TYPE))
                .setSourceImage(BOOT_DISK_IMAGE_NAME)
        );
        instanceProperties.setDisks(of(boot));
        instanceProperties.setTags(new Tags().setItems(of(NETWORK_TAGS)));
        instanceProperties.setMetadata(new Metadata().setItems(of(new Metadata.Items()
                .setKey(METADATA_LINUX_STARTUP_SCRIPT_KEY).setValue(DEB_JAVA_STARTUP_SCRIPT))));
        instanceProperties.setNetworkInterfaces(of(new NetworkInterface()
                .setName(NETWORK_NAME)
                .setAccessConfigs(of(new AccessConfig()
                        .setType(NAT_TYPE)
                        .setName(NAT_NAME)
                ))
        ));
        instanceTemplate.setProperties(instanceProperties);
        return instanceTemplate;
    }

    private static InstanceConfiguration validInstanceConfiguration() {
        return instanceConfiguration(DEB_JAVA_STARTUP_SCRIPT, NUM_EXECUTORS, LABEL, false, NULL_TEMPLATE);
    }

    private static InstanceConfiguration validInstanceConfigurationWithLabels(String labels) {
        return instanceConfiguration(DEB_JAVA_STARTUP_SCRIPT, NUM_EXECUTORS, labels, false, NULL_TEMPLATE);
    }

    private static InstanceConfiguration validInstanceConfigurationWithExecutors(String numExecutors) {
        return instanceConfiguration(DEB_JAVA_STARTUP_SCRIPT, numExecutors, LABEL, false, NULL_TEMPLATE);
    }

    private static InstanceConfiguration validInstanceConfigurationWithTemplate(String template) {
        return instanceConfiguration(DEB_JAVA_STARTUP_SCRIPT, NUM_EXECUTORS, LABEL, false, template);
    }

    private static InstanceConfiguration validInstanceConfigurationWithOneShot() {
        return instanceConfiguration(DEB_JAVA_STARTUP_SCRIPT, NUM_EXECUTORS, LABEL, true, NULL_TEMPLATE);
    }

    /**
     * This configuration creates an instance with no Java installed.
     *
     * @return
     */
    private static InstanceConfiguration invalidInstanceConfiguration() {
        return instanceConfiguration("", NUM_EXECUTORS, LABEL, false, NULL_TEMPLATE);
    }

    private static InstanceConfiguration instanceConfiguration(String startupScript, String numExecutors, String labels, boolean oneShot, String template) {
        InstanceConfiguration ic = new InstanceConfiguration(
                NAME_PREFIX,
                REGION,
                ZONE,
                MACHINE_TYPE,
                numExecutors,
                startupScript,
                PREEMPTIBLE,
                MIN_CPU_PLATFORM,
                labels,
                CONFIG_DESC,
                BOOT_DISK_TYPE,
                BOOT_DISK_AUTODELETE,
                BOOT_DISK_IMAGE_NAME,
                BOOT_DISK_PROJECT_ID,
                BOOT_DISK_SIZE_GB_STR,
                false,
                "",
                "",
                "",
                null,
                new AutofilledNetworkConfiguration(NETWORK_NAME, SUBNETWORK_NAME),
                EXTERNAL_ADDR,
                false,
                NETWORK_TAGS,
                SERVICE_ACCOUNT_EMAIL,
                RETENTION_TIME_MINUTES_STR,
                LAUNCH_TIMEOUT_SECONDS_STR,
                NODE_MODE,
                new AcceleratorConfiguration(ACCELERATOR_NAME, ACCELERATOR_COUNT),
                RUN_AS_USER,
                oneShot,
                template
        );
        ic.appendLabels(INTEGRATION_LABEL);
        return ic;
    }

    private static void deleteIntegrationInstances(boolean waitForCompletion) throws IOException {
        List<Instance> instances = client.getInstancesWithLabel(projectId, INTEGRATION_LABEL);
        for (Instance i : instances) {
            safeDelete(i.getName(), waitForCompletion);
        }
    }

    private static void safeDelete(String instanceId, boolean waitForCompletion) {
        try {
            Operation op = client.terminateInstance(projectId, ZONE, instanceId);
            if (waitForCompletion)
                client.waitForOperationCompletion(projectId, op.getName(), op.getZone(), 3 * 60 * 1000);
        } catch (Exception e) {
            log.warning(String.format("Error deleting instance %s: %s", instanceId, e.getMessage()));
        }
    }

    private static String logs() {
        sh.flush();
        return logOutput.toString();
    }
}
