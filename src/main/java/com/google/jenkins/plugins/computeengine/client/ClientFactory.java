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

package com.google.jenkins.plugins.computeengine.client;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.graphite.platforms.plugin.client.ComputeClient;
import com.google.jenkins.plugins.computeengine.ComputeEngineScopeRequirement;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotCredentials;
import hudson.AbortException;
import hudson.model.ItemGroup;
import hudson.security.ACL;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Optional;

/** Creates clients for communicating with Google APIs. */
public class ClientFactory {
  public static final String APPLICATION_NAME = "jenkins-google-compute-plugin";

  private static HttpTransport DEFAULT_TRANSPORT;

  private final com.google.graphite.platforms.plugin.client.ClientFactory clientFactory;

  /**
   * @param itemGroup A handle to the Jenkins instance
   * @param domainRequirements
   * @param credentialsId The idea of a GoogleRobotCredentials credential
   * @throws IOException
   */
  public ClientFactory(
      ItemGroup itemGroup, List<DomainRequirement> domainRequirements, String credentialsId)
      throws IOException {
    if (credentialsId == null) {
      throw new IllegalArgumentException(Messages.ClientFactory_CredentialsIdRequired());
    }

    HttpTransport transport;
    try {
      transport = getDefaultTransport();
    } catch (GeneralSecurityException e) {
      throw new AbortException(
          Messages.ClientFactory_FailedToInitializeHTTPTransport(e.getMessage()));
    }

    ComputeEngineScopeRequirement requirement = new ComputeEngineScopeRequirement();

    GoogleRobotCredentials credentials =
        CredentialsMatchers.firstOrNull(
            CredentialsProvider.lookupCredentials(
                GoogleRobotCredentials.class, itemGroup, ACL.SYSTEM, domainRequirements),
            CredentialsMatchers.withId(credentialsId));

    if (credentials == null) {
      throw new AbortException(Messages.ClientFactory_FailedToRetrieveCredentials(credentialsId));
    }

    Credential gcred;
    try {
      gcred = credentials.getGoogleCredential(requirement);
    } catch (GeneralSecurityException e) {
      throw new AbortException(
          Messages.ClientFactory_FailedToInitializeHTTPTransport(e.getMessage()));
    }
    try {
      this.clientFactory =
          new com.google.graphite.platforms.plugin.client.ClientFactory(
              Optional.of(transport), gcred, APPLICATION_NAME);
    } catch (GeneralSecurityException e) {
      throw new AbortException(
          Messages.ClientFactory_FailedToInitializeHTTPTransport(e.getMessage()));
    }
  }

  private static synchronized HttpTransport getDefaultTransport()
      throws GeneralSecurityException, IOException {
    if (DEFAULT_TRANSPORT == null) {
      DEFAULT_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
    }
    return DEFAULT_TRANSPORT;
  }

  public static synchronized void setDefaultTransport(HttpTransport transport) {
    DEFAULT_TRANSPORT = transport;
  }

  public ComputeClient compute() {
    return clientFactory.computeClient();
  }
}
