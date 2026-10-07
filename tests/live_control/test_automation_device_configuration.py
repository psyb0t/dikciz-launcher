"""Policy-gated device-configuration automation on the isolated Android emulator."""

from . import *


def device_configuration_script_state(configuration: dict[str, Any]) -> dict[str, Any]:
    script = next(
        item for item in configuration["scripts"] if item["id"] == AUTOMATION_LIVE_SCRIPT_ID
    )
    state = script["state"]
    assert isinstance(state, dict)
    return state


def mcp_device_configuration_script_state() -> dict[str, Any]:
    arguments = argparse.Namespace(
        host=AUTOMATION_HOST,
        port=MCP_PORT,
        expected_control_port=DEVICE_MCP_PORT,
        mutate=False,
    )
    session_id = MCP.initialize(arguments)
    try:
        MCP.notification_initialized(arguments, session_id)
        configuration = MCP.require_configuration(
            MCP.require_tool_result(
                MCP.call_tool(
                    arguments,
                    session_id,
                    AUTOMATION_DEVICE_CONFIGURATION_MCP_CONFIG_GET_REQUEST_ID,
                    MCP.TOOL_CONFIG_GET,
                ),
            ),
        )
        return device_configuration_script_state(configuration)
    finally:
        MCP.delete_session(arguments, session_id)


def expected_device_configuration_script_state(
    orientation: str,
    font_scale_percent: int,
) -> dict[str, Any]:
    return {
        AUTOMATION_DEVICE_CONFIGURATION_STATE_KEY: orientation,
        AUTOMATION_DEVICE_CONFIGURATION_FONT_SCALE_PERCENT_STATE_KEY: font_scale_percent,
    }


def wait_for_device_configuration_script_state(
    connection: socket.socket,
    orientation: str,
    font_scale_percent: int,
) -> dict[str, Any]:
    expected_state = expected_device_configuration_script_state(
        orientation,
        font_scale_percent,
    )
    deadline = time.monotonic() + CONFIGURATION_SAVE_TIMEOUT_SECONDS
    observed_state: dict[str, Any] | None = None
    while time.monotonic() < deadline:
        configuration = AUTOMATION.require_configuration(
            AUTOMATION.request(connection, AUTOMATION.TYPE_CONFIG_GET),
        )
        observed_state = device_configuration_script_state(configuration)
        if observed_state == expected_state:
            return configuration
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError(
        "device-configuration script state did not update: "
        f"expected={expected_state!r} observed={observed_state!r}",
    )


def device_configuration_automation_configuration(
    configuration: dict[str, Any],
    capabilities: list[str],
) -> dict[str, Any]:
    return automation_configuration(
        configuration,
        AUTOMATION_DEVICE_CONFIGURATION_SOURCE,
        AUTOMATION_DEVICE_CONFIGURATION_INITIAL_STATE,
        capabilities,
        ["patchState"],
        [
            {
                "event": AUTOMATION_DEVICE_CONFIGURATION_EVENT,
                "minimumIntervalMilliseconds": 0,
                "coalescingKey": AUTOMATION_DEVICE_CONFIGURATION_COALESCING_KEY,
            },
        ],
    )


def test_automation_device_configuration_is_policy_gated_and_uses_the_real_emulator_state(
    websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    original_accelerometer_rotation = device_system_setting(DEVICE_SETTING_ACCELEROMETER_ROTATION)
    original_font_scale = device_system_setting(DEVICE_SETTING_FONT_SCALE)
    original_user_rotation = device_system_setting(DEVICE_SETTING_USER_ROTATION)
    denied = device_configuration_automation_configuration(original, [])
    allowed = device_configuration_automation_configuration(
        original,
        [AUTOMATION_DEVICE_CONFIGURATION_CAPABILITY],
    )
    control = websocket_control
    try:
        emulator_font_scale_set(EMULATOR_DEVICE_FONT_SCALE_PERCENT_BASELINE)
        control.close()
        control = wait_for_restarted_websocket_control()
        emulator_device_orientation_set(EMULATOR_DEVICE_ORIENTATION_PORTRAIT)
        control.close()
        control = wait_for_restarted_websocket_control()
        restore_system_home()
        wait_for_page_viewport_orientation(EMULATOR_DEVICE_ORIENTATION_PORTRAIT)
        baseline_title_bounds = wait_for_physical_text_bounds(
            FIXTURE_WIDGET_TITLES["welcome"],
        )
        AUTOMATION.request(
            control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=denied,
        )
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
        emulator_font_scale_set(EMULATOR_DEVICE_FONT_SCALE_PERCENT_ENLARGED)
        control.close()
        control = wait_for_restarted_websocket_control()
        emulator_device_orientation_set(EMULATOR_DEVICE_ORIENTATION_LANDSCAPE)
        control.close()
        control = wait_for_restarted_websocket_control()
        wait_for_page_viewport_orientation(EMULATOR_DEVICE_ORIENTATION_LANDSCAPE)
        denied_configuration = AUTOMATION.require_configuration(
            AUTOMATION.request(control, AUTOMATION.TYPE_CONFIG_GET),
        )
        assert device_configuration_script_state(denied_configuration) == AUTOMATION_DEVICE_CONFIGURATION_INITIAL_STATE

        emulator_font_scale_set(EMULATOR_DEVICE_FONT_SCALE_PERCENT_BASELINE)
        control.close()
        control = wait_for_restarted_websocket_control()
        emulator_device_orientation_set(EMULATOR_DEVICE_ORIENTATION_PORTRAIT)
        control.close()
        control = wait_for_restarted_websocket_control()
        wait_for_page_viewport_orientation(EMULATOR_DEVICE_ORIENTATION_PORTRAIT)
        status = AUTOMATION.request(control, AUTOMATION.TYPE_AUTOMATION_STATUS)
        assert AUTOMATION_DEVICE_CONFIGURATION_CAPABILITY in status["capabilities"]
        assert AUTOMATION_DEVICE_CONFIGURATION_CAPABILITY not in status["androidAccess"]
        event = next(
            item
            for item in status["events"]
            if item["type"] == AUTOMATION_DEVICE_CONFIGURATION_EVENT
        )
        assert event["requiredCapability"] == AUTOMATION_DEVICE_CONFIGURATION_CAPABILITY
        assert event["requiredSubscriptionFields"] == []
        assert event["optionalSubscriptionFields"] == []

        AUTOMATION.request(
            control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=allowed,
        )
        initial_configuration = wait_for_device_configuration_script_state(
            control,
            EMULATOR_DEVICE_ORIENTATION_PORTRAIT,
            EMULATOR_DEVICE_FONT_SCALE_PERCENT_BASELINE,
        )
        assert device_configuration_script_state(initial_configuration) == expected_device_configuration_script_state(
            EMULATOR_DEVICE_ORIENTATION_PORTRAIT,
            EMULATOR_DEVICE_FONT_SCALE_PERCENT_BASELINE,
        )

        emulator_font_scale_set(EMULATOR_DEVICE_FONT_SCALE_PERCENT_ENLARGED)
        control.close()
        control = wait_for_restarted_websocket_control()
        restore_system_home()
        enlarged_title_bounds = wait_for_physical_text_bounds(FIXTURE_WIDGET_TITLES["welcome"])
        assert enlarged_title_bounds["bottom"] - enlarged_title_bounds["top"] > (
            baseline_title_bounds["bottom"] - baseline_title_bounds["top"]
        )
        run_device_operation("screenshot")
        shutil.copyfile(
            ARTIFACT_DIRECTORY / "screenshot.png",
            ARTIFACT_DIRECTORY / DEVICE_CONFIGURATION_FONT_SCALE_ARTIFACT_NAME,
        )
        wait_for_device_configuration_script_state(
            control,
            EMULATOR_DEVICE_ORIENTATION_PORTRAIT,
            EMULATOR_DEVICE_FONT_SCALE_PERCENT_ENLARGED,
        )

        emulator_font_scale_set(EMULATOR_DEVICE_FONT_SCALE_PERCENT_SMALL)
        control.close()
        control = wait_for_restarted_websocket_control()
        wait_for_device_configuration_script_state(
            control,
            EMULATOR_DEVICE_ORIENTATION_PORTRAIT,
            EMULATOR_DEVICE_FONT_SCALE_PERCENT_SMALL,
        )

        emulator_device_orientation_set(EMULATOR_DEVICE_ORIENTATION_LANDSCAPE)
        control.close()
        control = wait_for_restarted_websocket_control()
        wait_for_page_viewport_orientation(EMULATOR_DEVICE_ORIENTATION_LANDSCAPE)
        wait_for_device_configuration_script_state(
            control,
            EMULATOR_DEVICE_ORIENTATION_LANDSCAPE,
            EMULATOR_DEVICE_FONT_SCALE_PERCENT_SMALL,
        )
        emulator_device_orientation_set(EMULATOR_DEVICE_ORIENTATION_PORTRAIT)
        control.close()
        control = wait_for_restarted_websocket_control()
        wait_for_page_viewport_orientation(EMULATOR_DEVICE_ORIENTATION_PORTRAIT)
        configuration = wait_for_device_configuration_script_state(
            control,
            EMULATOR_DEVICE_ORIENTATION_PORTRAIT,
            EMULATOR_DEVICE_FONT_SCALE_PERCENT_SMALL,
        )
        state = device_configuration_script_state(configuration)
        assert mcp_device_configuration_script_state() == state
    finally:
        restoration_control: socket.socket | None = None
        try:
            emulator_font_scale_set(EMULATOR_DEVICE_FONT_SCALE_PERCENT_BASELINE)
            emulator_device_orientation_set(EMULATOR_DEVICE_ORIENTATION_PORTRAIT)
            restoration_control = wait_for_restarted_websocket_control()
            AUTOMATION.request(
                restoration_control,
                AUTOMATION.TYPE_CONFIG_REPLACE,
                config=original,
            )
        finally:
            restore_device_system_setting(DEVICE_SETTING_FONT_SCALE, original_font_scale)
            restore_device_system_setting(
                DEVICE_SETTING_USER_ROTATION,
                original_user_rotation,
            )
            restore_device_system_setting(
                DEVICE_SETTING_ACCELEROMETER_ROTATION,
                original_accelerometer_rotation,
            )
            if restoration_control is not None:
                restoration_control.close()
            if control is not websocket_control:
                control.close()
