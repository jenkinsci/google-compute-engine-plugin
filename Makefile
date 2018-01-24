hpi:
	export MAVEN_OPTS="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,address=8000,suspend=n"
	mvn -o hpi:run

upload:
	mvn package
	gsutil cp -a public-read target/google-compute-plugin.hpi gs://jenkins-graphite/google-compute-plugin-latest.hpi
	gsutil cp -a public-read target/google-compute-plugin.hpi gs://jenkins-graphite/google-compute-plugin-`git rev-parse --short HEAD`.hpi
