"""Constants for the live script-dashboard and page-address bridge scenario."""

from .constants import FIXTURE_HOME_PAGE_ID


SCRIPT_DASHBOARD_ACTION_EDIT = "edit"
SCRIPT_DASHBOARD_ACTION_LOGS = "logs"
SCRIPT_DASHBOARD_ACTION_TOGGLE_ENABLED = "toggle-enabled"
SCRIPT_DASHBOARD_ARTIFACT_NAME = "script-dashboard.png"
SCRIPT_DASHBOARD_DEFAULT_ARTIFACT_NAME = "default-script-dashboard.png"
SCRIPT_DASHBOARD_DEFAULT_SCRIPT_IDS = {
    "hourly-clock",
    "notification-digest",
    "screen-lock",
}
SCRIPT_DASHBOARD_DEFAULT_WIDGET_ID = "script-dashboard"
SCRIPT_DASHBOARD_EXPECTED_PATCHED_TEXT = "Focus toggle is off"
SCRIPT_DASHBOARD_EXPECTED_STATUS = "Focus is off"
SCRIPT_DASHBOARD_HOME_PAGE_ADDRESS = "1H1V"
SCRIPT_DASHBOARD_MANUAL_EVENT = "manual"
SCRIPT_EDITOR_DEFAULT_SOURCE = (
    "function on_event(event)\n"
    '  return { status = "Received " .. event.type }\n'
    "end"
)
SCRIPT_DASHBOARD_POLICY_DOCUMENT = {
    "id": "script-dashboard-policy",
    "title": "Script dashboard policy",
    "enabled": True,
    "capabilities": ["manualTrigger"],
    "actions": ["patchWidget"],
}
SCRIPT_DASHBOARD_POLICY_ID = SCRIPT_DASHBOARD_POLICY_DOCUMENT["id"]
SCRIPT_DASHBOARD_SCRIPT_DOCUMENT = {
    "id": "script-dashboard-fixture",
    "title": "Script dashboard fixture",
    "enabled": True,
    "apiVersion": 1,
    "source": (
        "function on_event(event)\n"
        f'  if event.type ~= "{SCRIPT_DASHBOARD_MANUAL_EVENT}" then return end\n'
        f'  local enabled = context.widgets["{SCRIPT_DASHBOARD_HOME_PAGE_ADDRESS}-focus"].checked\n'
        "  return {\n"
        '    status = "Focus is " .. (enabled and "on" or "off"),\n'
        "    actions = {\n"
        "      {\n"
        '        type = "patchWidget",\n'
        f'        widgetAddress = "{SCRIPT_DASHBOARD_HOME_PAGE_ADDRESS}-fixture-detail",\n'
        '        values = { state = { body = enabled and "Focus toggle is on" or "Focus toggle is off" } }\n'
        "      }\n"
        "    }\n"
        "  }\n"
        "end"
    ),
    "state": {},
}
SCRIPT_DASHBOARD_SCRIPT_ID = SCRIPT_DASHBOARD_SCRIPT_DOCUMENT["id"]
SCRIPT_DASHBOARD_SUBSCRIPTION_DOCUMENT = {
    "scriptId": SCRIPT_DASHBOARD_SCRIPT_ID,
    "policyId": SCRIPT_DASHBOARD_POLICY_ID,
    "enabled": True,
    "subscriptions": [
        {
            "event": SCRIPT_DASHBOARD_MANUAL_EVENT,
            "minimumIntervalMilliseconds": 0,
            "coalescingKey": "script-dashboard-manual",
        },
    ],
}
SCRIPT_DASHBOARD_STATUS_TEXT = f"Status: {SCRIPT_DASHBOARD_EXPECTED_STATUS}"
SCRIPT_DASHBOARD_WIDGET_DOCUMENT = {
    "id": SCRIPT_DASHBOARD_DEFAULT_WIDGET_ID,
    "type": "scriptDashboard",
    "title": "Scripts",
    "enabled": True,
    "cell": {"column": 0, "row": 2, "columnSpan": 4, "rowSpan": 4},
}


def script_dashboard_semantic_id(action: str) -> str:
    return (
        f"widget:{SCRIPT_DASHBOARD_DEFAULT_WIDGET_ID}:script:"
        f"{SCRIPT_DASHBOARD_SCRIPT_ID}:{action}"
    )


def default_script_dashboard_semantic_id(script_id: str, action: str) -> str:
    return (
        f"widget:{SCRIPT_DASHBOARD_DEFAULT_WIDGET_ID}:script:"
        f"{script_id}:{action}"
    )
