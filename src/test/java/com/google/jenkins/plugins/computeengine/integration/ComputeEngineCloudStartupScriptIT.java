/*
 * Copyright 2026 CloudBees, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.jenkins.plugins.computeengine.integration;

import static com.google.jenkins.plugins.computeengine.integration.ITUtil.NULL_TEMPLATE;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.NUM_EXECUTORS;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.PROJECT_ID;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.TEST_TIMEOUT_MULTIPLIER;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.ZONE;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.getLabel;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initCredentials;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.instanceConfigurationBuilder;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.teardownResources;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assume.assumeTrue;

import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.google.cloud.graphite.platforms.plugin.client.ComputeClient;
import com.google.cloud.graphite.platforms.plugin.client.model.GuestAttribute;
import com.google.common.collect.ImmutableList;
import com.google.jenkins.plugins.computeengine.ComputeEngineCloud;
import com.google.jenkins.plugins.computeengine.InstanceConfiguration;
import com.google.jenkins.plugins.computeengine.WindowsConfiguration;
import hudson.Util;
import hudson.model.Node;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.jvnet.hudson.test.BuildWatcher;

public class ComputeEngineCloudStartupScriptIT {
    private static final Logger log = Logger.getLogger(ComputeEngineCloudStartupScriptIT.class.getName());

    private static final String GCE_LABEL = "gce";

    @ClassRule
    public static Timeout timeout = new Timeout(20L * TEST_TIMEOUT_MULTIPLIER, TimeUnit.MINUTES);

    @Rule
    public JenkinsConfiguredWithCodeRule j = new JenkinsConfiguredWithCodeRule();

    @ClassRule
    public static BuildWatcher bw = new BuildWatcher();

    private ComputeClient client;
    private ComputeEngineCloud cloud;
    private final Map<String, String> label = getLabel(ComputeEngineCloudStartupScriptIT.class);

    @Before
    public void init() throws Exception {
        log.info("init");
        initCredentials(j);
    }

    @After
    public void teardown() throws IOException {
        log.info("teardown");
        if (client != null) {
            teardownResources(client, label, log);
        }
    }

    @Test
    @ConfiguredWithCode("startup-script-wait-casc.yml")
    public void testBuildWaitsForStartupScriptLinux() throws Exception {
        cloud = (ComputeEngineCloud) j.jenkins.clouds.getByName(ComputeEngineCloud.CLOUD_PREFIX + ITUtil.CLOUD_NAME);
        cloud.getConfigurations().get(0).setGoogleLabels(label);
        client = ITUtil.initClient(j, label, log);

        var pipelineScript = "node('" + GCE_LABEL + "') {\n"
                + "  sh 'cat /tmp/startup-output.txt'\n"
                + "  sh 'grep STARTUP_COMPLETE /tmp/startup-output.txt'\n"
                + "  semaphore 'startupWaitLinux'\n"
                + "}";

        runStartupScriptTest(pipelineScript, "startupWaitLinux");
    }

    @Test
    public void testBuildWaitsForStartupScriptWindows() throws Exception {
        var windowsUsername = System.getenv("GOOGLE_WINDOWS_USERNAME");
        var windowsPassword = System.getenv("GOOGLE_WINDOWS_PASSWORD");
        assumeTrue(
                "Skipping Windows test: GOOGLE_WINDOWS_USERNAME and GOOGLE_WINDOWS_PASSWORD must be set",
                windowsUsername != null && windowsPassword != null);

        cloud = ITUtil.initCloud(j);
        client = ITUtil.initClient(j, label, log);

        var passwordCredId = initWindowsPasswordCredential(windowsUsername, windowsPassword);

        var startupScript = "Set-Content -Path C:\\startup-output.txt -Value 'STARTUP_SCRIPT_BEGIN'\n"
                + "Start-Sleep -Seconds 90\n"
                + "Add-Content -Path C:\\startup-output.txt -Value 'STARTUP_COMPLETE'\n"
                + "Add-Content -Path C:\\startup-output.txt -Value \"Finished at $(Get-Date)\"\n";

        var pipelineScript = "node('" + GCE_LABEL + "') {\n"
                + "  bat 'type C:\\\\startup-output.txt'\n"
                + "  bat 'findstr STARTUP_COMPLETE C:\\\\startup-output.txt'\n"
                + "  semaphore 'startupWaitWindows'\n"
                + "}";

        var bootDiskProject = System.getenv("GOOGLE_BOOT_DISK_PROJECT_ID");
        if (bootDiskProject == null) {
            bootDiskProject = PROJECT_ID;
        }
        var windowsImage =
                String.format("projects/%s/global/images/jenkins-gce-integration-test-windows-jre", bootDiskProject);

        cloud.setConfigurations(ImmutableList.of(instanceConfigurationBuilder()
                .numExecutorsStr(NUM_EXECUTORS)
                .labels(GCE_LABEL)
                .oneShot(true)
                .createSnapshot(false)
                .template(NULL_TEMPLATE)
                .googleLabels(label)
                .startupScript(startupScript)
                .startupScriptExitReporterWindows(InstanceConfiguration.DEFAULT_WINDOWS_EXIT_REPORTER)
                .bootDiskSourceImageName(windowsImage)
                .bootDiskSizeGbStr("50")
                .remoteFs("C:\\Users\\" + windowsUsername)
                .javaExecPath(null) // TODO: fix instanceConfigurationBuilder() to not embed JVM flags in javaExecPath
                .windowsConfiguration(WindowsConfiguration.builder()
                        .passwordCredentialsId(passwordCredId)
                        .privateKeyCredentialsId("")
                        .build())
                .build()));

        runStartupScriptTest(pipelineScript, "startupWaitWindows");
    }

    private String initWindowsPasswordCredential(String username, String password) throws Exception {
        var cred = new UsernamePasswordCredentialsImpl(
                CredentialsScope.GLOBAL, null, "startup script IT - windows password", username, password);
        var store = new SystemCredentialsProvider.ProviderImpl().getStore(j.jenkins);
        store.addCredentials(Domain.global(), cred);
        return cred.getId();
    }

    private void runStartupScriptTest(String pipelineScript, String semaphoreName) throws Exception {
        var p = j.createProject(WorkflowJob.class, "startup-script-wait-test");
        p.setDefinition(new CpsFlowDefinition(pipelineScript, true));

        var build = p.scheduleBuild2(0).waitForStart();
        SemaphoreStep.waitForStart(semaphoreName + "/1", build);
        var buildLog = j.getLog(build);

        var workerNodeName = extractWorkerNodeName(buildLog);
        log.info("Build paused on node: " + workerNodeName);

        j.assertLogContains("STARTUP_COMPLETE", build);

        var guestAttrValue = getStartupScriptGuestAttribute(workerNodeName);
        assertThat("Guest attribute startup-script/status should be set", guestAttrValue.isPresent(), is(true));
        assertThat("Startup script should have exited with 0", guestAttrValue.get(), is("0"));
        log.info("Guest attribute startup-script/status = " + guestAttrValue.get());

        SemaphoreStep.success(semaphoreName + "/1", null);
        j.waitForCompletion(build);
        j.assertBuildStatusSuccess(build);
    }

    private String extractWorkerNodeName(String buildLog) {
        return j.jenkins.getNodes().stream()
                .map(Node::getNodeName)
                .filter(name -> buildLog.contains("Running on " + name))
                .findFirst()
                .orElseThrow(
                        () -> new AssertionError("Build log does not contain 'Running on <agent>'. Log:\n" + buildLog));
    }

    private Optional<String> getStartupScriptGuestAttribute(String instanceName) throws IOException {
        var namespace = InstanceConfiguration.GUEST_ATTRIBUTE_STARTUP_SCRIPT_NAMESPACE + "/";
        var attrs = client.getGuestAttributesSync(PROJECT_ID, ZONE, instanceName, Util.rawEncode(namespace));

        return attrs.stream()
                .filter(a -> a.getNamespace().equals(InstanceConfiguration.GUEST_ATTRIBUTE_STARTUP_SCRIPT_NAMESPACE)
                        && a.getKey().equals(InstanceConfiguration.GUEST_ATTRIBUTE_STARTUP_SCRIPT_STATUS_KEY))
                .map(GuestAttribute::getValue)
                .findFirst();
    }
}
