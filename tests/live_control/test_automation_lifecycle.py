"""Automation source lifecycle checks on the isolated Android emulator."""

from . import *


def test_automation_screen_delivery_and_notification_listener_disable_recovery(
    websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    screen_configuration = automation_configuration(
        original,
        AUTOMATION_SCREEN_SOURCE,
        AUTOMATION_SCREEN_INITIAL_STATE,
        AUTOMATION_SCREEN_CAPABILITIES,
        AUTOMATION_PATCH_STATE_ACTIONS,
        [
            {
                "event": AUTOMATION_SCREEN_EVENT,
                "minimumIntervalMilliseconds": 0,
                "coalescingKey": AUTOMATION_SCREEN_COALESCING_KEY,
            },
        ],
    )
    notification_configuration = automation_configuration(
        original,
        AUTOMATION_NOTIFICATION_SOURCE,
        AUTOMATION_NOTIFICATION_INITIAL_STATE,
        AUTOMATION_NOTIFICATION_CAPABILITIES,
        AUTOMATION_PATCH_STATE_ACTIONS,
        [
            {
                "event": AUTOMATION_NOTIFICATION_EVENT,
                "minimumIntervalMilliseconds": 0,
                "coalescingKey": AUTOMATION_NOTIFICATION_COALESCING_KEY,
                "packages": [AUTOMATION_NOTIFICATION_SOURCE_PACKAGE],
            },
        ],
    )
    try:
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=screen_configuration,
        )
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
        device_command(f"input keyevent {AUTOMATION_SCREEN_TOGGLE_KEYCODE}")
        wait_for_automation_script_state(
            websocket_control,
            AUTOMATION_LIVE_SCRIPT_ID,
            "screenOn",
            False,
        )
        device_command(f"input keyevent {AUTOMATION_SCREEN_WAKE_KEYCODE}")
        wait_for_automation_script_state(
            websocket_control,
            AUTOMATION_LIVE_SCRIPT_ID,
            "screenOn",
            True,
        )

        device_command(f"cmd notification allow_listener {AUTOMATION_NOTIFICATION_COMPONENT}")
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=notification_configuration,
        )
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
        device_command(
            "cmd notification post "
            f"-t {AUTOMATION_NOTIFICATION_TITLE} {AUTOMATION_NOTIFICATION_UNAVAILABLE_TAG} "
            f"{AUTOMATION_NOTIFICATION_TEXT}",
        )
        wait_for_automation_script_state(
            websocket_control,
            AUTOMATION_LIVE_SCRIPT_ID,
            "hasTitle",
            False,
        )

        device_command(f"cmd notification disallow_listener {AUTOMATION_NOTIFICATION_COMPONENT}")
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=notification_configuration,
        )
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
        device_command(
            "cmd notification post "
            f"-t {AUTOMATION_NOTIFICATION_TITLE} {AUTOMATION_NOTIFICATION_UNAVAILABLE_TAG} "
            f"{AUTOMATION_NOTIFICATION_TEXT}",
        )
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
        updated = AUTOMATION.require_configuration(
            AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
        )
        script = next(
            item
            for item in updated["scripts"]
            if item["id"] == AUTOMATION_LIVE_SCRIPT_ID
        )
        assert script["state"]["hasTitle"] == AUTOMATION_NOTIFICATION_DISABLED_STATE
        assert script["state"]["hasText"] == AUTOMATION_NOTIFICATION_DISABLED_STATE
    finally:
        try:
            device_command(f"input keyevent {AUTOMATION_SCREEN_WAKE_KEYCODE}")
            device_command(f"cmd notification disallow_listener {AUTOMATION_NOTIFICATION_COMPONENT}")
        finally:
            AUTOMATION.request(
                websocket_control,
                AUTOMATION.TYPE_CONFIG_REPLACE,
                config=original,
            )


def test_automation_sensor_registration_replaces_removed_sensor_types(
    websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    acceleration_configuration = automation_configuration(
        original,
        AUTOMATION_SENSOR_RECONFIGURATION_SOURCE,
        AUTOMATION_SENSOR_RECONFIGURATION_INITIAL_STATE,
        AUTOMATION_SENSOR_RECONFIGURATION_CAPABILITIES,
        AUTOMATION_PATCH_STATE_ACTIONS,
        [
            {
                "event": AUTOMATION_SENSOR_RECONFIGURATION_EVENT,
                "minimumIntervalMilliseconds": AUTOMATION_SENSOR_DELIVERY_INTERVAL_MILLISECONDS,
                "coalescingKey": AUTOMATION_SENSOR_ACCELERATION_RECONFIGURATION_COALESCING_KEY,
                "sensorTypes": [AUTOMATION_SENSOR_ACCELERATION_TYPE],
                "samplingPeriodMicroseconds": 200_000,
            },
        ],
    )
    gyroscope_configuration = automation_configuration(
        original,
        AUTOMATION_SENSOR_RECONFIGURATION_SOURCE,
        AUTOMATION_SENSOR_RECONFIGURATION_INITIAL_STATE,
        AUTOMATION_SENSOR_RECONFIGURATION_CAPABILITIES,
        AUTOMATION_PATCH_STATE_ACTIONS,
        [
            {
                "event": AUTOMATION_SENSOR_RECONFIGURATION_EVENT,
                "minimumIntervalMilliseconds": AUTOMATION_SENSOR_DELIVERY_INTERVAL_MILLISECONDS,
                "coalescingKey": AUTOMATION_SENSOR_GYROSCOPE_RECONFIGURATION_COALESCING_KEY,
                "sensorTypes": [AUTOMATION_SENSOR_GYROSCOPE_TYPE],
                "samplingPeriodMicroseconds": 200_000,
            },
        ],
    )
    try:
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=acceleration_configuration,
        )
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
        emulator_sensor_set(AUTOMATION_SENSOR_ACCELERATION_NAME, AUTOMATION_SENSOR_VALUES)
        wait_for_automation_script_state(
            websocket_control,
            AUTOMATION_LIVE_SCRIPT_ID,
            "accelerationSeen",
            True,
        )

        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=gyroscope_configuration,
        )
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
        emulator_sensor_set(AUTOMATION_SENSOR_ACCELERATION_NAME, AUTOMATION_SENSOR_VALUES)
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
        current = AUTOMATION.require_configuration(
            AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
        )
        script = next(
            item
            for item in current["scripts"]
            if item["id"] == AUTOMATION_LIVE_SCRIPT_ID
        )
        assert script["state"] == AUTOMATION_SENSOR_RECONFIGURATION_INITIAL_STATE

        emulator_sensor_set(AUTOMATION_SENSOR_GYROSCOPE_NAME, AUTOMATION_SENSOR_GYROSCOPE_VALUES)
        updated = wait_for_automation_script_state(
            websocket_control,
            AUTOMATION_LIVE_SCRIPT_ID,
            "gyroscopeSeen",
            True,
        )
        script = next(
            item
            for item in updated["scripts"]
            if item["id"] == AUTOMATION_LIVE_SCRIPT_ID
        )
        assert "accelerationSeen" not in script["state"]
    finally:
        try:
            emulator_sensor_set(
                AUTOMATION_SENSOR_ACCELERATION_NAME,
                AUTOMATION_SENSOR_RESTORED_VALUES,
            )
            emulator_sensor_set(
                AUTOMATION_SENSOR_GYROSCOPE_NAME,
                AUTOMATION_SENSOR_RESTORED_VALUES,
            )
        finally:
            AUTOMATION.request(
                websocket_control,
                AUTOMATION.TYPE_CONFIG_REPLACE,
                config=original,
            )


def test_automation_alarm_reschedules_after_configuration_replacement(
    websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    first_configuration = automation_configuration(
        original,
        AUTOMATION_ALARM_SOURCE,
        AUTOMATION_ALARM_INITIAL_STATE,
        AUTOMATION_ALARM_CAPABILITIES,
        AUTOMATION_PATCH_STATE_ACTIONS,
        [
            {
                "event": AUTOMATION_ALARM_EVENT,
                "minimumIntervalMilliseconds": 0,
                "coalescingKey": AUTOMATION_ALARM_COALESCING_KEY,
                "alarmIntervalMilliseconds": AUTOMATION_ALARM_MINIMUM_INTERVAL_MILLISECONDS,
            },
        ],
    )
    rescheduled_configuration = automation_configuration(
        original,
        AUTOMATION_ALARM_RESCHEDULED_SOURCE,
        AUTOMATION_ALARM_INITIAL_STATE,
        AUTOMATION_ALARM_CAPABILITIES,
        AUTOMATION_PATCH_STATE_ACTIONS,
        [
            {
                "event": AUTOMATION_ALARM_EVENT,
                "minimumIntervalMilliseconds": 0,
                "coalescingKey": AUTOMATION_ALARM_COALESCING_KEY,
                "alarmIntervalMilliseconds": AUTOMATION_ALARM_MINIMUM_INTERVAL_MILLISECONDS,
            },
        ],
    )
    try:
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=first_configuration,
        )
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
        assert AUTOMATION_ACTION_ALARM_EVENT in device_command("dumpsys alarm")

        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=rescheduled_configuration,
        )
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
        assert AUTOMATION_ACTION_ALARM_EVENT in device_command("dumpsys alarm")
        wait_for_automation_script_state(
            websocket_control,
            AUTOMATION_LIVE_SCRIPT_ID,
            AUTOMATION_ALARM_DELIVERY_STATE_KEY,
            AUTOMATION_ALARM_RESCHEDULED_DELIVERY,
            timeout_seconds=AUTOMATION_ALARM_TIMEOUT_SECONDS,
        )
    finally:
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=original,
        )


def test_automation_cold_service_stops_without_an_accepted_configuration(
    websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    original_artifact = ARTIFACT_DIRECTORY / CONFIG_ROOT_ARTIFACT_NAME
    invalid_artifact = ARTIFACT_DIRECTORY / AUTOMATION_COLD_INVALID_CONFIG_ARTIFACT_NAME
    log_record_count = len(device_log_records())
    run_device_operation(
        "file-pull",
        DEVICE_FILE=CONFIG_DEVICE_PATH,
        ARTIFACT_FILE=CONFIG_ROOT_ARTIFACT_NAME,
    )
    invalid_artifact.write_text(CONFIG_INVALID_CONTENT, encoding="utf-8")
    try:
        run_device_operation(
            "file-push",
            DEVICE_FILE=CONFIG_DEVICE_PATH,
            ARTIFACT_FILE=AUTOMATION_COLD_INVALID_CONFIG_ARTIFACT_NAME,
        )
        assert AUTOMATION_SERVICE_COMPONENT not in device_command(AUTOMATION_SERVICE_QUERY_COMMAND)
        synced = AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_AUTOMATION_SERVICE_SYNC,
        )
        assert synced[AUTOMATION_SERVICE_SYNC_RESULT_KEY] is True
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
        assert any(
            record["event"] == AUTOMATION_SERVICE_CONFIGURATION_REJECTED_EVENT
            for record in device_log_records()[log_record_count:]
        )
        assert AUTOMATION_SERVICE_COMPONENT not in device_command(AUTOMATION_SERVICE_QUERY_COMMAND)
    finally:
        run_device_operation(
            "file-push",
            DEVICE_FILE=CONFIG_DEVICE_PATH,
            ARTIFACT_FILE=CONFIG_ROOT_ARTIFACT_NAME,
        )
        restore_system_home()
        restoration_control = wait_for_restarted_websocket_control()
        try:
            AUTOMATION.request(
                restoration_control,
                AUTOMATION.TYPE_CONFIG_REPLACE,
                config=original,
            )
        finally:
            restoration_control.close()


def test_automation_cold_service_loads_valid_public_configuration(
    websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    configured = automation_configuration(
        original,
        AUTOMATION_LIVE_SOURCE,
        AUTOMATION_LIVE_INITIAL_STATE,
        [AUTOMATION_BATTERY_EVENT],
        AUTOMATION_PATCH_STATE_ACTIONS,
        [
            {
                "event": AUTOMATION_BATTERY_EVENT,
                "minimumIntervalMilliseconds": 0,
                "coalescingKey": AUTOMATION_COLD_VALID_BATTERY_COALESCING_KEY,
            },
        ],
    )
    public_tree_files = (
        (CONFIG_DEVICE_PATH, AUTOMATION_COLD_VALID_CONFIG_ARTIFACT_NAME),
        (AUTOMATION_LIVE_POLICY_DEVICE_PATH, AUTOMATION_COLD_VALID_POLICY_ARTIFACT_NAME),
        (AUTOMATION_LIVE_SCRIPT_DEVICE_PATH, AUTOMATION_COLD_VALID_SCRIPT_ARTIFACT_NAME),
    )
    state_artifact = ARTIFACT_DIRECTORY / AUTOMATION_COLD_VALID_STATE_ARTIFACT_NAME
    try:
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=configured,
        )
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
        for device_file, artifact_file in public_tree_files:
            run_device_operation(
                "file-pull",
                DEVICE_FILE=device_file,
                ARTIFACT_FILE=artifact_file,
            )

        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=original,
        )
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
        assert AUTOMATION_SERVICE_COMPONENT not in device_command(AUTOMATION_SERVICE_QUERY_COMMAND)

        device_command(f"am start -W -n {SETTINGS_ACTIVITY_COMPONENT}")
        wait_for_foreground_application(COMMAND_SETTINGS_PACKAGE_QUERY)
        for device_file, artifact_file in public_tree_files[1:]:
            run_device_operation(
                "file-push",
                DEVICE_FILE=device_file,
                ARTIFACT_FILE=artifact_file,
            )
        run_device_operation(
            "file-push",
            DEVICE_FILE=CONFIG_DEVICE_PATH,
            ARTIFACT_FILE=AUTOMATION_COLD_VALID_CONFIG_ARTIFACT_NAME,
        )
        assert AUTOMATION_SERVICE_COMPONENT not in device_command(AUTOMATION_SERVICE_QUERY_COMMAND)

        synced = AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_AUTOMATION_SERVICE_SYNC,
        )
        assert synced[AUTOMATION_SERVICE_SYNC_RESULT_KEY] is True
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
        assert AUTOMATION_SERVICE_COMPONENT in device_command(AUTOMATION_SERVICE_QUERY_COMMAND)

        device_command(f"dumpsys battery set level {AUTOMATION_COLD_VALID_BATTERY_LEVEL}")
        deadline = time.monotonic() + CONFIGURATION_SAVE_TIMEOUT_SECONDS
        observed_value: Any = None
        while time.monotonic() < deadline:
            run_device_operation(
                "file-pull",
                DEVICE_FILE=AUTOMATION_LIVE_STATE_DEVICE_PATH,
                ARTIFACT_FILE=AUTOMATION_COLD_VALID_STATE_ARTIFACT_NAME,
            )
            state = json.loads(state_artifact.read_text(encoding="utf-8"))
            observed_value = state.get(AUTOMATION_LIVE_STATE_KEY)
            if observed_value == AUTOMATION_COLD_VALID_BATTERY_LEVEL:
                break
            time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
        else:
            raise AssertionError(
                "cold automation service did not persist its battery state "
                f"while Home was stopped: observed={observed_value!r}",
            )
    finally:
        try:
            device_command("dumpsys battery reset")
        finally:
            restore_system_home()
            restoration_control = wait_for_restarted_websocket_control()
            try:
                AUTOMATION.request(
                    restoration_control,
                    AUTOMATION.TYPE_CONFIG_REPLACE,
                    config=original,
                )
            finally:
                restoration_control.close()
