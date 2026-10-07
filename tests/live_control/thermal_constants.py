"""Thermal-status fixtures for the isolated Android emulator."""

AUTOMATION_THERMAL_CAPABILITY = "thermalState"
AUTOMATION_THERMAL_COALESCING_KEY = "thermal"
AUTOMATION_THERMAL_EVENT = "thermalStatus"
AUTOMATION_THERMAL_LEVEL_KEY = "thermalLevel"
AUTOMATION_THERMAL_NONE_LEVEL = 0
AUTOMATION_THERMAL_NONE_STATE = "none"
AUTOMATION_THERMAL_SEVERE_LEVEL = 3
AUTOMATION_THERMAL_SEVERE_STATE = "severe"
AUTOMATION_THERMAL_STATE_KEY = "thermalState"
AUTOMATION_THERMAL_INITIAL_STATE = {
    AUTOMATION_THERMAL_LEVEL_KEY: "waiting",
    AUTOMATION_THERMAL_STATE_KEY: "waiting",
}
AUTOMATION_THERMAL_MCP_CONFIG_GET_REQUEST_ID = 61
AUTOMATION_THERMAL_SOURCE = (
    'function on_event(event)\n'
    '  if event.type ~= "thermalStatus" then return end\n'
    '  return { type = "patchState", values = {\n'
    '    thermalLevel = event.payload.level, thermalState = event.payload.status\n'
    '  } }\n'
    'end'
)
EMULATOR_THERMAL_RESET_STATE = "reset"
EMULATOR_THERMAL_SEVERE = "severe"
EMULATOR_THERMAL_STATES = frozenset((EMULATOR_THERMAL_RESET_STATE, EMULATOR_THERMAL_SEVERE))
