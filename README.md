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

[![Build Status](https://ci.jenkins.io/job/Plugins/job/google-compute-engine-plugin/job/master/badge/icon)](https://ci.jenkins.io/job/Plugins/job/google-compute-engine-plugin/job/develop/)
[![Contributors](https://img.shields.io/github/contributors/jenkinsci/google-compute-engine-plugin.svg)](https://github.com/jenkinsci/google-compute-engine-plugin/graphs/contributors)
[![Jenkins Plugin](https://img.shields.io/jenkins/plugin/v/google-compute-engine.svg)](https://plugins.jenkins.io/google-compute-engine)
[![GitHub release](https://img.shields.io/github/v/tag/jenkinsci/google-compute-engine-plugin?label=changelog)](https://github.com/jenkinsci/google-compute-engine-plugin/blob/develop/CHANGELOG.md)
[![Jenkins Plugin Installs](https://img.shields.io/jenkins/plugin/i/google-compute-engine.svg?color=blue)](https://plugins.jenkins.io/google-compute-engine)


# Google Compute Engine Plugin for Jenkins
**Prerequisite:**
1. Java11 (openjdk 11.0.19 2023-04-18 LTS) download via sdkman
1. Maven

Create settings.xml file **setting.xml** at path **~/.m2/** and insert the following code in it

```xml
<settings>
  <pluginGroups>
    <pluginGroup>org.jenkins-ci.tools</pluginGroup>
  </pluginGroups>
  <profiles>
    <!-- Give access to Jenkins plugins -->
    <profile>
      <id>jenkins</id>
      <activation>
        <activeByDefault>true</activeByDefault> <!-- change this to false, if you don't like to have it on per default -->
      </activation>
      <repositories>
        <repository>
          <id>repo.jenkins-ci.org</id>
          <url>https://repo.jenkins-ci.org/public/</url>
        </repository>
      </repositories>
      <pluginRepositories>
        <pluginRepository>
          <id>repo.jenkins-ci.org</id>
          <url>https://repo.jenkins-ci.org/public/</url>
        </pluginRepository>
      </pluginRepositories>
    </profile>
  </profiles>
  <mirrors>
    <mirror>
      <id>repo.jenkins-ci.org</id>
      <url>https://repo.jenkins-ci.org/public/</url>
      <mirrorOf>m.g.o-public</mirrorOf>
    </mirror>
  </mirrors>
</settings>
```

# Google Compute Engine Plugin for Jenkins
The Google Compute Engine (GCE) Plugin allows you to use GCE virtual machines (VMs) with Jenkins to execute build tasks. GCE VMs provision quickly, are destroyed by Jenkins when idle, and offer Preemptible VMs that run at a much lower price than regular VMs.

## Documentation
Please see the [Google Compute Engine Plugin](docs/Home.md) docs for complete documentation.

## Installation
2. Download the plugin from [here](https://storage.googleapis.com/jenkins-graphite/google-compute-plugin-latest.hpi).
2. Go to **Manage Jenkins** then **Manage Plugins**.
2. In the Plugin Manager, click the **Advanced** tab and then **Choose File** under the **Upload Plugin** section.
2. Choose the Jenkins plugin file downloaded in Step 1.
2. Click the **Upload** button.

## Plugin Source Build Installation
See [Plugin Source Build Installation](docs/source_build_installation.md) to build and install from
source.

## Configuration as Code Support
Support for [Jenkins Configuration as Code](https://jenkins.io/projects/jcasc/). See the below examples that are already automatically tested:

* [A configuration example](./src/test/resources/com/google/jenkins/plugins/computeengine/configuration-as-code.yml)
* [Another configuration example with Windows workers](./src/test/resources/com/google/jenkins/plugins/computeengine/integration/configuration-as-code-windows-it.yml)


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
[gcp-slack](https://cloud.google.com/community#home-support).

## Contributing
See [CONTRIBUTING.md](CONTRIBUTING.md)

## License
See [LICENSE](LICENSE)
