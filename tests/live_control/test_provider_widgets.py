"""Android provider-widget lifecycle and recovery behavior."""

from . import *


def wait_for_provider_automation_nodes(
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
        children = [
            node
            for node in snapshot["nodes"]
            if node[AUTOMATION.KEY_SEMANTIC_ID].startswith(child_prefix)
            and node[AUTOMATION.KEY_CLICKABLE]
            and node[AUTOMATION.KEY_VISIBLE]
        ]
        if host is not None and children:
            return host, children[0]
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError("the bound provider exposed no visible semantic action")


def wait_for_configurable_provider_action_nodes(
    connection: socket.socket,
    provider_id: str,
) -> tuple[dict[str, Any], dict[str, Any], dict[str, Any]]:
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
        action_nodes = [
            node
            for node in snapshot["nodes"]
            if node[AUTOMATION.KEY_SEMANTIC_ID].startswith(child_prefix)
            and node[AUTOMATION.KEY_CLICKABLE]
            and node[AUTOMATION.KEY_VISIBLE]
        ]
        primary = next(
            (
                node
                for node in action_nodes
                if str(node[AUTOMATION.KEY_RESOURCE_ID]).endswith(
                    CONFIGURABLE_PROVIDER_FIXTURE_PRIMARY_ACTION_RESOURCE_ID_SUFFIX,
                )
            ),
            None,
        )
        secondary = next(
            (
                node
                for node in action_nodes
                if str(node[AUTOMATION.KEY_RESOURCE_ID]).endswith(
                    CONFIGURABLE_PROVIDER_FIXTURE_SECONDARY_ACTION_RESOURCE_ID_SUFFIX,
                )
            ),
            None,
        )
        if host is not None and primary is not None and secondary is not None:
            return host, primary, secondary
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError("the configurable provider exposed no visible semantic actions")


def test_provider_actions_refresh_semantic_ids_after_remote_views_update(
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
        host, primary, secondary = wait_for_configurable_provider_action_nodes(
            provider_control,
            provider_id,
        )
        assert host[AUTOMATION.KEY_ROLE] == PROVIDER_AUTOMATION_ROLE
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
            assert AUTOMATION.require_node(mcp_snapshot, primary[AUTOMATION.KEY_SEMANTIC_ID]) == primary
            assert AUTOMATION.require_node(mcp_snapshot, secondary[AUTOMATION.KEY_SEMANTIC_ID]) == secondary
        finally:
            MCP.delete_session(arguments, session_id)
        AUTOMATION.request(
            provider_control,
            AUTOMATION.TYPE_TAP,
            semanticId=primary[AUTOMATION.KEY_SEMANTIC_ID],
        )
        wait_for_physical_text_bounds(CONFIGURABLE_PROVIDER_FIXTURE_PRIMARY_STATUS)
        _, refreshed_primary, refreshed_secondary = wait_for_configurable_provider_action_nodes(
            provider_control,
            provider_id,
        )
        assert refreshed_primary[AUTOMATION.KEY_SEMANTIC_ID] != primary[AUTOMATION.KEY_SEMANTIC_ID]
        assert refreshed_secondary[AUTOMATION.KEY_SEMANTIC_ID] != secondary[AUTOMATION.KEY_SEMANTIC_ID]
        with pytest.raises(AUTOMATION.WebSocketProtocolError, match="semantic ID was not found"):
            AUTOMATION.request(
                provider_control,
                AUTOMATION.TYPE_TAP,
                semanticId=primary[AUTOMATION.KEY_SEMANTIC_ID],
            )
        AUTOMATION.request(
            provider_control,
            AUTOMATION.TYPE_TAP,
            semanticId=refreshed_secondary[AUTOMATION.KEY_SEMANTIC_ID],
        )
        wait_for_physical_text_bounds(CONFIGURABLE_PROVIDER_FIXTURE_SECONDARY_STATUS)
        run_device_operation("screenshot")
        shutil.copyfile(
            ARTIFACT_DIRECTORY / "screenshot.png",
            ARTIFACT_DIRECTORY / CONFIGURABLE_PROVIDER_FIXTURE_ACTIONS_ARTIFACT_NAME,
        )
    finally:
        provider_control.close()


def test_websocket_exposes_and_invokes_bound_provider_widget_action(
    websocket_control: socket.socket,
) -> None:
    start_deskclock_provider_addition(websocket_control)
    provider_control = wait_for_restarted_websocket_control()
    resumed_control: socket.socket | None = None
    try:
        provider = wait_for_new_page_widget(provider_control, FIXTURE_NOTES_PAGE_ID, "provider")
        provider_id = provider["id"]
        assert isinstance(provider_id, str)
        host, child = wait_for_provider_automation_nodes(provider_control, provider_id)
        assert host[AUTOMATION.KEY_ROLE] == PROVIDER_AUTOMATION_ROLE
        assert child[AUTOMATION.KEY_SEMANTIC_ID].startswith(
            f"widget:{provider_id}{PROVIDER_AUTOMATION_HOST_SEMANTIC_SUFFIX}:"
        )
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
            assert AUTOMATION.require_node(mcp_snapshot, child[AUTOMATION.KEY_SEMANTIC_ID]) == child
        finally:
            MCP.delete_session(arguments, session_id)
        AUTOMATION.request(
            provider_control,
            AUTOMATION.TYPE_TAP,
            semanticId=child[AUTOMATION.KEY_SEMANTIC_ID],
        )
        wait_for_foreground_application(DESKCLOCK_APP_PACKAGE)
        run_device_operation("screenshot")
        shutil.copyfile(
            ARTIFACT_DIRECTORY / "screenshot.png",
            ARTIFACT_DIRECTORY / DESKCLOCK_PROVIDER_AUTOMATION_ACTION_ARTIFACT_NAME,
        )
        provider_control.close()
        run_device_operation("home-start")
        wait_for_foreground_application(DIKCIZ_DEBUG_PACKAGE)
        resumed_control = wait_for_restarted_websocket_control()
        wait_for_selected_page(resumed_control, FIXTURE_HOME_PAGE_ID)
        with pytest.raises(AUTOMATION.WebSocketProtocolError, match="semantic ID was not found"):
            AUTOMATION.request(
                resumed_control,
                AUTOMATION.TYPE_TAP,
                semanticId=child[AUTOMATION.KEY_SEMANTIC_ID],
            )
    finally:
        provider_control.close()
        if resumed_control is not None:
            resumed_control.close()


def test_direct_picker_binds_renders_and_deletes_deskclock_widget(
    websocket_control: socket.socket,
) -> None:
    start_deskclock_provider_addition(websocket_control)
    provider = wait_for_new_page_widget(websocket_control, FIXTURE_NOTES_PAGE_ID, "provider")
    resumed_control = wait_for_restarted_websocket_control()
    try:
        provider_id = provider["id"]
        provider_title = provider["title"]
        app_widget_id = provider["appWidgetId"]
        assert isinstance(provider_id, str)
        assert isinstance(provider_title, str)
        assert isinstance(app_widget_id, int)
        provider_semantic_id = f"widget:{provider_id}"
        wait_for_snapshot_node(resumed_control, provider_semantic_id)
        wait_for_visible_provider_content()
        run_device_operation("screenshot")
        shutil.copyfile(
            ARTIFACT_DIRECTORY / "screenshot.png",
            ARTIFACT_DIRECTORY / "deskclock-provider-added.png",
        )
        direct_long_press(
            physical_description_bounds(WIDGET_MOVE_HANDLE_DESCRIPTION.format(provider_title)),
        )
        wait_for_snapshot_node(resumed_control, f"widget:{provider_id}:resize")
        direct_tap(physical_resize_action_bounds())
        resize_bounds = physical_widget_bounds_for_title(provider_title)
        resize_start_x, resize_start_y = resize_handle_coordinate(resize_bounds, "end", "end")
        initial_provider_host_bounds = provider_host_bounds()
        initial_provider_host_height = (
            initial_provider_host_bounds["bottom"] - initial_provider_host_bounds["top"]
        )
        page_bounds = physical_page_scroll_bounds()
        maximum_provider_host_height = page_bounds["bottom"] - initial_provider_host_bounds["top"]
        expected_provider_host_height = min(
            initial_provider_host_height + DIRECT_DRAG_PIXELS,
            maximum_provider_host_height,
        )
        # A live provider host follows the drag through its own render pass, so
        # the resize needs the slower drag to receive every pointer sample.
        direct_swipe(
            (resize_start_x, resize_start_y),
            (
                resize_start_x + DIRECT_DRAG_PIXELS,
                resize_start_y + DIRECT_DRAG_PIXELS,
            ),
            duration_milliseconds=DIRECT_BOTTOM_EDGE_DRAG_DURATION_MILLISECONDS,
        )
        run_device_operation("uiautomator-dump")
        shutil.copyfile(
            ARTIFACT_DIRECTORY / "window.xml",
            ARTIFACT_DIRECTORY / DESKCLOCK_LIVE_RESIZE_UI_DUMP_ARTIFACT_NAME,
        )
        run_device_operation("screenshot")
        shutil.copyfile(
            ARTIFACT_DIRECTORY / "screenshot.png",
            ARTIFACT_DIRECTORY / DESKCLOCK_LIVE_RESIZE_ARTIFACT_NAME,
        )
        live_provider_host_bounds = wait_for_provider_host_height_at_least(
            expected_provider_host_height,
        )
        assert live_provider_host_bounds["bottom"] <= page_bounds["bottom"]
        wait_for_visible_provider_content()
        direct_tap(physical_page_indicator_bounds())
        resized = AUTOMATION.require_configuration(
            AUTOMATION.request(resumed_control, AUTOMATION.TYPE_CONFIG_GET),
        )
        resized_provider = provider_widget_by_id(resized, FIXTURE_NOTES_PAGE_ID, provider_id)
        assert resized_provider["appWidgetId"] == app_widget_id
        resized_cell = resized_provider["cell"]
        assert isinstance(resized_cell, dict)
        for field in WIDGET_CELL_FIELDS:
            assert isinstance(resized_cell[field], int)
        resized_bounds_before_round_trip = physical_widget_bounds_for_title(provider_title)
        assert_rendered_widget_has_area(resized_bounds_before_round_trip)
        assert_bounds_unchanged(
            provider_host_bounds(),
            live_provider_host_bounds,
        )
        assert bounds_contain(physical_page_scroll_bounds(), resized_bounds_before_round_trip)
        moved_provider = drag_new_provider_to_lower_viewport_edge(resumed_control, resized_provider)
        moved_cell = moved_provider["cell"]
        assert isinstance(moved_cell, dict)
        assert moved_provider["cell"]["columnSpan"] == resized_cell["columnSpan"]
        assert moved_provider["cell"]["rowSpan"] == resized_cell["rowSpan"]
        moved_bounds_before_round_trip = physical_widget_bounds_for_title(provider_title)
        wait_for_visible_provider_content()
        run_device_operation("screenshot")
        shutil.copyfile(
            ARTIFACT_DIRECTORY / "screenshot.png",
            ARTIFACT_DIRECTORY / DESKCLOCK_RESIZED_ARTIFACT_NAME,
        )
        direct_select_home_page(resumed_control)
        direct_select_notes_page(resumed_control)
        wait_for_snapshot_node(resumed_control, provider_semantic_id)
        wait_for_visible_provider_content()
        resized_bounds_after_round_trip = physical_widget_bounds_for_title(provider_title)
        assert_bounds_unchanged(resized_bounds_after_round_trip, moved_bounds_before_round_trip)
        assert_rendered_widget_has_area(resized_bounds_after_round_trip)
        assert bounds_contain(physical_page_scroll_bounds(), resized_bounds_after_round_trip)
        moved = AUTOMATION.require_configuration(
            AUTOMATION.request(resumed_control, AUTOMATION.TYPE_CONFIG_GET),
        )
        moved_provider = provider_widget_by_id(moved, FIXTURE_NOTES_PAGE_ID, provider_id)
        assert moved_provider["cell"] == moved_cell
        direct_long_press(
            physical_description_bounds(WIDGET_MOVE_HANDLE_DESCRIPTION.format(provider_title)),
        )
        direct_tap(physical_delete_action_bounds())
        wait_for_snapshot_node(resumed_control, provider_semantic_id, should_exist=False)
        saved = AUTOMATION.require_configuration(
            AUTOMATION.request(resumed_control, AUTOMATION.TYPE_CONFIG_GET),
        )
        assert all(
            widget.get("appWidgetId") != app_widget_id
            for widget in page_widgets_with_type(saved, FIXTURE_NOTES_PAGE_ID, "provider")
        )
    finally:
        resumed_control.close()


def test_direct_provider_addition_keeps_two_axis_target_page(
    websocket_control: socket.socket,
) -> None:
    before = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    provider_ids_before = {
        page_id: {
            widget["id"]
            for widget in page_widgets_with_type(before, page_id, "provider")
            if isinstance(widget.get("id"), str)
        }
        for page_id in (
            FIXTURE_HOME_PAGE_ID,
            FIXTURE_ACTIVITY_PAGE_ID,
            FIXTURE_NOTES_PAGE_ID,
        )
    }
    direct_select_activity_page(websocket_control)
    direct_select_notes_page(websocket_control)
    wait_for_selected_page(websocket_control, FIXTURE_NOTES_PAGE_ID)

    begin_deskclock_provider_addition()
    provider = wait_for_new_page_widget(
        websocket_control,
        FIXTURE_NOTES_PAGE_ID,
        "provider",
        known_widget_ids=provider_ids_before[FIXTURE_NOTES_PAGE_ID],
    )
    provider_id = provider["id"]
    provider_title = provider["title"]
    assert isinstance(provider_id, str)
    assert isinstance(provider_title, str)

    resumed_control = wait_for_restarted_websocket_control()
    try:
        wait_for_selected_page(resumed_control, FIXTURE_NOTES_PAGE_ID)
        physical_widget_bounds_for_title(provider_title)
        run_device_operation("screenshot")
        shutil.copyfile(
            ARTIFACT_DIRECTORY / "screenshot.png",
            ARTIFACT_DIRECTORY / DESKCLOCK_TWO_AXIS_TARGET_ARTIFACT_NAME,
        )
        after = AUTOMATION.require_configuration(
            AUTOMATION.request(resumed_control, AUTOMATION.TYPE_CONFIG_GET),
        )
        for page_id, provider_ids in provider_ids_before.items():
            current_provider_ids = {
                widget["id"]
                for widget in page_widgets_with_type(after, page_id, "provider")
                if isinstance(widget.get("id"), str)
            }
            if page_id == FIXTURE_NOTES_PAGE_ID:
                assert current_provider_ids == provider_ids | {provider_id}
                continue
            assert current_provider_ids == provider_ids
    finally:
        resumed_control.close()


def test_direct_provider_click_back_returns_to_launcher_and_preserves_widget(
    websocket_control: socket.socket,
) -> None:
    start_deskclock_provider_addition(websocket_control)
    provider_control = wait_for_restarted_websocket_control()
    resumed_control: socket.socket | None = None
    try:
        provider = wait_for_new_page_widget(provider_control, FIXTURE_NOTES_PAGE_ID, "provider")
        provider_id = provider["id"]
        provider_app_widget_id = provider["appWidgetId"]
        provider_component = provider["provider"]
        provider_cell = copy.deepcopy(provider["cell"])
        assert isinstance(provider_id, str)
        assert isinstance(provider_app_widget_id, int)
        assert isinstance(provider_component, str)
        provider_content_bounds = node_bounds(wait_for_visible_provider_content())
        assert provider_content_bounds is not None

        direct_tap(provider_content_bounds)
        wait_for_foreground_application(DESKCLOCK_APP_PACKAGE)
        provider_control.close()
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_TAP,
            semanticId=LUA_MANAGER_CLOSE_SEMANTIC_ID,
        )
        wait_for_foreground_application(DIKCIZ_DEBUG_PACKAGE)

        resumed_control = wait_for_restarted_websocket_control()
        resumed_snapshot = AUTOMATION.request(resumed_control, AUTOMATION.TYPE_SNAPSHOT)
        assert resumed_snapshot["selectedPageId"] == FIXTURE_NOTES_PAGE_ID
        resumed_configuration = AUTOMATION.require_configuration(
            AUTOMATION.request(resumed_control, AUTOMATION.TYPE_CONFIG_GET),
        )
        resumed_provider = provider_widget_by_id(
            resumed_configuration,
            FIXTURE_NOTES_PAGE_ID,
            provider_id,
        )
        assert resumed_provider["appWidgetId"] == provider_app_widget_id
        assert resumed_provider["provider"] == provider_component
        assert resumed_provider["cell"] == provider_cell
        wait_for_snapshot_node(resumed_control, f"widget:{provider_id}")
        wait_for_visible_provider_content()
        run_device_operation("screenshot")
        shutil.copyfile(
            ARTIFACT_DIRECTORY / "screenshot.png",
            ARTIFACT_DIRECTORY / DESKCLOCK_BACK_NAVIGATION_ARTIFACT_NAME,
        )
    finally:
        provider_control.close()
        if resumed_control is not None:
            resumed_control.close()


def test_direct_provider_title_edit_and_reconfiguration_preserve_identity(
    websocket_control: socket.socket,
) -> None:
    start_deskclock_provider_addition(websocket_control)
    created_control = wait_for_restarted_websocket_control()
    try:
        provider = wait_for_new_page_widget(created_control, FIXTURE_NOTES_PAGE_ID, "provider")
        provider_id = provider["id"]
        provider_title = provider["title"]
        provider_app_widget_id = provider["appWidgetId"]
        provider_component = provider["provider"]
        provider_cell = copy.deepcopy(provider["cell"])
        assert isinstance(provider_id, str)
        assert isinstance(provider_title, str)
        assert isinstance(provider_app_widget_id, int)
        assert isinstance(provider_component, str)
        wait_for_visible_provider_content()

        direct_open_widget_editor(provider_title)
        direct_enter_text(
            wait_for_physical_description_bounds(NATIVE_PROVIDER_WIDGET_TITLE_INPUT_DESCRIPTION),
            NATIVE_PROVIDER_EDITED_TITLE,
            description=NATIVE_PROVIDER_WIDGET_TITLE_INPUT_DESCRIPTION,
        )
        direct_tap(physical_button_bounds("Save"))
        edited = AUTOMATION.require_configuration(
            AUTOMATION.request(created_control, AUTOMATION.TYPE_CONFIG_GET),
        )
        edited_provider = provider_widget_by_id(edited, FIXTURE_NOTES_PAGE_ID, provider_id)
        assert edited_provider["title"] == NATIVE_PROVIDER_EDITED_TITLE
        assert edited_provider["appWidgetId"] == provider_app_widget_id
        assert edited_provider["provider"] == provider_component
        assert edited_provider["cell"] == provider_cell
        physical_widget_bounds_for_title(NATIVE_PROVIDER_EDITED_TITLE)

        direct_open_widget_editor(NATIVE_PROVIDER_EDITED_TITLE)
        direct_tap(wait_for_physical_button_bounds(NATIVE_PROVIDER_RECONFIGURE_LABEL))
        complete_deskclock_provider_configuration_if_requested()
        reconfigured_control = wait_for_restarted_websocket_control()
        try:
            reconfigured = AUTOMATION.require_configuration(
                AUTOMATION.request(reconfigured_control, AUTOMATION.TYPE_CONFIG_GET),
            )
            reconfigured_provider = provider_widget_by_id(
                reconfigured,
                FIXTURE_NOTES_PAGE_ID,
                provider_id,
            )
            assert reconfigured_provider["title"] == NATIVE_PROVIDER_EDITED_TITLE
            assert reconfigured_provider["appWidgetId"] == provider_app_widget_id
            assert reconfigured_provider["provider"] == provider_component
            assert reconfigured_provider["cell"] == provider_cell
            wait_for_snapshot_node(reconfigured_control, f"widget:{provider_id}")
            wait_for_visible_provider_content()
            direct_page_round_trip(reconfigured_control)
            physical_widget_bounds_for_title(NATIVE_PROVIDER_EDITED_TITLE)
            run_device_operation("screenshot")
            shutil.copyfile(
                ARTIFACT_DIRECTORY / "screenshot.png",
                ARTIFACT_DIRECTORY / DESKCLOCK_RECONFIGURED_ARTIFACT_NAME,
            )
        finally:
            reconfigured_control.close()
    finally:
        created_control.close()


def test_real_configurable_provider_cancel_and_reconfiguration_preserve_identity(
    websocket_control: socket.socket,
) -> None:
    fixture_package = configurable_provider_fixture_package()
    before = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    provider_ids_before = {
        widget["id"]
        for widget in page_widgets_with_type(before, FIXTURE_NOTES_PAGE_ID, "provider")
        if isinstance(widget.get("id"), str)
    }
    cancellation_control: socket.socket | None = None
    created_control: socket.socket | None = None
    reconfigured_control: socket.socket | None = None
    restarted_control: socket.socket | None = None
    try:
        start_configurable_provider_fixture_addition(websocket_control)
        complete_configurable_provider_fixture_configuration(save=False)
        cancellation_control = wait_for_restarted_websocket_control()
        cancelled = AUTOMATION.require_configuration(
            AUTOMATION.request(cancellation_control, AUTOMATION.TYPE_CONFIG_GET),
        )
        assert {
            widget["id"]
            for widget in page_widgets_with_type(cancelled, FIXTURE_NOTES_PAGE_ID, "provider")
            if isinstance(widget.get("id"), str)
        } == provider_ids_before

        start_configurable_provider_fixture_addition(cancellation_control)
        complete_configurable_provider_fixture_configuration(save=True)
        created_control = wait_for_restarted_websocket_control()
        provider = wait_for_new_page_widget(
            created_control,
            FIXTURE_NOTES_PAGE_ID,
            "provider",
            known_widget_ids=provider_ids_before,
        )
        provider_id = provider["id"]
        provider_title = provider["title"]
        provider_app_widget_id = provider["appWidgetId"]
        provider_component = provider["provider"]
        assert isinstance(provider_id, str)
        assert isinstance(provider_title, str)
        assert isinstance(provider_app_widget_id, int)
        assert provider_component.startswith(f"{fixture_package}/")
        provider_cell = copy.deepcopy(provider["cell"])
        wait_for_visible_provider_content()

        direct_open_widget_editor(provider_title)
        direct_enter_text(
            wait_for_physical_description_bounds(NATIVE_PROVIDER_WIDGET_TITLE_INPUT_DESCRIPTION),
            NATIVE_PROVIDER_EDITED_TITLE,
            description=NATIVE_PROVIDER_WIDGET_TITLE_INPUT_DESCRIPTION,
        )
        direct_tap(physical_button_bounds("Save"))
        edited = AUTOMATION.require_configuration(
            AUTOMATION.request(created_control, AUTOMATION.TYPE_CONFIG_GET),
        )
        edited_provider = provider_widget_by_id(edited, FIXTURE_NOTES_PAGE_ID, provider_id)
        assert edited_provider["appWidgetId"] == provider_app_widget_id
        assert edited_provider["provider"] == provider_component
        assert edited_provider["title"] == NATIVE_PROVIDER_EDITED_TITLE

        direct_open_widget_editor(NATIVE_PROVIDER_EDITED_TITLE)
        direct_tap(wait_for_physical_button_bounds(NATIVE_PROVIDER_RECONFIGURE_LABEL))
        complete_configurable_provider_fixture_configuration(save=True)
        reconfigured_control = wait_for_restarted_websocket_control()
        reconfigured = AUTOMATION.require_configuration(
            AUTOMATION.request(reconfigured_control, AUTOMATION.TYPE_CONFIG_GET),
        )
        reconfigured_provider = provider_widget_by_id(
            reconfigured,
            FIXTURE_NOTES_PAGE_ID,
            provider_id,
        )
        assert reconfigured_provider["appWidgetId"] == provider_app_widget_id
        assert reconfigured_provider["provider"] == provider_component
        assert reconfigured_provider["title"] == NATIVE_PROVIDER_EDITED_TITLE
        wait_for_visible_provider_content()
        direct_long_press(
            physical_description_bounds(
                WIDGET_MOVE_HANDLE_DESCRIPTION.format(NATIVE_PROVIDER_EDITED_TITLE),
            ),
        )
        wait_for_snapshot_node(reconfigured_control, f"widget:{provider_id}:resize")
        direct_tap(physical_resize_action_bounds())
        resize_bounds = physical_widget_bounds_for_title(NATIVE_PROVIDER_EDITED_TITLE)
        resize_start_x, resize_start_y = resize_handle_coordinate(resize_bounds, "end", "end")
        direct_swipe(
            (resize_start_x, resize_start_y),
            (
                resize_start_x + DIRECT_DRAG_PIXELS,
                resize_start_y + DIRECT_DRAG_PIXELS,
            ),
            duration_milliseconds=DIRECT_BOTTOM_EDGE_DRAG_DURATION_MILLISECONDS,
        )
        direct_tap(physical_page_indicator_bounds())
        resized = AUTOMATION.require_configuration(
            AUTOMATION.request(reconfigured_control, AUTOMATION.TYPE_CONFIG_GET),
        )
        resized_provider = provider_widget_by_id(
            resized,
            FIXTURE_NOTES_PAGE_ID,
            provider_id,
        )
        assert resized_provider["appWidgetId"] == provider_app_widget_id
        assert resized_provider["provider"] == provider_component
        assert resized_provider["cell"] != provider_cell
        moved_provider = drag_provider_within_initial_viewport(
            reconfigured_control,
            resized_provider,
        )
        assert moved_provider["appWidgetId"] == provider_app_widget_id
        assert moved_provider["provider"] == provider_component
        direct_page_round_trip(reconfigured_control)
        physical_widget_bounds_for_title(NATIVE_PROVIDER_EDITED_TITLE)

        run_device_operation("app-stop")
        run_device_operation("home-start")
        restarted_control = wait_for_restarted_websocket_control()
        restarted = AUTOMATION.require_configuration(
            AUTOMATION.request(restarted_control, AUTOMATION.TYPE_CONFIG_GET),
        )
        restarted_provider = provider_widget_by_id(
            restarted,
            FIXTURE_NOTES_PAGE_ID,
            provider_id,
        )
        assert restarted_provider["appWidgetId"] == provider_app_widget_id
        assert restarted_provider["provider"] == provider_component
        assert restarted_provider["title"] == NATIVE_PROVIDER_EDITED_TITLE
        direct_select_notes_page(restarted_control)
        AUTOMATION.request(
            restarted_control,
            AUTOMATION.TYPE_SCROLL_TO,
            semanticId=f"widget:{provider_id}",
        )
        wait_for_snapshot_node(restarted_control, f"widget:{provider_id}")
        wait_for_visible_provider_content()

        direct_long_press(
            physical_description_bounds(
                WIDGET_MOVE_HANDLE_DESCRIPTION.format(NATIVE_PROVIDER_EDITED_TITLE),
            ),
        )
        direct_tap(physical_delete_action_bounds())
        wait_for_snapshot_node(restarted_control, f"widget:{provider_id}", should_exist=False)
        saved = AUTOMATION.require_configuration(
            AUTOMATION.request(restarted_control, AUTOMATION.TYPE_CONFIG_GET),
        )
        assert all(
            widget.get("appWidgetId") != provider_app_widget_id
            for widget in page_widgets_with_type(saved, FIXTURE_NOTES_PAGE_ID, "provider")
        )
        run_device_operation("screenshot")
        shutil.copyfile(
            ARTIFACT_DIRECTORY / "screenshot.png",
            ARTIFACT_DIRECTORY / CONFIGURABLE_PROVIDER_RECONFIGURED_ARTIFACT_NAME,
        )
    finally:
        for control in (
            restarted_control,
            reconfigured_control,
            created_control,
            cancellation_control,
        ):
            if control is not None:
                control.close()


def test_direct_unavailable_provider_recovers_without_corrupting_configuration(
    websocket_control: socket.socket,
) -> None:
    expected = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    configured = copy.deepcopy(expected)
    notes_page = configuration_page(configured, FIXTURE_NOTES_PAGE_ID)
    notes_widgets = notes_page["widgets"]
    assert isinstance(notes_widgets, list)
    unavailable_provider = {
        "id": UNAVAILABLE_PROVIDER_ID,
        "type": "provider",
        "title": UNAVAILABLE_PROVIDER_TITLE,
        "provider": UNAVAILABLE_PROVIDER_COMPONENT,
        "appWidgetId": UNAVAILABLE_PROVIDER_APP_WIDGET_ID,
        "enabled": True,
        "locked": UNAVAILABLE_PROVIDER_LOCKED,
        "cell": UNAVAILABLE_PROVIDER_CELL,
    }
    notes_widgets.append(unavailable_provider)
    AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_CONFIG_REPLACE,
        config=configured,
    )
    direct_select_notes_page(websocket_control)
    provider_semantic_id = f"widget:{UNAVAILABLE_PROVIDER_ID}"
    wait_for_snapshot_node(websocket_control, provider_semantic_id)
    physical_widget_bounds_for_title(UNAVAILABLE_PROVIDER_TITLE)
    physical_text_bounds(UNAVAILABLE_PROVIDER_PLACEHOLDER)
    saved = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    assert (
        provider_widget_by_id(saved, FIXTURE_NOTES_PAGE_ID, UNAVAILABLE_PROVIDER_ID)
        == unavailable_provider
    )

    run_device_operation("app-stop")
    run_device_operation("home-start")
    restarted_control = wait_for_restarted_websocket_control()
    try:
        direct_select_notes_page(restarted_control)
        wait_for_snapshot_node(restarted_control, provider_semantic_id)
        physical_widget_bounds_for_title(UNAVAILABLE_PROVIDER_TITLE)
        physical_text_bounds(UNAVAILABLE_PROVIDER_PLACEHOLDER)
        run_device_operation("screenshot")
        shutil.copyfile(
            ARTIFACT_DIRECTORY / "screenshot.png",
            ARTIFACT_DIRECTORY / UNAVAILABLE_PROVIDER_ARTIFACT_NAME,
        )
        restarted = AUTOMATION.require_configuration(
            AUTOMATION.request(restarted_control, AUTOMATION.TYPE_CONFIG_GET),
        )
        assert (
            provider_widget_by_id(restarted, FIXTURE_NOTES_PAGE_ID, UNAVAILABLE_PROVIDER_ID)
            == unavailable_provider
        )
        direct_long_press(
            physical_description_bounds(
                WIDGET_MOVE_HANDLE_DESCRIPTION.format(UNAVAILABLE_PROVIDER_TITLE),
            ),
        )
        direct_tap(physical_delete_action_bounds())
        wait_for_snapshot_node(restarted_control, provider_semantic_id, should_exist=False)
        deleted = AUTOMATION.require_configuration(
            AUTOMATION.request(restarted_control, AUTOMATION.TYPE_CONFIG_GET),
        )
        assert all(
            widget.get("id") != UNAVAILABLE_PROVIDER_ID
            for widget in page_widgets_with_type(deleted, FIXTURE_NOTES_PAGE_ID, "provider")
        )
    finally:
        restarted_control.close()


def test_direct_picker_keeps_chrome_search_widget_available_after_lower_viewport_drop(
    websocket_control: socket.socket,
) -> None:
    start_chrome_search_provider_addition(websocket_control)
    try:
        provider = wait_for_new_page_widget(websocket_control, FIXTURE_NOTES_PAGE_ID, "provider")
    except AssertionError as exception:
        binding_events = [
            record
            for record in device_log_records()
            if record.get("event") in WIDGET_BIND_EVENTS
        ]
        raise AssertionError(f"Chrome provider did not bind: {binding_events}") from exception
    provider_id = provider["id"]
    provider_title = provider["title"]
    app_widget_id = provider["appWidgetId"]
    provider_component = provider["provider"]
    assert isinstance(provider_id, str)
    assert isinstance(provider_title, str)
    assert isinstance(app_widget_id, int)
    assert isinstance(provider_component, str)
    assert provider_component.startswith(f"{CHROME_PROVIDER_PACKAGE}/")
    provider_semantic_id = f"widget:{provider_id}"
    wait_for_snapshot_node(websocket_control, provider_semantic_id)
    wait_for_visible_provider_content()
    moved_provider = drag_new_provider_to_lower_viewport_edge(websocket_control, provider)
    moved_cell = moved_provider["cell"]
    assert isinstance(moved_cell, dict)
    direct_page_round_trip(websocket_control)
    wait_for_snapshot_node(websocket_control, provider_semantic_id)
    wait_for_visible_provider_content()
    saved = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    saved_provider = provider_widget_by_id(saved, FIXTURE_NOTES_PAGE_ID, provider_id)
    assert saved_provider["appWidgetId"] == app_widget_id
    assert saved_provider["cell"] == moved_cell
    assert saved_provider["provider"] == provider_component
    assert physical_widget_bounds_for_title(provider_title)["bottom"] > 0
    run_device_operation("screenshot")
    shutil.copyfile(
        ARTIFACT_DIRECTORY / "screenshot.png",
        ARTIFACT_DIRECTORY / CHROME_SEARCH_MOVED_ARTIFACT_NAME,
    )
