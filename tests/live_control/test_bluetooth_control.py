"""Real emulator Bluetooth discovery and strict transaction boundaries."""

from . import *
from .test_native_data import native_call, PROTOCOLS, grant_permissions
from .test_capability_expansion import wait_native_control


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_bluetooth_control_discovery_and_validation(websocket_control, protocol):
    grant_permissions(("android.permission.BLUETOOTH_CONNECT", "android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH_ADVERTISE"))
    restore_system_home()
    wait_native_control(protocol)
    inventory = native_call(protocol, "bluetoothDevices")
    if not inventory["available"]:
        native_call(protocol, "bluetoothScan", {"durationMilliseconds": 100}, error="native_unavailable")
        return
    originally_enabled = inventory["enabled"]
    try:
        if not originally_enabled: emulator_bluetooth_set("on")
        for mode in ("ble", "classic"):
            result = native_call(protocol, "bluetoothScan", {"mode": mode, "durationMilliseconds": 100, "maximumDevices": 2})
            assert result["mode"] == mode and result["count"] == len(result["items"]) <= 2
            assert isinstance(result["limitReached"], bool)
        for command,fields in (
            ("bluetoothScan", {"mode": "arbitrary"}),
            ("bluetoothScan", {"durationMilliseconds": 0}),
            ("bluetoothScan", {"maximumDevices": 201}),
            ("bluetoothPair", {"address": "not-a-mac"}),
            ("bluetoothExchange", {"address": "00:11:22:33:44:55", "serviceUuid": "not-a-uuid"}),
            ("bluetoothExchange", {"address": "00:11:22:33:44:55", "serviceUuid": "00001101-0000-1000-8000-00805f9b34fb", "dataBase64": "%%%"}),
        ):
            native_call(protocol, command, fields, error="validation_failed")
    finally:
        if not originally_enabled: emulator_bluetooth_set("off")


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_bluetooth_control_gatt_and_socket_deadlines(websocket_control, protocol):
    grant_permissions(("android.permission.BLUETOOTH_CONNECT", "android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH_ADVERTISE"))
    restore_system_home()
    wait_native_control(protocol)
    inventory = native_call(protocol, "bluetoothDevices")
    if not inventory["available"]:
        native_call(protocol, "bluetoothGatt", {"action":"discover","address":"00:11:22:33:44:55"}, error="native_unavailable")
        return
    originally_enabled=inventory["enabled"]
    service="00001101-0000-1000-8000-00805f9b34fb"
    base={"action":"discover","address":"00:11:22:33:44:55","timeoutMilliseconds":1}
    try:
        if not originally_enabled: emulator_bluetooth_set("on")
        for _ in range(2):
            native_call(protocol,"bluetoothGatt",base,error={"bluetooth_timeout","bluetooth_gatt_status_133","bluetooth_gatt_status_257","bluetooth_disconnected","bluetooth_gatt_unavailable"})
            native_call(protocol,"bluetoothExchange",{"address":base["address"],"serviceUuid":service,"timeoutMilliseconds":1,"readBytes":1},error={"bluetooth_timeout","bluetooth_socket_failed"})
        for fields in (
            {"action":"unknown"},
            {"serviceUuid":"not-a-uuid"},
            {"mtu":22},
            {"characteristicUuid":service},
            {"action":"read","serviceUuid":service},
            {"action":"write","serviceUuid":service,"characteristicUuid":service},
            {"action":"observe","serviceUuid":service,"characteristicUuid":service,"dataBase64":"AA=="},
            {"action":"readDescriptor","serviceUuid":service,"characteristicUuid":service},
        ):
            native_call(protocol,"bluetoothGatt",{**base,**fields},error="validation_failed")
    finally:
        if not originally_enabled: emulator_bluetooth_set("off")
