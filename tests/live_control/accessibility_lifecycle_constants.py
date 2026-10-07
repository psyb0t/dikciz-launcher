"""Accessibility-service lifecycle automation fixtures."""

ACCESSIBILITY_LIFECYCLE_ACTIONS = ["patchState"]
ACCESSIBILITY_LIFECYCLE_CAPABILITIES = ["accessibility"]
ACCESSIBILITY_LIFECYCLE_CONNECTED = "connected"
ACCESSIBILITY_LIFECYCLE_COALESCING_KEY = "accessibility-service-state"
ACCESSIBILITY_LIFECYCLE_DISCONNECTED = "disconnected"
ACCESSIBILITY_LIFECYCLE_DOCUMENT_KEYS = {
    "coalescingKey",
    "payload",
    "source",
    "timestampMilliseconds",
    "type",
}
ACCESSIBILITY_LIFECYCLE_ENABLED_KEY = "enabled"
ACCESSIBILITY_LIFECYCLE_EVENT = "accessibilityServiceState"
ACCESSIBILITY_LIFECYCLE_EVENT_SOURCE = "accessibility_service"
ACCESSIBILITY_LIFECYCLE_LUA_SOURCE = (
    'function on_event(event)\n'
    '  if event.type ~= "accessibilityServiceState" then\n'
    '    return { status = "ignored" }\n'
    '  end\n'
    '  return {\n'
    '    type = "patchState",\n'
    '    values = { accessibilityServiceState = event.payload.state }\n'
    '  }\n'
    'end'
)
ACCESSIBILITY_LIFECYCLE_MCP_CONFIG_REQUEST_ID = 4302
ACCESSIBILITY_LIFECYCLE_MCP_STATUS_REQUEST_ID = 4301
ACCESSIBILITY_LIFECYCLE_PAYLOAD_STATE_KEY = "state"
ACCESSIBILITY_LIFECYCLE_SCRIPT_STATE_KEY = "accessibilityServiceState"
ACCESSIBILITY_LIFECYCLE_SUBSCRIPTIONS = [
    {
        "event": ACCESSIBILITY_LIFECYCLE_EVENT,
        "minimumIntervalMilliseconds": 0,
        "coalescingKey": ACCESSIBILITY_LIFECYCLE_COALESCING_KEY,
    },
]
ACCESSIBILITY_LIFECYCLE_WEBSOCKET_EVENT = "automation_event"
