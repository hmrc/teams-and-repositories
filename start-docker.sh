#!/usr/bin/env sh

export AGENT_VERSION=0.2.0
export AGENT_URL="https://artefacts.tax.service.gov.uk/artifactory/hmrc-releases-local/uk/gov/hmrc/jvm-bobby/${AGENT_VERSION}/jvm-bobby-${AGENT_VERSION}-assembly.jar"
curl --location --noproxy "discoverd" --retry 5 "${AGENT_URL}" -o "agent.jar"
export JAVA_OPTS="$JAVA_OPTS -javaagent:${PWD}/agent.jar"

SCRIPT=$(find . -type f -name teams-and-repositories)
exec $SCRIPT $HMRC_CONFIG -Dconfig.file=conf/teams-and-repositories.conf
