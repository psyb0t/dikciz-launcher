"""Device control and private VNC helpers."""

from .constants import *
from .system_source_constants import *
from .thermal_constants import EMULATOR_THERMAL_STATES
from .power_saver_constants import EMULATOR_POWER_SAVE_STATES
from .device_idle_constants import EMULATOR_DEVICE_IDLE_MODES
from .night_mode_constants import EMULATOR_NIGHT_MODES
from .device_configuration_constants import (
    EMULATOR_DEVICE_FONT_SCALE_PERCENTAGES,
    EMULATOR_DEVICE_ORIENTATIONS,
    EMULATOR_DEVICE_ORIENTATION_LANDSCAPE,
)
from .ringer_mode_constants import EMULATOR_RINGER_MODES
from .interruption_filter_constants import (
    EMULATOR_INTERRUPTION_FILTERS,
    EMULATOR_NOTIFICATION_POLICY_ACCESS_STATES,
)

def load_script(module_name: str, relative_path: str) -> ModuleType:
    source_path = LAB_ROOT / relative_path
    specification = importlib.util.spec_from_file_location(module_name, source_path)
    assert specification is not None and specification.loader is not None
    module = importlib.util.module_from_spec(specification)
    specification.loader.exec_module(module)
    return module


AUTOMATION = load_script("dikciz_automation_smoke", "scripts/test-dikciz-automation.py")
MCP = load_script("dikciz_mcp_smoke", "scripts/test-dikciz-mcp.py")


def bounds_for(snapshot: dict[str, Any], semantic_id: str) -> dict[str, int]:
    node = AUTOMATION.require_node(snapshot, semantic_id)
    bounds = node.get("bounds")
    assert isinstance(bounds, dict)
    values = {name: bounds.get(name) for name in ("left", "top", "right", "bottom")}
    assert all(isinstance(value, int) for value in values.values())
    return {name: int(value) for name, value in values.items()}


def center(bounds: dict[str, int]) -> tuple[int, int]:
    return ((bounds["left"] + bounds["right"]) // 2, (bounds["top"] + bounds["bottom"]) // 2)


def device_command(command: str) -> str:
    return run_device_operation("device-shell", DEVICE_COMMAND=command)


def emulator_dikciz_accessibility_set(enabled: bool) -> str:
    operation = "dikciz-accessibility-on" if enabled else "dikciz-accessibility-off"
    return run_device_operation(operation)


def device_density_scale() -> float:
    output = device_command("wm density")
    matches = list(DEVICE_DENSITY_PATTERN.finditer(output))
    assert matches, f"could not determine device density from: {output!r}"
    return int(matches[-1].group("density")) / 160


def emulator_geo_fix(longitude: float, latitude: float) -> str:
    return run_device_operation(
        "emulator-geo-fix",
        EMULATOR_GEO_LATITUDE=str(latitude),
        EMULATOR_GEO_LONGITUDE=str(longitude),
    )


def emulator_sensor_set(sensor_name: str, values: str) -> str:
    return run_device_operation(
        "emulator-sensor-set",
        EMULATOR_SENSOR_NAME=sensor_name,
        EMULATOR_SENSOR_VALUES=values,
    )


def emulator_sms_send(sender: str, body: str) -> str:
    assert sender.isdecimal()
    assert 1 <= len(sender) <= 32
    assert body
    assert len(body) <= 512
    assert "\n" not in body
    assert "\r" not in body
    return run_device_operation(
        "emulator-sms-send",
        EMULATOR_SMS_SENDER=sender,
        EMULATOR_SMS_BODY=body,
    )


def emulator_power_set(ac: str) -> str:
    assert ac in {"on", "off"}
    return run_device_operation("emulator-power-set", EMULATOR_POWER_AC=ac)


def emulator_thermal_set(state: str) -> str:
    assert state in EMULATOR_THERMAL_STATES
    return run_device_operation("emulator-thermal-set", EMULATOR_THERMAL_STATE=state)


def emulator_power_save_set(state: str) -> str:
    assert state in EMULATOR_POWER_SAVE_STATES
    return run_device_operation("emulator-power-save-set", EMULATOR_POWER_SAVE_MODE=state)


def emulator_device_idle_set(mode: str) -> str:
    assert mode in EMULATOR_DEVICE_IDLE_MODES
    return run_device_operation("emulator-device-idle-set", EMULATOR_DEVICE_IDLE_MODE=mode)


def emulator_night_mode_set(mode: str) -> str:
    assert mode in EMULATOR_NIGHT_MODES
    return run_device_operation("emulator-night-mode-set", EMULATOR_NIGHT_MODE=mode)


def emulator_device_orientation_set(orientation: str) -> str:
    assert orientation in EMULATOR_DEVICE_ORIENTATIONS
    return run_device_operation(
        "emulator-device-orientation-set",
        EMULATOR_DEVICE_ORIENTATION=orientation,
    )


def emulator_font_scale_set(percent: int) -> str:
    assert percent in EMULATOR_DEVICE_FONT_SCALE_PERCENTAGES
    return run_device_operation(
        "emulator-font-scale-set",
        EMULATOR_FONT_SCALE_PERCENT=str(percent),
    )


def emulator_ringer_mode_set(mode: str) -> str:
    assert mode in EMULATOR_RINGER_MODES
    return run_device_operation("emulator-ringer-mode-set", EMULATOR_RINGER_MODE=mode)


def emulator_notification_policy_access_set(state: str) -> str:
    assert state in EMULATOR_NOTIFICATION_POLICY_ACCESS_STATES
    return run_device_operation(
        "emulator-notification-policy-access-set",
        EMULATOR_NOTIFICATION_POLICY_ACCESS=state,
    )


def emulator_interruption_filter_set(mode: str) -> str:
    assert mode in EMULATOR_INTERRUPTION_FILTERS
    return run_device_operation("emulator-interruption-filter-set", EMULATOR_INTERRUPTION_FILTER=mode)


def emulator_wifi_set(state: str) -> str:
    assert state in {
        AUTOMATION_SYSTEM_WIFI_CONNECTED,
        AUTOMATION_SYSTEM_WIFI_DISCONNECTED,
    }
    return run_device_operation(
        "emulator-wifi-set",
        EMULATOR_WIFI_STATE=state,
    )


def emulator_bluetooth_set(state: str) -> str:
    allowed_states = {"on", "off"}
    assert state in allowed_states
    return run_device_operation(
        "emulator-bluetooth-set",
        EMULATOR_BLUETOOTH_STATE=state,
    )


def emulator_calendar_fixture_create() -> str:
    return run_device_operation("calendar-fixture-create")


def emulator_calendar_fixture_update() -> str:
    return run_device_operation("calendar-fixture-update")


def emulator_calendar_fixture_delete() -> str:
    return run_device_operation("calendar-fixture-delete")


def emulator_contacts_fixture_create() -> str:
    return run_device_operation("contacts-fixture-create")


def emulator_contacts_fixture_delete() -> str:
    return run_device_operation("contacts-fixture-delete")


def automation_configuration(
    original: dict[str, Any],
    source: str,
    state: dict[str, Any],
    capabilities: list[str],
    actions: list[str],
    subscriptions: list[dict[str, Any]],
) -> dict[str, Any]:
    configured = copy.deepcopy(original)
    configured["scripts"].append(
        {
            "id": AUTOMATION_LIVE_SCRIPT_ID,
            "title": "Automation live fixture",
            "enabled": True,
            "apiVersion": LUA_SCRIPT_API_VERSION,
            "source": source,
            "state": state,
        },
    )
    configured["automation"] = {
        "apiVersion": 1,
        "policies": [
            {
                "id": AUTOMATION_LIVE_POLICY_ID,
                "title": "Automation live policy",
                "enabled": True,
                "capabilities": capabilities,
                "actions": actions,
            },
        ],
        "scripts": [
            {
                "scriptId": AUTOMATION_LIVE_SCRIPT_ID,
                "policyId": AUTOMATION_LIVE_POLICY_ID,
                "enabled": True,
                "subscriptions": subscriptions,
            },
        ],
    }
    return configured


def write_lua_script_archive(target: Path, source: str) -> None:
    manifest = {
        "archiveVersion": 1,
        "kind": LUA_ARCHIVE_KIND,
        "scriptId": LUA_ARCHIVE_ID,
    }
    metadata = {
        "id": LUA_ARCHIVE_ID,
        "title": LUA_ARCHIVE_TITLE,
        "enabled": True,
        "apiVersion": LUA_SCRIPT_API_VERSION,
    }
    with zipfile.ZipFile(target, mode="w", compression=zipfile.ZIP_DEFLATED) as archive:
        archive.writestr(LUA_ARCHIVE_MANIFEST_FILE_NAME, json.dumps(manifest, separators=(",", ":")))
        archive.writestr(LUA_ARCHIVE_METADATA_FILE_NAME, json.dumps(metadata, separators=(",", ":")))
        archive.writestr(LUA_ARCHIVE_SOURCE_FILE_NAME, source)
        archive.writestr(LUA_ARCHIVE_STATE_FILE_NAME, json.dumps(LUA_ARCHIVE_STATE, separators=(",", ":")))


def device_system_setting(setting: str) -> str:
    value = device_command(
        f"settings get {DEVICE_SETTINGS_SYSTEM_NAMESPACE} {setting}",
    ).strip()
    assert DEVICE_SETTING_VALUE_PATTERN.fullmatch(value)
    return value


def set_device_system_setting(setting: str, value: str) -> None:
    assert DEVICE_SETTING_VALUE_PATTERN.fullmatch(value)
    assert value != DEVICE_SETTING_ABSENT_VALUE
    device_command(f"settings put {DEVICE_SETTINGS_SYSTEM_NAMESPACE} {setting} {value}")


def restore_device_system_setting(setting: str, value: str) -> None:
    if value == DEVICE_SETTING_ABSENT_VALUE:
        device_command(f"settings delete {DEVICE_SETTINGS_SYSTEM_NAMESPACE} {setting}")
        return
    set_device_system_setting(setting, value)


def device_night_mode() -> str:
    result = DEVICE_NIGHT_MODE_PATTERN.search(device_command(DEVICE_UI_MODE_NIGHT_COMMAND))
    assert result is not None
    return result.group("mode")


def set_device_night_mode(mode: str) -> None:
    assert mode in DEVICE_UI_MODE_NIGHT_VALUES
    device_command(DEVICE_UI_MODE_NIGHT_SET_COMMAND.format(mode))
    assert device_night_mode() == mode


def assert_system_configuration_keeps_saved_home(expected: dict[str, Any]) -> None:
    restarted_control = wait_for_restarted_websocket_control()
    try:
        actual = AUTOMATION.require_configuration(
            AUTOMATION.request(restarted_control, AUTOMATION.TYPE_CONFIG_GET),
        )
        assert actual == expected
    finally:
        restarted_control.close()


def receive_exactly(connection: socket.socket, size: int) -> bytes:
    received = bytearray()
    while len(received) < size:
        chunk = connection.recv(size - len(received))
        if not chunk:
            raise AssertionError("private VNC connection closed before its expected RFB packet")
        received.extend(chunk)
    return bytes(received)


@dataclass(frozen=True)
class PrivateVncClient:
    connection: socket.socket
    width: int
    height: int

    def key_press(self, key_symbol: int) -> None:
        self.connection.sendall(
            struct.pack("!BBHI", RFB_KEY_EVENT_TYPE, RFB_KEY_DOWN, 0, key_symbol),
        )
        self.connection.sendall(
            struct.pack("!BBHI", RFB_KEY_EVENT_TYPE, 0, 0, key_symbol),
        )

    def left_click(self, x: int, y: int) -> None:
        self.pointer_down(x, y)
        time.sleep(RFB_POINTER_CLICK_HOLD_SECONDS)
        self.pointer_up(x, y)

    def pointer_down(self, x: int, y: int) -> None:
        self.pointer_event(RFB_POINTER_BUTTON_LEFT, x, y)

    def pointer_move(self, x: int, y: int) -> None:
        self.pointer_event(RFB_POINTER_BUTTON_LEFT, x, y)

    def pointer_up(self, x: int, y: int) -> None:
        self.pointer_event(RFB_POINTER_NO_BUTTON, x, y)

    def pointer_event(self, button_mask: int, x: int, y: int) -> None:
        assert 0 <= x < self.width
        assert 0 <= y < self.height
        self.connection.sendall(struct.pack("!BBHH", RFB_POINTER_EVENT_TYPE, button_mask, x, y))

    def click_home(self) -> None:
        self.left_click(
            self.width * NAVIGATION_HOME_X_NUMERATOR // NAVIGATION_HOME_X_DENOMINATOR,
            self.height - NAVIGATION_BAR_HEIGHT_PIXELS // 2,
        )

    def click_overview(self) -> None:
        self.left_click(
            self.width * NAVIGATION_OVERVIEW_X_NUMERATOR // NAVIGATION_OVERVIEW_X_DENOMINATOR,
            self.height - NAVIGATION_BAR_HEIGHT_PIXELS // 2,
        )


@contextmanager
def connected_private_vnc() -> Generator[PrivateVncClient, None, None]:
    with socket.create_connection(
        (PRIVATE_VNC_HOST, PRIVATE_VNC_PORT),
        timeout=RFB_TIMEOUT_SECONDS,
    ) as connection:
        connection.settimeout(RFB_TIMEOUT_SECONDS)
        assert receive_exactly(connection, len(RFB_PROTOCOL_VERSION)) == RFB_PROTOCOL_VERSION
        connection.sendall(RFB_PROTOCOL_VERSION)
        security_type_count = receive_exactly(connection, 1)[0]
        security_types = receive_exactly(connection, security_type_count)
        assert RFB_SECURITY_NONE in security_types
        connection.sendall(bytes((RFB_SECURITY_NONE,)))
        security_result = struct.unpack("!I", receive_exactly(connection, 4))[0]
        assert security_result == 0
        connection.sendall(RFB_CLIENT_SHARED)
        initialization = receive_exactly(connection, RFB_SERVER_INITIALIZATION_BYTES)
        width, height = struct.unpack("!HH", initialization[:4])
        name_length = struct.unpack("!I", initialization[-4:])[0]
        receive_exactly(connection, name_length)
        yield PrivateVncClient(connection=connection, width=width, height=height)


def current_device_log_path() -> str:
    date_lines = [
        line.strip()
        for line in device_command("date -u +%F").splitlines()
        if UTC_DATE_PATTERN.fullmatch(line.strip())
    ]
    assert len(date_lines) == 1
    date = date_lines[0]
    return f"{LOG_DIRECTORY}/{date}.log"


def device_log_records() -> list[dict[str, Any]]:
    run_device_operation(
        "file-pull",
        DEVICE_FILE=current_device_log_path(),
        ARTIFACT_FILE=EVENT_LOG_ARTIFACT_NAME,
    )
    log_content = (ARTIFACT_DIRECTORY / EVENT_LOG_ARTIFACT_NAME).read_text(encoding="utf-8")
    return [json.loads(line) for line in log_content.splitlines() if line]
