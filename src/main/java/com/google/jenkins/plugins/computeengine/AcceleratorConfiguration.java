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

import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.google.api.services.compute.model.AcceleratorType;
import com.google.jenkins.plugins.computeengine.client.ClientFactory;
import com.google.jenkins.plugins.computeengine.client.ComputeClient;
import hudson.Extension;
import hudson.RelativePath;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.util.ListBoxModel;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

public class AcceleratorConfiguration implements Describable<AcceleratorConfiguration> {
  public final String gpuType;
  public final String gpuCount;

  @DataBoundConstructor
  public AcceleratorConfiguration(String gpuType, String gpuCount) {
    this.gpuType = gpuType;
    this.gpuCount = gpuCount;
  }

  public Integer gpuCount() {
    return Integer.parseInt(gpuCount);
  }

  public Descriptor<AcceleratorConfiguration> getDescriptor() {
    return Jenkins.getInstance().getDescriptor(getClass());
  }

  @Override
  public int hashCode() {
    return Objects.hash(gpuType, gpuCount);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    if (!AcceleratorConfiguration.class.isAssignableFrom(obj.getClass())) {
      return false;
    }
    final AcceleratorConfiguration other = (AcceleratorConfiguration) obj;
    return this.gpuType.equals(other.gpuType) && this.gpuCount.equals(other.gpuCount);
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
      ClientFactory clientFactory =
          new ClientFactory(context, new ArrayList<DomainRequirement>(), credentialsId);
      return clientFactory.compute();
    }

    public ListBoxModel doFillGpuTypeItems(
        @AncestorInPath Jenkins context,
        @QueryParameter("projectId") @RelativePath("../..") final String projectId,
        @QueryParameter("zone") @RelativePath("..") String zone,
        @QueryParameter("credentialsId") @RelativePath("../..") final String credentialsId) {
      ListBoxModel items = new ListBoxModel();
      try {
        ComputeClient compute = computeClient(context, credentialsId);
        List<AcceleratorType> acceleratorTypes = compute.getAcceleratorTypes(projectId, zone);

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
