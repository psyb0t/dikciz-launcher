"""Automation sources, policy enforcement, and emulator injection behavior."""

from . import *


def wait_for_injected_automation_sensor_state(
    connection: socket.socket,
    script_id: str,
    sensor_name: str,
    sensor_values: str,
    state_key: str,
    expected_value: Any,
) -> dict[str, Any]:
    deadline = time.monotonic() + CONFIGURATION_SAVE_TIMEOUT_SECONDS
    observed_value: Any = None
    while time.monotonic() < deadline:
        emulator_sensor_set(sensor_name, sensor_values)
        configuration = AUTOMATION.require_configuration(
            AUTOMATION.request(connection, AUTOMATION.TYPE_CONFIG_GET),
        )
        script = next(
            (item for item in configuration["scripts"] if item["id"] == script_id),
            None,
        )
        if isinstance(script, dict):
            observed_value = script.get("state", {}).get(state_key)
        if observed_value == expected_value:
            return configuration
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError(
        f"injected sensor state did not update: {script_id}.{state_key} observed={observed_value!r}",
    )


def wait_for_injected_automation_location_state(
    connection: socket.socket,
    script_id: str,
    longitude: float,
    latitude: float,
    expected_latitude: float | None,
) -> dict[str, Any]:
    deadline = time.monotonic() + CONFIGURATION_SAVE_TIMEOUT_SECONDS
    observed_value: Any = None
    while time.monotonic() < deadline:
        emulator_geo_fix(longitude, latitude)
        retry_deadline = min(
            deadline,
            time.monotonic() + AUTOMATION_LOCATION_EVENT_RETRY_SECONDS,
        )
        while time.monotonic() < retry_deadline:
            configuration = AUTOMATION.require_configuration(
                AUTOMATION.request(connection, AUTOMATION.TYPE_CONFIG_GET),
            )
            script = next(
                (item for item in configuration["scripts"] if item["id"] == script_id),
                None,
            )
            if isinstance(script, dict):
                observed_value = script.get("state", {}).get("latitude")
            if (
                isinstance(observed_value, (int, float)) and
                (
                    expected_latitude is None or
                    abs(observed_value - expected_latitude) <= AUTOMATION_LOCATION_COORDINATE_TOLERANCE
                )
            ):
                return configuration
            time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    received_event_count = sum(
        record["event"] == AUTOMATION_LOCATION_RECEIVED_EVENT
        for record in device_log_records()
    )
    raise AssertionError(
        f"injected location state did not update: {script_id}.latitude observed={observed_value!r} "
        f"location_callbacks={received_event_count}",
    )

def test_automation_battery_event_updates_public_lua_state(
    websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    configured = copy.deepcopy(original)
    configured["scripts"].append(
        {
            "id": AUTOMATION_LIVE_SCRIPT_ID,
            "title": "Automation battery fixture",
            "enabled": True,
            "apiVersion": LUA_SCRIPT_API_VERSION,
            "source": AUTOMATION_LIVE_SOURCE,
            "state": AUTOMATION_LIVE_INITIAL_STATE,
        },
    )
    configured["automation"] = {
        "apiVersion": 1,
        "policies": [
            {
                "id": AUTOMATION_LIVE_POLICY_ID,
                "title": "Automation battery policy",
                "enabled": True,
                "capabilities": ["battery"],
                "actions": ["patchState"],
            },
        ],
        "scripts": [
            {
                "scriptId": AUTOMATION_LIVE_SCRIPT_ID,
                "policyId": AUTOMATION_LIVE_POLICY_ID,
                "enabled": True,
                "subscriptions": [
                    {
                        "event": "battery",
                        "minimumIntervalMilliseconds": 0,
                        "coalescingKey": "automation-live-battery",
                    },
                ],
            },
        ],
    }
    try:
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=configured,
        )
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
        device_command(f"dumpsys battery set level {AUTOMATION_LIVE_BATTERY_LEVEL}")
        updated = wait_for_automation_script_state(
            websocket_control,
            AUTOMATION_LIVE_SCRIPT_ID,
            AUTOMATION_LIVE_STATE_KEY,
            AUTOMATION_LIVE_BATTERY_LEVEL,
        )
        script = next(
            script for script in updated["scripts"] if script["id"] == AUTOMATION_LIVE_SCRIPT_ID
        )
        assert script["state"] == {AUTOMATION_LIVE_STATE_KEY: AUTOMATION_LIVE_BATTERY_LEVEL}
    finally:
        try:
            device_command("dumpsys battery reset")
        finally:
            AUTOMATION.request(
                websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=original,
        )


def test_automation_public_file_reload_preserves_last_accepted_service_configuration(
    websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    configured = automation_configuration(
        original,
        AUTOMATION_RELOAD_SOURCE,
        AUTOMATION_RELOAD_INITIAL_STATE,
        ["battery", "charging"],
        ["patchState"],
        [
            {
                "event": "battery",
                "minimumIntervalMilliseconds": 0,
                "coalescingKey": "automation-reload-battery",
            },
        ],
    )
    artifact_path = ARTIFACT_DIRECTORY / AUTOMATION_LIVE_SCRIPT_ARTIFACT_NAME
    try:
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=configured,
        )
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
        device_command(f"am start -W -n {SETTINGS_ACTIVITY_COMPONENT}")
        wait_for_foreground_application(COMMAND_SETTINGS_PACKAGE_QUERY)
        run_device_operation(
            "file-pull",
            DEVICE_FILE=AUTOMATION_LIVE_SCRIPT_DEVICE_PATH,
            ARTIFACT_FILE=AUTOMATION_LIVE_SCRIPT_ARTIFACT_NAME,
        )
        subscription_document = json.loads(artifact_path.read_text(encoding="utf-8"))
        subscription_document["subscriptions"] = [
            {
                "event": "charging",
                "minimumIntervalMilliseconds": 0,
                "coalescingKey": "automation-reload-charging",
            },
        ]
        artifact_path.write_text(
            json.dumps(subscription_document, separators=(",", ":")),
            encoding="utf-8",
        )
        run_device_operation(
            "file-push",
            DEVICE_FILE=AUTOMATION_LIVE_SCRIPT_DEVICE_PATH,
            ARTIFACT_FILE=AUTOMATION_LIVE_SCRIPT_ARTIFACT_NAME,
        )
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
        restore_system_home()
        device_command(f"dumpsys battery set level {AUTOMATION_RELOAD_BATTERY_LEVEL}")
        emulator_power_set("off")
        emulator_power_set("on")
        with connected_websocket_control() as resumed_control:
            updated = wait_for_automation_script_state(
                resumed_control,
                AUTOMATION_LIVE_SCRIPT_ID,
                "charging",
                True,
            )
            script = next(
                script
                for script in updated["scripts"]
                if script["id"] == AUTOMATION_LIVE_SCRIPT_ID
            )
            assert script["state"]["batteryLevel"] != AUTOMATION_RELOAD_BATTERY_LEVEL

        device_command(f"am start -W -n {SETTINGS_ACTIVITY_COMPONENT}")
        wait_for_foreground_application(COMMAND_SETTINGS_PACKAGE_QUERY)
        artifact_path.write_text(CONFIG_INVALID_CONTENT, encoding="utf-8")
        run_device_operation(
            "file-push",
            DEVICE_FILE=AUTOMATION_LIVE_SCRIPT_DEVICE_PATH,
            ARTIFACT_FILE=AUTOMATION_LIVE_SCRIPT_ARTIFACT_NAME,
        )
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
        restore_system_home()
        emulator_power_set("off")
        with connected_websocket_control() as resumed_control:
            wait_for_automation_script_state(
                resumed_control,
                AUTOMATION_LIVE_SCRIPT_ID,
                "charging",
                False,
            )
    finally:
        try:
            emulator_power_set("on")
            device_command("dumpsys battery reset")
        finally:
            restore_system_home()
            with connected_websocket_control() as restoration_control:
                AUTOMATION.request(
                    restoration_control,
                    AUTOMATION.TYPE_CONFIG_REPLACE,
                    config=original,
                )


def test_automation_sensor_and_approximate_location_events_use_real_emulator_injection(
    websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    configured = automation_configuration(
        original,
        AUTOMATION_SENSOR_LOCATION_SOURCE,
        AUTOMATION_SENSOR_INITIAL_STATE,
        ["sensors", "locationApproximate"],
        ["patchState"],
        [
            {
                "event": "sensor",
                "minimumIntervalMilliseconds": AUTOMATION_SENSOR_DELIVERY_INTERVAL_MILLISECONDS,
                "coalescingKey": "automation-sensor-acceleration",
                "sensorTypes": [AUTOMATION_SENSOR_ACCELERATION_TYPE],
                "samplingPeriodMicroseconds": 200_000,
            },
            {
                "event": "location",
                "minimumIntervalMilliseconds": 0,
                "coalescingKey": "automation-location-approximate",
                "locationPrecision": "approximate",
            },
        ],
    )
    try:
        device_command(
            f"pm grant {DIKCIZ_DEBUG_PACKAGE} android.permission.ACCESS_COARSE_LOCATION",
        )
        device_command(
            f"pm grant {DIKCIZ_DEBUG_PACKAGE} android.permission.ACCESS_FINE_LOCATION",
        )
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=configured,
        )
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
        updated = wait_for_injected_automation_sensor_state(
            websocket_control,
            AUTOMATION_LIVE_SCRIPT_ID,
            AUTOMATION_SENSOR_ACCELERATION_NAME,
            AUTOMATION_SENSOR_VALUES,
            "sensorVectorInjected",
            True,
        )
        script = next(
            item
            for item in updated["scripts"]
            if item["id"] == AUTOMATION_LIVE_SCRIPT_ID
        )
        assert script["state"]["sensorVectorInjected"] is True

        updated = wait_for_injected_automation_location_state(
            websocket_control,
            AUTOMATION_LIVE_SCRIPT_ID,
            AUTOMATION_LOCATION_APPROXIMATE_LONGITUDE,
            AUTOMATION_LOCATION_APPROXIMATE_LATITUDE,
            round(AUTOMATION_LOCATION_APPROXIMATE_LATITUDE, 2),
        )
        script = next(
            item
            for item in updated["scripts"]
            if item["id"] == AUTOMATION_LIVE_SCRIPT_ID
        )
        assert script["state"]["longitude"] == round(AUTOMATION_LOCATION_APPROXIMATE_LONGITUDE, 2)
        assert script["state"]["altitudeVisible"] is False
        assert script["state"]["bearingVisible"] is False
        assert script["state"]["speedVisible"] is False

    finally:
        try:
            device_command(
                f"pm revoke {DIKCIZ_DEBUG_PACKAGE} android.permission.ACCESS_FINE_LOCATION",
            )
            device_command(
                f"pm revoke {DIKCIZ_DEBUG_PACKAGE} android.permission.ACCESS_COARSE_LOCATION",
            )
        finally:
            emulator_geo_fix(0.0, 0.0)
            emulator_sensor_set(AUTOMATION_SENSOR_ACCELERATION_NAME, "0:0:0")
            # Each revoke kills the process. Android restarts home after the
            # first one, the second kills that restart, and from then on it
            # only brings the service back. The control plane lives in the
            # activity, so home has to be asked for again.
            restore_system_home()
            with connected_websocket_control() as restoration_control:
                AUTOMATION.request(
                    restoration_control,
                    AUTOMATION.TYPE_CONFIG_REPLACE,
                    config=original,
                )


def test_automation_precise_location_event_uses_real_emulator_injection(
    websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    configured = automation_configuration(
        original,
        AUTOMATION_SENSOR_LOCATION_SOURCE,
        AUTOMATION_SENSOR_INITIAL_STATE,
        ["locationPrecise"],
        ["patchState"],
        [
            {
                "event": "location",
                "minimumIntervalMilliseconds": 0,
                "coalescingKey": "automation-location-precise",
                "locationPrecision": "precise",
            },
        ],
    )
    try:
        device_command(
            f"pm grant {DIKCIZ_DEBUG_PACKAGE} android.permission.ACCESS_COARSE_LOCATION",
        )
        device_command(
            f"pm grant {DIKCIZ_DEBUG_PACKAGE} android.permission.ACCESS_FINE_LOCATION",
        )
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=configured,
        )
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
        updated = wait_for_injected_automation_location_state(
            websocket_control,
            AUTOMATION_LIVE_SCRIPT_ID,
            AUTOMATION_LOCATION_PRECISE_LONGITUDE,
            AUTOMATION_LOCATION_PRECISE_LATITUDE,
            AUTOMATION_LOCATION_PRECISE_LATITUDE,
        )
        script = next(
            item
            for item in updated["scripts"]
            if item["id"] == AUTOMATION_LIVE_SCRIPT_ID
        )
        assert abs(
            script["state"]["longitude"] - AUTOMATION_LOCATION_PRECISE_LONGITUDE,
        ) <= AUTOMATION_LOCATION_COORDINATE_TOLERANCE
        assert script["state"]["altitudeVisible"] is True
        assert script["state"]["bearingVisible"] is True
        assert script["state"]["speedVisible"] is True
    finally:
        try:
            device_command(
                f"pm revoke {DIKCIZ_DEBUG_PACKAGE} android.permission.ACCESS_FINE_LOCATION",
            )
            device_command(
                f"pm revoke {DIKCIZ_DEBUG_PACKAGE} android.permission.ACCESS_COARSE_LOCATION",
            )
        finally:
            emulator_geo_fix(0.0, 0.0)
            # Each revoke kills the process. Android restarts home after
            # the first one, the second kills that restart, and from then
            # on it only brings the service back. The control plane lives
            # in the activity, so home has to be asked for again.
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


def test_automation_precise_location_subscription_requires_fine_android_permission(
    websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    configured = automation_configuration(
        original,
        AUTOMATION_SENSOR_LOCATION_SOURCE,
        AUTOMATION_SENSOR_INITIAL_STATE,
        ["locationPrecise"],
        ["patchState"],
        [
            {
                "event": "location",
                "minimumIntervalMilliseconds": 0,
                "coalescingKey": "automation-location-precise-permission",
                "locationPrecision": "precise",
            },
        ],
    )
    control: socket.socket | None = None
    try:
        device_command(
            f"pm revoke {DIKCIZ_DEBUG_PACKAGE} android.permission.ACCESS_FINE_LOCATION",
        )
        device_command(
            f"pm grant {DIKCIZ_DEBUG_PACKAGE} android.permission.ACCESS_COARSE_LOCATION",
        )
        control = wait_for_restarted_websocket_control()
        AUTOMATION.request(
            control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=configured,
        )
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
        assert any(
            record["event"] == AUTOMATION_LOCATION_REGISTRATION_SKIPPED_EVENT and
            record.get("reason") == AUTOMATION_LOCATION_PERMISSION_MISSING_REASON
            for record in device_log_records()
        )
        emulator_geo_fix(
            AUTOMATION_LOCATION_PRECISE_LONGITUDE,
            AUTOMATION_LOCATION_PRECISE_LATITUDE,
        )
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
        updated = AUTOMATION.require_configuration(
            AUTOMATION.request(control, AUTOMATION.TYPE_CONFIG_GET),
        )
        script = next(
            item
            for item in updated["scripts"]
            if item["id"] == AUTOMATION_LIVE_SCRIPT_ID
        )
        assert script["state"] == AUTOMATION_SENSOR_INITIAL_STATE
    finally:
        if control is not None:
            control.close()
        try:
            device_command(
                f"pm revoke {DIKCIZ_DEBUG_PACKAGE} android.permission.ACCESS_COARSE_LOCATION",
            )
        finally:
            emulator_geo_fix(0.0, 0.0)
            restoration_control = wait_for_restarted_websocket_control()
            try:
                AUTOMATION.request(
                    restoration_control,
                    AUTOMATION.TYPE_CONFIG_REPLACE,
                    config=original,
                )
            finally:
                restoration_control.close()


def test_automation_coarse_only_location_delivers_approximate_without_precise(
    websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    configured = automation_configuration(
        original,
        AUTOMATION_COARSE_LOCATION_SOURCE,
        AUTOMATION_COARSE_LOCATION_INITIAL_STATE,
        AUTOMATION_COARSE_LOCATION_CAPABILITIES,
        AUTOMATION_PATCH_STATE_ACTIONS,
        [
            {
                "event": "location",
                "minimumIntervalMilliseconds": 0,
                "coalescingKey": AUTOMATION_COARSE_LOCATION_APPROXIMATE_COALESCING_KEY,
                "locationPrecision": "approximate",
            },
            {
                "event": "location",
                "minimumIntervalMilliseconds": 0,
                "coalescingKey": AUTOMATION_COARSE_LOCATION_PRECISE_COALESCING_KEY,
                "locationPrecision": "precise",
            },
        ],
    )
    control: socket.socket | None = None
    try:
        device_command(
            f"pm revoke {DIKCIZ_DEBUG_PACKAGE} android.permission.ACCESS_FINE_LOCATION",
        )
        device_command(
            f"pm grant {DIKCIZ_DEBUG_PACKAGE} android.permission.ACCESS_COARSE_LOCATION",
        )
        control = wait_for_restarted_websocket_control()
        AUTOMATION.request(
            control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=configured,
        )
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
        updated = wait_for_injected_automation_location_state(
            control,
            AUTOMATION_LIVE_SCRIPT_ID,
            AUTOMATION_LOCATION_APPROXIMATE_LONGITUDE,
            AUTOMATION_LOCATION_APPROXIMATE_LATITUDE,
            None,
        )
        script = next(
            item
            for item in updated["scripts"]
            if item["id"] == AUTOMATION_LIVE_SCRIPT_ID
        )
        state = script["state"]
        assert state[AUTOMATION_COARSE_LOCATION_APPROXIMATE_STATE_KEY] is True
        assert state[AUTOMATION_COARSE_LOCATION_PRECISE_STATE_KEY] is False
        assert isinstance(state["latitude"], (int, float))
        assert isinstance(state["longitude"], (int, float))
    finally:
        if control is not None:
            control.close()
        try:
            device_command(
                f"pm revoke {DIKCIZ_DEBUG_PACKAGE} android.permission.ACCESS_COARSE_LOCATION",
            )
        finally:
            emulator_geo_fix(0.0, 0.0)
            restoration_control = wait_for_restarted_websocket_control()
            try:
                AUTOMATION.request(
                    restoration_control,
                    AUTOMATION.TYPE_CONFIG_REPLACE,
                    config=original,
                )
            finally:
                restoration_control.close()


def test_automation_notification_redaction_package_filter_rate_limit_and_denied_action(
    websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    notification_configuration = automation_configuration(
        original,
        AUTOMATION_NOTIFICATION_SOURCE,
        AUTOMATION_NOTIFICATION_INITIAL_STATE,
        ["notificationsMetadata"],
        ["patchState"],
        [
            {
                "event": "notificationPosted",
                "minimumIntervalMilliseconds": 0,
                "coalescingKey": "automation-notification-posted",
                "packages": [AUTOMATION_NOTIFICATION_SOURCE_PACKAGE],
            },
        ],
    )
    rate_limited_configuration = automation_configuration(
        original,
        AUTOMATION_SCREEN_SOURCE,
        AUTOMATION_SCREEN_INITIAL_STATE,
        ["screen"],
        ["patchState"],
        [
            {
                "event": "screen",
                "minimumIntervalMilliseconds": AUTOMATION_SCREEN_RATE_LIMIT_MILLISECONDS,
                "coalescingKey": "automation-screen-rate-limit",
            },
        ],
    )
    denied_action_configuration = automation_configuration(
        original,
        AUTOMATION_DENIED_SCREEN_SOURCE,
        {},
        ["screen"],
        [],
        [
            {
                "event": "screen",
                "minimumIntervalMilliseconds": 0,
                "coalescingKey": "automation-screen-denied-action",
            },
        ],
    )
    try:
        device_command(f"cmd notification allow_listener {AUTOMATION_NOTIFICATION_COMPONENT}")
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=notification_configuration,
        )
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
        device_command(
            "cmd notification post "
            f"-t {AUTOMATION_NOTIFICATION_TITLE} automation-metadata {AUTOMATION_NOTIFICATION_TEXT}",
        )
        updated = wait_for_automation_script_state(
            websocket_control,
            AUTOMATION_LIVE_SCRIPT_ID,
            "hasTitle",
            False,
        )
        script = next(
            item
            for item in updated["scripts"]
            if item["id"] == AUTOMATION_LIVE_SCRIPT_ID
        )
        assert script["state"]["hasText"] is False

        notification_configuration["automation"]["policies"][0]["capabilities"] = [
            "notificationsMetadata",
            "notificationsContent",
        ]
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=notification_configuration,
        )
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
        device_command(
            "cmd notification post "
            f"-t {AUTOMATION_NOTIFICATION_TITLE} automation-content {AUTOMATION_NOTIFICATION_TEXT}",
        )
        updated = wait_for_automation_script_state(
            websocket_control,
            AUTOMATION_LIVE_SCRIPT_ID,
            "hasTitle",
            True,
        )
        script = next(
            item
            for item in updated["scripts"]
            if item["id"] == AUTOMATION_LIVE_SCRIPT_ID
        )
        assert script["state"]["hasText"] is True

        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=rate_limited_configuration,
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
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
        current = AUTOMATION.require_configuration(
            AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
        )
        script = next(
            item
            for item in current["scripts"]
            if item["id"] == AUTOMATION_LIVE_SCRIPT_ID
        )
        assert script["state"]["screenOn"] is False

        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=denied_action_configuration,
        )
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
        device_command(f"input keyevent {AUTOMATION_SCREEN_TOGGLE_KEYCODE}")
        device_command(f"input keyevent {AUTOMATION_SCREEN_WAKE_KEYCODE}")
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
        rejected_snapshot = AUTOMATION.request(websocket_control, AUTOMATION.TYPE_SNAPSHOT)
        assert rejected_snapshot["selectedPageId"] == selected_page(original)
        assert any(
            record["event"] == "automation_action_rejected" and
            record.get("reason") == "capability_denied"
            for record in device_log_records()
        )
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
