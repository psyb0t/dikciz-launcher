"""Revoked access and stale wakeups must not retain active script effects."""

import json
import time
import uuid

import pytest

from . import (
    ARTIFACT_DIRECTORY, AUTOMATION, AUTOMATION_ACTION_ALARM_EVENT,
    DIKCIZ_DEBUG_PACKAGE, connected_websocket_control, device_command,
    run_device_operation, wait_for_restarted_websocket_control,
)
from .alarm_lifecycle_support import (
    SETTLE_SECONDS, clock_when, configuration,
    pending_alarm_records, replace, wait_delivery,
    receiver_records,
)
from .test_alarm_lifecycle import ALARM_RECEIVER
from .test_alarm_adversarial import (
    INVALID_VALUES, reset_without_counter_fixtures, validate_interval,
)
from .notification_permission import ensure_notification_posting_permission
from .script_status import wait_script_result


CLOCK_SCRIPT_ID = "hourly-clock"
NOTIFICATION_PERMISSION = "android.permission.POST_NOTIFICATIONS"
DISABLED_WAIT_SECONDS = 65


def stale_wakeup(connection, evidence, disable_source):
    document = configuration(connection)
    if disable_source:
        for script in document["scripts"]:
            script["enabled"] = False
    else:
        for script in document["automation"]["scripts"]:
            script["enabled"] = False
    replace(connection, document)
    time.sleep(SETTLE_SECONDS)
    evidence["pendingBeforeBroadcast"] = pending_alarm_records()
    previous = clock_when()
    receiver_count = len(receiver_records())
    probe = f"dikciz-test://alarm/{uuid.uuid4()}"
    device_command(
        f"run-as {DIKCIZ_DEBUG_PACKAGE} am broadcast --user 0 "
        f"-n {ALARM_RECEIVER} -a {AUTOMATION_ACTION_ALARM_EVENT} -d {probe}",
    )
    deadline = time.monotonic() + SETTLE_SECONDS
    while len(receiver_records()) == receiver_count and time.monotonic() < deadline:
        time.sleep(SETTLE_SECONDS)
    evidence["receiverRecords"] = receiver_records()[receiver_count:]
    assert len(evidence["receiverRecords"]) == 1, evidence
    time.sleep(DISABLED_WAIT_SECONDS)
    evidence["pendingAfterBroadcast"] = pending_alarm_records()
    evidence["clockBefore"] = previous
    evidence["clockAfter"] = clock_when()
    assert not evidence["pendingBeforeBroadcast"], evidence
    assert not evidence["pendingAfterBroadcast"], evidence
    assert evidence["clockAfter"] == previous, evidence


def revoke_posting(connection, evidence):
    device_command(f"pm grant {DIKCIZ_DEBUG_PACKAGE} {NOTIFICATION_PERMISSION}")
    previous = clock_when()
    AUTOMATION.request(connection, AUTOMATION.TYPE_AUTOMATION_TRIGGER, scriptId=CLOCK_SCRIPT_ID)
    wait_delivery(previous, evidence)
    connection.close()
    device_command(f"pm revoke {DIKCIZ_DEBUG_PACKAGE} {NOTIFICATION_PERMISSION}")
    run_device_operation("home-start", PACKAGE_NAME=DIKCIZ_DEBUG_PACKAGE)
    current = wait_for_restarted_websocket_control()
    try:
        previous = clock_when()
        AUTOMATION.request(current, AUTOMATION.TYPE_AUTOMATION_TRIGGER, scriptId=CLOCK_SCRIPT_ID)
        evidence["revokedRun"] = wait_script_result(CLOCK_SCRIPT_ID, "rejected")
        assert clock_when() == previous, "Revoked permission still posted a notification"
        device_command(f"pm grant {DIKCIZ_DEBUG_PACKAGE} {NOTIFICATION_PERMISSION}")
        AUTOMATION.request(current, AUTOMATION.TYPE_AUTOMATION_TRIGGER, scriptId=CLOCK_SCRIPT_ID)
        evidence["recoveredRun"] = wait_script_result(CLOCK_SCRIPT_ID, "completed")
        wait_delivery(previous, evidence)
    finally:
        current.close()


@pytest.mark.parametrize(
    "case", [*INVALID_VALUES, "maximum", "automation_disabled", "permission_revoked"],
)
def test_alarm_revocation_and_stale_wakeups(case):
    run_alarm_revocation_case(case)


def test_alarm_stale_wakeup_after_script_disable():
    run_alarm_revocation_case("script_disabled")


def run_alarm_revocation_case(case):
    evidence = {"scenario": case, "result": "failed"}
    connection = None
    try:
        connection = wait_for_restarted_websocket_control()
        reset_without_counter_fixtures(connection)
        if case == "permission_revoked":
            revoke_posting(connection, evidence)
        elif case in {"script_disabled", "automation_disabled"}:
            stale_wakeup(connection, evidence, case == "script_disabled")
        else:
            validate_interval(connection, evidence, case)
        evidence["result"] = "passed"
    finally:
        if connection is not None:
            connection.close()
        artifact = ARTIFACT_DIRECTORY / f"alarm-revocation-{case}.json"
        artifact.write_text(json.dumps(evidence, indent=2), encoding="utf-8")
        run_device_operation("home-start", PACKAGE_NAME=DIKCIZ_DEBUG_PACKAGE)
        with connected_websocket_control() as cleanup:
            ensure_notification_posting_permission(cleanup)
            reset_without_counter_fixtures(cleanup)
