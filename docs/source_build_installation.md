<!--
 Copyright 2019 Google LLC

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

     https://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
-->
# Plugin Source Build Installation

1. Clone the plugin and enter the directory:
    ```bash
    git clone git@github.com:jenkinsci/google-compute-engine-plugin.git
    cd google-compute-engine-plugin
    ```
1. Checkout the branch that you would like to build from:
    ```bash      
    git checkout <branch name>
    ```
1. Build the plugin into a .hpi plugin file. When running a build for the first time, run the clean and package maven goals:
    ```bash
    mvn clean package
    ```
   Followed by:
    ```bash
    mvn hpi:hpi
    ```
1. Go to **Manage Jenkins** then **Manage Plugins**.
1. In the Plugin Manager, click the **Advanced** tab and then **Choose File** under the **Upload Plugin** section.
1. Choose the Jenkins plugin file built in Step 3.
1. Click the **Upload** button.
