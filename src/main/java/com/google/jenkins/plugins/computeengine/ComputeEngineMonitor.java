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
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.AsyncPeriodicWork;
import hudson.model.TaskListener;
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
            "com.google.jenkins.plugins.computeengine.minimumInstanceCheckPeriod", Duration.ofMinutes(10));

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

    /** Provision minimum instances right after Jenkins has started */
    // TODO: getting error when using `InitMilestone.COMPLETED`
    /* com.google.jenkins.plugins.computeengine.integration.ComputeEngineCloudMinimumInstancesIT.testRetentionAgentsPreservedAcrossBuilds -- Time elapsed: 4.535 s <<< ERROR!
    java.lang.Exception: Jenkins initialization has not reached the COMPLETED initialization stage. Current state is Configuration for all jobs updated. Likely there is an issue with the Initialization task graph (e.g. usage of @Initializer(after = InitMilestone.COMPLETED)). See JENKINS-37759 for more inf
        * */
    @Initializer(after = InitMilestone.JOB_CONFIG_ADAPTED)
    public static void onStartup() {
        MinimumInstanceChecker.checkForMinimumInstances();
    }
}
