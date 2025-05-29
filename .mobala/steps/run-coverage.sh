#!/usr/bin/env bash

set -euo pipefail

function run-coverage() {
  sbt -batch -no-colors -v \
    --java-home "$JAVA_HOME" \
    "$VERSION_COMMAND clean" \
    coverage \
    "$VERSION_COMMAND Test/compile" \
    "$VERSION_COMMAND test" \
    "$VERSION_COMMAND coverageReport"
  
  docker rm $(docker ps -aq) || true
}