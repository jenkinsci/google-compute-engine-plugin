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

[![Build Status](https://ci.jenkins.io/job/Plugins/job/google-compute-engine-plugin/job/master/badge/icon)](https://ci.jenkins.io/job/Plugins/job/google-compute-engine-plugin/job/develop/)
[![Contributors](https://img.shields.io/github/contributors/jenkinsci/google-compute-engine-plugin.svg)](https://github.com/jenkinsci/google-compute-engine-plugin/graphs/contributors)
[![Jenkins Plugin](https://img.shields.io/jenkins/plugin/v/google-compute-engine.svg)](https://plugins.jenkins.io/google-compute-engine)
[![GitHub release](https://img.shields.io/github/v/tag/jenkinsci/google-compute-engine-plugin?label=changelog)](https://github.com/jenkinsci/google-compute-engine-plugin/blob/develop/CHANGELOG.md)
[![Jenkins Plugin Installs](https://img.shields.io/jenkins/plugin/i/google-compute-engine.svg?color=blue)](https://plugins.jenkins.io/google-compute-engine)

# Google Compute Engine Plugin for Jenkins
The Google Compute Engine (GCE) Plugin allows you to use GCE virtual machines (VMs) with Jenkins to execute build tasks. GCE VMs provision quickly, are destroyed by Jenkins when idle, and offer Preemptible VMs that run at a much lower price than regular VMs.

## Documentation
Please see the [Google Compute Engine Plugin](docs/Home.md) docs for complete documentation.

## Installation
1. Download the plugin from [here](https://storage.googleapis.com/jenkins-graphite/google-compute-plugin-latest.hpi).
1. Go to **Manage Jenkins** then **Manage Plugins**.
1. In the Plugin Manager, click the **Advanced** tab and then **Choose File** under the **Upload Plugin** section.
1. Choose the Jenkins plugin file downloaded in Step 1.
1. Click the **Upload** button.

## Feature requests and bug reports
Please file feature requests and bug reports under [issues](https://github.com/jenkinsci/google-compute-engine-plugin/issues).

**NOTE**: Starting with version 4.0, you will be required to use version 0.9 or higher of the
[Google OAuth Credentials plugin](https://github.com/jenkinsci/google-oauth-plugin). Version 0.9 of
the OAuth plugin is still compatible with older versions of this plugin. Please verify you are
using the correct versions before filing a bug request.

## Community

The GCP Jenkins community uses the **#gcp-jenkins** slack channel on
[https://googlecloud-community.slack.com](https://googlecloud-community.slack.com)
to ask questions and share feedback. Invitation link available here:
[http://bit.ly/gcp-slack](http://bit.ly/gcp-slack).

## Contributing
See [CONTRIBUTING.md](CONTRIBUTING.md)

## License
See [LICENSE](LICENSE)
