"""Observed Android activity launches through the shared command target."""

import re
from concurrent.futures import ThreadPoolExecutor

from . import *
from .test_app_drawer import open_drawer_through_control
from .test_automation_manual_trigger import MANUAL_TRIGGER_CAPABILITY
from .test_app_groups import apply_group_page, app_widget, FIRST_APP_WIDGET_ID, FIRST_COLUMN, SETTINGS_TILE_TITLE
from . import test_remote_device_control as REMOTE


REMOTE.TOOLS["launchApp"] = "dikciz_launch_app"
REMOTE.TOOLS["appAction"] = "dikciz_app_action"
REMOTE.TOOLS["intent"] = "dikciz_intent"
REMOTE.TOOLS["automationDispatch"] = "dikciz_automation_dispatch"
PROTOCOLS = ("websocket", "mcp", "http")


@pytest.mark.parametrize("protocol", PROTOCOLS)
@pytest.mark.parametrize("action, outcome", (("launch", "launched"), ("appInfo", "app_info_opened")))
def test_activity_launch_verified_foreground(websocket_control, protocol, action, outcome):
    try:
        emulator_dikciz_accessibility_set(True)
        restore_system_home()
        result = REMOTE.remote_call(protocol, "appAction", {
            "action": action, "component": ANDROID_SETTINGS_COMPONENT,
        })
        assert result["outcome"] == outcome, result
        launch, = result["activityLaunches"]
        assert launch["outcome"] == "launched", launch
        assert launch["verified"] is True
        assert launch["accepted"] is True
        assert launch["expectedPackageName"] == ANDROID_SETTINGS_PACKAGE
        assert launch["observedPackageName"] == ANDROID_SETTINGS_PACKAGE
        assert len(launch["launchId"]) == 36
        wait_for_foreground_application(ANDROID_SETTINGS_PACKAGE)
    finally:
        restore_system_home()
        emulator_dikciz_accessibility_set(False)


@pytest.mark.parametrize("protocol", PROTOCOLS)
@pytest.mark.parametrize("action", ("launch", "appInfo"))
def test_activity_launch_unavailable_observation_is_not_success(websocket_control, protocol, action):
    try:
        emulator_dikciz_accessibility_set(False)
        result = REMOTE.remote_call(protocol, "appAction", {
            "action": action, "component": ANDROID_SETTINGS_COMPONENT,
        })
        assert result["outcome"] == "launch_unverified", result
        launch, = result["activityLaunches"]
        assert launch["verified"] is False
        assert launch["accepted"] is True
        assert launch["reason"] == "accessibility_not_enabled"
        assert launch["observedPackageName"] is None
    finally:
        restore_system_home()


@pytest.mark.parametrize("protocol", PROTOCOLS)
@pytest.mark.parametrize("action_type", ("launchApp", "appAction", "explicitIntent"))
def test_activity_launch_typed_actions_share_observed_outcome(websocket_control, protocol, action_type):
    try:
        emulator_dikciz_accessibility_set(True)
        source = ACCESSIBILITY_HTML_SOURCE_WIDGET_ADDRESS
        action = {"type": action_type, "component": ANDROID_SETTINGS_COMPONENT}
        if action_type == "appAction":
            action["action"] = "launch"
        if action_type == "explicitIntent":
            action.update(intentType="activity", action="android.intent.action.MAIN")
        result = REMOTE.remote_call(protocol, "automationDispatch", {
            "sourceWidgetAddress": source, "actions": [action],
        })
        assert result["actions"][0]["outcome"] == "launched", result
        launch, = result["activityLaunches"]
        assert launch["verified"] is True
        assert launch["observedPackageName"] == ANDROID_SETTINGS_PACKAGE
        wait_for_foreground_application(ANDROID_SETTINGS_PACKAGE)
    finally:
        restore_system_home()
        emulator_dikciz_accessibility_set(False)


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_activity_launch_settings_intent_has_separate_task(websocket_control, protocol):
    try:
        emulator_dikciz_accessibility_set(True)
        restore_system_home()
        home = device_command("dumpsys activity activities")
        home_task = re.search(r"topResumedActivity=.*? t([0-9]+)[} ]", home)
        assert home_task, home
        result = REMOTE.remote_call(protocol, "intent", {
            "intentType": "activity", "action": "android.settings.ACCESSIBILITY_SETTINGS",
        })
        launch, = result["activityLaunches"]
        assert launch["verified"] is True, launch
        wait_for_foreground_application(ANDROID_SETTINGS_PACKAGE)
        tasks = device_command("dumpsys activity activities")
        settings_task = re.search(r"topResumedActivity=.*? t([0-9]+)[} ]", tasks)
        assert settings_task, tasks
        assert settings_task.group(1) != home_task.group(1), tasks
        emulator_device_orientation_set("landscape")
        restore_system_home()
        emulator_device_orientation_set("portrait")
        wait_for_foreground_application(DIKCIZ_DEBUG_PACKAGE)
        assert REMOTE.remote_call(protocol, "deviceInfo")["application"]["packageName"] == DIKCIZ_DEBUG_PACKAGE
    finally:
        emulator_device_orientation_set("portrait")
        restore_system_home()
        emulator_dikciz_accessibility_set(False)


@pytest.mark.parametrize("route", ("drawer", "shortcut"))
def test_activity_launch_native_routes_include_verification(websocket_control, route):
    try:
        emulator_dikciz_accessibility_set(True)
        if route == "drawer":
            open_drawer_through_control()
            AUTOMATION.request(websocket_control, AUTOMATION.TYPE_SET_TEXT,
                               semanticId=APP_DRAWER_SEARCH_SEMANTIC_ID, text=ANDROID_SETTINGS_LABEL_QUERY)
            semantic_id = APP_DRAWER_APP_PREFIX + ANDROID_SETTINGS_COMPONENT
            wait_for_snapshot_node(websocket_control, semantic_id)
            result = AUTOMATION.request(websocket_control, AUTOMATION.TYPE_TAP, semanticId=semantic_id)
        else:
            apply_group_page(websocket_control, [app_widget(
                FIRST_APP_WIDGET_ID, SETTINGS_TILE_TITLE, ANDROID_SETTINGS_COMPONENT, FIRST_COLUMN,
            )])
            result = REMOTE.remote_call("http", "launchApp", {"semanticId": "widget:" + FIRST_APP_WIDGET_ID})
            assert result["launched"] is True, result
        launch, = result["activityLaunches"]
        assert launch["verified"] is True, launch
        wait_for_foreground_application(ANDROID_SETTINGS_PACKAGE)
        run_device_operation("screenshot")
    finally:
        restore_system_home()
        emulator_dikciz_accessibility_set(False)


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_activity_launch_self_finishing_target_is_not_observed(
    websocket_control, installed_configurable_provider_fixture, protocol,
):
    try:
        emulator_dikciz_accessibility_set(True)
        package = configurable_provider_fixture_package()
        result = REMOTE.remote_call(protocol, "automationDispatch", {
            "sourceWidgetAddress": ACCESSIBILITY_HTML_SOURCE_WIDGET_ADDRESS,
            "actions": [{"type": "explicitIntent", "intentType": "activity",
                         "action": "android.intent.action.MAIN",
                         "component": package + "/eu.psyb0t.dikciz.fixture.FixtureWidgetConfigurationActivity"}],
        })
        assert result["actions"][0]["outcome"] == "launch_not_observed", result
        launch, = result["activityLaunches"]
        assert launch["accepted"] is True
        assert launch["verified"] is False
        assert launch["outcome"] == "launch_not_observed"
        assert launch["observedPackageName"] != package
        wait_for_foreground_application(DIKCIZ_DEBUG_PACKAGE)
    finally:
        restore_system_home()
        emulator_dikciz_accessibility_set(False)


@pytest.mark.parametrize("mode", ("command", "dispatch"))
def test_activity_launch_html_returns_observation(websocket_control, mode):
    original = current_configuration(websocket_control)
    try:
        emulator_dikciz_accessibility_set(True)
        configured = copy.deepcopy(original)
        page = configured["launcher"]["home"]["pages"][0]
        widget = page["widgets"][0]
        if mode == "command":
            operation = "dikciz.command(\"appAction\", {action: \"launch\", component: " + json.dumps(ANDROID_SETTINGS_COMPONENT) + "})"
        else:
            operation = "dikciz.dispatch({actions: [{type: \"launchApp\", component: " + json.dumps(ANDROID_SETTINGS_COMPONENT) + "}]})"
        widget["html"] = '<p>Checking external activity launch</p>'
        widget["css"] = 'body{margin:0;padding:16px;color:#edf3fb;font:16px system-ui}'
        widget["cell"] = {"column": 0, "row": 0, "columnSpan": 4, "rowSpan": 2}
        page["widgets"] = [widget]
        widget["javascript"] = (
            'window.addEventListener("dikciz-ready", async () => {'
            'const result = await ' + operation + ';'
            'await selfWidget.patchState({launchVerification: JSON.stringify(result)});'
            '});'
        )
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_REPLACE, config=configured)
        wait_for_foreground_application(ANDROID_SETTINGS_PACKAGE)
        deadline = time.monotonic() + CONFIGURATION_SAVE_TIMEOUT_SECONDS
        observed = None
        while time.monotonic() < deadline:
            current = current_configuration(websocket_control)
            observed = current["launcher"]["home"]["pages"][0]["widgets"][0].get("state", {}).get("launchVerification")
            if observed is not None:
                break
            time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
        assert observed is not None
        observed = json.loads(observed)
        launch, = observed["activityLaunches"]
        assert launch["verified"] is True, observed
        assert launch["observedPackageName"] == ANDROID_SETTINGS_PACKAGE
        if mode == "command":
            assert observed["outcome"] == "launched"
        else:
            assert observed["actions"][0]["outcome"] == "launched"
    finally:
        restore_system_home()
        emulator_dikciz_accessibility_set(False)


def test_activity_launch_lua_uses_verified_outcome(websocket_control):
    try:
        emulator_dikciz_accessibility_set(True)
        original = current_configuration(websocket_control)
        configured = automation_configuration(
            original,
            'function on_event(event) return { actions = {{ type = "launchApp", component = "' + ANDROID_SETTINGS_COMPONENT + '" }} } end',
            {}, [MANUAL_TRIGGER_CAPABILITY], ["launchApp"],
            [{"event": "manual", "minimumIntervalMilliseconds": 0, "coalescingKey": "verified-launch"}],
        )
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_REPLACE, config=configured)
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_AUTOMATION_TRIGGER, scriptId=AUTOMATION_LIVE_SCRIPT_ID)
        wait_for_foreground_application(ANDROID_SETTINGS_PACKAGE)
        deadline = time.monotonic() + CONFIGURATION_SAVE_TIMEOUT_SECONDS
        records = []
        while time.monotonic() < deadline:
            records = AUTOMATION.request(websocket_control, AUTOMATION.TYPE_SCRIPT_LOGS)["records"]
            if any(record.get("scriptId") == AUTOMATION_LIVE_SCRIPT_ID
                   and record.get("event") == "automation_action_completed"
                   and record.get("outcome") == "launched" for record in records):
                break
            time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
        else:
            raise AssertionError(records)
        status = json.loads(device_command("cat " + LUA_ANDROID_ACCESS_RUN_STATUS_PATH))
        assert status["outcome"] == "completed", status
    finally:
        restore_system_home()
        emulator_dikciz_accessibility_set(False)


def test_activity_launch_locked_device_is_not_success(websocket_control):
    previous = device_command("locksettings get-disabled").strip()
    assert previous in ("true", "false"), previous
    try:
        device_command("locksettings set-disabled false")
        emulator_dikciz_accessibility_set(True)
        device_command("input keyevent KEYCODE_SLEEP")
        for protocol in PROTOCOLS:
            result = REMOTE.remote_call(protocol, "appAction", {
                "action": "launch", "component": ANDROID_SETTINGS_COMPONENT,
            })
            assert result["outcome"] == "device_locked", result
            launch, = result["activityLaunches"]
            assert launch["verified"] is False
            assert launch["reason"] == "device_locked"
    finally:
        device_command("input keyevent KEYCODE_WAKEUP")
        device_command("wm dismiss-keyguard")
        device_command("locksettings set-disabled " + previous)
        restore_system_home()
        emulator_dikciz_accessibility_set(False)


def test_activity_launch_concurrent_requests_keep_separate_evidence(websocket_control):
    try:
        emulator_dikciz_accessibility_set(True)
        with ThreadPoolExecutor(max_workers=len(PROTOCOLS)) as executor:
            futures = [executor.submit(REMOTE.remote_call, protocol, "appAction", {
                "action": "launch", "component": ANDROID_SETTINGS_COMPONENT,
            }) for protocol in PROTOCOLS]
            results = [future.result() for future in futures]
        ids = []
        for result in results:
            launch, = result["activityLaunches"]
            assert result["outcome"] == "launched", result
            assert launch["verified"] is True
            assert launch["observedPackageName"] == ANDROID_SETTINGS_PACKAGE
            ids.append(launch["launchId"])
        assert len(set(ids)) == len(PROTOCOLS)
    finally:
        restore_system_home()
        emulator_dikciz_accessibility_set(False)


def test_activity_launch_batch_preserves_each_observation(websocket_control):
    from .test_app_groups import CLOCK_COMPONENT

    try:
        emulator_dikciz_accessibility_set(True)
        components = (ANDROID_SETTINGS_COMPONENT, CLOCK_COMPONENT)
        result = REMOTE.remote_call("http", "automationDispatch", {
            "sourceWidgetAddress": ACCESSIBILITY_HTML_SOURCE_WIDGET_ADDRESS,
            "actions": [{"type": "launchApp", "component": component} for component in components],
        })
        assert [action["outcome"] for action in result["actions"]] == ["launched"] * len(components), result
        assert len(result["activityLaunches"]) == len(components)
        for launch, component in zip(result["activityLaunches"], components, strict=True):
            assert launch["verified"] is True
            assert launch["observedPackageName"] == component.split("/", 1)[0]
        assert len({launch["launchId"] for launch in result["activityLaunches"]}) == len(components)
    finally:
        restore_system_home()
        emulator_dikciz_accessibility_set(False)
