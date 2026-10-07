"""Policy-gated package lifecycle automation on the isolated Android emulator."""

from . import *


def package_lifecycle_script_state(configuration: dict[str, Any]) -> dict[str, Any]:
    script = next(
        item for item in configuration["scripts"] if item["id"] == AUTOMATION_LIVE_SCRIPT_ID
    )
    state = script["state"]
    assert isinstance(state, dict)
    return state


def mcp_package_lifecycle_script_state() -> dict[str, Any]:
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
                    AUTOMATION_PACKAGE_LIFECYCLE_MCP_CONFIG_GET_REQUEST_ID,
                    MCP.TOOL_CONFIG_GET,
                ),
            ),
        )
        return package_lifecycle_script_state(configuration)
    finally:
        MCP.delete_session(arguments, session_id)


def package_lifecycle_configuration(
    configuration: dict[str, Any],
    capabilities: list[str],
) -> dict[str, Any]:
    return automation_configuration(
        configuration,
        AUTOMATION_PACKAGE_LIFECYCLE_SOURCE,
        AUTOMATION_PACKAGE_LIFECYCLE_INITIAL_STATE,
        capabilities,
        ["patchState"],
        [
            {
                "event": AUTOMATION_PACKAGE_LIFECYCLE_EVENT,
                "minimumIntervalMilliseconds": 0,
                "coalescingKey": AUTOMATION_PACKAGE_LIFECYCLE_COALESCING_KEY,
            },
        ],
    )


def remove_configurable_provider_fixture_if_present(package_name: str) -> None:
    if f"package:{package_name}" in device_command(f"pm list packages {package_name}"):
        uninstall_configurable_provider_fixture(package_name)


def test_automation_package_lifecycle_is_policy_gated_and_uses_real_android_broadcasts(
    websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    package_name = configurable_provider_fixture_package()
    denied = package_lifecycle_configuration(original, [])
    allowed = package_lifecycle_configuration(
        original,
        [AUTOMATION_PACKAGE_LIFECYCLE_CAPABILITY],
    )
    try:
        remove_configurable_provider_fixture_if_present(package_name)
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=denied,
        )
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
        install_configurable_provider_fixture()
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
        denied_configuration = AUTOMATION.require_configuration(
            AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
        )
        assert package_lifecycle_script_state(denied_configuration) == AUTOMATION_PACKAGE_LIFECYCLE_INITIAL_STATE
        uninstall_configurable_provider_fixture(package_name)

        status = AUTOMATION.request(websocket_control, AUTOMATION.TYPE_AUTOMATION_STATUS)
        assert AUTOMATION_PACKAGE_LIFECYCLE_CAPABILITY in status["capabilities"]
        assert AUTOMATION_PACKAGE_LIFECYCLE_CAPABILITY not in status["androidAccess"]
        event = next(
            item
            for item in status["events"]
            if item["type"] == AUTOMATION_PACKAGE_LIFECYCLE_EVENT
        )
        assert event["requiredCapability"] == AUTOMATION_PACKAGE_LIFECYCLE_CAPABILITY
        assert event["requiredSubscriptionFields"] == []
        assert event["optionalSubscriptionFields"] == []

        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=allowed,
        )
        install_configurable_provider_fixture()
        installed_configuration = wait_for_automation_script_state(
            websocket_control,
            AUTOMATION_LIVE_SCRIPT_ID,
            AUTOMATION_PACKAGE_LIFECYCLE_STATE_INSTALLED_SEEN_KEY,
            True,
        )
        assert package_lifecycle_script_state(installed_configuration)[
            AUTOMATION_PACKAGE_LIFECYCLE_STATE_PACKAGE_KEY
        ] == package_name

        install_configurable_provider_fixture()
        wait_for_automation_script_state(
            websocket_control,
            AUTOMATION_LIVE_SCRIPT_ID,
            AUTOMATION_PACKAGE_LIFECYCLE_STATE_UPDATED_SEEN_KEY,
            True,
        )

        uninstall_configurable_provider_fixture(package_name)
        removed_configuration = wait_for_automation_script_state(
            websocket_control,
            AUTOMATION_LIVE_SCRIPT_ID,
            AUTOMATION_PACKAGE_LIFECYCLE_STATE_REMOVED_SEEN_KEY,
            True,
        )
        state = package_lifecycle_script_state(removed_configuration)
        assert state[AUTOMATION_PACKAGE_LIFECYCLE_STATE_PACKAGE_KEY] == package_name
        assert mcp_package_lifecycle_script_state() == state
    finally:
        try:
            remove_configurable_provider_fixture_if_present(package_name)
        finally:
            AUTOMATION.request(
                websocket_control,
                AUTOMATION.TYPE_CONFIG_REPLACE,
                config=original,
            )
