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
import jenkins.model.Jenkins;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
        return this.gpuType + "(" + this.gpuCount + ")";
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<AcceleratorConfiguration> {
        @Override
        public String getDisplayName() {
            return null;
        }

        private static ComputeClient computeClient;

        public static void setComputeClient(ComputeClient client) {
            computeClient = client;
        }

        public ListBoxModel doFillGpuTypeItems(@AncestorInPath Jenkins context,
                                               @QueryParameter("zone") @RelativePath("..") String zone,
                                               @QueryParameter("credentialsId") @RelativePath("../..") final String credentialsId) {
            ListBoxModel items = new ListBoxModel();
            try {
                ComputeClient compute = computeClient(context, credentialsId);
                List<AcceleratorType> acceleratorTypes = compute.getAcceleratorTypes(zone);

                for (AcceleratorType a : acceleratorTypes) {
                    items.add(a.getName(), a.getSelfLink());
                }
                return items;
            } catch (IOException ioe) {
                items.clear();
                items.add("Error retrieving GPU types");
                return items;
            } catch (IllegalArgumentException iae) {
                //TODO: log
                return null;
            }

        }

        private static ComputeClient computeClient(Jenkins context, String credentialsId) throws IOException {
            if (computeClient != null) {
                return computeClient;
            }
            ClientFactory clientFactory = new ClientFactory(context, new ArrayList<DomainRequirement>(), credentialsId);
            return clientFactory.compute();
        }
    }
}
