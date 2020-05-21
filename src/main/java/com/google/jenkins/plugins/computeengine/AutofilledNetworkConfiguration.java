/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.jenkins.plugins.computeengine;

import static com.google.jenkins.plugins.computeengine.ComputeEngineCloud.checkPermissions;

import com.google.api.services.compute.model.Network;
import com.google.api.services.compute.model.Subnetwork;
import com.google.cloud.graphite.platforms.plugin.client.ComputeClient;
import com.google.common.base.Strings;
import hudson.Extension;
import hudson.RelativePath;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

public class AutofilledNetworkConfiguration extends NetworkConfiguration {
  private static final Logger LOGGER =
      Logger.getLogger(AutofilledNetworkConfiguration.class.getName());

  @DataBoundConstructor
  public AutofilledNetworkConfiguration(String network, String subnetwork) {
    super(network, subnetwork);
  }

  public AutofilledNetworkConfiguration() {
    super("", "");
  }

  @Extension
  public static final class DescriptorImpl extends NetworkConfigurationDescriptor {
    public String getDisplayName() {
      return "Available networks";
    }

    public ListBoxModel doFillNetworkItems(
        @AncestorInPath Jenkins context,
        @QueryParameter("projectId") @RelativePath("../..") final String projectId,
        @QueryParameter("credentialsId") @RelativePath("../..") final String credentialsId) {
      checkPermissions();
      ListBoxModel items = new ListBoxModel();
      items.add("");

      try {
        ComputeClient compute = computeClient(context, credentialsId);
        List<Network> networks = compute.listNetworks(projectId);

        for (Network n : networks) {
          items.add(n.getName(), n.getSelfLink());
        }
        return items;
      } catch (IOException | IllegalArgumentException e) {
        String message = "Error retrieving networks";
        LOGGER.log(Level.SEVERE, message, e);
        items.clear();
        items.add(new ListBoxModel.Option(message, "", true));
        return items;
      }
    }

    public FormValidation doCheckNetwork(@QueryParameter String value) {
      checkPermissions();
      if (value.equals("")) {
        return FormValidation.error("Please select a network...");
      }
      return FormValidation.ok();
    }

    public ListBoxModel doFillSubnetworkItems(
        @AncestorInPath Jenkins context,
        @QueryParameter("network") final String network,
        @QueryParameter("region") @RelativePath("..") final String region,
        @QueryParameter("projectId") @RelativePath("../..") final String projectId,
        @QueryParameter("credentialsId") @RelativePath("../..") final String credentialsId) {
      ListBoxModel items = new ListBoxModel();
      checkPermissions();

      if (Strings.isNullOrEmpty(region)) {
        return items;
      }

      try {
        ComputeClient compute = computeClient(context, credentialsId);
        List<Subnetwork> subnetworks = compute.listSubnetworks(projectId, network, region);

        if (subnetworks.size() <= 1) {
          items.add(new ListBoxModel.Option("", "", false));
        }
        if (subnetworks.size() == 0) {
          items.add(new ListBoxModel.Option("default", "default", true));
          return items;
        }

        for (Subnetwork s : subnetworks) {
          items.add(s.getName(), s.getSelfLink());
        }
        return items;
      } catch (IOException | IllegalArgumentException e) {
        String message = "Error retrieving subnetworks";
        LOGGER.log(Level.SEVERE, message, e);
        items.clear();
        items.add(new ListBoxModel.Option(message, "", true));
        return items;
      }
    }

    public FormValidation doCheckSubnetwork(@QueryParameter String value) {
      checkPermissions();
      if (value.isEmpty()) {
        return FormValidation.error("Please select a subnetwork...");
      }

      return FormValidation.ok();
    }
  }
}
