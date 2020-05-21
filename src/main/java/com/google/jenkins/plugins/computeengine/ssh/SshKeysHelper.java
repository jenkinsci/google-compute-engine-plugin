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

import static com.google.api.client.util.Throwables.propagate;

import com.google.common.collect.ImmutableMap;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.KeyPair;
import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/** Utility for generating OpenSSH RSA key pairs for use in GoogleKeyPair. */
class SshKeysHelper {
  private static final int KEY_SIZE = 2048;

  static Map<String, String> generate() {
    JSch jsch = new JSch();
    KeyPair pair;
    try {
      pair = KeyPair.genKeyPair(jsch, KeyPair.RSA, KEY_SIZE);
    } catch (JSchException e) {
      throw propagate(e);
    }
    ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
    builder.put("public", getPublicKey(pair));
    builder.put("private", getPrivateKey(pair));
    return builder.build();
  }

  private static String getPublicKey(KeyPair pair) {
    return "ssh-rsa " + Base64.getEncoder().encodeToString(pair.getPublicKeyBlob());
  }

  private static String getPrivateKey(KeyPair pair) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    pair.writePrivateKey(out);
    try {
      return out.toString(StandardCharsets.US_ASCII.toString());
    } catch (UnsupportedEncodingException e) {
      throw propagate(e);
    }
  }
}
