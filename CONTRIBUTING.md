<!--
 Copyright 2020 Google LLC

 Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 compliance with the License. You may obtain a copy of the License at

        https://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software distributed under the License
 is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 implied. See the License for the specific language governing permissions and limitations under the
 License.
-->
# How to contribute

We'd love to accept your patches and contributions to this project. There are
just a few small guidelines you need to follow.

## Contributor License Agreement

Contributions to this project must be accompanied by a Contributor License
Agreement. You (or your employer) retain the copyright to your contribution,
this simply gives us permission to use and redistribute your contributions as
part of the project. Head over to <https://cla.developers.google.com/> to see
your current agreements on file or to sign a new one.

You generally only need to submit a CLA once, so if you've already submitted one
(even if it was for a different project), you probably don't need to do it
again.

## Code reviews

All submissions, including submissions by project members, require review. We
use GitHub pull requests for this purpose. Consult
[GitHub Help](https://help.github.com/articles/about-pull-requests/) for more
information on using pull requests.

## Getting your pull request through
### Testing
An essential part of getting your change through is to make sure all existing tests pass.

#### Prerequisites
* Maven to build and run tests.
* A GCP project to test on with the compute engine API enabled with all relevant permissions
  enabled, especially billing. Running integration tests will also incur billing.
* For development, IntelliJ is recommended.
* **For Windows Images/VM's**, have Java and OpenSSH pre-installed. We have suggested
  startup-scripts for installing both if you do not want to pre-install,
  but pre-installing is advised.


#### Running the tests
* Write/change tests as necessary based on the code changes you made.
* Make sure you are at the directory where pom.xml is located.

##### Unit Tests
* Run the following: 
    ```
    mvn test
    ```

##### Integration Tests

Integration tests provision actual instances in a GCP project, run pipeline, take snapshot etc.
Therefore, they are disabled in the CI and expected to be executed by contributors in their laptop itself.

Reasons for disabling integration test in CI,
* getting provisioning GCP infra is not possible
* even if we did get a GCP infra setup in the CI, it is risky to expose that, as someone can abuse the CI.

By default, integration tests are skipped from the maven goals, need to enable using the `skipITs` property.

Steps to execute integration test
* Prepare VM images  
  (ideally we should automate this see idea [here](https://github.com/jenkinsci/google-compute-engine-plugin/pull/492#discussion_r1892705637))  
  The jenkins agent images need to have java installed. We have a packer script to create the image and upload to your configured GCP project.
  The scripts are located in [testimages/linux](./testimages/linux)
  Navigate to the directory and execute,
  ```bash
  bash setup-gce-image.sh
  ```
  * The above agent image contains the `java` command available on the PATH; which the plugin uses by default for launching the agent.
    This plugin also supports configuring custom path for java executable, and we have an integration test for that `ComputeEngineCloudNonStandardJavaIT`.
    If you would like to execute this test, please create an image with `java` command not being on the PATH, but at a custom path `/usr/bin/non-standard-java`.  
    To create a non-standard java image, execute,
    ```bash
    bash setup-gce-image.sh non-standard-java
    ```
  If you want to delete the images or recreate them, use the arguments `--recreate` or `--delete`.

* Create a service account with relevant access - See [Refer to IAM Credentials](Home.md#iam-credentials)   

* Export these mandatory environment variable   
  ```bash
  export GOOGLE_PROJECT_ID=your-project-id
  export GOOGLE_CREDENTIALS_FILE=/path/to/sa-key.json
  export GOOGLE_REGION=us-central1
  export GOOGLE_ZONE=us-central1-a
  export GOOGLE_SA_NAME=jenkins-agent-sa
  ```
* Run the integration tests as,  
  * Run all the tests   
    ```bash
    mvn verify -DskipITs=false
    ```
  * Run a specific test class  
    ```bash
    mvn clean test -Dtest=ComputeEngineCloudRestartPreemptedIT
    ```
  * Run a specific test method  
    ```bash
    mvn clean test -Dtest=ComputeEngineCloudRestartPreemptedIT#testIfNodeWasPreempted
    ```
  You can also debug the tests with surefire by passing `-Dmaven.surefire.debug=true` and in your IDE connect to remote debug port `8000`.

###### Windows Integration Test
* By default, the integration tests only use linux based agents for testing. If you make a
  windows-related change, or otherwise want to test that a change still works for windows agents,
  run the tests with the flag `-Dit.windows=true` like this:  
  ```bash
  mvn verify -Dit.windows=true
  ```
* You need to prepare the windows image before running the tests.  
  * Build the image using the Packer setup in `testimages/windows/`:  
    ```bash
    # Password is auto-generated if not set
    export JENKINS_PASSWORD=your-secure-password  # optional
    bash testimages/windows/setup-gce-image.sh
    ```
    This creates a Windows Server 2022 image with Java 21 and OpenSSH pre-installed.
    The build can be run from any platform (macOS, Linux) — it does not require a Windows machine.
    Use `--recreate` to rebuild an existing image, or `--delete` to remove it.
  * In addition to the environment variables mentioned in the previous section, also export these variables too,  
    ```bash
    export GOOGLE_BOOT_DISK_PROJECT_ID=your-project-id # will be the same as your project id
    export GOOGLE_BOOT_DISK_IMAGE_NAME=jenkins-gce-integration-test-windows-jre
    export GOOGLE_JENKINS_PASSWORD=password # the password from the image build output
    ```
