#!/usr/bin/env sh

export AGENT_URL="https://artefacts.tax.service.gov.uk/artifactory/hmrc-releases-local/uk/gov/hmrc/jvm-bobby/0.1.0/jvm-bobby-0.1.0-assembly.jar"
curl --silent --location --noproxy "discoverd" --retry 5 --fail "${AGENT_URL}" -o "agent.jar"
export JAVA_OPTS="$JAVA_OPTS -javaagent:${PWD}/agent.jar"

SCRIPT=$(find . -type f -name teams-and-repositories)
exec $SCRIPT $HMRC_CONFIG -Dconfig.file=conf/teams-and-repositories.conf
