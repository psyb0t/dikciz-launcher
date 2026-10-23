"""USSD validation, current Android access and explicit lab radio failures."""

from . import *
from .test_native_data import PROTOCOLS, native_call, grant_permissions
from .test_capability_expansion import wait_native_control


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_ussd_control_validation_and_platform_status(websocket_control, protocol):
    for fields in (
        {}, {"code": ""}, {"code": "123"}, {"code": "tel:*123#"}, {"code": "*123#;id"},
        {"code": "*123#\n"}, {"code": "*123"}, {"code": "*#"}, {"code": "##"},
        {"code": "*" + "1" * 181 + "#"}, {"code": None}, {"code": True},
        {"code": "*123#", "subscriptionId": -1}, {"code": "*123#", "subscriptionId": True},
        {"code": "*123#", "timeoutMilliseconds": 0}, {"code": "*123#", "timeoutMilliseconds": 120001},
        {"code": "*123#", "maximumResponseCharacters": 0},
        {"code": "*123#", "maximumResponseCharacters": 65537},
        {"code": "*123#", "maximumResponseCharacters": "4096"},
    ):
        native_call(protocol, "ussdRequest", fields, error="validation_failed")
    device_command("pm revoke " + DIKCIZ_DEBUG_PACKAGE + " android.permission.CALL_PHONE")
    restore_system_home()
    wait_native_control(protocol)
    denied = native_call(protocol, "ussdRequest", {"code": "*123#"}, error="native_permission_telephony")
    assert denied["permissionRequest"]["requirementId"] == "telephony"
    grant_permissions(("android.permission.CALL_PHONE",))
    restore_system_home()
    wait_native_control(protocol)
    error = native_call(protocol, "ussdRequest", {"code": "*123#", "timeoutMilliseconds": 100},
        error=("no_default_voice_subscription", "ussd_service_unavailable", "ussd_network_rejected", "ussd_timeout", "ussd_unavailable"))
    assert "response" not in error
