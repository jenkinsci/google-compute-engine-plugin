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

import hudson.Extension;
import hudson.model.AsyncPeriodicWork;
import hudson.model.TaskListener;
import hudson.model.listeners.ItemListener;
import java.time.Duration;
import java.util.logging.Logger;
import jenkins.util.SystemProperties;

/**
 * Periodically checks and maintains minimum instance count for each GCE cloud configuration.
 * This serves as a backup mechanism since minimum instances are normally created automatically
 * when an event happens (example: reaching a milestone in jenkins startup, agent is allocated to a build, etc.)
 */
@Extension
public class ComputeEngineMonitor extends AsyncPeriodicWork {
    private static final Logger LOGGER = Logger.getLogger(ComputeEngineMonitor.class.getName());

    private static final Duration recurrencePeriod = SystemProperties.getDuration(
            ComputeEngineMonitor.class.getName() + ".minimumInstanceCheckPeriod", Duration.ofMinutes(10));

    public ComputeEngineMonitor() {
        super("GCE minimum instances checker");
    }

    @Override
    public long getRecurrencePeriod() {
        return recurrencePeriod.toMillis();
    }

    @Override
    protected void execute(TaskListener listener) {
        LOGGER.fine("executing periodic check of minimum instances");
        MinimumInstanceChecker.checkForMinimumInstances();
    }

    /** Provision minimum instances right after Jenkins has fully started. */
    @Extension
    public static class OnStartupListener extends ItemListener {
        @Override
        public void onLoaded() {
            LOGGER.info("launching minimum instances checker on startup");
            MinimumInstanceChecker.checkForMinimumInstances();
        }
    }
}
