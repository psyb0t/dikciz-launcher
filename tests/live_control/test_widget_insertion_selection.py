"""Rendered insertion state, independent of persisted widget appearance."""

import base64
import struct
import zlib

from . import *
from .test_direct_widget_picker import direct_picker, EMPTY
from .test_html_widget_library import close_active_dialog, package_picker_entry_semantic_id, package_picker_load_semantic_id
from .test_app_drawer import open_drawer_through_control, filter_drawer_to_settings, open_app_actions_sheet


SELECTION_COLOR = (94, 168, 255)
EDGE_PIXELS = 8


def selection_pixels(png, bounds):
    assert png.startswith(b"\x89PNG\r\n\x1a\n")
    offset, compressed = 8, bytearray()
    while offset < len(png):
        length = struct.unpack_from(">I", png, offset)[0]
        kind, data = png[offset + 4:offset + 8], png[offset + 8:offset + 8 + length]
        if kind == b"IHDR":
            width, height, depth, color, compression, filtering, interlace = struct.unpack(">IIBBBBB", data)
            assert depth == 8 and color in (2, 6) and interlace == 0
        if kind == b"IDAT":
            compressed.extend(data)
        offset += length + 12
    channels = 4 if color == 6 else 3
    stride = width * channels
    decoded = zlib.decompress(compressed)
    previous = bytearray(stride)
    count = 0
    for y in range(height):
        start = y * (stride + 1)
        filter_kind = decoded[start]
        row = bytearray(decoded[start + 1:start + 1 + stride])
        assert filter_kind in range(5)
        for index in range(stride):
            left = row[index - channels] if index >= channels else 0
            above = previous[index]
            upper_left = previous[index - channels] if index >= channels else 0
            if filter_kind == 1:
                row[index] = (row[index] + left) & 255
            elif filter_kind == 2:
                row[index] = (row[index] + above) & 255
            elif filter_kind == 3:
                row[index] = (row[index] + (left + above) // 2) & 255
            elif filter_kind == 4:
                prediction = left + above - upper_left
                predictors = (left, above, upper_left)
                closest = min(predictors, key=lambda candidate: abs(prediction - candidate))
                row[index] = (row[index] + closest) & 255
        if bounds["top"] <= y < bounds["bottom"]:
            for x in range(max(0, bounds["left"]), min(width, bounds["right"])):
                edge = min(x - bounds["left"], bounds["right"] - x - 1, y - bounds["top"], bounds["bottom"] - y - 1)
                if edge < EDGE_PIXELS and tuple(row[x * channels:x * channels + 3]) == SELECTION_COLOR:
                    count += 1
        previous = row
    return count


def capture_selection(connection, identifier, name):
    snapshot = wait_for_snapshot_node(connection, "widget:" + identifier)
    bounds = bounds_for(snapshot, "widget:" + identifier)
    result = AUTOMATION.request(connection, "screenshot")
    png = base64.b64decode(result["pngBase64"])
    (ARTIFACT_DIRECTORY / ("insertion-selection-" + name + ".png")).write_bytes(png)
    return selection_pixels(png, bounds)


@pytest.mark.parametrize("kind", ["empty-html", "template", "dashboard", "shortcut-api", "shortcut-drawer"])
def test_insertion_selection_finishes_without_edit_outline(websocket_control, kind):
    if kind in ("shortcut-api", "shortcut-drawer"):
        direct_picker(websocket_control, "Empty HTML")
        close_active_dialog(websocket_control)
        if kind == "shortcut-api":
            AUTOMATION.request(websocket_control, "appAction", action="addShortcut", component=ANDROID_SETTINGS_COMPONENT)
        else:
            open_drawer_through_control()
            filter_drawer_to_settings()
            open_app_actions_sheet(APP_DRAWER_SETTINGS_ACTIONS_DESCRIPTION)
            direct_tap(wait_for_physical_button_bounds(APP_ACTION_ADD_SHORTCUT_LABEL))
            wait_for_snapshot_node(websocket_control, APP_DRAWER_SEARCH_SEMANTIC_ID, should_exist=False)
    else:
        query = {"empty-html": "Empty HTML", "template": "Agenda", "dashboard": "Scripts"}[kind]
        snapshot = direct_picker(websocket_control, query)
        if kind == "template":
            AUTOMATION.request(websocket_control, "tap", semanticId=package_picker_entry_semantic_id("agenda"))
            load = package_picker_load_semantic_id("agenda")
            wait_for_snapshot_node(websocket_control, load)
            AUTOMATION.request(websocket_control, "tap", semanticId=load)
        else:
            if kind == "empty-html":
                semantic_id = EMPTY
            else:
                nodes = [node for node in snapshot["nodes"] if node.get("semanticId", "").startswith("picker:widget:home:entry:")]
                assert nodes, snapshot
                semantic_id = nodes[0]["semanticId"]
            AUTOMATION.request(websocket_control, "tap", semanticId=semantic_id)
    wait_for_snapshot_node(websocket_control, "picker:widget:home:search", should_exist=False)
    config = AUTOMATION.require_configuration(AUTOMATION.request(websocket_control, "configGet"))
    widgets = configuration_page(config, "home")["widgets"]
    assert len(widgets) == 1
    identifier = widgets[0]["id"]
    time.sleep(.4)
    assert capture_selection(websocket_control, identifier, kind + "-inserted") == 0
    if kind != "empty-html":
        return
    AUTOMATION.request(websocket_control, "longPress", semanticId="widget:" + identifier + ":move-handle")
    action = "widget:" + identifier + ":resize"
    wait_for_snapshot_node(websocket_control, action)
    AUTOMATION.request(websocket_control, "tap", semanticId=action)
    assert capture_selection(websocket_control, identifier, "explicit-resize") > 0
    snapshot = AUTOMATION.request(websocket_control, "snapshot")
    direct_tap(bounds_for(snapshot, "page:canvas"))
    assert capture_selection(websocket_control, identifier, "resize-finished") == 0
    saved = AUTOMATION.require_configuration(AUTOMATION.request(websocket_control, "configGet"))
    assert configuration_widget(saved, identifier) == widgets[0]
