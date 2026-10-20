"""Permission setup must never request default SMS app ownership."""

import uuid

from . import *
from .test_remote_bearer_auth import connected_mcp, mcp_tool
from .test_device_files import http_request


SMS_ROLE_ID = "smsRole"
UNAVAILABLE = "unavailable"
ON_DEMAND = "onDemand"
FULL_ACCESS = "settings:automation:full-access"
NEXT_ACCESS = "settings:device-access:next"
CLOSE_ACCESS = "settings:device-access:close"
OPEN_SETUP = "openAutomationSetup"
ROLE_COMMAND = "cmd role get-role-holders --user 0 android.app.role.SMS"
PROTOCOLS = ("websocket", "mcp", "http")


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_sms_role_setup_absent_from_status(websocket_control, protocol):
    if protocol == "websocket":
        status = AUTOMATION.request(websocket_control, AUTOMATION.TYPE_AUTOMATION_STATUS)
    elif protocol == "mcp":
        with connected_mcp() as session:
            status = mcp_tool(*session, 3, "dikciz_automation_status")
    else:
        code, _, response = http_request({"requestId": str(uuid.uuid4()), "type": "automationStatus"})
        assert code == 200, response
        status = response["result"]
    access = status["deviceAccess"]
    entries = {entry["id"]: entry for entry in access["requirements"]}
    assert SMS_ROLE_ID not in entries
    AUTOMATION.verify_device_access(access)
    assert all(entry["kind"] != "role" for entry in entries.values())
    assert entries["sms"]["kind"] == "runtimePermission"
    actionable = [entry for entry in entries.values() if entry["state"] not in (UNAVAILABLE, ON_DEMAND)]
    assert access["requirementCount"] == len(actionable)
    assert access["grantedCount"] == sum(entry["state"] == "granted" for entry in actionable)


def test_sms_role_setup_wizard_keeps_messaging_app_and_skips_role(websocket_control):
    role_before = device_command(ROLE_COMMAND).strip()
    configuration = AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET)
    try:
        for _ in range(2):
            AUTOMATION.request(websocket_control, OPEN_SETUP)
            wait_for_snapshot_node(websocket_control, FULL_ACCESS)
            AUTOMATION.request(websocket_control, AUTOMATION.TYPE_TAP, semanticId=FULL_ACCESS)
            snapshot = wait_for_snapshot_node(websocket_control, NEXT_ACCESS)
            next_button = AUTOMATION.require_node(snapshot, NEXT_ACCESS)
            assert "Default SMS role" not in next_button["text"]
            wait_for_physical_text_bounds("Full device access")
            assert all("Default SMS role" not in node.get("text", "") for node in physical_nodes())
            status = AUTOMATION.request(websocket_control, AUTOMATION.TYPE_AUTOMATION_STATUS)
            entries = status["deviceAccess"]["requirements"]
            assert all(entry["id"] != SMS_ROLE_ID for entry in entries)
            run_device_operation("screenshot")
            AUTOMATION.request(websocket_control, AUTOMATION.TYPE_TAP, semanticId=CLOSE_ACCESS)
        assert device_command(ROLE_COMMAND).strip() == role_before
        assert AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET) == configuration
    finally:
        snapshot = AUTOMATION.request(websocket_control, AUTOMATION.TYPE_SNAPSHOT)
        if any(node.get("semanticId") == CLOSE_ACCESS for node in snapshot["nodes"]):
            AUTOMATION.request(websocket_control, AUTOMATION.TYPE_TAP, semanticId=CLOSE_ACCESS)
