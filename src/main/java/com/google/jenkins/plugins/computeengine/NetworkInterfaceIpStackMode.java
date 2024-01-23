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

import com.google.api.services.compute.model.NetworkInterface;
import hudson.model.AbstractDescribableImpl;

public abstract class NetworkInterfaceIpStackMode extends AbstractDescribableImpl<NetworkInterfaceIpStackMode> {
    public static final String NAT_TYPE = "ONE_TO_ONE_NAT";
    public static final String NAT_NAME = "External NAT";

    public static final String SINGLE_IP_STACK_TYPE = "IPV4_ONLY";
    public static final String DUAL_IP_STACK_TYPE = "IPV4_IPV6";

    protected NetworkInterface getNetworkInterface() {
        return null;
    }

    public static class Descriptor extends hudson.model.Descriptor<NetworkInterfaceIpStackMode> {}
}
