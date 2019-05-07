/*
 * Copyright 2018 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.jenkins.plugins.computeengine;

import static com.google.jenkins.plugins.computeengine.client.ComputeClient.nameFromSelfLink;

import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernameListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.google.api.services.compute.model.AcceleratorConfig;
import com.google.api.services.compute.model.AccessConfig;
import com.google.api.services.compute.model.AttachedDisk;
import com.google.api.services.compute.model.AttachedDiskInitializeParams;
import com.google.api.services.compute.model.DiskType;
import com.google.api.services.compute.model.Image;
import com.google.api.services.compute.model.Instance;
import com.google.api.services.compute.model.InstanceTemplate;
import com.google.api.services.compute.model.MachineType;
import com.google.api.services.compute.model.Metadata;
import com.google.api.services.compute.model.NetworkInterface;
import com.google.api.services.compute.model.Operation;
import com.google.api.services.compute.model.Region;
import com.google.api.services.compute.model.Scheduling;
import com.google.api.services.compute.model.ServiceAccount;
import com.google.api.services.compute.model.Tags;
import com.google.api.services.compute.model.Zone;
import com.google.common.base.Strings;
import com.google.jenkins.plugins.computeengine.client.ClientFactory;
import com.google.jenkins.plugins.computeengine.client.ComputeClient;
import hudson.Extension;
import hudson.RelativePath;
import hudson.Util;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.Node.Mode;
import hudson.model.TaskListener;
import hudson.model.labels.LabelAtom;
import hudson.security.ACL;
import hudson.slaves.CloudRetentionStrategy;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.text.RandomStringGenerator;
import org.jenkinsci.plugins.durabletask.executors.OnceRetentionStrategy;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

public class InstanceConfiguration implements Describable<InstanceConfiguration> {
  public static final Long DEFAULT_BOOT_DISK_SIZE_GB = 10L;
  public static final Integer DEFAULT_NUM_EXECUTORS = 1;
  public static final Integer DEFAULT_LAUNCH_TIMEOUT_SECONDS = 300;
  public static final Integer DEFAULT_RETENTION_TIME_MINUTES =
      (DEFAULT_LAUNCH_TIMEOUT_SECONDS / 60) + 1;
  public static final String DEFAULT_RUN_AS_USER = "jenkins";
  public static final String ERROR_NO_SUBNETS =
      "No subnetworks exist in the given network and region.";
  public static final String METADATA_LINUX_STARTUP_SCRIPT_KEY = "startup-script";
  public static final String METADATA_WINDOWS_STARTUP_SCRIPT_KEY = "windows-startup-script-ps1";
  public static final String NAT_TYPE = "ONE_TO_ONE_NAT";
  public static final String NAT_NAME = "External NAT";
  public static final List<String> KNOWN_IMAGE_PROJECTS =
      Collections.unmodifiableList(
          new ArrayList<String>() {
            {
              add("centos-cloud");
              add("coreos-cloud");
              add("cos-cloud");
              add("debian-cloud");
              add("rhel-cloud");
              add("suse-cloud");
              add("suse-sap-cloud");
              add("ubuntu-os-cloud");
              add("windows-cloud");
              add("windows-sql-cloud");
            }
          });
  private String description;
  private String namePrefix;
  private String region;
  private String zone;
  private String machineType;
  private String numExecutorsStr;
  private String startupScript;
  private boolean preemptible;
  private String minCpuPlatform;
  private String labels;
  private String runAsUser;
  private String bootDiskType;
  private boolean bootDiskAutoDelete;
  private String bootDiskSourceImageName;
  private String bootDiskSourceImageProject;
  private NetworkConfiguration networkConfiguration;
  private boolean externalAddress;
  private boolean useInternalAddress;
  private String networkTags;
  private String serviceAccountEmail;
  private Node.Mode mode;
  private AcceleratorConfiguration acceleratorConfiguration;
  private String retentionTimeMinutesStr;
  private String launchTimeoutSecondsStr;
  private String bootDiskSizeGbStr;
  private boolean oneShot;
  private String template;
  private boolean windows;
  private String windowsPasswordCredentialsId;
  private String windowsPrivateKeyCredentialsId;
  private Optional<WindowsConfiguration> windowsConfig;
  private boolean createSnapshot;
  private String remoteFs;
  private String javaExecPath;
  public Map<String, String> googleLabels;
  public Integer numExecutors;
  public Integer retentionTimeMinutes;
  public Integer launchTimeoutSeconds;
  public Long bootDiskSizeGb;
  public transient Set<LabelAtom> labelSet;
  protected transient ComputeEngineCloud cloud;

  @DataBoundConstructor
  public InstanceConfiguration(
      String labelString,
      boolean windows,
      String runAsUser,
      String windowsPasswordCredentialsId,
      String windowsPrivateKeyCredentialsId) {
    this.setLabels(labelString);
    this.setWindows(windows);
    this.setRunAsUser(runAsUser);
    this.setWindowsPasswordCredentialsId(windowsPasswordCredentialsId);
    this.setWindowsPrivateKeyCredentialsId(windowsPrivateKeyCredentialsId);
    this.setWindowsConfig(
        makeWindowsConfiguration(
            windows, runAsUser, windowsPasswordCredentialsId, windowsPrivateKeyCredentialsId));
    readResolve();
  }

  @DataBoundSetter
  /**
   * Sets the Java executable path for this instance.
   *
   * @param javaExecPath The Java executable path to be set.
   */
  public void setJavaExecPath(String javaExecPath) {
    this.javaExecPath = javaExecPath;
  }

  @DataBoundSetter
  public void setDescription(String description) {
    this.description = description;
  }

  @DataBoundSetter
  public void setNamePrefix(String namePrefix) {
    this.namePrefix = namePrefix;
  }

  @DataBoundSetter
  public void setRegion(String region) {
    this.region = region;
  }

  @DataBoundSetter
  public void setZone(String zone) {
    this.zone = zone;
  }

  @DataBoundSetter
  public void setMachineType(String machineType) {
    this.machineType = machineType;
  }

  @DataBoundSetter
  public void setNumExecutorsStr(String numExecutorsStr) {
    this.numExecutors = intOrDefault(numExecutorsStr, DEFAULT_NUM_EXECUTORS);
    this.numExecutorsStr = numExecutors.toString();
  }

  @DataBoundSetter
  public void setStartupScript(String startupScript) {
    this.startupScript = startupScript;
  }

  @DataBoundSetter
  public void setPreemptible(boolean preemptible) {
    this.preemptible = preemptible;
  }

  @DataBoundSetter
  public void setMinCpuPlatform(String minCpuPlatform) {
    this.minCpuPlatform = minCpuPlatform;
  }

  // Provided through constructor
  private void setLabels(String labels) {
    this.labels = Util.fixNull(labels);
  }

  // Provided through constructor
  public void setRunAsUser(String runAsUser) {
    this.runAsUser = runAsUser;
  }

  @DataBoundSetter
  public void setBootDiskType(String bootDiskType) {
    this.bootDiskType = bootDiskType;
  }

  @DataBoundSetter
  public void setBootDiskAutoDelete(boolean bootDiskAutoDelete) {
    this.bootDiskAutoDelete = bootDiskAutoDelete;
  }

  @DataBoundSetter
  public void setBootDiskSourceImageName(String bootDiskSourceImageName) {
    this.bootDiskSourceImageName = bootDiskSourceImageName;
  }

  @DataBoundSetter
  public void setBootDiskSourceImageProject(String bootDiskSourceImageProject) {
    this.bootDiskSourceImageProject = bootDiskSourceImageProject;
  }

  @DataBoundSetter
  public void setNetworkConfiguration(NetworkConfiguration networkConfiguration) {
    this.networkConfiguration = networkConfiguration;
  }

  @DataBoundSetter
  public void setExternalAddress(boolean externalAddress) {
    this.externalAddress = externalAddress;
  }

  @DataBoundSetter
  public void setUseInternalAddress(boolean useInternalAddress) {
    this.useInternalAddress = useInternalAddress;
  }

  @DataBoundSetter
  public void setNetworkTags(String networkTags) {
    this.networkTags = Util.fixNull(networkTags).trim();
  }

  @DataBoundSetter
  public void setServiceAccountEmail(String serviceAccountEmail) {
    this.serviceAccountEmail = serviceAccountEmail;
  }

  @DataBoundSetter
  public void setMode(Mode mode) {
    this.mode = mode;
  }

  @DataBoundSetter
  public void setAcceleratorConfiguration(AcceleratorConfiguration acceleratorConfiguration) {
    this.acceleratorConfiguration = acceleratorConfiguration;
  }

  @DataBoundSetter
  public void setRetentionTimeMinutesStr(String retentionTimeMinutesStr) {
    this.retentionTimeMinutes =
        intOrDefault(retentionTimeMinutesStr, DEFAULT_RETENTION_TIME_MINUTES);
    this.retentionTimeMinutesStr = this.retentionTimeMinutes.toString();
  }

  @DataBoundSetter
  public void setLaunchTimeoutSecondsStr(String launchTimeoutSecondsStr) {
    this.launchTimeoutSeconds =
        intOrDefault(launchTimeoutSecondsStr, DEFAULT_LAUNCH_TIMEOUT_SECONDS);
    this.launchTimeoutSecondsStr = this.launchTimeoutSeconds.toString();
  }

  @DataBoundSetter
  public void setBootDiskSizeGbStr(String bootDiskSizeGbStr) {
    this.bootDiskSizeGb = longOrDefault(bootDiskSizeGbStr, DEFAULT_BOOT_DISK_SIZE_GB);
    this.bootDiskSizeGbStr = this.bootDiskSizeGb.toString();
  }

  @DataBoundSetter
  public void setOneShot(boolean oneShot) {
    this.oneShot = oneShot;
    this.createSnapshot &= oneShot;
  }

  @DataBoundSetter
  public void setTemplate(String template) {
    this.template = template;
  }

  // Provided through constructor
  public void setWindows(boolean windows) {
    this.windows = windows;
  }

  // Provided through constructor
  public void setWindowsPasswordCredentialsId(String windowsPasswordCredentialsId) {
    this.windowsPasswordCredentialsId = windowsPasswordCredentialsId;
  }

  // Provided through constructor
  public void setWindowsPrivateKeyCredentialsId(String windowsPrivateKeyCredentialsId) {
    this.windowsPrivateKeyCredentialsId = windowsPrivateKeyCredentialsId;
  }

  // Generated in constructor
  public void setWindowsConfig(Optional<WindowsConfiguration> windowsConfig) {
    this.windowsConfig = windowsConfig;
  }

  @DataBoundSetter
  public void setCreateSnapshot(boolean createSnapshot) {
    this.createSnapshot = createSnapshot && this.oneShot;
  }

  @DataBoundSetter
  public void setRemoteFs(String remoteFs) {
    this.remoteFs = remoteFs;
  }

  public static Integer intOrDefault(String toParse, Integer defaultTo) {
    Integer toReturn;
    try {
      toReturn = Integer.parseInt(toParse);
    } catch (NumberFormatException nfe) {
      toReturn = defaultTo;
    }
    return toReturn;
  }

  public static Long longOrDefault(String toParse, Long defaultTo) {
    Long toReturn;
    try {
      toReturn = Long.parseLong(toParse);
    } catch (NumberFormatException nfe) {
      toReturn = defaultTo;
    }
    return toReturn;
  }

  private static boolean notNullOrEmpty(String s) {
    return s != null && !s.isEmpty();
  }

  private static String stripSelfLinkPrefix(String s) {
    if (s.contains("https://www.googleapis.com")) {
      return s.substring(s.indexOf("/projects/") + 1, s.length());
    }
    return s;
  }

  public String getDescription() {
    return description;
  }

  public String getNamePrefix() {
    return namePrefix;
  }

  public String getRegion() {
    return region;
  }

  public String getZone() {
    return zone;
  }

  public String getMachineType() {
    return machineType;
  }

  public String getNumExecutorsStr() {
    return numExecutorsStr;
  }

  public String getStartupScript() {
    return startupScript;
  }

  public boolean isPreemptible() {
    return preemptible;
  }

  public String getMinCpuPlatform() {
    return minCpuPlatform;
  }

  public String getRunAsUser() {
    return runAsUser;
  }

  public String getBootDiskType() {
    return bootDiskType;
  }

  public boolean isBootDiskAutoDelete() {
    return bootDiskAutoDelete;
  }

  public String getBootDiskSourceImageName() {
    return bootDiskSourceImageName;
  }

  public String getBootDiskSourceImageProject() {
    return bootDiskSourceImageProject;
  }

  public NetworkConfiguration getNetworkConfiguration() {
    return networkConfiguration;
  }

  public boolean isExternalAddress() {
    return externalAddress;
  }

  public boolean isUseInternalAddress() {
    return useInternalAddress;
  }

  public String getNetworkTags() {
    return networkTags;
  }

  public String getServiceAccountEmail() {
    return serviceAccountEmail;
  }

  public AcceleratorConfiguration getAcceleratorConfiguration() {
    return acceleratorConfiguration;
  }

  public String getRetentionTimeMinutesStr() {
    return retentionTimeMinutesStr;
  }

  public String getLaunchTimeoutSecondsStr() {
    return launchTimeoutSecondsStr;
  }

  public String getBootDiskSizeGbStr() {
    return bootDiskSizeGbStr;
  }

  public boolean isOneShot() {
    return oneShot;
  }

  public String getTemplate() {
    return template;
  }

  public boolean isWindows() {
    return windows;
  }

  public String getWindowsPasswordCredentialsId() {
    return windowsPasswordCredentialsId;
  }

  public String getWindowsPrivateKeyCredentialsId() {
    return windowsPrivateKeyCredentialsId;
  }

  public Optional<WindowsConfiguration> getWindowsConfig() {
    return windowsConfig;
  }

  public boolean isCreateSnapshot() {
    return createSnapshot;
  }

  public String getRemoteFs() {
    return remoteFs;
  }

  public Descriptor<InstanceConfiguration> getDescriptor() {
    return Jenkins.get().getDescriptor(getClass());
  }

  public String getLabelString() {
    return labels;
  }

  public Set<LabelAtom> getLabelSet() {
    return labelSet;
  }

  public Node.Mode getMode() {
    return mode;
  }

  public String getDisplayName() {
    return description;
  }

  public int getLaunchTimeoutMillis() {
    return launchTimeoutSeconds * 1000;
  }

  /** @return The Java executable path for this {@link InstanceConfiguration}. */
  public String getJavaExecPath() {
    return javaExecPath;
  }

  public void appendLabels(Map<String, String> labels) {
    if (googleLabels == null) {
      googleLabels = new HashMap<>();
    }
    googleLabels.putAll(labels);
  }

  public void appendLabel(String key, String value) {
    if (googleLabels == null) {
      googleLabels = new HashMap<>();
    }
    googleLabels.put(key, value);
  }

  public ComputeEngineInstance provision(TaskListener listener) throws IOException {
    PrintStream logger = listener.getLogger();
    try {
      Instance instance = instance();
      // TODO: JENKINS-55285
      Operation operation = cloud.getClient().insertInstance(cloud.projectId, template, instance);
      logger.println("Sent insert request");
      String targetRemoteFs = this.remoteFs;
      ComputeEngineComputerLauncher launcher = null;
      if (this.windows) {
        launcher =
            new ComputeEngineWindowsLauncher(
                cloud.getCloudName(), operation, this.useInternalAddress);
        if (targetRemoteFs == null || targetRemoteFs.isEmpty()) {
          targetRemoteFs = "C:\\";
        }
      } else {
        launcher =
            new ComputeEngineLinuxLauncher(
                cloud.getCloudName(), operation, this.useInternalAddress);
        if (targetRemoteFs == null || targetRemoteFs.isEmpty()) {
          targetRemoteFs = "/tmp";
        }
      }
      ComputeEngineInstance computeEngineInstance =
          new ComputeEngineInstance(
              cloud.name,
              instance.getName(),
              instance.getZone(),
              instance.getDescription(),
              runAsUser,
              targetRemoteFs,
              windowsConfig,
              createSnapshot,
              oneShot,
              numExecutors,
              mode,
              labels,
              launcher,
              (oneShot
                  ? new OnceRetentionStrategy(retentionTimeMinutes)
                  : new CloudRetentionStrategy(retentionTimeMinutes)),
              getLaunchTimeoutMillis(),
              javaExecPath);
      return computeEngineInstance;
    } catch (Descriptor.FormException fe) {
      logger.printf("Error provisioning instance: %s", fe.getMessage());
      return null;
    }
  }

  /** Initializes transient properties */
  protected Object readResolve() {
    Jenkins.get().checkPermission(Jenkins.RUN_SCRIPTS);

    labelSet = Label.parse(labels);
    return this;
  }

  public Instance instance() throws IOException {
    Instance instance = new Instance();
    instance.setName(uniqueName());
    instance.setDescription(description);
    instance.setZone(nameFromSelfLink(zone));

    if (StringUtils.isNotEmpty(template)) {
      InstanceTemplate instanceTemplate =
          cloud
              .getClient()
              .getTemplate(nameFromSelfLink(cloud.projectId), nameFromSelfLink(template));
      Map<String, String> mergedLabels = new HashMap<>(googleLabels);
      if (instanceTemplate.getProperties().getLabels() != null) {
        Map<String, String> templateLabels = instanceTemplate.getProperties().getLabels();
        mergedLabels.putAll(templateLabels);
      }
      instance.setLabels(mergedLabels);
    } else {
      instance.setLabels(googleLabels);
      instance.setMachineType(stripSelfLinkPrefix(machineType));
      instance.setMetadata(metadata());
      instance.setTags(tags());
      instance.setScheduling(scheduling());
      instance.setDisks(disks());
      instance.setGuestAccelerators(accelerators());
      instance.setNetworkInterfaces(networkInterfaces());
      instance.setServiceAccounts(serviceAccounts());

      // optional
      if (notNullOrEmpty(minCpuPlatform)) {
        instance.setMinCpuPlatform(minCpuPlatform);
      }
    }
    return instance;
  }

  private String uniqueName() {
    char[][] pairs = {{'a', 'z'}, {'0', '9'}};
    RandomStringGenerator generator =
        new RandomStringGenerator.Builder().withinRange(pairs).build();
    String suffix = generator.generate(6);

    String prefix = namePrefix;
    if (!prefix.endsWith(("-"))) {
      prefix += "-";
    }

    return prefix + suffix;
  }

  private Metadata metadata() {
    if (notNullOrEmpty(startupScript)) {
      Metadata metadata = new Metadata();
      List<Metadata.Items> items = new ArrayList<>();
      if (this.windows) {
        items.add(
            new Metadata.Items()
                .setKey(METADATA_WINDOWS_STARTUP_SCRIPT_KEY)
                .setValue(startupScript));
      } else {
        items.add(
            new Metadata.Items().setKey(METADATA_LINUX_STARTUP_SCRIPT_KEY).setValue(startupScript));
      }
      metadata.setItems(items);
      return metadata;
    }
    return null;
  }

  private Tags tags() {
    if (notNullOrEmpty(networkTags)) {
      Tags tags = new Tags();
      tags.setItems(Arrays.asList(networkTags.split(" ")));
      return tags;
    }
    return null;
  }

  private Scheduling scheduling() {
    Scheduling scheduling = new Scheduling();
    scheduling.setPreemptible(preemptible);
    return scheduling;
  }

  private List<AttachedDisk> disks() {
    AttachedDisk boot = new AttachedDisk();
    boot.setBoot(true);
    boot.setAutoDelete(bootDiskAutoDelete);
    boot.setInitializeParams(
        new AttachedDiskInitializeParams()
            .setDiskSizeGb(bootDiskSizeGb)
            .setDiskType(bootDiskType)
            .setSourceImage(bootDiskSourceImageName));

    List<AttachedDisk> disks = new ArrayList<>();
    disks.add(boot);
    return disks;
  }

  private List<AcceleratorConfig> accelerators() {
    if (acceleratorConfiguration != null
        && notNullOrEmpty(acceleratorConfiguration.gpuCount)
        && notNullOrEmpty(acceleratorConfiguration.gpuType)) {
      List<AcceleratorConfig> accelerators = new ArrayList<>();
      accelerators.add(
          new AcceleratorConfig()
              .setAcceleratorType(acceleratorConfiguration.gpuType)
              .setAcceleratorCount(acceleratorConfiguration.gpuCount()));
      return accelerators;
    }
    return null;
  }

  private List<NetworkInterface> networkInterfaces() {
    List<NetworkInterface> networkInterfaces = new ArrayList<>();
    List<AccessConfig> accessConfigs = new ArrayList<>();
    if (externalAddress) {
      accessConfigs.add(new AccessConfig().setType(NAT_TYPE).setName(NAT_NAME));
    }
    NetworkInterface nic = new NetworkInterface().setAccessConfigs(accessConfigs);

    // Don't include subnetwork name if using default
    if (!networkConfiguration.getSubnetwork().equals("default")) {
      nic.setSubnetwork(stripSelfLinkPrefix(networkConfiguration.getSubnetwork()));
    }

    networkInterfaces.add(nic);
    return networkInterfaces;
  }

  private List<ServiceAccount> serviceAccounts() {
    if (notNullOrEmpty(serviceAccountEmail)) {
      List<ServiceAccount> serviceAccounts = new ArrayList<>();
      serviceAccounts.add(
          new ServiceAccount()
              .setEmail(serviceAccountEmail)
              .setScopes(
                  Arrays.asList(new String[] {"https://www.googleapis.com/auth/cloud-platform"})));
      return serviceAccounts;
    } else {
      return null;
    }
  }

  @Extension
  public static final class DescriptorImpl extends Descriptor<InstanceConfiguration> {
    private static ComputeClient computeClient;

    public static void setComputeClient(ComputeClient client) {
      computeClient = client;
    }

    public static String defaultRetentionTimeMinutes() {
      return DEFAULT_RETENTION_TIME_MINUTES.toString();
    }

    public static String defaultLaunchTimeoutSeconds() {
      return DEFAULT_LAUNCH_TIMEOUT_SECONDS.toString();
    }

    public static String defaultBootDiskSizeGb() {
      return DEFAULT_BOOT_DISK_SIZE_GB.toString();
    }

    public static String defaultBootDiskAutoDelete() {
      return "true";
    }

    public static String defaultRunAsUser() {
      return DEFAULT_RUN_AS_USER.toString();
    }

    public static NetworkConfiguration defaultNetworkConfiguration() {
      return new AutofilledNetworkConfiguration();
    }

    private static ComputeClient computeClient(Jenkins context, String credentialsId)
        throws IOException {
      if (computeClient != null) {
        return computeClient;
      }
      ClientFactory clientFactory =
          new ClientFactory(context, new ArrayList<DomainRequirement>(), credentialsId);
      return clientFactory.compute();
    }

    @Override
    public String getHelpFile(String fieldName) {
      String p = super.getHelpFile(fieldName);
      if (p == null) {
        Descriptor d = Jenkins.get().getDescriptor(ComputeEngineInstance.class);
        if (d != null) p = d.getHelpFile(fieldName);
      }
      return p;
    }

    public List<NetworkConfiguration.NetworkConfigurationDescriptor>
        getNetworkConfigurationDescriptors() {
      List<NetworkConfiguration.NetworkConfigurationDescriptor> d =
          Jenkins.get().getDescriptorList(NetworkConfiguration.class);
      // No deprecated regions
      Iterator it = d.iterator();
      while (it.hasNext()) {
        NetworkConfiguration.NetworkConfigurationDescriptor o =
            (NetworkConfiguration.NetworkConfigurationDescriptor) it.next();
        if (o.clazz.getName().equals("NetworkConfiguration")) {
          it.remove();
        }
      }
      return d;
    }

    public FormValidation doCheckNetworkTags(@QueryParameter String value) {
      if (value == null || value.isEmpty()) {
        return FormValidation.ok();
      }

      String re = "[a-z]([-a-z0-9]*[a-z0-9])?";
      for (String tag : value.split(" ")) {
        if (!tag.matches(re)) {
          return FormValidation.error(
              "Tags must be space-delimited and each tag must match regex" + re);
        }
      }

      return FormValidation.ok();
    }

    public FormValidation doCheckNamePrefix(@QueryParameter String value) {
      if (value == null || value.isEmpty()) {
        return FormValidation.error("A prefix is required");
      }

      String re = "[a-z]([-a-z0-9]*[a-z0-9])?";
      if (!value.matches(re)) {
        return FormValidation.error("Prefix must match regex " + re);
      }

      Integer maxLen = 50;
      if (value.length() > maxLen) {
        return FormValidation.error("Maximum length is " + maxLen);
      }
      return FormValidation.ok();
    }

    public FormValidation doCheckDescription(@QueryParameter String value) {
      if (value == null || value.isEmpty()) {
        return FormValidation.error("A description is required");
      }
      return FormValidation.ok();
    }

    public ListBoxModel doFillRegionItems(
        @AncestorInPath Jenkins context,
        @QueryParameter("projectId") @RelativePath("..") final String projectId,
        @QueryParameter("credentialsId") @RelativePath("..") final String credentialsId) {
      ListBoxModel items = new ListBoxModel();
      items.add("");
      try {
        ComputeClient compute = computeClient(context, credentialsId);
        List<Region> regions = compute.getRegions(projectId);

        for (Region r : regions) {
          items.add(r.getName(), r.getSelfLink());
        }
        return items;
      } catch (IOException ioe) {
        items.clear();
        items.add("Error retrieving regions");
        return items;
      }
    }

    public ListBoxModel doFillTemplateItems(
        @AncestorInPath Jenkins context,
        @QueryParameter("projectId") @RelativePath("..") final String projectId,
        @QueryParameter("credentialsId") @RelativePath("..") final String credentialsId) {
      ListBoxModel items = new ListBoxModel();
      items.add("");
      try {
        ComputeClient compute = computeClient(context, credentialsId);
        List<InstanceTemplate> instanceTemplates = compute.getTemplates(projectId);

        for (InstanceTemplate instanceTemplate : instanceTemplates) {
          items.add(instanceTemplate.getName(), instanceTemplate.getSelfLink());
        }
        return items;
      } catch (IOException ioe) {
        items.clear();
        items.add("Error retrieving instanceTemplates");
        return items;
      }
    }

    public FormValidation doCheckRegion(@QueryParameter String value) {
      if (value.equals("")) {
        return FormValidation.error("Please select a region...");
      }
      return FormValidation.ok();
    }

    public ListBoxModel doFillZoneItems(
        @AncestorInPath Jenkins context,
        @QueryParameter("projectId") @RelativePath("..") final String projectId,
        @QueryParameter("region") final String region,
        @QueryParameter("credentialsId") @RelativePath("..") final String credentialsId) {
      ListBoxModel items = new ListBoxModel();
      items.add("");
      try {
        ComputeClient compute = computeClient(context, credentialsId);
        List<Zone> zones = compute.getZones(projectId, region);

        for (Zone z : zones) {
          items.add(z.getName(), z.getSelfLink());
        }
        return items;
      } catch (IOException ioe) {
        items.clear();
        items.add("Error retrieving zones");
        return items;
      } catch (IllegalArgumentException iae) {
        // TODO log
        return null;
      }
    }

    public FormValidation doCheckZone(@QueryParameter String value) {
      if (value.equals("")) {
        return FormValidation.error("Please select a zone...");
      }
      return FormValidation.ok();
    }

    public ListBoxModel doFillMachineTypeItems(
        @AncestorInPath Jenkins context,
        @QueryParameter("projectId") @RelativePath("..") final String projectId,
        @QueryParameter("zone") final String zone,
        @QueryParameter("credentialsId") @RelativePath("..") final String credentialsId) {
      ListBoxModel items = new ListBoxModel();
      items.add("");
      try {
        ComputeClient compute = computeClient(context, credentialsId);
        List<MachineType> machineTypes = compute.getMachineTypes(projectId, zone);

        for (MachineType m : machineTypes) {
          items.add(m.getName(), m.getSelfLink());
        }
        return items;
      } catch (IOException ioe) {
        items.clear();
        items.add("Error retrieving machine types");
        return items;
      } catch (IllegalArgumentException iae) {
        // TODO log
        return null;
      }
    }

    public FormValidation doCheckMachineType(@QueryParameter String value) {
      if (value.equals("")) {
        return FormValidation.error("Please select a machine type...");
      }
      return FormValidation.ok();
    }

    public ListBoxModel doFillMinCpuPlatformItems(
        @AncestorInPath Jenkins context,
        @QueryParameter("projectId") @RelativePath("..") final String projectId,
        @QueryParameter("zone") final String zone,
        @QueryParameter("credentialsId") @RelativePath("..") final String credentialsId) {
      ListBoxModel items = new ListBoxModel();
      items.add("");
      try {
        ComputeClient compute = computeClient(context, credentialsId);
        List<String> cpuPlatforms = compute.cpuPlatforms(projectId, zone);

        for (String cpuPlatform : cpuPlatforms) {
          items.add(cpuPlatform);
        }
        return items;
      } catch (IOException ioe) {
        items.clear();
        items.add("Error retrieving cpu Platforms");
        return items;
      } catch (IllegalArgumentException iae) {
        // TODO log
        return null;
      }
    }

    public ListBoxModel doFillBootDiskTypeItems(
        @AncestorInPath Jenkins context,
        @QueryParameter("projectId") @RelativePath("..") final String projectId,
        @QueryParameter("zone") String zone,
        @QueryParameter("credentialsId") @RelativePath("..") final String credentialsId) {
      ListBoxModel items = new ListBoxModel();
      try {
        ComputeClient compute = computeClient(context, credentialsId);
        List<DiskType> diskTypes = compute.getBootDiskTypes(projectId, zone);

        for (DiskType dt : diskTypes) {
          items.add(dt.getName(), dt.getSelfLink());
        }
        return items;
      } catch (IOException ioe) {
        items.clear();
        items.add("Error retrieving disk types");
        return items;
      } catch (IllegalArgumentException iae) {
        // TODO: log
        return null;
      }
    }

    public ListBoxModel doFillBootDiskSourceImageProjectItems(
        @AncestorInPath Jenkins context,
        @QueryParameter("projectId") @RelativePath("..") final String projectId) {
      ListBoxModel items = new ListBoxModel();
      items.add("");
      items.add(projectId);
      for (String v : KNOWN_IMAGE_PROJECTS) {
        items.add(v);
      }
      return items;
    }

    public FormValidation doCheckBootDiskSourceImageProject(@QueryParameter String value) {
      if (value.equals("")) {
        return FormValidation.warning("Please select source image project...");
      }
      return FormValidation.ok();
    }

    public ListBoxModel doFillBootDiskSourceImageNameItems(
        @AncestorInPath Jenkins context,
        @QueryParameter("bootDiskSourceImageProject") final String projectId,
        @QueryParameter("credentialsId") @RelativePath("..") final String credentialsId) {
      ListBoxModel items = new ListBoxModel();
      items.add("");
      try {
        ComputeClient compute = computeClient(context, credentialsId);
        List<Image> images = compute.getImages(projectId);

        for (Image i : images) {
          items.add(i.getName(), i.getSelfLink());
        }
      } catch (IOException ioe) {
        items.clear();
        items.add("Error retrieving images for project");
      } catch (IllegalArgumentException iae) {
        // TODO: log
        return null;
      }
      return items;
    }

    public FormValidation doCheckBootDiskSourceImageName(@QueryParameter String value) {
      if (value.equals("")) {
        return FormValidation.warning("Please select source image...");
      }
      return FormValidation.ok();
    }

    public FormValidation doCheckBootDiskSizeGbStr(
        @AncestorInPath Jenkins context,
        @QueryParameter String value,
        @QueryParameter("bootDiskSourceImageProject") final String projectId,
        @QueryParameter("bootDiskSourceImageName") final String imageName,
        @QueryParameter("credentialsId") @RelativePath("..") final String credentialsId) {
      if (Strings.isNullOrEmpty(credentialsId)
          || Strings.isNullOrEmpty(projectId)
          || Strings.isNullOrEmpty(imageName)) return FormValidation.ok();

      try {
        ComputeClient compute = computeClient(context, credentialsId);
        Image i = compute.getImage(nameFromSelfLink(projectId), nameFromSelfLink(imageName));
        if (i == null) return FormValidation.error("Could not find image " + imageName);
        Long bootDiskSizeGb = Long.parseLong(value);
        if (bootDiskSizeGb < i.getDiskSizeGb()) {
          return FormValidation.error(
              String.format(
                  "The disk image you have chosen requires a minimum of %dGB. Please increase boot disk size to accommodate.",
                  i.getDiskSizeGb()));
        }
      } catch (IOException ioe) {
        return FormValidation.error(ioe, "Error validating boot disk size");
      }
      return FormValidation.ok();
    }

    public FormValidation doCheckLabelString(
        @QueryParameter String value, @QueryParameter Node.Mode mode) {
      if (mode == Node.Mode.EXCLUSIVE && (value == null || value.trim().isEmpty())) {
        return FormValidation.warning(
            "You may want to assign labels to this node;"
                + " it's marked to only run jobs that are exclusively tied to itself or a label.");
      }
      return FormValidation.ok();
    }

    public ListBoxModel doFillWindowsPasswordCredentialsIdItems(
        @AncestorInPath Jenkins context, @QueryParameter String value) {
      if (context == null || !context.hasPermission(Item.CONFIGURE)) {
        return new StandardListBoxModel();
      }
      List<DomainRequirement> domainRequirements = new ArrayList<DomainRequirement>();
      return new StandardListBoxModel()
          .withEmptySelection()
          .withMatching(
              CredentialsMatchers.instanceOf(StandardUsernamePasswordCredentials.class),
              CredentialsProvider.lookupCredentials(
                  StandardUsernamePasswordCredentials.class,
                  context,
                  ACL.SYSTEM,
                  domainRequirements));
    }

    public ListBoxModel doFillWindowsPrivateKeyCredentialsIdItems(
        @AncestorInPath Jenkins context, @QueryParameter String value) {
      if (context == null || !context.hasPermission(Item.CONFIGURE)) {
        return new StandardUsernameListBoxModel();
      }
      List<DomainRequirement> domainRequirements = new ArrayList<DomainRequirement>();
      return new StandardUsernameListBoxModel()
          .withEmptySelection()
          .withMatching(
              CredentialsMatchers.instanceOf(BasicSSHUserPrivateKey.class),
              CredentialsProvider.lookupCredentials(
                  StandardUsernameCredentials.class, context, ACL.SYSTEM, domainRequirements));
    }

    public FormValidation doCheckWindowsPrivateKeyCredentialsId(
        @AncestorInPath Jenkins context,
        @QueryParameter String value,
        @QueryParameter("windows") boolean windows,
        @QueryParameter("windowsPasswordCredentialsId") String windowsPasswordCredentialsId) {
      if (windows
          && (Strings.isNullOrEmpty(value)
              && Strings.isNullOrEmpty(windowsPasswordCredentialsId))) {
        return FormValidation.error("A password or private key credential is required");
      }
      return FormValidation.ok();
    }

    public FormValidation doCheckCreateSnapshot(
        @AncestorInPath Jenkins context,
        @QueryParameter boolean value,
        @QueryParameter("oneShot") boolean oneShot) {
      if (!oneShot && value) {
        return FormValidation.error(Messages.InstanceConfiguration_SnapshotConfigError());
      }
      return FormValidation.ok();
    }
  }

  private static Optional<WindowsConfiguration> makeWindowsConfiguration(
      boolean windows,
      String runAsUser,
      String windowsPasswordCredentialsId,
      String windowsPrivateKeyCredentialsId) {
    if (windows) {
      return Optional.of(
          new WindowsConfiguration(
              runAsUser, windowsPasswordCredentialsId, windowsPrivateKeyCredentialsId));
    } else {
      return Optional.empty();
    }
  }

  // For use in Builder only
  private InstanceConfiguration() {}

  public static class Builder {

    private InstanceConfiguration instanceConfiguration;

    public Builder() {
      instanceConfiguration = new InstanceConfiguration();
    }

    public Builder description(String description) {
      instanceConfiguration.setDescription(description);
      return this;
    }

    public Builder namePrefix(String namePrefix) {
      instanceConfiguration.setNamePrefix(namePrefix);
      return this;
    }

    public Builder region(String region) {
      instanceConfiguration.setRegion(region);
      return this;
    }

    public Builder zone(String zone) {
      instanceConfiguration.setZone(zone);
      return this;
    }

    public Builder machineType(String machineType) {
      instanceConfiguration.setMachineType(machineType);
      return this;
    }

    public Builder numExecutorsStr(String numExecutorsStr) {
      instanceConfiguration.setNumExecutorsStr(numExecutorsStr);
      return this;
    }

    public Builder startupScript(String startupScript) {
      instanceConfiguration.setStartupScript(startupScript);
      return this;
    }

    public Builder preemptible(boolean preemptible) {
      instanceConfiguration.setPreemptible(preemptible);
      return this;
    }

    public Builder minCpuPlatform(String minCpuPlatform) {
      instanceConfiguration.setMinCpuPlatform(minCpuPlatform);
      return this;
    }

    public Builder labels(String labels) {
      instanceConfiguration.setLabels(labels);
      return this;
    }

    public Builder runAsUser(String runAsUser) {
      instanceConfiguration.setRunAsUser(runAsUser);
      return this;
    }

    public Builder bootDiskType(String bootDiskType) {
      instanceConfiguration.setBootDiskType(bootDiskType);
      return this;
    }

    public Builder bootDiskAutoDelete(boolean bootDiskAutoDelete) {
      instanceConfiguration.setBootDiskAutoDelete(bootDiskAutoDelete);
      return this;
    }

    public Builder bootDiskSourceImageName(String bootDiskSourceImageName) {
      instanceConfiguration.setBootDiskSourceImageName(bootDiskSourceImageName);
      return this;
    }

    public Builder bootDiskSourceImageProject(String bootDiskSourceImageProject) {
      instanceConfiguration.setBootDiskSourceImageProject(bootDiskSourceImageProject);
      return this;
    }

    public Builder networkConfiguration(NetworkConfiguration networkConfiguration) {
      instanceConfiguration.setNetworkConfiguration(networkConfiguration);
      return this;
    }

    public Builder externalAddress(boolean externalAddress) {
      instanceConfiguration.setExternalAddress(externalAddress);
      return this;
    }

    public Builder useInternalAddress(boolean useInternalAddress) {
      instanceConfiguration.setUseInternalAddress(useInternalAddress);
      return this;
    }

    public Builder networkTags(String networkTags) {
      instanceConfiguration.setNetworkTags(networkTags);
      return this;
    }

    public Builder serviceAccountEmail(String serviceAccountEmail) {
      instanceConfiguration.setServiceAccountEmail(serviceAccountEmail);
      return this;
    }

    public Builder mode(Node.Mode mode) {
      instanceConfiguration.setMode(mode);
      return this;
    }

    public Builder acceleratorConfiguration(AcceleratorConfiguration acceleratorConfiguration) {
      instanceConfiguration.setAcceleratorConfiguration(acceleratorConfiguration);
      return this;
    }

    public Builder retentionTimeMinutesStr(String retentionTimeMinutesStr) {
      instanceConfiguration.setRetentionTimeMinutesStr(retentionTimeMinutesStr);
      return this;
    }

    public Builder launchTimeoutSecondsStr(String launchTimeoutSecondsStr) {
      instanceConfiguration.setLaunchTimeoutSecondsStr(launchTimeoutSecondsStr);
      return this;
    }

    public Builder bootDiskSizeGbStr(String bootDiskSizeGbStr) {
      this.instanceConfiguration.setBootDiskSizeGbStr(bootDiskSizeGbStr);
      return this;
    }

    public Builder oneShot(boolean oneShot) {
      instanceConfiguration.setOneShot(oneShot);
      return this;
    }

    public Builder template(String template) {
      instanceConfiguration.setTemplate(template);
      return this;
    }

    public Builder windows(boolean windows) {
      instanceConfiguration.setWindows(windows);
      return this;
    }

    public Builder windowsPasswordCredentialsId(String windowsPasswordCredentialsId) {
      instanceConfiguration.setWindowsPasswordCredentialsId(windowsPasswordCredentialsId);
      return this;
    }

    public Builder windowsPrivateKeyCredentialsId(String windowsPrivateKeyCredentialsId) {
      instanceConfiguration.setWindowsPrivateKeyCredentialsId(windowsPrivateKeyCredentialsId);
      return this;
    }

    public Builder createSnapshot(boolean createSnapshot) {
      instanceConfiguration.setCreateSnapshot(createSnapshot);
      return this;
    }

    public Builder remoteFs(String remoteFs) {
      instanceConfiguration.setRemoteFs(remoteFs);
      return this;
    }

    public Builder javaExecPath(String javaExecPath) {
      instanceConfiguration.setJavaExecPath(javaExecPath);
      return this;
    }

    public InstanceConfiguration build() {
      instanceConfiguration.setWindowsConfig(
          makeWindowsConfiguration(
              instanceConfiguration.windows,
              instanceConfiguration.runAsUser,
              instanceConfiguration.windowsPasswordCredentialsId,
              instanceConfiguration.windowsPrivateKeyCredentialsId));
      instanceConfiguration.readResolve();
      return instanceConfiguration;
    }
  }
}
