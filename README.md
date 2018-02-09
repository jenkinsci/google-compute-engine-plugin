# Google Compute Engine Plugin (alpha)

This plugin allows Jenkins to dynamically provision agents in Google Compute Engine. After a configurable idle period, instances will be deleted and you will no longer be billed for their use.

## Installation

1. Download the plugin from [here](https://storage.googleapis.com/jenkins-graphite/google-compute-plugin-latest.hpi).
1. Go to **Manage Jenkins **then** Manage Plugins**. 
1. In the Plugin Manager, click the **Advanced** tab and then **Choose File **under the **Upload Plugin **section**.**
1. Choose the Jenkins plugin file downloaded in Step 1.
1. Click the **Upload** button.

## Usage

### IAM Credentials

1. Create a service account using the Google Cloud SDK.  
  
    gcloud iam service-accounts create jenkins-gce

1. Add the **instanceAdmin** and **serviceAccountUser** roles to the service account.  
  
    export PROJECT=$(gcloud info --format='value(config.project)')  
    export SA_EMAIL=$(gcloud iam service-accounts list --filter="name:jenkins-gce" --format='value(email)')  
    gcloud projects add-iam-policy-binding --member serviceAccount:$SA_EMAIL --role roles/compute.instanceAdmin --role roles/iam.serviceAccountUser $PROJECT

1. Download a JSON Service Account key for your newly created service account. Take note of where the file was created, you will upload it to Jenkins in a subsequent step.  
  
    gcloud iam service-accounts keys create --iam-account $SA_EMAIL jenkins-gce.json

1. In Jenkins, click the **Credentials** button on the left side of the screen. Then click **System**.
1. Click **Global credentials** then **Add credentials **on the left.
1. In the **Kind** dropdown, select **Google Service Account from private key**.
1. Enter your project name then select your JSON key that was created in the preceding steps.
1. Click **OK**.

### Google Compute Engine configuration

Each GCE configuration can point to a different GCP project. Follow the steps below to create one.

1. Go to **Manage Jenkins**, then **Configure System**
1. At the bottom of the page there will be a button labeled **Add a new cloud**, click the button then click **Google Compute Engine**.
1. Enter a name for your cloud configuration and the Project ID that you will be using to deploy the instances.
1. In the **Service Account Credentials** dropdown, select the credentials that you uploaded earlier.

### Instance configurations

An instance configuration allows you to map an instance to a set of labels that Jenkins can use to determine when a job requires a particular type of instance. You can create many instance configurations per GCE configuration.

1. Click on the **Add** button.
1. In the **Name Prefix** field, choose a prefix for the name of the instances that will be deployed.
1. Set a **Description** that identifies what this instance configuration will be used for.
1. Optionally, set a label in the **Label** field that will allow you to restrict jobs to only run on this type of node.
1. Select a **Region** and **Zone** to define where instances will be launched.
1. Select the **Machine Type** for this instance configuration which defines the number of cores and RAM that will be allocated.
1. Select the **Network** and **Subnetwork** that the instance will be deployed into.
1. For the **Boot Disk** configuration choose an image that has Java 8 installed and on its default path.

Once complete you should be able to create jobs that restrict their builds to the label you selected. Instances will provision on demand. When no builds have been run for the configured **Node Retention Time** (default 6 minutes) the instances will be terminated.

## Advanced configurations

Instance configurations have many options that were not listed above. A few of the important ones are explained below.

-  **Preemptible** - instances provisioned by Jenkins will be launched as [Preemptible VMs](https://cloud.google.com/preemptible-vms/) these are up to 80% less expensive than normal VMs but can be terminated at any time. When using this setting, ensure that builds can be retried without impacting your workload.
-  **Disk Type and Size** - dictates the performance of the filesystem that your agents are running on. Note that in GCE, [larger disks get higher IOPS and throughput](https://cloud.google.com/compute/docs/disks/performance#type_comparison).
-  **Network tags **- these tags will be applied to the instances provisioned by Jenkins. These should be set to allow the Jenkins master to access port 22 on the Jenkins agents. More info on [firewall rules in GCE is available here](https://cloud.google.com/vpc/docs/firewalls).
-  **External IP** - dictates whether the instance should receive an external routable IP address. In GCE, you will need to have either an external IP or a [NAT gateway](https://cloud.google.com/vpc/docs/special-configurations#multiple-natgateways) setup in order to download anything from the internet.
-  **Startup Script** - defines a set of commands that should be run before making the instance available for running your jobs. For more info, review the [startup script docs](https://cloud.google.com/compute/docs/startupscript).
-  **GPUs** - attach 1 or more GPUs to the instance. For more info, visit the [GCE GPU docs](https://cloud.google.com/compute/docs/gpus/).
-  **Service Account E-mail **- sets the service account that the instance will be able to access from metadata. For more info, review the [service account documentation](https://cloud.google.com/compute/docs/access/service-accounts).
