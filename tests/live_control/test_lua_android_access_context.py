"""Lua receives a read-only copy of the current Android-access document."""

from . import *


def manual_context_configuration(
    configuration: dict[str, Any],
    source: str,
) -> dict[str, Any]:
    return automation_configuration(
        configuration,
        source,
        LUA_ANDROID_ACCESS_INITIAL_STATE,
        [LUA_ANDROID_ACCESS_CAPABILITY],
        ["patchState", "patchWidget"],
        [
            {
                "event": "manual",
                "minimumIntervalMilliseconds": 0,
                "coalescingKey": "lua-android-access-context",
            },
        ],
    )


def wait_for_failed_run_status() -> dict[str, Any]:
    deadline = time.monotonic() + LUA_ANDROID_ACCESS_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        status = json.loads(device_command(f"cat {LUA_ANDROID_ACCESS_RUN_STATUS_PATH}"))
        if status.get("outcome") == "failed":
            return status
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError("Lua read-only context mutation did not record a failed run")


def wait_for_target_widget_checked(connection: socket.socket) -> None:
    deadline = time.monotonic() + LUA_ANDROID_ACCESS_TIMEOUT_SECONDS
    state: Any = None
    while time.monotonic() < deadline:
        configuration = AUTOMATION.require_configuration(
            AUTOMATION.request(connection, AUTOMATION.TYPE_CONFIG_GET),
        )
        target = next(
            widget
            for widget in configuration["launcher"]["home"]["pages"][0]["widgets"]
            if widget["id"] == LUA_ANDROID_ACCESS_TARGET_WIDGET_ID
        )
        state = target.get("state")
        if isinstance(state, dict) and state.get("checked") is True:
            return
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError(f"patchWidget did not check the target widget: {state!r}")


def test_lua_android_access_context_matches_status_and_rejects_mutation(
    websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    expected_accessibility = AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_AUTOMATION_STATUS,
    )["androidAccess"][LUA_ANDROID_ACCESS_STATE_KEY]
    assert isinstance(expected_accessibility, bool)
    try:
        _, rejected = AUTOMATION.request_error_with_id(
            websocket_control,
            AUTOMATION.TYPE_AUTOMATION_DISPATCH,
            sourceWidgetAddress=LUA_ANDROID_ACCESS_TARGET_WIDGET_ADDRESS,
            actions=[{"type": "toggle", "widgetId": LUA_ANDROID_ACCESS_TARGET_WIDGET_ID}],
        )
        assert rejected["code"] == "validation_failed"

        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=manual_context_configuration(original, LUA_ANDROID_ACCESS_CONTEXT_SOURCE),
        )
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_AUTOMATION_TRIGGER,
            scriptId=AUTOMATION_LIVE_SCRIPT_ID,
        )
        wait_for_automation_script_state(
            websocket_control,
            AUTOMATION_LIVE_SCRIPT_ID,
            LUA_ANDROID_ACCESS_STATE_KEY,
            expected_accessibility,
        )
        # The script's actions apply in order, so the widget patch lands after the state.
        wait_for_target_widget_checked(websocket_control)

        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=manual_context_configuration(original, LUA_ANDROID_ACCESS_MUTATION_SOURCE),
        )
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_AUTOMATION_TRIGGER,
            scriptId=AUTOMATION_LIVE_SCRIPT_ID,
        )
        failed_run = wait_for_failed_run_status()
        assert failed_run["status"] == "invalid_lua"
        configuration = AUTOMATION.require_configuration(
            AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
        )
        script = next(
            item
            for item in configuration["scripts"]
            if item["id"] == AUTOMATION_LIVE_SCRIPT_ID
        )
        assert script["state"][LUA_ANDROID_ACCESS_STATE_KEY] == LUA_ANDROID_ACCESS_UNCHANGED_STATE
    finally:
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=original,
        )
