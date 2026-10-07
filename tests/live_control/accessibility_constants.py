"""Cross-app Android accessibility automation fixtures."""

ACCESSIBILITY_ACTION_CLICK = "click"
ACCESSIBILITY_ACTION_CAPABILITY = "accessibilityAction"
ACCESSIBILITY_GESTURE_ACTION_CAPABILITY = "accessibilityGesture"
ACCESSIBILITY_GLOBAL_ACTION_CAPABILITY = "accessibilityGlobalAction"
ACCESSIBILITY_ANDROID_PACKAGE = "com.android.settings"
ACCESSIBILITY_CAPABILITY = "accessibility"
ACCESSIBILITY_DEVICE_ACCESS_GRANTED_STATE = "granted"
ACCESSIBILITY_DEVICE_ACCESS_KIND = "systemSettings"
ACCESSIBILITY_DEVICE_ACCESS_NEEDS_SETUP_STATE = "needsSetup"
ACCESSIBILITY_ERROR_NOT_ENABLED = "accessibility_not_enabled"
ACCESSIBILITY_ERROR_NODE_UNAVAILABLE = "accessibility_node_unavailable"
ACCESSIBILITY_ERROR_SNAPSHOT_EXPIRED = "accessibility_snapshot_expired"
ACCESSIBILITY_ERROR_SNAPSHOT_STALE = "accessibility_snapshot_stale"
ACCESSIBILITY_ERROR_SNAPSHOT_LIMIT = "accessibility_snapshot_limit"
ACCESSIBILITY_EVENT = "accessibilityWindow"
ACCESSIBILITY_EVENT_COALESCING_KEY = "accessibility-settings-window"
ACCESSIBILITY_EVENT_STATE_KEY = "settingsWindowReceived"
ACCESSIBILITY_EVENT_SUBSCRIPTIONS = [
    {
        "event": ACCESSIBILITY_EVENT,
        "minimumIntervalMilliseconds": 0,
        "coalescingKey": ACCESSIBILITY_EVENT_COALESCING_KEY,
    },
]
ACCESSIBILITY_INITIAL_STATE = {ACCESSIBILITY_EVENT_STATE_KEY: False}
ACCESSIBILITY_HTML_SOURCE_WIDGET_ADDRESS = "1H1V-welcome"
ACCESSIBILITY_GLOBAL_ACTION_BACK = "back"
ACCESSIBILITY_GLOBAL_ACTION_HOME = "home"
ACCESSIBILITY_GESTURE_DURATION_MILLISECONDS = 100
ACCESSIBILITY_GESTURE_TAP = "tap"
ACCESSIBILITY_INVALID_NODE_ID = "node.999"
ACCESSIBILITY_MCP_ACTION_REQUEST_ID = 4102
ACCESSIBILITY_MCP_SNAPSHOT_REQUEST_ID = 4101
ACCESSIBILITY_MCP_STATUS_REQUEST_ID = 4104
ACCESSIBILITY_MCP_STATUS_VERIFICATION_REQUEST_ID = 4103
ACCESSIBILITY_MAXIMUM_SNAPSHOT_COUNT = 32
ACCESSIBILITY_NODE_BOUNDS_KEYS = {"bottom", "left", "right", "top"}
ACCESSIBILITY_NODE_ID_SEPARATOR = "."
ACCESSIBILITY_NODE_KEYS = {
    "actions",
    "bounds",
    "checkable",
    "checked",
    "className",
    "clickable",
    "contentDescription",
    "editable",
    "enabled",
    "focusable",
    "nodeId",
    "packageName",
    "scrollable",
    "text",
    "viewIdResourceName",
    "visibleToUser",
}
ACCESSIBILITY_OUTCOME_EXECUTED = "executed"
ACCESSIBILITY_PACKAGE_NAME_KEY = "packageName"
ACCESSIBILITY_SNAPSHOT_KEYS = {
    "nodeCount",
    "nodes",
    "packageName",
    "snapshotId",
    "truncated",
    "windowId",
}
ACCESSIBILITY_SOURCE = (
    'function on_event(event)\n'
    '  if event.type == "accessibilityWindow" and '\
    'event.payload.packageName == "com.android.settings" then\n'
    '    return { type = "patchState", values = { settingsWindowReceived = true } }\n'
    '  end\n'
    'end'
)
ACCESSIBILITY_LUA_ACTION_SOURCE = (
    'function on_event(event)\n'
    '  if event.type ~= "accessibilityWindow" then\n'
    '    return { status = "ignored" }\n'
    '  end\n'
    '  local accessibility = context.accessibility\n'
    '  if accessibility == nil then\n'
    '    return { status = "snapshot unavailable" }\n'
    '  end\n'
    '  for _, node in ipairs(accessibility.nodes) do\n'
    '    if node.text == "Network & internet" and node.visibleToUser then\n'
    '      local parent = string.match(node.nodeId, "^(.*)%.%d+$")\n'
    '      if parent ~= nil then\n'
    '        return {\n'
    '          status = "opening network",\n'
    '          actions = { {\n'
    '            type = "accessibilityAction",\n'
    '            snapshotId = accessibility.snapshotId,\n'
    '            nodeId = parent,\n'
    '            action = "click"\n'
    '          } }\n'
    '        }\n'
    '      end\n'
    '    end\n'
    '  end\n'
    '  return { status = "target unavailable" }\n'
    'end'
)
ACCESSIBILITY_LUA_GESTURE_SOURCE = (
    'function on_event(event)\n'
    '  if event.type ~= "accessibilityWindow" then\n'
    '    return { status = "ignored" }\n'
    '  end\n'
    '  local accessibility = context.accessibility\n'
    '  if accessibility == nil then\n'
    '    return { status = "snapshot unavailable" }\n'
    '  end\n'
    '  for _, node in ipairs(accessibility.nodes) do\n'
    '    if node.text == "Network & internet" and node.visibleToUser then\n'
    '      local bounds = node.bounds\n'
    '      return {\n'
    '        status = "opening network by tap",\n'
    '        actions = { {\n'
    '          type = "accessibilityGesture",\n'
    '          snapshotId = accessibility.snapshotId,\n'
    '          gesture = "tap",\n'
    '          x = math.floor((bounds.left + bounds.right) / 2),\n'
    '          y = math.floor((bounds.top + bounds.bottom) / 2),\n'
    '          durationMilliseconds = 100\n'
    '        } }\n'
    '      }\n'
    '    end\n'
    '  end\n'
    '  return { status = "target unavailable" }\n'
    'end'
)
ACCESSIBILITY_SETTINGS_COMPONENT = "com.android.settings/.Settings"
ACCESSIBILITY_SETTINGS_ENTRY_TEXT = "Network & internet"
ACCESSIBILITY_SERVICE_READY_TIMEOUT_SECONDS = 10
# Dikciz retains at most 32 live snapshot descriptors and each lasts five
# seconds, so a poll faster than one every 0.16s fills the store and the
# next request is refused with accessibility_snapshot_limit. This stays
# well inside that budget rather than borrowing the generic retry interval.
ACCESSIBILITY_SNAPSHOT_POLL_SECONDS = 0.5
ACCESSIBILITY_SNAPSHOT_EXPIRY_WAIT_SECONDS = 6
ACCESSIBILITY_THIRD_PARTY_ACTIVITY_CLASS = ".FixtureAccessibilityActivity"
ACCESSIBILITY_THIRD_PARTY_ACTION_COUNT_FORMAT = "External action count: {}"
ACCESSIBILITY_THIRD_PARTY_ACTION_COUNT_INITIAL = 0
ACCESSIBILITY_THIRD_PARTY_ACTION_COUNT_MCP = 2
ACCESSIBILITY_THIRD_PARTY_ACTION_COUNT_WEBSOCKET = 1
ACCESSIBILITY_THIRD_PARTY_ACTION_SCREENSHOT = "accessibility-third-party-action.png"
ACCESSIBILITY_THIRD_PARTY_ACTION_TEXT = "Complete external action"
