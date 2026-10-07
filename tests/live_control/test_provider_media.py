"""Media-style Android provider-widget control behavior."""

from . import *


def wait_for_configurable_provider_media_action_nodes(
    connection: socket.socket,
    provider_id: str,
) -> tuple[dict[str, Any], dict[str, Any], dict[str, Any], dict[str, Any]]:
    host_semantic_id = f"widget:{provider_id}{PROVIDER_AUTOMATION_HOST_SEMANTIC_SUFFIX}"
    child_prefix = f"{host_semantic_id}:"
    deadline = time.monotonic() + PROVIDER_RENDER_TIMEOUT_SECONDS
    expected_media_resource_ids = [
        f"{CONFIGURABLE_PROVIDER_FIXTURE_EXPECTED_PACKAGE}{resource_id_suffix}"
        for resource_id_suffix in CONFIGURABLE_PROVIDER_FIXTURE_MEDIA_ACTION_RESOURCE_ID_SUFFIXES
    ]
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
        media_actions = [
            node
            for node in snapshot["nodes"]
            if node[AUTOMATION.KEY_SEMANTIC_ID].startswith(child_prefix)
            and node[AUTOMATION.KEY_CLICKABLE]
            and node[AUTOMATION.KEY_VISIBLE]
            and str(node[AUTOMATION.KEY_RESOURCE_ID]).endswith(
                CONFIGURABLE_PROVIDER_FIXTURE_MEDIA_ACTION_RESOURCE_ID_SUFFIXES,
            )
        ]
        media_resource_ids = [
            str(node[AUTOMATION.KEY_RESOURCE_ID])
            for node in media_actions
        ]
        if host is not None and media_resource_ids == expected_media_resource_ids:
            previous, play_pause, next_track = media_actions
            return host, previous, play_pause, next_track
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError("the configurable provider exposed no ordered media controls")


def test_provider_media_controls_rotate_semantic_ids_and_dispatch_in_order(
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
        host, previous, play_pause, next_track = wait_for_configurable_provider_media_action_nodes(
            provider_control,
            provider_id,
        )
        assert host[AUTOMATION.KEY_ROLE] == PROVIDER_AUTOMATION_ROLE
        wait_for_physical_text_bounds(CONFIGURABLE_PROVIDER_FIXTURE_MEDIA_PAUSED_TRACK_ONE_STATUS)
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
            for node in (host, previous, play_pause, next_track):
                assert AUTOMATION.require_node(
                    mcp_snapshot,
                    node[AUTOMATION.KEY_SEMANTIC_ID],
                ) == node
        finally:
            MCP.delete_session(arguments, session_id)
        AUTOMATION.request(
            provider_control,
            AUTOMATION.TYPE_TAP,
            semanticId=next_track[AUTOMATION.KEY_SEMANTIC_ID],
        )
        wait_for_physical_text_bounds(CONFIGURABLE_PROVIDER_FIXTURE_MEDIA_PAUSED_TRACK_TWO_STATUS)
        _, refreshed_previous, refreshed_play_pause, refreshed_next_track = (
            wait_for_configurable_provider_media_action_nodes(provider_control, provider_id)
        )
        assert refreshed_previous[AUTOMATION.KEY_SEMANTIC_ID] != previous[AUTOMATION.KEY_SEMANTIC_ID]
        assert refreshed_play_pause[AUTOMATION.KEY_SEMANTIC_ID] != play_pause[AUTOMATION.KEY_SEMANTIC_ID]
        assert refreshed_next_track[AUTOMATION.KEY_SEMANTIC_ID] != next_track[AUTOMATION.KEY_SEMANTIC_ID]
        with pytest.raises(AUTOMATION.WebSocketProtocolError, match="semantic ID was not found"):
            AUTOMATION.request(
                provider_control,
                AUTOMATION.TYPE_TAP,
                semanticId=next_track[AUTOMATION.KEY_SEMANTIC_ID],
            )
        AUTOMATION.request(
            provider_control,
            AUTOMATION.TYPE_TAP,
            semanticId=refreshed_play_pause[AUTOMATION.KEY_SEMANTIC_ID],
        )
        wait_for_physical_text_bounds(CONFIGURABLE_PROVIDER_FIXTURE_MEDIA_PLAYING_TRACK_TWO_STATUS)
        _, refreshed_previous, _, _ = wait_for_configurable_provider_media_action_nodes(
            provider_control,
            provider_id,
        )
        AUTOMATION.request(
            provider_control,
            AUTOMATION.TYPE_TAP,
            semanticId=refreshed_previous[AUTOMATION.KEY_SEMANTIC_ID],
        )
        wait_for_physical_text_bounds(CONFIGURABLE_PROVIDER_FIXTURE_MEDIA_PLAYING_TRACK_ONE_STATUS)
        run_device_operation("screenshot")
        shutil.copyfile(
            ARTIFACT_DIRECTORY / "screenshot.png",
            ARTIFACT_DIRECTORY / CONFIGURABLE_PROVIDER_FIXTURE_MEDIA_ACTIONS_ARTIFACT_NAME,
        )
    finally:
        provider_control.close()
