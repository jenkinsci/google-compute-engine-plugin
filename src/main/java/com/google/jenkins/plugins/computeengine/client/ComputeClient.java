package com.google.jenkins.plugins.computeengine.client;

import com.google.api.services.compute.Compute;
import com.google.api.services.compute.model.Region;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Client for communicating with the Google Compute API
 *
 *  @see <a href="https://cloud.google.com/compute/">Cloud Engine</a>
 */
public class ComputeClient {
    private final Compute compute;
    private final String projectId;

    ComputeClient(Compute compute, String projectId) {
        this.compute = compute;
        this.projectId = projectId;
    }

    /**
     *
     * @return
     * @throws IOException
     */
    public List<Region> getRegions() throws IOException {
        List<Region> regions = compute
                .regions()
                .list(projectId)
                .execute()
                .getItems();

        // Sort by name
        regions.sort(Comparator.comparing(Region::getName));

        // No deprecated regions
        regions.removeIf(r-> r.getDeprecated() != null);
        return regions;
    }
}