#!/bin/bash

set -e -u -x

version=$(cat version/version)

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

pushd plugin
  GOOGLE_PROJECT_ID=$project_id GOOGLE_CREDENTIALS=$service_account_json \
    mvn \
    -B \
    -Dtag=google-compute-engine-$version \
    -DreleaseVersion=$version \
    -DdevelopmentVersion=$version \
    -DpushChanges=false \
    release:prepare \
    release:perform
popd
