#!/bin/bash

set -euo pipefail

readonly DEVICE_OPERATION_SCRIPT=/opt/android-lab/scripts/lab-device.sh
readonly AUTOMATION_RESET_SCRIPT=/work/scripts/test-dikciz-automation.py
readonly DIKCIZ_DEBUG_PACKAGE=eu.psyb0t.dikciz.launcher.debug
readonly DIKCIZ_HOME_ACTIVITY=org.fossify.home.dikciz.DikcizHomeActivity
readonly DIKCIZ_HOME_COMPONENT="$DIKCIZ_DEBUG_PACKAGE/$DIKCIZ_HOME_ACTIVITY"
readonly DIKCIZ_TEST_FIXTURE=/work/tests/fixtures/dikciz-ui-fixture.json
readonly DIKCIZ_TEST_DIRECTORY=/work/tests/live_control
readonly DIKCIZ_TEST_PATTERN='^test_[A-Za-z0-9_]+$'
readonly CLEAR_CONFIGURATION_ATTEMPTS=3
readonly CLEAR_CONFIGURATION_RETRY_SECONDS=1
readonly CONFIGURATION_DIRECTORY=/sdcard/Dikciz
readonly CONTROL_PLANE_CONNECTION_TIMEOUT_SECONDS=1
readonly CONTROL_PLANE_READY_TIMEOUT_SECONDS=15
readonly CONTROL_PLANE_RETRY_SECONDS=1
readonly DEVICE_LOOPBACK_HOST=127.0.0.1
readonly BUNDLED_CONFIGURATION_RESET_TIMEOUT_SECONDS=30
readonly STALE_CONFIGURATION_BACKUP_DIRECTORY=/sdcard/Dikciz.__android_lab_real_test_backup
readonly REAL_ARTIFACT_DIRECTORY=/work/.android-lab/real/artifacts
readonly REAL_DEVICE_OPERATION_LOG_FILE="$REAL_ARTIFACT_DIRECTORY/lab-device.log"
readonly TEST_AUTOMATION_DEVICE_PORT=19001
readonly TEST_AUTOMATION_HOST_PORT=19101
readonly TEST_MCP_DEVICE_PORT=19002
readonly TEST_MCP_HOST_PORT=19002
readonly REAL_STORAGE_ACCESS_SETTINGS_ACTION=android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
readonly REAL_STORAGE_ACCESS_SETTINGS_PACKAGE=com.android.settings
readonly REAL_STORAGE_ACCESS_SWITCH_CLASS=android.widget.Switch
readonly HOME_ROLE_PROMPT_CANCEL_BUTTON_RESOURCE_ID=android:id/button2
readonly HOME_ROLE_PROMPT_TITLE_RESOURCE_ID=com.android.permissioncontroller:id/title
readonly HOME_ROLE_PROMPT_TITLE_SUFFIX='as your default home app?'
readonly REAL_STORAGE_ACCESS_TIMEOUT_SECONDS=30
readonly REAL_STORAGE_ACCESS_UI_TIMEOUT_SECONDS=15
readonly REAL_STORAGE_ACCESS_RETRY_SECONDS=1
readonly DEVICE_ADMIN_SETTINGS_ACTIVITY_ACTION=android.settings.SETTINGS
readonly DEVICE_ADMIN_SETTINGS_PACKAGE=com.android.settings
readonly DEVICE_ADMIN_SETTINGS_SEARCH_X=600
readonly DEVICE_ADMIN_SETTINGS_SEARCH_Y=518
readonly DEVICE_ADMIN_SETTINGS_RESULT_X=540
readonly DEVICE_ADMIN_SETTINGS_RESULT_Y=536
readonly DEVICE_ADMIN_SETTINGS_QUERY='Device%sadmin%sapps'
readonly DEVICE_ADMIN_SETTINGS_SETTLE_SECONDS=1
readonly DEVICE_ADMIN_SETTINGS_SCREENSHOT_FILE="$REAL_ARTIFACT_DIRECTORY/device-admin-settings.png"
readonly DEVICE_ADMIN_SETTINGS_VERIFICATION_FIELD_RESOURCE_ID=com.android.settings:id/edittext_view
readonly DEVICE_ADMIN_SETTINGS_CANCEL_BUTTON_RESOURCE_ID=android:id/button2
readonly DEVICE_ADMIN_SETTINGS_LAYOUT_FILE="$REAL_ARTIFACT_DIRECTORY/device-admin-settings-layout.json"
readonly REAL_STORAGE_ACCESS_LAYOUT_FILE="$REAL_ARTIFACT_DIRECTORY/all-files-access-layout.json"
readonly REAL_UIAUTOMATOR_WINDOW_FILE="$REAL_ARTIFACT_DIRECTORY/window.xml"
readonly REAL_DEVICE_SCREENSHOT_FILE="$REAL_ARTIFACT_DIRECTORY/screenshot.png"
readonly REAL_DEVICE_INSPECTION_SCREENSHOT_FILE="$REAL_ARTIFACT_DIRECTORY/current-phone-screen.png"
readonly FIRST_LAUNCH_STORAGE_ACCESS_SCREENSHOT_FILE="$REAL_ARTIFACT_DIRECTORY/first-launch-storage-access.png"
readonly LATEST_LAUNCHER_SCREENSHOT_FILE="$REAL_ARTIFACT_DIRECTORY/latest-launcher-state.png"
readonly POST_STORAGE_ACCESS_BUNDLED_SCREENSHOT_FILE="$REAL_ARTIFACT_DIRECTORY/post-storage-access-bundled.png"
readonly DIKCIZ_PRIVATE_STATE_READY_TIMEOUT_SECONDS=30
readonly DIKCIZ_PRIVATE_STATE_RETRY_SECONDS=1
readonly UIAUTOMATOR_TO_JSON_SCRIPT=/opt/android-lab/scripts/uiautomator_to_json.py

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

on_error() {
    local exit_code=$?
    trap - ERR
    log ERROR "command failed exit=${exit_code}"
    exit "$exit_code"
}

trap on_error ERR

LOG_FILE="${LOG_FILE:-/tmp/android-lab-dikciz-real-test.log}"
exec 3>&1
exec > >(tee -a "$LOG_FILE" >&3) 2> >(tee -a "$LOG_FILE" >&2)

LATEST_INSTALL_READY=false
AUTOMATION_CONTROL_FORWARD_CREATED=false
MCP_CONTROL_FORWARD_CREATED=false
TEST_RESET_ONLY="${DIKCIZ_TEST_RESET_ONLY:-false}"
TEST_PROVIDER_INVENTORY_ONLY="${DIKCIZ_TEST_PROVIDER_INVENTORY_ONLY:-false}"
TEST_DEVICE_ADMIN_SETTINGS_ONLY="${DIKCIZ_TEST_DEVICE_ADMIN_SETTINGS_ONLY:-false}"
TEST_INSPECT_ONLY="${DIKCIZ_TEST_INSPECT_ONLY:-false}"
CONFIGURABLE_PROVIDER_APK="${DIKCIZ_CONFIGURABLE_PROVIDER_APK:-}"
CONFIGURABLE_PROVIDER_INSTALLED=false
CONFIGURABLE_PROVIDER_PACKAGE="${DIKCIZ_CONFIGURABLE_PROVIDER_PACKAGE:-}"
readonly CONFIGURABLE_PROVIDER_EXPECTED_APK='tests/configurable-widget-provider/app/build/outputs/apk/debug/app-debug.apk'
readonly CONFIGURABLE_PROVIDER_EXPECTED_PACKAGE='eu.psyb0t.dikciz.fixture'

run_device_operation() {
    LOG_FILE="$REAL_DEVICE_OPERATION_LOG_FILE" bash "$DEVICE_OPERATION_SCRIPT" "$@"
}

run_device_shell() {
    DEVICE_COMMAND="$1" run_device_operation device-shell
}

clear_dikciz_configuration() {
    local attempt
    for ((attempt = 1; attempt <= CLEAR_CONFIGURATION_ATTEMPTS; attempt++)); do
        if run_device_shell "rm -rf $CONFIGURATION_DIRECTORY $STALE_CONFIGURATION_BACKUP_DIRECTORY && test ! -e $CONFIGURATION_DIRECTORY && test ! -e $STALE_CONFIGURATION_BACKUP_DIRECTORY"; then
            return 0
        fi
        log WARN "could not clear Dikciz configuration attempt=${attempt}"
        run_device_operation app-stop
        sleep "$CLEAR_CONFIGURATION_RETRY_SECONDS"
    done
    log ERROR "could not clear Dikciz configuration"
    return 1
}

validate_real_test_environment() {
    [[ "${TEST_REAL:-}" == 1 ]] || {
        log ERROR "TEST_REAL must be 1 for the physical-device test"
        exit 1
    }
    [[ "${TEST_DEVIVE_ID:-}" =~ ^[A-Za-z0-9._:-]+$ ]] || {
        log ERROR "TEST_DEVIVE_ID must contain one configured ADB serial"
        exit 1
    }
    case "$TEST_RESET_ONLY" in
    true | false) ;;
    *)
        log ERROR "DIKCIZ_TEST_RESET_ONLY must be true or false"
        exit 1
        ;;
    esac
    case "$TEST_PROVIDER_INVENTORY_ONLY" in
    true | false) ;;
    *)
        log ERROR "DIKCIZ_TEST_PROVIDER_INVENTORY_ONLY must be true or false"
        exit 1
        ;;
    esac
    case "$TEST_DEVICE_ADMIN_SETTINGS_ONLY" in
    true | false) ;;
    *)
        log ERROR "DIKCIZ_TEST_DEVICE_ADMIN_SETTINGS_ONLY must be true or false"
        exit 1
        ;;
    esac
    case "$TEST_INSPECT_ONLY" in
    true | false) ;;
    *)
        log ERROR "DIKCIZ_TEST_INSPECT_ONLY must be true or false"
        exit 1
        ;;
    esac
    if [[ "$TEST_PROVIDER_INVENTORY_ONLY" == true || "$TEST_DEVICE_ADMIN_SETTINGS_ONLY" == true || "$TEST_INSPECT_ONLY" == true ]]; then
        return 0
    fi
    if [[ -n "$CONFIGURABLE_PROVIDER_APK$CONFIGURABLE_PROVIDER_PACKAGE" ]]; then
        [[ "$CONFIGURABLE_PROVIDER_APK" == "$CONFIGURABLE_PROVIDER_EXPECTED_APK" && -s "$CONFIGURABLE_PROVIDER_APK" ]] || {
            log ERROR "DIKCIZ_CONFIGURABLE_PROVIDER_APK must be the built fixed configurable-provider fixture APK"
            exit 1
        }
        [[ "$CONFIGURABLE_PROVIDER_PACKAGE" == "$CONFIGURABLE_PROVIDER_EXPECTED_PACKAGE" ]] || {
            log ERROR "DIKCIZ_CONFIGURABLE_PROVIDER_PACKAGE must be the fixed configurable-provider fixture package"
            exit 1
        }
    fi
    [[ -s "${APK:-}" && "${APK:-}" != /* && "${APK:-}" == *.apk ]] || {
        log ERROR "APK must be a built relative APK path"
        exit 1
    }
    [[ "${PACKAGE_NAME:-}" == "$DIKCIZ_DEBUG_PACKAGE" ]] || {
        log ERROR "physical-device test must use the Dikciz debug package"
        exit 1
    }
    if [[ "$TEST_RESET_ONLY" == false && "$TEST_PROVIDER_INVENTORY_ONLY" == false && "$TEST_INSPECT_ONLY" == false ]]; then
        [[ "${DIKCIZ_TEST_SELECTOR:-}" =~ $DIKCIZ_TEST_PATTERN ]] || {
            log ERROR "DIKCIZ_TEST_SELECTOR must name one focused Dikciz pytest test"
            exit 1
        }
    fi
    [[ -s "$AUTOMATION_RESET_SCRIPT" && -s "$DIKCIZ_TEST_FIXTURE" && -d "$DIKCIZ_TEST_DIRECTORY" ]] || {
        log ERROR "Dikciz test assets are unavailable"
        exit 1
    }
}

device_admin_verification_gate_is_visible() {
    run_device_operation uiautomator-dump
    rg --quiet --fixed-strings \
        "resource-id=\"$DEVICE_ADMIN_SETTINGS_VERIFICATION_FIELD_RESOURCE_ID\"" \
        "$REAL_UIAUTOMATOR_WINDOW_FILE"
}

dismiss_device_admin_verification_gate() {
    local -a cancel_centers=()
    local cancel_x cancel_y
    python3 "$UIAUTOMATOR_TO_JSON_SCRIPT" "$REAL_UIAUTOMATOR_WINDOW_FILE" \
        >"$DEVICE_ADMIN_SETTINGS_LAYOUT_FILE"
    mapfile -t cancel_centers < <(
        jq -r \
            --arg cancel_button_resource_id "$DEVICE_ADMIN_SETTINGS_CANCEL_BUTTON_RESOURCE_ID" \
            '.elements[] | select(
				.resourceId == $cancel_button_resource_id and
				.center.x != null and
				.center.y != null
			) | "\(.center.x) \(.center.y)"' \
            "$DEVICE_ADMIN_SETTINGS_LAYOUT_FILE"
    )
    [[ "${#cancel_centers[@]}" -eq 1 ]] || {
        log ERROR "could not find the Device-admin verification Cancel button"
        return 1
    }
    read -r cancel_x cancel_y <<<"${cancel_centers[0]}"
    [[ "$cancel_x" =~ ^[0-9]+$ && "$cancel_y" =~ ^[0-9]+$ ]] || {
        log ERROR "Device-admin verification Cancel button did not provide numeric coordinates"
        return 1
    }
    run_device_shell "input tap $cancel_x $cancel_y"
    sleep "$DEVICE_ADMIN_SETTINGS_SETTLE_SECONDS"
    if device_admin_verification_gate_is_visible; then
        log ERROR "ColorOS left the Device-admin verification gate open after Cancel"
        return 1
    fi
}

stop_at_device_admin_verification_gate() {
    capture_device_screenshot \
        "$DEVICE_ADMIN_SETTINGS_SCREENSHOT_FILE" \
        "Device-admin verification gate captured"
    dismiss_device_admin_verification_gate
    log ERROR "ColorOS requires a user-completed visual verification before Device admin Settings can open"
    return 1
}

open_dikciz_device_admin_settings() {
    mkdir --parents "$REAL_ARTIFACT_DIRECTORY"
    run_device_operation unlock
    if device_admin_verification_gate_is_visible; then
        dismiss_device_admin_verification_gate
    fi
    run_device_shell "am start -a $DEVICE_ADMIN_SETTINGS_ACTIVITY_ACTION -p $DEVICE_ADMIN_SETTINGS_PACKAGE"
    sleep "$DEVICE_ADMIN_SETTINGS_SETTLE_SECONDS"
    run_device_shell "input tap $DEVICE_ADMIN_SETTINGS_SEARCH_X $DEVICE_ADMIN_SETTINGS_SEARCH_Y"
    sleep "$DEVICE_ADMIN_SETTINGS_SETTLE_SECONDS"
    if device_admin_verification_gate_is_visible; then
        stop_at_device_admin_verification_gate
        return 1
    fi
    run_device_shell "input text $DEVICE_ADMIN_SETTINGS_QUERY"
    sleep "$DEVICE_ADMIN_SETTINGS_SETTLE_SECONDS"
    run_device_shell "input tap $DEVICE_ADMIN_SETTINGS_RESULT_X $DEVICE_ADMIN_SETTINGS_RESULT_Y"
    sleep "$DEVICE_ADMIN_SETTINGS_SETTLE_SECONDS"
    if device_admin_verification_gate_is_visible; then
        stop_at_device_admin_verification_gate
        return 1
    fi
    run_device_operation uiautomator-dump
    capture_device_screenshot "$DEVICE_ADMIN_SETTINGS_SCREENSHOT_FILE" "Dikciz Device admin Settings page captured"
}

has_real_storage_access() {
    run_device_shell "appops get --uid $DIKCIZ_DEBUG_PACKAGE MANAGE_EXTERNAL_STORAGE" |
        rg --quiet --fixed-strings 'Uid mode: MANAGE_EXTERNAL_STORAGE: allow'
}

find_real_storage_access_switch() {
    local expected_checked="$1"
    local -a switch_centers=()
    [[ "$expected_checked" == true || "$expected_checked" == false ]] || {
        log ERROR "expected storage-access switch state must be true or false"
        return 1
    }
    timeout "$REAL_STORAGE_ACCESS_UI_TIMEOUT_SECONDS" \
        bash "$DEVICE_OPERATION_SCRIPT" uiautomator-dump
    python3 "$UIAUTOMATOR_TO_JSON_SCRIPT" "$REAL_UIAUTOMATOR_WINDOW_FILE" \
        >"$REAL_STORAGE_ACCESS_LAYOUT_FILE"
    mapfile -t switch_centers < <(
        jq -r \
            --arg switch_class "$REAL_STORAGE_ACCESS_SWITCH_CLASS" \
            --argjson expected_checked "$expected_checked" \
            '.elements[] | select(
				.className == $switch_class and
				(.interactions | index("checkable")) and
				((((.state | index("checked")) != null) == $expected_checked)) and
				.center.x != null and
				.center.y != null
			) | "\(.center.x) \(.center.y)"' \
            "$REAL_STORAGE_ACCESS_LAYOUT_FILE"
    )
    [[ "${#switch_centers[@]}" -eq 1 ]] || return 1
    printf '%s\n' "${switch_centers[0]}"
}

dismiss_real_home_role_prompt_if_visible() {
    local -a cancel_centers=()
    mapfile -t cancel_centers < <(
        jq -r \
            --arg cancel_button_resource_id "$HOME_ROLE_PROMPT_CANCEL_BUTTON_RESOURCE_ID" \
            --arg title_resource_id "$HOME_ROLE_PROMPT_TITLE_RESOURCE_ID" \
            --arg title_suffix "$HOME_ROLE_PROMPT_TITLE_SUFFIX" \
            'if ([
				.elements[] | select(
					.resourceId == $title_resource_id and
					((.text // "") | endswith($title_suffix))
				)
			] | length) == 1 then
				.elements[] | select(
					.resourceId == $cancel_button_resource_id and
					.text == "Cancel" and
					.center.x != null and
					.center.y != null
				) | "\(.center.x) \(.center.y)"
			else empty end' \
            "$REAL_STORAGE_ACCESS_LAYOUT_FILE"
    )
    [[ "${#cancel_centers[@]}" -eq 1 ]] || return 1
    local cancel_x cancel_y
    read -r cancel_x cancel_y <<<"${cancel_centers[0]}"
    [[ "$cancel_x" =~ ^[0-9]+$ && "$cancel_y" =~ ^[0-9]+$ ]] || return 1
    run_device_shell "input tap $cancel_x $cancel_y"
    log WARN "dismissed an Android Home-role prompt without changing the current launcher"
}

grant_real_storage_access() {
    set_real_storage_access true
}

revoke_real_storage_access() {
    set_real_storage_access false
}

set_real_storage_access() {
    local expected_access="$1"
    local deadline switch_center switch_checked switch_x switch_y
    [[ "$expected_access" == true || "$expected_access" == false ]] || {
        log ERROR "expected Dikciz storage access must be true or false"
        return 1
    }
    if [[ "$expected_access" == true ]] && has_real_storage_access; then
        return 0
    fi
    if [[ "$expected_access" == false ]] && ! has_real_storage_access; then
        return 0
    fi
    if [[ "$expected_access" == true ]]; then
        switch_checked=false
    else
        switch_checked=true
    fi
    run_device_shell "am start -a $REAL_STORAGE_ACCESS_SETTINGS_ACTION -d package:$DIKCIZ_DEBUG_PACKAGE -p $REAL_STORAGE_ACCESS_SETTINGS_PACKAGE"
    deadline=$((SECONDS + REAL_STORAGE_ACCESS_TIMEOUT_SECONDS))
    while ((SECONDS < deadline)); do
        if switch_center="$(find_real_storage_access_switch "$switch_checked")"; then
            read -r switch_x switch_y <<<"$switch_center"
            if [[ "$switch_x" =~ ^[0-9]+$ && "$switch_y" =~ ^[0-9]+$ ]]; then
                run_device_shell "input tap $switch_x $switch_y"
                if [[ "$expected_access" == true ]] && has_real_storage_access; then
                    log INFO "Dikciz storage access granted through Android Settings"
                    return 0
                fi
                if [[ "$expected_access" == false ]] && ! has_real_storage_access; then
                    log INFO "Dikciz storage access revoked through Android Settings"
                    return 0
                fi
            fi
        elif dismiss_real_home_role_prompt_if_visible; then
            run_device_shell "am start -a $REAL_STORAGE_ACCESS_SETTINGS_ACTION -d package:$DIKCIZ_DEBUG_PACKAGE -p $REAL_STORAGE_ACCESS_SETTINGS_PACKAGE"
        fi
        sleep "$REAL_STORAGE_ACCESS_RETRY_SECONDS"
    done
    log ERROR "could not set Dikciz storage access through Android Settings"
    return 1
}

capture_first_launch_storage_access_preview() {
    if has_real_storage_access; then
        log ERROR "fresh Dikciz install unexpectedly has storage access before the first launch"
        return 1
    fi
    run_device_operation home-start
    capture_device_screenshot "$FIRST_LAUNCH_STORAGE_ACCESS_SCREENSHOT_FILE" "first-launch storage-access preview captured"
}

capture_post_storage_access_bundled_preview() {
    capture_device_screenshot "$POST_STORAGE_ACCESS_BUNDLED_SCREENSHOT_FILE" "post-storage-access bundled configuration preview captured"
}

capture_device_screenshot() {
    local target_file success_message
    target_file="$1"
    success_message="$2"
    run_device_operation screenshot
    install --mode=0600 "$REAL_DEVICE_SCREENSHOT_FILE" "$target_file"
    log INFO "$success_message"
}

inspect_real_device() {
    local active_admins
    mkdir --parents "$REAL_ARTIFACT_DIRECTORY"
    run_device_operation unlock
    run_device_operation uiautomator-dump
    capture_device_screenshot \
        "$REAL_DEVICE_INSPECTION_SCREENSHOT_FILE" \
        "configured physical device screen captured without changing Dikciz"
    active_admins="$(run_device_shell 'dumpsys device_policy')" || {
        log ERROR "could not read configured physical device Device-admin state"
        return 1
    }
    if rg --quiet --fixed-strings "$DIKCIZ_DEBUG_PACKAGE" <<<"$active_admins"; then
        log INFO "Dikciz Device Admin remains active on the configured physical device"
        return 0
    fi
    log INFO "Dikciz Device Admin is inactive on the configured physical device"
}

dikciz_private_state_is_ready() {
    run_device_shell "run-as $DIKCIZ_DEBUG_PACKAGE true" >/dev/null 2>&1 # Intentional: a missing sandbox is an expected transient state immediately after APK installation.
}

wait_for_dikciz_private_state() {
    local deadline
    deadline=$((SECONDS + DIKCIZ_PRIVATE_STATE_READY_TIMEOUT_SECONDS))
    while ((SECONDS < deadline)); do
        if dikciz_private_state_is_ready; then
            log INFO "Dikciz standard private data directory is ready"
            return 0
        fi
        sleep "$DIKCIZ_PRIVATE_STATE_RETRY_SECONDS"
    done
    log ERROR "Dikciz standard private data directory did not become ready after APK installation"
    return 1
}

refresh_dikciz_home_role() {
    ANDROID_LAB_FORCE_HOME_ROLE_REASSIGN=true run_device_operation home-role-set
}

wait_for_dikciz_home_activity() {
    local deadline home_activities package_paths
    if ! package_paths="$(run_device_shell "pm path --user 0 $DIKCIZ_DEBUG_PACKAGE")"; then
        log ERROR "Dikciz package is absent after APK replacement"
        return 1
    fi
    deadline=$((SECONDS + DIKCIZ_PRIVATE_STATE_READY_TIMEOUT_SECONDS))
    while ((SECONDS < deadline)); do
        if home_activities="$(run_device_shell "cmd package query-activities --brief --user 0 -a android.intent.action.MAIN -c android.intent.category.HOME")" &&
            rg --quiet --fixed-strings "$DIKCIZ_HOME_COMPONENT" <<<"$home_activities"; then
            log INFO "Dikciz Home activity published after APK replacement"
            return 0
        fi
        sleep "$DIKCIZ_PRIVATE_STATE_RETRY_SECONDS"
    done
    log ERROR "Dikciz package is installed but its Home activity did not become available after APK replacement paths=${package_paths//$'\n'/,}"
    return 1
}

initialize_dikciz_private_state() {
    if ! refresh_dikciz_home_role; then
        log ERROR "could not assign Dikciz the Home role before private data initialization"
        return 1
    fi
    if ! wait_for_dikciz_home_activity; then
        log ERROR "Dikciz Home activity is not published after APK replacement"
        return 1
    fi
    if ! run_device_operation home-start; then
        log ERROR "could not start Dikciz through Android Home to initialize its private data directory"
        return 1
    fi
    if ! wait_for_dikciz_private_state; then
        run_device_operation app-stop || log WARN "could not stop Dikciz after private data initialization failed"
        return 1
    fi
    run_device_operation app-stop
}

control_plane_is_reachable() {
    local port="$1"
    timeout "$CONTROL_PLANE_CONNECTION_TIMEOUT_SECONDS" \
        bash -c "</dev/tcp/$DEVICE_LOOPBACK_HOST/$port" >/dev/null 2>&1 # Intentional: failed probes are expected until the launcher opens its local control port.
}

wait_for_control_planes() {
    local deadline
    deadline=$((SECONDS + CONTROL_PLANE_READY_TIMEOUT_SECONDS))
    while ((SECONDS < deadline)); do
        if control_plane_is_reachable "$TEST_AUTOMATION_HOST_PORT" && control_plane_is_reachable "$TEST_MCP_HOST_PORT"; then
            log INFO "physical-device control planes are ready"
            return 0
        fi
        sleep "$CONTROL_PLANE_RETRY_SECONDS"
    done
    log ERROR "physical-device control planes did not become ready before timeout"
    return 1
}

reset_to_bundled_configuration() {
    if ! timeout "$BUNDLED_CONFIGURATION_RESET_TIMEOUT_SECONDS" \
        python3 "$AUTOMATION_RESET_SCRIPT" \
        --host "$DEVICE_LOOPBACK_HOST" \
        --port "$TEST_AUTOMATION_HOST_PORT" \
        --expected-control-port "$TEST_AUTOMATION_DEVICE_PORT" \
        --reset-to-bundled; then
        log ERROR "could not reset Dikciz to its bundled configuration through the local control plane"
        return 1
    fi
    log INFO "Dikciz bundled configuration restored through its local control plane"
}

prepare_real_device() {
    mkdir --parents "$REAL_ARTIFACT_DIRECTORY"
    run_device_operation unlock
    run_device_operation app-stop
    run_device_operation app-uninstall
    clear_dikciz_configuration
    run_device_operation install
    initialize_dikciz_private_state
    LATEST_INSTALL_READY=true
    log INFO "current Dikciz APK installed and its test state cleared"
    run_device_operation home-role-set
    capture_first_launch_storage_access_preview
    grant_real_storage_access
    run_device_operation home-start
    capture_post_storage_access_bundled_preview
    ANDROID_LAB_FORWARD_HOST_PORT="$TEST_AUTOMATION_HOST_PORT" \
        ANDROID_LAB_FORWARD_DEVICE_PORT="$TEST_AUTOMATION_DEVICE_PORT" \
        run_device_operation control-forward
    AUTOMATION_CONTROL_FORWARD_CREATED=true
    ANDROID_LAB_FORWARD_HOST_PORT="$TEST_MCP_HOST_PORT" \
        ANDROID_LAB_FORWARD_DEVICE_PORT="$TEST_MCP_DEVICE_PORT" \
        run_device_operation control-forward
    MCP_CONTROL_FORWARD_CREATED=true
    wait_for_control_planes
}

install_configurable_provider_fixture() {
    [[ -n "$CONFIGURABLE_PROVIDER_APK" ]] || return 0
    APK="$CONFIGURABLE_PROVIDER_APK" \
        PACKAGE_NAME="$CONFIGURABLE_PROVIDER_PACKAGE" \
        run_device_operation install
    CONFIGURABLE_PROVIDER_INSTALLED=true
    log INFO "fixed configurable-provider fixture installed on the configured physical device"
}

remove_configurable_provider_fixture() {
    [[ "$CONFIGURABLE_PROVIDER_INSTALLED" == true ]] || return 0
    PACKAGE_NAME="$CONFIGURABLE_PROVIDER_PACKAGE" run_device_operation app-uninstall
    CONFIGURABLE_PROVIDER_INSTALLED=false
    log INFO "fixed configurable-provider fixture removed from the configured physical device"
}

leave_latest_launcher_state() {
    run_device_operation unlock
    if ! reset_to_bundled_configuration; then
        return 1
    fi
    run_device_operation home-role-set
    run_device_operation home-start
    capture_device_screenshot "$LATEST_LAUNCHER_SCREENSHOT_FILE" "latest Dikciz state preview captured"
    log INFO "latest Dikciz build and bundled configuration left on physical device"
}

cleanup() {
    local exit_code=$?
    trap - EXIT
    if [[ "$LATEST_INSTALL_READY" == true ]] && ! leave_latest_launcher_state; then
        log ERROR "could not leave the latest Dikciz state on the physical device"
        exit_code=1
    fi
    if ! remove_configurable_provider_fixture; then
        log ERROR "could not remove the fixed configurable-provider fixture"
        exit_code=1
    fi
    if [[ "$AUTOMATION_CONTROL_FORWARD_CREATED" == true ]]; then
        ANDROID_LAB_FORWARD_HOST_PORT="$TEST_AUTOMATION_HOST_PORT" \
            ANDROID_LAB_FORWARD_DEVICE_PORT="$TEST_AUTOMATION_DEVICE_PORT" \
            run_device_operation control-unforward >/dev/null 2>&1 || log WARN "could not remove the physical-device control forward"
    fi
    if [[ "$MCP_CONTROL_FORWARD_CREATED" == true ]]; then
        ANDROID_LAB_FORWARD_HOST_PORT="$TEST_MCP_HOST_PORT" \
            ANDROID_LAB_FORWARD_DEVICE_PORT="$TEST_MCP_DEVICE_PORT" \
            run_device_operation control-unforward >/dev/null 2>&1 || log WARN "could not remove the physical-device MCP forward"
    fi
    exit "$exit_code"
}

trap cleanup EXIT

validate_real_test_environment
if [[ "$TEST_PROVIDER_INVENTORY_ONLY" == true ]]; then
    run_device_operation provider-inventory
    exit 0
fi
if [[ "$TEST_INSPECT_ONLY" == true ]]; then
    inspect_real_device
    exit 0
fi
if [[ "$TEST_DEVICE_ADMIN_SETTINGS_ONLY" == true ]]; then
    open_dikciz_device_admin_settings
    exit 0
fi
prepare_real_device
if [[ "$TEST_RESET_ONLY" == true ]]; then
    log INFO "configured physical device reset to the latest Dikciz build"
    exit 0
fi
install_configurable_provider_fixture
ANDROID_LAB_ARTIFACT_DIR="$REAL_ARTIFACT_DIRECTORY" \
    ANDROID_LAB_FORWARD_HOST_PORT="$TEST_AUTOMATION_HOST_PORT" \
    ANDROID_LAB_MCP_HOST_PORT="$TEST_MCP_HOST_PORT" \
    DIKCIZ_TEST_CONFIGURABLE_PROVIDER_PACKAGE="$CONFIGURABLE_PROVIDER_PACKAGE" \
    TEST_REAL=1 \
    python3 -m pytest -p no:cacheprovider -q "$DIKCIZ_TEST_DIRECTORY" -k "$DIKCIZ_TEST_SELECTOR"
log INFO "focused Dikciz test passed on the configured physical device"
