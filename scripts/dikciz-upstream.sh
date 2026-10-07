#!/bin/bash

set -euo pipefail

readonly PROJECT_DIRECTORY=.
readonly RELEASE_MARKER_PATH=.fossify/release-marker.txt
readonly OVERLAY_DIRECTORY=.fossify/overlays
readonly DEFAULT_BACKUP_DIRECTORY=.android-lab/fossify-refresh
readonly REFRESH_CONFIRMATION=1
readonly RELEASE_MARKER_VERSION_PATTERN='^[0-9]+\.[0-9]+\.[0-9]+$'
readonly STAGE_DIRECTORY_PREFIX=dikciz-upstream-stage
readonly BACKUP_DIRECTORY_PREFIX=dikciz-before-refresh
readonly -a GENERATED_COMPARISON_PATHS=(
    .git
    .android-lab
    .research_files
    .testing
    .gradle
    .kotlin
    build
)

readonly -a PRESERVED_FILE_PATHS=(
    Makefile
    .gitignore
    AGENTS.md
    CONTRIBUTING.md
    PLAN.md
    README.md
    CHANGELOG.md
    .env
    .env.local
    .env.example
    local.properties
    .editorconfig
    .envrc
    .tool-versions
    .python-version
    .nvmrc
    pyrightconfig.json
    app/src/AGENTS.md
)

readonly -a PRESERVED_DIRECTORY_PATHS=(
    scripts
    .agents
    .github
    .fossify/overlays
    .agent_notes
    .agent_knowledge
    .idea
    .vscode
    captures
    docs
    tests
    app/src/dikciz
    app/src/dikcizDebug
)

log() {
    local level="$1"
    shift
    jq -cn \
        --arg time "$(date -u '+%Y-%m-%dT%H:%M:%S.%3NZ')" \
        --arg level "$level" \
        --arg file "${BASH_SOURCE[1]##*/}" \
        --argjson line "${BASH_LINENO[0]}" \
        --arg func "${FUNCNAME[1]:-main}" \
        --arg msg "$*" \
        '{time: $time, level: $level, file: $file, line: $line, func: $func, msg: $msg}' >&2
}

fail() {
    log ERROR "$*"
    exit 1
}

on_error() {
    local exit_code=$?
    trap - ERR
    log ERROR "command failed exit=${exit_code}"
    exit "$exit_code"
}

trap on_error ERR

WORKSPACE_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
PROJECT_ROOT="$(realpath "$WORKSPACE_DIRECTORY/$PROJECT_DIRECTORY")"
STATE_ROOT="$(realpath -m "$WORKSPACE_DIRECTORY/.android-lab")"
UPSTREAM_DIRECTORY=
BACKUP_DIRECTORY=
STAGE_DIRECTORY=

LOG_FILE="${LOG_FILE:-$WORKSPACE_DIRECTORY/.android-lab/fossify-refresh/dikciz-upstream.log}"
mkdir --parents "$(dirname "$LOG_FILE")"
exec > >(tee -a "$LOG_FILE") 2>&1

usage() {
    printf '%s\n' \
        'usage: dikciz-upstream.sh <compare|dry-run|refresh> <workspace-relative-upstream-dir> [workspace-relative-backup-dir]' >&2
}

is_safe_relative_path() {
    local relative_path="$1"
    case "$relative_path" in
    '' | /* | . | .. | ../* | */../* | */..)
        return 1
        ;;
    esac

    return 0
}

resolve_workspace_directory() {
    local relative_path="$1"
    local resolved_path

    is_safe_relative_path "$relative_path" || fail "path must be a workspace-relative directory without traversal"
    resolved_path="$(realpath "$WORKSPACE_DIRECTORY/$relative_path")"
    [[ "$resolved_path" == "$WORKSPACE_DIRECTORY/"* ]] || fail "path must stay under the Android lab workspace"
    [[ -d "$resolved_path" ]] || fail "directory does not exist: $relative_path"
    printf '%s\n' "$resolved_path"
}

resolve_backup_directory() {
    local relative_path="$1"
    local resolved_path

    is_safe_relative_path "$relative_path" || fail "backup path must be workspace-relative without traversal"
    case "$relative_path" in
    .android-lab/*)
        ;;
    *)
        fail "backup path must stay under .android-lab"
        ;;
    esac

    resolved_path="$(realpath -m "$WORKSPACE_DIRECTORY/$relative_path")"
    [[ "$resolved_path" == "$STATE_ROOT/"* ]] || fail "backup path must stay under .android-lab"
    printf '%s\n' "$resolved_path"
}

validate_upstream_tree() {
    [[ "$UPSTREAM_DIRECTORY" != "$PROJECT_ROOT" ]] || fail "upstream source cannot be the live Dikciz project"
    [[ -x "$UPSTREAM_DIRECTORY/gradlew" ]] || fail "upstream source is missing an executable Gradle wrapper"
    [[ -d "$UPSTREAM_DIRECTORY/app/src/main" ]] || fail "upstream source is missing app/src/main"
    [[ -s "$UPSTREAM_DIRECTORY/app/build.gradle.kts" ]] || fail "upstream source is missing app/build.gradle.kts"
    [[ -s "$UPSTREAM_DIRECTORY/$RELEASE_MARKER_PATH" ]] || fail "upstream source is missing its Fossify release marker"
}

release_marker_version() {
    local marker_path="$1"
    local marker_version

    marker_version="$(tail -n 1 "$marker_path" | tr -d '\r\n')"
    [[ "$marker_version" =~ $RELEASE_MARKER_VERSION_PATTERN ]] || fail "invalid Fossify release marker: $marker_path"
    printf '%s\n' "$marker_version"
}

is_newer_release_marker() {
    local current_version="$1"
    local incoming_version="$2"
    local current_major
    local current_minor
    local current_patch
    local incoming_major
    local incoming_minor
    local incoming_patch

    IFS=. read -r current_major current_minor current_patch <<<"$current_version"
    IFS=. read -r incoming_major incoming_minor incoming_patch <<<"$incoming_version"
    if ((10#$incoming_major > 10#$current_major)); then
        return 0
    fi
    if ((10#$incoming_major != 10#$current_major)); then
        return 1
    fi
    if ((10#$incoming_minor > 10#$current_minor)); then
        return 0
    fi
    if ((10#$incoming_minor != 10#$current_minor)); then
        return 1
    fi
    if ((10#$incoming_patch > 10#$current_patch)); then
        return 0
    fi

    return 1
}

validate_release_upgrade() {
    local current_version
    local incoming_version

    current_version="$(release_marker_version "$PROJECT_ROOT/$RELEASE_MARKER_PATH")"
    incoming_version="$(release_marker_version "$UPSTREAM_DIRECTORY/$RELEASE_MARKER_PATH")"
    is_newer_release_marker "$current_version" "$incoming_version" && return
    fail "incoming Fossify release $incoming_version is not newer than current release $current_version"
}

copy_preserved_file() {
    local relative_path="$1"
    local source_path="$PROJECT_ROOT/$relative_path"
    local target_path="$STAGE_DIRECTORY/$relative_path"

    [[ -f "$source_path" || -L "$source_path" ]] || return 0
    mkdir --parents "$(dirname "$target_path")"
    cp --archive -- "$source_path" "$target_path"
}

copy_preserved_directory() {
    local relative_path="$1"
    local source_path="$PROJECT_ROOT/$relative_path"
    local target_path="$STAGE_DIRECTORY/$relative_path"

    [[ -d "$source_path" ]] || return 0
    [[ ! -e "$target_path" && ! -L "$target_path" ]] || fail "incoming Fossify source conflicts with preserved Dikciz path: $relative_path"
    mkdir --parents "$(dirname "$target_path")"
    cp --archive -- "$source_path" "$target_path"
}

copy_preserved_environment_files() {
    local environment_file

    shopt -s nullglob
    for environment_file in "$PROJECT_ROOT"/.env.*; do
        copy_preserved_file "$(basename "$environment_file")"
    done
    shopt -u nullglob
}

apply_overlay_patches() {
    local patch_path
    local patch_name
    local -a patch_names=(
        app-build.gradle.kts.patch
        gradle-wrapper.properties.patch
    )

    command -v patch >/dev/null || fail "GNU patch is missing from the Android lab image"
    for patch_name in "${patch_names[@]}"; do
        patch_path="$PROJECT_ROOT/$OVERLAY_DIRECTORY/$patch_name"
        [[ -s "$patch_path" ]] || fail "required Dikciz integration patch is missing: $patch_name"
        patch --batch --forward --fuzz=0 --strip=1 --directory="$STAGE_DIRECTORY" <"$patch_path" || fail "Dikciz integration patch conflicts with incoming Fossify source: $patch_name"
    done
}

prepare_stage() {
    local incoming_path
    local relative_path
    local -a incoming_paths

    mkdir --parents "$BACKUP_DIRECTORY"
    STAGE_DIRECTORY="$(mktemp -d "$BACKUP_DIRECTORY/$STAGE_DIRECTORY_PREFIX.XXXXXX")"
    shopt -s dotglob nullglob
    incoming_paths=("$UPSTREAM_DIRECTORY"/*)
    shopt -u dotglob nullglob
    for incoming_path in "${incoming_paths[@]}"; do
        case "$(basename "$incoming_path")" in
        .git | .android-lab | .research_files | .testing | .gradle | .kotlin | build | .github | scripts | .agents)
            continue
            ;;
        esac
        cp --archive -- "$incoming_path" "$STAGE_DIRECTORY/"
    done

    for relative_path in "${PRESERVED_FILE_PATHS[@]}"; do
        copy_preserved_file "$relative_path"
    done
    copy_preserved_environment_files
    for relative_path in "${PRESERVED_DIRECTORY_PATHS[@]}"; do
        copy_preserved_directory "$relative_path"
    done
    apply_overlay_patches

    [[ -s "$STAGE_DIRECTORY/$RELEASE_MARKER_PATH" ]] || fail "staged source lost its Fossify release marker"
    [[ -d "$STAGE_DIRECTORY/app/src/dikciz" ]] || fail "staged source lost Dikciz product code"
    [[ -d "$STAGE_DIRECTORY/app/src/dikcizDebug" ]] || fail "staged source lost Dikciz debug product code"
}

cleanup_stage() {
    local first_stage_entry

    [[ -n "$STAGE_DIRECTORY" && -d "$STAGE_DIRECTORY" ]] || return 0
    case "$STAGE_DIRECTORY" in
    "$BACKUP_DIRECTORY"/"$STAGE_DIRECTORY_PREFIX".*)
        ;;
    *)
        log WARN "refusing to remove an unexpected staging directory"
        return
        ;;
    esac

    first_stage_entry="$(find "$STAGE_DIRECTORY" -mindepth 1 -maxdepth 1 -print -quit)"
    if [[ -n "$first_stage_entry" ]]; then
        rm --recursive --force -- "$STAGE_DIRECTORY"
    else
        rmdir -- "$STAGE_DIRECTORY"
    fi
    STAGE_DIRECTORY=
}

trap cleanup_stage EXIT

show_staged_delta() {
    local diff_status
    local generated_path
    local -a diff_arguments=(-q -r)

    for generated_path in "${GENERATED_COMPARISON_PATHS[@]}"; do
        diff_arguments+=("--exclude=$generated_path")
    done

    if diff "${diff_arguments[@]}" "$PROJECT_ROOT" "$STAGE_DIRECTORY"; then
        log INFO "staged Fossify source matches the current Dikciz source tree"
        return
    else
        diff_status=$?
    fi

    [[ "$diff_status" -eq 1 ]] || fail "could not compare the staged Fossify source"
    log INFO "staged Fossify source has the differences listed above"
}

verify_backup() {
    local backup_snapshot="$1"
    local diff_status

    if diff -qr --exclude=.git --exclude=.android-lab --exclude=.research_files --exclude=.testing --exclude=.gradle --exclude=.kotlin --exclude=build "$PROJECT_ROOT" "$backup_snapshot"; then
        log INFO "verified complete pre-refresh backup"
        return
    else
        diff_status=$?
    fi

    [[ "$diff_status" -eq 1 ]] || fail "could not verify the pre-refresh backup"
    fail "pre-refresh backup differs from the live Dikciz project"
}

refresh_project() {
    local backup_root
    local backup_snapshot
    local replaced_project
    local source_path
    local restore_path

    [[ "${DIKCIZ_UPSTREAM_REFRESH:-0}" == "$REFRESH_CONFIRMATION" ]] || fail "set DIKCIZ_UPSTREAM_REFRESH=1 to replace the live Dikciz source tree"
    validate_release_upgrade
    prepare_stage
    show_staged_delta

    backup_root="$(mktemp -d "$BACKUP_DIRECTORY/$BACKUP_DIRECTORY_PREFIX.XXXXXX")"
    backup_snapshot="$backup_root/snapshot"
    log INFO "creating pre-refresh source backup; Git metadata, lab state and scratch stay in place"
    mkdir --parents "$backup_snapshot"
    shopt -s dotglob nullglob
    for source_path in "$PROJECT_ROOT"/*; do
        case "$(basename "$source_path")" in
        .git | .android-lab | .research_files | .testing | .gradle | .kotlin | build) continue ;;
        esac
        cp --archive -- "$source_path" "$backup_snapshot/" || fail "could not create the source backup"
    done
    verify_backup "$backup_snapshot"

    replaced_project="$backup_root/replaced-project"
    mkdir --parents "$replaced_project"
    local -a moved_paths=()
    local -a installed_paths=()
    shopt -s dotglob nullglob
    for source_path in "$PROJECT_ROOT"/*; do
        case "$(basename "$source_path")" in
        .git | .android-lab | .research_files | .testing | .gradle | .kotlin | build) continue ;;
        esac
        if ! mv -- "$source_path" "$replaced_project/"; then
            for restore_path in "${moved_paths[@]}"; do
                mv -- "$replaced_project/$restore_path" "$PROJECT_ROOT/" || fail "restore failed; preserved source is in $replaced_project"
            done
            fail "could not move source into its verified backup"
        fi
        moved_paths+=("$(basename "$source_path")")
    done
    for source_path in "$STAGE_DIRECTORY"/*; do
        if ! mv -- "$source_path" "$PROJECT_ROOT/"; then
            for restore_path in "${installed_paths[@]}"; do
                mv -- "$PROJECT_ROOT/$restore_path" "$STAGE_DIRECTORY/" || fail "could not roll back staged source"
            done
            for restore_path in "${moved_paths[@]}"; do
                mv -- "$replaced_project/$restore_path" "$PROJECT_ROOT/" || fail "restore failed; preserved source is in $replaced_project"
            done
            fail "staged refresh failed; prior source restored"
        fi
        installed_paths+=("$(basename "$source_path")")
    done
    shopt -u dotglob nullglob
    log INFO "Fossify source refresh completed with a verified backup at $backup_root"
}

main() {
    local command="$1"
    local upstream_relative_path="$2"
    local backup_relative_path="${3:-$DEFAULT_BACKUP_DIRECTORY}"

    UPSTREAM_DIRECTORY="$(resolve_workspace_directory "$upstream_relative_path")"
    BACKUP_DIRECTORY="$(resolve_backup_directory "$backup_relative_path")"
    validate_upstream_tree
    if [[ "${DEBUG:-}" == 1 ]]; then
        log DEBUG "validated workspace-local Fossify source and backup paths"
    fi
    log INFO "preparing a Fossify source refresh stage"

    case "$command" in
    compare | dry-run)
        prepare_stage
        show_staged_delta
        ;;
    refresh)
        refresh_project
        ;;
    *)
        usage
        fail "unknown command: $command"
        ;;
    esac
}

[[ "$#" -ge 2 && "$#" -le 3 ]] || {
    usage
    exit 1
}

main "$@"
