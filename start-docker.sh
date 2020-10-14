#!/usr/bin/env sh

export JVM_BOBBY_VERSION=0.3.0
export JVM_BOBBY_URL="https://artefacts.tax.service.gov.uk/artifactory/hmrc-releases-local/uk/gov/hmrc/jvm-bobby/${JVM_BOBBY_VERSION}/jvm-bobby-${JVM_BOBBY_VERSION}-assembly.jar"
curl --location --noproxy "discoverd" --retry 5 "${JVM_BOBBY_URL}" -o "jvm-bobby.jar"
export JAVA_OPTS="$JAVA_OPTS -javaagent:${PWD}/jvm-bobby.jar"

SCRIPT=$(find . -type f -name teams-and-repositories)
exec $SCRIPT $HMRC_CONFIG -Dconfig.file=conf/teams-and-repositories.conf
