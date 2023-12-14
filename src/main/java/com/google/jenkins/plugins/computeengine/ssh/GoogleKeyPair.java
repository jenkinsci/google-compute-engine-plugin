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

import hudson.util.Secret;
import java.io.Serializable;
import java.util.Map;

/** Class to store auto generated key pair from SshKeysHelper Utility */
public class GoogleKeyPair extends GoogleKeyCredential implements Serializable {
    private final Secret privateKey;
    private final String publicKey;

    private GoogleKeyPair(String publicKey, Secret privateKey, String user) {
        super(user);
        this.publicKey = user + ":" + publicKey + " " + user;
        this.privateKey = privateKey;
    }

    public static GoogleKeyPair generate(String user) {
        Map<String, String> keys = SshKeysHelper.generate();
        return new GoogleKeyPair(keys.get("public"), Secret.fromString(keys.get("private")), user);
    }

    public String getPublicKey() {
        return publicKey;
    }

    public Secret getPrivateKey() {
        return privateKey;
    }

    @Override
    public String toString() {
        return "Public key:\n" + publicKey + "\n\nPrivate key:\n" + privateKey.getEncryptedValue();
    }
}
