package com.google.jenkins.plugins.computeengine;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.google.api.services.compute.model.Instance;
import com.google.jenkins.plugins.computeengine.client.ClientFactory;
import com.google.jenkins.plugins.computeengine.client.ComputeClient;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotPrivateKeyCredentials;
import com.google.jenkins.plugins.credentials.oauth.ServiceAccountConfig;
import hudson.model.Node;
import hudson.model.labels.LabelAtom;
import hudson.slaves.NodeProvisioner;
import jenkins.model.Jenkins;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.logging.StreamHandler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ComputeEngineCloudIT {
    private static Logger log = Logger.getLogger(ComputeEngineCloudIT.class.getName());

    private static final String DEB_JAVA_STARTUP_SCRIPT = "#!/bin/bash\n" +
            "/etc/init.d/ssh stop\n" +
            "echo \"deb http://http.debian.net/debian jessie-backports main\" | \\\n" +
            "      sudo tee --append /etc/apt/sources.list.d/jessie-backports.list > /dev/null\n" +
            "apt-get -y update\n" +
            "apt-get -y install -t jessie-backports openjdk-8-jdk\n" +
            "update-java-alternatives -s java-1.8.0-openjdk-amd64\n" +
            "/etc/init.d/ssh start";

    private static final String CLOUD_NAME = "integration";
    private static final String NAME_PREFIX = "integration";
    private static final String REGION = format("projects/%s/regions/us-west1");
    private static final String ZONE = "us-west1-a";
    private static final String ZONE_BASE = format("projects/%s/zones/" + ZONE);
    private static final String LABEL = "integration";
    private static final String MACHINE_TYPE = ZONE_BASE + "/machineTypes/n1-standard-1";
    private static final String NUM_EXECUTORS = "1";
    private static final boolean PREEMPTIBLE = false;
    private static final String CONFIG_DESC = "integration";
    private static final String BOOT_DISK_TYPE = ZONE_BASE + "/diskTypes/pd-ssd";
    private static final boolean BOOT_DISK_AUTODELETE = true;
    private static final String BOOT_DISK_PROJECT_ID = "debian-cloud";
    private static final String BOOT_DISK_IMAGE_NAME = "projects/debian-cloud/global/images/family/debian-8";
    private static final String BOOT_DISK_SIZE_GB_STR = "10";
    private static final Node.Mode NODE_MODE = Node.Mode.EXCLUSIVE;
    private static final String ACCELERATOR_NAME = "";
    private static final String ACCELERATOR_COUNT = "";
    private static final String RUN_AS_USER = "jenkins";

    private static Map<String, String> INTEGRATION_LABEL;
    static {
        INTEGRATION_LABEL = new HashMap<String, String>();
        INTEGRATION_LABEL.put("integration", "delete");
    }
    private final String NETWORK_NAME = format("projects/%s/global/networks/default");
    private final String SUBNETWORK_NAME = "default";
    private final boolean EXTERNAL_ADDR = true;
    private final String NETWORK_TAGS = "ssh";
    private final String SERVICE_ACCOUNT_EMAIL = "";
    private final String RETENTION_TIME_MINUTES_STR = "";
    private final String LAUNCH_TIMEOUT_SECONDS_STR = "";

    private Logger cloudLogger;
    private StreamHandler sh;
    private ByteArrayOutputStream logOutput;

    private ComputeClient client;
    private String projectId;

    private static String format(String s) {
        String projectId = System.getenv("GOOGLE_PROJECT_ID");
        if (projectId == null) {
            throw new RuntimeException("GOOGLE_PROJECT_ID env var must be set");
        }
        return String.format(s, projectId);
    }

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Before
    public void init() throws Exception {
        log.info("init");
        projectId = System.getenv("GOOGLE_PROJECT_ID");
        assertNotNull("GOOGLE_PROJECT_ID env var must be set", projectId);

        String serviceAccountKeyJson = System.getenv("GOOGLE_CREDENTIALS");
        assertNotNull("GOOGLE_CREDENTIALS env var must be set", serviceAccountKeyJson);

        ServiceAccountConfig sac = new StringJsonServiceAccountConfig(serviceAccountKeyJson);
        Credentials c = (Credentials) new GoogleRobotPrivateKeyCredentials(projectId, sac, null);

        CredentialsStore store = new SystemCredentialsProvider.ProviderImpl().getStore(r.jenkins);
        store.addCredentials(Domain.global(), c);

        ClientFactory clientFactory = new ClientFactory(Jenkins.getInstance(), new ArrayList<DomainRequirement>(),
                    projectId);
        this.client = clientFactory.compute();

        List<InstanceConfiguration> configs = new ArrayList<>();
        configs.add(validInstanceConfiguration1());

        ComputeEngineCloud gcp = new ComputeEngineCloud(CLOUD_NAME, projectId, projectId, "2", configs);

        assertEquals(0, r.jenkins.clouds.size());
        r.jenkins.clouds.add(gcp);
        assertEquals(1, r.jenkins.clouds.size());
        assertEquals(1, ((ComputeEngineCloud) r.jenkins.clouds.get(0)).configurations.size());

        // We need to capture log output to make sense of most failures
        cloudLogger = LogManager.getLogManager().getLogger("com.google.jenkins.plugins.computeengine.ComputeEngineCloud");
        logOutput = new ByteArrayOutputStream();
        sh = new StreamHandler(logOutput, new SimpleFormatter());
        cloudLogger.addHandler(sh);
    }

    @After
    public void teardown() throws Exception {
        log.info("teardown");
        sh.close();
        log.info(logOutput.toString());
    }

    @Test
    public void testCredentialsCreated() {
        List<Credentials> creds = new SystemCredentialsProvider.ProviderImpl().getStore(r.jenkins).getCredentials(Domain.global());
        assertEquals(1, creds.size());
    }

    @Test
    public void testConfigCreated() throws Exception {
        InstanceConfiguration want = validInstanceConfiguration1();
        InstanceConfiguration got = ((ComputeEngineCloud) r.jenkins.clouds.get(0)).getInstanceConfig(CONFIG_DESC);
        r.assertEqualBeans(want, got, "namePrefix,region,zone,machineType,preemptible,startupScript,bootDiskType,bootDiskSourceImageName,bootDiskSourceImageProject,bootDiskSizeGb,acceleratorConfiguration,network,subnetwork,externalAddress,networkTags,serviceAccountEmail");
    }

    @Test(timeout=300000)
    public void testWorkerCreated() throws Exception {
        ComputeEngineCloud cloud = (ComputeEngineCloud) r.jenkins.clouds.get(0);
        Collection<NodeProvisioner.PlannedNode> planned = cloud.provision(new LabelAtom(LABEL), 1);
        sh.flush();
        assertEquals(logOutput.toString(), 1, planned.size());

        NodeProvisioner.PlannedNode node = planned.iterator().next();
        Node n = node.future.get();
    }

    private InstanceConfiguration validInstanceConfiguration1() {
        InstanceConfiguration ic = new InstanceConfiguration(
                NAME_PREFIX,
                REGION,
                ZONE,
                MACHINE_TYPE,
                NUM_EXECUTORS,
                DEB_JAVA_STARTUP_SCRIPT,
                PREEMPTIBLE,
                LABEL,
                CONFIG_DESC,
                BOOT_DISK_TYPE,
                BOOT_DISK_AUTODELETE,
                BOOT_DISK_IMAGE_NAME,
                BOOT_DISK_PROJECT_ID,
                BOOT_DISK_SIZE_GB_STR,
                NETWORK_NAME,
                SUBNETWORK_NAME,
                EXTERNAL_ADDR,
                NETWORK_TAGS,
                SERVICE_ACCOUNT_EMAIL,
                RETENTION_TIME_MINUTES_STR,
                LAUNCH_TIMEOUT_SECONDS_STR,
                NODE_MODE,
                new AcceleratorConfiguration(ACCELERATOR_NAME, ACCELERATOR_COUNT),
                RUN_AS_USER);
       ic.appendLabels(INTEGRATION_LABEL);
    }

    private void deleteIntegrationInstances() throws IOException {
        List<Instance> instances = client.getInstancesWithLabel(projectId, INTEGRATION_LABEL);
        for(Instance i : instances) {

        }
    }


}
