#!/bin/bash

set -e -u -x

apk add --no-cache git openssh

version=$(cat version/version)

mkdir -p $HOME/.ssh
cat >$HOME/.ssh/config <<EOL
Host github.com
    StrictHostKeyChecking no
    IdentityFile ~/.ssh/key
EOL

echo "$git_private_key" >$HOME/.ssh/key
chmod 0400 $HOME/.ssh/key

# Write a Maven config file that allows uploading the build artifact
# to the Jenkins repository.
cat >maven-settings.xml <<EOL
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0
                      http://maven.apache.org/xsd/settings-1.0.0.xsd">
  <servers>
    <server>
      <id>maven.jenkins-ci.org</id> <!-- For parent 1.397 or newer; this ID is used for historical reasons and independent of the actual host name -->
      <username>${artifactory_username}</username>
      <password>${artifactory_password}</password>
    </server>
  </servers>
</settings>
EOL

# Maven will use git to make a commit. It should be configured.
git config --global user.email "release-robot@cloud-graphite.com"
git config --global user.name "release-robot"

#TODO: delete maven.test.skip=true
#TODO: add custom maven settings file
pushd plugin
  GOOGLE_PROJECT_ID=$project_id GOOGLE_CREDENTIALS=$service_account_json \
    mvn \
    -B \
    -Dmaven.test.skip=true \
    -Dmaven.test.skipTests=true \
    -DskipTests \
    -Darguments=-DskipTests \
    -Dtag=google-compute-engine-$version \
    -DreleaseVersion=$version \
    -DdevelopmentVersion=$version \
    release:prepare \
    release:perform
popd
