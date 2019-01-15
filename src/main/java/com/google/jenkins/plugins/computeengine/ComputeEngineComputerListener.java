/*
 * Copyright 2018 Google Inc. All Rights Reserved.
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
import hudson.Extension;
import hudson.model.Computer;
import hudson.model.TaskListener;
import hudson.slaves.ComputerListener;
import jenkins.model.Jenkins;
import jenkins.security.MasterToSlaveCallable;
import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;

import java.io.IOException;


@Extension
public class ComputeEngineComputerListener extends ComputerListener {
    final String SHUTDONW_SCRIPT = "shutdown-script";

    @Override
    public void onOnline(Computer c, TaskListener listener) throws IOException {
        if (c instanceof ComputeEngineComputer) {
            ComputeEngineComputer computer = (ComputeEngineComputer) c;
            computer.onConnected();
            
            if (computer.getPreemptible()) {
//                if (!computer.getInstance().getMetadata().containsKey(SHUTDONW_SCRIPT)) {
//                    computer.getInstance().getMetadata().
//                }
                Jenkins.MasterComputer.threadPoolForRemoting.submit(() -> {
                    printMessage("Calling 0 local");
                    try {
                        Boolean result = computer.getChannel().call(new MasterCall(listener));
                        printMessage("Calling 0 local result " + result);
                        computer.setPreempted(result);
                    } catch (IOException | InterruptedException e) {
                        printMessage("Calling 0.1 exception " + e.getMessage());
                    }
                });
            }
        }
    }


    private void printMessage(String string) {
        System.out.println("CC local: " + string);
    }

    private static final class MasterCall extends MasterToSlaveCallable<Boolean, IOException> {
        private static final String METADATA_SERVER_URL = "http://metadata.google.internal/computeMetadata/v1/instance/preempted?wait_for_change=true";

        private final TaskListener listener;

        private MasterCall(TaskListener listener) {
            this.listener = listener;
        }

        @Override
        public Boolean call() throws IOException {
            printMessage("Calling 111 ext");
            return runningOnComputeEngine();
        }

        private Boolean runningOnComputeEngine() throws IOException {
            printMessage("Calling 112 ext");
            HttpTransport transport = new NetHttpTransport();
            printMessage("Calling 113 ext");
            GenericUrl metadata = new GenericUrl(METADATA_SERVER_URL);
            HttpRequest request = transport.createRequestFactory().buildGetRequest(metadata);
            request.setHeaders(new HttpHeaders().set("Metadata-Flavor", "Google"));
            request.setReadTimeout(Integer.MAX_VALUE);
            printMessage("Calling 114 ext");
            HttpResponse response = request.execute();
            printMessage("Calling 2 ext response " + response.getStatusMessage());
            final String result = IOUtils.toString(response.getContent(), Charsets.UTF_8);
            printMessage("Calling 2 ext result " + result);
            response.disconnect();
            return "TRUE".equals(result);
        }

        private void printMessage(String string) {
            listener.error("CC extrernal: " + string);
            System.out.println("CC extrernal: " + string);
        }

    }
}
