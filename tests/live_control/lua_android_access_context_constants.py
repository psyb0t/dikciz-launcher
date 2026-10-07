"""Lua Android-access context fixtures for the isolated emulator."""

LUA_ANDROID_ACCESS_CAPABILITY = "manualTrigger"
LUA_ANDROID_ACCESS_TARGET_WIDGET_ADDRESS = "1H1V-focus"
LUA_ANDROID_ACCESS_TARGET_WIDGET_ID = "focus"
LUA_ANDROID_ACCESS_CONTEXT_SOURCE = (
    'function on_event(event)\n'
    '  if event.type ~= "manual" then return end\n'
    '  return {\n'
    '    actions = {\n'
    '      { type = "patchState", values = { accessibility = context.androidAccess.accessibility } },\n'
    f'      {{ type = "patchWidget", widgetAddress = "{LUA_ANDROID_ACCESS_TARGET_WIDGET_ADDRESS}", '
    'values = { state = { checked = true } } }\n'
    '    }\n'
    '  }\n'
    'end'
)
LUA_ANDROID_ACCESS_INITIAL_STATE = {"accessibility": "waiting"}
LUA_ANDROID_ACCESS_MUTATION_SOURCE = (
    'function on_event(event)\n'
    '  if event.type ~= "manual" then return end\n'
    '  context.androidAccess.accessibility = true\n'
    '  return { type = "patchState", values = { accessibility = true } }\n'
    'end'
)
LUA_ANDROID_ACCESS_RUN_STATUS_PATH = "/sdcard/Dikciz/scripts/automation-live/run-status.json"
LUA_ANDROID_ACCESS_STATE_KEY = "accessibility"
LUA_ANDROID_ACCESS_TIMEOUT_SECONDS = 10
LUA_ANDROID_ACCESS_UNCHANGED_STATE = "waiting"
