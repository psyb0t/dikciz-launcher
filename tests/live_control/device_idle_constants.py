"""Device-idle fixtures for the isolated Android emulator."""

AUTOMATION_DEVICE_IDLE_CAPABILITY = "deviceIdleState"
AUTOMATION_DEVICE_IDLE_COALESCING_KEY = "device-idle"
AUTOMATION_DEVICE_IDLE_EVENT = "deviceIdleMode"
AUTOMATION_DEVICE_IDLE_INITIAL_STATE = {"deviceIdle": "waiting"}
AUTOMATION_DEVICE_IDLE_MCP_CONFIG_GET_REQUEST_ID = 67
AUTOMATION_DEVICE_IDLE_SOURCE = (
    'function on_event(event)\n'
    f'  if event.type ~= "{AUTOMATION_DEVICE_IDLE_EVENT}" then return end\n'
    '  return { type = "patchState", values = { deviceIdle = event.payload.idle } }\n'
    'end'
)
AUTOMATION_DEVICE_IDLE_STATE_KEY = "deviceIdle"
EMULATOR_DEVICE_IDLE_MODE_ACTIVE = "active"
EMULATOR_DEVICE_IDLE_MODE_IDLE = "idle"
EMULATOR_DEVICE_IDLE_MODES = frozenset(
    (
        EMULATOR_DEVICE_IDLE_MODE_ACTIVE,
        EMULATOR_DEVICE_IDLE_MODE_IDLE,
    ),
)
