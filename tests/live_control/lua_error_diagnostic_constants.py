"""Constants for installed Lua source-location diagnostics."""

from .constants import AUTOMATION_LIVE_SCRIPT_ID


LUA_ERROR_DIAGNOSTIC_ACTIONS = ["patchState"]
LUA_ERROR_DIAGNOSTIC_CAPABILITIES = ["manualTrigger"]
LUA_ERROR_DIAGNOSTIC_EVENT = "automation_delivery_rejected"
LUA_ERROR_DIAGNOSTIC_LOCATION_PREFIX = f"scripts/{AUTOMATION_LIVE_SCRIPT_ID}/main.lua:"
LUA_ERROR_DIAGNOSTIC_REASON = "invalid_lua"
LUA_ERROR_DIAGNOSTIC_REDACTED_VALUE = "[REDACTED]"
LUA_ERROR_DIAGNOSTIC_RUNTIME_SOURCE = """function on_event(event)
  error(\"token=lua-error-diagnostic-value\")
end"""
LUA_ERROR_DIAGNOSTIC_RUNTIME_SECRET = "lua-error-diagnostic-value"
LUA_ERROR_DIAGNOSTIC_VIEWER_SCROLL_ATTEMPTS = 12
LUA_ERROR_DIAGNOSTIC_VIEWER_SCROLL_VIEW_CLASS_NAME = "android.widget.ScrollView"
LUA_ERROR_DIAGNOSTIC_SUBSCRIPTIONS = [
    {"event": "manual", "minimumIntervalMilliseconds": 0},
]
LUA_ERROR_DIAGNOSTIC_SYNTAX_SOURCE = """function on_event(event)
  local = invalid
end"""
LUA_ERROR_DIAGNOSTIC_VIEWER_ARTIFACT_NAME = "lua-error-diagnostics.png"
