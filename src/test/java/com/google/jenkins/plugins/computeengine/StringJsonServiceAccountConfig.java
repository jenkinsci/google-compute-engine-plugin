/*
 * Copyright 2018 Google LLC
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

import com.google.api.client.json.jackson.JacksonFactory;
import com.google.api.client.util.PemReader;
import com.google.jenkins.plugins.credentials.oauth.JsonKey;
import com.google.jenkins.plugins.credentials.oauth.ServiceAccountConfig;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * A {@link ServiceAccountConfig} depending solely on a secret String. Only used
 * for integration tests.
 *
 * @deprecated To be replaced by {@link
 *     com.google.jenkins.plugins.credentials.oauth.JsonServiceAccountConfig} when upgrading the
 *     google-oauth-plugin to 0.8.
 */
@Deprecated
public class StringJsonServiceAccountConfig extends ServiceAccountConfig {
  private static final long serialVersionUID = 6818111194672325387L;
  private static final Logger LOGGER =
      Logger.getLogger(
          com.google.jenkins.plugins.credentials.oauth.JsonServiceAccountConfig.class
              .getSimpleName());

  private transient JsonKey jsonKey;

  @DataBoundConstructor
  public StringJsonServiceAccountConfig(String jsonKeyString) {
    if (jsonKeyString != null) {
      InputStream stream = new ByteArrayInputStream(jsonKeyString.getBytes(StandardCharsets.UTF_8));
      try {
        jsonKey = JsonKey.load(new JacksonFactory(), stream);
      } catch (IOException e) {
        LOGGER.log(Level.SEVERE, "Failed to read json key from file", e);
      }
    }
  }

  @Override
  public com.google.jenkins.plugins.credentials.oauth.JsonServiceAccountConfig.DescriptorImpl
      getDescriptor() {
    return (com.google.jenkins.plugins.credentials.oauth.JsonServiceAccountConfig.DescriptorImpl)
        Jenkins.get()
            .getDescriptorOrDie(
                com.google.jenkins.plugins.credentials.oauth.JsonServiceAccountConfig.class);
  }

  @Override
  public String getAccountId() {
    if (jsonKey != null) {
      return jsonKey.getClientEmail();
    }
    return null;
  }

  @Override
  public PrivateKey getPrivateKey() {
    if (jsonKey != null) {
      String privateKey = jsonKey.getPrivateKey();
      if (privateKey != null && !privateKey.isEmpty()) {
        PemReader pemReader = new PemReader(new StringReader(privateKey));
        try {
          PemReader.Section section = pemReader.readNextSection();
          PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(section.getBase64DecodedBytes());
          return KeyFactory.getInstance("RSA").generatePrivate(keySpec);
        } catch (IOException | InvalidKeySpecException | NoSuchAlgorithmException e) {
          LOGGER.log(Level.SEVERE, "Failed to read private key", e);
        }
      }
    }
    return null;
  }
}
