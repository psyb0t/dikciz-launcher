"""Live package removal and intentional native icon movement."""

from . import *
from .test_native_data import native_call, PROTOCOLS
from .test_home_showcase import showcase_document, showcase_probe, SHOWCASE_WIDGET
from .test_app_groups import (
    apply_group_page, app_widget, group_page_widgets,
    FIRST_APP_WIDGET_ID, SECOND_APP_WIDGET_ID, SETTINGS_TILE_TITLE, CLOCK_TILE_TITLE,
    SETTINGS_COMPONENT, CLOCK_COMPONENT, FIRST_COLUMN, SECOND_COLUMN,
)

REMOVAL_DEADLINE_SECONDS = 6


def test_app_list_and_hold_package_removal_is_live(websocket_control):
    package = CONFIGURABLE_PROVIDER_FIXTURE_EXPECTED_PACKAGE
    assert not AUTOMATION.request(websocket_control, "appCatalogue", query=package)["apps"]
    run_device_operation("install", APK=CONFIGURABLE_PROVIDER_FIXTURE_APK_PATH)
    try:
        showcase_document(websocket_control, "most-used-apps")
        showcase_probe(websocket_control, lambda value: CONFIGURABLE_PROVIDER_FIXTURE_APPLICATION_LABEL in value.get("labels", []))
        started = time.monotonic()
        uninstall_configurable_provider_fixture(package)
        assert not AUTOMATION.request(websocket_control, "appCatalogue", query=package)["apps"]
        while time.monotonic() - started < REMOVAL_DEADLINE_SECONDS:
            config = AUTOMATION.require_configuration(AUTOMATION.request(websocket_control, "configGet"))
            labels = configuration_widget(config, SHOWCASE_WIDGET)["state"].get("probe", {}).get("labels", [])
            if CONFIGURABLE_PROVIDER_FIXTURE_APPLICATION_LABEL not in labels:
                run_device_operation("screenshot")
                shutil.copyfile(ARTIFACT_DIRECTORY/"screenshot.png", ARTIFACT_DIRECTORY/"most-used-after-uninstall.png")
                return
            time.sleep(0.1)
        raise AssertionError("Uninstalled app remained visible until the polling refresh")
    finally:
        if package in device_command("pm list packages " + package):
            uninstall_configurable_provider_fixture(package)


@pytest.mark.parametrize("hold_seconds,should_group", [(0, False), (0.55, False), (1.15, True)])
def test_app_list_and_hold_requires_one_second(websocket_control, hold_seconds, should_group):
    apply_group_page(websocket_control, [
        app_widget(FIRST_APP_WIDGET_ID, SETTINGS_TILE_TITLE, SETTINGS_COMPONENT, FIRST_COLUMN),
        app_widget(SECOND_APP_WIDGET_ID, CLOCK_TILE_TITLE, CLOCK_COMPONENT, SECOND_COLUMN),
    ])
    source = center(wait_for_physical_description_bounds(WIDGET_MOVE_HANDLE_DESCRIPTION.format(CLOCK_TILE_TITLE)))
    target = center(wait_for_physical_description_bounds(WIDGET_MOVE_HANDLE_DESCRIPTION.format(SETTINGS_TILE_TITLE)))
    if hold_seconds == 0:
        direct_swipe(source, (target[0], source[1]), 180)
    else:
        direct_hold_then_drag(source, (target[0], source[1]), hold_seconds)
    time.sleep(1)
    widgets = group_page_widgets(websocket_control)
    if should_group:
        assert len(widgets) == 1 and widgets[0]["type"] == "appGroup"
    else:
        assert [(widget["id"], widget["type"], widget["cell"]["column"]) for widget in widgets] == [
            (FIRST_APP_WIDGET_ID, "app", FIRST_COLUMN), (SECOND_APP_WIDGET_ID, "app", SECOND_COLUMN),
        ]


def test_app_list_and_hold_release_opens_edit_actions(websocket_control):
    apply_group_page(websocket_control, [
        app_widget(FIRST_APP_WIDGET_ID, SETTINGS_TILE_TITLE, SETTINGS_COMPONENT, FIRST_COLUMN),
        app_widget(SECOND_APP_WIDGET_ID, CLOCK_TILE_TITLE, CLOCK_COMPONENT, SECOND_COLUMN),
    ])
    direct_long_press(wait_for_physical_description_bounds(WIDGET_MOVE_HANDLE_DESCRIPTION.format(CLOCK_TILE_TITLE)))
    wait_for_snapshot_node(websocket_control, "widget:clock-tile:move")
    assert len(group_page_widgets(websocket_control)) == 2


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_app_list_and_hold_subscription_patch_contract(websocket_control, protocol):
    configured = AUTOMATION.require_configuration(AUTOMATION.request(websocket_control, "configGet"))
    configuration_page(configured, "home")["widgets"] = [{
        "id":"event-patch", "title":"Event patch", "type":"html", "enabled":True,
        "html":"<p>Event patch</p>", "css":"", "javascript":"", "state":{},
        "eventSubscriptions":[{"event":"example.keep", "minimumIntervalMilliseconds":0}],
        "cell":{"column":0,"row":0,"columnSpan":2,"rowSpan":2},
    }]
    AUTOMATION.request(websocket_control, "configReplace", config=configured)
    address = "1H1V-event-patch"
    original = native_call(protocol, "widgetGet", {"widgetAddress":address})["eventSubscriptions"]
    expected = [*original, {"event":"packageChanged", "minimumIntervalMilliseconds":0}]
    def patch(values, error=None):
        return native_call(protocol, "automationDispatch", {
            "sourceWidgetAddress":address,
            "actions":[{"type":"patchWidget", "widgetAddress":address, "values":{"eventSubscriptions":values}}],
        }, error=error)
    def wait_for_subscriptions(expected_subscriptions):
        deadline = time.monotonic() + CONFIGURATION_SAVE_TIMEOUT_SECONDS
        while time.monotonic() < deadline:
            observed = native_call(protocol, "widgetGet", {"widgetAddress":address})["eventSubscriptions"]
            if observed == expected_subscriptions:
                return
            time.sleep(0.1)
        raise AssertionError(f"Subscription refresh timed out: {observed!r}")
    maximum = [{"event":f"example.event{index}", "minimumIntervalMilliseconds":0} for index in range(128)]
    patch(maximum)
    wait_for_subscriptions(maximum)
    patch(expected)
    wait_for_subscriptions(expected)
    for invalid in (None, {}, "packageChanged", [expected[0]] * 129):
        patch(invalid, error="validation_failed")
        wait_for_subscriptions(expected)
    for invalid in (expected + [expected[0]], [{"event":"invalid/name", "minimumIntervalMilliseconds":0}]):
        result = patch(invalid)
        assert result["actions"][0]["outcome"] == "configuration_rejected"
        wait_for_subscriptions(expected)
    patch([])
    wait_for_subscriptions([])


@pytest.mark.parametrize("scenario", [
    "test_dropping_an_app_tile_on_another_builds_one_group",
    "test_dropping_a_group_on_an_app_tile_trades_their_cells",
    "test_tapping_an_app_tile_launches_its_app",
])
def test_app_list_and_hold_existing_icon_flows(websocket_control, scenario):
    from . import test_app_groups
    getattr(test_app_groups, scenario)(websocket_control)
