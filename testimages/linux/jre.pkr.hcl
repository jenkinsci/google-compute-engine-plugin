# https://github.com/jenkinsci/google-compute-engine-plugin/pull/186#issuecomment-1664345279

packer {
  required_plugins {
    googlecompute = {
      version = ">= 1.1.1"
      source = "github.com/hashicorp/googlecompute"
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

source "googlecompute" "base" {
  project_id = var.project
  zone = var.zone
  image_storage_locations = [var.region]
  source_image_project_id = ["debian-cloud"]
  source_image_family = "debian-12"
  image_name = var.agent_image
  ssh_username = "jenkins"
}

build {
  sources = ["sources.googlecompute.base"]
  provisioner "shell" {
    script = "./install-java.sh"
    environment_vars = [
      "AGENT_IMAGE=${var.agent_image}"
    ]
  }
}