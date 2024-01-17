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
