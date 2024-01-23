/*
 * Copyright 2024 CloudBees, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.jenkins.plugins.computeengine;

import com.google.api.services.compute.model.AccessConfig;
import com.google.api.services.compute.model.NetworkInterface;
import hudson.Extension;
import java.util.ArrayList;
import java.util.List;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class NetworkInterfaceSingleStack extends NetworkInterfaceIpStackMode {

    private boolean externalIPV4Address;

    @DataBoundConstructor
    public NetworkInterfaceSingleStack() {}

    public NetworkInterfaceSingleStack(boolean externalIPV4Address) {
        this.externalIPV4Address = externalIPV4Address;
    }

    public boolean getExternalIPV4Address() {
        return externalIPV4Address;
    }

    @DataBoundSetter
    public void setExternalIPV4Address(boolean externalIPV4Address) {
        this.externalIPV4Address = externalIPV4Address;
    }

    @Override
    protected NetworkInterface getNetworkInterface() {
        List<AccessConfig> accessConfigs = new ArrayList<>();
        NetworkInterface networkInterface = new NetworkInterface().setStackType(SINGLE_IP_STACK_TYPE);
        if (externalIPV4Address) {
            accessConfigs.add(new AccessConfig().setType(NAT_TYPE).setName(NAT_NAME));
            networkInterface.setAccessConfigs(accessConfigs);
        }
        return networkInterface;
    }

    @Extension
    @Symbol("singleStack")
    public static class DescriptorImpl extends NetworkInterfaceIpStackMode.Descriptor {
        @Override
        public String getDisplayName() {
            return SINGLE_IP_STACK_TYPE + " (single-stack)";
        }
    }
}
