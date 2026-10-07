#!/bin/bash
set -euo pipefail

log() {
    local level="$1"
    shift
    jq -cn --arg level "$level" --arg msg "$*" --arg time "$(date -u '+%Y-%m-%dT%H:%M:%SZ')" --arg file "${BASH_SOURCE[1]##*/}" --argjson line "${BASH_LINENO[0]}" --arg func "${FUNCNAME[1]:-main}" '{time:$time,level:$level,file:$file,line:$line,func:$func,msg:$msg}' >&2
}
trap 'log ERROR "refresh contract failed"' ERR
LOG_FILE="${LOG_FILE:-/tmp/dikciz-refresh-test.log}"
exec > >(tee -a "$LOG_FILE") 2>&1

fixture="$(mktemp -d /tmp/dikciz-refresh-contract.XXXXXX)"
trap 'rm -rf -- "$fixture"' EXIT
SCRIPT_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
readonly SCRIPT_DIRECTORY
mkdir -p "$fixture/app/scripts" "$fixture/app/.git" "$fixture/state" "$fixture/app/.research_files/upstream/app/src/main" "$fixture/app/app/src/dikciz" "$fixture/app/app/src/dikcizDebug" "$fixture/app/.fossify/overlays"
cp "$SCRIPT_DIRECTORY/dikciz-upstream.sh" "$fixture/app/scripts/"
ln -s ../state "$fixture/app/.android-lab"
printf '%s\n' preserved >"$fixture/app/.git/sentinel"
printf '%s\n' phone >"$fixture/state/sentinel"
printf '%s\n' product >"$fixture/app/app/src/dikciz/sentinel"
printf '%s\n' product-debug >"$fixture/app/app/src/dikcizDebug/sentinel"
printf '%s\n' '1.0.0' >"$fixture/app/.fossify/release-marker.txt"
upstream="$fixture/app/.research_files/upstream"
mkdir -p "$upstream/.fossify" "$upstream/gradle/wrapper" "$upstream/.github"
printf '%s\n' '1.0.1' >"$upstream/.fossify/release-marker.txt"
printf '%s\n' '#!/bin/bash' >"$upstream/gradlew"
chmod 0755 "$upstream/gradlew"
printf '%s\n' old >"$upstream/app/build.gradle.kts"
printf '%s\n' old >"$upstream/gradle/wrapper/gradle-wrapper.properties"
printf '%s\n' upstream >"$upstream/app/src/main/sentinel"
for path in app/build.gradle.kts gradle/wrapper/gradle-wrapper.properties; do
    patch_name="$(basename "$path").patch"
    [[ "$path" != app/build.gradle.kts ]] || patch_name=app-build.gradle.kts.patch
    printf '%s\n' "--- a/$path" "+++ b/$path" '@@ -1 +1 @@' '-old' '+integrated' >"$fixture/app/.fossify/overlays/$patch_name"
done
if bash "$fixture/app/scripts/dikciz-upstream.sh" refresh .research_files/upstream; then
    log ERROR "refresh accepted missing confirmation"
    exit 1
fi
DIKCIZ_UPSTREAM_REFRESH=1 bash "$fixture/app/scripts/dikciz-upstream.sh" refresh .research_files/upstream
[[ "$(cat "$fixture/app/.git/sentinel")" == preserved ]]
[[ -L "$fixture/app/.android-lab" && "$(cat "$fixture/state/sentinel")" == phone ]]
[[ "$(cat "$fixture/app/app/src/main/sentinel")" == upstream ]]
[[ "$(cat "$fixture/app/app/src/dikciz/sentinel")" == product ]]
[[ "$(cat "$fixture/app/.fossify/release-marker.txt")" == 1.0.1 ]]
[[ "$(cat "$fixture/app/app/build.gradle.kts")" == integrated ]]
[[ -d "$upstream" ]]
backup="$(find "$fixture/state/fossify-refresh" -mindepth 1 -maxdepth 1 -type d -name 'dikciz-before-refresh.*')"
[[ -s "$backup/snapshot/.fossify/release-marker.txt" ]]
[[ "$(cat "$backup/snapshot/.fossify/release-marker.txt")" == 1.0.0 ]]
log INFO "root refresh preserves Git, shared state, product overlays, upstream input and verified backup"
