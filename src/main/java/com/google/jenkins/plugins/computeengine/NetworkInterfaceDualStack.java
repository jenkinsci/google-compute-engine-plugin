package com.google.jenkins.plugins.computeengine;

import com.google.api.services.compute.model.AccessConfig;
import com.google.api.services.compute.model.NetworkInterface;
import hudson.Extension;
import java.util.ArrayList;
import java.util.List;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class NetworkInterfaceDualStack extends NetworkInterfaceIpStackMode {
    public static final String PREMIUM_NETWORK_TIER = "PREMIUM";
    public static final String IPV6_TYPE = "DIRECT_IPV6";
    public static final String IPV6_NAME = "external-ipv6";

    private boolean externalIPV4Address;
    private static final boolean externalIPV6Address = true; // always true in dual-stack type

    @DataBoundConstructor
    public NetworkInterfaceDualStack() {}

    public boolean getExternalIPV4Address() {
        return externalIPV4Address;
    }

    public boolean getExternalIPV46ddress() {
        return externalIPV6Address;
    }

    @DataBoundSetter
    public void setExternalIPV4Address(boolean externalIPV4Address) {
        this.externalIPV4Address = externalIPV4Address;
    }

    @Override
    protected NetworkInterface getNetworkInterface() {
        List<AccessConfig> accessConfigs = new ArrayList<>();
        NetworkInterface networkInterface = new NetworkInterface().setStackType(DUAL_IP_STACK_TYPE);
        if (externalIPV4Address) {
            accessConfigs.add(new AccessConfig().setType(NAT_TYPE).setName(NAT_NAME));
            networkInterface.setAccessConfigs(accessConfigs);
        }
        List<AccessConfig> ipv6AccessConfigs = new ArrayList<>();
        if (externalIPV6Address) { // always true
            ipv6AccessConfigs.add(
                    new AccessConfig().setType(IPV6_TYPE).setName(IPV6_NAME).setNetworkTier(PREMIUM_NETWORK_TIER));
            networkInterface.setIpv6AccessConfigs(ipv6AccessConfigs);
        }

        return networkInterface;
    }

    @Extension
    @Symbol("dualStack")
    public static class DescriptorImpl extends NetworkInterfaceIpStackMode.Descriptor {
        @Override
        public String getDisplayName() {
            return DUAL_IP_STACK_TYPE + " (dual-stack)";
        }
    }
}
