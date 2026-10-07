"""Redacted audit-log contract coverage."""

from . import *


def raise_log_level_to_debug(connection: socket.socket) -> None:
    configured = current_configuration(connection)
    configured[AUDIT_LOGGING_SECTION][AUDIT_LOGGING_LEVEL_KEY] = AUDIT_DEBUG_LOG_LEVEL
    AUTOMATION.require_configuration(
        AUTOMATION.request(connection, AUTOMATION.TYPE_CONFIG_REPLACE, config=configured),
    )


def provoke_configuration_file_change(connection: socket.socket) -> None:
    """Edit the public configuration from outside and wait for its record.

    This is the documented promise that a change written to /sdcard/Dikciz
    reloads by itself, so the test makes the edit the way an owner would, with
    a file push rather than a launcher command.

    Two things have to be arranged rather than assumed. The record is written
    at debug level and the bundled configuration logs at info, so the level is
    raised first through the control plane, which applies it immediately.
    And reading the day's log hoping an earlier test happened to change a file
    would leave this test depending on what ran before it.
    """
    raise_log_level_to_debug(connection)
    run_device_operation(
        "file-pull",
        DEVICE_FILE=DEVICE_CONFIGURATION_PATH,
        ARTIFACT_FILE=AUDIT_CONFIGURATION_ARTIFACT_NAME,
    )
    run_device_operation(
        "file-push",
        DEVICE_FILE=DEVICE_CONFIGURATION_PATH,
        ARTIFACT_FILE=AUDIT_CONFIGURATION_ARTIFACT_NAME,
    )
    deadline = time.monotonic() + CONFIGURATION_SAVE_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        if any(
            record.get("event") == CONFIGURATION_FILE_CHANGED_EVENT
            for record in device_log_records()
        ):
            return
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError(
        f"no {CONFIGURATION_FILE_CHANGED_EVENT} record after an external configuration edit",
    )


def test_debug_log_records_every_tested_control_surface_without_content_leaks(
    websocket_control: socket.socket,
) -> None:
    provoke_configuration_file_change(websocket_control)
    run_device_operation(
        "file-pull",
        DEVICE_FILE=current_device_log_path(),
        ARTIFACT_FILE=EVENT_LOG_ARTIFACT_NAME,
    )
    log_content = (ARTIFACT_DIRECTORY / EVENT_LOG_ARTIFACT_NAME).read_text(encoding="utf-8")
    records = [json.loads(line) for line in log_content.splitlines() if line]
    assert records
    assert all(LOG_RECORD_REQUIRED_FIELDS <= record.keys() for record in records)
    events = {record["event"] for record in records}
    required_events = (
        LOG_FOCUSED_REQUIRED_EVENTS
        if os.environ.get("DIKCIZ_TEST_SELECTOR")
        else LOG_FULL_SUITE_REQUIRED_EVENTS
    )
    assert required_events <= events
    for forbidden_value in LOG_FORBIDDEN_VALUES:
        assert forbidden_value not in log_content
