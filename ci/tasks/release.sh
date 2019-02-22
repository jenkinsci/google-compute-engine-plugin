#!/bin/bash

set -e -u -x

version=$(cat version/version)
echo $version

pushd plugin
  GOOGLE_PROJECT_ID=$project_id GOOGLE_CREDENTIALS=$service_account_json \
    mvn \
    -DdryRun=true \
    -DreleaseVersion=$version \
    -DdevelopmentVersion=$version \
    release:prepare
popd
