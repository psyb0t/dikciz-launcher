"""External app tasks and startup build identification."""

import re

from . import *
from .test_app_drawer import open_drawer_through_control
from .test_app_groups import (
    apply_group_page,
    app_widget,
    FIRST_APP_WIDGET_ID,
    CLOCK_TILE_TITLE,
    FIRST_COLUMN,
    CLOCK_COMPONENT,
)
from .test_remote_device_control import remote_call


LAUNCH_ROUTES = ("drawer", "shortcut", "api")
STARTUP_VERSION = "startup:version"
HOME_ACTIVITY = "org.fossify.home.dikciz.DikcizHomeActivity"
CLOCK_PACKAGE = CLOCK_COMPONENT.split("/", 1)[0]


def launch_clock(connection, route):
    if route == "drawer":
        open_drawer_through_control()
        AUTOMATION.request(
            connection,
            AUTOMATION.TYPE_SET_TEXT,
            semanticId=APP_DRAWER_SEARCH_SEMANTIC_ID,
            text=CLOCK_TILE_TITLE,
        )
        semantic_id = APP_DRAWER_APP_PREFIX + CLOCK_COMPONENT
        wait_for_snapshot_node(connection, semantic_id)
        AUTOMATION.request(connection, AUTOMATION.TYPE_TAP, semanticId=semantic_id)
    elif route == "shortcut":
        apply_group_page(connection, [app_widget(
            FIRST_APP_WIDGET_ID, CLOCK_TILE_TITLE, CLOCK_COMPONENT, FIRST_COLUMN,
        )])
        direct_tap(physical_description_bounds(WIDGET_MOVE_HANDLE_DESCRIPTION.format(CLOCK_TILE_TITLE)))
    else:
        result = AUTOMATION.request(
            connection, APP_ACTION_COMMAND, action=APP_ACTION_TYPE_LAUNCH, component=CLOCK_COMPONENT,
        )
        assert result["outcome"] == "launched"
    wait_for_foreground_application(CLOCK_PACKAGE)


def recent_task(component, expected_task=None):
    package, activity = component.split("/", 1)
    short_component = component.replace(package + "/" + package + ".", package + "/.")
    if expected_task is None:
        activities = device_command("dumpsys activity activities")
        resumed = [line for line in activities.splitlines()
                   if "ResumedActivity" in line and short_component in line]
        assert resumed, activities
        match = re.search(r"\bt(\d+)\b", resumed[0])
        assert match, resumed
        expected_task = match.group(1)
    recents = device_command("dumpsys activity recents")
    entries = re.split(r"(?m)^\s*\* Recent #", recents)
    matches = [entry for entry in entries if re.search(r"Task\{[^\n]* #" + expected_task + r"\b", entry)
               and "intent={" in entry
               and f"cmp={short_component}" in entry.split("intent={", 1)[1].splitlines()[0]]
    assert matches, "App has no independent recent task: " + recents
    entry = matches[0]
    task = re.search(r"Task\{[^\n]* #(\d+)\b", entry)
    assert task, entry
    return task.group(1)


@pytest.mark.parametrize("route", LAUNCH_ROUTES)
def test_launcher_regression_app_survives_home_in_recents(websocket_control, route):
    try:
        launch_clock(websocket_control, route)
        task = recent_task(CLOCK_COMPONENT)
        device_command("input keyevent KEYCODE_HOME")
        wait_for_foreground_application(DIKCIZ_DEBUG_PACKAGE)
        assert recent_task(CLOCK_COMPONENT, task) == task
        device_command("input keyevent KEYCODE_APP_SWITCH")
        card = wait_for_physical_description_bounds(CLOCK_TILE_TITLE)
        run_device_operation("screenshot")
        direct_tap(card)
        wait_for_foreground_application(CLOCK_PACKAGE)
        assert recent_task(CLOCK_COMPONENT) == task
        device_command("input keyevent KEYCODE_HOME")
        launch_clock(websocket_control, "api")
        assert recent_task(CLOCK_COMPONENT) == task
    finally:
        restore_system_home()


def test_launcher_regression_splash_displays_running_version_at_bottom():
    expected_version = remote_call("http", "deviceInfo")["application"]["versionName"]
    run_device_operation("app-stop", PACKAGE_NAME=DIKCIZ_DEBUG_PACKAGE)
    run_device_operation("app-start", PACKAGE_NAME=DIKCIZ_DEBUG_PACKAGE, ACTIVITY=HOME_ACTIVITY)
    connection = wait_for_restarted_websocket_control()
    try:
        snapshot = wait_for_snapshot_node(connection, STARTUP_VERSION)
        version = AUTOMATION.require_node(snapshot, STARTUP_VERSION)
        assert expected_version in version["text"]
        assert any(kind in version["text"] for kind in ("debug", "minified", "release"))
        message = AUTOMATION.require_node(snapshot, "startup:message")
        assert version["bounds"]["top"] > message["bounds"]["bottom"]
        run_device_operation("screenshot")
        screenshot = ARTIFACT_DIRECTORY / "screenshot.png"
        (ARTIFACT_DIRECTORY / "startup-version.png").write_bytes(screenshot.read_bytes())
        AUTOMATION.request(connection, AUTOMATION.TYPE_TAP, semanticId=STARTUP_DISMISS_SEMANTIC_ID)
        wait_for_snapshot_node(connection, STARTUP_VERSION, should_exist=False)
    finally:
        connection.close()


def test_launcher_regression_standard_activity_retains_task_and_state(
    websocket_control,
    installed_configurable_provider_fixture,
):
    package = CONFIGURABLE_PROVIDER_FIXTURE_EXPECTED_PACKAGE
    component = f"{package}/{ACCESSIBILITY_THIRD_PARTY_ACTIVITY_CLASS}"
    title = CONFIGURABLE_PROVIDER_FIXTURE_APPLICATION_LABEL
    try:
        apply_group_page(websocket_control, [app_widget(
            FIRST_APP_WIDGET_ID,
            title,
            component,
            FIRST_COLUMN,
        )])
        direct_tap(physical_description_bounds(WIDGET_MOVE_HANDLE_DESCRIPTION.format(title)))
        wait_for_foreground_application(package)
        task = recent_task(component)
        direct_tap(wait_for_physical_text_bounds(ACCESSIBILITY_THIRD_PARTY_ACTION_TEXT))
        expected_count = ACCESSIBILITY_THIRD_PARTY_ACTION_COUNT_FORMAT.format(1)
        wait_for_physical_text_bounds(expected_count)
        device_command("input keyevent KEYCODE_HOME")
        wait_for_foreground_application(DIKCIZ_DEBUG_PACKAGE)
        assert recent_task(component, task) == task
        direct_tap(physical_description_bounds(WIDGET_MOVE_HANDLE_DESCRIPTION.format(title)))
        wait_for_foreground_application(package)
        assert recent_task(component) == task
        wait_for_physical_text_bounds(expected_count)
    finally:
        restore_system_home()
