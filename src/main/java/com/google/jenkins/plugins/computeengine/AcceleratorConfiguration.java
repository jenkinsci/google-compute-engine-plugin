/*
 * Copyright 2020 Google LLC
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

package com.google.jenkins.plugins.computeengine;

import com.google.api.services.compute.model.AcceleratorType;
import com.google.cloud.graphite.platforms.plugin.client.ClientFactory;
import com.google.cloud.graphite.platforms.plugin.client.ComputeClient;
import com.google.jenkins.plugins.computeengine.client.ClientUtil;
import hudson.Extension;
import hudson.RelativePath;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.util.ListBoxModel;
import java.io.IOException;
import java.util.List;
import jenkins.model.Jenkins;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

@Getter
@EqualsAndHashCode
public class AcceleratorConfiguration implements Describable<AcceleratorConfiguration> {
  private final String gpuType;
  private final String gpuCount;

  @DataBoundConstructor
  public AcceleratorConfiguration(String gpuType, String gpuCount) {
    this.gpuType = gpuType;
    this.gpuCount = gpuCount;
  }

  public Integer gpuCount() {
    return Integer.parseInt(gpuCount);
  }

  @SuppressWarnings("unchecked")
  public Descriptor<AcceleratorConfiguration> getDescriptor() {
    return Jenkins.get().getDescriptor(getClass());
  }

  @Override
  public String toString() {
    return String.format("%s (%s)", this.gpuType, this.gpuCount);
  }

  @Extension
  public static final class DescriptorImpl extends Descriptor<AcceleratorConfiguration> {
    private static ComputeClient computeClient;

    public static void setComputeClient(ComputeClient client) {
      computeClient = client;
    }

    private static ComputeClient computeClient(Jenkins context, String credentialsId)
        throws IOException {
      if (computeClient != null) {
        return computeClient;
      }
      ClientFactory clientFactory = ClientUtil.getClientFactory(context, credentialsId);
      return clientFactory.computeClient();
    }

    public ListBoxModel doFillGpuTypeItems(
        @AncestorInPath Jenkins context,
        @QueryParameter("projectId") @RelativePath("../..") final String projectId,
        @QueryParameter("zone") @RelativePath("..") String zone,
        @QueryParameter("credentialsId") @RelativePath("../..") final String credentialsId) {
      ListBoxModel items = new ListBoxModel();
      try {
        ComputeClient compute = computeClient(context, credentialsId);
        List<AcceleratorType> acceleratorTypes = compute.listAcceleratorTypes(projectId, zone);

        for (AcceleratorType a : acceleratorTypes) {
          items.add(a.getName(), a.getSelfLink());
        }
        return items;
      } catch (IOException ioe) {
        items.clear();
        items.add("Error retrieving GPU types");
        return items;
      } catch (IllegalArgumentException iae) {
        // TODO: log
        return null;
      }
    }
  }
}
