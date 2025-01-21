package com.google.jenkins.plugins.computeengine.client;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.compute.Compute;
import com.google.cloud.graphite.platforms.plugin.client.ClientFactory;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.jenkins.plugins.computeengine.ComputeEngineScopeRequirement;
import com.google.jenkins.plugins.credentials.oauth.GoogleOAuth2Credentials;
import com.google.jenkins.plugins.credentials.oauth.GoogleRobotCredentials;
import hudson.AbortException;
import hudson.model.ItemGroup;
import hudson.security.ACL;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Optional;
import jenkins.model.Jenkins;

/** Utilities for using the gcp-plugin-core clients. */
public class ClientUtil {
    private static final String APPLICATION_NAME = "jenkins-google-compute-plugin";

    /**
     * Creates a {@link ClientFactory} for generating the GCP API clients.
     *
     * @param itemGroup The Jenkins context to use for retrieving the credentials.
     * @param domainRequirements A list of domain requirements.
     * @param credentialsId The ID of the credentials to use for generating clients.
     * @param transport An {@link Optional} parameter that specifies the {@link HttpTransport} to use.
     *     A default will be used if unspecified.
     * @return A {@link ClientFactory} to get clients.
     * @throws AbortException If there was an error initializing the ClientFactory.
     */
    public static ClientFactory getClientFactory(
            ItemGroup itemGroup,
            ImmutableList<DomainRequirement> domainRequirements,
            String credentialsId,
            Optional<HttpTransport> transport)
            throws AbortException {
        Preconditions.checkNotNull(itemGroup);
        Preconditions.checkNotNull(domainRequirements);
        Preconditions.checkArgument(!Strings.isNullOrEmpty(credentialsId));
        Preconditions.checkNotNull(transport);

        ClientFactory clientFactory;
        try {
            GoogleRobotCredentials robotCreds = getRobotCredentials(itemGroup, domainRequirements, credentialsId);
            Credential googleCredential = getGoogleCredential(robotCreds);
            clientFactory = new ClientFactory(transport, googleCredential, APPLICATION_NAME);
        } catch (IOException | GeneralSecurityException ex) {
            throw new AbortException(Messages.ClientFactory_FailedToInitializeHTTPTransport(ex));
        }
        return clientFactory;
    }

    /**
     * Creates a {@link ClientFactory} for generating the GCP API clients.
     *
     * @param itemGroup The Jenkins context to use for retrieving the credentials.
     * @param credentialsId The ID of the credentials to use for generating clients.
     * @return A {@link ClientFactory} to get clients.
     * @throws AbortException If there was an error initializing the ClientFactory.
     */
    public static ClientFactory getClientFactory(ItemGroup itemGroup, String credentialsId) throws AbortException {
        return getClientFactory(itemGroup, ImmutableList.of(), credentialsId, Optional.empty());
    }

    private static GoogleRobotCredentials getRobotCredentials(
            ItemGroup itemGroup, List<DomainRequirement> domainRequirements, String credentialsId)
            throws AbortException {

        GoogleOAuth2Credentials credentials = CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentials(
                        GoogleOAuth2Credentials.class, itemGroup, ACL.SYSTEM, domainRequirements),
                CredentialsMatchers.withId(credentialsId));

        if (!(credentials instanceof GoogleRobotCredentials)) {
            throw new AbortException(Messages.ClientFactory_FailedToRetrieveCredentials(credentialsId));
        }

        return (GoogleRobotCredentials) credentials;
    }

    private static Credential getGoogleCredential(GoogleRobotCredentials credentials) throws GeneralSecurityException {
        return credentials.getGoogleCredential(new ComputeEngineScopeRequirement());
    }

    public static ComputeClientV2 createComputeClientV2(String projectId, String credentialsId)
            throws GeneralSecurityException, IOException {
        Credential httpRequestInitializer = ClientUtil.getGoogleCredential(
                ClientUtil.getRobotCredentials(Jenkins.get(), ImmutableList.of(), credentialsId));
        Compute compute = new Compute.Builder(
                        GoogleNetHttpTransport.newTrustedTransport(), new JacksonFactory(), httpRequestInitializer)
                .setGoogleClientRequestInitializer(request ->
                        request.setRequestHeaders(request.getRequestHeaders().setUserAgent(APPLICATION_NAME)))
                .setApplicationName(ClientUtil.APPLICATION_NAME)
                .build();
        return new ComputeClientV2(projectId, compute);
    }
}
