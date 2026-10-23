"""Flat Dikciz package choices through the actual native picker."""

from . import *
from .test_html_widget_library import (
    close_active_dialog,
    package_picker_entry_semantic_id,
    package_picker_load_semantic_id,
)

SEARCH = "picker:widget:home:search"
EMPTY = "picker:widget:home:entry:native:html"
DEMO_PACKAGES = {
    "voice-notes": "Voice notes",
    "agenda": "Agenda",
    "time-weather": "Time and weather",
    "device-status": "Device status",
}


def direct_picker(
    connection: socket.socket,
    query: str = "Dikciz",
) -> dict[str, object]:
    config = AUTOMATION.require_configuration(AUTOMATION.request(connection, "configGet"))
    bundled = json.loads(
        (Path(__file__).resolve().parents[2] / "app/src/dikciz/assets/config.json").read_text(),
    )
    config["limits"]["maxTextCharacters"] = bundled["limits"]["maxTextCharacters"]
    configuration_page(config, "home")["widgets"] = []
    AUTOMATION.request(connection, "configReplace", config=config)
    AUTOMATION.request(connection, "longPress", semanticId="page:canvas")
    wait_for_snapshot_node(connection, "page:menu:home:widgets")
    AUTOMATION.request(connection, "tap", semanticId="page:menu:home:widgets")
    wait_for_snapshot_node(connection, SEARCH)
    AUTOMATION.request(connection, "setText", semanticId=SEARCH, text=query)
    return AUTOMATION.request(connection, "snapshot")


def test_direct_widget_picker_has_descriptions_and_searches_them(
    websocket_control: socket.socket,
) -> None:
    snapshot = direct_picker(websocket_control)
    for package_id, title in DEMO_PACKAGES.items():
        node = AUTOMATION.require_node(snapshot, package_picker_entry_semantic_id(package_id))
        assert title in node["text"] and "\n" in node["text"], node
    empty = AUTOMATION.require_node(snapshot, EMPTY)
    assert "Empty HTML widget" in empty["text"]
    assert "Start" in empty["text"]
    assert not any(node.get("text") == "New HTML widget" for node in snapshot["nodes"])
    run_device_operation("screenshot")
    shutil.copyfile(
        ARTIFACT_DIRECTORY / "screenshot.png",
        ARTIFACT_DIRECTORY / "direct-widget-picker.png",
    )
    AUTOMATION.request(websocket_control, "setText", semanticId=SEARCH, text="microphone")
    snapshot = wait_for_snapshot_node(websocket_control, package_picker_entry_semantic_id("voice-notes"))
    assert not any(
        node.get("semanticId") == package_picker_entry_semantic_id("agenda")
        for node in snapshot["nodes"]
    )
    AUTOMATION.request(websocket_control, "setText", semanticId=SEARCH, text="no-such-widget-zzzz")
    wait_for_snapshot_node(websocket_control, package_picker_entry_semantic_id("voice-notes"), should_exist=False)


@pytest.mark.parametrize("package_id", tuple(DEMO_PACKAGES))
def test_direct_widget_picker_loads_complete_packages(
    websocket_control: socket.socket,
    package_id: str,
) -> None:
    direct_picker(websocket_control, DEMO_PACKAGES[package_id])
    entry = package_picker_entry_semantic_id(package_id)
    wait_for_snapshot_node(websocket_control, entry)
    AUTOMATION.request(websocket_control, "tap", semanticId=entry)
    load = package_picker_load_semantic_id(package_id)
    wait_for_snapshot_node(websocket_control, load)
    before = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, "configGet"),
    )
    assert configuration_page(before, "home")["widgets"] == []
    AUTOMATION.request(websocket_control, "tap", semanticId=load)
    config = AUTOMATION.require_configuration(AUTOMATION.request(websocket_control, "configGet"))
    widgets = configuration_page(config, "home")["widgets"]
    assert len(widgets) == 1 and widgets[0]["type"] == "html"
    assert all(widgets[0][field] for field in ("html", "css", "javascript"))
    assert widgets[0]["state"] == {}
    wait_for_snapshot_node(websocket_control, entry, should_exist=False)


def test_direct_widget_picker_empty_creates_without_a_submenu(
    websocket_control: socket.socket,
) -> None:
    direct_picker(websocket_control, "Empty HTML")
    wait_for_snapshot_node(websocket_control, EMPTY)
    AUTOMATION.request(websocket_control, "tap", semanticId=EMPTY)
    config = AUTOMATION.require_configuration(AUTOMATION.request(websocket_control, "configGet"))
    widgets = configuration_page(config, "home")["widgets"]
    assert len(widgets) == 1 and widgets[0]["type"] == "html"
    assert widgets[0]["state"] == {} and widgets[0]["javascript"] == ""
    assert "<" in widgets[0]["html"]
    wait_for_snapshot_node(websocket_control, SEARCH, should_exist=False)


def test_direct_widget_picker_cancel_preview_leaves_home_unchanged(
    websocket_control: socket.socket,
) -> None:
    direct_picker(websocket_control, "Voice notes")
    AUTOMATION.request(
        websocket_control,
        "tap",
        semanticId=package_picker_entry_semantic_id("voice-notes"),
    )
    load = package_picker_load_semantic_id("voice-notes")
    wait_for_snapshot_node(websocket_control, load)
    close_active_dialog(websocket_control)
    config = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, "configGet"),
    )
    assert configuration_page(config, "home")["widgets"] == []
    wait_for_snapshot_node(websocket_control, load, should_exist=False)
