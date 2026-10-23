"""NFC command validation and actual reader availability through every transport."""

from . import *
from .test_native_data import native_call, PROTOCOLS


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_nfc_control_validation_and_availability(websocket_control, protocol):
    restore_system_home()
    status = native_call(protocol, "nfcStatus")
    failure = "native_unavailable" if not status["available"] else "nfc_disabled" if not status["enabled"] else "nfc_timeout"
    native_call(protocol, "nfcRead", {"timeoutMilliseconds": 50}, error=failure)
    native_call(protocol, "nfcWrite", {"dataBase64": "0AAA", "timeoutMilliseconds": 50}, error=failure)
    native_call(protocol, "nfcTransceive", {"technology": "IsoDep", "dataBase64": "AA==", "timeoutMilliseconds": 50}, error=failure)
    for command,fields in (
        ("nfcRead", {"timeoutMilliseconds": 0}),
        ("nfcRead", {"expectedTagIdBase64": "%%%"}),
        ("nfcWrite", {"dataBase64": "%%%"}),
        ("nfcWrite", {"dataBase64": "AAAA"}),
        ("nfcWrite", {"dataBase64": "0AAA", "format": "yes"}),
        ("nfcTransceive", {"technology": "ArbitraryClass", "dataBase64": "AA=="}),
        ("nfcTransceive", {"technology": "IsoDep", "dataBase64": "AA==", "ioTimeoutMilliseconds": 60001}),
    ):
        native_call(protocol, command, fields, error="validation_failed")
