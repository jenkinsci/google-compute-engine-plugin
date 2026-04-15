#!/usr/bin/env bash
set -euxo pipefail

project=${GOOGLE_PROJECT_ID:-$(gcloud config get-value core/project)}
region=${GOOGLE_REGION:-$(gcloud config get-value compute/region)}
zone=${GOOGLE_ZONE:-$(gcloud config get-value compute/zone)}

current_dir=$(dirname "$0")
pushd $current_dir

agent_image="jenkins-gce-integration-test-windows-jre"
if [ "${1:-}" == "non-standard-java" ]; then
  agent_image="${agent_image}-non-standard-java"
  shift
fi

# Default password is 'Agent007!' if not provided
JENKINS_PASSWORD=${JENKINS_PASSWORD:-Agent007!}

case "${1:-}" in
  --recreate)
    if gcloud compute images describe $agent_image --project=$project 2>/dev/null; then
      gcloud compute images delete $agent_image --project=$project --quiet
    fi
    ;;
  --delete)
    if gcloud compute images describe $agent_image --project=$project 2>/dev/null; then
      gcloud compute images delete $agent_image --project=$project --quiet
      exit 0
    fi
    ;;
esac

if ! gcloud compute images describe $agent_image --project=$project 2>/dev/null; then
  packer init ./
  packer build \
    -var project=$project \
    -var region=$region \
    -var zone=$zone \
    -var agent_image=$agent_image \
    -var jenkins_password=$JENKINS_PASSWORD \
    ./
  gcloud compute images describe $agent_image --project=$project
fi

popd
