#!/bin/bash
set -euo pipefail

log() {
    local level="$1"
    shift
    jq -cn --arg level "$level" --arg msg "$*" --arg time "$(date -u '+%Y-%m-%dT%H:%M:%SZ')" --arg file "${BASH_SOURCE[1]##*/}" --argjson line "${BASH_LINENO[0]}" --arg func "${FUNCNAME[1]:-main}" '{time:$time,level:$level,file:$file,line:$line,func:$func,msg:$msg}' >&2
}
trap 'log ERROR "CI tooling bootstrap failed"' ERR
LOG_FILE="${LOG_FILE:-/tmp/dikciz-ci-build.log}"
exec > >(tee -a "$LOG_FILE") 2>&1

[[ "${CI:-}" == true ]] || {
    log ERROR "CI-only bootstrap; build local images using the Android Lab README"
    exit 1
}
[[ "${ANDROID_LAB_VERSION:-}" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || {
    log ERROR "ANDROID_LAB_VERSION must be a release version"
    exit 1
}
readonly LAB_REPOSITORY=https://github.com/psyb0t/android-lab.git
fixture="$(mktemp -d /tmp/dikciz-ci-toolchain.XXXXXX)"
trap 'rm -rf -- "$fixture"' EXIT
log INFO "building version-checked local tooling for CI"
git clone --depth 1 --branch main "$LAB_REPOSITORY" "$fixture/lab"
[[ "$(<"$fixture/lab/VERSION")" == "$ANDROID_LAB_VERSION" ]] || {
    log ERROR "Android Lab source version differs from required image version"
    exit 1
}
make -C "$fixture/lab" dev-image ANDROID_LAB_ACCEPT_ANDROID_LICENSES=yes
make build
