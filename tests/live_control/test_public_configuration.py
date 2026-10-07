"""Public HTML widget configuration reload and recovery behavior."""

from . import *


def test_adb_pull_edit_push_reloads_the_live_home(
    websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    run_device_operation(
        "file-pull",
        DEVICE_FILE=CONFIG_DEVICE_PATH,
        ARTIFACT_FILE=CONFIG_ROOT_ARTIFACT_NAME,
    )
    run_device_operation(
        "file-pull",
        DEVICE_FILE=CONFIG_WELCOME_WIDGET_DEVICE_PATH,
        ARTIFACT_FILE=CONFIG_WELCOME_WIDGET_ARTIFACT_NAME,
    )
    root_artifact = ARTIFACT_DIRECTORY / CONFIG_ROOT_ARTIFACT_NAME
    widget_artifact = ARTIFACT_DIRECTORY / CONFIG_WELCOME_WIDGET_ARTIFACT_NAME
    root = json.loads(root_artifact.read_text(encoding="utf-8"))
    widget = json.loads(widget_artifact.read_text(encoding="utf-8"))
    try:
        assert root["launcher"]["home"]["pages"] == [
            FIXTURE_HOME_PAGE_ID,
            FIXTURE_NOTES_PAGE_ID,
            FIXTURE_ACTIVITY_PAGE_ID,
        ]
        assert widget["id"] == "welcome"
        assert widget["type"] == "html"
        widget["style"] = {
            "background": {"color": "#173A5E", "opacity": 0.75},
            "border": {"color": "#FFFFFFFF", "widths": {"all": 1}},
            "text": {"font": {"source": "bundled", "id": "comic-neue"}},
        }
        widget["locked"] = True
        root["launcher"]["home"]["selectedPageId"] = FIXTURE_HOME_PAGE_ID
        widget_artifact.write_text(json.dumps(widget, separators=(",", ":")), encoding="utf-8")
        root_artifact.write_text(json.dumps(root, separators=(",", ":")), encoding="utf-8")
        run_device_operation(
            "file-push",
            DEVICE_FILE=CONFIG_WELCOME_WIDGET_DEVICE_PATH,
            ARTIFACT_FILE=CONFIG_WELCOME_WIDGET_ARTIFACT_NAME,
        )
        run_device_operation(
            "file-push",
            DEVICE_FILE=CONFIG_DEVICE_PATH,
            ARTIFACT_FILE=CONFIG_ROOT_ARTIFACT_NAME,
        )
        wait_for_selected_page(websocket_control, FIXTURE_HOME_PAGE_ID)
        wait_for_snapshot_node(websocket_control, WELCOME_MOVE_HANDLE_SEMANTIC_ID, should_exist=False)
        physical_widget_bounds()
        run_device_operation("screenshot")
        shutil.copyfile(
            ARTIFACT_DIRECTORY / "screenshot.png",
            ARTIFACT_DIRECTORY / ADB_STYLE_LOCK_ARTIFACT_NAME,
        )
        run_device_operation("uiautomator-dump")
        run_device_operation("ui-layout")
        assert (ARTIFACT_DIRECTORY / "screenshot.png").stat().st_size > 0
        assert (ARTIFACT_DIRECTORY / "window.xml").stat().st_size > 0
        assert (ARTIFACT_DIRECTORY / "layout.json").stat().st_size > 0
        reloaded = AUTOMATION.require_configuration(
            AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
        )
        assert selected_page(reloaded) == FIXTURE_HOME_PAGE_ID
        assert configuration_widget(reloaded, "welcome")["locked"] is True
    finally:
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=original,
        )


def test_public_page_and_html_widget_files_reload_independently(
    websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    run_device_operation(
        "file-pull",
        DEVICE_FILE=CONFIG_HOME_PAGE_DEVICE_PATH,
        ARTIFACT_FILE=CONFIG_HOME_PAGE_ARTIFACT_NAME,
    )
    run_device_operation(
        "file-pull",
        DEVICE_FILE=CONFIG_WELCOME_WIDGET_DEVICE_PATH,
        ARTIFACT_FILE=CONFIG_WELCOME_WIDGET_ARTIFACT_NAME,
    )
    page_artifact = ARTIFACT_DIRECTORY / CONFIG_HOME_PAGE_ARTIFACT_NAME
    widget_artifact = ARTIFACT_DIRECTORY / CONFIG_WELCOME_WIDGET_ARTIFACT_NAME
    page = json.loads(page_artifact.read_text(encoding="utf-8"))
    widget = json.loads(widget_artifact.read_text(encoding="utf-8"))
    try:
        assert page["id"] == FIXTURE_HOME_PAGE_ID
        assert page["widgets"] == ["welcome", "focus", "fixture-detail"]
        assert widget["id"] == "welcome"
        assert widget["type"] == "html"
        page["title"] = "Home from page.json"
        widget["html"] = "<p>Widget reload from welcome.json</p>"
        page_artifact.write_text(json.dumps(page, separators=(",", ":")), encoding="utf-8")
        widget_artifact.write_text(json.dumps(widget, separators=(",", ":")), encoding="utf-8")
        run_device_operation(
            "file-push",
            DEVICE_FILE=CONFIG_HOME_PAGE_DEVICE_PATH,
            ARTIFACT_FILE=CONFIG_HOME_PAGE_ARTIFACT_NAME,
        )
        run_device_operation(
            "file-push",
            DEVICE_FILE=CONFIG_WELCOME_WIDGET_DEVICE_PATH,
            ARTIFACT_FILE=CONFIG_WELCOME_WIDGET_ARTIFACT_NAME,
        )
        wait_for_physical_text_bounds("Widget reload from welcome.json")
        reloaded = AUTOMATION.require_configuration(
            AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
        )
        assert page_title(reloaded, FIXTURE_HOME_PAGE_ID) == "Home from page.json"
        assert configuration_widget(reloaded, "welcome")["html"] == "<p>Widget reload from welcome.json</p>"
    finally:
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=original,
        )


def test_invalid_adb_push_keeps_the_last_valid_home_and_recovers(
    websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    initial_bounds = physical_widget_bounds()
    artifact_path = ARTIFACT_DIRECTORY / CONFIG_INVALID_ARTIFACT_NAME
    run_device_operation(
        "file-pull",
        DEVICE_FILE=CONFIG_DEVICE_PATH,
        ARTIFACT_FILE=CONFIG_ROOT_ARTIFACT_NAME,
    )
    root_document = json.loads(
        (ARTIFACT_DIRECTORY / CONFIG_ROOT_ARTIFACT_NAME).read_text(encoding="utf-8"),
    )
    try:
        artifact_path.write_text(CONFIG_INVALID_CONTENT, encoding="utf-8")
        run_device_operation(
            "file-push",
            DEVICE_FILE=CONFIG_DEVICE_PATH,
            ARTIFACT_FILE=CONFIG_INVALID_ARTIFACT_NAME,
        )
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
        assert any(
            record["event"] == CONFIGURATION_RELOAD_REJECTED_EVENT
            for record in device_log_records()
        )
        rejected_snapshot = AUTOMATION.request(websocket_control, AUTOMATION.TYPE_SNAPSHOT)
        assert rejected_snapshot["selectedPageId"] == selected_page(original)
        assert_bounds_unchanged(physical_widget_bounds(), initial_bounds)

        corrected = copy.deepcopy(root_document)
        corrected["launcher"]["home"]["selectedPageId"] = FIXTURE_NOTES_PAGE_ID
        artifact_path.write_text(json.dumps(corrected, separators=(",", ":")), encoding="utf-8")
        run_device_operation(
            "file-push",
            DEVICE_FILE=CONFIG_DEVICE_PATH,
            ARTIFACT_FILE=CONFIG_INVALID_ARTIFACT_NAME,
        )
        wait_for_selected_page(websocket_control, FIXTURE_NOTES_PAGE_ID)
    finally:
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=original,
        )
