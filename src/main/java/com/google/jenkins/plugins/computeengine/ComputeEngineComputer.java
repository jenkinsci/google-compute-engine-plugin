package com.google.jenkins.plugins.computeengine;

import com.google.api.services.compute.model.Instance;
import hudson.slaves.AbstractCloudComputer;
import org.kohsuke.stapler.HttpRedirect;
import org.kohsuke.stapler.HttpResponse;

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
     * @return
     * @throws IOException
     */
    public String getInstanceStatus() throws IOException {
        instance = _getInstance();
        return instance.getStatus();
    }

    private Instance _getInstance() throws IOException {
        ComputeEngineInstance node = getNode();
        ComputeEngineCloud cloud = getCloud();

        return cloud.client.getInstance(cloud.projectId, node.zone, node.getNodeName());
    }

    protected ComputeEngineCloud getCloud() {
        ComputeEngineInstance node = getNode();
        if (node == null)
            return null;

        return node.getCloud();
    }

    /**
     * When the slave is deleted, terminate the instance.
     */
    @Override
    public HttpResponse doDoDelete() throws IOException {
        checkPermission(DELETE);
        if (getNode() != null) {
            try {
                getNode().terminate();
            } catch (InterruptedException ie) {
                //TODO: log
            }
        }
        return new HttpRedirect("..");
    }
}
