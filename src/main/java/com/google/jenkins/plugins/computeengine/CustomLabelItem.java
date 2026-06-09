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
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.kohsuke.stapler.DataBoundConstructor;

@Getter
@EqualsAndHashCode
public class CustomLabelItem implements Describable<CustomLabelItem> {
    @SuppressWarnings("lgtm[java/hardcoded-credential-api-call]")
    private final String key;

    private final String value;

    @DataBoundConstructor
    public CustomLabelItem(String key, String value) {
        this.key = key;
        this.value = value;
    }

    @Override
    public String toString() {
        return String.format("%s=%s", key, value);
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<CustomLabelItem> {}
}
