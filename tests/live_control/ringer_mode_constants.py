"""Ringer-mode fixtures for the isolated Android emulator."""

AUTOMATION_RINGER_MODE_CAPABILITY = "ringerModeState"
AUTOMATION_RINGER_MODE_COALESCING_KEY = "ringer-mode"
AUTOMATION_RINGER_MODE_EVENT = "ringerMode"
AUTOMATION_RINGER_MODE_INITIAL_STATE = {"ringerMode": "waiting"}
AUTOMATION_RINGER_MODE_MCP_CONFIG_GET_REQUEST_ID = 63
AUTOMATION_RINGER_MODE_SOURCE = (
    'function on_event(event)\n'
    f'  if event.type ~= "{AUTOMATION_RINGER_MODE_EVENT}" then return end\n'
    '  return { type = "patchState", values = { ringerMode = event.payload.mode } }\n'
    'end'
)
AUTOMATION_RINGER_MODE_STATE_KEY = "ringerMode"
EMULATOR_RINGER_MODE_NORMAL = "normal"
EMULATOR_RINGER_MODE_SILENT = "silent"
EMULATOR_RINGER_MODE_VIBRATE = "vibrate"
EMULATOR_RINGER_MODES = frozenset(
    (
        EMULATOR_RINGER_MODE_NORMAL,
        EMULATOR_RINGER_MODE_SILENT,
        EMULATOR_RINGER_MODE_VIBRATE,
    ),
)
