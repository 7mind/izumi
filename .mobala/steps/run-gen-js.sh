#!/usr/bin/env bash

set -euo pipefail

function run-gen-js() {
  bash sbtgen.sc --js
}