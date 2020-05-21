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

import com.google.cloud.graphite.platforms.plugin.client.ClientFactory;
import com.google.cloud.graphite.platforms.plugin.client.ComputeClient;
import com.google.jenkins.plugins.computeengine.client.ClientUtil;
import hudson.model.Describable;
import hudson.model.Descriptor;
import java.io.IOException;
import jenkins.model.Jenkins;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public abstract class NetworkConfiguration implements Describable<NetworkConfiguration> {
  private final String network;
  private final String subnetwork;

  public NetworkConfiguration(String network, String subnetwork) {
    this.network = network;
    this.subnetwork = subnetwork;
  }

  @SuppressWarnings("unchecked")
  public Descriptor<NetworkConfiguration> getDescriptor() {
    return Jenkins.get().getDescriptor(getClass());
  }

  @Override
  public String toString() {
    return String.format("Network: %s%nSubnetwork: %s", getNetwork(), getSubnetwork());
  }

  public abstract static class NetworkConfigurationDescriptor
      extends Descriptor<NetworkConfiguration> {
    private static ComputeClient computeClient;

    public static void setComputeClient(ComputeClient client) {
      computeClient = client;
    }

    public static ComputeClient computeClient(Jenkins context, String credentialsId)
        throws IOException {
      if (computeClient != null) {
        return computeClient;
      }
      ClientFactory clientFactory = ClientUtil.getClientFactory(context, credentialsId);
      return clientFactory.computeClient();
    }

    public abstract String getDisplayName();
  }
}
