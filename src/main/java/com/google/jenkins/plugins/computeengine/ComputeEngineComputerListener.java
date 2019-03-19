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

import hudson.Extension;
import hudson.model.Computer;
import hudson.model.TaskListener;
import hudson.slaves.ComputerListener;

import java.io.IOException;

@Extension
public class ComputeEngineComputerListener extends ComputerListener {
    @Override
    public void onOnline(Computer c, TaskListener listener) throws IOException {
        if (c instanceof ComputeEngineComputer) {
            ComputeEngineComputer computer = (ComputeEngineComputer) c;
            computer.onConnected(listener);
        }
    }
}
