/*
 * Copyright 2020 Google LLC
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

import static com.google.jenkins.plugins.computeengine.ComputeEngineCloud.checkPermissions;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.google.common.base.Strings;
import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Serializable;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import jenkins.model.Jenkins;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.java.Log;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

/**
 * Class to contain information needed to configure and access SSH Credential information if custom
 * private SSH key option is selected. This avoids passing in several parameters between multiple
 * classes and also isolates logic in accessing credentials.
 */
@Getter
@Setter(onMethod = @__(@DataBoundSetter))
@Builder(builderClassName = "Builder")
@AllArgsConstructor
@ToString
@EqualsAndHashCode
@Log
public class SshConfiguration implements Describable<SshConfiguration>, Serializable {

  private String customPrivateKeyCredentialsId;

  @DataBoundConstructor
  public SshConfiguration() {}

  /**
   * Returns the SSH private key if a custom SSH Credential is selected.
   *
   * @param id private key id from selected credential
   * @return SSH private key in plain text to use for SSH
   */
  public static SSHUserPrivateKey getCustomPrivateKeyCredentials(String id) {
    if (Strings.isNullOrEmpty(id)) {
      return null;
    }
    return CredentialsMatchers.firstOrNull(
        CredentialsProvider.lookupCredentials(
            SSHUserPrivateKey.class, Jenkins.get(), null, Collections.emptyList()),
        CredentialsMatchers.withId(id));
  }

  @SuppressWarnings("unchecked")
  @Override
  public Descriptor<SshConfiguration> getDescriptor() {
    return Jenkins.get().getDescriptor(SshConfiguration.class);
  }

  @Extension
  public static final class DescriptorImpl extends Descriptor<SshConfiguration> {

    public ListBoxModel doFillCustomPrivateKeyCredentialsIdItems(
        @AncestorInPath Jenkins context, @QueryParameter String customPrivateKeyCredentialsId) {
      checkPermissions();
      if (context == null || !context.hasPermission(Item.CONFIGURE)) {
        return new StandardListBoxModel();
      }

      StandardListBoxModel result = new StandardListBoxModel();

      return result
          .includeMatchingAs(
              ACL.SYSTEM,
              context,
              SSHUserPrivateKey.class,
              new ArrayList<>(),
              CredentialsMatchers.always())
          .includeCurrentValue(customPrivateKeyCredentialsId);
    }

    public FormValidation doCheckCustomPrivateKeyCredentialsId(
        @QueryParameter String value,
        @QueryParameter("customPrivateKeyCredentialsId") String customPrivateKeyCredentialsId)
        throws IOException {
      checkPermissions();

      if (Strings.isNullOrEmpty(value) && Strings.isNullOrEmpty(customPrivateKeyCredentialsId)) {
        return FormValidation.error("An SSH private key credential is required");
      }

      SSHUserPrivateKey customPrivateKey = getCustomPrivateKeyCredentials(value);
      String privateKeyString = "";

      if (customPrivateKey != null) {
        privateKeyString = customPrivateKey.getPrivateKeys().get(0);
      } else {
        return FormValidation.error(
            "Cannot find credential with name \"" + value + "\" in Global Credentials");
      }

      boolean hasProperStart = false;
      boolean hasProperEnd = false;
      BufferedReader br = new BufferedReader(new StringReader(privateKeyString));
      String nextLine;

      while ((nextLine = br.readLine()) != null) {
        if (nextLine.equals("-----BEGIN RSA PRIVATE KEY-----")
            || nextLine.equals("-----BEGIN OPENSSH PRIVATE KEY-----")) {
          hasProperStart = true;
        }
        if (nextLine.equals("-----END RSA PRIVATE KEY-----")
            || nextLine.equals("-----END OPENSSH PRIVATE KEY-----")) {
          hasProperEnd = true;
        }
      }

      if (!hasProperStart) {
        return FormValidation.error("Invalid private key format (missing BEGIN key marker)");
      }

      if (!hasProperEnd) {
        return FormValidation.error("Invalid private key format (missing END key marker)");
      }
      return FormValidation.ok();
    }
  }
}
