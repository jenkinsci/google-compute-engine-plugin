package com.google.jenkins.plugins.computeengine;

import com.google.api.services.compute.model.Instance;
import hudson.slaves.AbstractCloudComputer;
import hudson.slaves.Cloud;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.HttpRedirect;
import org.kohsuke.stapler.HttpResponse;

import javax.annotation.CheckForNull;
import java.io.IOException;

public class ComputeEngineComputer extends AbstractCloudComputer<ComputeEngineInstance> {

    private volatile Instance instance;

    public ComputeEngineComputer(ComputeEngineInstance slave) {
        super(slave);
    }

    @Override
    public ComputeEngineInstance getNode() {
        return (ComputeEngineInstance) super.getNode();
    }

    public void onConnected() {
        ComputeEngineInstance node = getNode();
        if (node != null) {
            node.onConnected();
        }
    }

    public String getNumExecutorsStr() {
        return "computer";
        //return String.valueOf(super.getNumExecutors());
    }

    @DataBoundSetter
    public void setNumExecutorsStr(String value) {
        Integer v = InstanceConfiguration.intOrDefault(value, InstanceConfiguration.DEFAULT_NUM_EXECUTORS);
        ComputeEngineInstance node = getNode();
        if(node != null) {
            node.setNumExecutors(v);
        }
    }

    /**
     * Returns a cached representation of the Instance
     *
     * @return
     * @throws IOException
     */
    public Instance getInstance() throws IOException {
        if (instance == null)
            instance = _getInstance();
        return instance;
    }

    public Instance refreshInstance() throws IOException {
        instance = _getInstance();
        return instance;
    }

    /**
     * Returns the most current status of the Instance as reported by the GCE API
     *
     * @return
     * @throws IOException
     */
    public String getInstanceStatus() throws IOException {
        instance = _getInstance();
        return instance.getStatus();
    }

    private Instance _getInstance() throws IOException {
        try {
            ComputeEngineInstance node = getNode();
            ComputeEngineCloud cloud = getCloud();

            if (node != null) {
                return cloud.client.getInstance(cloud.projectId, node.zone, node.getNodeName());
            } else {
                return null;
            }
        } catch (CloudNotFoundException cnfe) {
            return null;
        }
    }

    protected ComputeEngineCloud getCloud() throws CloudNotFoundException {
        ComputeEngineInstance node = getNode();
        if (node == null)
            throw new CloudNotFoundException("Could not retrieve cloud from empty node");

        return node.getCloud();
    }

    /**
     * When the slave is deleted, terminate the instance.
     */
    @Override
    public HttpResponse doDoDelete() throws IOException {
        checkPermission(DELETE);
        ComputeEngineInstance node = getNode();
        if (node != null) {
            try {
                node.terminate();
            } catch (InterruptedException ie) {
                //TODO: log
            }
        }
        return new HttpRedirect("..");
    }
}
