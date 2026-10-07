"""Night-mode fixtures for the isolated Android emulator."""

AUTOMATION_NIGHT_MODE_CAPABILITY = "nightModeState"
AUTOMATION_NIGHT_MODE_COALESCING_KEY = "night-mode"
AUTOMATION_NIGHT_MODE_EVENT = "nightMode"
AUTOMATION_NIGHT_MODE_INITIAL_STATE = {"nightMode": "waiting"}
AUTOMATION_NIGHT_MODE_MCP_CONFIG_GET_REQUEST_ID = 68
AUTOMATION_NIGHT_MODE_SOURCE = (
    'function on_event(event)\n'
    f'  if event.type ~= "{AUTOMATION_NIGHT_MODE_EVENT}" then return end\n'
    '  return { type = "patchState", values = { nightMode = event.payload.night } }\n'
    'end'
)
AUTOMATION_NIGHT_MODE_STATE_KEY = "nightMode"
EMULATOR_NIGHT_MODE_AUTO = "auto"
EMULATOR_NIGHT_MODE_NO = "no"
EMULATOR_NIGHT_MODE_YES = "yes"
EMULATOR_NIGHT_MODES = frozenset(
    {
        EMULATOR_NIGHT_MODE_AUTO,
        EMULATOR_NIGHT_MODE_NO,
        EMULATOR_NIGHT_MODE_YES,
    },
)
