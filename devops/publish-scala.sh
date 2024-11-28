#!/usr/bin/env bash

set -e
set -x

source ./devops/.env.sh
source ./devops/.validate-publishing.sh

[[ ! -f "$SONATYPE_SECRET" ]] && echo "SONATYPE_SECRET=$SONATYPE_SECRET is not a file" && exit 0


echo "PUBLISH SCALA LIBRARIES..."

if [[ "$CI_BRANCH_TAG" =~ ^v.*$ ]] ; then
  sbt -batch -no-colors -v \
    --java-home "$JAVA_HOME" \
    "show credentials" \
    "$VERSION_COMMAND clean" \
    "$VERSION_COMMAND package" \
    "$VERSION_COMMAND publishSigned" \
    "sonatypeBundleRelease"
else
  sbt -batch -no-colors -v \
    --java-home "$JAVA_HOME" \
    "show credentials" \
    "$VERSION_COMMAND clean" \
    "$VERSION_COMMAND package" \
    "$VERSION_COMMAND publishSigned"
fi
