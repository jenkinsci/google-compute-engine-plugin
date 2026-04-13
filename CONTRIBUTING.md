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
# Contributing

We'd love to accept your patches and contributions to this project.

## Prerequisites

- JDK 21 or later (Java 17 was dropped from Jenkins weekly in Jan 2026 and from the LTS line in April 2026)
- Maven 3.9 or later
- (Recommended) IntelliJ IDEA
- For integration tests: a GCP project with the Compute Engine API enabled and billing active

## Quick Start

```bash
git clone git@github.com:jenkinsci/google-compute-engine-plugin.git
cd google-compute-engine-plugin
mvn clean verify          # compile + unit tests
mvn hpi:run               # launch Jenkins locally with the plugin at http://localhost:8080/jenkins
```

## Building from Source

```bash
mvn clean package         # build the plugin
mvn hpi:hpi               # produce the .hpi file in target/
```

To install the built plugin:

1. Go to **Manage Jenkins > Plugins > Advanced**.
2. Under **Deploy Plugin**, click **Choose File** and select the `.hpi` from `target/`.
3. Click **Deploy**.

## Code Style

The project uses [Spotless](https://github.com/diffplug/spotless) for code formatting, enforced during the build. If the build fails on formatting:

```bash
mvn spotless:apply        # auto-fix formatting
```

## Contributor License Agreement

Contributions to this project must be accompanied by a Contributor License
Agreement. You (or your employer) retain the copyright to your contribution;
this simply gives us permission to use and redistribute your contributions as
part of the project. Head over to <https://cla.developers.google.com/> to see
your current agreements on file or to sign a new one.

You generally only need to submit a CLA once, so if you've already submitted one
(even if it was for a different project), you probably don't need to do it
again.

## Code Reviews

All submissions, including submissions by project members, require review. We
use GitHub pull requests for this purpose. Consult
[GitHub Help](https://help.github.com/articles/about-pull-requests/) for more
information on using pull requests.

## Testing

### Unit Tests

```bash
mvn test
```

### Integration Tests

Integration tests provision actual GCE instances, run pipelines, and take snapshots. They are disabled by default and expected to be run on a contributor's own machine with a real GCP project.

**Why disabled in CI:**
- Requires a GCP project with billing — not feasible in public CI.
- Exposing GCP credentials in CI is a security risk.

#### Prepare VM Images

The agent images need Java pre-installed. Packer scripts create and upload the images to your GCP project.

**Linux image:**

```bash
cd testimages/linux
bash setup-gce-image.sh
```

The default image has `java` on PATH. To also create a non-standard-java image (java at `/usr/bin/non-standard-java`) for `ComputeEngineCloudNonStandardJavaIT`:

```bash
bash setup-gce-image.sh non-standard-java
```

Use `--recreate` to rebuild or `--delete` to remove images.

**Windows image:**

```bash
# Password is auto-generated if JENKINS_PASSWORD is not set
export JENKINS_PASSWORD=your-secure-password  # optional
bash testimages/windows/setup-gce-image.sh
```

This creates a Windows Server 2022 image with Java 21 and OpenSSH pre-installed. The build runs from any platform (macOS, Linux) — it does not require a Windows machine. Use `--recreate` to rebuild or `--delete` to remove.

#### Set Environment Variables

```bash
export GOOGLE_PROJECT_ID=your-project-id
export GOOGLE_CREDENTIALS_FILE=/path/to/sa-key.json
export GOOGLE_REGION=us-central1
export GOOGLE_ZONE=us-central1-a
export GOOGLE_SA_NAME=jenkins-agent-sa
```

For Windows tests, also export:

```bash
export GOOGLE_BOOT_DISK_PROJECT_ID=your-project-id
export GOOGLE_BOOT_DISK_IMAGE_NAME=jenkins-gce-integration-test-windows-jre
export GOOGLE_JENKINS_PASSWORD=password-from-image-build
```

#### Create a Service Account

See the [IAM Credentials setup](README.md#create-a-gcp-service-account) in the user documentation.

#### Run Tests

Run all integration tests:

```bash
mvn verify -DskipITs=false
```

Run a specific test class:

```bash
mvn clean test -Dtest=ComputeEngineCloudRestartPreemptedIT
```

Run a specific test method:

```bash
mvn clean test -Dtest=ComputeEngineCloudRestartPreemptedIT#testIfNodeWasPreempted
```

Run Windows integration tests:

```bash
mvn verify -Dit.windows=true
```

#### Debugging Tests

Attach a remote debugger on port 8000:

```bash
mvn clean test -Dtest=YourTestClass -Dmaven.surefire.debug=true
```

Then connect your IDE's remote debug configuration to `localhost:8000`.

## License

See [LICENSE](LICENSE)
