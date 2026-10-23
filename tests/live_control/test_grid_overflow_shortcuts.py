"""Grid overflow removes shortcuts while preserving installed applications."""

from . import *
from .test_native_data import PROTOCOLS, native_call


INITIAL_COLUMNS = 8
RETAINED_SHORTCUTS = 3


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_grid_overflow_shortcuts_keep_installed_apps(websocket_control, protocol):
    configured = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    for page in configured["launcher"]["home"]["pages"]:
        page["widgets"] = []
    page = configuration_page(configured, selected_page(configured))
    page["locked"] = True
    page["widgets"] = [{
        "id": "overflow-shortcut-" + str(index),
        "type": APP_WIDGET_TYPE, "title": "Settings " + str(index),
        "component": ANDROID_SETTINGS_COMPONENT, "enabled": True,
        "cell": {"column": index, "row": 0, "columnSpan": 1, "rowSpan": 1},
    } for index in range(INITIAL_COLUMNS)]
    configured["launcher"]["home"]["nativeGrid"].update(columns=INITIAL_COLUMNS, rows=1)
    configured = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_REPLACE, config=configured),
    )
    original = configuration_page(configured, selected_page(configured))["widgets"]
    installed = AUTOMATION.request(
        websocket_control, APP_CATALOGUE_COMMAND, query=ANDROID_SETTINGS_PACKAGE,
    )["apps"]
    assert any(item["component"] == ANDROID_SETTINGS_COMPONENT for item in installed)
    fields = {"columns": RETAINED_SHORTCUTS, "rows": 1,
        "gapDp": NATIVE_GRID_GAP_DP, "outerPaddingDp": NATIVE_GRID_OUTER_PADDING_DP}
    result = native_call(protocol, AUTOMATION.TYPE_GRID_SET, fields)
    saved = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    retained = configuration_page(saved, page["id"])
    assert retained["locked"] is True
    assert retained["widgets"] == original[:RETAINED_SHORTCUTS]
    assert result["evictedWidgets"] == [
        {"pageId": page["id"], "id": item["id"]} for item in original[RETAINED_SHORTCUTS:]
    ]
    assert AUTOMATION.request(
        websocket_control, APP_CATALOGUE_COMMAND, query=ANDROID_SETTINGS_PACKAGE,
    )["apps"] == installed
    fields.update(columns=INITIAL_COLUMNS, rows=NATIVE_GRID_ROWS)
    assert native_call(protocol, AUTOMATION.TYPE_GRID_SET, fields)["evictedWidgets"] == []
    grown = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    assert configuration_page(grown, page["id"])["widgets"] == original[:RETAINED_SHORTCUTS]
