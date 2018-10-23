package com.google.jenkins.plugins.computeengine;

import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import hudson.security.ACL;
import jenkins.model.Jenkins;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


/**
 *
 * Class to contain information needed to configure and access Windows agents
 * This avoids passing in several parameters between multiple classes and also isolates logic in accessing credentials
 *
 */
public class WindowsConfiguration {

    private String windowsUsername;
    private Optional<String> passwordCredentialsId;
    private Optional<String> privateKeyCredentialsId;

    /**
     * Constructor for WindowsConfig
     *
     * @param windowsUsername Username of an existing account on the windows agent
     * @param passwordCredentialsId Credentials Id of credential containing password for the account under windowsUsername
     * @param privateKeyCredentialsId Credentials Id of credential containing private SSH key for account under windowsUsername
     */
    public WindowsConfiguration(String windowsUsername, String passwordCredentialsId, String privateKeyCredentialsId) {
        this.windowsUsername = windowsUsername;
        this.privateKeyCredentialsId = Optional.ofNullable(privateKeyCredentialsId);
        this.passwordCredentialsId = Optional.ofNullable(passwordCredentialsId);
    }

    /**
     * Getter for windowsUsername
     * @return windows username string
     */
    public String getWindowsUsername() {
        return this.windowsUsername;
    }

    /**
     * Returns the Optional for privateKeyCredentialsId.
     * May be null if user provided password credentials instead
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

        StandardUsernamePasswordCredentials cred = CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentials(StandardUsernamePasswordCredentials.class,
                        Jenkins.getInstance(), ACL.SYSTEM, domainRequirements),
                CredentialsMatchers.withId(passwordCredentialsId.get()));
        return cred.getPassword().getPlainText();
    }

    /**
     * Returns the SSH private key if a SSH credential is provided
     *
     * @return SSH private key in plain text to use for SSH
     */
    public StandardUsernameCredentials getPrivateKeyCredentials() {
        StandardUsernameCredentials cred = CredentialsMatchers.firstOrNull(
                new SystemCredentialsProvider.ProviderImpl().getCredentials(BasicSSHUserPrivateKey.class,
                        Jenkins.getInstance(), ACL.SYSTEM),
                CredentialsMatchers.withId(privateKeyCredentialsId.get()));
        return cred;
    }
}
