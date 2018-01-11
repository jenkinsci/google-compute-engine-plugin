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

    public Descriptor<AcceleratorConfiguration> getDescriptor() {
        return Jenkins.getInstance().getDescriptor(getClass());
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<AcceleratorConfiguration> {
        @Override
        public String getDisplayName() {
            return null;
        }

        public ListBoxModel doFillGpuTypeItems(@AncestorInPath Jenkins context,
                                               @QueryParameter("zone") @RelativePath("..") String zone,
                                               @QueryParameter("credentialsId") @RelativePath("../..") final String credentialsId) {
            ListBoxModel items = new ListBoxModel();
            items.add("");
            if (zone == null || zone.isEmpty() || credentialsId == null || credentialsId.isEmpty()) {
                return items;
            }
            try {
                ClientFactory clientFactory = new ClientFactory(context, new ArrayList<DomainRequirement>(), credentialsId);
                ComputeClient compute = clientFactory.compute();
                List<AcceleratorType> acceleratorTypes = compute.getAcceleratorTypes(zone);

                for (AcceleratorType a : acceleratorTypes) {
                    items.add(a.getName(), a.getSelfLink());
                }
                return items;
            } catch (IOException ioe) {
                items.clear();
                items.add("Error retrieving GPU types");
                return items;
            }
        }
    }
}
