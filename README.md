<!--
 Copyright 2018 Google LLC

 Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 compliance with the License. You may obtain a copy of the License at

        https://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software distributed under the License
 is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 implied. See the License for the specific language governing permissions and limitations under the
 License.
-->
# Google Compute Engine Plugin for Jenkins
The Google Compute Engine (GCE) Plugin allows you to use GCE virtual machines (VMs) with Jenkins to execute build tasks. GCE VMs provision quickly, are destroyed by Jenkins when idle, and offer Preemptible VMs that run at a much lower price than regular VMs.

## Documentation
Please see the [Google Compute Engine Plugin](https://wiki.jenkins.io/display/JENKINS/Google+Compute+Engine+Plugin) wiki for complete documentation.

## Installation
1. Download the plugin from [here](https://storage.googleapis.com/jenkins-graphite/google-compute-plugin-latest.hpi).
1. Go to **Manage Jenkins** then **Manage Plugins**.
1. In the Plugin Manager, click the **Advanced** tab and then **Choose File** under the **Upload Plugin** section.
1. Choose the Jenkins plugin file downloaded in Step 1.
1. Click the **Upload** button.

## Feature requests and bug reports
Please file feature requests and bug reports under the `google-compute-engine-plugin` component of the [Jenkins CI JIRA](https://issues.jenkins-ci.org).

## Contributing
See [CONTRIBUTING.md](CONTRIBUTING.md)

## License
See [LICENSE](LICENSE)
