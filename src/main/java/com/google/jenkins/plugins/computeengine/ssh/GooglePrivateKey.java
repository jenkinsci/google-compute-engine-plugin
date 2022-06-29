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

package com.google.jenkins.plugins.computeengine.ssh;

import com.google.jenkins.plugins.computeengine.SshConfiguration;
import hudson.util.Secret;
import java.io.Serializable;

/** Class to store optional custom private key selected by user */
public class GooglePrivateKey extends GoogleKeyCredential implements Serializable {
  private final Secret privateKey;

  private GooglePrivateKey(Secret privateKey, String user) {
    super(user);
    this.privateKey = privateKey;
  }

  public static GooglePrivateKey generate(String credentialsId, String user) {
    String privateKeyStr =
        SshConfiguration.getCustomPrivateKeyCredentials(credentialsId).getPrivateKeys().get(0);
    return new GooglePrivateKey(Secret.fromString(privateKeyStr), user);
  }

  public Secret getPrivateKey() {
    return privateKey;
  }

  @Override
  public String toString() {
    return "Private key:\n" + privateKey.getEncryptedValue();
  }
}
