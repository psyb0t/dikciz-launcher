#!/bin/bash
set -euo pipefail

DIKCIZ_CONTROL_READY_TIMEOUT_SECONDS=60
DIKCIZ_DEBUG_PACKAGE=eu.psyb0t.dikciz.launcher.debug
DIKCIZ_PUBLIC_CONFIGURATION_DIRECTORY=/sdcard/Dikciz
TEST_CONFIGURATION_RELATIVE_PATH=tests/fixtures/dikciz-ui-fixture.json
DIKCIZ_TEST_RESET_PENDING=false
SHARED_FORWARD_PORT=19101
SHARED_MCP_PORT=19102
FORWARD_ARTIFACT_DIRECTORY=/artifacts
DIKCIZ_TEST_SELECTOR_PATTERN='^test_[A-Za-z0-9_]+$'

automation_smoke() {
    local -a mutation_arguments=()
    if ! compose ps --status running --services | grep -Fx forward >/dev/null; then
        log ERROR "Android lab forward is not running. Run make forward after starting Dikciz"
        exit 1
    fi
    if [[ "${DIKCIZ_AUTOMATION_MUTATE:-false}" == true ]]; then
        mutation_arguments=(--mutate)
    fi
    compose exec --no-TTY forward \
        python3 /work/scripts/test-dikciz-automation.py \
        --port "${ANDROID_LAB_FORWARD_HOST_PORT:-$DEFAULT_FORWARD_PORT}" \
        --expected-control-port "${ANDROID_LAB_FORWARD_DEVICE_PORT:-$DEFAULT_FORWARD_PORT}" \
        "${mutation_arguments[@]}" || {
        log ERROR "Dikciz automation smoke test failed"
        exit 1
    }
    log INFO "Dikciz automation smoke test passed through the isolated ADB forward"
}

mcp_smoke() {
    local -a mutation_arguments=()
    if ! compose ps --status running --services | grep -Fx forward >/dev/null; then
        log ERROR "Android lab forward is not running. Run make forward after starting Dikciz"
        exit 1
    fi
    if [[ "${DIKCIZ_MCP_MUTATE:-false}" == true ]]; then
        mutation_arguments=(--mutate)
    fi
    compose exec --no-TTY forward \
        python3 /work/scripts/test-dikciz-mcp.py \
        --port "${ANDROID_LAB_MCP_HOST_PORT:-$DEFAULT_MCP_FORWARD_PORT}" \
        --expected-control-port "${ANDROID_LAB_MCP_DEVICE_PORT:-$DEFAULT_MCP_FORWARD_PORT}" \
        "${mutation_arguments[@]}" || {
        log ERROR "Dikciz MCP smoke test failed"
        exit 1
    }
    log INFO "Dikciz MCP smoke test passed through the isolated ADB forward"
}

dikciz_test() {
    local interrupt_after_prepare pytest_exit_code test_log_file test_target
    local -a pytest_arguments
    interrupt_after_prepare="${DIKCIZ_TEST_INTERRUPT_AFTER_PREPARE:-false}"
    [[ "$interrupt_after_prepare" == true || "$interrupt_after_prepare" == false ]] || {
        log ERROR "DIKCIZ_TEST_INTERRUPT_AFTER_PREPARE must be true or false"
        exit 1
    }
    pytest_exit_code=0
    test_target=/work/tests/live_control
    test_log_file="$ARTIFACT_DIR/dikciz-test.log"
    # One line per test as it finishes. The JUnit report is only written when
    # the run ends, so a run that is interrupted or killed would otherwise
    # leave no record of which tests had already passed or failed.
    pytest_arguments=(
        -p no:cacheprovider
        --junitxml "$FORWARD_ARTIFACT_DIRECTORY/dikciz-test.xml"
        -v "$test_target"
    )
    if [[ -n "${DIKCIZ_TEST_SELECTOR:-}" ]]; then
        [[ "$DIKCIZ_TEST_SELECTOR" =~ $DIKCIZ_TEST_SELECTOR_PATTERN ]] || {
            log ERROR "DIKCIZ_TEST_SELECTOR must name one Dikciz pytest test"
            exit 1
        }
        pytest_arguments+=(-k "$DIKCIZ_TEST_SELECTOR")
    fi
    trap 'restore_dikciz_after_test "$?"' EXIT
    trap 'restore_dikciz_after_test 130' INT
    trap 'restore_dikciz_after_test 143' TERM
    prepare_dikciz_test_device
    if [[ "$interrupt_after_prepare" == true ]]; then
        log INFO "delivering fixed SIGINT to the Dikciz test runner after setup"
        kill -INT "$$"
    fi
    if ! compose ps --status running --services | grep -Fx forward >/dev/null; then
        log ERROR "Android lab forward is not running"
        exit 1
    fi
    if compose exec --no-TTY \
        --env ANDROID_LAB_INSTANCE=shared \
        --env DIKCIZ_TEST_IN_FORWARD=true \
        --env DIKCIZ_TEST_SELECTOR \
        --env DIKCIZ_GUIDE_CAPTURE \
        --env DIKCIZ_TEST_CONFIGURABLE_PROVIDER_APK \
        --env DIKCIZ_TEST_CONFIGURABLE_PROVIDER_PACKAGE \
        --env DIKCIZ_TEST_MEDIA_SESSION_SECONDARY_FIXTURE_APK \
        --env DIKCIZ_TEST_MEDIA_SESSION_SECONDARY_FIXTURE_PACKAGE \
        --env APK \
        --env PACKAGE_NAME \
        --env ANDROID_LAB_ARTIFACT_DIR="$FORWARD_ARTIFACT_DIRECTORY" \
        --env ANDROID_LAB_KEEP_ADB_SERVER=true \
        --env ANDROID_LAB_FORWARD_HOST_PORT="$SHARED_FORWARD_PORT" \
        --env ANDROID_LAB_MCP_HOST_PORT="$SHARED_MCP_PORT" \
        forward \
        python3 -m pytest "${pytest_arguments[@]}" 2>&1 | tee "$test_log_file"; then
        log INFO "Dikciz release UI tests passed through the shared emulator ADB forward, result saved at $test_log_file"
    else
        pytest_exit_code=$?
        log ERROR "Dikciz release UI tests failed, restoring the bundled Dikciz home before returning"
    fi
    if ((pytest_exit_code != 0)); then
        return "$pytest_exit_code"
    fi
}

restore_dikciz_after_test() {
    local exit_code="$1"
    trap - EXIT INT TERM
    if [[ "$DIKCIZ_TEST_RESET_PENDING" != true ]]; then
        exit "$exit_code"
    fi
    if reset_dikciz; then
        log INFO "shared emulator restored to the bundled Dikciz home after the test run"
    else
        log ERROR "could not restore the bundled Dikciz home after the test run"
        if ((exit_code == 0)); then
            exit_code=1
        fi
    fi
    exit "$exit_code"
}

prepare_dikciz_test_device() {
    local package_name
    [[ "$ANDROID_LAB_INSTANCE" == shared ]] || {
        log ERROR "Dikciz release UI tests require the shared Android lab emulator"
        exit 1
    }
    [[ "${DIKCIZ_TEST_FIXTURE:-}" == "$TEST_CONFIGURATION_RELATIVE_PATH" ]] || {
        log ERROR "DIKCIZ_TEST_FIXTURE must be the checked-in Dikciz UI fixture"
        exit 1
    }
    [[ -s "$REPOSITORY_DIR/$DIKCIZ_TEST_FIXTURE" ]] || {
        log ERROR "Dikciz UI fixture is missing"
        exit 1
    }
    package_name="${PACKAGE_NAME:-}"
    [[ "$package_name" =~ ^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$ ]] || {
        log ERROR "PACKAGE_NAME must be a dotted Android package name"
        exit 1
    }
    DIKCIZ_TEST_RESET_PENDING=true
    reset_dikciz_install
    run_device_operation home-role-set
    run_device_operation home-start
    forward
    wait_for_dikciz_control_planes
}

wait_for_dikciz_control_planes() {
    local automation_port automation_target_port deadline mcp_port mcp_target_port
    automation_port="${ANDROID_LAB_FORWARD_HOST_PORT:-$SHARED_FORWARD_PORT}"
    automation_target_port="${ANDROID_LAB_FORWARD_DEVICE_PORT:-$DEFAULT_FORWARD_PORT}"
    mcp_port="${ANDROID_LAB_MCP_HOST_PORT:-$SHARED_MCP_PORT}"
    mcp_target_port="${ANDROID_LAB_MCP_DEVICE_PORT:-$DEFAULT_MCP_FORWARD_PORT}"
    deadline=$((SECONDS + DIKCIZ_CONTROL_READY_TIMEOUT_SECONDS))
    while ((SECONDS < deadline)); do
        if compose exec --no-TTY forward \
            python3 /work/scripts/test-dikciz-automation.py \
            --port "$automation_port" \
            --expected-control-port "$automation_target_port" \
            --probe \
            >/dev/null 2>&1 &&
            compose exec --no-TTY forward \
                python3 /work/scripts/test-dikciz-mcp.py \
                --port "$mcp_port" \
                --expected-control-port "$mcp_target_port" \
                --probe \
                >/dev/null 2>&1; then
            log INFO "Dikciz control planes are ready on the shared emulator"
            return 0
        fi
        sleep 1
    done
    log ERROR "Dikciz control planes did not become reachable"
    return 1
}

wait_for_running_dikciz_control_planes() {
    if ! compose ps --status running --services | grep -Fx forward >/dev/null; then
        log ERROR "Android lab forward is not running. Run make dikciz-run first"
        return 1
    fi
    wait_for_dikciz_control_planes
}

grant_dikciz_storage_access() {
    local package_name
    package_name="${PACKAGE_NAME:-}"
    [[ "$package_name" == "$DIKCIZ_DEBUG_PACKAGE" ]] || {
        log ERROR "storage access only permits package=$DIKCIZ_DEBUG_PACKAGE"
        return 1
    }
    DEVICE_COMMAND="appops set --uid $package_name MANAGE_EXTERNAL_STORAGE allow" run_device_operation device-shell
    log INFO "Dikciz file access granted on the isolated emulator without clearing configuration"
}

reset_dikciz_install() {
    local package_name
    package_name="${PACKAGE_NAME:-}"
    [[ "$package_name" == "$DIKCIZ_DEBUG_PACKAGE" ]] || {
        log ERROR "Dikciz reset only permits package=$DIKCIZ_DEBUG_PACKAGE"
        return 1
    }
    start
    run_device_operation app-stop
    run_device_operation app-uninstall
    DEVICE_COMMAND="rm -rf $DIKCIZ_PUBLIC_CONFIGURATION_DIRECTORY" run_device_operation device-shell
    run_device_operation install
    grant_dikciz_storage_access
    log INFO "latest Dikciz debug APK installed with its public configuration cleared"
}

reset_dikciz() {
    reset_dikciz_install
    run_device_operation home-role-set
    run_device_operation home-start
    forward
    wait_for_dikciz_control_planes
    log INFO "shared emulator reset to the current Dikciz debug build"
}

android_lab_extension_command() {
    case "$1" in
    dikciz-storage-access) grant_dikciz_storage_access ;;
    dikciz-control-ready) wait_for_running_dikciz_control_planes ;;
    automation-smoke) automation_smoke ;;
    mcp-smoke) mcp_smoke ;;
    dikciz-test) dikciz_test ;;
    dikciz-reset) reset_dikciz ;;
    *)
        log ERROR "unsupported Dikciz lab command=$1"
        return 1
        ;;
    esac
}
