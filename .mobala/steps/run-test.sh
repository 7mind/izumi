#!/usr/bin/env bash

set -euo pipefail

function run-test() {
  sbt -batch -no-colors -v \
    --java-home "$JAVA_HOME" \
    "$VERSION_COMMAND clean" \
    "$VERSION_COMMAND Test/compile" \
    "$VERSION_COMMAND test"
  
  docker rm "$(docker ps -aq)" || true
}
