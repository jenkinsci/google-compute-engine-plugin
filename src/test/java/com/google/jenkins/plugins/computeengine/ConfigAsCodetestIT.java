package com.google.jenkins.plugins.computeengine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import com.google.api.services.compute.model.Instance;
import hudson.model.labels.LabelAtom;
import hudson.slaves.NodeProvisioner;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import java.util.Collection;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class ConfigAsCodetestIT extends ComputeEngineCloudIT {

  @Rule public JenkinsRule r = new JenkinsConfiguredWithCodeRule();

  @Test(timeout = 300000)
  @ConfiguredWithCode("configuration-as-code-it.yml")
  public void testWorkerCreated() throws Exception {
    ComputeEngineCloud cloud = (ComputeEngineCloud) r.jenkins.clouds.getByName("gce-integration");
    // Add a new node
    Collection<NodeProvisioner.PlannedNode> planned = cloud.provision(new LabelAtom(LABEL), 1);

    // There should be a planned node
    assertEquals(logs(), 1, planned.size());

    String name = planned.iterator().next().displayName;

    // Wait for the node creation to finish
    planned.iterator().next().future.get();

    // There should be no warning logs
    assertFalse(logs(), logs().contains("WARNING"));

    Instance instance = cloud.getClient().getInstance(projectId, ZONE, name);

    assertNotNull(logs(), instance);
  }
}
