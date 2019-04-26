<!--
 Copyright 2019 Google LLC

 Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 compliance with the License. You may obtain a copy of the License at

        https://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software distributed under the License
 is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 implied. See the License for the specific language governing permissions and limitations under the
 License.
-->
# Changelog
All notable changes to this project will be documented in this file.

## [3.0.1] - 2019-04-26
### Added
- Code formatting tools for pom.xml and java source files
- Support for use with Configuration as Code plugin, with test dependencies on configuration-as-code
  version 1.9
- Secret used during integration test can be provided through GOOGLE_CREDENTIALS_FILE environment
  variable which should be a path to the secret file
- Documentation transferred from wiki.jenkins.io and stored in source

### Changed
- Use unique instance IDs for each cloud
- Test classes run in parallel using the concurrency property in pom.xml
- Constructor for InstanceConfiguration now only takes required fields, use builder instead
- Readme links updated to point to Github for issues and pull requests
- Plugin parent pom upgraded to 3.36

### Fixed
- Remote location for agent.jar can now be specified
- The relevant files now all have properly formatted license headers

## [3.0.0] - 2019-03-13
### Fixed
- Removed windows username field from instance configuration; use runAsUser instead. Default user changed to jenkins from Build.
- Cleaned up imports, removed outdated terminology, and fixed type of shell for Windodws integration tests.

## [2.0.0] - 2019-02-26
### Added
- One-shot instances feature
- Create snapshot option for one-shot instances

### Changes
- Changed Jenkins version to 2.107.1

### Fixed
- Changed script to build Windows image to accommodate updates in OpenSSH for Windows

## [1.0.9] - 2019-01-30
### Added
- Cleanup of nodes that weren't terminated properly

### Fixed
- Null issue for no vm template selected

## [1.0.8] - 2019-01-14
### Added
- Compute Engine Instance templates feature added

### Changes
- Instance cap based on number of executors, not on number of instances
- Increased the timeout for integration tests

### Fixed
- Labels from instance configuration now properly allocated to instances
- Fixed typo in documentation - license file
- Fixed typo in jelly help file
