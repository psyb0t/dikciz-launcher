"""Measure real notification cadence from an explicit opt-in clock fixture."""

import json
import re
import socket
import time

from . import (
    ARTIFACT_DIRECTORY,
    AUTOMATION,
    AUTOMATION_NOTIFICATION_COMPONENT,
    DIKCIZ_DEBUG_PACKAGE,
    device_command,
    device_log_records,
    run_device_operation,
    websocket_control,
)


CLOCK_TITLE = "Dikciz clock"
CLOCK_SCRIPT_ID = "hourly-clock"
CLOCK_STATUS_PATH = "/sdcard/Dikciz/scripts/hourly-clock/run-status.json"
DELIVERY_COUNT = 4
DELIVERY_TIMEOUT_SECONDS = 75
POLL_SECONDS = 2
MINIMUM_GAP_MS = 59_000
MAXIMUM_GAP_MS = 70_000
NOTIFICATION_RECORD_PATTERN = r"(?m)^    NotificationRecord\("
NOTIFICATION_TIME_PATTERN = r"(?m)^\s+when=(\d+)/"
NOTIFICATION_TEXT_PATTERN = r"android.text=String \(([^\n]*)\)"
CADENCE_ARTIFACT = "minute-notification-cadence.json"


def clock_notification() -> dict | None:
    dump = device_command("dumpsys notification --noredact")
    active = dump.split("  Notification List:", 1)[1].split("  Enqueued", 1)[0]
    matches = [
        record for record in re.split(NOTIFICATION_RECORD_PATTERN, active)[1:]
        if f"pkg={DIKCIZ_DEBUG_PACKAGE} " in record.splitlines()[0]
        and f"android.title=String ({CLOCK_TITLE})" in record
    ]
    if not matches:
        return None
    assert len(matches) == 1, "Clock created duplicate active notifications"
    record = matches[0]
    timestamp = re.search(NOTIFICATION_TIME_PATTERN, record)
    body = re.search(NOTIFICATION_TEXT_PATTERN, record)
    assert timestamp is not None and body is not None, record
    return {"when": int(timestamp[1]), "body": body[1]}


def test_explicit_clock_posts_every_minute_while_awake(websocket_control: socket.socket) -> None:
    device_command("input keyevent KEYCODE_WAKEUP")
    device_command("wm dismiss-keyguard")
    device_command("cmd statusbar collapse")
    configured = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_RESET),
    )
    configured["scripts"] = [{
        "id": CLOCK_SCRIPT_ID, "title": "Minute clock fixture", "enabled": True,
        "apiVersion": 1, "state": {},
        "source": 'local function two_digits(value)\n  if value < 10 then return "0" .. tostring(value) end\n  return tostring(value)\nend\n\nfunction on_event(event)\n  local clock = two_digits(event.time.hour24) .. ":" .. two_digits(event.time.minute)\n  return {\n    status = "Notification requested for " .. clock,\n    actions = {\n      { type = "postNotification", title = "Dikciz clock", text = "Current time: " .. clock }\n    }\n  }\nend',
    }]
    configured["automation"] = {
        "apiVersion": 1,
        "policies": [{"id": CLOCK_SCRIPT_ID, "title": "Minute fixture", "enabled": True,
                      "capabilities": ["alarm"], "actions": ["postNotification"]}],
        "scripts": [{"scriptId": CLOCK_SCRIPT_ID, "policyId": CLOCK_SCRIPT_ID,
                     "enabled": True, "subscriptions": [{
                         "event": "alarm", "minimumIntervalMilliseconds": 60000,
                         "alarmIntervalMilliseconds": 60000,
                     }]}],
    }
    AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_REPLACE, config=configured)
    device_command(f"pm grant {DIKCIZ_DEBUG_PACKAGE} android.permission.POST_NOTIFICATIONS")
    device_command(f"cmd notification allow_listener {AUTOMATION_NOTIFICATION_COMPONENT}")
    previous = clock_notification()
    previous_when = previous["when"] if previous else 0
    evidence = {"deliveries": [], "gapsMs": []}
    artifact = ARTIFACT_DIRECTORY / CADENCE_ARTIFACT
    # The day's log also holds alarms from earlier tests in the same run.
    record_count_before_deliveries = len(device_log_records())
    try:
        for index in range(DELIVERY_COUNT):
            deadline = time.monotonic() + DELIVERY_TIMEOUT_SECONDS
            while True:
                notification = clock_notification()
                if notification and notification["when"] > previous_when:
                    break
                assert time.monotonic() < deadline, evidence
                time.sleep(POLL_SECONDS)
            status = json.loads(device_command(f"cat {CLOCK_STATUS_PATH}"))
            assert status["outcome"] == "completed", status
            expected_time = status["status"].removeprefix("Notification requested for ")
            assert notification["body"] == f"Current time: {expected_time}", notification
            evidence["deliveries"].append({**notification, "scriptStatus": status})
            if index:
                gap = notification["when"] - previous_when
                evidence["gapsMs"].append(gap)
                assert MINIMUM_GAP_MS <= gap <= MAXIMUM_GAP_MS, evidence
            previous_when = notification["when"]
            artifact.write_text(json.dumps(evidence, indent=2), encoding="utf-8")
            if index == 1:
                device_command("am start -a android.settings.SETTINGS")

        records = device_log_records()[record_count_before_deliveries:]
        evidence["timerEvents"] = [
            record for record in records
            if record.get("event") == "automation_alarm_delivered"
        ]
        evidence["clockActions"] = [
            record for record in records
            if record.get("script_id") == CLOCK_SCRIPT_ID
            and record.get("action_type") == "postNotification"
            and record.get("event_type") == "alarm"
        ]
        assert len(evidence["clockActions"]) == DELIVERY_COUNT, evidence
        assert all(record["outcome"] == "executed" for record in evidence["clockActions"])
        assert len(evidence["timerEvents"]) == DELIVERY_COUNT, evidence
        assert all(record["source"] == "foreground_timer" for record in evidence["timerEvents"])
    finally:
        artifact.write_text(json.dumps(evidence, indent=2), encoding="utf-8")
        run_device_operation("home-start", PACKAGE_NAME=DIKCIZ_DEBUG_PACKAGE)
        run_device_operation("screenshot")
