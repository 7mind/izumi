#!/usr/bin/env bash

set -e
self="$(realpath "$0")"
path="$(dirname "$self")"
echo "Working in $path"
cd "$path"
export LANG="C.UTF-8"

function nixify() {
  read -r -a args <<< "$(grep -v '^\s*$' .keep.env | sed "s/^/--keep /;s/$/ /" | tr '\n' ' ')"

  if [[ -z "${IN_NIX_SHELL+x}" ]]; then
      echo "Restarting in Nix..."
      set -x
      nix flake lock
      nix flake metadata
      exec nix develop \
        --ignore-environment \
        --keep HOME \
        --keep DOCKER_HOST \
        --keep CI_BRANCH \
        --keep CI_COMMIT \
        --keep CI_BRANCH_TAG \
        --keep CI_PULL_REQUEST \
        --keep CI_BUILD_UNIQ_SUFFIX \
        --keep CI \
        "${args[@]}" \
        --command bash "$self" "$@"
  fi
}

for i in "$@"
do
case $i in
    nix) shift && nixify "$@" ;;
    env) exec bash -norc ;;
    *) "./devops/$i.sh" ;;
esac
done
