#!/usr/bin/env bash
# Copyright 2020 Google Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
pwd
mkdir result

# The files will only exist if maven reaches the corresponding phase of the build
function cpe() {
  if [[ -e target/$1 ]]; then
     cp -rv target/$1 result
  else
     echo "target/$1 not copied, check the completed build phases."
  fi
}

# Copy over the important artifacts
cpe google-compute-engine.hpi
cpe failsafe-reports
cpe surefire-reports

# Compress the artifacts for upload
tar -zcvf ${BUILD_ARTIFACTS} result
