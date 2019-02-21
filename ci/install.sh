#!/bin/bash
fly -t jenkins set-pipeline \
  -c pipeline.yml \
  -v project_id=$PROJECT_ID \
  -v service_account_json=$SERVICE_ACCOUNT_JSON \
  -p compute-engine-plugin-develop
