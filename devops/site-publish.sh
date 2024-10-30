#!/usr/bin/env bash

set -e
set -x

source ./devops/.env.sh
source ./devops/.validate-publishing.sh

sbt -batch -no-colors -v \
  "project docs" \
  "$VERSION_COMMAND clean" \
  "$VERSION_COMMAND makeSite" \
  "$VERSION_COMMAND ghpagesSynchLocal" \
  "$VERSION_COMMAND ghpagesPushSite"