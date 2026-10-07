"""Theme application, settings, reset, safe mode, and picker boundaries."""

from . import *


def wait_for_rendering_asset_reload(record_count: int) -> None:
    deadline = time.monotonic() + CONFIGURATION_SAVE_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        if any(
            record.get("event") == "configuration_assets_reloaded"
            for record in device_log_records()[record_count:]
        ):
            return
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError("editing a public rendering asset did not refresh the home")


def test_direct_theme_picker_applies_reapplies_and_ignores_invalid_sdcard_files(
    websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    theme = custom_theme_document()
    custom_theme_artifact = ARTIFACT_DIRECTORY / THEME_ARTIFACT_NAME
    invalid_theme_artifact = ARTIFACT_DIRECTORY / THEME_INVALID_ARTIFACT_NAME
    wallpaper_artifact = ARTIFACT_DIRECTORY / THEME_WALLPAPER_ARTIFACT_NAME
    custom_theme_artifact.write_text(json.dumps(theme), encoding="utf-8")
    write_test_wallpaper(wallpaper_artifact)
    invalid_theme_artifact.write_text(
        json.dumps({"version": 1, "title": THEME_INVALID_TITLE}),
        encoding="utf-8",
    )
    try:
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=original,
        )
        wait_for_physical_text_bounds(FIXTURE_WIDGET_TITLES["welcome"])
        run_device_operation(
            "file-push",
            DEVICE_FILE=(
                f"{THEME_WALLPAPER_DEVICE_DIRECTORY}/{THEME_WALLPAPER_ARTIFACT_NAME}"
            ),
            ARTIFACT_FILE=THEME_WALLPAPER_ARTIFACT_NAME,
        )
        run_device_operation(
            "file-push",
            DEVICE_FILE=f"{THEME_DEVICE_DIRECTORY}/{THEME_ARTIFACT_NAME}",
            ARTIFACT_FILE=THEME_ARTIFACT_NAME,
        )
        run_device_operation(
            "file-push",
            DEVICE_FILE=f"{THEME_DEVICE_DIRECTORY}/{THEME_INVALID_ARTIFACT_NAME}",
            ARTIFACT_FILE=THEME_INVALID_ARTIFACT_NAME,
        )
        open_command_sheet()
        wait_for_snapshot_node(
            websocket_control,
            COMMAND_SETTINGS_SEMANTIC_ID,
        )
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_TAP,
            semanticId=COMMAND_SETTINGS_SEMANTIC_ID,
        )
        wait_for_snapshot_node(websocket_control, THEME_SETTINGS_SEMANTIC_ID)
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_TAP,
            semanticId=THEME_SETTINGS_SEMANTIC_ID,
        )
        theme_picker_snapshot = wait_for_snapshot_node(websocket_control, THEME_SEMANTIC_ID)
        invalid_theme_semantic_id = f"theme:{THEME_INVALID_ID}"
        assert all(
            node.get("semanticId") != invalid_theme_semantic_id
            for node in theme_picker_snapshot["nodes"]
        )

        log_record_count = len(device_log_records())
        direct_tap(wait_for_physical_button_bounds(THEME_CUSTOM_TITLE))
        applied = AUTOMATION.require_configuration(
            AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
        )
        assert_theme_applied(applied)
        wait_for_physical_text_bounds(FIXTURE_WIDGET_TITLES["welcome"])
        run_device_operation("screenshot")
        shutil.copyfile(
            ARTIFACT_DIRECTORY / "screenshot.png",
            ARTIFACT_DIRECTORY / THEME_SCREENSHOT_ARTIFACT_NAME,
        )
        applied_events = {
            record["event"]
            for record in device_log_records()[log_record_count:]
        }
        assert "launcher_background_rendered" in applied_events
        assert "launcher_wallpaper_fallback" not in applied_events

        dirty = copy.deepcopy(applied)
        configuration_widget(dirty, "welcome")["style"] = {
            "background": {"color": THEME_REBEL_COLOR},
        }
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=dirty,
        )
        open_command_sheet()
        wait_for_snapshot_node(
            websocket_control,
            COMMAND_SETTINGS_SEMANTIC_ID,
        )
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_TAP,
            semanticId=COMMAND_SETTINGS_SEMANTIC_ID,
        )
        wait_for_snapshot_node(websocket_control, THEME_SETTINGS_SEMANTIC_ID)
        wait_for_physical_button_bounds(f"Theme: {THEME_CUSTOM_TITLE}, modified")
        direct_tap(wait_for_physical_button_bounds(f"Theme: {THEME_CUSTOM_TITLE}, modified"))
        wait_for_snapshot_node(websocket_control, THEME_SEMANTIC_ID)
        direct_tap(wait_for_physical_button_bounds(THEME_CUSTOM_TITLE))
        reapplied = AUTOMATION.require_configuration(
            AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
        )
        assert_theme_applied(reapplied)

        reloaded_theme = copy.deepcopy(theme)
        reloaded_theme["background"]["color"] = "#350040"
        reloaded_theme["widgetStyle"]["background"]["color"] = "#3D1257"
        original_theme_text = json.dumps(theme)
        reloaded_theme_text = json.dumps(reloaded_theme)
        assert len(reloaded_theme_text) == len(original_theme_text)
        custom_theme_artifact.write_text(reloaded_theme_text, encoding="utf-8")
        reload_log_record_count = len(device_log_records())
        run_device_operation(
            "file-push",
            DEVICE_FILE=f"{THEME_DEVICE_DIRECTORY}/{THEME_ARTIFACT_NAME}",
            ARTIFACT_FILE=THEME_ARTIFACT_NAME,
        )
        wait_for_rendering_asset_reload(reload_log_record_count)
        run_device_operation("screenshot")
        assert (ARTIFACT_DIRECTORY / "screenshot.png").stat().st_size > 0
        assert_theme_applied(
            AUTOMATION.require_configuration(
                AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
            ),
        )
        reloaded_events = {
            record["event"]
            for record in device_log_records()[reload_log_record_count:]
        }
        assert "configuration_assets_reloaded" in reloaded_events

        events = {record["event"] for record in device_log_records()}
        assert {"theme_applied", "theme_catalogue_loaded", "theme_catalogue_rejected"} <= events
    finally:
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=original,
        )
        device_command(
            "rm -f {}/{} {}/{} {}/{}".format(
                THEME_DEVICE_DIRECTORY,
                THEME_ARTIFACT_NAME,
                THEME_DEVICE_DIRECTORY,
                THEME_INVALID_ARTIFACT_NAME,
                THEME_WALLPAPER_DEVICE_DIRECTORY,
                THEME_WALLPAPER_ARTIFACT_NAME,
            ),
        )


def test_websocket_reset_keeps_other_root_namespaces(
    websocket_control: socket.socket,
) -> None:
    configured = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    configured[TEST_HARNESS_NAMESPACE] = copy.deepcopy(TEST_HARNESS_NAMESPACE_VALUE)
    configured["scripts"] = [
        {
            "id": LUA_RESET_BUNDLED_SCRIPT_ID,
            "title": LUA_RESET_BUNDLED_SCRIPT_TITLE,
            "enabled": True,
            "apiVersion": LUA_SCRIPT_API_VERSION,
            "source": LUA_RESET_COLLISION_SOURCE,
            "state": {},
        },
        {
            "id": LUA_RESET_USER_SCRIPT_ID,
            "title": LUA_RESET_USER_SCRIPT_TITLE,
            "enabled": True,
            "apiVersion": LUA_SCRIPT_API_VERSION,
            "source": LUA_RESET_USER_SCRIPT_SOURCE,
            "state": LUA_RESET_USER_SCRIPT_STATE,
        },
    ]
    AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_CONFIG_REPLACE,
        config=configured,
    )
    open_command_sheet()
    AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_TAP,
        semanticId=COMMAND_SETTINGS_SEMANTIC_ID,
    )
    wait_for_snapshot_node(websocket_control, SETTINGS_RESET_SEMANTIC_ID)
    AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_TAP,
        semanticId=SETTINGS_RESET_SEMANTIC_ID,
    )
    wait_for_snapshot_node(websocket_control, SETTINGS_RESET_CONFIRM_SEMANTIC_ID)
    wait_for_physical_text_bounds("Reset Dikciz home?")
    AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_TAP,
        semanticId=SETTINGS_RESET_CONFIRM_SEMANTIC_ID,
    )
    reset = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    assert reset[TEST_HARNESS_NAMESPACE] == TEST_HARNESS_NAMESPACE_VALUE
    assert page_ids(reset) == [
        FIXTURE_HOME_PAGE_ID,
        FIXTURE_NOTES_PAGE_ID,
        FIXTURE_ACTIVITY_PAGE_ID,
    ]
    assert page_title(reset, FIXTURE_HOME_PAGE_ID) == "Home"
    assert selected_page(reset) == FIXTURE_HOME_PAGE_ID
    assert "background" not in reset["launcher"]["home"]
    assert reset["launcher"]["home"]["selectedThemeId"] == "dikciz-dark"
    assert reset["launcher"]["home"]["homePageId"] == FIXTURE_HOME_PAGE_ID
    assert reset["launcher"]["home"]["styleDefaults"] == {"widget": {}}
    reset_scripts = {script["id"]: script for script in reset["scripts"]}
    assert reset_scripts[LUA_RESET_BUNDLED_SCRIPT_ID]["title"] == LUA_RESET_BUNDLED_SCRIPT_TITLE
    assert reset_scripts[LUA_RESET_BUNDLED_SCRIPT_ID]["source"] != LUA_RESET_COLLISION_SOURCE
    assert reset_scripts[LUA_RESET_BUNDLED_SCRIPT_ID]["state"] == LUA_RESET_BUNDLED_SCRIPT_STATE
    assert reset_scripts[LUA_RESET_USER_SCRIPT_ID]["source"] == LUA_RESET_USER_SCRIPT_SOURCE
    assert reset_scripts[LUA_RESET_USER_SCRIPT_ID]["state"] == LUA_RESET_USER_SCRIPT_STATE
    with pytest.raises(AssertionError, match="configuration has no widget: fixture-detail"):
        configuration_widget(reset, "fixture-detail")


def test_websocket_seed_reset_and_wait_for_restore_the_bundled_home(
    websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    try:
        seeded = copy.deepcopy(original)
        seeded[TEST_HARNESS_NAMESPACE] = copy.deepcopy(TEST_HARNESS_NAMESPACE_VALUE)
        configuration_widget(seeded, "welcome")["state"] = {"body": "Seeded over WebSocket"}
        persisted = AUTOMATION.require_configuration(
            AUTOMATION.request(
                websocket_control,
                AUTOMATION.TYPE_CONFIG_SEED,
                config=seeded,
            ),
        )
        assert configuration_widget(persisted, "welcome")["state"] == {
            "body": "Seeded over WebSocket",
        }

        found = AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_WAIT_FOR,
            semanticId=PAGE_SCROLL_SEMANTIC_ID,
            timeoutMilliseconds=WAIT_FOR_TIMEOUT_MILLISECONDS,
        )
        assert found["found"] is True
        assert found["node"]["semanticId"] == PAGE_SCROLL_SEMANTIC_ID

        missing = AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_WAIT_FOR,
            semanticId="phase1:absent",
            timeoutMilliseconds=1,
        )
        assert missing["found"] is False

        reset = AUTOMATION.require_configuration(
            AUTOMATION.request(websocket_control, AUTOMATION.TYPE_RESET),
        )
        assert reset[TEST_HARNESS_NAMESPACE] == TEST_HARNESS_NAMESPACE_VALUE
        assert configuration_widget(reset, "welcome")["state"] != {
            "body": "Seeded over WebSocket",
        }
        assert reset["launcher"]["home"]["selectedThemeId"] == "dikciz-dark"
    finally:
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=original,
        )


def test_mcp_seed_reset_and_wait_for_restore_the_bundled_home() -> None:
    arguments = argparse.Namespace(host=AUTOMATION_HOST, port=MCP_PORT, mutate=False)
    session_id = MCP.initialize(arguments)
    original: dict[str, Any] | None = None
    try:
        MCP.notification_initialized(arguments, session_id)
        original = MCP.require_configuration(
            MCP.require_tool_result(
                MCP.call_tool(
                    arguments,
                    session_id,
                    MCP_SEED_RESET_CONFIG_GET_REQUEST_ID,
                    "dikciz_config_get",
                ),
            ),
        )
        seeded = copy.deepcopy(original)
        seeded[TEST_HARNESS_NAMESPACE] = copy.deepcopy(TEST_HARNESS_NAMESPACE_VALUE)
        configuration_widget(seeded, "welcome")["state"] = {"body": "Seeded over MCP"}
        persisted = MCP.require_configuration(
            MCP.require_tool_result(
                MCP.call_tool(
                    arguments,
                    session_id,
                    MCP_SEED_RESET_REQUEST_ID,
                    "dikciz_config_seed",
                    {"config": seeded},
                ),
            ),
        )
        assert configuration_widget(persisted, "welcome")["state"] == {"body": "Seeded over MCP"}

        found = MCP.require_tool_result(
            MCP.call_tool(
                arguments,
                session_id,
                MCP_SEED_RESET_WAIT_REQUEST_ID,
                "dikciz_wait_for",
                {
                    "semanticId": PAGE_SCROLL_SEMANTIC_ID,
                    "timeoutMilliseconds": WAIT_FOR_TIMEOUT_MILLISECONDS,
                },
            ),
        )
        assert found["found"] is True
        assert found["node"]["semanticId"] == PAGE_SCROLL_SEMANTIC_ID

        reset = MCP.require_configuration(
            MCP.require_tool_result(
                MCP.call_tool(
                    arguments,
                    session_id,
                    MCP_SEED_RESET_RESET_REQUEST_ID,
                    "dikciz_config_reset",
                ),
            ),
        )
        assert reset[TEST_HARNESS_NAMESPACE] == TEST_HARNESS_NAMESPACE_VALUE
        assert configuration_widget(reset, "welcome")["state"] != {"body": "Seeded over MCP"}
        assert reset["launcher"]["home"]["selectedThemeId"] == "dikciz-dark"
    finally:
        if original is not None:
            MCP.require_tool_result(
                MCP.call_tool(
                    arguments,
                    session_id,
                    MCP_SEED_RESET_RESET_REQUEST_ID + 1,
                    "dikciz_config_replace",
                    {"config": original},
                ),
            )
        MCP.delete_session(arguments, session_id)


def test_websocket_safe_mode_keeps_the_saved_home_and_blocks_mutation(
    websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    open_command_sheet()
    AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_TAP,
        semanticId=COMMAND_SETTINGS_SEMANTIC_ID,
    )
    wait_for_snapshot_node(websocket_control, SETTINGS_SAFE_MODE_SEMANTIC_ID)
    AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_TAP,
        semanticId=SETTINGS_SAFE_MODE_SEMANTIC_ID,
    )
    safe_snapshot = wait_for_snapshot_node(websocket_control, SAFE_MODE_RESET_SEMANTIC_ID)
    assert safe_snapshot["selectedPageId"] == FIXTURE_HOME_PAGE_ID
    wait_for_snapshot_node(websocket_control, "widget:notes-intro", should_exist=False)
    wait_for_physical_text_bounds(SAFE_MODE_MESSAGE)
    saved_while_safe = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    assert saved_while_safe == original
    assert AUTOMATION.verify_control_status(websocket_control)[AUTOMATION.KEY_SAFE_MODE] is True
    with pytest.raises(AUTOMATION.WebSocketProtocolError):
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=original,
        )
    AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_TAP,
        semanticId=SAFE_MODE_EXIT_SEMANTIC_ID,
    )
    wait_for_snapshot_node(websocket_control, SAFE_MODE_EXIT_SEMANTIC_ID, should_exist=False)
    restored = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    assert restored == original


def test_mcp_settings_renames_a_page_and_saves_it(
    websocket_control: socket.socket,
) -> None:
    open_command_sheet()
    wait_for_snapshot_node(websocket_control, COMMAND_SETTINGS_SEMANTIC_ID)
    arguments = argparse.Namespace(host=AUTOMATION_HOST, port=MCP_PORT, mutate=False)
    session_id = MCP.initialize(arguments)
    try:
        MCP.notification_initialized(arguments, session_id)
        MCP.require_tool_result(
            MCP.call_tool(
                arguments,
                session_id,
                380,
                "dikciz_tap",
                {"semanticId": COMMAND_SETTINGS_SEMANTIC_ID},
            ),
        )
        wait_for_snapshot_node(websocket_control, SETTINGS_NOTES_TITLE_SEMANTIC_ID)
        MCP.require_tool_result(
            MCP.call_tool(
                arguments,
                session_id,
                381,
                "dikciz_set_text",
                {
                    "semanticId": SETTINGS_NOTES_TITLE_SEMANTIC_ID,
                    "text": SETTINGS_RENAMED_PAGE_TITLE,
                },
            ),
        )
        MCP.require_tool_result(
            MCP.call_tool(
                arguments,
                session_id,
                382,
                "dikciz_tap",
                {"semanticId": SETTINGS_SAVE_PAGES_SEMANTIC_ID},
            ),
        )
        saved = MCP.require_configuration(
            MCP.require_tool_result(
                MCP.call_tool(arguments, session_id, 383, "dikciz_config_get"),
            ),
        )
        assert page_title(saved, FIXTURE_NOTES_PAGE_ID) == SETTINGS_RENAMED_PAGE_TITLE
    finally:
        MCP.delete_session(arguments, session_id)


def test_mcp_command_sheet_adds_a_settings_shortcut_and_persists_it(
    websocket_control: socket.socket,
) -> None:
    open_command_sheet()
    wait_for_snapshot_node(websocket_control, COMMAND_SEARCH_SEMANTIC_ID)
    arguments = argparse.Namespace(host=AUTOMATION_HOST, port=MCP_PORT, mutate=False)
    session_id = MCP.initialize(arguments)
    try:
        MCP.notification_initialized(arguments, session_id)
        before = MCP.require_configuration(
            MCP.require_tool_result(
                MCP.call_tool(arguments, session_id, 400, "dikciz_config_get"),
            ),
        )
        MCP.require_tool_result(
            MCP.call_tool(
                arguments,
                session_id,
                401,
                "dikciz_set_text",
                {
                    "semanticId": COMMAND_SEARCH_SEMANTIC_ID,
                    "text": COMMAND_UPPERCASE_SETTINGS_QUERY,
                },
            ),
        )
        app_entry_semantic_id = wait_for_command_app_entry(
            websocket_control,
            SETTINGS_APP_LABEL,
        )
        MCP.require_tool_result(
            MCP.call_tool(
                arguments,
                session_id,
                402,
                "dikciz_tap",
                {"semanticId": app_entry_semantic_id},
            ),
        )
        add_shortcut_semantic_id = f"{app_entry_semantic_id}:add-shortcut"
        wait_for_snapshot_node(websocket_control, add_shortcut_semantic_id)
        MCP.require_tool_result(
            MCP.call_tool(
                arguments,
                session_id,
                403,
                "dikciz_tap",
                {"semanticId": add_shortcut_semantic_id},
            ),
        )
        shortcut = wait_for_new_page_widget(
            websocket_control,
            FIXTURE_HOME_PAGE_ID,
            "app",
        )
        shortcut_id = shortcut["id"]
        assert isinstance(shortcut_id, str)
        assert shortcut["component"] == SETTINGS_ACTIVITY_COMPONENT
        assert shortcut["displayStyle"] == "iconLabel"
        assert shortcut["title"] == SETTINGS_APP_LABEL
        wait_for_snapshot_node(websocket_control, f"widget:{shortcut_id}")
        wait_for_physical_description_bounds(
            app_shortcut_content_description(
                SETTINGS_APP_LABEL,
                APP_SHORTCUT_DEFAULT_STYLE_LABEL,
            ),
        )
        physical_widget_bounds_for_title(SETTINGS_APP_LABEL)
        direct_page_round_trip(websocket_control)
        after_round_trip = MCP.require_configuration(
            MCP.require_tool_result(
                MCP.call_tool(arguments, session_id, 404, "dikciz_config_get"),
            ),
        )
        persisted_shortcut = configuration_widget(after_round_trip, shortcut_id)
        assert persisted_shortcut == shortcut
        assert len(selected_page_widget_ids(after_round_trip)) == len(selected_page_widget_ids(before)) + 1
        run_device_operation("screenshot")
        shutil.copyfile(
            ARTIFACT_DIRECTORY / "screenshot.png",
            ARTIFACT_DIRECTORY / COMMAND_SHORTCUT_ARTIFACT_NAME,
        )
        run_device_operation(
            "file-pull",
            DEVICE_FILE=current_device_log_path(),
            ARTIFACT_FILE=EVENT_LOG_ARTIFACT_NAME,
        )
        log_content = (ARTIFACT_DIRECTORY / EVENT_LOG_ARTIFACT_NAME).read_text(encoding="utf-8")
        records = [json.loads(line) for line in log_content.splitlines() if line]
        events = {record["event"] for record in records}
        shortcut_created_records = [
            record for record in records if record["event"] == "app_shortcut_created"
        ]
        assert any(record["display_style"] == "iconLabel" for record in shortcut_created_records)
        assert {
            "app_catalogue_loaded",
            "app_shortcut_created",
            "command_sheet_filtered",
            "command_sheet_opened",
        } <= events
        assert COMMAND_UPPERCASE_SETTINGS_QUERY not in log_content
        assert SETTINGS_APP_LABEL not in log_content
        assert any(
            record.get("semantic_id") == COMMAND_REDACTED_ADD_SHORTCUT_SEMANTIC_ID
            for record in records
        )
    finally:
        MCP.delete_session(arguments, session_id)


def test_direct_command_sheet_launches_settings_and_restores_dikciz(
    websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    search_bounds = open_command_sheet()
    direct_enter_text(search_bounds, SETTINGS_APP_LABEL)
    direct_tap(wait_for_physical_button_bounds(SETTINGS_APP_LABEL))
    direct_tap(wait_for_physical_button_bounds("Launch app"))
    try:
        assert SETTINGS_ACTIVITY_COMPONENT in run_device_operation("app-current")
    finally:
        run_device_operation(
            "app-start",
            PACKAGE_NAME=DIKCIZ_DEBUG_PACKAGE,
            ACTIVITY=DIKCIZ_DEBUG_ACTIVITY,
        )
        restored_control = wait_for_restarted_websocket_control()
    try:
        restored = AUTOMATION.require_configuration(
            AUTOMATION.request(restored_control, AUTOMATION.TYPE_CONFIG_GET),
        )
    finally:
        restored_control.close()
    assert restored == original


def test_page_navigation_is_permanent_chrome_not_a_widget(
    websocket_control: socket.socket,
) -> None:
    document = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    assert all(
        widget["type"] != "pageSwitcher"
        for page in document["launcher"]["home"]["pages"]
        for widget in page["widgets"]
    )
    snapshot = wait_for_snapshot_node(websocket_control, HORIZONTAL_PAGE_INDICATOR_SEMANTIC_ID)
    wait_for_snapshot_node(websocket_control, VERTICAL_PAGE_INDICATOR_SEMANTIC_ID)
    assert all(
        node.get("semanticId", "").startswith("widget:") is False
        or "page-switcher" not in node.get("semanticId", "")
        for node in snapshot["nodes"]
    )
    assert physical_page_indicator_bounds("horizontal")
    assert physical_page_indicator_bounds("vertical")
    direct_page_round_trip(websocket_control)
    wait_for_snapshot_node(websocket_control, HORIZONTAL_PAGE_INDICATOR_SEMANTIC_ID)
    wait_for_snapshot_node(websocket_control, VERTICAL_PAGE_INDICATOR_SEMANTIC_ID)


def test_websocket_rejects_legacy_page_switcher_widgets(
    websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    invalid = copy.deepcopy(original)
    invalid["launcher"]["home"]["pages"][0]["widgets"].append(
        {
            "id": "legacy-page-switcher",
            "type": "pageSwitcher",
            "enabled": True,
            "cell": {"column": 0, "row": 0, "columnSpan": 1, "rowSpan": 1},
        },
    )
    with pytest.raises(AUTOMATION.WebSocketProtocolError):
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=invalid,
        )
    current = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    assert current == original


def test_direct_picker_does_not_offer_page_navigation_as_a_widget(
    websocket_control: socket.socket,
) -> None:
    direct_select_notes_page(websocket_control)
    open_picker_on_selected_page()
    assert all(
        (node.get("text") or node.get("content-desc")) != "Page switcher"
        for node in physical_nodes()
    )
    device_command(f"input keyevent {KEYCODE_BACK}")


def test_direct_picker_does_not_offer_removed_group_widgets(
    websocket_control: socket.socket,
) -> None:
    direct_select_notes_page(websocket_control)
    open_picker_on_selected_page()
    assert all(
        (node.get("text") or node.get("content-desc")) != "Group"
        for node in physical_nodes()
    )
    device_command(f"input keyevent {KEYCODE_BACK}")
