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
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

@Getter
@EqualsAndHashCode
public class CustomMetadataItem implements Describable<CustomMetadataItem> {
    private static final Set<String> RESERVED_KEYS = Set.of(
            InstanceConfiguration.SSH_METADATA_KEY,
            InstanceConfiguration.METADATA_LINUX_STARTUP_SCRIPT_KEY,
            InstanceConfiguration.METADATA_WINDOWS_STARTUP_SCRIPT_KEY,
            InstanceConfiguration.GUEST_ATTRIBUTES_METADATA_KEY);

    private final String key;
    private final String value;

    @DataBoundConstructor
    public CustomMetadataItem(String key, String value) throws Descriptor.FormException {
        if (RESERVED_KEYS.contains(key)) {
            throw new Descriptor.FormException(
                    "Custom Metadata: key '" + key + "' is reserved for internal use by the plugin.", "key");
        }
        this.key = key;
        this.value = value;
    }

    @Override
    public String toString() {
        return String.format("%s=%s", key, value);
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<CustomMetadataItem> {
        public FormValidation doCheckKey(@QueryParameter String value) {
            if (value == null || value.isEmpty()) {
                return FormValidation.error("Key must not be empty");
            }
            if (RESERVED_KEYS.contains(value)) {
                return FormValidation.error("Key '%s' is reserved for internal use by the plugin.", value);
            }
            return FormValidation.ok();
        }
    }
}
