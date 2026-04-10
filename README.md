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

The Google Compute Engine (GCE) Plugin provisions GCE virtual machines as Jenkins agents on demand. Agents are launched when builds need them, and terminated when idle. The plugin supports Linux and Windows VMs, Spot and Preemptible instances, startup scripts, GPU acceleration, additional disk attachments, and Configuration as Code.

## Table of Contents

- [Prerequisites](#prerequisites)
- [Setup](#setup)
  - [Create a GCP Service Account](#create-a-gcp-service-account)
  - [Add Credentials to Jenkins](#add-credentials-to-jenkins)
  - [Add a GCE Cloud](#add-a-gce-cloud)
- [Instance Configuration](#instance-configuration)
  - [General](#general)
  - [Launch Configuration](#launch-configuration)
  - [One-Shot](#one-shot)
  - [Location](#location)
  - [Machine Configuration (Advanced)](#machine-configuration-advanced)
  - [Networking](#networking)
  - [Boot Disk](#boot-disk)
  - [Disk Mapping](#disk-mapping)
  - [IAM](#iam)
- [Configuration Reference](#configuration-reference)
- [Configuration as Code (CasC)](#configuration-as-code-casc)
- [Windows Agents](#windows-agents)
- [Advanced Features](#advanced-features)
  - [Provisioning Types: Standard, Spot, and Preemptible](#provisioning-types-standard-spot-and-preemptible)
  - [Minimum Instance Scaling](#minimum-instance-scaling)
  - [Startup Script Exit Reporter](#startup-script-exit-reporter)
  - [Disk Mapping](#disk-mapping-1)
  - [Custom Metadata](#custom-metadata)
  - [One-Shot Instances and Snapshots](#one-shot-instances-and-snapshots)
  - [Instance Templates](#instance-templates)
  - [Custom Java Path](#custom-java-path)
- [Provisioning Behavior](#provisioning-behavior)
- [Troubleshooting](#troubleshooting)
- [Feature Requests and Bug Reports](#feature-requests-and-bug-reports)
- [Community](#community)
- [Contributing](#contributing)
- [License](#license)

## Prerequisites

- Jenkins 2.516.3 or later
- [Google OAuth Credentials plugin](https://github.com/jenkinsci/google-oauth-plugin) 0.9 or later
- A GCP project with the [Compute Engine API](https://console.cloud.google.com/apis/api/compute.googleapis.com) enabled
- A GCP service account with these IAM roles:
  - `roles/compute.instanceAdmin` -- create and manage VMs
  - `roles/compute.networkAdmin` -- configure networking
  - `roles/iam.serviceAccountUser` -- attach service accounts to VMs

## Setup

### Create a GCP Service Account

```bash
gcloud iam service-accounts create jenkins-gce

export PROJECT=$(gcloud info --format='value(config.project)')
export SA_EMAIL=$(gcloud iam service-accounts list \
  --filter="name:jenkins-gce" --format='value(email)')

gcloud projects add-iam-policy-binding $PROJECT \
  --member serviceAccount:$SA_EMAIL --role roles/compute.instanceAdmin
gcloud projects add-iam-policy-binding $PROJECT \
  --member serviceAccount:$SA_EMAIL --role roles/compute.networkAdmin
gcloud projects add-iam-policy-binding $PROJECT \
  --member serviceAccount:$SA_EMAIL --role roles/iam.serviceAccountUser
```

Download a JSON key for the service account:

```bash
gcloud iam service-accounts keys create \
  --iam-account $SA_EMAIL jenkins-gce.json
```

### Add Credentials to Jenkins

1. Go to **Manage Jenkins > Credentials > System > Global credentials > Add credentials**.
2. In the **Kind** dropdown, select **Google Service Account from private key**.
3. Enter your project name and upload the JSON key created above.
4. Click **OK**.

### Add a GCE Cloud

Each GCE cloud configuration points to a single GCP project. You can add multiple clouds for different projects.

1. Go to **Manage Jenkins > Nodes and Clouds > Clouds**.
2. Click **Add a new cloud > Google Compute Engine**.
3. Enter a **Name** for the cloud and the **Project ID**.
4. Select the credentials you added in the previous step from the **Service Account Credentials** dropdown.
5. Set the **Instance Cap** to limit the total number of concurrent VMs across all instance configurations.

## Instance Configuration

Instance configurations define what kind of VM to launch for a given set of Jenkins labels. You can create multiple instance configurations per cloud. Click the **Add** button under Instance Configurations to create one.

### General

| Field | Description |
|-------|-------------|
| **Name Prefix** | Prefix for VM names. Must match `[a-z]([-a-z0-9]*[a-z0-9])?`, max 50 characters. A random suffix is appended. |
| **Description** | Display name for this configuration in the Jenkins UI. |
| **Node Retention Time** | Minutes to keep an idle agent before termination. Default: 6. |
| **Usage** | `Use this node as much as possible` (Normal) or `Only build jobs with label expressions matching this node` (Exclusive). |
| **Labels** | Space-separated labels for matching builds to this configuration. When set to Exclusive mode, only builds requesting these labels will use this agent type. |
| **Number of Executors** | Parallel build slots per agent. Default: 1. Must be 1 when one-shot is enabled. |
| **Terminate idle agents during shutdown** | Delete idle agents and their GCE instances when the Jenkins controller stops. |
| **Minimum Number of Instances** | Total agents (busy + idle) to maintain at all times. The plugin proactively launches agents to meet this count. Default: 0 (disabled). |
| **Minimum Number of Spare Instances** | Idle agents to keep ready for incoming builds. Works alongside the minimum total. Default: 0 (disabled). |
| **Time Range** | Optionally restrict minimum instance scaling to specific hours and days of the week. Supports `HH:mm` or `h:mm a` time formats. |

### Launch Configuration

| Field | Description |
|-------|-------------|
| **Launch Timeout** | Seconds to wait for an agent to connect before giving up. Default: 300. |
| **Use Internal IP** | Connect to the agent via its internal IP even if an external IP is assigned. Use this when the Jenkins controller is in the same VPC. |
| **Ignore Jenkins Proxy** | Bypass the Jenkins HTTP proxy when connecting to the agent. |
| **Run as user** | SSH username on the agent. Default: `jenkins`. |
| **Custom SSH Private Key** | Authenticate with a custom SSH key pair instead of the auto-generated one. The corresponding public key must be in the agent image's `~/.ssh/authorized_keys` for the configured user. Public key format: `ssh-rsa <key> <credential-id>`. |
| **Remote Location** | Agent working directory. Default: `/tmp` (Linux) or `C:\` (Windows). |
| **Java Path** | Path to the Java executable on the agent. Default: `java` (must be on PATH). |
| **Windows** | Enable for Windows VMs. See [Windows Agents](#windows-agents). |

### One-Shot

| Field | Description |
|-------|-------------|
| **Enabled** | Delete the agent after a single build completes. Number of executors must be 1. |
| **Create snapshot** | Take a disk snapshot when a one-shot instance terminates. Only available when one-shot is enabled. |

### Location

| Field | Description |
|-------|-------------|
| **Region** | GCP region (e.g. `us-central1`). Populates from your project. |
| **Zone** | GCP zone within the region (e.g. `us-central1-a`). |

### Machine Configuration (Advanced)

These fields are inside the **Advanced** section of Machine Configuration.

| Field | Description |
|-------|-------------|
| **Template** | Use a GCE [instance template](https://cloud.google.com/compute/docs/instance-templates/) instead of configuring machine details here. When a template is selected, all other advanced settings are ignored. |
| **Provisioning Type** | `Standard`, `Spot`, or `Preemptible`. See [Provisioning Types](#provisioning-types-standard-spot-and-preemptible). |
| **Max Run Duration Seconds** | (Standard and Spot only) GCP automatically deletes the VM after this many seconds. See [Limit VM runtime](https://cloud.google.com/compute/docs/instances/limit-vm-runtime). |
| **Machine Type** | VM shape (e.g. `n1-standard-1`). Populates from your project and zone. |
| **Minimum CPU Platform** | Request a specific CPU generation (e.g. Intel Skylake). |
| **Startup script** | Bash (Linux) or PowerShell (Windows) script that runs before the agent connects. |
| **Linux startup script exit reporter** | Script that reports startup completion to a GCE guest attribute so the controller waits before launching the agent. Uses `$1` as the exit code placeholder. Default: `curl`. Clear to disable waiting. See [Startup Script Exit Reporter](#startup-script-exit-reporter). |
| **Windows startup script exit reporter** | Same as above for Windows. Uses `$args[0]` as the exit code placeholder. Default: `Invoke-RestMethod`. |
| **Custom metadata** | Additional key-value pairs set as instance metadata. Reserved keys (`ssh-keys`, `startup-script`, `windows-startup-script-ps1`, `enable-guest-attributes`) cannot be overridden. |
| **GPUs** | Attach GPUs by specifying a type (e.g. `nvidia-tesla-t4`) and count. See [GPUs on Compute Engine](https://cloud.google.com/compute/docs/gpus). |

### Networking

| Field | Description |
|-------|-------------|
| **Network Configuration** | `Autofilled` selects from networks/subnetworks in the current project. `Shared VPC` lets you specify a host project, region, and subnetwork name for cross-project networking. |
| **Network tags** | Space-delimited tags applied to the VM. Each tag must match `[a-z]([-a-z0-9]*[a-z0-9])?`. Use these to apply [firewall rules](https://cloud.google.com/vpc/docs/firewalls) -- at minimum, allow TCP port 22 from the Jenkins controller to agents. |
| **IP stack type** | `Single stack` (IPv4 only) or `Dual stack` (IPv4 + IPv6). |
| **External IPv4 Address** | Attach an external IPv4 address. Without an external IP or a [Cloud NAT](https://cloud.google.com/nat/docs/overview) gateway, the VM cannot reach the internet. |

### Boot Disk

| Field | Description |
|-------|-------------|
| **Image project** | GCP project containing the boot image. Your project is listed first, followed by well-known public projects (debian-cloud, ubuntu-os-cloud, windows-cloud, etc.). |
| **Image name** | The OS image. Must have Java installed and accessible at the configured Java Path. |
| **Disk Type** | Storage type (e.g. `pd-ssd`, `pd-standard`, `pd-balanced`). Larger disks get higher IOPS and throughput. |
| **Size** | Boot disk size in GB. Must be at least as large as the image requires. Default: 10. |
| **Delete on termination** | Delete the boot disk when the instance is terminated. Default: true. |

### Disk Mapping

Attach additional disks to the instance. One disk per line, using comma-separated `key=value` pairs. Three modes:

1. **Create from snapshot** -- line contains `source-snapshot`:
   ```
   source-snapshot=my-data-snapshot,size=100,type=pd-ssd,auto-delete=yes
   ```

2. **Create blank disk** -- line contains `size` or `type` but no `source-snapshot`:
   ```
   size=200,type=pd-ssd,name=scratch-disk,auto-delete=yes
   ```

3. **Attach existing disk** -- line contains only `name`:
   ```
   name=shared-data-disk,mode=ro,auto-delete=no
   ```

**Common keys** (all modes): `device-name`, `mode` (`ro` | `rw`, default `rw`), `interface` (`SCSI` | `NVME`), `auto-delete` (`yes` | `no`, default `yes`).

Short names for `source-snapshot`, `type`, and `name` are automatically qualified to the instance's project and zone. For cross-project resources, use a relative path (e.g. `projects/{project}/global/snapshots/{name}`). Mounting disks inside the VM must be done via the startup script.

### IAM

| Field | Description |
|-------|-------------|
| **Service Account E-mail** | GCP service account attached to the VM, controlling what GCP APIs it can access from the metadata server. Scoped to `https://www.googleapis.com/auth/cloud-platform`. |

## Configuration Reference

Complete reference of all fields with their CasC keys, types, and defaults.

### Cloud Configuration

| UI Field | CasC Key | Type | Default | Description |
|----------|----------|------|---------|-------------|
| Name | `cloudName` | String | *required* | Display name for this cloud |
| Project ID | `projectId` | String | *required* | GCP project ID |
| Instance Cap | `instanceCapStr` | String | - | Max concurrent VMs across all configurations |
| Service Account Credentials | `credentialsId` | String | *required* | Jenkins credential ID for the GCP service account |
| No delay provisioning | `noDelayProvisioning` | Boolean | `false` | Provision immediately without waiting for load estimation |

### Instance Configuration -- General

| UI Field | CasC Key | Type | Default |
|----------|----------|------|---------|
| Name Prefix | `namePrefix` | String | *required* |
| Description | `description` | String | *required* |
| Node Retention Time (minutes) | `retentionTimeMinutesStr` | String | `6` |
| Usage | `mode` | `NORMAL` / `EXCLUSIVE` | `NORMAL` |
| Labels | `labelString` | String | `""` |
| Number of Executors | `numExecutorsStr` | String | `1` |
| Terminate idle agents during shutdown | `terminateIdleDuringShutdown` | Boolean | `false` |
| Minimum Number of Instances | `minimumNumberOfInstances` | Integer | `0` |
| Minimum Number of Spare Instances | `minimumNumberOfSpareInstances` | Integer | `0` |
| Time Range | `minimumNumberOfInstancesTimeRangeConfig` | Object | *null* |

### Instance Configuration -- Launch

| UI Field | CasC Key | Type | Default |
|----------|----------|------|---------|
| Launch Timeout (seconds) | `launchTimeoutSecondsStr` | String | `300` |
| Use Internal IP | `useInternalAddress` | Boolean | `false` |
| Ignore Jenkins Proxy | `ignoreProxy` | Boolean | `false` |
| Run as user | `runAsUser` | String | `jenkins` |
| Custom SSH Private Key | `sshConfiguration` | Object | *null* |
| Remote Location | `remoteFs` | String | `""` |
| Java Path | `javaExecPath` | String | `java` |
| Windows | `windowsConfiguration` | Object | *null* |

### Instance Configuration -- One-Shot

| UI Field | CasC Key | Type | Default |
|----------|----------|------|---------|
| Enabled | `oneShot` | Boolean | `false` |
| Create snapshot | `createSnapshot` | Boolean | `false` |

### Instance Configuration -- Location

| UI Field | CasC Key | Type | Default |
|----------|----------|------|---------|
| Region | `region` | String | *required* |
| Zone | `zone` | String | *required* |

### Instance Configuration -- Machine (Advanced)

| UI Field | CasC Key | Type | Default |
|----------|----------|------|---------|
| Template | `template` | String | `""` |
| Provisioning Type | `provisioningType` | Object | `standard` (maxRunDurationSeconds: 0) |
| Machine Type | `machineType` | String | *required* |
| Minimum CPU Platform | `minCpuPlatform` | String | `""` |
| Startup script | `startupScript` | String | `""` |
| Linux exit reporter | `startupScriptExitReporterLinux` | String | curl script |
| Windows exit reporter | `startupScriptExitReporterWindows` | String | Invoke-RestMethod script |
| Custom metadata | `customMetadata` | List | `[]` |
| GPUs | `acceleratorConfiguration` | Object | *null* |

### Instance Configuration -- Networking

| UI Field | CasC Key | Type | Default |
|----------|----------|------|---------|
| Network Configuration | `networkConfiguration` | Object | `autofilled` (default/default) |
| Network tags | `networkTags` | String | `""` |
| IP stack type | `networkInterfaceIpStackMode` | Object | `singleStack` |
| External IPv4 Address | (inside singleStack/dualStack) `externalIPV4Address` | Boolean | `true` |

### Instance Configuration -- Boot Disk

| UI Field | CasC Key | Type | Default |
|----------|----------|------|---------|
| Image project | `bootDiskSourceImageProject` | String | current project |
| Image name | `bootDiskSourceImageName` | String | *required* |
| Disk Type | `bootDiskType` | String | *required* |
| Size (GB) | `bootDiskSizeGbStr` | String | `10` |
| Delete on termination | `bootDiskAutoDelete` | Boolean | `true` |

### Instance Configuration -- Disk Mapping

| UI Field | CasC Key | Type | Default |
|----------|----------|------|---------|
| Disk Mapping | `diskMapping` | String | `""` |

### Instance Configuration -- IAM

| UI Field | CasC Key | Type | Default |
|----------|----------|------|---------|
| Service Account E-mail | `serviceAccountEmail` | String | `""` |

## Configuration as Code (CasC)

This plugin fully supports [Jenkins Configuration as Code](https://jenkins.io/projects/jcasc/). Below are annotated examples for common scenarios. For machine-verified configurations used in the plugin's integration tests, see the YAML files in [`src/test/resources/.../integration/`](./src/test/resources/com/google/jenkins/plugins/computeengine/integration/).

### Basic Linux Agent

```yaml
jenkins:
  clouds:
    - computeEngine:
        cloudName: gce-prod
        projectId: my-gcp-project
        instanceCapStr: "10"
        credentialsId: my-gcp-project
        configurations:
          - namePrefix: jenkins-agent
            description: Linux build agent
            mode: EXCLUSIVE
            labelString: linux gce
            numExecutorsStr: "1"
            runAsUser: jenkins
            javaExecPath: java
            retentionTimeMinutesStr: "6"
            launchTimeoutSecondsStr: "300"
            # Location
            region: us-central1
            zone: us-central1-a
            # Machine
            machineType: n1-standard-2
            # Networking -- uses project default network
            networkConfiguration:
              autofilled:
                network: default
                subnetwork: default
            networkTags: jenkins-agent ssh
            networkInterfaceIpStackMode:
              singleStack:
                externalIPV4Address: true
            # Boot disk
            bootDiskSourceImageProject: ubuntu-os-cloud
            bootDiskSourceImageName: ubuntu-2404-noble-amd64-v20260401
            bootDiskType: pd-ssd
            bootDiskSizeGbStr: "50"
            bootDiskAutoDelete: true
            # IAM
            serviceAccountEmail: jenkins-agent@my-gcp-project.iam.gserviceaccount.com

credentials:
  system:
    domainCredentials:
      - credentials:
          - googleRobotPrivateKeyCredentials:
              id: my-gcp-project
              projectId: my-gcp-project
              serviceAccountConfig:
                jsonServiceAccountConfig:
                  secretJsonKey: "{base64-encoded-json-key}"
```

### Windows Agent

```yaml
jenkins:
  clouds:
    - computeEngine:
        cloudName: gce-windows
        projectId: my-gcp-project
        instanceCapStr: "5"
        credentialsId: my-gcp-project
        configurations:
          - namePrefix: win-agent
            description: Windows build agent
            mode: EXCLUSIVE
            labelString: windows gce
            numExecutorsStr: "1"
            runAsUser: jenkins
            oneShot: true
            # Windows requires password or SSH key credentials
            windowsConfiguration:
              passwordCredentialsId: windows-password
              privateKeyCredentialsId: ""
            # Startup script to configure SSH public key auth
            startupScript: |
              Stop-Service sshd
              $PublicKey = "jenkins:<your-public-key>"
              Set-Content -Path $env:PROGRAMDATA\ssh\administrators_authorized_keys -Value $PublicKey
              icacls $env:PROGRAMDATA\ssh\administrators_authorized_keys /inheritance:r
              icacls $env:PROGRAMDATA\ssh\administrators_authorized_keys /grant SYSTEM:`(F`)
              icacls $env:PROGRAMDATA\ssh\administrators_authorized_keys /grant BUILTIN\Administrators:`(F`)
              Restart-Service sshd
            region: us-central1
            zone: us-central1-a
            machineType: n1-standard-2
            networkConfiguration:
              autofilled:
                network: default
                subnetwork: default
            networkTags: jenkins-agent ssh
            networkInterfaceIpStackMode:
              singleStack:
                externalIPV4Address: true
            bootDiskSourceImageProject: windows-cloud
            bootDiskSourceImageName: windows-server-2022-dc-v20260401
            bootDiskType: pd-ssd
            bootDiskSizeGbStr: "50"
            bootDiskAutoDelete: true

credentials:
  system:
    domainCredentials:
      - credentials:
          - usernamePassword:
              scope: SYSTEM
              id: windows-password
              username: jenkins
              password: "{your-password}"
```

### Spot VM with Max Run Duration

```yaml
# Use Spot VMs for cost savings with a 3-hour safety net
configurations:
  - namePrefix: spot-agent
    description: Spot build agent
    labelString: spot gce
    provisioningType:
      spotVm:
        maxRunDurationSeconds: 10800    # 3 hours -- GCP deletes the VM after this
    machineType: n1-standard-4
    # ... remaining fields same as basic example
```

### Minimum Instances with Business-Hours Scheduling

```yaml
# Keep 3 agents running during business hours, with 1 always idle
configurations:
  - namePrefix: jenkins-agent
    description: Scaled build agent
    minimumNumberOfInstances: 3
    minimumNumberOfSpareInstances: 1
    minimumNumberOfInstancesTimeRangeConfig:
      activeFrom: "09:00"
      activeTo: "17:00"
      monday: true
      tuesday: true
      wednesday: true
      thursday: true
      friday: true
      saturday: false
      sunday: false
    # ... remaining fields same as basic example
```

How minimum instance scaling works with the above configuration:

- build#1 starts: agent-1 busy, agent-2 idle, agent-3 idle
- build#2 starts: agent-1 busy, agent-2 busy, agent-3 idle
- build#3 starts: agent-1 busy, agent-2 busy, agent-3 busy, agent-4 launched (to maintain 1 spare)

Outside the configured time range, the minimum is not enforced and idle agents are terminated normally.

### Custom Metadata

```yaml
configurations:
  - namePrefix: jenkins-agent
    description: Agent with custom metadata
    customMetadata:
      - key: environment
        value: production
      - key: setup-script
        value: |
          line1
          line2
          line3
    # ... remaining fields same as basic example
```

Reserved keys that cannot be used: `ssh-keys`, `startup-script`, `windows-startup-script-ps1`, `enable-guest-attributes`.

### Startup Script with Exit Reporter

```yaml
# Controller waits for the startup script to complete before launching the agent
configurations:
  - namePrefix: jenkins-agent
    description: Agent with startup script
    startupScript: |
      #!/bin/bash
      apt-get update && apt-get install -y build-essential
      echo "Setup complete"
    # Default exit reporter uses curl -- override only if curl is not available
    startupScriptExitReporterLinux: |
      curl -s -X PUT -H "Metadata-Flavor: Google" \
        "http://metadata.google.internal/computeMetadata/v1/instance/guest-attributes/startup-script/status" \
        -d "$1"
    # ... remaining fields same as basic example
```

Clear the exit reporter field to disable waiting (the agent will connect as soon as SSH is available, without waiting for the startup script).

### Disk Mapping

```yaml
configurations:
  - namePrefix: jenkins-agent
    description: Agent with additional disks
    diskMapping: |
      source-snapshot=build-cache-snap,size=100,type=pd-ssd,auto-delete=yes
      size=200,type=pd-ssd,name=workspace,auto-delete=yes
    startupScript: |
      #!/bin/bash
      # Mount the additional disks
      mkfs.ext4 -F /dev/disk/by-id/google-workspace
      mkdir -p /mnt/workspace && mount /dev/disk/by-id/google-workspace /mnt/workspace
    # ... remaining fields same as basic example
```

### Shared VPC

```yaml
configurations:
  - namePrefix: jenkins-agent
    description: Agent in shared VPC
    networkConfiguration:
      sharedVpc:
        projectId: shared-vpc-host-project
        region: us-central1
        subnetworkShortName: jenkins-subnet
    # ... remaining fields same as basic example
```

## Windows Agents

Windows VMs require additional setup compared to Linux.

**VM image prerequisites:**
- OpenSSH Server installed and running
- Java installed and on PATH (or configured via Java Path)
- An administrative user with a known username and password

**Configuration:**

1. Enable the **Windows** checkbox in the Launch Configuration section.
2. Provide either a **Password Credential** (username/password) or a **Private Key Credential** (SSH key) for authentication.
3. Set **Run as user** to match the Windows admin username.

![Windows configuration](docs/images/windowsconfig.png)

![Credential selection](docs/images/credentialsdropdown.png)

For password-based authentication, create a Username/Password credential in Jenkins:

![Username and password credential](docs/images/usernamepassword.png)

For SSH key-based authentication:

![SSH credential](docs/images/sshcred.png)

**Startup script to install prerequisites automatically:**

If your image does not have the prerequisites pre-installed, you can use a startup script. Pre-installing is recommended for faster agent startup.

```powershell
Set-ExecutionPolicy Bypass -Scope Process -Force
Invoke-Expression ((New-Object System.Net.WebClient).DownloadString('https://chocolatey.org/install.ps1'))
RefreshEnv.cmd
choco install -y openssh -params '"/SSHServerFeature"'
choco install -y jre8

$username = "jenkins"
$password = ConvertTo-SecureString "P@ssword2" -AsPlainText -Force
$cred = New-Object System.Management.Automation.PSCredential -ArgumentList $username, $password
Start-Process cmd /c -WindowStyle Hidden -Credential $cred -ErrorAction SilentlyContinue
```

See the [Windows VM Instances](https://cloud.google.com/compute/docs/instances/windows) documentation for more details.

## Advanced Features

### Provisioning Types: Standard, Spot, and Preemptible

GCP offers three VM provisioning options with different availability, pricing, and termination policies:

| Type | Pricing | Termination | Max Run Duration |
|------|---------|-------------|------------------|
| **Standard** | Full price, guaranteed by GCP SLAs | Only when you delete it (or max run duration expires) | Supported |
| **Spot** | Same as Preemptible (up to 60-91% discount) | May be terminated if GCP needs resources; not subject to the 24-hour limit | Supported |
| **Preemptible** | Same as Spot | May be terminated if GCP needs resources; always terminated after 24 hours | Not supported |

Use the **Max Run Duration Seconds** field (Standard and Spot only) to set a hard time limit after which GCP automatically deletes the VM. This is useful as a safety net to prevent runaway instances.

- [Spot VMs documentation](https://cloud.google.com/compute/docs/instances/spot)
- [Preemptible VMs documentation](https://cloud.google.com/compute/docs/instances/preemptible)

CasC syntax:

```yaml
# Standard (default)
provisioningType:
  standard:
    maxRunDurationSeconds: 0    # 0 = no limit

# Spot with 3-hour limit
provisioningType:
  spotVm:
    maxRunDurationSeconds: 10800

# Preemptible
provisioningType:
  preemptibleVm: {}
```

> **Note:** The legacy CasC field `preemptible: true` still works but is deprecated. Use `provisioningType: preemptibleVm` instead.

### Minimum Instance Scaling

Keep a pool of agents running proactively so builds don't wait for provisioning.

**`minimumNumberOfInstances`** -- the total number of agents (busy + idle) to maintain. When the retention strategy would terminate an idle agent, termination is blocked if it would drop below this minimum.

**`minimumNumberOfSpareInstances`** -- the number of idle agents to keep ready. When all spare agents become busy, new ones are launched.

These two settings combine: set a total minimum of 3 and a spare minimum of 1, and you get 3 agents at rest with 1 always idle. As builds consume agents, new ones are launched to maintain the spare count, but the total can grow beyond 3 as demand requires.

**Time-range scheduling** restricts when minimums are enforced. Outside the window, idle agents are terminated normally. Use this for business-hours-only pools.

### Startup Script Exit Reporter

When a startup script is configured, the plugin can wait for it to complete before connecting the Jenkins agent. This prevents builds from starting on an agent that is still running its initialization.

**How it works:**

1. The plugin wraps your startup script with a trap (Linux) or try/finally (Windows) block.
2. When the script finishes, the exit reporter sends the exit code to a GCE guest attribute via the instance metadata server.
3. The controller polls the guest attribute and only launches the agent after the script reports success.

**Linux** -- the default reporter uses `curl`:

```bash
curl -s -X PUT -H "Metadata-Flavor: Google" \
  "http://metadata.google.internal/computeMetadata/v1/instance/guest-attributes/startup-script/status" \
  -d "$1"
```

If `curl` is not available, alternatives:

**bash `/dev/tcp`** (no external tools):
```bash
exec 3<>/dev/tcp/metadata.google.internal/80
printf "PUT /computeMetadata/v1/instance/guest-attributes/startup-script/status HTTP/1.0\r\nHost: metadata.google.internal\r\nMetadata-Flavor: Google\r\nContent-Length: ${#1}\r\n\r\n$1" >&3
exec 3>&-
```

**wget**:
```bash
wget -q --method=PUT --header="Metadata-Flavor: Google" --body-data="$1" \
  "http://metadata.google.internal/computeMetadata/v1/instance/guest-attributes/startup-script/status" -O /dev/null
```

**python3**:
```bash
python3 -c "import urllib.request; urllib.request.urlopen(urllib.request.Request( \
  'http://metadata.google.internal/computeMetadata/v1/instance/guest-attributes/startup-script/status', \
  data=b'$1', headers={'Metadata-Flavor':'Google'}, method='PUT'))"
```

**Windows** -- the default reporter uses `Invoke-RestMethod`:

```powershell
Invoke-RestMethod -Method PUT -Body "$($args[0])" `
  -Headers @{'Metadata-Flavor'='Google'} `
  -Uri "http://metadata.google.internal/computeMetadata/v1/instance/guest-attributes/startup-script/status"
```

**To disable waiting**, clear the exit reporter field. The agent will connect as soon as SSH is available.

### Disk Mapping

Attach additional disks beyond the boot disk. See the [Disk Mapping](#disk-mapping) section under Instance Configuration for the full syntax reference.

Key points:
- Additional disks are attached but not mounted. Use the startup script to format and mount them.
- Three modes: create from snapshot, create blank disk, or attach an existing disk.
- Short names are auto-qualified to the instance's project and zone.
- Cross-project resources require a relative path or full URI.

### Custom Metadata

Attach arbitrary key-value pairs as GCE instance metadata. Accessible from within the VM via the [metadata server](https://cloud.google.com/compute/docs/metadata/overview).

The following keys are reserved and cannot be overridden: `ssh-keys`, `startup-script`, `windows-startup-script-ps1`, `enable-guest-attributes`.

Values can be multiline (use YAML block scalars in CasC).

### One-Shot Instances and Snapshots

One-shot mode deletes the agent after a single build completes. This guarantees a clean environment for every build.

When **Create snapshot** is also enabled, the plugin takes a disk snapshot before terminating the instance. This is useful for debugging failed builds -- you can inspect the disk state after the fact.

Constraints:
- Number of executors must be 1 when one-shot is enabled.
- Create snapshot is only available when one-shot is enabled.

### Instance Templates

Instead of configuring machine details (machine type, disks, networking, etc.) in the plugin, you can reference a GCE [instance template](https://cloud.google.com/compute/docs/instance-templates/). The template defines the VM configuration, and the plugin only manages agent lifecycle and SSH keys.

When a template is selected, **all advanced configuration settings are ignored** -- the template takes full control of the VM specification.

### Custom Java Path

If Java is not on the system PATH, set the **Java Path** field to the full path (e.g. `/usr/lib/jvm/java-21/bin/java`). This is useful for custom images where Java is installed in a non-standard location.

## Provisioning Behavior

### No Delay Provisioning

By default, Jenkins estimates load to avoid over-provisioning cloud nodes. This plugin uses its own provisioning strategy that launches a new VM as soon as demand is detected, without waiting for the estimation cycle. In the worst case, this results in some extra VMs that are quickly terminated.

**To disable** this aggressive strategy:

- **System property**: set `com.google.jenkins.plugins.computeengine.disableNoDelayProvisioning=true`
- **UI**: uncheck the **No delay provisioning** checkbox in the cloud configuration.

### Instance Cap

The **Instance Cap** on the cloud limits total concurrent VMs across all instance configurations. If the cap is reached, no new agents are provisioned until existing ones terminate.

### Retention and Termination

Idle agents are terminated after the configured **Node Retention Time** (default: 6 minutes). The **Launch Timeout** (default: 300 seconds) controls how long the plugin waits for a new agent to connect before giving up.

If **Terminate idle agents during shutdown** is enabled, idle agents are deleted when the Jenkins controller stops.

## Troubleshooting

### Enable Debug Logging

Add a log recorder in **Manage Jenkins > System Log > Add new log recorder**:

| Logger | Level |
|--------|-------|
| `com.google.jenkins.plugins.computeengine` | `ALL` |

### Instance Not Launching

1. **Credentials**: verify the service account key is valid and the credential is correctly selected in the cloud configuration.
2. **IAM roles**: the service account needs `compute.instanceAdmin`, `compute.networkAdmin`, and `iam.serviceAccountUser`.
3. **API quota**: check [GCP quotas](https://console.cloud.google.com/iam-admin/quotas) for CPU, IP addresses, and SSD storage in your region.
4. **Firewall**: ensure a firewall rule allows TCP port 22 from the Jenkins controller to agent VMs. Apply the rule via network tags.
5. **Boot image**: the image must have Java installed at the configured path.

### Agent Connects but Goes Offline

- **Retention time too low**: increase from the 6-minute default if builds take time to queue.
- **Startup script hanging**: if a startup script runs but never finishes, the exit reporter will not fire and the agent will time out. Check the script logic and test it on a standalone VM.
- **Boot disk too small**: if the disk fills during startup, SSH may fail. Increase the boot disk size.

### Windows Agent Not Connecting

- **OpenSSH not installed**: the Windows image must have OpenSSH Server installed and the `sshd` service running.
- **Password credentials**: verify the username/password credential ID matches what is configured.
- **Firewall rules**: same as Linux -- TCP port 22 from controller to agent.

### Startup Script Not Completing

- **Exit reporter cleared**: if the exit reporter field is empty, the controller does not wait for the script. The agent connects as soon as SSH is available, which may be before the script finishes.
- **Guest attributes not enabled**: the plugin automatically sets `enable-guest-attributes=TRUE` in instance metadata. If you are using an instance template, ensure this metadata key is set.
- **curl not available**: on minimal images, `curl` may not be installed. Use one of the alternative reporters (bash `/dev/tcp`, `wget`, or `python3`).

### Spot/Preemptible VM Terminated Mid-Build

This is expected behavior. Spot and Preemptible VMs can be reclaimed by GCP at any time. Ensure your builds are idempotent and can be retried. Use the **Max Run Duration Seconds** field on Spot VMs to set a predictable upper bound.

### Stale Instances

The plugin runs a periodic cleanup (`CleanLostNodesWork`) that terminates VMs that no longer have a corresponding Jenkins node. If you see orphaned instances, check the Jenkins system log for cleanup-related messages.

## Feature Requests and Bug Reports

Please file feature requests and bug reports under [issues](https://github.com/jenkinsci/google-compute-engine-plugin/issues).

## Community

The GCP Jenkins community uses the **#gcp-jenkins** Slack channel on
[https://googlecloud-community.slack.com](https://googlecloud-community.slack.com)
to ask questions and share feedback. Invitation link available here:
[gcp-slack](https://cloud.google.com/community#home-support).

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for development setup, testing, and contribution guidelines.

## License

See [LICENSE](LICENSE)
