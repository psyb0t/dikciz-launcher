"""Constants for system-event automation on the isolated Android emulator."""

AUTOMATION_SYSTEM_CAPABILITIES = ["time", "userPresence", "connectivity"]
AUTOMATION_SYSTEM_SOURCE = (
    'function on_event(event)\n'
    '  if event.type == "time" then\n'
    '    return { type = "patchState", values = { timeAction = event.payload.action } }\n'
    '  end\n'
    '  if event.type == "userPresent" then\n'
    '    return { type = "patchState", values = { userPresent = true } }\n'
    '  end\n'
    '  if event.type == "connectivity" then\n'
    '    return { type = "patchState", values = {\n'
    '      connectivity = event.payload.state,\n'
    '      connectivityHasWifi = event.payload.transports[1] == "wifi",\n'
    '      connectivityMetered = event.payload.metered,\n'
    '      connectivityValidated = event.payload.validated\n'
    '    } }\n'
    '  end\n'
    'end'
)
AUTOMATION_SYSTEM_INITIAL_STATE = {
    "connectivity": "waiting",
    "connectivityHasWifi": "waiting",
    "connectivityMetered": "waiting",
    "connectivityValidated": "waiting",
    "timeAction": "waiting",
    "userPresent": False,
}
AUTOMATION_SYSTEM_WIFI_CONNECTED = "connected"
AUTOMATION_SYSTEM_WIFI_DISCONNECTED = "disconnected"
AUTOMATION_SYSTEM_CONNECTIVITY_COALESCING_KEY = "automation-system-connectivity"
AUTOMATION_SYSTEM_CONNECTIVITY_CONNECTED_STATE = "connected"
AUTOMATION_SYSTEM_CONNECTIVITY_DISCONNECTED_STATE = "disconnected"
AUTOMATION_SYSTEM_CONNECTIVITY_HAS_WIFI_STATE_KEY = "connectivityHasWifi"
AUTOMATION_SYSTEM_CONNECTIVITY_METERED_STATE_KEY = "connectivityMetered"
AUTOMATION_SYSTEM_CONNECTIVITY_STATE_KEY = "connectivity"
AUTOMATION_SYSTEM_CONNECTIVITY_VALIDATED_STATE_KEY = "connectivityValidated"
AUTOMATION_SYSTEM_CONNECTIVITY_WIFI_VALIDATED = False
AUTOMATION_SYSTEM_MCP_CONFIG_GET_REQUEST_ID = 480
AUTOMATION_SYSTEM_TIME_ACTION = "android.intent.action.TIME_TICK"
AUTOMATION_SYSTEM_TIME_COALESCING_KEY = "automation-system-time"
AUTOMATION_SYSTEM_TIME_STATE_KEY = "timeAction"
AUTOMATION_SYSTEM_USER_PRESENT_COALESCING_KEY = "automation-system-user-present"
AUTOMATION_SYSTEM_USER_PRESENT_STATE_KEY = "userPresent"
AUTOMATION_SYSTEM_TIME_TICK_TIMEOUT_SECONDS = 70
AUTOMATION_SYSTEM_UNLOCK_COMMAND = "wm dismiss-keyguard"
