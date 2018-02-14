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
import com.google.jenkins.plugins.computeengine.client.ClientFactory;
import com.google.jenkins.plugins.computeengine.client.ComputeClient;
import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Objects;

public abstract class NetworkConfiguration implements Describable<NetworkConfiguration> {
    public final String network;
    public final String subnetwork;

    public NetworkConfiguration(String network, String subnetwork) {
        this.network = network;
        this.subnetwork = subnetwork;
    }

    public String getNetwork() {
        return network;
    }

    public String getSubnetwork() {
        return subnetwork;
    }

    public Descriptor<NetworkConfiguration> getDescriptor() {
        return Jenkins.getInstance().getDescriptor(getClass());
    }

    @Override
    public int hashCode() {
        return Objects.hash(network, subnetwork);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (!NetworkConfiguration.class.isAssignableFrom(obj.getClass())) {
            return false;
        }
        final NetworkConfiguration other = (NetworkConfiguration) obj;
        return this.network.equals(other.network) && this.subnetwork.equals(other.subnetwork);
    }

    @Override
    public String toString() {
        return String.format("Network: %s\nSubnetwork: %s", getNetwork(), getSubnetwork());
    }

    public static class NetworkConfigurationDescriptor extends Descriptor<NetworkConfiguration> {
        private static ComputeClient computeClient;

        public String getDisplayName() {
           return "Manually enter...";
       }

        public static void setComputeClient(ComputeClient client) {
            computeClient = client;
        }

        public static ComputeClient computeClient(Jenkins context, String credentialsId) throws IOException {
            if (computeClient != null) {
                return computeClient;
            }
            ClientFactory clientFactory = new ClientFactory(context, new ArrayList<DomainRequirement>(), credentialsId);
            return clientFactory.compute();
        }
    }
}
