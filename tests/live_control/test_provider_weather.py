"""Weather-style Android provider-widget recovery behavior."""

from . import *


def wait_for_configurable_provider_weather_node(
    connection: socket.socket,
    provider_id: str,
) -> tuple[dict[str, Any], dict[str, Any]]:
    host_semantic_id = f"widget:{provider_id}{PROVIDER_AUTOMATION_HOST_SEMANTIC_SUFFIX}"
    child_prefix = f"{host_semantic_id}:"
    deadline = time.monotonic() + PROVIDER_RENDER_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        snapshot = AUTOMATION.request(connection, AUTOMATION.TYPE_SNAPSHOT)
        host = next(
            (
                node
                for node in snapshot["nodes"]
                if node[AUTOMATION.KEY_SEMANTIC_ID] == host_semantic_id
            ),
            None,
        )
        refresh = next(
            (
                node
                for node in snapshot["nodes"]
                if node[AUTOMATION.KEY_SEMANTIC_ID].startswith(child_prefix)
                and node[AUTOMATION.KEY_CLICKABLE]
                and node[AUTOMATION.KEY_VISIBLE]
                and str(node[AUTOMATION.KEY_RESOURCE_ID]).endswith(
                    CONFIGURABLE_PROVIDER_FIXTURE_WEATHER_REFRESH_ACTION_RESOURCE_ID_SUFFIX,
                )
            ),
            None,
        )
        if host is not None and refresh is not None:
            return host, refresh
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError("the configurable provider exposed no weather refresh control")


def test_provider_weather_refresh_recovers_from_unavailable_state(
    websocket_control: socket.socket,
    installed_configurable_provider_fixture: None,
) -> None:
    start_configurable_provider_fixture_addition(websocket_control)
    complete_configurable_provider_fixture_configuration(save=True)
    provider_control = wait_for_restarted_websocket_control()
    try:
        provider = wait_for_new_page_widget(provider_control, FIXTURE_NOTES_PAGE_ID, "provider")
        provider_id = provider["id"]
        assert isinstance(provider_id, str)
        host, refresh = wait_for_configurable_provider_weather_node(provider_control, provider_id)
        assert host[AUTOMATION.KEY_ROLE] == PROVIDER_AUTOMATION_ROLE
        wait_for_physical_text_bounds(CONFIGURABLE_PROVIDER_FIXTURE_WEATHER_UNAVAILABLE_STATUS)
        arguments = argparse.Namespace(host=AUTOMATION_HOST, port=MCP_PORT, mutate=False)
        session_id = MCP.initialize(arguments)
        try:
            MCP.notification_initialized(arguments, session_id)
            mcp_snapshot = MCP.require_tool_result(
                MCP.call_tool(
                    arguments,
                    session_id,
                    MCP_PROVIDER_AUTOMATION_SNAPSHOT_REQUEST_ID,
                    MCP.TOOL_SNAPSHOT,
                ),
            )
            assert AUTOMATION.require_node(mcp_snapshot, host[AUTOMATION.KEY_SEMANTIC_ID]) == host
            assert AUTOMATION.require_node(mcp_snapshot, refresh[AUTOMATION.KEY_SEMANTIC_ID]) == refresh
        finally:
            MCP.delete_session(arguments, session_id)
        AUTOMATION.request(
            provider_control,
            AUTOMATION.TYPE_TAP,
            semanticId=refresh[AUTOMATION.KEY_SEMANTIC_ID],
        )
        wait_for_physical_text_bounds(CONFIGURABLE_PROVIDER_FIXTURE_WEATHER_RECOVERED_STATUS)
        _, refreshed_refresh = wait_for_configurable_provider_weather_node(provider_control, provider_id)
        assert refreshed_refresh[AUTOMATION.KEY_SEMANTIC_ID] != refresh[AUTOMATION.KEY_SEMANTIC_ID]
        with pytest.raises(AUTOMATION.WebSocketProtocolError, match="semantic ID was not found"):
            AUTOMATION.request(
                provider_control,
                AUTOMATION.TYPE_TAP,
                semanticId=refresh[AUTOMATION.KEY_SEMANTIC_ID],
            )
        run_device_operation("screenshot")
        shutil.copyfile(
            ARTIFACT_DIRECTORY / "screenshot.png",
            ARTIFACT_DIRECTORY / "configurable-provider-weather-recovered.png",
        )
    finally:
        provider_control.close()
