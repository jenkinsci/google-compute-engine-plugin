/*
 * Copyright 2019 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.jenkins.plugins.computeengine;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import hudson.model.TaskListener;
import jenkins.security.MasterToSlaveCallable;
import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;

import java.io.IOException;

final class PreemptedCheckCallable extends MasterToSlaveCallable<Boolean, IOException> {
    private static final String METADATA_SERVER_URL = "http://metadata.google.internal/computeMetadata/v1/instance/preempted?wait_for_change=true";

    private final TaskListener listener;

    PreemptedCheckCallable(TaskListener listener) {
        this.listener = listener;
    }

    @Override
    public Boolean call() throws IOException {
        HttpTransport transport = new NetHttpTransport();
        GenericUrl metadata = new GenericUrl(METADATA_SERVER_URL);
        HttpRequest request = transport.createRequestFactory().buildGetRequest(metadata);
        request.setHeaders(new HttpHeaders().set("Metadata-Flavor", "Google"));
        request.setReadTimeout(Integer.MAX_VALUE);
        listener.getLogger().println("Preemptible instance, listening metadata for preemption event");
        HttpResponse response = request.execute();
        final String result = IOUtils.toString(response.getContent(), Charsets.UTF_8);
        listener.getLogger().println("Got preemption event " + result);
        response.disconnect();
        return "TRUE".equals(result);
    }
}
