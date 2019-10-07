# Copyright 2019 Google Inc.
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

#!/usr/bin/env bash
mkdir result

# The files will only exist if maven reaches the corresponding phase of the build
function cpe() {
  if [[ -e $1 ]]; then
     cp -r target/$1 result
  fi
}

cpe google-compute-engine.hpi
cpe failsafe-reports
cpe surefire-reports

# Compress the artifacts for upload
tar -zcvf ${FAILED_ARTIFACTS} result
