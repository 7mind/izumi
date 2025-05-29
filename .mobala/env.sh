#!/usr/bin/env bash

set -euo pipefail
if [[ "${DO_VERBOSE}" == 1 ]] ; then set -x ; fi


set_scala_sbtgen_variables

set_jdk_path
set_jvm_options
set_jvm_optimizations
debug_env


# this script receives all the CLI args from the main script and may decide which flows should be enabled
flow_enable do-build