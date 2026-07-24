package com.google.jenkins.plugins.computeengine.integration;

import static com.google.jenkins.plugins.computeengine.integration.ITUtil.LABEL;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.NULL_TEMPLATE;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.NUM_EXECUTORS;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.PROJECT_ID;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.ZONE;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.getLabel;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initCloud;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.initCredentials;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.instanceConfigurationBuilder;
import static com.google.jenkins.plugins.computeengine.integration.ITUtil.teardownResources;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

import com.google.api.services.compute.model.Instance;
import com.google.jenkins.plugins.computeengine.ComputeEngineCloud;
import com.google.jenkins.plugins.computeengine.client.ComputeClientV2;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.hamcrest.core.Every;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class ComputeClientV2IT {

    private static final Logger LOGGER = Logger.getLogger(ComputeClientV2IT.class.getName());

    @Rule
    public JenkinsRule j = new JenkinsRule();

    private static final String RUNNING = "RUNNING";
    private static final String STOPPING = "STOPPING";
    private static final String CLOUD_A = "cloud-a";
    private static final String CLOUD_B = "cloud-b";

    private static final Map<String, String> GOOGLE_LABELS = getLabel(ComputeClientV2IT.class);
    private static final Map<String, String> GOOGLE_LABELS_NON_JENKINS =
            Map.of("non-jenkins-label", "non-jenkins-value");
    private static final Map<String, String> GOOGLE_LABELS_NEW_LABELS = Map.of("new-key", "new-value");
    private static final String LABEL_FILTER_PRESENCE_KEY = "label-filter-test-key";
    private static final Map<String, String> GOOGLE_LABELS_CLOUD_A =
            Map.of(LABEL_FILTER_PRESENCE_KEY, "delete", ComputeEngineCloud.JENKINS_CLOUD_NAME_LABEL_KEY, CLOUD_A);
    private static final Map<String, String> GOOGLE_LABELS_CLOUD_B =
            Map.of(LABEL_FILTER_PRESENCE_KEY, "delete", ComputeEngineCloud.JENKINS_CLOUD_NAME_LABEL_KEY, CLOUD_B);

    @Before
    public void setUp() throws Exception {
        initCredentials(j);
        initCloud(j);
    }

    @After
    public void tearDown() throws IOException {
        teardownResources(getCloud(j).getClient(), GOOGLE_LABELS, LOGGER);
        teardownResources(getCloud(j).getClient(), GOOGLE_LABELS_NON_JENKINS, LOGGER);
        // merge googleLabels and newLabels for teardown purposes only
        Map<String, String> allLabels = new HashMap<>();
        allLabels.putAll(GOOGLE_LABELS);
        allLabels.putAll(GOOGLE_LABELS_NEW_LABELS);
        teardownResources(getCloud(j).getClient(), allLabels, LOGGER);
        teardownResources(getCloud(j).getClient(), GOOGLE_LABELS_CLOUD_A, LOGGER);
        teardownResources(getCloud(j).getClient(), GOOGLE_LABELS_CLOUD_B, LOGGER);
    }

    public ComputeEngineCloud getCloud(JenkinsRule j) {
        return (ComputeEngineCloud) j.jenkins.clouds.getByName("gce-integration");
    }

    // create dummy instances
    public String createOneInstance(ComputeClientV2 clientV2, Map<String, String> googleLabels) throws IOException {
        var instanceConfig = instanceConfigurationBuilder()
                .numExecutorsStr(NUM_EXECUTORS)
                .labels(LABEL)
                .oneShot(true)
                .createSnapshot(false)
                .template(NULL_TEMPLATE)
                .zone(ZONE)
                .googleLabels(googleLabels)
                .build();
        Instance instance = instanceConfig.instance();
        clientV2.getCompute().instances().insert(PROJECT_ID, ZONE, instance).execute();
        return instance.getName();
    }

    @Test
    public void testRetrieveInstancesByLabelAndStatus() throws Exception {
        ComputeClientV2 clientV2 = getCloud(j).getClientV2();
        String jenkinsVm = createOneInstance(clientV2, GOOGLE_LABELS);
        String nonJenkinsVm = createOneInstance(clientV2, GOOGLE_LABELS_NON_JENKINS);
        String cloudAVm = createOneInstance(clientV2, GOOGLE_LABELS_CLOUD_A);
        String cloudBVm = createOneInstance(clientV2, GOOGLE_LABELS_CLOUD_B);
        awaitRunning(clientV2, jenkinsVm, nonJenkinsVm, cloudAVm, cloudBVm);

        String jenkinsKey = GOOGLE_LABELS.keySet().iterator().next();
        String nonJenkinsKey = GOOGLE_LABELS_NON_JENKINS.keySet().iterator().next();

        // presenceKey filter on the jenkins key returns only the jenkins VM.
        assertRetrieved(clientV2, jenkinsKey, Map.of(), RUNNING, jenkinsVm);

        // Once stopped, the jenkins VM leaves RUNNING and shows up under STOPPING.
        clientV2.getCompute().instances().stop(PROJECT_ID, ZONE, jenkinsVm).execute();
        awaitRetrieved(clientV2, jenkinsKey, RUNNING /* none */);
        awaitRetrieved(clientV2, jenkinsKey, STOPPING, jenkinsVm);

        // presenceKey filter works for any key (nothing hardcoded)
        awaitRetrieved(clientV2, nonJenkinsKey, RUNNING, nonJenkinsVm);

        // cloud-a returns only cloudAVm, cloud-b returns only cloudBVm.
        assertRetrieved(clientV2, LABEL_FILTER_PRESENCE_KEY, cloudNameFilter(CLOUD_A), RUNNING, cloudAVm);
        assertRetrieved(clientV2, LABEL_FILTER_PRESENCE_KEY, cloudNameFilter(CLOUD_B), RUNNING, cloudBVm);
    }

    private static Map<String, String> cloudNameFilter(String cloudName) {
        return Map.of(ComputeEngineCloud.JENKINS_CLOUD_NAME_LABEL_KEY, cloudName);
    }

    private void awaitRunning(ComputeClientV2 clientV2, String... instanceNames) {
        await().atMost(2, TimeUnit.MINUTES)
                .until(
                        () -> Arrays.stream(instanceNames)
                                .map(name -> getInstanceUnchecked(clientV2, name))
                                .collect(Collectors.toList()),
                        Every.everyItem(instanceIsRunning()));
    }

    /** Asserts filter retrieves exactly {@code expectedNames} VMs from GCP. */
    private static void assertRetrieved(
            ComputeClientV2 clientV2,
            String presenceKey,
            Map<String, String> labelFilters,
            String status,
            String... expectedNames)
            throws IOException {
        var actual = clientV2.retrieveInstanceByLabelKeyAndStatus(presenceKey, labelFilters, status).stream()
                .map(Instance::getName)
                .collect(Collectors.toList());
        assertThat(actual, containsInAnyOrder(expectedNames));
    }

    /** Waits until finding {@code expectedNames} VMs within 30s timeout. */
    private static void awaitRetrieved(
            ComputeClientV2 clientV2, String presenceKey, String status, String... expectedNames) {
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            var actual = clientV2.retrieveInstanceByLabelKeyAndStatus(presenceKey, Map.of(), status).stream()
                    .map(Instance::getName)
                    .collect(Collectors.toList());
            assertThat(actual, containsInAnyOrder(expectedNames));
        });
    }

    @Test
    public void testUpdateInstanceLabels() throws Exception {
        ComputeClientV2 clientV2 = getCloud(j).getClientV2();
        String instance1 = createOneInstance(clientV2, GOOGLE_LABELS);
        await().atMost(2, TimeUnit.MINUTES).until(() -> Objects.nonNull(getInstance(clientV2, instance1)));
        clientV2.updateInstanceLabels(getInstance(clientV2, instance1), GOOGLE_LABELS_NEW_LABELS);
        assertLabel(clientV2, instance1, "new-key", "new-value");
        // change label value see if it gets updated
        clientV2.updateInstanceLabels(getInstance(clientV2, instance1), Map.of("new-key", "new-value-2"));
        assertLabel(clientV2, instance1, "new-key", "new-value-2");
        // change label value back so that instance can get teardown automatically
        clientV2.updateInstanceLabels(getInstance(clientV2, instance1), GOOGLE_LABELS_NEW_LABELS);
    }

    private static Instance getInstance(ComputeClientV2 clientV2, String instanceName) throws IOException {
        return clientV2.getCompute()
                .instances()
                .get(PROJECT_ID, ZONE, instanceName)
                .execute();
    }

    /** {@link #getInstance} wrapper for use inside lambdas that cannot throw a checked {@link IOException}. */
    private static Instance getInstanceUnchecked(ComputeClientV2 clientV2, String instanceName) {
        try {
            return getInstance(clientV2, instanceName);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void assertLabel(ComputeClientV2 clientV2, String instanceName, String key, String value) {
        await().atMost(2, TimeUnit.MINUTES).until(() -> {
            var instance = clientV2.getCompute()
                    .instances()
                    .get(PROJECT_ID, ZONE, instanceName)
                    .execute();
            return instance.getLabels().containsKey(key)
                    && instance.getLabels().get(key).equals(value);
        });
    }

    private Matcher<Instance> instanceIsRunning() {
        return new RunningStatusMatcher();
    }

    private static class RunningStatusMatcher extends TypeSafeMatcher<Instance> {
        @Override
        protected boolean matchesSafely(Instance instance) {
            return instance != null && instance.getStatus().equals("RUNNING");
        }

        @Override
        public void describeTo(Description description) {
            description.appendText("instance status is RUNNING");
        }
    }
}
