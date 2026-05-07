/*
 * Copyright 2026 CloudBees, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
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
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

@Getter
@Setter(onMethod = @__(@DataBoundSetter))
@Builder(builderClassName = "Builder")
@AllArgsConstructor
@ToString
@EqualsAndHashCode
public class ShieldedVmConfiguration implements Describable<ShieldedVmConfiguration>, Serializable {

    private boolean enableSecureBoot;
    private boolean enableVtpm;
    private boolean enableIntegrityMonitoring;

    @DataBoundConstructor
    public ShieldedVmConfiguration() {
        this.enableSecureBoot = true;
        this.enableVtpm = true;
        this.enableIntegrityMonitoring = true;
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<ShieldedVmConfiguration> {}
}
