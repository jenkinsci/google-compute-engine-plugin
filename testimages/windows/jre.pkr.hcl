packer {
  required_plugins {
    googlecompute = {
      version = ">= 1.1.1"
      source  = "github.com/hashicorp/googlecompute"
    }
  }
}

variable "project" {
  type = string
}

variable "region" {
  type = string
}

variable "zone" {
  type = string
}

variable "agent_image" {
  type = string
}

variable "jenkins_password" {
  type      = string
  default   = "Agent007!"
  sensitive = true
}


source "googlecompute" "base" {
  project_id              = var.project
  zone                    = var.zone
  image_storage_locations = [var.region]
  source_image_project_id = ["windows-cloud"]
  source_image_family     = "windows-2022"
  image_name              = var.agent_image
  machine_type            = "e2-standard-4" # Windows provisioning is resource-heavy; smaller VMs significantly slow the build
  disk_size               = 50
  communicator            = "winrm"
  winrm_username          = "packer_user"
  winrm_insecure          = true
  winrm_use_ssl           = true
  metadata = {
    windows-startup-script-cmd = "winrm quickconfig -quiet & net user /add packer_user & net localgroup administrators packer_user /add & winrm set winrm/config/service/auth @{Basic=\"true\"}"
  }
}

build {
  sources = ["sources.googlecompute.base"]
  provisioner "powershell" {
    script            = "./install-java.ps1"
    elevated_user     = build.User
    elevated_password = build.Password
    environment_vars = [
      "AGENT_IMAGE=${var.agent_image}",
      "JENKINS_PASSWORD=${var.jenkins_password}"
    ]
  }
}
