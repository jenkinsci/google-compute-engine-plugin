<!--
 Copyright 2020 Google LLC

 Licensed under the Apache License, Version 2.0 (the "License"); you may not use this
file except in
 compliance with the License. You may obtain a copy of the License at

        https://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software distributed under
the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY 
KIND, either express or implied. See the License for the specific language governing 
permissions and limitations under the License.
-->
# Google Compute Engine Plugin
The Google Compute Engine (GCE) Plugin allows you to use GCE virtual machines (VMs) with Jenkins to execute build tasks. GCE VMs provision quickly, are destroyed by Jenkins when idle, and offer Preemptible VMs that run at a much lower price than regular VMs.


View Google Compute Engine on the plugin site for more information.

## Usage
### IAM Credentials
1. Create a service account using the Google Cloud SDK.

   ```
    gcloud iam service-accounts create jenkins-gce
   ```

2. Add the instanceAdmin, networkAdmin and serviceAccountUser roles to the service account.

   ```
   export PROJECT=$(gcloud info --format='value(config.project)') 
   export SA_EMAIL=$(gcloud iam service-accounts list --filter="name:jenkins-gce" \
    --format='value(email)') 
   gcloud projects add-iam-policy-binding --member serviceAccount:$SA_EMAIL \
    --role roles/compute.instanceAdmin $PROJECT
   gcloud projects add-iam-policy-binding --member serviceAccount:$SA_EMAIL \
    --role roles/compute.networkAdmin $PROJECT
   gcloud projects add-iam-policy-binding --member serviceAccount:$SA_EMAIL \
    --role roles/iam.serviceAccountUser $PROJECT
   ```

3. Download a JSON Service Account key for your newly created service account. Take note
   of where the file was created, you will upload it to Jenkins in a subsequent step.

   ```
   gcloud iam service-accounts keys create --iam-account $SA_EMAIL jenkins-gce.json
   ```
4. In Jenkins, click the Credentials button on the left side of the screen. Then click 
   System.
5. Click Global credentials then **Add credentials** on the left.
6. In the Kind dropdown, select Google Service Account from private key.
7. Enter your project name then select your JSON key that was created in the preceding
   steps.
8. Click OK.

### Google Compute Engine configuration
Each GCE configuration can point to a different GCP project. Follow the steps below to create one.

 1. Go to Manage Jenkins, then Configure System
 2. At the bottom of the page there will be a button labeled Add a new cloud, click the 
    button then click Google Compute Engine.
 3. Enter a name for your cloud configuration and the Project ID that you will be using
    to deploy the instances.
 4. In the Service Account Credentials dropdown, select the credentials that you uploaded
    earlier.

### Instance configurations
An instance configuration allows you to map an instance to a set of labels that Jenkins
 can use to determine when a job requires a particular type of instance. You can create 
 many instance configurations per GCE configuration.

1. Click on the Add button.
2. In the Name Prefix field, choose a prefix for the name of the instances that will be
   deployed.
3. Set a Description that identifies what this instance configuration will be used for.
4. Optionally, set a label in the Label field that will allow you to restrict jobs to  
   only run on this type of node.
5. Select a Region and Zone to define where instances will be launched.
6. Select the Machine Type for this instance configuration which defines the number of 
   cores and RAM that will be allocated.
7. Select the Network and Subnetwork that the instance will be deployed into.
8. For the Boot Disk configuration choose an image that has Java 8 installed and on its 
    default path.

Once complete you should be able to create jobs that restrict their builds to the label
 you selected. Instances will provision on demand. When no builds have been run for the
  configured Node Retention Time (default 6 minutes) the instances will be terminated.

### Advanced configurations
Instance configurations have many options that were not listed above. A few of the
 important ones are explained below.

* Preemptible - instances provisioned by Jenkins will be launched as Preemptible VMs
  these are up to 80% less expensive than normal VMs but can be terminated at any time.
  When using this setting, ensure that builds can be retried without impacting your
  workload.
* Disk Type and Size - dictates the performance of the filesystem that your agents are
 running on. Note that in GCE, larger disks get higher IOPS and throughput.
* Network tags - these tags will be applied to the instances provisioned by Jenkins.
 These should be set to allow the Jenkins master to access port 22 on the Jenkins agents.
  More info on firewall rules in GCE is available here.
* External IP - dictates whether the instance should receive an external routable IP
  address. In GCE, you will need to have either an external IP or a NAT gateway setup in 
  order to download anything from the internet.
* Startup Script - defines a set of commands that should be run before making the
 instance available for running your jobs. For more info, review the startup script docs.
* GPUs - attach 1 or more GPUs to the instance. For more info, visit the GCE GPU docs.
* Service Account E-mail - sets the service account that the instance will be able to
  access from metadata. For more info, review the service account documentation.


# No delay provisioning

By default Jenkins estimates load to avoid over-provisioning of cloud nodes.
This plugin will use its own provisioning strategy by default, with this strategy, a new node is created on GCP as soon as NodeProvisioner detects need for more agents.
In worst case scenarios, this will results in some extra nodes provisioned on GCP, which will be shortly terminated.

## How to configure

### Using a system property

If you want to turn off this Strategy globally then you can set a SystemProperty `com.google.jenkins.plugins.computeengine.disableNoDelayProvisioning=true`

### Using the the UI

Follow the steps below to configure it:

 1. Go to Manage Jenkins, then Configure System
 2. At the bottom of the page there will be a Cloud Section
 3. Select the cloud project and look for the `No delay provisioning` checkbox, and click on to enable it. 
