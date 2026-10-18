#!/bin/bash
# Builds the signed Dikciz release APK inside the Android Lab tool image, verifies
# its signer and version, and writes dist/dikciz-launcher-vX.Y.Z.apk plus .sha256.
set -euo pipefail

log() {
    local level="$1"
    shift
    jq -cn --arg level "$level" --arg msg "$*" --arg time "$(date -u '+%Y-%m-%dT%H:%M:%SZ')" --arg file "${BASH_SOURCE[1]##*/}" --argjson line "${BASH_LINENO[0]}" --arg func "${FUNCNAME[1]:-main}" '{time:$time,level:$level,file:$file,line:$line,func:$func,msg:$msg}' >&2
}
on_error() {
    local exit_code=$?
    trap - ERR
    log ERROR "release APK build failed exit=${exit_code}"
    exit "$exit_code"
}
trap on_error ERR
LOG_FILE="${LOG_FILE:-/tmp/dikciz-release-apk.log}"
exec > >(tee -a "$LOG_FILE") 2>&1

readonly RELEASE_TASK=:app:assembleDikcizRelease
readonly BUILD_TOOLS_DIRECTORY=/opt/android-sdk/build-tools
readonly DIST_DIRECTORY=dist
readonly VERSION_PATTERN='^[0-9]+\.[0-9]+\.[0-9]+$'
readonly -a SIGNING_VARIABLES=(SIGNING_KEY_ALIAS SIGNING_KEY_PASSWORD SIGNING_STORE_FILE SIGNING_STORE_PASSWORD DIKCIZ_RELEASE_CERT_SHA256)

built_apk="${1:-}"
[[ -n "$built_apk" && "$built_apk" != /* && "$built_apk" == *.apk ]] || {
    log ERROR "pass the workspace-relative Gradle output APK path"
    exit 2
}
for variable in "${SIGNING_VARIABLES[@]}"; do
    [[ -n "${!variable:-}" ]] || {
        log ERROR "missing signing input; Gradle would build an unsigned APK variable=${variable}"
        exit 1
    }
done
[[ -f "$SIGNING_STORE_FILE" ]] || {
    log ERROR "release keystore is not inside the mounted workspace path=${SIGNING_STORE_FILE}"
    exit 1
}
version="$(<VERSION)"
[[ "$version" =~ $VERSION_PATTERN ]] || {
    log ERROR "VERSION must look like X.Y.Z version=${version}"
    exit 1
}

log INFO "building the signed Dikciz release APK version=${version}"
rm -f -- "$built_apk"
GRADLE_TASK="$RELEASE_TASK" android-lab gradle
[[ -s "$built_apk" ]] || {
    log ERROR "Gradle did not produce the signed release APK path=${built_apk}"
    exit 1
}

build_tools="$(find "$BUILD_TOOLS_DIRECTORY" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -n 1)"
signer_report="$("$build_tools/apksigner" verify --print-certs "$built_apk")"
signer_sha256="$(sed -n 's/^Signer #1 certificate SHA-256 digest: //p' <<<"$signer_report")"
expected_sha256="$(tr -d ':' <<<"$DIKCIZ_RELEASE_CERT_SHA256" | tr '[:upper:]' '[:lower:]')"
[[ -n "$signer_sha256" && "$signer_sha256" == "$expected_sha256" ]] || {
    log ERROR "release APK signer does not match the release certificate actual=${signer_sha256} expected=${expected_sha256}"
    exit 1
}

badging="$("$build_tools/aapt2" dump badging "$built_apk")"
apk_version_name="$(sed -n "s/.* versionName='\([^']*\)'.*/\1/p" <<<"$badging" | head -n 1)"
[[ "$apk_version_name" == "$version" ]] || {
    log ERROR "release APK versionName differs from VERSION apk=${apk_version_name} version=${version}"
    exit 1
}

release_name="dikciz-launcher-v${version}.apk"
mkdir -p "$DIST_DIRECTORY"
cp -- "$built_apk" "$DIST_DIRECTORY/$release_name"
(cd "$DIST_DIRECTORY" && sha256sum -- "$release_name" >"$release_name.sha256")
log INFO "signed release APK ready path=${DIST_DIRECTORY}/${release_name} signer_sha256=${signer_sha256}"
