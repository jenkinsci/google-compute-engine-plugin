#!/bin/bash

[ -z "$BRANCH" ] && echo "Need to set BRANCH" && exit 1
[ -z "$PROJECT_ID" ] && echo "Need to set PROJECT_ID" && exit 1
[ -z "$SERVICE_ACCOUNT_JSON" ] && echo "Need to set SERVICE_ACCOUNT_JSON" && exit 1

fly -t jenkins set-pipeline \
  -c pipeline.yml \
  -v project_id=$PROJECT_ID \
  -v service_account_json="$SERVICE_ACCOUNT_JSON" \
  -v plugin_branch=$BRANCH \
  -v ci_branch=ci \
  -p compute-engine-plugin-$BRANCH
