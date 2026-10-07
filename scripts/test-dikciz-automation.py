#!/usr/bin/env python3

"""Prove the Dikciz release automation socket through the isolated ADB forward."""

from __future__ import annotations

import argparse
import base64
import copy
import hashlib
import json
import secrets
import socket
import struct
import time
import uuid
from pathlib import Path
from typing import Any

AUTOMATION_PATH = "/v1/automation"
BUNDLED_CONFIGURATION_PATH = (
    Path(__file__).resolve().parent.parent
    / "dikciz-launcher"
    / "app"
    / "src"
    / "dikciz"
    / "assets"
    / "config.json"
)
DEFAULT_HOST = "127.0.0.1"
DEFAULT_PORT = 19_001
DEFAULT_APP_WIDGET_DISPLAY_STYLE = "iconLabel"
DEFAULT_HTML_WIDGET_CSS = ""
DEFAULT_HTML_WIDGET_HEIGHT_MODE = "fixed"
DEFAULT_HTML_WIDGET_JAVASCRIPT = ""
EMULATOR_ACCELEROMETER_TYPE = 1
EMULATOR_GYROSCOPE_TYPE = 4
FRAME_FIN_BIT = 0x80
FRAME_MASK_BIT = 0x80
FRAME_OPCODE_CLOSE = 0x8
FRAME_OPCODE_PING = 0x9
FRAME_OPCODE_PONG = 0xA
FRAME_OPCODE_TEXT = 0x1
FRAME_OPCODE_MASK = 0x0F
HEADER_AUTHORIZATION = "Authorization"
HANDSHAKE_MAX_BYTES = 8_192
HANDSHAKE_SUFFIX = b"\r\n\r\n"
MAXIMUM_CONFIGURATION_DOCUMENT_BYTES = 8 * 1024 * 1024
MAXIMUM_FRAME_BYTES = MAXIMUM_CONFIGURATION_DOCUMENT_BYTES
MAXIMUM_ACTIVE_MEDIA_SESSION_COUNT = 64
MAXIMUM_AVAILABLE_SENSOR_COUNT = 64
# A request is answered while the control plane keeps streaming events, so
# the wait is bounded by time rather than by how many unrelated events
# happen to arrive first. A client that leaves its connection idle between
# requests can queue any number of events behind the next answer, and a
# count budget turns that into a protocol error the server never made.
RESPONSE_TIMEOUT_SECONDS = 30
PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
PROTOCOL_VERSION = 1
SHELL_COMMAND_CURRENT_UID = "id -u"
KEY_ACTION = "action"
KEY_ACCESSIBILITY = "accessibility"
KEY_COMMANDS = "commands"
KEY_ACTIONS = "actions"
KEY_ACTIVE_MEDIA_SESSION_COUNT = "activeMediaSessionCount"
KEY_ACTIVE_MEDIA_SESSION_LIMIT = "activeMediaSessionLimit"
KEY_ACTIVE_MEDIA_SESSIONS_TRUNCATED = "activeMediaSessionsTruncated"
KEY_ACTIVITY_RECOGNITION = "activityRecognition"
KEY_ANDROID_ACCESS = "androidAccess"
KEY_API_VERSION = "apiVersion"
KEY_AUTOMATION_SERVICE_SYNCED = "automationServiceSynced"
KEY_AVAILABLE_SENSOR_COUNT = "availableSensorCount"
KEY_AVAILABLE_SENSOR_LIMIT = "availableSensorLimit"
KEY_AVAILABLE_SENSORS = "availableSensors"
KEY_AVAILABLE_SENSORS_TRUNCATED = "availableSensorsTruncated"
KEY_BODY_SENSORS = "bodySensors"
KEY_BODY_SENSORS_BACKGROUND = "bodySensorsBackground"
KEY_BLUETOOTH_CONNECT = "bluetoothConnect"
KEY_CALENDAR_READ = "calendarRead"
KEY_CONTACTS_READ = "contactsRead"
KEY_HEALTH_DATA_BACKGROUND = "healthDataBackground"
KEY_HEART_RATE_READ = "heartRateRead"
KEY_HOME_ROLE = "homeRole"
KEY_PHONE_STATE_READ = "phoneStateRead"
KEY_SMS_RECEIVE = "smsReceive"
KEY_SMS_SEND = "smsSend"
KEY_CAPABILITIES = "capabilities"
KEY_CELL = "cell"
KEY_CODE = "code"
KEY_COLUMNS = "columns"
KEY_CHECKED = "checked"
KEY_CLICKABLE = "clickable"
KEY_CONFIG = "config"
KEY_COMMAND = "command"
KEY_COMMAND_TYPE = "commandType"
KEY_COMPONENT = "component"
KEY_CSS = "css"
KEY_DELTA_Y = "deltaY"
KEY_DEVICE_ACCESS = "deviceAccess"
KEY_DISPATCHED = "dispatched"
KEY_DISPLAY_STYLE = "displayStyle"
KEY_ENABLED = "enabled"
KEY_GAP_DP = "gapDp"
KEY_EXECUTION_SCOPE = "executionScope"
KEY_EXIT_CODE = "exitCode"
KEY_EVENTS = "events"
KEY_EVENT = "event"
KEY_EVENT_SUBSCRIPTIONS = "eventSubscriptions"
KEY_FOUND = "found"
KEY_GRANTED_COUNT = "grantedCount"
KEY_HEIGHT_MODE = "heightMode"
KEY_HOME = "home"
KEY_ID = "id"
KEY_INTENT_TYPE = "intentType"
KEY_INTENT_TYPES = "intentTypes"
KEY_JAVASCRIPT = "javascript"
KEY_LIMITS = "limits"
KEY_KIND = "kind"
KEY_LAUNCHER = "launcher"
KEY_LOCKED = "locked"
KEY_OUTPUT = "output"
KEY_OPTIONAL_FIELDS = "optionalFields"
KEY_OUTCOME = "outcome"
KEY_OUTER_PADDING_DP = "outerPaddingDp"
KEY_MCP = "mcp"
KEY_MCP_TOOL = "mcpTool"
KEY_MEDIA_VOLUME_MUTABLE = "mediaVolumeMutable"
KEY_MINIMUM = "minimum"
KEY_MAXIMUM = "maximum"
KEY_MESSAGE = "message"
KEY_NODE_ID = "nodeId"
KEY_FIELDS = "fields"
KEY_PATH = "path"
KEY_PORT = "port"
KEY_PROTOCOL_VERSION = "protocolVersion"
KEY_PAGES = "pages"
KEY_PAGE_ID = "pageId"
KEY_PLACEMENT_FAILURES = "placementFailures"
KEY_QUERY = "query"
KEY_RESOURCE_ID = "resourceId"
KEY_REQUEST_ID = "requestId"
KEY_ROOT = "root"
KEY_ROWS = "rows"
KEY_REQUIRED_FIELDS = "requiredFields"
KEY_REQUIREMENT_COUNT = "requirementCount"
KEY_REQUIREMENTS = "requirements"
KEY_REVISION = "revision"
KEY_ROLE = "role"
KEY_SCREEN = "screen"
KEY_SHARED_STORAGE = "sharedStorage"
KEY_SCRIPT_LOG_ACTION_TYPE = "actionType"
KEY_SCRIPT_LOG_DIAGNOSTIC = "diagnostic"
KEY_SCRIPT_LOG_EVENT = "event"
KEY_SCRIPT_LOG_EVENT_TYPE = "eventType"
KEY_SCRIPT_LOG_LEVEL = "level"
KEY_SCRIPT_LOG_OUTCOME = "outcome"
KEY_SCRIPT_LOG_POLICY_ID = "policyId"
KEY_SCRIPT_LOG_REASON = "reason"
KEY_SCRIPT_LOG_RECORDS = "records"
KEY_SCRIPT_LOG_SCRIPT_ID = "scriptId"
KEY_SCRIPT_LOG_SCRIPT_KIND = "scriptKind"
KEY_SCRIPT_LOG_TIMESTAMP = "timestamp"
KEY_SCRIPT_LOGS_TRUNCATED = "truncated"
KEY_SELECTED_PAGE_ID = "selectedPageId"
KEY_SAFE_MODE = "safeMode"
KEY_SAFE_MODE_ALLOWED_COMMANDS = "safeModeAllowedCommands"
KEY_SAFE_MODE_ALLOWED_TAP_IDS = "safeModeAllowedTapIds"
KEY_SEMANTIC_ID = "semanticId"
KEY_SCRIPT_ID = "scriptId"
KEY_SOURCE_WIDGET_ADDRESS = "sourceWidgetAddress"
KEY_SIDE_EFFECT = "sideEffect"
KEY_STATE = "state"
KEY_SNAPSHOT_ID = "snapshotId"
KEY_SNAPSHOT = "snapshot"
KEY_TEXT = "text"
KEY_TIMEOUT_MILLISECONDS = "timeoutMilliseconds"
KEY_TRANSPORTS = "transports"
KEY_TYPE = "type"
KEY_VISIBLE = "visible"
KEY_WIDGETS = "widgets"
KEY_WIDGET_ADDRESS = "widgetAddress"
KEY_WIDGET_TYPE = "widgetType"
KEY_WAKE_UP = "wakeUp"
KEY_WEBSOCKET = "webSocket"
TYPE_ADD_WIDGET = "addWidget"
TYPE_CONFIG_GET = "configGet"
TYPE_CONFIG_REPLACE = "configReplace"
TYPE_CONFIG_SEED = "configSeed"
TYPE_AUTOMATION_DISPATCH = "automationDispatch"
TYPE_AUTOMATION_SERVICE_SYNC = "automationServiceSync"
TYPE_AUTOMATION_STATUS = "automationStatus"
TYPE_AUTOMATION_TRIGGER = "automationTrigger"
TYPE_ACCESSIBILITY_ACTION = "accessibilityAction"
TYPE_ACCESSIBILITY_SNAPSHOT = "accessibilitySnapshot"
TYPE_APP_ACTION = "appAction"
TYPE_APP_CATALOGUE = "appCatalogue"
TYPE_CONTROL_STATUS = "controlStatus"
TYPE_DIAGNOSTICS = "diagnostics"
TYPE_ERROR = "error"
TYPE_EVENT = "event"
TYPE_FIND = "find"
TYPE_HOME_GET = "homeGet"
TYPE_HTML_WIDGET_RENDERER_CRASH = "htmlWidgetRendererCrash"
TYPE_HELLO = "hello"
TYPE_INTENT = "intent"
TYPE_LAUNCH_APP = "launchApp"
TYPE_LONG_PRESS = "longPress"
TYPE_OPEN_AUTOMATION_SETUP = "openAutomationSetup"
TYPE_REMOTE_AUTH_DISABLE = "remoteAuthDisable"
TYPE_REMOTE_AUTH_ENABLE = "remoteAuthEnable"
TYPE_REMOTE_AUTH_STATUS = "remoteAuthStatus"
TYPE_RESULT = "result"
TYPE_RESET = "reset"
TYPE_SCREENSHOT = "screenshot"
TYPE_SCROLL_BY = "scrollBy"
TYPE_SCROLL_TO = "scrollTo"
TYPE_SET_TEXT = "setText"
TYPE_SELECT_PAGE = "selectPage"
TYPE_SHELL = "shell"
TYPE_SCRIPT_LOGS = "scriptLogs"
TYPE_SNAPSHOT = "snapshot"
TYPE_TAP = "tap"
TYPE_UI_DUMP = "uiDump"
TYPE_WAIT_FOR = "waitFor"
TYPE_GRID_SET = "gridSet"
TYPE_WIDGET_GET = "widgetGet"
TYPE_WIDGET_MOVE = "widgetMove"
TYPE_WIDGET_RESIZE = "widgetResize"
WIDGET_TYPE_APP = "app"
WIDGET_TYPE_HTML = "html"
VALUE_APP_SANDBOX = "app_sandbox"
VALUE_INTENT_BROADCAST = "broadcast"
VALUE_SMOKE_BROADCAST_ACTION = "eu.psyb0t.dikciz.ACTION_WEBSOCKET_SMOKE"
WEBSOCKET_ACCEPT_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
AUTOMATION_STATUS_ACCESS_KEYS = {
    KEY_ACCESSIBILITY,
    KEY_ACTIVITY_RECOGNITION,
    KEY_BODY_SENSORS,
    KEY_BODY_SENSORS_BACKGROUND,
    KEY_BLUETOOTH_CONNECT,
    KEY_CALENDAR_READ,
    KEY_CONTACTS_READ,
    "clipboardMonitorActive",
    "deviceAdminActive",
    KEY_HEALTH_DATA_BACKGROUND,
    KEY_HEART_RATE_READ,
    KEY_HOME_ROLE,
    "healthConnectAvailable",
    KEY_PHONE_STATE_READ,
    KEY_SMS_RECEIVE,
    KEY_SMS_SEND,
    "highSamplingRateSensors",
    "locationApproximate",
    "locationPrecise",
    KEY_MEDIA_VOLUME_MUTABLE,
    "notificationListener",
    "notificationPolicyAccess",
    "notificationPosting",
    KEY_SHARED_STORAGE,
}
AUTOMATION_STATUS_ACTIONS = {
    "accessibilityAction",
    "accessibilityGesture",
    "accessibilityGlobalAction",
    "appAction",
    "emitEvent",
    "explicitIntent",
    "launchApp",
    "lockDevice",
    "mediaControl",
    "mediaVolume",
    "notificationControl",
    "patchDom",
    "patchState",
    "patchWidget",
    "postNotification",
    "selectPage",
    "sendSms",
}
AUTOMATION_STATUS_CAPABILITIES = {
    "accessibility",
    "alarm",
    "appPackagesMetadata",
    "battery",
    "bluetoothState",
    "calendarEventsContent",
    "calendarEventsMetadata",
    "contactsMetadata",
    "clipboardContent",
    "clipboardMetadata",
    "charging",
    "powerSaveState",
    "deviceIdleState",
    "nightModeState",
    "deviceConfiguration",
    "ringerModeState",
    "interruptionFilterState",
    "connectivity",
    "deviceAdministration",
    "healthSteps",
    "locationApproximate",
    "locationPrecise",
    "manualTrigger",
    "mediaSessionsContent",
    "mediaSessionsMetadata",
    "notificationsContent",
    "notificationsMetadata",
    "phoneState",
    "screen",
    "sensors",
    "smsContent",
    "smsMetadata",
    "thermalState",
    "time",
    "userPresence",
    "widgetState",
}
AUTOMATION_STATUS_EVENT_TYPES = {
    "accessibilityServiceState",
    "accessibilityWindow",
    "alarm",
    "battery",
    "bluetoothState",
    "calendarEvent",
    "contactsChanged",
    "clipboardChanged",
    "charging",
    "powerSaveMode",
    "deviceIdleMode",
    "nightMode",
    "deviceConfiguration",
    "packageChanged",
    "ringerMode",
    "interruptionFilter",
    "connectivity",
    "deviceAdminState",
    "healthDailySteps",
    "location",
    "manual",
    "mediaSession",
    "notificationPosted",
    "notificationRemoved",
    "phoneState",
    "screen",
    "sensor",
    "smsReceived",
    "thermalStatus",
    "time",
    "userPresent",
    "widgetChanged",
}
AUTOMATION_STATUS_INTENT_TYPES = {"activity", "broadcast"}
AUTOMATION_STATUS_SENSOR_KEYS = {
    "maximumDelayMicroseconds",
    "minimumDelayMicroseconds",
    "name",
    KEY_TYPE,
    "vendor",
    "wakeUp",
}
AUTOMATION_STATUS_SENSITIVE_FIELD_NAMES = {
    "command",
    "java",
    "latitude",
    "longitude",
    "payload",
    "shell",
}
DEVICE_ACCESS_ENTRY_KEYS = {KEY_ID, KEY_KIND, KEY_STATE}
DEVICE_ACCESS_KINDS = {
    "deviceAdministration",
    "onDemand",
    "role",
    "runtimePermission",
    "systemSettings",
}
DEVICE_ACCESS_NON_ACTIONABLE_STATES = {"onDemand", "unavailable"}
DEVICE_ACCESS_REQUIREMENTS = {
    "accessibility",
    "activityRecognition",
    "bluetooth",
    "calendar",
    "callHistory",
    "cameraAndMicrophone",
    "contacts",
    "deviceAdministration",
    "exactAlarms",
    "ignoreBatteryOptimizations",
    "locationBackground",
    "locationForeground",
    "mediaLibrary",
    "nearbyWifi",
    "notificationListener",
    "notificationPolicy",
    "notifications",
    "overlay",
    "rootShell",
    "sharedStorage",
    "sms",
    "smsRole",
    "systemSettings",
    "telephony",
    "usageAccess",
}
DEVICE_ACCESS_STATES = {"granted", "needsSetup", "onDemand", "unavailable"}
CONTROL_STATUS_COMMAND_FIELDS = {
    TYPE_ACCESSIBILITY_ACTION: (KEY_ACTION, KEY_NODE_ID, KEY_SNAPSHOT_ID, KEY_TEXT),
    TYPE_ACCESSIBILITY_SNAPSHOT: (),
    TYPE_ADD_WIDGET: (KEY_WIDGET_TYPE,),
    TYPE_APP_ACTION: (KEY_ACTION, KEY_COMPONENT),
    TYPE_APP_CATALOGUE: (KEY_QUERY,),
    TYPE_AUTOMATION_DISPATCH: (KEY_ACTIONS, KEY_SOURCE_WIDGET_ADDRESS),
    TYPE_AUTOMATION_SERVICE_SYNC: (),
    TYPE_AUTOMATION_STATUS: (),
    TYPE_AUTOMATION_TRIGGER: (KEY_SCRIPT_ID,),
    TYPE_CONFIG_GET: (),
    TYPE_CONFIG_REPLACE: (KEY_CONFIG,),
    TYPE_CONFIG_SEED: (KEY_CONFIG,),
    TYPE_CONTROL_STATUS: (),
    TYPE_DIAGNOSTICS: (),
    TYPE_FIND: (KEY_SEMANTIC_ID,),
    TYPE_GRID_SET: (KEY_COLUMNS, KEY_GAP_DP, KEY_OUTER_PADDING_DP, KEY_ROWS),
    TYPE_HOME_GET: (),
    TYPE_HTML_WIDGET_RENDERER_CRASH: (KEY_WIDGET_ADDRESS,),
    TYPE_INTENT: (KEY_ACTION, KEY_INTENT_TYPE),
    TYPE_LAUNCH_APP: (KEY_SEMANTIC_ID,),
    TYPE_LONG_PRESS: (KEY_SEMANTIC_ID,),
    TYPE_OPEN_AUTOMATION_SETUP: (),
    TYPE_REMOTE_AUTH_DISABLE: (),
    TYPE_REMOTE_AUTH_ENABLE: (),
    TYPE_REMOTE_AUTH_STATUS: (),
    TYPE_RESET: (),
    TYPE_SCREENSHOT: (),
    TYPE_SCROLL_BY: (KEY_DELTA_Y,),
    TYPE_SCROLL_TO: (KEY_SEMANTIC_ID,),
    TYPE_SELECT_PAGE: (KEY_PAGE_ID,),
    TYPE_SET_TEXT: (KEY_SEMANTIC_ID, KEY_TEXT),
    TYPE_SHELL: (KEY_COMMAND, KEY_ROOT),
    TYPE_SCRIPT_LOGS: (),
    TYPE_SNAPSHOT: (),
    TYPE_TAP: (KEY_SEMANTIC_ID,),
    TYPE_UI_DUMP: (),
    TYPE_WAIT_FOR: (KEY_SEMANTIC_ID, KEY_TIMEOUT_MILLISECONDS),
    TYPE_WIDGET_GET: (KEY_WIDGET_ADDRESS,),
    TYPE_WIDGET_MOVE: (KEY_CELL, KEY_WIDGET_ADDRESS),
    TYPE_WIDGET_RESIZE: (KEY_CELL, KEY_WIDGET_ADDRESS),
}
CONTROL_STATUS_DEVICE_COMMAND_TYPES = {
    TYPE_APP_ACTION,
    TYPE_AUTOMATION_SERVICE_SYNC,
    TYPE_INTENT,
    TYPE_LAUNCH_APP,
    TYPE_SHELL,
}
CONTROL_STATUS_INTERACTIVE_COMMAND_TYPES = {TYPE_ACCESSIBILITY_ACTION, TYPE_TAP}
CONTROL_STATUS_READ_COMMAND_TYPES = {
    TYPE_ACCESSIBILITY_SNAPSHOT,
    TYPE_APP_CATALOGUE,
    TYPE_AUTOMATION_STATUS,
    TYPE_CONFIG_GET,
    TYPE_CONTROL_STATUS,
    TYPE_DIAGNOSTICS,
    TYPE_FIND,
    TYPE_HOME_GET,
    TYPE_REMOTE_AUTH_STATUS,
    TYPE_SCREENSHOT,
    TYPE_SNAPSHOT,
    TYPE_SCRIPT_LOGS,
    TYPE_UI_DUMP,
    TYPE_WAIT_FOR,
    TYPE_WIDGET_GET,
}
CONTROL_STATUS_SAFE_MODE_ALLOWED_COMMAND_TYPES = {
    TYPE_ACCESSIBILITY_SNAPSHOT,
    TYPE_APP_CATALOGUE,
    TYPE_AUTOMATION_STATUS,
    TYPE_CONFIG_GET,
    TYPE_CONTROL_STATUS,
    TYPE_DIAGNOSTICS,
    TYPE_HOME_GET,
    TYPE_RESET,
    TYPE_SCREENSHOT,
    TYPE_SNAPSHOT,
    TYPE_SCRIPT_LOGS,
    TYPE_UI_DUMP,
    TYPE_WIDGET_GET,
}
CONTROL_STATUS_SAFE_MODE_ALLOWED_TAP_IDS = {"safeMode:exit", "safeMode:reset"}
CONTROL_STATUS_MCP_TOOLS = {
    TYPE_ACCESSIBILITY_ACTION: "dikciz_accessibility_action",
    TYPE_ACCESSIBILITY_SNAPSHOT: "dikciz_accessibility_snapshot",
    TYPE_ADD_WIDGET: "dikciz_add_widget",
    TYPE_APP_ACTION: "dikciz_app_action",
    TYPE_APP_CATALOGUE: "dikciz_app_catalogue",
    TYPE_AUTOMATION_DISPATCH: "dikciz_automation_dispatch",
    TYPE_AUTOMATION_SERVICE_SYNC: "dikciz_automation_service_sync",
    TYPE_AUTOMATION_STATUS: "dikciz_automation_status",
    TYPE_AUTOMATION_TRIGGER: "dikciz_automation_trigger",
    TYPE_CONFIG_GET: "dikciz_config_get",
    TYPE_CONFIG_REPLACE: "dikciz_config_replace",
    TYPE_CONFIG_SEED: "dikciz_config_seed",
    TYPE_CONTROL_STATUS: "dikciz_control_status",
    TYPE_DIAGNOSTICS: "dikciz_diagnostics",
    TYPE_FIND: "dikciz_find",
    TYPE_GRID_SET: "dikciz_grid_set",
    TYPE_HOME_GET: "dikciz_home_get",
    TYPE_HTML_WIDGET_RENDERER_CRASH: "dikciz_html_widget_renderer_crash",
    TYPE_INTENT: "dikciz_intent",
    TYPE_LAUNCH_APP: "dikciz_launch_app",
    TYPE_LONG_PRESS: "dikciz_long_press",
    TYPE_OPEN_AUTOMATION_SETUP: "dikciz_open_automation_setup",
    TYPE_REMOTE_AUTH_DISABLE: "dikciz_remote_auth_disable",
    TYPE_REMOTE_AUTH_ENABLE: "dikciz_remote_auth_enable",
    TYPE_REMOTE_AUTH_STATUS: "dikciz_remote_auth_status",
    TYPE_RESET: "dikciz_config_reset",
    TYPE_SCREENSHOT: "dikciz_screenshot",
    TYPE_SCROLL_BY: "dikciz_scroll_by",
    TYPE_SCROLL_TO: "dikciz_scroll_to",
    TYPE_SELECT_PAGE: "dikciz_select_page",
    TYPE_SET_TEXT: "dikciz_set_text",
    TYPE_SHELL: "dikciz_shell",
    TYPE_SCRIPT_LOGS: "dikciz_script_logs",
    TYPE_SNAPSHOT: "dikciz_snapshot",
    TYPE_TAP: "dikciz_tap",
    TYPE_UI_DUMP: "dikciz_ui_dump",
    TYPE_WAIT_FOR: "dikciz_wait_for",
    TYPE_WIDGET_GET: "dikciz_widget_get",
    TYPE_WIDGET_MOVE: "dikciz_widget_move",
    TYPE_WIDGET_RESIZE: "dikciz_widget_resize",
}
SCRIPT_LOG_RECORD_KEYS = {
    KEY_SCRIPT_LOG_ACTION_TYPE,
    KEY_SCRIPT_LOG_DIAGNOSTIC,
    KEY_SCRIPT_LOG_EVENT,
    KEY_SCRIPT_LOG_EVENT_TYPE,
    KEY_SCRIPT_LOG_LEVEL,
    KEY_SCRIPT_LOG_OUTCOME,
    KEY_SCRIPT_LOG_POLICY_ID,
    KEY_SCRIPT_LOG_REASON,
    KEY_SCRIPT_LOG_SCRIPT_ID,
    KEY_SCRIPT_LOG_SCRIPT_KIND,
    KEY_SCRIPT_LOG_TIMESTAMP,
}
SCRIPT_LOG_REQUIRED_STRING_KEYS = {
    KEY_SCRIPT_LOG_EVENT,
    KEY_SCRIPT_LOG_LEVEL,
    KEY_SCRIPT_LOG_SCRIPT_ID,
    KEY_SCRIPT_LOG_TIMESTAMP,
}
CONTROL_STATUS_LIMIT_KEYS = {
    "maximumAppQueryCharacters",
    "maximumComponentCharacters",
    "maximumIntentActionCharacters",
    "maximumSemanticIdCharacters",
    "maximumShellCommandCharacters",
    "maximumTextCharacters",
    "scrollDelta",
    "waitTimeoutMilliseconds",
}
CONTROL_STATUS_EVENT_FIELDS = {
    "automation_command_completed": (
        (KEY_REQUEST_ID, KEY_COMMAND_TYPE, KEY_OUTCOME),
        (KEY_CODE,),
    ),
    "automation_ui_rendered": ((KEY_SCREEN, KEY_SELECTED_PAGE_ID), ()),
    "html_widget_event": ((KEY_EVENT,), ()),
}
CONTROL_STATUS_PLACEMENT_FAILURES = ("grid_bounds", "grid_collision", "page_full")
CONTROL_STATUS_SIDE_EFFECT_DEVICE = "device"
CONTROL_STATUS_SIDE_EFFECT_INTERACTIVE = "interactive"
CONTROL_STATUS_SIDE_EFFECT_LAUNCHER = "launcher"
CONTROL_STATUS_SIDE_EFFECT_READ = "read"
CONTROL_STATUS_TRANSPORT_KEYS = {KEY_MCP, KEY_WEBSOCKET}
CONTROL_STATUS_TRANSPORT_VALUE_KEYS = {KEY_PATH, KEY_PORT}
CONTROL_STATUS_WEBSOCKET_PATH = "/v1/automation"
CONTROL_STATUS_MCP_PATH = "/mcp"
CONTROL_STATUS_WEBSOCKET_PORT = 19_001
CONTROL_STATUS_MCP_PORT = 19_002


class WebSocketProtocolError(RuntimeError):
    """The control plane did not meet its local WebSocket contract."""


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", default=DEFAULT_HOST)
    parser.add_argument("--port", default=DEFAULT_PORT, type=int)
    parser.add_argument("--expected-control-port", type=int)
    parser.add_argument("--probe", action="store_true")
    parser.add_argument("--mutate", action="store_true")
    parser.add_argument("--reset-to-bundled", action="store_true")
    arguments = parser.parse_args()
    if sum((arguments.probe, arguments.mutate, arguments.reset_to_bundled)) > 1:
        parser.error("--probe, --mutate, and --reset-to-bundled cannot be combined")
    return arguments


def receive_exact(connection: socket.socket, size: int) -> bytes:
    chunks: list[bytes] = []
    remaining = size
    while remaining:
        chunk = connection.recv(remaining)
        if not chunk:
            raise WebSocketProtocolError("control plane closed the socket early")
        chunks.append(chunk)
        remaining -= len(chunk)
    return b"".join(chunks)


def perform_handshake(
    connection: socket.socket,
    host: str,
    port: int,
    authorization: str | None = None,
) -> None:
    key = base64.b64encode(secrets.token_bytes(16)).decode("ascii")
    request_lines = [
        f"GET {AUTOMATION_PATH} HTTP/1.1",
        f"Host: {host}:{port}",
        "Upgrade: websocket",
        "Connection: Upgrade",
        f"Sec-WebSocket-Key: {key}",
        "Sec-WebSocket-Version: 13",
    ]
    if authorization is not None:
        request_lines.append(f"{HEADER_AUTHORIZATION}: {authorization}")
    request = "\r\n".join((*request_lines, "", ""))
    connection.sendall(request.encode("ascii"))
    response = bytearray()
    while not response.endswith(HANDSHAKE_SUFFIX):
        if len(response) >= HANDSHAKE_MAX_BYTES:
            raise WebSocketProtocolError("control plane handshake is oversized")
        response.extend(receive_exact(connection, 1))
    lines = response.decode("ascii").split("\r\n")
    if lines[0] != "HTTP/1.1 101 Switching Protocols":
        raise WebSocketProtocolError(f"unexpected handshake response: {lines[0]}")
    headers = {
        name.strip().lower(): value.strip()
        for line in lines[1:]
        if line and ":" in line
        for name, value in (line.split(":", maxsplit=1),)
    }
    expected_accept = base64.b64encode(
        hashlib.sha1(f"{key}{WEBSOCKET_ACCEPT_GUID}".encode("ascii")).digest(),
    ).decode("ascii")
    if headers.get("sec-websocket-accept") != expected_accept:
        raise WebSocketProtocolError("control plane returned an invalid WebSocket accept key")


def send_frame(connection: socket.socket, opcode: int, payload: bytes) -> None:
    payload_size = len(payload)
    if payload_size > MAXIMUM_FRAME_BYTES:
        raise WebSocketProtocolError("test payload exceeds the protocol limit")
    mask = secrets.token_bytes(4)
    header = bytearray((FRAME_FIN_BIT | opcode,))
    if payload_size <= 125:
        header.append(FRAME_MASK_BIT | payload_size)
    elif payload_size <= 65_535:
        header.extend((FRAME_MASK_BIT | 126,))
        header.extend(struct.pack("!H", payload_size))
    else:
        header.extend((FRAME_MASK_BIT | 127,))
        header.extend(struct.pack("!Q", payload_size))
    masked_payload = bytes(value ^ mask[index % len(mask)] for index, value in enumerate(payload))
    connection.sendall(bytes(header) + mask + masked_payload)


def receive_frame(connection: socket.socket) -> tuple[int, bytes]:
    first_byte, second_byte = receive_exact(connection, 2)
    if first_byte & FRAME_FIN_BIT == 0 or second_byte & FRAME_MASK_BIT:
        raise WebSocketProtocolError("control plane sent an invalid WebSocket frame")
    payload_size = second_byte & 0x7F
    if payload_size == 126:
        payload_size = struct.unpack("!H", receive_exact(connection, 2))[0]
    elif payload_size == 127:
        payload_size = struct.unpack("!Q", receive_exact(connection, 8))[0]
    if payload_size > MAXIMUM_FRAME_BYTES:
        raise WebSocketProtocolError("control plane sent an oversized WebSocket frame")
    return first_byte & FRAME_OPCODE_MASK, receive_exact(connection, payload_size)


def receive_json(connection: socket.socket) -> dict[str, Any]:
    while True:
        opcode, payload = receive_frame(connection)
        if opcode == FRAME_OPCODE_PING:
            send_frame(connection, FRAME_OPCODE_PONG, payload)
            continue
        if opcode != FRAME_OPCODE_TEXT:
            raise WebSocketProtocolError(f"unexpected response frame opcode: {opcode}")
        value = json.loads(payload.decode("utf-8"))
        if not isinstance(value, dict):
            raise WebSocketProtocolError("control plane response is not an object")
        return value


def receive_response(connection: socket.socket, request_id: str) -> dict[str, Any]:
    deadline = time.monotonic() + RESPONSE_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        response = receive_json(connection)
        if response.get("type") == TYPE_EVENT:
            continue
        if response.get("requestId") != request_id:
            raise WebSocketProtocolError("control plane response has the wrong request ID")
        return response
    raise WebSocketProtocolError("control plane did not return a response in time")


def receive_event(connection: socket.socket, event_type: str) -> dict[str, Any]:
    deadline = time.monotonic() + RESPONSE_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        event = receive_json(connection)
        if event.get(KEY_TYPE) != TYPE_EVENT:
            raise WebSocketProtocolError("control plane sent a response to an event observer")
        if event.get(KEY_EVENT) == event_type:
            return event
    raise WebSocketProtocolError("control plane did not return its expected event in time")


def request(connection: socket.socket, command_type: str, **fields: Any) -> dict[str, Any]:
    _, result = request_with_id(connection, command_type, **fields)
    return result


def request_with_id(
    connection: socket.socket,
    command_type: str,
    **fields: Any,
) -> tuple[str, dict[str, Any]]:
    request_id = str(uuid.uuid4())
    message = {KEY_REQUEST_ID: request_id, KEY_TYPE: command_type, **fields}
    send_frame(connection, FRAME_OPCODE_TEXT, json.dumps(message, separators=(",", ":")).encode("utf-8"))
    response = receive_response(connection, request_id)
    if response.get("type") != TYPE_RESULT:
        raise WebSocketProtocolError(f"control plane returned an error: {response}")
    result = response.get("result")
    if not isinstance(result, dict):
        raise WebSocketProtocolError("control plane result is not an object")
    return request_id, result


def request_error_with_id(
    connection: socket.socket,
    command_type: str,
    **fields: Any,
) -> tuple[str, dict[str, Any]]:
    request_id = str(uuid.uuid4())
    message = {KEY_REQUEST_ID: request_id, KEY_TYPE: command_type, **fields}
    send_frame(connection, FRAME_OPCODE_TEXT, json.dumps(message, separators=(",", ":")).encode("utf-8"))
    response = receive_response(connection, request_id)
    if response.get(KEY_TYPE) != TYPE_ERROR:
        raise WebSocketProtocolError(f"control plane returned a result: {response}")
    return request_id, response


def hello(connection: socket.socket) -> dict[str, Any]:
    request_id = str(uuid.uuid4())
    message = {
        KEY_REQUEST_ID: request_id,
        KEY_TYPE: TYPE_HELLO,
        KEY_PROTOCOL_VERSION: PROTOCOL_VERSION,
    }
    send_frame(connection, FRAME_OPCODE_TEXT, json.dumps(message, separators=(",", ":")).encode("utf-8"))
    response = receive_response(connection, request_id)
    if response.get("type") != TYPE_HELLO:
        raise WebSocketProtocolError(f"control plane rejected hello: {response}")
    snapshot = response.get("snapshot")
    if not isinstance(snapshot, dict):
        raise WebSocketProtocolError("hello response has no UI snapshot")
    return snapshot


def require_node(snapshot: dict[str, Any], semantic_id: str) -> dict[str, Any]:
    nodes = snapshot.get("nodes")
    if not isinstance(nodes, list):
        raise WebSocketProtocolError("snapshot has no nodes")
    for node in nodes:
        if isinstance(node, dict) and node.get("semanticId") == semantic_id:
            return node
    raise WebSocketProtocolError(f"snapshot has no semantic ID: {semantic_id}")


def require_configuration(result: dict[str, Any]) -> dict[str, Any]:
    configuration = result.get(KEY_CONFIG)
    if not isinstance(configuration, dict):
        raise WebSocketProtocolError("configuration result has no document")
    return configuration


def verify_script_logs(connection: socket.socket) -> None:
    snapshot = request(connection, TYPE_SCRIPT_LOGS)
    if set(snapshot) != {KEY_SCRIPT_LOG_RECORDS, KEY_SCRIPT_LOGS_TRUNCATED}:
        raise WebSocketProtocolError("script logs returned an unexpected schema")
    records = snapshot.get(KEY_SCRIPT_LOG_RECORDS)
    if not isinstance(records, list) or not isinstance(snapshot.get(KEY_SCRIPT_LOGS_TRUNCATED), bool):
        raise WebSocketProtocolError("script logs returned invalid snapshot metadata")
    for record in records:
        if not isinstance(record, dict) or set(record) != SCRIPT_LOG_RECORD_KEYS:
            raise WebSocketProtocolError("script logs returned an invalid record")
        for key, value in record.items():
            if key in SCRIPT_LOG_REQUIRED_STRING_KEYS and not isinstance(value, str):
                raise WebSocketProtocolError("script logs omitted a required string field")
            if key not in SCRIPT_LOG_REQUIRED_STRING_KEYS and value is not None and not isinstance(value, str):
                raise WebSocketProtocolError("script logs returned an invalid optional field")


def verify_device_access(document: Any) -> None:
    if not isinstance(document, dict) or set(document) != {
        KEY_GRANTED_COUNT,
        KEY_REQUIREMENT_COUNT,
        KEY_REQUIREMENTS,
    }:
        raise WebSocketProtocolError("device access returned an unexpected schema")
    requirements = document.get(KEY_REQUIREMENTS)
    if not isinstance(requirements, list) or len(requirements) != len(DEVICE_ACCESS_REQUIREMENTS):
        raise WebSocketProtocolError("device access returned an invalid requirement inventory")
    requirement_ids = set()
    actionable_count = 0
    granted_count = 0
    for requirement in requirements:
        if not isinstance(requirement, dict) or set(requirement) != DEVICE_ACCESS_ENTRY_KEYS:
            raise WebSocketProtocolError("device access returned an invalid requirement")
        requirement_id = requirement.get(KEY_ID)
        kind = requirement.get(KEY_KIND)
        state = requirement.get(KEY_STATE)
        if not isinstance(requirement_id, str) or kind not in DEVICE_ACCESS_KINDS or state not in DEVICE_ACCESS_STATES:
            raise WebSocketProtocolError("device access returned an invalid requirement value")
        requirement_ids.add(requirement_id)
        if state not in DEVICE_ACCESS_NON_ACTIONABLE_STATES:
            actionable_count += 1
            granted_count += state == "granted"
    if requirement_ids != DEVICE_ACCESS_REQUIREMENTS:
        raise WebSocketProtocolError("device access omitted a supported requirement")
    if document.get(KEY_REQUIREMENT_COUNT) != actionable_count or document.get(KEY_GRANTED_COUNT) != granted_count:
        raise WebSocketProtocolError("device access returned invalid requirement totals")


def verify_automation_status(connection: socket.socket) -> None:
    status = request(connection, TYPE_AUTOMATION_STATUS)
    expected_keys = {
        KEY_ACTIONS,
        KEY_ACTIVE_MEDIA_SESSION_COUNT,
        KEY_ACTIVE_MEDIA_SESSION_LIMIT,
        KEY_ACTIVE_MEDIA_SESSIONS_TRUNCATED,
        KEY_ANDROID_ACCESS,
        KEY_API_VERSION,
        KEY_AVAILABLE_SENSOR_COUNT,
        KEY_AVAILABLE_SENSOR_LIMIT,
        KEY_AVAILABLE_SENSORS,
        KEY_AVAILABLE_SENSORS_TRUNCATED,
        KEY_CAPABILITIES,
        KEY_DEVICE_ACCESS,
        KEY_EVENTS,
        KEY_INTENT_TYPES,
    }
    if set(status) != expected_keys or status.get(KEY_API_VERSION) != PROTOCOL_VERSION:
        raise WebSocketProtocolError("automation status returned an unexpected schema")
    if set(status.get(KEY_CAPABILITIES, [])) != AUTOMATION_STATUS_CAPABILITIES:
        raise WebSocketProtocolError("automation status omitted a policy capability")
    if set(status.get(KEY_ACTIONS, [])) != AUTOMATION_STATUS_ACTIONS:
        raise WebSocketProtocolError("automation status omitted an action")
    if set(status.get(KEY_INTENT_TYPES, [])) != AUTOMATION_STATUS_INTENT_TYPES:
        raise WebSocketProtocolError("automation status omitted an intent type")
    events = status.get(KEY_EVENTS)
    event_types = {event.get(KEY_TYPE) for event in events if isinstance(event, dict)} if isinstance(events, list) else set()
    if event_types != AUTOMATION_STATUS_EVENT_TYPES:
        raise WebSocketProtocolError("automation status omitted an event type")
    access = status.get(KEY_ANDROID_ACCESS)
    if not isinstance(access, dict) or set(access) != AUTOMATION_STATUS_ACCESS_KEYS or not all(
        isinstance(value, bool) for value in access.values()
    ):
        raise WebSocketProtocolError("automation status returned invalid Android access state")
    verify_device_access(status.get(KEY_DEVICE_ACCESS))
    sensors = status.get(KEY_AVAILABLE_SENSORS)
    sensor_count = status.get(KEY_AVAILABLE_SENSOR_COUNT)
    if (
        not isinstance(sensors, list)
        or not isinstance(sensor_count, int)
        or sensor_count < len(sensors)
        or len(sensors) > MAXIMUM_AVAILABLE_SENSOR_COUNT
        or status.get(KEY_AVAILABLE_SENSOR_LIMIT) != MAXIMUM_AVAILABLE_SENSOR_COUNT
        or status.get(KEY_AVAILABLE_SENSORS_TRUNCATED) is not (sensor_count > len(sensors))
    ):
        raise WebSocketProtocolError("automation status returned an invalid sensor inventory")
    sensor_types = set()
    for sensor in sensors:
        if not isinstance(sensor, dict) or set(sensor) != AUTOMATION_STATUS_SENSOR_KEYS:
            raise WebSocketProtocolError("automation status returned an invalid sensor entry")
        if not isinstance(sensor.get(KEY_TYPE), int) or not isinstance(sensor.get(KEY_WAKE_UP), bool):
            raise WebSocketProtocolError("automation status sensor types are invalid")
        sensor_types.add(sensor[KEY_TYPE])
    if not {EMULATOR_ACCELEROMETER_TYPE, EMULATOR_GYROSCOPE_TYPE}.issubset(sensor_types):
        raise WebSocketProtocolError("automation status omitted emulator sensors used by the live suite")
    if AUTOMATION_STATUS_SENSITIVE_FIELD_NAMES.intersection(status):
        raise WebSocketProtocolError("automation status exposed an unsupported control surface")
    active_media_session_count = status.get(KEY_ACTIVE_MEDIA_SESSION_COUNT)
    if (
        not isinstance(active_media_session_count, int)
        or active_media_session_count < 0
        or status.get(KEY_ACTIVE_MEDIA_SESSION_LIMIT) != MAXIMUM_ACTIVE_MEDIA_SESSION_COUNT
        or status.get(KEY_ACTIVE_MEDIA_SESSIONS_TRUNCATED) is not (
            active_media_session_count > MAXIMUM_ACTIVE_MEDIA_SESSION_COUNT
        )
    ):
        raise WebSocketProtocolError("automation status returned an invalid media-session inventory")


def verify_control_status(connection: socket.socket) -> dict[str, Any]:
    status = request(connection, TYPE_CONTROL_STATUS)
    expected_keys = {
        KEY_COMMANDS,
        KEY_EVENTS,
        KEY_LIMITS,
        KEY_PLACEMENT_FAILURES,
        KEY_PROTOCOL_VERSION,
        KEY_SAFE_MODE,
        KEY_SAFE_MODE_ALLOWED_COMMANDS,
        KEY_SAFE_MODE_ALLOWED_TAP_IDS,
        KEY_TRANSPORTS,
    }
    if set(status) != expected_keys or status.get(KEY_PROTOCOL_VERSION) != PROTOCOL_VERSION:
        raise WebSocketProtocolError("control status returned an unexpected schema")
    if status.get(KEY_PLACEMENT_FAILURES) != list(CONTROL_STATUS_PLACEMENT_FAILURES):
        raise WebSocketProtocolError("control status returned invalid placement failures")
    if not isinstance(status.get(KEY_SAFE_MODE), bool):
        raise WebSocketProtocolError("control status returned invalid safe-mode state")
    if status.get(KEY_SAFE_MODE_ALLOWED_COMMANDS) != sorted(CONTROL_STATUS_SAFE_MODE_ALLOWED_COMMAND_TYPES):
        raise WebSocketProtocolError("control status returned invalid safe-mode command metadata")
    if status.get(KEY_SAFE_MODE_ALLOWED_TAP_IDS) != sorted(CONTROL_STATUS_SAFE_MODE_ALLOWED_TAP_IDS):
        raise WebSocketProtocolError("control status returned invalid safe-mode tap metadata")
    transports = status.get(KEY_TRANSPORTS)
    if not isinstance(transports, dict) or set(transports) != CONTROL_STATUS_TRANSPORT_KEYS:
        raise WebSocketProtocolError("control status returned invalid transports")
    expected_transports = {
        KEY_WEBSOCKET: (CONTROL_STATUS_WEBSOCKET_PATH, CONTROL_STATUS_WEBSOCKET_PORT),
        KEY_MCP: (CONTROL_STATUS_MCP_PATH, CONTROL_STATUS_MCP_PORT),
    }
    for name, (path, port) in expected_transports.items():
        transport = transports.get(name)
        if (
            not isinstance(transport, dict)
            or set(transport) != CONTROL_STATUS_TRANSPORT_VALUE_KEYS
            or transport.get(KEY_PATH) != path
            or transport.get(KEY_PORT) != port
        ):
            raise WebSocketProtocolError("control status returned an invalid transport entry")
    limits = status.get(KEY_LIMITS)
    if not isinstance(limits, dict) or set(limits) != CONTROL_STATUS_LIMIT_KEYS:
        raise WebSocketProtocolError("control status returned invalid limits")
    for range_key in ("scrollDelta", "waitTimeoutMilliseconds"):
        value = limits.get(range_key)
        if (
            not isinstance(value, dict)
            or set(value) != {KEY_MINIMUM, KEY_MAXIMUM}
            or not isinstance(value.get(KEY_MINIMUM), int)
            or not isinstance(value.get(KEY_MAXIMUM), int)
            or value[KEY_MINIMUM] > value[KEY_MAXIMUM]
        ):
            raise WebSocketProtocolError("control status returned an invalid range")
    commands = status.get(KEY_COMMANDS)
    if not isinstance(commands, list) or len(commands) != len(CONTROL_STATUS_COMMAND_FIELDS):
        raise WebSocketProtocolError("control status returned an invalid command inventory")
    observed_types = []
    for command in commands:
        if not isinstance(command, dict) or set(command) != {KEY_FIELDS, KEY_MCP_TOOL, KEY_TYPE, KEY_SIDE_EFFECT}:
            raise WebSocketProtocolError("control status returned an invalid command entry")
        command_type = command.get(KEY_TYPE)
        if command_type not in CONTROL_STATUS_COMMAND_FIELDS:
            raise WebSocketProtocolError("control status returned an unknown command")
        if command.get(KEY_FIELDS) != list(CONTROL_STATUS_COMMAND_FIELDS[command_type]):
            raise WebSocketProtocolError("control status returned invalid command fields")
        if command.get(KEY_MCP_TOOL) != CONTROL_STATUS_MCP_TOOLS[command_type]:
            raise WebSocketProtocolError("control status returned invalid MCP mapping")
        if command_type in CONTROL_STATUS_READ_COMMAND_TYPES:
            expected_side_effect = CONTROL_STATUS_SIDE_EFFECT_READ
        elif command_type in CONTROL_STATUS_DEVICE_COMMAND_TYPES:
            expected_side_effect = CONTROL_STATUS_SIDE_EFFECT_DEVICE
        elif command_type in CONTROL_STATUS_INTERACTIVE_COMMAND_TYPES:
            expected_side_effect = CONTROL_STATUS_SIDE_EFFECT_INTERACTIVE
        else:
            expected_side_effect = CONTROL_STATUS_SIDE_EFFECT_LAUNCHER
        if command.get(KEY_SIDE_EFFECT) != expected_side_effect:
            raise WebSocketProtocolError("control status returned invalid side-effect metadata")
        observed_types.append(command_type)
    if observed_types != sorted(CONTROL_STATUS_COMMAND_FIELDS):
        raise WebSocketProtocolError("control status command inventory is not type-sorted")
    events = status.get(KEY_EVENTS)
    if not isinstance(events, list) or len(events) != len(CONTROL_STATUS_EVENT_FIELDS):
        raise WebSocketProtocolError("control status returned an invalid event inventory")
    observed_event_types = []
    for event in events:
        if not isinstance(event, dict) or set(event) != {KEY_TYPE, KEY_REQUIRED_FIELDS, KEY_OPTIONAL_FIELDS}:
            raise WebSocketProtocolError("control status returned an invalid event entry")
        event_type = event.get(KEY_TYPE)
        expected_fields = CONTROL_STATUS_EVENT_FIELDS.get(event_type)
        if expected_fields is None:
            raise WebSocketProtocolError("control status returned an unknown event")
        if event.get(KEY_REQUIRED_FIELDS) != list(expected_fields[0]):
            raise WebSocketProtocolError("control status returned invalid event required fields")
        if event.get(KEY_OPTIONAL_FIELDS) != list(expected_fields[1]):
            raise WebSocketProtocolError("control status returned invalid event optional fields")
        observed_event_types.append(event_type)
    if observed_event_types != sorted(CONTROL_STATUS_EVENT_FIELDS):
        raise WebSocketProtocolError("control status event inventory is not type-sorted")
    return status


def bundled_configuration() -> dict[str, Any]:
    try:
        configuration = json.loads(BUNDLED_CONFIGURATION_PATH.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise WebSocketProtocolError("could not read the bundled configuration") from error
    if not isinstance(configuration, dict):
        raise WebSocketProtocolError("bundled configuration is not an object")
    return configuration


def bundled_home() -> dict[str, Any]:
    configuration = bundled_configuration()
    launcher = configuration.get(KEY_LAUNCHER)
    if not isinstance(launcher, dict):
        raise WebSocketProtocolError("bundled configuration has no launcher namespace")
    home = launcher.get(KEY_HOME)
    if not isinstance(home, dict):
        raise WebSocketProtocolError("bundled configuration has no launcher home")
    pages = home.get(KEY_PAGES)
    if not isinstance(pages, list):
        raise WebSocketProtocolError("bundled configuration home has no pages")
    for page in pages:
        if not isinstance(page, dict):
            raise WebSocketProtocolError("bundled configuration has an invalid page")
        page.setdefault(KEY_LOCKED, False)
        widgets = page.get(KEY_WIDGETS)
        if not isinstance(widgets, list):
            raise WebSocketProtocolError("bundled configuration page has no widgets")
        for widget in widgets:
            if not isinstance(widget, dict):
                raise WebSocketProtocolError("bundled configuration has an invalid widget")
            widget.setdefault(KEY_LOCKED, False)
            widget_type = widget.get(KEY_TYPE)
            if widget_type == WIDGET_TYPE_APP:
                widget.setdefault(KEY_DISPLAY_STYLE, DEFAULT_APP_WIDGET_DISPLAY_STYLE)
            if widget_type == WIDGET_TYPE_HTML:
                widget.setdefault(KEY_CSS, DEFAULT_HTML_WIDGET_CSS)
                widget.setdefault(KEY_JAVASCRIPT, DEFAULT_HTML_WIDGET_JAVASCRIPT)
                widget.setdefault(KEY_STATE, {})
                widget.setdefault(KEY_HEIGHT_MODE, DEFAULT_HTML_WIDGET_HEIGHT_MODE)
                widget.setdefault(KEY_EVENT_SUBSCRIPTIONS, [])
    return home


def first_json_difference(
    expected: object,
    actual: object,
    path: str = "$",
) -> str | None:
    if type(expected) is not type(actual):
        return f"{path} has a different JSON type"
    if isinstance(expected, dict):
        if set(expected) != set(actual):
            return f"{path} has different keys"
        for key, expected_value in expected.items():
            difference = first_json_difference(expected_value, actual[key], f"{path}.{key}")
            if difference is not None:
                return difference
        return None
    if isinstance(expected, list):
        if len(expected) != len(actual):
            return f"{path} has a different array length"
        for index, expected_value in enumerate(expected):
            difference = first_json_difference(expected_value, actual[index], f"{path}[{index}]")
            if difference is not None:
                return difference
        return None
    return None if expected == actual else path


def reset_to_bundled_configuration(arguments: argparse.Namespace) -> dict[str, Any]:
    expected_control_port = getattr(arguments, "expected_control_port", None) or arguments.port
    expected_home = bundled_home()
    with socket.create_connection((arguments.host, arguments.port), timeout=10) as connection:
        connection.settimeout(10)
        perform_handshake(connection, arguments.host, arguments.port)
        snapshot = hello(connection)
        if snapshot.get("screen") != "home":
            raise WebSocketProtocolError("hello snapshot is not the Dikciz home screen")
        diagnostics = request(connection, TYPE_DIAGNOSTICS)
        if diagnostics.get("controlPort") != expected_control_port:
            raise WebSocketProtocolError("diagnostics returned the wrong control port")

        reset = request(connection, TYPE_RESET)
        configuration = require_configuration(reset)
        launcher = configuration.get(KEY_LAUNCHER)
        actual_home = launcher.get(KEY_HOME) if isinstance(launcher, dict) else None
        difference = first_json_difference(expected_home, actual_home, "launcher.home")
        if difference is not None:
            raise WebSocketProtocolError(f"configuration reset did not return the bundled home: {difference}")
        reset_snapshot = reset.get(KEY_SNAPSHOT)
        if not isinstance(reset_snapshot, dict) or reset_snapshot.get("screen") != "home":
            raise WebSocketProtocolError("configuration reset did not reload the bundled home")
        send_frame(connection, FRAME_OPCODE_CLOSE, b"")
    return {"controlPort": arguments.port, "reset": True, "status": "passed"}


def verify_device_actions(connection: socket.socket) -> None:
    verify_automation_status(connection)
    verify_control_status(connection)
    verify_script_logs(connection)
    configuration = require_configuration(request(connection, TYPE_CONFIG_GET))
    if not isinstance(configuration.get(KEY_LAUNCHER), dict):
        raise WebSocketProtocolError("configuration result has no launcher namespace")

    automation_service_sync = request(connection, TYPE_AUTOMATION_SERVICE_SYNC)
    if automation_service_sync.get(KEY_AUTOMATION_SERVICE_SYNCED) is not True:
        raise WebSocketProtocolError("automation-service sync was not acknowledged")

    waited = request(
        connection,
        TYPE_WAIT_FOR,
        semanticId="pageScroll",
        **{KEY_TIMEOUT_MILLISECONDS: 500},
    )
    if waited.get(KEY_FOUND) is not True:
        raise WebSocketProtocolError("waitFor did not resolve the page scroll node")

    shell = request(
        connection,
        TYPE_SHELL,
        **{
            KEY_COMMAND: SHELL_COMMAND_CURRENT_UID,
            KEY_ROOT: False,
        },
    )
    if shell.get(KEY_EXECUTION_SCOPE) != VALUE_APP_SANDBOX:
        raise WebSocketProtocolError("shell did not report app sandbox execution")
    if shell.get(KEY_EXIT_CODE) != 0:
        raise WebSocketProtocolError("shell command did not exit successfully")
    if not str(shell.get(KEY_OUTPUT, "")).strip().isdigit():
        raise WebSocketProtocolError("shell command did not return the Android app UID")

    intent = request(
        connection,
        TYPE_INTENT,
        action=VALUE_SMOKE_BROADCAST_ACTION,
        intentType=VALUE_INTENT_BROADCAST,
    )
    if intent.get(KEY_DISPATCHED) is not True or intent.get(KEY_INTENT_TYPE) != VALUE_INTENT_BROADCAST:
        raise WebSocketProtocolError("broadcast intent was not dispatched")


def verify_mutations(connection: socket.socket) -> None:
    original = require_configuration(request(connection, TYPE_CONFIG_GET))
    try:
        modified = copy.deepcopy(original)
        home = modified.get(KEY_LAUNCHER, {}).get(KEY_HOME, {})
        pages = home.get(KEY_PAGES)
        if not isinstance(pages, list) or len(pages) < 2:
            raise WebSocketProtocolError("configuration needs two pages for replacement proof")
        alternate_page_id = pages[1].get(KEY_ID)
        if not isinstance(alternate_page_id, str):
            raise WebSocketProtocolError("configuration alternate page has no ID")
        home[KEY_SELECTED_PAGE_ID] = alternate_page_id
        seeded = request(connection, TYPE_CONFIG_SEED, config=modified)
        replacement_snapshot = seeded.get(KEY_SNAPSHOT)
        if not isinstance(replacement_snapshot, dict) or replacement_snapshot.get(KEY_SELECTED_PAGE_ID) != alternate_page_id:
            raise WebSocketProtocolError("configuration seed did not reload the selected page")

        request(connection, TYPE_SELECT_PAGE, pageId="home")
        before = request(connection, TYPE_SNAPSHOT)
        toggle_before = require_node(before, "widget:focus:toggle").get("checked")
        request(connection, TYPE_TAP, semanticId="widget:focus:toggle")
        after = request(connection, TYPE_SNAPSHOT)
        toggle_after = require_node(after, "widget:focus:toggle").get("checked")
        if not isinstance(toggle_before, bool) or toggle_after == toggle_before:
            raise WebSocketProtocolError("tap did not change the toggle widget")
        text = "Dikciz automation test"
        result = request(connection, TYPE_SET_TEXT, semanticId="widget:welcome", text=text)
        if result.get("text") != text:
            raise WebSocketProtocolError("setText did not return the saved text")
        reset = request(connection, TYPE_RESET)
        reset_snapshot = reset.get(KEY_SNAPSHOT)
        if not isinstance(reset_snapshot, dict) or reset_snapshot.get("screen") != "home":
            raise WebSocketProtocolError("configuration reset did not reload the bundled home")
    finally:
        restored = request(connection, TYPE_CONFIG_REPLACE, config=original)
        if require_configuration(restored) != original:
            raise WebSocketProtocolError("configuration restoration did not preserve the original document")


def run_smoke_test(arguments: argparse.Namespace) -> dict[str, Any]:
    expected_control_port = getattr(arguments, "expected_control_port", None) or arguments.port
    with socket.create_connection((arguments.host, arguments.port), timeout=10) as connection:
        connection.settimeout(10)
        perform_handshake(connection, arguments.host, arguments.port)
        snapshot = hello(connection)
        if snapshot.get("screen") != "home":
            raise WebSocketProtocolError("hello snapshot is not the Dikciz home screen")
        require_node(snapshot, "pageScroll")
        found = request(connection, TYPE_FIND, semanticId="pageScroll")
        if found.get("found") is not True:
            raise WebSocketProtocolError("find did not resolve the page scroll view")
        diagnostics = request(connection, TYPE_DIAGNOSTICS)
        if diagnostics.get("controlPort") != expected_control_port:
            raise WebSocketProtocolError("diagnostics returned the wrong control port")
        screenshot = request(connection, TYPE_SCREENSHOT)
        png = base64.b64decode(screenshot.get("pngBase64", ""), validate=True)
        if not png.startswith(PNG_SIGNATURE):
            raise WebSocketProtocolError("screenshot is not PNG data")
        verify_device_actions(connection)
        if arguments.mutate:
            verify_mutations(connection)
        send_frame(connection, FRAME_OPCODE_CLOSE, b"")
    return {"controlPort": arguments.port, "mutations": arguments.mutate, "status": "passed"}


def run_probe(arguments: argparse.Namespace) -> dict[str, Any]:
    with socket.create_connection((arguments.host, arguments.port), timeout=10) as connection:
        connection.settimeout(10)
        perform_handshake(connection, arguments.host, arguments.port)
        # A readiness probe can run before the owner grants first-run storage access.
        # Full smoke coverage still requires configuration and the rendered Home screen.
        hello(connection)
        send_frame(connection, FRAME_OPCODE_CLOSE, b"")
    return {"controlPort": arguments.port, "status": "ready"}


def main() -> None:
    arguments = parse_arguments()
    result = (
        reset_to_bundled_configuration(arguments)
        if arguments.reset_to_bundled
        else run_probe(arguments)
        if arguments.probe
        else run_smoke_test(arguments)
    )
    print(json.dumps(result, sort_keys=True))


if __name__ == "__main__":
    main()
