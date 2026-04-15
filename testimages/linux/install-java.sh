#!/bin/bash
set -euxo pipefail

echo "AGENT_IMAGE value is: $AGENT_IMAGE"
if [ -z "$AGENT_IMAGE" ]; then
  echo "AGENT_IMAGE variable is not set"
  exit 1
fi

install_java() {
  export DEBIAN_FRONTEND=noninteractive
  apt-get update
  apt-get install -y wget apt-transport-https gpg
  wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public | gpg --dearmor | tee /etc/apt/trusted.gpg.d/adoptium.gpg > /dev/null
  echo "deb https://packages.adoptium.net/artifactory/deb $(awk -F= '/^VERSION_CODENAME/{print$2}' /etc/os-release) main" | tee /etc/apt/sources.list.d/adoptium.list
  apt-get update
  # jre is sufficient in the integration tests (jdk would be required if we are building a java projects inside the agent.)
  apt-get install -y temurin-25-jre
  java -version

  if [[ "$AGENT_IMAGE" == *non-standard-java ]]; then
    sudo mv /usr/bin/java /usr/bin/non-standard-java
    /usr/bin/non-standard-java -version
  fi
}

export -f install_java
script_block_to_run="export AGENT_IMAGE=$AGENT_IMAGE; $(declare -f install_java); install_java;"
sudo bash -c "$script_block_to_run"