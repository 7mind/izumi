#!/usr/bin/env bash

# bash>4.2 required due to `declare -g`
bash_major="${BASH_VERSINFO[0]}"
bash_minor="${BASH_VERSINFO[1]}"

if (( bash_major < 4 )) || { (( bash_major == 4 )) && (( bash_minor < 2 )); }; then
  echo "Error: Bash 4.2 or higher required. Your version is $BASH_VERSION."
  exit 1
fi

set -euo pipefail

# run wih `MOBALA_UPDATE=1 ./run` to update commit lock file

export MOBALA_UPDATE=${MOBALA_UPDATE:-0}
export MOBALA_SELF_UPDATE=${MOBALA_SELF_UPDATE:-1}
export MOBALA_CACHE_FORCE_UPDATE=${MOBALA_CACHE_FORCE_UPDATE:-0}

read_trimmed_string() { [[ -s "$1" ]] && sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//' "$1" || echo "$2"; }

function get_commit_of_remote_ref() {
  curl -sLJ0 -H 'Cache-Control: no-cache, no-store' -w "%{response_code}" "$1" \
    | grep -m 1 '"sha":' | sed -r 's/.*\"sha\":[^\"]*\"([^\"]*)\".*/\1/';
}

MOBALA_REMOTE_LOCK_SOURCE_REF=$(read_trimmed_string ".mobala/version.txt" "release")
export MOBALA_REMOTE_LOCK_SOURCE_REF
export MOBALA_REMOTE_GET_COMMIT_URL="https://api.github.com/repos/7mind/mobala/commits/${MOBALA_REMOTE_LOCK_SOURCE_REF}"

MOBALA_REMOTE_LATEST_COMMIT=$(get_commit_of_remote_ref "${MOBALA_REMOTE_GET_COMMIT_URL}" || echo 0)
export MOBALA_REMOTE_LATEST_COMMIT

if [[ "${MOBALA_UPDATE}" == 0 && -f ".mobala/version-commit.lock" ]]; then
  MOBALA_LOCK_COMMIT=$(cat ".mobala/version-commit.lock")
  export MOBALA_LOCK_COMMIT
  export MOBALA_REMOTE_VERSION="${MOBALA_LOCK_COMMIT}"
  echo "[info] running mobala branch \`${MOBALA_REMOTE_LOCK_SOURCE_REF}\` commit ${MOBALA_LOCK_COMMIT}"
  if [[ "${MOBALA_REMOTE_LATEST_COMMIT}" != 0 && "${MOBALA_REMOTE_LATEST_COMMIT}" != "${MOBALA_LOCK_COMMIT}" ]]; then
    echo "[info] new version of mobala is available. run with MOBALA_UPDATE=1 to update to latest commit ${MOBALA_REMOTE_LATEST_COMMIT}"
  fi
else
  echo "[info] updating mobala lock, downloading updated commit for remote ref \`${MOBALA_REMOTE_LOCK_SOURCE_REF}\`"
  if [[ "${MOBALA_REMOTE_LATEST_COMMIT}" == 0 ]]; then
    echo "[error] couldn't fetch latest mobala commit from ${MOBALA_REMOTE_GET_COMMIT_URL} due to network error"
    exit 1
  fi
  export MOBALA_LOCK_COMMIT="${MOBALA_REMOTE_LATEST_COMMIT}"
  printf '%s' "${MOBALA_LOCK_COMMIT}" > ".mobala/version-commit.lock"
  echo "[info] updated mobala lock to commit ${MOBALA_LOCK_COMMIT} which is the latest commit for ${MOBALA_REMOTE_LOCK_SOURCE_REF}"
fi

export SYS_CACHE_DIR="${XDG_CACHE_HOME:-"${HOME}/.cache"}"
export CACHE_DIR="${SYS_CACHE_DIR%/}/mobala/${MOBALA_LOCK_COMMIT}"
export MOBALA_CACHE_MAIN="${CACHE_DIR}/mobala.sh"
export MOBALA_CACHE_LIB="${CACHE_DIR}/mobala-lib.sh"
export MOBALA_CACHE_RESOLVER="${CACHE_DIR}/mobala-resolver.sh"

export MOBALA_REMOTE_BASE="https://raw.githubusercontent.com/7mind/mobala/${MOBALA_LOCK_COMMIT}"
export MOBALA_REMOTE_MAIN_FILE="${MOBALA_REMOTE_BASE}/mobala.sh"
export MOBALA_REMOTE_LIB_FILE="${MOBALA_REMOTE_BASE}/mobala-lib.sh"
export MOBALA_REMOTE_RESOLVER_FILE="${MOBALA_REMOTE_BASE}/mobala-resolver.sh"

script_path="$(realpath "$0")"
script_dirname="$(dirname "$script_path")"

export MOBALA_PATH="${script_dirname}"
export MOBALA_SUBDIR=${MOBALA_SUBDIR:-".mobala"}
export MOBALA_KEEP=${MOBALA_KEEP:-"${MOBALA_PATH}/${MOBALA_SUBDIR}/keep.env"}
export MOBALA_ENV=${MOBALA_ENV:-"${MOBALA_PATH}/${MOBALA_SUBDIR}/env.sh"}
export MOBALA_MODS=${MOBALA_MODS:-"${MOBALA_PATH}/${MOBALA_SUBDIR}/mods"}
export MOBALA_PARAMS=${MOBALA_PARAMS:-"${MOBALA_PATH}/${MOBALA_SUBDIR}/params"}

function download-file() {
    origin="${1}"
    target="${2}"
    mkdir -p "${CACHE_DIR}"
    cache_name="$(basename "$target")"
    cache_tmp="${CACHE_DIR}/${cache_name}.tmp"
    rm -rf "${cache_tmp}"
    download_response=$(curl -sLJ0 -H 'Cache-Control: no-cache, no-store' -o "${cache_tmp}" -w "%{response_code}" "${origin}" || true)
    if [[ "${download_response}" == "200" ]]; then
        rm -rf "${target}"
        mv "${cache_tmp}" "${target}"
        echo "[info] cache updated: ${target}"
    else
        echo "[warn] download failed with ${download_response} status code for ${target}"
        rm "${cache_tmp}"
    fi
}

function check-cache() {
    if [[ -f "${MOBALA_CACHE_MAIN}" && -f "${MOBALA_CACHE_LIB}" && -f "${MOBALA_CACHE_RESOLVER}" ]]; then
        echo "[info] mobala.sh cache found at '${MOBALA_CACHE_MAIN}'"
    else
        echo "[info] mobala.sh cache not found at '${MOBALA_CACHE_MAIN}'"
        export MOBALA_CACHE_STALE=1
    fi
}

function update-cache() {
    if [[ "${MOBALA_CACHE_FORCE_UPDATE}" == 1 || "${MOBALA_CACHE_STALE:-0}" == 1 ]] ; then
        download-file "${MOBALA_REMOTE_LIB_FILE}" "${MOBALA_CACHE_LIB}"
        download-file "${MOBALA_REMOTE_MAIN_FILE}" "${MOBALA_CACHE_MAIN}"
        download-file "${MOBALA_REMOTE_RESOLVER_FILE}" "${MOBALA_CACHE_RESOLVER}"
    fi
}

function verify-cache() {
    if ! [[ -f "${MOBALA_CACHE_MAIN}" ]]; then
        >&2 echo "[error] mobala.sh cache not found for commit ${MOBALA_LOCK_COMMIT}."
        exit 1
    fi
    if ! [[ -f "${MOBALA_CACHE_LIB}" ]]; then
        >&2 echo "[error] mobala-lib.sh cache not found for commit ${MOBALA_LOCK_COMMIT}."
        exit 1
    fi
    if ! [[ -f "${MOBALA_CACHE_RESOLVER}" ]]; then
        >&2 echo "[error] mobala-resolver.sh cache not found for commit ${MOBALA_LOCK_COMMIT}."
        exit 1
    fi
}

function update-self(){
  if [[ "${CI:-false}" == "false" && "${MOBALA_SELF_UPDATE}" == 1 ]] ; then
    if [[ "${MOBALA_LOCK_COMMIT:-0}" != 0 && -f "${MOBALA_CACHE_RESOLVER}" ]]; then
        cp "${MOBALA_CACHE_RESOLVER}" "${script_path}"
      else
        download-file "${MOBALA_REMOTE_RESOLVER_FILE}" "${script_path}"
    fi
    chmod +x "${script_path}"
  fi
}

trap 'update-self' EXIT

check-cache
update-cache
verify-cache

bash "${MOBALA_CACHE_MAIN}" "$@"
