package com.google.jenkins.plugins.computeengine;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.google.api.services.compute.Compute;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotPrivateKeyCredentials;
import com.google.jenkins.plugins.credentials.oauth.ServiceAccountConfig;
import hudson.logging.LogRecorderManager;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.labels.LabelAtom;
import hudson.slaves.NodeProvisioner;
import jenkins.model.Jenkins;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import sun.security.jca.GetInstance;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.logging.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ComputeEngineCloudIT {
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
    private static final String REGION = "us-west1";
    private static final String ZONE = "us-west1-a";
    private static final String LABEL = "integration";
    private static final String MACHINE_TYPE = "n1-standard-1";
    private static final String NUM_EXECUTORS = "1";
    private static final boolean PREEMPTIBLE = false;
    private static final String CONFIG_DESC = "integration";
    private static final String BOOT_DISK_TYPE = "pd-standard";
    private static final boolean BOOT_DISK_AUTODELETE = true;
    private static final String BOOT_DISK_IMAGE_NAME = "debian-8";
    private static final String BOOT_DISK_PROJECT_ID = "debian-cloud";
    private static final String BOOT_DISK_SIZE_GB_STR = "10";
    private static final Node.Mode NODE_MODE = Node.Mode.EXCLUSIVE;
    private static final String ACCELERATOR_NAME = "";
    private static final String ACCELERATOR_COUNT = "";
    private static final String RUN_AS_USER = "jenkins";
    private final String NETWORK_NAME = "default";
    private final String SUBNETWORK_NAME = "default";
    private final boolean EXTERNAL_ADDR = true;
    private final String NETWORK_TAGS = "ssh";
    private final String SERVICE_ACCOUNT_EMAIL = "";
    private final String RETENTION_TIME_MINUTES_STR = "";
    private final String LAUNCH_TIMEOUT_SECONDS_STR = "";

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Before
    public void init() throws Exception {
        String projectId = System.getenv("GOOGLE_PROJECT_ID");
        assertNotNull("GOOGLE_PROJECT_ID env var must be set", projectId);

        String serviceAccountKeyJson = System.getenv("GOOGLE_CREDENTIALS");
        assertNotNull("GOOGLE_CREDENTIALS env var must be set", serviceAccountKeyJson);

        ServiceAccountConfig sac = new StringJsonServiceAccountConfig(serviceAccountKeyJson);
        Credentials c = (Credentials) new GoogleRobotPrivateKeyCredentials(projectId, sac, null);

        CredentialsStore store = new SystemCredentialsProvider.ProviderImpl().getStore(r.jenkins);
        store.addCredentials(Domain.global(), c);

        List<InstanceConfiguration> configs = new ArrayList<>();
        configs.add(validInstanceConfiguration1());

        ComputeEngineCloud gcp = new ComputeEngineCloud(CLOUD_NAME, projectId, projectId, "1", configs);

        assertEquals(0, r.jenkins.clouds.size());
        r.jenkins.clouds.add(gcp);
        assertEquals(1, r.jenkins.clouds.size());
        assertEquals(1, ((ComputeEngineCloud)r.jenkins.clouds.get(0)).configurations.size());
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

    @Test
    public void testWorkerCreated() throws Exception {
        // We need to capture log output to make sense of most failures
        Logger cloudLogger = LogManager.getLogManager().getLogger("com.google.jenkins.plugins.computeengine.ComputeEngineCloud");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        StreamHandler sh = new StreamHandler(baos, new SimpleFormatter());
        cloudLogger.addHandler(sh);

        //JenkinsRule.WebClient wc = r.createWebClient();
        ComputeEngineCloud cloud = (ComputeEngineCloud) r.jenkins.clouds.get(0);
        Collection<NodeProvisioner.PlannedNode> planned = cloud.provision(new LabelAtom(LABEL), 1);
        sh.flush();
        assertEquals(baos.toString(), 1, planned.size());

        NodeProvisioner.PlannedNode node = planned.iterator().next();
        node.future.wait();
    }

    private InstanceConfiguration validInstanceConfiguration1() {
        return new InstanceConfiguration(
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
    }


}
