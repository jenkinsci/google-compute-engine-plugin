#!/bin/bash

set -e -u -x

pushd plugin
  GOOGLE_PROJECT_ID=$project_id GOOGLE_CREDENTIALS=$service_account_json mvn verify
popd
