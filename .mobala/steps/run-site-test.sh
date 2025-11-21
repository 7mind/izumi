#!/usr/bin/env bash

set -euo pipefail

function run-site-test() {
  sbt -batch -no-colors -v \
    --java-home "$JAVA_HOME" \
    "project docs" \
    "$VERSION_COMMAND clean" \
    "$VERSION_COMMAND makeSite"
}