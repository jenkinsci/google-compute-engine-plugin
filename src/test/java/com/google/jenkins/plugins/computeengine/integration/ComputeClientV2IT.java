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
import static org.junit.Assert.assertEquals;

import com.google.api.services.compute.model.Instance;
import com.google.jenkins.plugins.computeengine.ComputeEngineCloud;
import com.google.jenkins.plugins.computeengine.client.ComputeClientV2;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
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

    private static final Map<String, String> GOOGLE_LABELS = getLabel(ComputeClientV2IT.class);
    private static final Map<String, String> GOOGLE_LABELS_NON_JENKINS =
            Map.of("non-jenkins-label", "non-jenkins-value");
    private static final Map<String, String> GOOGLE_LABELS_NEW_LABELS = Map.of("new-key", "new-value");

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
        String instance1 = createOneInstance(clientV2, GOOGLE_LABELS);
        String instance2 = createOneInstance(clientV2, GOOGLE_LABELS_NON_JENKINS);
        await().atMost(2, TimeUnit.MINUTES)
                .until(
                        () -> List.of(getInstance(clientV2, instance1), getInstance(clientV2, instance2)),
                        Every.everyItem(instanceIsRunning()));
        String jenkinsKey = GOOGLE_LABELS.keySet().iterator().next();
        String nonJenkinsKey = GOOGLE_LABELS_NON_JENKINS.keySet().iterator().next();
        var matchingInstances = clientV2.retrieveInstanceByLabelKeyAndStatus(jenkinsKey, "RUNNING");
        assertEquals(1, matchingInstances.size());
        assertEquals(instance1, matchingInstances.get(0).getName());
        // stop the instance and see 0 matching instances for jenkins label in RUNNING state
        clientV2.getCompute().instances().stop(PROJECT_ID, ZONE, instance1).execute();
        await().timeout(5, TimeUnit.SECONDS)
                .until(() -> clientV2.retrieveInstanceByLabelKeyAndStatus(jenkinsKey, "RUNNING")
                        .isEmpty());
        await().timeout(30, TimeUnit.SECONDS)
                .until(() -> clientV2.retrieveInstanceByLabelKeyAndStatus(jenkinsKey, "STOPPING").stream()
                        .map(Instance::getName)
                        .collect(Collectors.toList())
                        .contains(instance1));
        // non-jenkins instance can also be filtered by label and status
        await().timeout(5, TimeUnit.SECONDS)
                .until(() -> clientV2.retrieveInstanceByLabelKeyAndStatus(nonJenkinsKey, "RUNNING").stream()
                        .map(Instance::getName)
                        .collect(Collectors.toList())
                        .contains(instance2));
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
