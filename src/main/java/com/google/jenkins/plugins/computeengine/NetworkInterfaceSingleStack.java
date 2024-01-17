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
