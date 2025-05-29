#!/usr/bin/env bash

set -euo pipefail

function do-build() {
  step_run_cond run-gen
  step_run_cond run-gen-js
  step_run_cond run-gen-jsonly
  step_run_cond run-test
  step_run_cond run-coverage
  step_run_cond run-site-test  
  step_run_cond run-publish-scala
  step_run_cond run-site-publish
}
