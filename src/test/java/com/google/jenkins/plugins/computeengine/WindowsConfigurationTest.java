package com.google.jenkins.plugins.computeengine;

import com.cloudbees.jenkins.plugins.sshcredentials.SSHUserPrivateKey;
import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsDescriptor;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.Collections;
import java.util.List;

public class WindowsConfigurationTest {

  @Rule public JenkinsRule r = new JenkinsRule();

  @Test
  public void testHttpCredentials() {
    WindowsConfiguration.DescriptorImpl descriptor =
        r.jenkins.getDescriptorByType(WindowsConfiguration.DescriptorImpl.class);
    Assert.assertNotNull(descriptor);
    ListBoxModel m = descriptor.doFillPasswordCredentialsIdItems(r.jenkins);
    // Ensure empty value is added
    MatcherAssert.assertThat(m.size(), CoreMatchers.is(1));
    SystemCredentialsProvider.getInstance()
        .getCredentials()
        .add(
            new UsernamePasswordCredentialsImpl(
                CredentialsScope.SYSTEM, "system_id", "system_ak", "system_sk", "system_desc"));
    // Ensure added SYSTEM credential is displayed
    m = descriptor.doFillPasswordCredentialsIdItems(r.jenkins);
    MatcherAssert.assertThat(m.size(), CoreMatchers.is(2));
    // Ensure added GLOBAL credential is displayed
    SystemCredentialsProvider.getInstance()
        .getCredentials()
        .add(
            new UsernamePasswordCredentialsImpl(
                CredentialsScope.GLOBAL, "global_id", "global_ak", "global_sk", "global_desc"));
    m = descriptor.doFillPasswordCredentialsIdItems(r.jenkins);
    MatcherAssert.assertThat(m.size(), CoreMatchers.is(3));
  }

  @Test
  public void testSshCredentials() {
    WindowsConfiguration.DescriptorImpl descriptor =
        r.jenkins.getDescriptorByType(WindowsConfiguration.DescriptorImpl.class);
    Assert.assertNotNull(descriptor);
    ListBoxModel m = descriptor.doFillPrivateKeyCredentialsIdItems(r.jenkins);
    // Ensure empty value is added
    MatcherAssert.assertThat(m.size(), CoreMatchers.is(1));
    SystemCredentialsProvider.getInstance()
        .getCredentials()
        .add(
            new BasicSSHUserPrivateKey(
                CredentialsScope.SYSTEM,
                "shi",
                "key",
                new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource("somekey"),
                "",
                ""));
    // Ensure added SYSTEM credential is displayed
    m = descriptor.doFillPrivateKeyCredentialsIdItems(r.jenkins);
    MatcherAssert.assertThat(m.size(), CoreMatchers.is(2));
    // Ensure added GLOBAL credential is displayed
    SystemCredentialsProvider.getInstance()
        .getCredentials()
        .add(
            new BasicSSHUserPrivateKey(
                CredentialsScope.SYSTEM,
                "ghi",
                "key",
                new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource("somekey"),
                "",
                ""));
    m = descriptor.doFillPrivateKeyCredentialsIdItems(r.jenkins);
    MatcherAssert.assertThat(m.size(), CoreMatchers.is(3));

    // Ensure other implementations of SSHUserPrivateKey are selectable
    SystemCredentialsProvider.getInstance()
        .getCredentials()
        .add(new SSHUserPrivateKey() {
          @NonNull
          @Override
          public String getPrivateKey() {
            return "somekey";
          }

          @Override
          public Secret getPassphrase() {
            return null;
          }

          @NonNull
          @Override
          public List<String> getPrivateKeys() {
            return Collections.emptyList();
          }

          @NonNull
          @Override
          public String getDescription() {
            return "custom-ssk-key";
          }

          @NonNull
          @Override
          public String getId() {
            return "custom-ssk-key";
          }

          @NonNull
          @Override
          public String getUsername() {
            return "test";
          }

          @Override
          public CredentialsScope getScope() {
            return CredentialsScope.GLOBAL;
          }

          @NonNull
          @Override
          public CredentialsDescriptor getDescriptor() {
            return null;
          }
        });
    m = descriptor.doFillPrivateKeyCredentialsIdItems(r.jenkins);
    MatcherAssert.assertThat(m.size(), CoreMatchers.is(4));
  }
}
