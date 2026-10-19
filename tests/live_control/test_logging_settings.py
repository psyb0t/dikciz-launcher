"""Native logging controls and quiet app launches with inspectable outcomes."""

from . import *
from .test_app_drawer import (
    filter_drawer_to_settings,
    open_drawer_through_control,
    settings_row_semantic_id,
)
from .test_remote_bearer_auth import connected_mcp, mcp_tool


SETTINGS_LOGGING = "settings:logging"
LOGGING_LEVEL_PREFIX = "logging:level:"
LOGGING_RETENTION = "logging:retention-days"
LOGGING_SAVE = "logging:save"
LOGGING_CANCEL = "logging:cancel"
LOGGING_LEVELS = ("debug", "info", "warn", "error")
VALID_RETENTION_DAYS = (1, 7, 90)
INVALID_RETENTION_VALUES = ("garbage", "0", "91", "2147483648")
DEFAULT_RETENTION_DAYS = 7
DEFAULT_LOG_LEVEL = "info"
APP_ACTION_COMPLETED = "app_action_completed"
APP_ACTION_LAUNCHED = "launched"
LOGGING_SCREENSHOT = "logging-settings.png"
TOAST_QUEUE_COMMAND = "dumpsys notification --noredact"


def read_logging(connection: socket.socket) -> dict[str, Any]:
    return AUTOMATION.request(connection, AUTOMATION.TYPE_CONFIG_GET)["config"]["logging"]


def open_logging(connection: socket.socket, from_home: bool = False) -> None:
    if from_home:
        AUTOMATION.request(connection, AUTOMATION.TYPE_TAP, semanticId="launcher:controls")
        wait_for_snapshot_node(connection, "launcher:controls:dikciz-settings")
        AUTOMATION.request(
            connection,
            AUTOMATION.TYPE_TAP,
            semanticId="launcher:controls:dikciz-settings",
        )
    wait_for_snapshot_node(connection, SETTINGS_LOGGING)
    AUTOMATION.request(connection, AUTOMATION.TYPE_TAP, semanticId=SETTINGS_LOGGING)
    wait_for_snapshot_node(connection, LOGGING_SAVE)


def test_logging_settings_levels_retention_validation_and_cancel(
    websocket_control: socket.socket,
) -> None:
    configuration = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_RESET),
    )
    configuration.pop("logging", None)
    AUTOMATION.request(
        websocket_control, AUTOMATION.TYPE_CONFIG_REPLACE, config=configuration,
    )
    initial = read_logging(websocket_control)
    assert initial["level"] == DEFAULT_LOG_LEVEL
    assert initial["retentionDays"] == DEFAULT_RETENTION_DAYS
    invalid = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    invalid["logging"]["retentionDays"] = -1
    AUTOMATION.request_error_with_id(
        websocket_control, AUTOMATION.TYPE_CONFIG_REPLACE, config=invalid,
    )
    assert read_logging(websocket_control) == initial
    open_logging(websocket_control, from_home=True)
    run_device_operation("screenshot")
    shutil.copyfile(ARTIFACT_DIRECTORY / "screenshot.png", ARTIFACT_DIRECTORY / LOGGING_SCREENSHOT)
    for value in INVALID_RETENTION_VALUES:
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_SET_TEXT,
            semanticId=LOGGING_RETENTION,
            text=value,
        )
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_TAP, semanticId=LOGGING_SAVE)
        assert read_logging(websocket_control) == initial
        wait_for_snapshot_node(websocket_control, LOGGING_SAVE)
    for level in LOGGING_LEVELS:
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_TAP,
            semanticId=LOGGING_LEVEL_PREFIX + level,
        )
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_SET_TEXT,
            semanticId=LOGGING_RETENTION,
            text=str(DEFAULT_RETENTION_DAYS),
        )
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_TAP, semanticId=LOGGING_SAVE)
        assert read_logging(websocket_control) == dict(initial, level=level)
        open_logging(websocket_control)
    for days in VALID_RETENTION_DAYS:
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_SET_TEXT,
            semanticId=LOGGING_RETENTION,
            text=str(days),
        )
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_TAP, semanticId=LOGGING_SAVE)
        assert read_logging(websocket_control)["retentionDays"] == days
        open_logging(websocket_control)
    saved = read_logging(websocket_control)
    AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_TAP,
        semanticId=LOGGING_LEVEL_PREFIX + DEFAULT_LOG_LEVEL,
    )
    AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_SET_TEXT,
        semanticId=LOGGING_RETENTION,
        text=str(DEFAULT_RETENTION_DAYS),
    )
    AUTOMATION.request(websocket_control, AUTOMATION.TYPE_TAP, semanticId=LOGGING_CANCEL)
    assert read_logging(websocket_control) == saved


def test_logging_launch_is_quiet_and_keeps_app_target_in_both_protocols(
    websocket_control: socket.socket,
) -> None:
    open_drawer_through_control()
    filter_drawer_to_settings()
    wait_for_snapshot_node(websocket_control, settings_row_semantic_id())
    AUTOMATION.request(websocket_control, AUTOMATION.TYPE_TAP, semanticId=settings_row_semantic_id())
    queue = device_command(TOAST_QUEUE_COMMAND)
    toast_section = queue.partition("Toast Queue:")[2].split("\n\n", 1)[0]
    assert DIKCIZ_DEBUG_PACKAGE not in toast_section
    wait_for_foreground_application(ANDROID_SETTINGS_PACKAGE)
    logs = AUTOMATION.request(websocket_control, "scriptLogs")
    records = [record for record in logs["records"] if record["event"] == APP_ACTION_COMPLETED]
    assert any(
        record.get("componentPackage") == ANDROID_SETTINGS_PACKAGE
        and record.get("actionType") == APP_ACTION_TYPE_LAUNCH
        and record["outcome"] == APP_ACTION_LAUNCHED
        for record in records
    )
    with connected_mcp() as (arguments, session_id):
        remote = mcp_tool(arguments, session_id, 3, "dikciz_script_logs")
        assert any(
            record.get("componentPackage") == ANDROID_SETTINGS_PACKAGE
            and record["outcome"] == APP_ACTION_LAUNCHED
            for record in remote["records"]
        )
    restore_system_home()
    wait_for_snapshot_node(websocket_control, APP_DRAWER_SEARCH_SEMANTIC_ID, should_exist=False)
