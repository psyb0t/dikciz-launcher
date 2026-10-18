#!/bin/bash
# Builds the pinned Android Lab images with the installer from that release's tag.
# Runs on the host from the repository root; the Makefile calls it when an image is missing.
set -euo pipefail

readonly SCRIPT_NAME=install-android-lab.sh
readonly ANDROID_LAB_REPOSITORY=https://github.com/psyb0t/android-lab.git
readonly SDK_TERMS_URL=https://developer.android.com/studio/terms
readonly VERSION_PATTERN='^[0-9]+\.[0-9]+\.[0-9]+$'
readonly STATE_DIRECTORY_NAME=.android-lab
readonly INSTALLER_DIRECTORY_NAME=android-lab-install
readonly EXIT_USAGE=2

json_string() {
    local value="$1"
    value="${value//\\/\\\\}"
    value="${value//\"/\\\"}"
    value="${value//$'\n'/\\n}"
    value="${value//$'\r'/\\r}"
    value="${value//$'\t'/\\t}"
    printf '"%s"' "$value"
}

# jq is not a host requirement, so the JSON line is assembled here.
log() {
    local level="$1"
    shift
    printf '{"time":%s,"level":%s,"file":%s,"line":%d,"func":%s,"msg":%s}\n' \
        "$(json_string "$(date -u '+%Y-%m-%dT%H:%M:%SZ')")" \
        "$(json_string "$level")" \
        "$(json_string "$SCRIPT_NAME")" \
        "${BASH_LINENO[0]}" \
        "$(json_string "${FUNCNAME[1]:-main}")" \
        "$(json_string "$*")" >&2
}

on_error() {
    local exit_code=$?
    trap - ERR
    log ERROR "command failed exit=${exit_code}"
    exit "$exit_code"
}

trap on_error ERR

LOG_FILE="${LOG_FILE:-/tmp/dikciz-install-android-lab.log}"
exec > >(tee -a "$LOG_FILE") 2> >(tee -a "$LOG_FILE" >&2)

usage() {
    printf '%s\n' "Usage: $SCRIPT_NAME VERSION [--emulator]" \
        'Build android-lab:VERSION, and with --emulator its emulator image, from the vVERSION installer.'
}

version="${1:-}"
emulator_flag="${2:-}"
[[ "$version" =~ $VERSION_PATTERN ]] || {
    log ERROR "VERSION must look like X.Y.Z version=${version}"
    usage >&2
    exit "$EXIT_USAGE"
}
[[ -z "$emulator_flag" || "$emulator_flag" == --emulator ]] || {
    log ERROR "unknown option option=${emulator_flag}"
    usage >&2
    exit "$EXIT_USAGE"
}
[[ "${ANDROID_LAB_ACCEPT_ANDROID_LICENSES:-}" == yes ]] || {
    log ERROR "Android Lab ${version} images are missing. Read ${SDK_TERMS_URL}, then rerun the same make command with ANDROID_LAB_ACCEPT_ANDROID_LICENSES=yes to build them."
    exit 1
}
# The emulator build bind-mounts the installer checkout through the host Docker
# daemon, so it must live at a real host path: the workspace's state directory.
state_directory="$(realpath -m "$PWD/$STATE_DIRECTORY_NAME")"
installer_directory="$state_directory/$INSTALLER_DIRECTORY_NAME"
mkdir --parents "$installer_directory"
release_checkout="$(mktemp -d "$installer_directory/v${version}.XXXXXX")"
trap 'rm -rf -- "$release_checkout"' EXIT

log INFO "fetching the Android Lab installer repository=${ANDROID_LAB_REPOSITORY} tag=v${version}"
git -c advice.detachedHead=false clone --quiet --depth 1 --branch "v${version}" \
    "$ANDROID_LAB_REPOSITORY" "$release_checkout/android-lab" || {
    log ERROR "could not fetch Android Lab release tag=v${version}"
    exit 1
}

installer_arguments=("$version" --work-dir "$installer_directory")
if [[ -n "$emulator_flag" ]]; then
    installer_arguments+=(--emulator)
fi
bash "$release_checkout/android-lab/install.sh" "${installer_arguments[@]}" >&2
log INFO "Android Lab images are ready version=${version}"
