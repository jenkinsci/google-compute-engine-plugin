/*
 * Copyright 2018 Google LLC
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

import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.google.common.base.Strings;
import hudson.security.ACL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import jenkins.model.Jenkins;

/**
 * Class to contain information needed to configure and access Windows agents This avoids passing in
 * several parameters between multiple classes and also isolates logic in accessing credentials
 */
public class WindowsConfiguration {

  private String windowsUsername;
  private Optional<String> passwordCredentialsId;
  private Optional<String> privateKeyCredentialsId;

  /**
   * Constructor for WindowsConfig
   *
   * @param windowsUsername Username of an existing account on the windows agent
   * @param passwordCredentialsId Credentials Id of credential containing password for the account
   *     under windowsUsername
   * @param privateKeyCredentialsId Credentials Id of credential containing private SSH key for
   *     account under windowsUsername
   */
  public WindowsConfiguration(
      String windowsUsername, String passwordCredentialsId, String privateKeyCredentialsId) {
    this.windowsUsername = windowsUsername;
    // TODO (rachelyen) verify both are non-null
    this.privateKeyCredentialsId =
        Optional.ofNullable(Strings.emptyToNull(privateKeyCredentialsId));
    this.passwordCredentialsId = Optional.ofNullable(Strings.emptyToNull(passwordCredentialsId));
  }

  /**
   * Getter for windowsUsername
   *
   * @return windows username string
   */
  public String getWindowsUsername() {
    return this.windowsUsername;
  }

  /**
   * Returns the Optional for privateKeyCredentialsId. May be null if user provided password
   * credentials instead
   *
   * @return
   */
  public Optional<String> getPrivateKeyCredentialsId() {
    return privateKeyCredentialsId;
  }

  /**
   * Gets the password if a username and password credential is provided
   *
   * @return password in plain text to use for SSH
   */
  public String getPassword() {
    List<DomainRequirement> domainRequirements = new ArrayList<DomainRequirement>();

    StandardUsernamePasswordCredentials cred =
        CredentialsMatchers.firstOrNull(
            CredentialsProvider.lookupCredentials(
                StandardUsernamePasswordCredentials.class,
                Jenkins.get(),
                ACL.SYSTEM,
                domainRequirements),
            CredentialsMatchers.withId(passwordCredentialsId.get()));

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
    StandardUsernameCredentials cred =
        CredentialsMatchers.firstOrNull(
            new SystemCredentialsProvider.ProviderImpl()
                .getCredentials(BasicSSHUserPrivateKey.class, Jenkins.get(), ACL.SYSTEM),
            CredentialsMatchers.withId(privateKeyCredentialsId.get()));
    return cred;
  }
}
