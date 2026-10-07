"""Shared observations for real scheduler lifecycle tests."""

import json
import re
import time

from . import (
    ARTIFACT_DIRECTORY, AUTOMATION, AUTOMATION_ACTION_ALARM_EVENT,
    DIKCIZ_DEBUG_PACKAGE, device_command, device_log_records,
)
from .test_alarm_cadence import clock_notification


ALARM_EVENT = "alarm"
TIMER_LOG_EVENT = "automation_alarm_delivered"
RECEIVER_LOG_EVENT = "automation_alarm_receiver_received"
POLL_SECONDS = 2
SETTLE_SECONDS = 2
DELIVERY_TIMEOUT_SECONDS = 80
DEFAULT_INTERVAL_MS = 60_000
LONG_INTERVAL_MS = 120_000
POWER_ASLEEP_STATE = "mWakefulness=Asleep"
ASLEEP_TIMEOUT_SECONDS = 30


def configuration(connection):
    return AUTOMATION.require_configuration(
        AUTOMATION.request(connection, AUTOMATION.TYPE_CONFIG_GET),
    )


def replace(connection, document):
    return AUTOMATION.require_configuration(
        AUTOMATION.request(connection, AUTOMATION.TYPE_CONFIG_REPLACE, config=document),
    )


def set_alarm_interval(document, interval):
    for script in document["automation"]["scripts"]:
        for subscription in script["subscriptions"]:
            if subscription["event"] == ALARM_EVENT:
                subscription["alarmIntervalMilliseconds"] = interval


def clock_when():
    notification = clock_notification()
    return notification["when"] if notification else 0


def timer_records():
    return [record for record in device_log_records() if record.get("event") == TIMER_LOG_EVENT]


def receiver_records():
    return [record for record in device_log_records() if record.get("event") == RECEIVER_LOG_EVENT]


def save_evidence(evidence, stage):
    evidence["stage"] = stage
    path = ARTIFACT_DIRECTORY / f"alarm-lifecycle-{evidence['scenario']}.json"
    path.write_text(json.dumps(evidence, indent=2), encoding="utf-8")


def wait_delivery(previous, evidence, timeout=DELIVERY_TIMEOUT_SECONDS):
    started = time.monotonic()
    while time.monotonic() - started < timeout:
        notification = clock_notification()
        if notification and notification["when"] > previous:
            observation = {**notification, "waitSeconds": time.monotonic() - started}
            evidence.setdefault("deliveries", []).append(observation)
            save_evidence(evidence, "notification_received")
            return observation
        time.sleep(POLL_SECONDS)
    raise AssertionError(f"No new Android clock notification within {timeout}s: {evidence}")


def observe_silence(seconds, evidence):
    previous = clock_when()
    baseline = len(timer_records())
    save_evidence(evidence, "observing_disabled_timer")
    time.sleep(seconds)
    events = timer_records()[baseline:]
    evidence["disabledTimerEvents"] = events
    assert not events, evidence
    assert clock_when() == previous, "Disabled timer still changed the Android notification"


def power_observation():
    power = device_command("dumpsys power")
    alarm = device_command("dumpsys alarm")
    return {
        "power": [line.strip() for line in power.splitlines() if "mWakefulness=" in line],
        "idle": device_command("cmd deviceidle get deep").strip(),
        "runtime": [line.strip() for line in alarm.splitlines() if "Runtime uptime" in line],
    }


def wait_for_asleep_observation():
    """Observe the device once the screen-off transition has finished.

    Android moves from Awake to Asleep through Dozing, so a single reading
    taken right after the sleep key can catch the intermediate state and
    describe a device that is still on its way down.
    """
    deadline = time.monotonic() + ASLEEP_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        state = power_observation()
        if state["power"] == [POWER_ASLEEP_STATE]:
            return state
        time.sleep(POLL_SECONDS)
    return power_observation()


def pending_alarm_records():
    dump = device_command("dumpsys alarm")
    blocks = re.split(r"(?m)^    [A-Z_]+ #\d+: ", dump)[1:]
    return [
        block.splitlines()[:8] for block in blocks
        if DIKCIZ_DEBUG_PACKAGE in block.splitlines()[0]
        and AUTOMATION_ACTION_ALARM_EVENT in block
    ]


def broadcast_receipt(probe):
    dump = device_command("dumpsys activity broadcasts")
    blocks = re.split(r"(?m)^\s+Historical Broadcast #\d+:\s*$", dump)[1:]
    for block in blocks:
        record = "\n".join(block.splitlines()[:30])
        if probe in record and AUTOMATION_ACTION_ALARM_EVENT in record:
            return record
    raise AssertionError(f"No Android broadcast history for {probe}")
