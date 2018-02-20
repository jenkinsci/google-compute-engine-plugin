/*
 * Copyright 2018 Google Inc.
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
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.services.AbstractGoogleClientRequest;
import com.google.api.client.googleapis.services.GoogleClientRequestInitializer;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.compute.Compute;
import com.google.jenkins.plugins.computeengine.ComputeEngineScopeRequirement;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotCredentials;
import hudson.AbortException;
import hudson.model.ItemGroup;
import hudson.security.ACL;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

/**
 * Creates clients for communicating with Google APIs.
 */
public class ClientFactory {
    public static final String APPLICATION_NAME = "jenkins-google-compute-plugin";

    private static HttpTransport DEFAULT_TRANSPORT;

    private final HttpTransport transport;
    private final JsonFactory jsonFactory;
    private final GoogleRobotCredentials credentials;
    private final HttpRequestInitializer gcred;

    /**
     *
     * @param itemGroup A handle to the Jenkins instance
     * @param domainRequirements
     * @param credentialsId The idea of a GoogleRobotCredentials credential
     * @throws IOException
     */
    public ClientFactory(ItemGroup itemGroup, List<DomainRequirement> domainRequirements, String credentialsId)
            throws IOException {
        if (credentialsId == null) {
            throw new IllegalArgumentException(Messages.ClientFactory_CredentialsIdRequired());
        }

        try {
            this.transport = getDefaultTransport();
        } catch (GeneralSecurityException e) {
            throw new AbortException(
                    Messages.ClientFactory_FailedToInitializeHTTPTransport(e.getMessage()));
        }
        this.jsonFactory = new JacksonFactory();

        ComputeEngineScopeRequirement requirement = new ComputeEngineScopeRequirement();

        this.credentials = CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentials(
                        GoogleRobotCredentials.class,
                        itemGroup,
                        ACL.SYSTEM,
                        domainRequirements),
                CredentialsMatchers.withId(credentialsId)
        );

        if (credentials == null) {
            throw new AbortException(Messages.ClientFactory_FailedToRetrieveCredentials(credentialsId));
        }

        try {
            this.gcred = credentials.getGoogleCredential(requirement);
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
        ComputeClient client = new ComputeClient();
        client.setCompute(new Compute.Builder(transport, jsonFactory, gcred)
                .setGoogleClientRequestInitializer(new GoogleClientRequestInitializer() {
                    @Override
                    public void initialize(AbstractGoogleClientRequest<?> request) throws IOException {
                        request.setRequestHeaders((request.getRequestHeaders().setUserAgent(APPLICATION_NAME)));
                    }
                })
                .setApplicationName(APPLICATION_NAME)
                .build());
        return client;
    }
}