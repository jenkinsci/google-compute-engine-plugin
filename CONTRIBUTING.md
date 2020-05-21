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
* By default, the integration tests are not executed. In case you are interested in executing 
  them, disable the `skipITs` property.
* The following environment variables are required to run the integration tests. 5, 6, and 7 are
  only required when running a windows integration test.

1. GOOGLE_PROJECT_ID
1. GOOGLE_CREDENTIALS
1. GOOGLE_REGION
1. GOOGLE_ZONE
1. GOOGLE_BOOT_DISK_PROJECT_ID
1. GOOGLE_BOOT_DISK_IMAGE_NAME
1. GOOGLE_JENKINS_PASSWORD
1. GOOGLE_SA_NAME

* Run the following:
```
mvn verify -DskipITs=false
```

###### Windows Integration Test
* By default, the integration tests only use linux based agents for testing. If you make a
  windows-related change, or otherwise want to test that a change still works for windows agents,
  run the tests with the flag `-Dit.windows=true` like this:
```bash
mvn verify -Dit.windows=true
```

Make sure you have these extra environment variables configured:
  * GOOGLE_BOOT_DISK_PROJECT_ID will be the same as your project id.
  * GOOGLE_BOOT_DISK_IMAGE_NAME will be the name of the image you created using packer in Google
    cloud console.
  * GOOGLE_JENKINS_PASSWORD will be the password you set when creating the image with packer, used
    for password based ssh authentication.
  * More information on building your baseline windows image can be found [here](WINDOWS.md) 
    and an example powershell script for setup can be found [here](windows-it-install.ps1).
