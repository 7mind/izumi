#!/usr/bin/env bash

set -euo pipefail

function run-gen-jsonly() {
  bash sbtgen.sc --nojvm --js
}