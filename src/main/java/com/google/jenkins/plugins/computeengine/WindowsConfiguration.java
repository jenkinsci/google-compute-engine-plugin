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

/**
 *
 * Class to contain information needed to configure and access Windows agents
 * This avoids passing in several parameters between multiple classes and also isolates logic in accessing credentials
 *
 */
public class WindowsConfiguration {

    private String windowsUsername;

    private String passwordCredentialsId;
    private String privateKeyCredentialsId;

    public WindowsConfiguration(String windowsUsername, String passwordCredentialsId, String privateKeyCredentialsId) {
        this.windowsUsername = windowsUsername;
        this.privateKeyCredentialsId = privateKeyCredentialsId;
        this.passwordCredentialsId = passwordCredentialsId;
    }

    public String getWindowsUsername() {
        return this.windowsUsername;
    }

    public String getPrivateKeyCredentialsId() {
        return privateKeyCredentialsId;
    }

    public String getPassword() throws Exception {
        if (passwordCredentialsId == null) {
            throw new Exception("No username password credentials Id was provided");
        }
        List<DomainRequirement> domainRequirements = new ArrayList<DomainRequirement>();

        StandardUsernamePasswordCredentials cred = CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentials(StandardUsernamePasswordCredentials.class,
                        Jenkins.getInstance(), ACL.SYSTEM, domainRequirements),
                CredentialsMatchers.withId(passwordCredentialsId));
        return cred.getPassword().getPlainText();
    }

    public StandardUsernameCredentials getPrivateKeyCredentials() throws Exception {
        if (privateKeyCredentialsId == null) {
            throw new Exception("No SSH credentials Id was provided");
        }
        StandardUsernameCredentials cred = CredentialsMatchers.firstOrNull(
                new SystemCredentialsProvider.ProviderImpl().getCredentials(BasicSSHUserPrivateKey.class,
                        Jenkins.getInstance(), ACL.SYSTEM),
                CredentialsMatchers.withId(privateKeyCredentialsId));
        return cred;
    }
}
