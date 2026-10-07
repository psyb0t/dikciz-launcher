"""Shared setup and assertions for notification-action automation tests."""

from . import *


NOTIFICATION_ACTION_ACTION = "notificationControl"
NOTIFICATION_ACTION_ACTION_COUNT_KEY = "actionCount"
NOTIFICATION_ACTION_ACTIVITY = ".FixtureNotificationActionActivity"
NOTIFICATION_ACTION_BATTERY_EVENT = "battery"
NOTIFICATION_ACTION_BATTERY_LEVEL = 69
NOTIFICATION_ACTION_BATTERY_PRIMER_LEVEL = 68
NOTIFICATION_ACTION_BATTERY_TRIGGER_LEVEL_KEY = "batteryActionLevel"
NOTIFICATION_ACTION_CAPABILITIES = ["notificationsMetadata", "battery"]
NOTIFICATION_ACTION_COMPLETED_EVENT = "automation_action_completed"
NOTIFICATION_ACTION_DISABLED_BATTERY_TRIGGER_LEVEL = -1
NOTIFICATION_ACTION_EVENT = "notificationPosted"
NOTIFICATION_ACTION_INITIAL_STATE = {
    "actionToken": "",
    NOTIFICATION_ACTION_BATTERY_TRIGGER_LEVEL_KEY: NOTIFICATION_ACTION_DISABLED_BATTERY_TRIGGER_LEVEL,
    "hasActionTokens": False,
}
NOTIFICATION_ACTION_METADATA_INITIAL_STATE = {"hasToken": True}
NOTIFICATION_ACTION_METADATA_SOURCE = (
    'function on_event(event)\n'
    '  if event.type ~= "notificationPosted" then return end\n'
    '  return { type = "patchState", values = { hasToken = event.payload.actionTokens ~= nil } }\n'
    'end'
)
NOTIFICATION_ACTION_PACKAGE_KEY = "notificationActionPackages"
NOTIFICATION_ACTION_PREFERENCES_FILE = "dikciz_notification_action.xml"
NOTIFICATION_ACTION_PREFERENCES_PATH = f"shared_prefs/{NOTIFICATION_ACTION_PREFERENCES_FILE}"
NOTIFICATION_ACTION_OUTCOME_EXECUTED = "executed"
NOTIFICATION_ACTION_OUTCOME_PERMISSION_DENIED = "android_permission_denied"
NOTIFICATION_ACTION_OUTCOME_TARGET_UNAVAILABLE = "target_unavailable"
NOTIFICATION_ACTION_REJECTED_EVENT = "automation_action_rejected"
NOTIFICATION_ACTION_SOURCE = f"""function on_event(event)
  if event.type == "notificationPosted" then
    local actionTokens = event.payload.actionTokens
    return {{ type = "patchState", values = {{
      actionToken = actionTokens ~= nil and actionTokens[1] or "",
      hasActionTokens = actionTokens ~= nil
    }} }}
  end
  if event.type == "battery" and
      event.payload.level == context.state.{NOTIFICATION_ACTION_BATTERY_TRIGGER_LEVEL_KEY} and
      context.state.actionToken ~= "" then
    return {{ type = "notificationControl", actionToken = context.state.actionToken }}
  end
end"""
NOTIFICATION_ACTION_DIRECT_SOURCE = (
    'function on_event(event)\n'
    '  if event.type ~= "notificationPosted" then return end\n'
    '  local actionTokens = event.payload.actionTokens\n'
    '  if actionTokens == nil or actionTokens[1] == nil then return end\n'
    '  return {\n'
    '    { type = "notificationControl", actionToken = actionTokens[1] },\n'
    '    { type = "notificationControl", actionToken = actionTokens[1] }\n'
    '  }\n'
    'end'
)
NOTIFICATION_ACTION_TOKEN_KEY = "actionToken"
NOTIFICATION_ACTION_STATUS_REQUEST_ID = 81
NOTIFICATION_ACTION_TIMEOUT_SECONDS = 15


def notification_action_configuration(
    original: dict[str, Any],
    source: str,
    initial_state: dict[str, Any],
    package_name: str,
    *,
    include_battery_subscription: bool,
) -> dict[str, Any]:
    subscriptions = [
        {
            "event": NOTIFICATION_ACTION_EVENT,
            "minimumIntervalMilliseconds": 0,
            "coalescingKey": NOTIFICATION_ACTION_EVENT,
            "packages": [package_name],
        },
    ]
    if include_battery_subscription:
        subscriptions.append(
            {
                "event": NOTIFICATION_ACTION_BATTERY_EVENT,
                "minimumIntervalMilliseconds": 0,
                "coalescingKey": NOTIFICATION_ACTION_BATTERY_EVENT,
            },
        )
    configuration = automation_configuration(
        original,
        source,
        initial_state,
        NOTIFICATION_ACTION_CAPABILITIES,
        ["patchState", NOTIFICATION_ACTION_ACTION],
        subscriptions,
    )
    configuration["automation"]["policies"][0][NOTIFICATION_ACTION_PACKAGE_KEY] = [package_name]
    return configuration


def notification_action_configuration_with_battery_trigger(
    configuration: dict[str, Any],
    battery_level: int,
) -> dict[str, Any]:
    configured = copy.deepcopy(configuration)
    script = next(
        item
        for item in configured["scripts"]
        if item["id"] == AUTOMATION_LIVE_SCRIPT_ID
    )
    script["state"][NOTIFICATION_ACTION_BATTERY_TRIGGER_LEVEL_KEY] = battery_level
    return configured


def metadata_only_notification_configuration(
    original: dict[str, Any],
    package_name: str,
) -> dict[str, Any]:
    return automation_configuration(
        original,
        NOTIFICATION_ACTION_METADATA_SOURCE,
        NOTIFICATION_ACTION_METADATA_INITIAL_STATE,
        ["notificationsMetadata"],
        ["patchState"],
        [
            {
                "event": NOTIFICATION_ACTION_EVENT,
                "minimumIntervalMilliseconds": 0,
                "coalescingKey": NOTIFICATION_ACTION_EVENT,
                "packages": [package_name],
            },
        ],
    )


def start_fixture_notification_action(package_name: str) -> None:
    device_command(f"am force-stop {package_name}")
    device_command(f"am start -W -n {package_name}/{NOTIFICATION_ACTION_ACTIVITY}")


def fixture_notification_action_count(package_name: str) -> int:
    output = device_command(f"run-as {package_name} cat {NOTIFICATION_ACTION_PREFERENCES_PATH}")
    match = re.search(
        rf'name="{NOTIFICATION_ACTION_ACTION_COUNT_KEY}" value="(?P<count>\d+)"',
        output,
    )
    assert match is not None
    return int(match.group("count"))


def wait_for_fixture_notification_action_count(
    package_name: str,
    expected_count: int,
) -> int:
    deadline = time.monotonic() + NOTIFICATION_ACTION_TIMEOUT_SECONDS
    observed_count = -1
    while time.monotonic() < deadline:
        observed_count = fixture_notification_action_count(package_name)
        if observed_count == expected_count:
            return observed_count
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError(
        f"fixture notification action count did not become {expected_count}: {observed_count}",
    )


def wait_for_notification_action_token(connection: socket.socket) -> str:
    deadline = time.monotonic() + NOTIFICATION_ACTION_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        configuration = AUTOMATION.require_configuration(
            AUTOMATION.request(connection, AUTOMATION.TYPE_CONFIG_GET),
        )
        script = next(item for item in configuration["scripts"] if item["id"] == AUTOMATION_LIVE_SCRIPT_ID)
        token = script["state"].get(NOTIFICATION_ACTION_TOKEN_KEY)
        if isinstance(token, str) and token:
            return token
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError("notification action token was not persisted")


def wait_for_notification_action_outcome(
    record_count: int,
    expected_event: str,
    expected_event_type: str,
    expected_outcome: str,
) -> None:
    deadline = time.monotonic() + NOTIFICATION_ACTION_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        records = device_log_records()[record_count:]
        if any(
            record.get("action_type") == NOTIFICATION_ACTION_ACTION
            and record.get("event") == expected_event
            and record.get("event_type") == expected_event_type
            and record.get("outcome") == expected_outcome
            and record.get("script_id") == AUTOMATION_LIVE_SCRIPT_ID
            for record in records
        ):
            return
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError(
        "notification action did not report "
        f"{expected_event}: {expected_event_type}: {expected_outcome}",
    )


def notification_action_records() -> list[dict[str, Any]]:
    record_fields = (
        "action_type",
        "event",
        "event_type",
        "outcome",
        "script_id",
    )
    return [
        {field: record.get(field) for field in record_fields}
        for record in device_log_records()
        if record.get("action_type") == NOTIFICATION_ACTION_ACTION
        and record.get("script_id") == AUTOMATION_LIVE_SCRIPT_ID
    ]


def assert_mcp_notification_action_status(expected_status: dict[str, Any]) -> None:
    arguments = argparse.Namespace(
        host=AUTOMATION_HOST,
        port=MCP_PORT,
        expected_control_port=DEVICE_MCP_PORT,
        mutate=False,
    )
    session_id = MCP.initialize(arguments)
    try:
        MCP.notification_initialized(arguments, session_id)
        actual_status = MCP.require_tool_result(
            MCP.call_tool(
                arguments,
                session_id,
                NOTIFICATION_ACTION_STATUS_REQUEST_ID,
                MCP.TOOL_AUTOMATION_STATUS,
            ),
        )
        assert actual_status == expected_status
    finally:
        MCP.delete_session(arguments, session_id)
