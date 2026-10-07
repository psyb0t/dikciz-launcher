"""Battery Saver fixtures for the isolated Android emulator."""

AUTOMATION_POWER_SAVE_CAPABILITY = "powerSaveState"
AUTOMATION_POWER_SAVE_COALESCING_KEY = "power-save"
AUTOMATION_POWER_SAVE_ENABLED_KEY = "powerSaveEnabled"
AUTOMATION_POWER_SAVE_EVENT = "powerSaveMode"
AUTOMATION_POWER_SAVE_INITIAL_STATE = {AUTOMATION_POWER_SAVE_ENABLED_KEY: "waiting"}
AUTOMATION_POWER_SAVE_MCP_CONFIG_GET_REQUEST_ID = 62
AUTOMATION_POWER_SAVE_SOURCE = (
    "function on_event(event)\n"
    f'  if event.type ~= "{AUTOMATION_POWER_SAVE_EVENT}" then return end\n'
    "  return { type = \"patchState\", values = {\n"
    f"    {AUTOMATION_POWER_SAVE_ENABLED_KEY} = event.payload.enabled\n"
    "  } }\n"
    "end"
)
EMULATOR_POWER_SAVE_OFF = "off"
EMULATOR_POWER_SAVE_ON = "on"
EMULATOR_POWER_SAVE_STATES = frozenset((EMULATOR_POWER_SAVE_OFF, EMULATOR_POWER_SAVE_ON))
EMULATOR_POWER_AC_OFF = "off"
EMULATOR_POWER_AC_ON = "on"
