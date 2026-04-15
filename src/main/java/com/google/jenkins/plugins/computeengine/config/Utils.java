/*
 * Copyright 2024 CloudBees, Inc.
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

package com.google.jenkins.plugins.computeengine.config;

import hudson.util.FormValidation;
import org.kohsuke.stapler.QueryParameter;

class Utils {

    static FormValidation doCheckMaxRunDurationSeconds(@QueryParameter String value) {
        try {
            long maxRunDurationSeconds = Long.parseLong(value);
            if (maxRunDurationSeconds < 0) {
                return FormValidation.error("Max run duration must be greater than or equal to 0");
            }
            return FormValidation.ok();
        } catch (NumberFormatException e) {
            return FormValidation.error("Max run duration must be non-negative number");
        }
    }
}
