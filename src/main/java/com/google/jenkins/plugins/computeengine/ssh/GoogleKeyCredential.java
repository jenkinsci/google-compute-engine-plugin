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

/** Abstract class that is parent of GoogleKeyPair and GooglePrivateKey */
public abstract class GoogleKeyCredential implements Serializable {
    private final String user;

    public GoogleKeyCredential(String user) {
        this.user = user;
    }

    public abstract Secret getPrivateKey();
}
