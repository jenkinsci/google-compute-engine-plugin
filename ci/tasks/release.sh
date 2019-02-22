#!/bin/bash

set -e -u -x

version=$(cat version/version)

pushd plugin
  GOOGLE_PROJECT_ID=$project_id GOOGLE_CREDENTIALS=$service_account_json \
    mvn \
    -B \
    -Dtag=google-compute-engine-$version \
    -DdryRun=true \
    -DreleaseVersion=$version \
    -DdevelopmentVersion=$version \
    release:prepare
popd
