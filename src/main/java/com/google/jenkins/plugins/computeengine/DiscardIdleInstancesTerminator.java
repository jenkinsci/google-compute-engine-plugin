/*
 * Copyright 2026 CloudBees, Inc.
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

import hudson.init.Terminator;
import hudson.model.Computer;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;

public class DiscardIdleInstancesTerminator {

    private static final Logger LOGGER = Logger.getLogger(DiscardIdleInstancesTerminator.class.getName());

    @Terminator
    public static void discardIdleInstances() {
        LOGGER.fine("Looking for idle GCE instances to discard during shutdown");
        var futures = Arrays.stream(Jenkins.get().getComputers())
                .filter(ComputeEngineComputer.class::isInstance)
                .filter(Computer::isIdle)
                .map(Computer::getNode)
                .filter(ComputeEngineInstance.class::isInstance)
                .map(ComputeEngineInstance.class::cast)
                .filter(ComputeEngineInstance::isTerminateIdleDuringShutdown)
                .map(DiscardIdleInstancesTerminator::terminateInstanceAsync)
                .toList();
        /* Wait for all terminations to avoid classloader unload while tasks run (would cause NoClassDefFoundError and leave VMs running).
        `ComputeEngineInstance._terminate` calls GCP async APIs, so should return quickly; 10s timeout is sufficient. */
        try {
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).get(10, TimeUnit.SECONDS);
            LOGGER.fine(() -> "Done discarding idle instances, there were " + futures.size() + " instances to discard");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Timeout or error waiting for GCE instance terminations", e);
        }
    }

    private static CompletableFuture<Void> terminateInstanceAsync(ComputeEngineInstance node) {
        return CompletableFuture.runAsync(
                () -> {
                    LOGGER.info(() -> "Discarding idle GCE instance " + node.getNodeName() + " during shutdown");
                    try {
                        node.terminate();
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, "Failed to terminate instance " + node.getNodeName(), e);
                    }
                },
                Computer.threadPoolForRemoting);
    }
}
