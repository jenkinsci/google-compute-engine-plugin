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
package com.google.jenkins.plugins.computeengine;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import hudson.model.TaskListener;
import java.io.IOException;
import jenkins.security.MasterToSlaveCallable;
import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;

/**
 * Slave callback class checking if instance was preempted. All of code here is serialized and
 * executed on node.
 */
final class PreemptedCheckCallable extends MasterToSlaveCallable<Boolean, IOException> {
  private static final String METADATA_SERVER_URL =
      "http://metadata.google.internal/computeMetadata/v1/instance/preempted?wait_for_change=%s";

  private final TaskListener listener;

  /**
   * Callback constructor.
   *
   * @param listener Node listener on which we can add extra information from slave side.
   */
  PreemptedCheckCallable(TaskListener listener) {
    this.listener = listener;
  }

  /**
   * Actual callback code, executed on node side. Checks in Google metadata server if instance was
   * preempted.
   *
   * <p>See
   * https://cloud.google.com/compute/docs/instances/create-start-preemptible-instance#detecting_if_an_instance_was_preempted
   *
   * @return True if node was preempted.
   * @throws IOException Exception when calling Google metadata API
   */
  @Override
  public Boolean call() throws IOException {
    HttpRequest initialRequest = createMetadataRequest(false);
    HttpResponse initialResponse = initialRequest.execute();
    final String initialResult = IOUtils.toString(initialResponse.getContent(), Charsets.UTF_8);
    initialResponse.disconnect();
    if ("TRUE".equals(initialResult)) {
      listener
          .getLogger()
          .println("Instance was already preempted before monitoring metadata changes.");
      return true;
    }

    HttpRequest request = createMetadataRequest(true);
    listener.getLogger().println("Preemptive instance, listening to metadata for preemption event");
    HttpResponse response = request.execute();
    final String result = IOUtils.toString(response.getContent(), Charsets.UTF_8);
    listener.getLogger().println("Got preemption event " + result);
    response.disconnect();
    return "TRUE".equals(result);
  }

  private HttpRequest createMetadataRequest(boolean waitForChange) throws IOException {
    HttpTransport transport = new NetHttpTransport();
    GenericUrl metadata = new GenericUrl(getMetadataServerUrl(waitForChange));
    HttpRequest request = transport.createRequestFactory().buildGetRequest(metadata);
    request.setHeaders(new HttpHeaders().set("Metadata-Flavor", "Google"));
    request.setReadTimeout(Integer.MAX_VALUE);
    return request;
  }

  private static String getMetadataServerUrl(boolean waitForChange) {
    return String.format(METADATA_SERVER_URL, waitForChange);
  }
}
