/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.jenkins.plugins.computeengine;

import static com.google.jenkins.plugins.computeengine.ComputeEngineCloud.checkPermissions;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernameListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.google.common.base.Strings;
import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.io.Serializable;
import java.util.ArrayList;
import jenkins.model.Jenkins;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

/**
 * Class to contain information needed to configure and access Windows agents This avoids passing in
 * several parameters between multiple classes and also isolates logic in accessing credentials
 */
@Getter
@Setter(onMethod = @__(@DataBoundSetter))
@Builder(builderClassName = "Builder")
@AllArgsConstructor
@ToString
@EqualsAndHashCode
public class WindowsConfiguration implements Describable<WindowsConfiguration>, Serializable {
    private static final long serialVersionUID = 1L;
    private String passwordCredentialsId;
    private String privateKeyCredentialsId;

    @DataBoundConstructor
    public WindowsConfiguration() {}

    /**
     * Gets the password if a username and password credential is provided
     *
     * @return password in plain text to use for SSH
     */
    public String getPassword() {
        if (passwordCredentialsId.isEmpty()) {
            return null;
        }
        StandardUsernamePasswordCredentials cred = CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentials(
                        StandardUsernamePasswordCredentials.class, Jenkins.get(), ACL.SYSTEM, new ArrayList<>()),
                CredentialsMatchers.withId(passwordCredentialsId));
        if (cred == null) {
            return null;
        }
        return cred.getPassword().getPlainText();
    }

    /**
     * Returns the SSH private key if a SSH credential is provided
     *
     * @return SSH private key in plain text to use for SSH
     */
    public StandardUsernameCredentials getPrivateKeyCredentials() {
        if (Strings.isNullOrEmpty(privateKeyCredentialsId)) {
            return null;
        }
        return CredentialsMatchers.firstOrNull(
            new SystemCredentialsProvider.ProviderImpl()
                .getCredentials(SSHUserPrivateKey.class, Jenkins.get(), ACL.SYSTEM),
            CredentialsMatchers.withId(privateKeyCredentialsId));
    }

    @Extension
    public static final class DescriptorImpl extends Descriptor<WindowsConfiguration> {
        public ListBoxModel doFillPasswordCredentialsIdItems(@AncestorInPath Jenkins context) {
            checkPermissions(context, Jenkins.ADMINISTER);
            return new StandardListBoxModel()
                    .includeEmptyValue()
                    .includeMatchingAs(
                            ACL.SYSTEM,
                            context,
                            StandardUsernamePasswordCredentials.class,
                            new ArrayList<>(),
                            CredentialsMatchers.instanceOf(StandardUsernamePasswordCredentials.class));
        }

        public ListBoxModel doFillPrivateKeyCredentialsIdItems(@AncestorInPath Jenkins context) {
            checkPermissions(context, Jenkins.ADMINISTER);
            return new StandardUsernameListBoxModel()
                    .includeEmptyValue()
                    .includeMatchingAs(
                            ACL.SYSTEM,
                            context,
                            SSHUserPrivateKey.class,
                            new ArrayList<>(),
                            CredentialsMatchers.instanceOf(SSHUserPrivateKey.class));
        }

        public FormValidation doCheckPrivateKeyCredentialsId(
                @QueryParameter String value, @QueryParameter("passwordCredentialsId") String passwordCredentialsId) {
            if (Strings.isNullOrEmpty(value) && Strings.isNullOrEmpty(passwordCredentialsId)) {
                return FormValidation.error("A password or private key credential is required");
            }
            return FormValidation.ok();
        }
    }
}
