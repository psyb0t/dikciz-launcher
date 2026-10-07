#!/usr/bin/env python3

"""Prove Dikciz Streamable HTTP MCP through the isolated Android lab forward."""

from __future__ import annotations

import argparse
import base64
import copy
import http.client
import json
from typing import Any

DEFAULT_HOST = "127.0.0.1"
DEFAULT_PORT = 19_002
AUTOMATION_API_VERSION = 1
EMULATOR_ACCELEROMETER_TYPE = 1
EMULATOR_GYROSCOPE_TYPE = 4
HEADER_ACCEPT = "Accept"
HEADER_CONTENT_TYPE = "Content-Type"
HEADER_MCP_PROTOCOL_VERSION = "MCP-Protocol-Version"
HEADER_MCP_SESSION_ID = "MCP-Session-Id"
HEADER_ORIGIN = "Origin"
HTTP_METHOD_DELETE = "DELETE"
HTTP_METHOD_GET = "GET"
HTTP_METHOD_POST = "POST"
HTTP_STATUS_ACCEPTED = 202
HTTP_STATUS_BAD_REQUEST = 400
HTTP_STATUS_FORBIDDEN = 403
HTTP_STATUS_METHOD_NOT_ALLOWED = 405
HTTP_STATUS_NOT_FOUND = 404
HTTP_STATUS_NO_CONTENT = 204
HTTP_STATUS_OK = 200
JSON_RPC_VERSION = "2.0"
MAXIMUM_ACTIVE_MEDIA_SESSION_COUNT = 64
MAXIMUM_AVAILABLE_SENSOR_COUNT = 64
KEY_ARGUMENTS = "arguments"
KEY_ACTION = "action"
KEY_ACCESSIBILITY = "accessibility"
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
KEY_CLIPBOARD_MONITOR_ACTIVE = "clipboardMonitorActive"
KEY_DEVICE_ADMIN_ACTIVE = "deviceAdminActive"
KEY_HEALTH_DATA_BACKGROUND = "healthDataBackground"
KEY_HEART_RATE_READ = "heartRateRead"
KEY_HEALTH_CONNECT_AVAILABLE = "healthConnectAvailable"
KEY_HOME_ROLE = "homeRole"
KEY_PHONE_STATE_READ = "phoneStateRead"
KEY_SMS_RECEIVE = "smsReceive"
KEY_SMS_SEND = "smsSend"
KEY_CAPABILITIES = "capabilities"
KEY_CLIENT_INFO = "clientInfo"
KEY_CODE = "code"
KEY_CONTENT = "content"
KEY_COMMAND = "command"
KEY_ROOT = "root"
KEY_COMMAND_TYPE = "commandType"
KEY_COMMANDS = "commands"
KEY_CONFIG = "config"
KEY_DATA = "data"
KEY_DEVICE_ACCESS = "deviceAccess"
KEY_DISPATCHED = "dispatched"
KEY_EVENT = "event"
KEY_EXECUTION_SCOPE = "executionScope"
KEY_EXIT_CODE = "exitCode"
KEY_EVENTS = "events"
KEY_FOUND = "found"
KEY_FIELDS = "fields"
KEY_GRANTED_COUNT = "grantedCount"
KEY_ID = "id"
KEY_INTENT_TYPE = "intentType"
KEY_INTENT_TYPES = "intentTypes"
KEY_HOME = "home"
KEY_IS_ERROR = "isError"
KEY_LAUNCHER = "launcher"
KEY_LIMITS = "limits"
KEY_KIND = "kind"
KEY_MAXIMUM = "maximum"
KEY_METHOD = "method"
KEY_META = "_meta"
KEY_MIME_TYPE = "mimeType"
KEY_MCP = "mcp"
KEY_MCP_TOOL = "mcpTool"
KEY_MEDIA_VOLUME_MUTABLE = "mediaVolumeMutable"
KEY_MINIMUM = "minimum"
KEY_NODE_ID = "nodeId"
KEY_NAME = "name"
KEY_OUTPUT = "output"
KEY_OPTIONAL_FIELDS = "optionalFields"
KEY_OUTCOME = "outcome"
KEY_PARAMS = "params"
KEY_PAGES = "pages"
KEY_PATH = "path"
KEY_PORT = "port"
KEY_PROTOCOL_VERSION = "protocolVersion"
KEY_RESULT = "result"
KEY_RESULT_TYPE = "resultType"
KEY_REQUEST_ID = "requestId"
KEY_REQUIRED_FIELDS = "requiredFields"
KEY_REQUIREMENT_COUNT = "requirementCount"
KEY_REQUIREMENTS = "requirements"
KEY_SCREEN = "screen"
KEY_SEMANTIC_ID = "semanticId"
KEY_SHARED_STORAGE = "sharedStorage"
KEY_SELECTED_PAGE_ID = "selectedPageId"
KEY_PLACEMENT_FAILURES = "placementFailures"
KEY_SAFE_MODE = "safeMode"
KEY_SAFE_MODE_ALLOWED_COMMANDS = "safeModeAllowedCommands"
KEY_SAFE_MODE_ALLOWED_TAP_IDS = "safeModeAllowedTapIds"
KEY_SIDE_EFFECT = "sideEffect"
KEY_STATE = "state"
KEY_SNAPSHOT_ID = "snapshotId"
KEY_SNAPSHOT = "snapshot"
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
KEY_SCRIPT_ID = "scriptId"
KEY_TIMEOUT_MILLISECONDS = "timeoutMilliseconds"
KEY_TRANSPORTS = "transports"
KEY_STRUCTURED_CONTENT = "structuredContent"
KEY_TOOLS = "tools"
KEY_TYPE = "type"
KEY_WAKE_UP = "wakeUp"
KEY_WEBSOCKET = "webSocket"
MCP_PATH = "/mcp"
MCP_PROTOCOL_VERSION = "2026-07-28"
MIME_TYPE_EVENT_STREAM = "text/event-stream"
MIME_TYPE_JSON = "application/json"
MIME_TYPE_PNG = "image/png"
METHOD_INITIALIZE = "initialize"
METHOD_INITIALIZED = "notifications/initialized"
METHOD_TOOLS_CALL = "tools/call"
METHOD_TOOLS_LIST = "tools/list"
ORIGIN_LOCAL = "http://127.0.0.1"
ORIGIN_UNTRUSTED = "https://example.invalid"
PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
RESULT_TYPE_COMPLETE = "complete"
SHELL_COMMAND_CURRENT_UID = "id -u"
TOOL_DIAGNOSTICS = "dikciz_diagnostics"
TOOL_CONFIG_GET = "dikciz_config_get"
TOOL_CONFIG_REPLACE = "dikciz_config_replace"
TOOL_CONFIG_RESET = "dikciz_config_reset"
TOOL_CONFIG_SEED = "dikciz_config_seed"
TOOL_AUTOMATION_DISPATCH = "dikciz_automation_dispatch"
TOOL_AUTOMATION_SERVICE_SYNC = "dikciz_automation_service_sync"
TOOL_AUTOMATION_STATUS = "dikciz_automation_status"
TOOL_AUTOMATION_TRIGGER = "dikciz_automation_trigger"
TOOL_ACCESSIBILITY_ACTION = "dikciz_accessibility_action"
TOOL_ACCESSIBILITY_SNAPSHOT = "dikciz_accessibility_snapshot"
TOOL_CONTROL_STATUS = "dikciz_control_status"
TOOL_FIND = "dikciz_find"
TOOL_SCREENSHOT = "dikciz_screenshot"
TOOL_SELECT_PAGE = "dikciz_select_page"
TOOL_SNAPSHOT = "dikciz_snapshot"
TOOL_TAP = "dikciz_tap"
TOOL_WAIT_FOR = "dikciz_wait_for"
TOOL_SET_TEXT = "dikciz_set_text"
TOOL_SHELL = "dikciz_shell"
TOOL_SCRIPT_LOGS = "dikciz_script_logs"
TOOL_INTENT = "dikciz_intent"
VALUE_APP_SANDBOX = "app_sandbox"
VALUE_INTENT_BROADCAST = "broadcast"
VALUE_SMOKE_BROADCAST_ACTION = "eu.psyb0t.dikciz.ACTION_MCP_SMOKE"
AUTOMATION_STATUS_ACCESS_KEYS = {
    KEY_ACCESSIBILITY,
    KEY_ACTIVITY_RECOGNITION,
    KEY_BODY_SENSORS,
    KEY_BODY_SENSORS_BACKGROUND,
    KEY_BLUETOOTH_CONNECT,
    KEY_CALENDAR_READ,
    KEY_CONTACTS_READ,
    KEY_CLIPBOARD_MONITOR_ACTIVE,
    KEY_DEVICE_ADMIN_ACTIVE,
    KEY_HEALTH_DATA_BACKGROUND,
    KEY_HEART_RATE_READ,
    KEY_HEALTH_CONNECT_AVAILABLE,
    KEY_HOME_ROLE,
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
    KEY_WAKE_UP,
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
CONTROL_STATUS_DEVICE_SIDE_EFFECT = "device"
CONTROL_STATUS_INTERACTIVE_SIDE_EFFECT = "interactive"
CONTROL_STATUS_LAUNCHER_SIDE_EFFECT = "launcher"
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
CONTROL_STATUS_READ_SIDE_EFFECT = "read"
CONTROL_STATUS_SIDE_EFFECTS = {
    CONTROL_STATUS_DEVICE_SIDE_EFFECT,
    CONTROL_STATUS_INTERACTIVE_SIDE_EFFECT,
    CONTROL_STATUS_LAUNCHER_SIDE_EFFECT,
    CONTROL_STATUS_READ_SIDE_EFFECT,
}
CONTROL_STATUS_TRANSPORT_KEYS = {KEY_MCP, KEY_WEBSOCKET}
CONTROL_STATUS_TRANSPORT_VALUE_KEYS = {KEY_PATH, KEY_PORT}
CONTROL_STATUS_WEBSOCKET_PATH = "/v1/automation"
CONTROL_STATUS_MCP_PATH = "/mcp"
CONTROL_STATUS_WEBSOCKET_PORT = 19_001
CONTROL_STATUS_MCP_PORT = 19_002
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


class McpProtocolError(RuntimeError):
    """The Streamable HTTP MCP endpoint violated its local contract."""


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", default=DEFAULT_HOST)
    parser.add_argument("--port", default=DEFAULT_PORT, type=int)
    parser.add_argument("--expected-control-port", type=int)
    parser.add_argument("--probe", action="store_true")
    parser.add_argument("--mutate", action="store_true")
    arguments = parser.parse_args()
    if arguments.probe and arguments.mutate:
        parser.error("--probe cannot be combined with --mutate")
    return arguments


def encode_message(message: dict[str, Any]) -> bytes:
    return json.dumps(message, separators=(",", ":")).encode("utf-8")


def request(
    arguments: argparse.Namespace,
    method: str,
    body: dict[str, Any] | None = None,
    headers: dict[str, str] | None = None,
) -> tuple[int, dict[str, str], Any]:
    connection = http.client.HTTPConnection(arguments.host, arguments.port, timeout=10)
    request_headers = dict(headers or {})
    request_body = encode_message(body) if body is not None else None
    connection.request(method, MCP_PATH, body=request_body, headers=request_headers)
    response = connection.getresponse()
    response_body = response.read()
    response_headers = {name.lower(): value for name, value in response.getheaders()}
    connection.close()
    if not response_body:
        return response.status, response_headers, None
    try:
        return response.status, response_headers, json.loads(response_body.decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exception:
        raise McpProtocolError("MCP response body is not valid JSON") from exception


def mcp_headers(session_id: str | None = None) -> dict[str, str]:
    headers = {
        HEADER_ACCEPT: f"{MIME_TYPE_JSON}, {MIME_TYPE_EVENT_STREAM}",
        HEADER_CONTENT_TYPE: MIME_TYPE_JSON,
        HEADER_ORIGIN: ORIGIN_LOCAL,
    }
    if session_id is not None:
        headers[HEADER_MCP_PROTOCOL_VERSION] = MCP_PROTOCOL_VERSION
        headers[HEADER_MCP_SESSION_ID] = session_id
    return headers


def require_status(actual_status: int, expected_status: int) -> None:
    if actual_status != expected_status:
        raise McpProtocolError(f"expected HTTP {expected_status}, got HTTP {actual_status}")


def initialize(arguments: argparse.Namespace) -> str:
    message = {
        KEY_ID: 1,
        KEY_METHOD: METHOD_INITIALIZE,
        KEY_PARAMS: {
            KEY_CAPABILITIES: {},
            KEY_CLIENT_INFO: {KEY_NAME: "dikciz-lab-smoke", "version": "1.0.0"},
            KEY_PROTOCOL_VERSION: MCP_PROTOCOL_VERSION,
        },
        "jsonrpc": JSON_RPC_VERSION,
    }
    status, headers, response = request(arguments, HTTP_METHOD_POST, message, mcp_headers())
    require_status(status, HTTP_STATUS_OK)
    if not isinstance(response, dict) or response.get(KEY_ID) != message[KEY_ID]:
        raise McpProtocolError("initialize returned an invalid JSON-RPC response")
    result = response.get(KEY_RESULT)
    if not isinstance(result, dict) or result.get(KEY_PROTOCOL_VERSION) != MCP_PROTOCOL_VERSION:
        raise McpProtocolError("initialize did not negotiate the requested MCP version")
    session_id = headers.get(HEADER_MCP_SESSION_ID.lower())
    if not session_id:
        raise McpProtocolError("initialize did not return an MCP session ID")
    return session_id


def notification_initialized(arguments: argparse.Namespace, session_id: str) -> None:
    message = {KEY_METHOD: METHOD_INITIALIZED, KEY_PARAMS: {}, "jsonrpc": JSON_RPC_VERSION}
    status, _, response = request(arguments, HTTP_METHOD_POST, message, mcp_headers(session_id))
    require_status(status, HTTP_STATUS_ACCEPTED)
    if response is not None:
        raise McpProtocolError("initialized notification unexpectedly returned a body")


def request_message(
    arguments: argparse.Namespace,
    session_id: str,
    request_id: int,
    method: str,
    params: dict[str, Any],
) -> dict[str, Any]:
    request_params = dict(params)
    request_params[KEY_META] = {
        "io.modelcontextprotocol/clientCapabilities": {},
        "io.modelcontextprotocol/protocolVersion": MCP_PROTOCOL_VERSION,
    }
    message = {
        KEY_ID: request_id,
        KEY_METHOD: method,
        KEY_PARAMS: request_params,
        "jsonrpc": JSON_RPC_VERSION,
    }
    status, _, response = request(arguments, HTTP_METHOD_POST, message, mcp_headers(session_id))
    require_status(status, HTTP_STATUS_OK)
    if not isinstance(response, dict) or response.get(KEY_ID) != request_id:
        raise McpProtocolError("MCP response did not preserve the JSON-RPC request ID")
    result = response.get(KEY_RESULT)
    if not isinstance(result, dict):
        raise McpProtocolError(f"MCP request returned an error: {response}")
    return result


def call_tool(
    arguments: argparse.Namespace,
    session_id: str,
    request_id: int,
    tool_name: str,
    tool_arguments: dict[str, Any] | None = None,
) -> dict[str, Any]:
    return request_message(
        arguments,
        session_id,
        request_id,
        METHOD_TOOLS_CALL,
        {KEY_ARGUMENTS: tool_arguments or {}, KEY_NAME: tool_name},
    )


def verify_preinitialized_request_is_rejected(arguments: argparse.Namespace, session_id: str) -> None:
    status, _, _ = request(
        arguments,
        HTTP_METHOD_POST,
        {
            KEY_ID: 2,
            KEY_METHOD: METHOD_TOOLS_LIST,
            KEY_PARAMS: {KEY_META: {"io.modelcontextprotocol/protocolVersion": MCP_PROTOCOL_VERSION}},
            "jsonrpc": JSON_RPC_VERSION,
        },
        mcp_headers(session_id),
    )
    require_status(status, HTTP_STATUS_BAD_REQUEST)


def verify_transport_rejections(arguments: argparse.Namespace) -> None:
    status, _, _ = request(arguments, HTTP_METHOD_GET)
    require_status(status, HTTP_STATUS_METHOD_NOT_ALLOWED)
    message = {
        KEY_ID: 1,
        KEY_METHOD: METHOD_INITIALIZE,
        KEY_PARAMS: {KEY_PROTOCOL_VERSION: MCP_PROTOCOL_VERSION},
        "jsonrpc": JSON_RPC_VERSION,
    }
    headers = mcp_headers()
    headers[HEADER_ORIGIN] = ORIGIN_UNTRUSTED
    status, _, _ = request(arguments, HTTP_METHOD_POST, message, headers)
    require_status(status, HTTP_STATUS_FORBIDDEN)


def require_tool_result(result: dict[str, Any]) -> dict[str, Any]:
    if result.get("resultType") != RESULT_TYPE_COMPLETE or result.get(KEY_IS_ERROR) is True:
        raise McpProtocolError(f"tool call failed: {result}")
    structured_content = result.get(KEY_STRUCTURED_CONTENT)
    if not isinstance(structured_content, dict):
        raise McpProtocolError("tool result has no structured content")
    return structured_content


def require_configuration(result: dict[str, Any]) -> dict[str, Any]:
    configuration = result.get(KEY_CONFIG)
    if not isinstance(configuration, dict):
        raise McpProtocolError("configuration result has no document")
    return configuration


def verify_script_logs(
    arguments: argparse.Namespace,
    session_id: str,
    request_id: int,
) -> None:
    snapshot = require_tool_result(call_tool(arguments, session_id, request_id, TOOL_SCRIPT_LOGS))
    if set(snapshot) != {KEY_SCRIPT_LOG_RECORDS, KEY_SCRIPT_LOGS_TRUNCATED}:
        raise McpProtocolError("script logs returned an unexpected schema")
    records = snapshot.get(KEY_SCRIPT_LOG_RECORDS)
    if not isinstance(records, list) or not isinstance(snapshot.get(KEY_SCRIPT_LOGS_TRUNCATED), bool):
        raise McpProtocolError("script logs returned invalid snapshot metadata")
    for record in records:
        if not isinstance(record, dict) or set(record) != SCRIPT_LOG_RECORD_KEYS:
            raise McpProtocolError("script logs returned an invalid record")
        for key, value in record.items():
            if key in SCRIPT_LOG_REQUIRED_STRING_KEYS and not isinstance(value, str):
                raise McpProtocolError("script logs omitted a required string field")
            if key not in SCRIPT_LOG_REQUIRED_STRING_KEYS and value is not None and not isinstance(value, str):
                raise McpProtocolError("script logs returned an invalid optional field")


def verify_device_access(document: Any) -> None:
    if not isinstance(document, dict) or set(document) != {
        KEY_GRANTED_COUNT,
        KEY_REQUIREMENT_COUNT,
        KEY_REQUIREMENTS,
    }:
        raise McpProtocolError("device access returned an unexpected schema")
    requirements = document.get(KEY_REQUIREMENTS)
    if not isinstance(requirements, list) or len(requirements) != len(DEVICE_ACCESS_REQUIREMENTS):
        raise McpProtocolError("device access returned an invalid requirement inventory")
    requirement_ids = set()
    actionable_count = 0
    granted_count = 0
    for requirement in requirements:
        if not isinstance(requirement, dict) or set(requirement) != DEVICE_ACCESS_ENTRY_KEYS:
            raise McpProtocolError("device access returned an invalid requirement")
        requirement_id = requirement.get(KEY_ID)
        kind = requirement.get(KEY_KIND)
        state = requirement.get(KEY_STATE)
        if not isinstance(requirement_id, str) or kind not in DEVICE_ACCESS_KINDS or state not in DEVICE_ACCESS_STATES:
            raise McpProtocolError("device access returned an invalid requirement value")
        requirement_ids.add(requirement_id)
        if state not in DEVICE_ACCESS_NON_ACTIONABLE_STATES:
            actionable_count += 1
            granted_count += state == "granted"
    if requirement_ids != DEVICE_ACCESS_REQUIREMENTS:
        raise McpProtocolError("device access omitted a supported requirement")
    if document.get(KEY_REQUIREMENT_COUNT) != actionable_count or document.get(KEY_GRANTED_COUNT) != granted_count:
        raise McpProtocolError("device access returned invalid requirement totals")


def verify_automation_status(
    arguments: argparse.Namespace,
    session_id: str,
    request_id: int,
) -> None:
    status = require_tool_result(call_tool(arguments, session_id, request_id, TOOL_AUTOMATION_STATUS))
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
    if set(status) != expected_keys or status.get(KEY_API_VERSION) != AUTOMATION_API_VERSION:
        raise McpProtocolError("automation status returned an unexpected schema")
    if set(status.get(KEY_CAPABILITIES, [])) != AUTOMATION_STATUS_CAPABILITIES:
        raise McpProtocolError("automation status omitted a policy capability")
    if set(status.get(KEY_ACTIONS, [])) != AUTOMATION_STATUS_ACTIONS:
        raise McpProtocolError("automation status omitted an action")
    if set(status.get(KEY_INTENT_TYPES, [])) != AUTOMATION_STATUS_INTENT_TYPES:
        raise McpProtocolError("automation status omitted an intent type")
    events = status.get(KEY_EVENTS)
    event_types = {event.get(KEY_TYPE) for event in events if isinstance(event, dict)} if isinstance(events, list) else set()
    if event_types != AUTOMATION_STATUS_EVENT_TYPES:
        raise McpProtocolError("automation status omitted an event type")
    access = status.get(KEY_ANDROID_ACCESS)
    if not isinstance(access, dict) or set(access) != AUTOMATION_STATUS_ACCESS_KEYS or not all(
        isinstance(value, bool) for value in access.values()
    ):
        raise McpProtocolError("automation status returned invalid Android access state")
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
        raise McpProtocolError("automation status returned an invalid sensor inventory")
    sensor_types = set()
    for sensor in sensors:
        if not isinstance(sensor, dict) or set(sensor) != AUTOMATION_STATUS_SENSOR_KEYS:
            raise McpProtocolError("automation status returned an invalid sensor entry")
        if not isinstance(sensor.get(KEY_TYPE), int) or not isinstance(sensor.get(KEY_WAKE_UP), bool):
            raise McpProtocolError("automation status sensor types are invalid")
        sensor_types.add(sensor[KEY_TYPE])
    if not {EMULATOR_ACCELEROMETER_TYPE, EMULATOR_GYROSCOPE_TYPE}.issubset(sensor_types):
        raise McpProtocolError("automation status omitted emulator sensors used by the live suite")
    if AUTOMATION_STATUS_SENSITIVE_FIELD_NAMES.intersection(status):
        raise McpProtocolError("automation status exposed an unsupported control surface")
    active_media_session_count = status.get(KEY_ACTIVE_MEDIA_SESSION_COUNT)
    if (
        not isinstance(active_media_session_count, int)
        or active_media_session_count < 0
        or status.get(KEY_ACTIVE_MEDIA_SESSION_LIMIT) != MAXIMUM_ACTIVE_MEDIA_SESSION_COUNT
        or status.get(KEY_ACTIVE_MEDIA_SESSIONS_TRUNCATED) is not (
            active_media_session_count > MAXIMUM_ACTIVE_MEDIA_SESSION_COUNT
        )
    ):
        raise McpProtocolError("automation status returned an invalid media-session inventory")


def verify_control_status(
    arguments: argparse.Namespace,
    session_id: str,
    request_id: int,
) -> dict[str, Any]:
    status = require_tool_result(call_tool(arguments, session_id, request_id, TOOL_CONTROL_STATUS))
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
    if set(status) != expected_keys or status.get(KEY_PROTOCOL_VERSION) != AUTOMATION_API_VERSION:
        raise McpProtocolError("control status returned an unexpected schema")
    if status.get(KEY_PLACEMENT_FAILURES) != list(CONTROL_STATUS_PLACEMENT_FAILURES):
        raise McpProtocolError("control status returned invalid placement failures")
    if not isinstance(status.get(KEY_SAFE_MODE), bool):
        raise McpProtocolError("control status returned invalid safe-mode state")
    for key in (KEY_SAFE_MODE_ALLOWED_COMMANDS, KEY_SAFE_MODE_ALLOWED_TAP_IDS):
        value = status.get(key)
        if not isinstance(value, list) or value != sorted(value) or not all(isinstance(item, str) for item in value):
            raise McpProtocolError("control status returned invalid safe-mode metadata")
    transports = status.get(KEY_TRANSPORTS)
    if not isinstance(transports, dict) or set(transports) != CONTROL_STATUS_TRANSPORT_KEYS:
        raise McpProtocolError("control status returned invalid transports")
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
            raise McpProtocolError("control status returned an invalid transport entry")
    limits = status.get(KEY_LIMITS)
    if not isinstance(limits, dict) or set(limits) != CONTROL_STATUS_LIMIT_KEYS:
        raise McpProtocolError("control status returned invalid limits")
    for range_key in ("scrollDelta", "waitTimeoutMilliseconds"):
        value = limits.get(range_key)
        if (
            not isinstance(value, dict)
            or set(value) != {KEY_MINIMUM, KEY_MAXIMUM}
            or not isinstance(value.get(KEY_MINIMUM), int)
            or not isinstance(value.get(KEY_MAXIMUM), int)
            or value[KEY_MINIMUM] > value[KEY_MAXIMUM]
        ):
            raise McpProtocolError("control status returned an invalid range")
    tools_result = request_message(arguments, session_id, request_id + 1, METHOD_TOOLS_LIST, {})
    tools = tools_result.get(KEY_TOOLS)
    if not isinstance(tools, list):
        raise McpProtocolError("tools/list returned no tool array")
    tool_names = {tool.get(KEY_NAME) for tool in tools if isinstance(tool, dict)}
    commands = status.get(KEY_COMMANDS)
    if not isinstance(commands, list) or not commands:
        raise McpProtocolError("control status returned an invalid command inventory")
    command_types = []
    for command in commands:
        if not isinstance(command, dict) or set(command) != {KEY_FIELDS, KEY_MCP_TOOL, KEY_TYPE, KEY_SIDE_EFFECT}:
            raise McpProtocolError("control status returned an invalid command entry")
        fields = command.get(KEY_FIELDS)
        if not isinstance(fields, list) or fields != sorted(fields) or not all(isinstance(value, str) for value in fields):
            raise McpProtocolError("control status returned invalid command fields")
        tool_name = command.get(KEY_MCP_TOOL)
        if not isinstance(tool_name, str) or tool_name not in tool_names:
            raise McpProtocolError("control status returned an invalid MCP mapping")
        if command.get(KEY_SIDE_EFFECT) not in CONTROL_STATUS_SIDE_EFFECTS:
            raise McpProtocolError("control status returned invalid side-effect metadata")
        command_type = command.get(KEY_TYPE)
        if not isinstance(command_type, str):
            raise McpProtocolError("control status returned an invalid command type")
        command_types.append(command_type)
    if command_types != sorted(command_types) or len(command_types) != len(set(command_types)):
        raise McpProtocolError("control status command inventory is not type-sorted")
    events = status.get(KEY_EVENTS)
    if not isinstance(events, list) or len(events) != len(CONTROL_STATUS_EVENT_FIELDS):
        raise McpProtocolError("control status returned an invalid event inventory")
    event_types = []
    for event in events:
        if not isinstance(event, dict) or set(event) != {KEY_TYPE, KEY_REQUIRED_FIELDS, KEY_OPTIONAL_FIELDS}:
            raise McpProtocolError("control status returned an invalid event entry")
        event_type = event.get(KEY_TYPE)
        expected_fields = CONTROL_STATUS_EVENT_FIELDS.get(event_type)
        if expected_fields is None:
            raise McpProtocolError("control status returned an unknown event")
        if event.get(KEY_REQUIRED_FIELDS) != list(expected_fields[0]):
            raise McpProtocolError("control status returned invalid event required fields")
        if event.get(KEY_OPTIONAL_FIELDS) != list(expected_fields[1]):
            raise McpProtocolError("control status returned invalid event optional fields")
        event_types.append(event_type)
    if event_types != sorted(CONTROL_STATUS_EVENT_FIELDS):
        raise McpProtocolError("control status event inventory is not type-sorted")
    return status


def verify_tools(arguments: argparse.Namespace, session_id: str) -> None:
    expected_control_port = getattr(arguments, "expected_control_port", None) or arguments.port
    tools_result = request_message(arguments, session_id, 3, METHOD_TOOLS_LIST, {})
    tools = tools_result.get(KEY_TOOLS)
    if not isinstance(tools, list):
        raise McpProtocolError("tools/list returned no tool array")
    tool_names = {tool.get(KEY_NAME) for tool in tools if isinstance(tool, dict)}
    required_tools = {
        TOOL_ACCESSIBILITY_ACTION,
        TOOL_ACCESSIBILITY_SNAPSHOT,
        TOOL_CONFIG_GET,
        TOOL_CONFIG_REPLACE,
        TOOL_CONFIG_RESET,
        TOOL_CONFIG_SEED,
        TOOL_AUTOMATION_DISPATCH,
        TOOL_AUTOMATION_SERVICE_SYNC,
        TOOL_AUTOMATION_STATUS,
        TOOL_AUTOMATION_TRIGGER,
        TOOL_CONTROL_STATUS,
        TOOL_DIAGNOSTICS,
        TOOL_FIND,
        TOOL_INTENT,
        TOOL_SCREENSHOT,
        TOOL_SHELL,
        TOOL_SCRIPT_LOGS,
        TOOL_SNAPSHOT,
        TOOL_WAIT_FOR,
    }
    if not required_tools.issubset(tool_names):
        raise McpProtocolError("tools/list omitted a required Dikciz control tool")

    snapshot = require_tool_result(call_tool(arguments, session_id, 4, TOOL_SNAPSHOT))
    if snapshot.get(KEY_SCREEN) != "home":
        raise McpProtocolError("snapshot is not the Dikciz home screen")

    found = require_tool_result(
        call_tool(arguments, session_id, 5, TOOL_FIND, {"semanticId": "pageScroll"}),
    )
    if found.get("found") is not True:
        raise McpProtocolError("find did not resolve the page scroll node")

    waited = require_tool_result(
        call_tool(
            arguments,
            session_id,
            6,
            TOOL_WAIT_FOR,
            {"semanticId": "pageScroll", KEY_TIMEOUT_MILLISECONDS: 500},
        ),
    )
    if waited.get(KEY_FOUND) is not True:
        raise McpProtocolError("wait tool did not resolve the page scroll node")

    absent = require_tool_result(
        call_tool(arguments, session_id, 7, TOOL_FIND, {"semanticId": "widget:absent"}),
    )
    if absent.get("found") is not False:
        raise McpProtocolError("find unexpectedly resolved an absent semantic ID")

    screenshot = call_tool(arguments, session_id, 8, TOOL_SCREENSHOT)
    require_tool_result(screenshot)
    content = screenshot.get(KEY_CONTENT)
    if not isinstance(content, list):
        raise McpProtocolError("screenshot did not return MCP content")
    image = next((item for item in content if isinstance(item, dict) and item.get(KEY_TYPE) == "image"), None)
    if not isinstance(image, dict) or image.get(KEY_MIME_TYPE) != MIME_TYPE_PNG:
        raise McpProtocolError("screenshot did not return PNG MCP image content")
    try:
        png = base64.b64decode(image.get(KEY_DATA, ""), validate=True)
    except (TypeError, ValueError) as exception:
        raise McpProtocolError("screenshot image content is not base64") from exception
    if not png.startswith(PNG_SIGNATURE):
        raise McpProtocolError("screenshot image content is not a PNG")

    diagnostics = require_tool_result(call_tool(arguments, session_id, 9, TOOL_DIAGNOSTICS))
    if diagnostics.get("mcpControlPort") != expected_control_port:
        raise McpProtocolError("diagnostics returned the wrong MCP control port")

    verify_automation_status(arguments, session_id, 10)
    verify_control_status(arguments, session_id, 15)
    verify_script_logs(arguments, session_id, 17)

    automation_service_sync = require_tool_result(
        call_tool(arguments, session_id, 11, TOOL_AUTOMATION_SERVICE_SYNC),
    )
    if automation_service_sync.get(KEY_AUTOMATION_SERVICE_SYNCED) is not True:
        raise McpProtocolError("automation-service sync was not acknowledged")

    configuration = require_configuration(
        require_tool_result(call_tool(arguments, session_id, 12, TOOL_CONFIG_GET)),
    )
    if not isinstance(configuration.get(KEY_LAUNCHER), dict):
        raise McpProtocolError("configuration result has no launcher namespace")

    shell = require_tool_result(
        call_tool(arguments, session_id, 13, TOOL_SHELL, {KEY_COMMAND: SHELL_COMMAND_CURRENT_UID, KEY_ROOT: False}),
    )
    if shell.get(KEY_EXECUTION_SCOPE) != VALUE_APP_SANDBOX:
        raise McpProtocolError("shell did not report app sandbox execution")
    if not isinstance(shell.get(KEY_EXIT_CODE), int) or shell.get(KEY_EXIT_CODE) != 0:
        raise McpProtocolError("shell command did not exit successfully")
    if not str(shell.get(KEY_OUTPUT, "")).strip().isdigit():
        raise McpProtocolError("shell command did not return the Android app UID")

    intent = require_tool_result(
        call_tool(
            arguments,
            session_id,
            14,
            TOOL_INTENT,
            {KEY_ACTION: VALUE_SMOKE_BROADCAST_ACTION, KEY_INTENT_TYPE: VALUE_INTENT_BROADCAST},
        ),
    )
    if intent.get(KEY_DISPATCHED) is not True or intent.get(KEY_INTENT_TYPE) != VALUE_INTENT_BROADCAST:
        raise McpProtocolError("broadcast intent was not dispatched")


def verify_mutations(arguments: argparse.Namespace, session_id: str) -> None:
    original = require_configuration(
        require_tool_result(call_tool(arguments, session_id, 13, TOOL_CONFIG_GET)),
    )
    try:
        modified = copy.deepcopy(original)
        home = modified.get(KEY_LAUNCHER, {}).get(KEY_HOME, {})
        pages = home.get(KEY_PAGES)
        if not isinstance(pages, list) or len(pages) < 2:
            raise McpProtocolError("configuration needs two pages for replacement proof")
        alternate_page_id = pages[1].get(KEY_ID)
        if not isinstance(alternate_page_id, str):
            raise McpProtocolError("configuration alternate page has no ID")
        home["selectedPageId"] = alternate_page_id
        replaced = require_tool_result(
            call_tool(arguments, session_id, 14, TOOL_CONFIG_SEED, {KEY_CONFIG: modified}),
        )
        replacement_snapshot = replaced.get(KEY_SNAPSHOT)
        if not isinstance(replacement_snapshot, dict) or replacement_snapshot.get("selectedPageId") != alternate_page_id:
            raise McpProtocolError("configuration seed did not reload the selected page")

        reset = require_tool_result(call_tool(arguments, session_id, 15, TOOL_CONFIG_RESET))
        reset_snapshot = reset.get(KEY_SNAPSHOT)
        if not isinstance(reset_snapshot, dict) or reset_snapshot.get(KEY_SCREEN) != "home":
            raise McpProtocolError("configuration reset did not reload the bundled home")

        require_tool_result(
            call_tool(arguments, session_id, 16, TOOL_SELECT_PAGE, {"pageId": "home"}),
        )
        before = require_tool_result(call_tool(arguments, session_id, 17, TOOL_SNAPSHOT))
        nodes = before.get("nodes")
        if not isinstance(nodes, list):
            raise McpProtocolError("snapshot has no nodes before mutation")
        focus = next(
            (
                node
                for node in nodes
                if isinstance(node, dict) and node.get("semanticId") == "widget:focus:toggle"
            ),
            None,
        )
        if not isinstance(focus, dict) or not isinstance(focus.get("checked"), bool):
            raise McpProtocolError("focus toggle is unavailable for mutation proof")
        require_tool_result(
            call_tool(arguments, session_id, 18, TOOL_TAP, {"semanticId": "widget:focus:toggle"}),
        )
        after = require_tool_result(call_tool(arguments, session_id, 19, TOOL_SNAPSHOT))
        after_nodes = after.get("nodes")
        after_focus = next(
            (
                node
                for node in after_nodes
                if isinstance(node, dict) and node.get("semanticId") == "widget:focus:toggle"
            ),
            None,
        )
        if not isinstance(after_focus, dict) or after_focus.get("checked") == focus.get("checked"):
            raise McpProtocolError("tap did not change the focus toggle")
        text = "Dikciz MCP smoke test"
        result = require_tool_result(
            call_tool(arguments, session_id, 20, TOOL_SET_TEXT, {"semanticId": "widget:welcome", "text": text}),
        )
        if result.get("text") != text:
            raise McpProtocolError("set text did not return the saved value")
    finally:
        restored = require_tool_result(
            call_tool(arguments, session_id, 21, TOOL_CONFIG_REPLACE, {KEY_CONFIG: original}),
        )
        if require_configuration(restored) != original:
            raise McpProtocolError("configuration restoration did not preserve the original document")


def delete_session(arguments: argparse.Namespace, session_id: str) -> None:
    status, _, response = request(arguments, HTTP_METHOD_DELETE, headers={HEADER_MCP_SESSION_ID: session_id})
    require_status(status, HTTP_STATUS_NO_CONTENT)
    if response is not None:
        raise McpProtocolError("session deletion unexpectedly returned a body")
    status, _, _ = request(
        arguments,
        HTTP_METHOD_POST,
        {KEY_ID: 20, KEY_METHOD: METHOD_TOOLS_LIST, KEY_PARAMS: {}, "jsonrpc": JSON_RPC_VERSION},
        mcp_headers(session_id),
    )
    require_status(status, HTTP_STATUS_NOT_FOUND)


def run_smoke_test(arguments: argparse.Namespace) -> dict[str, Any]:
    verify_transport_rejections(arguments)
    session_id = initialize(arguments)
    try:
        verify_preinitialized_request_is_rejected(arguments, session_id)
        notification_initialized(arguments, session_id)
        verify_tools(arguments, session_id)
        if arguments.mutate:
            verify_mutations(arguments, session_id)
    finally:
        delete_session(arguments, session_id)
    return {"mcpControlPort": arguments.port, "mutations": arguments.mutate, "status": "passed"}


def run_probe(arguments: argparse.Namespace) -> dict[str, Any]:
    session_id = initialize(arguments)
    delete_session(arguments, session_id)
    return {"mcpControlPort": arguments.port, "status": "ready"}


def main() -> None:
    arguments = parse_arguments()
    result = run_probe(arguments) if arguments.probe else run_smoke_test(arguments)
    print(json.dumps(result, sort_keys=True))


if __name__ == "__main__":
    main()
