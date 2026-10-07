"""Exercise cancellation, rescheduling, process loss, and idle on one emulator."""

import copy
import time
import uuid

import pytest

from . import (
    AUTOMATION, AUTOMATION_ACTION_ALARM_EVENT, AUTOMATION_NOTIFICATION_COMPONENT,
    DIKCIZ_DEBUG_PACKAGE, connected_websocket_control, device_command,
    run_device_operation, wait_for_restarted_websocket_control,
)
from .alarm_lifecycle_support import (
    ALARM_EVENT, DEFAULT_INTERVAL_MS, LONG_INTERVAL_MS, POWER_ASLEEP_STATE, SETTLE_SECONDS,
    broadcast_receipt, clock_when, configuration, observe_silence, pending_alarm_records,
    power_observation, replace, save_evidence, set_alarm_interval, timer_records,
    wait_delivery, wait_for_asleep_observation,
)
from .notification_permission import ensure_notification_posting_permission


DISABLED_OBSERVATION_SECONDS = 70
SLEEP_OBSERVATION_SECONDS = 150
RECOVERY_TIMEOUT_SECONDS = 130
INVALID_INTERVALS = {
    "zero": 0,
    "below_minimum": 59_999,
    "above_maximum": 86_400_001,
    "null": None,
    "string": "60000",
}
ALARM_RECEIVER = (
    f"{DIKCIZ_DEBUG_PACKAGE}/org.fossify.home.dikciz.DikcizAutomationAlarmReceiver"
)


def cancellation(connection, evidence):
    document = configuration(connection)
    alarm_ids = [
        script["scriptId"] for script in document["automation"]["scripts"]
        if any(item["event"] == ALARM_EVENT for item in script["subscriptions"])
    ]
    for script in document["automation"]["scripts"]:
        if script["scriptId"] in alarm_ids:
            script["enabled"] = False
    replace(connection, document)
    time.sleep(SETTLE_SECONDS)
    evidence["pendingAfterDisable"] = pending_alarm_records()
    observe_silence(DISABLED_OBSERVATION_SECONDS, evidence)
    previous = clock_when()
    document = configuration(connection)
    for script in document["automation"]["scripts"]:
        if script["scriptId"] in alarm_ids:
            script["enabled"] = True
    replace(connection, document)
    wait_delivery(previous, evidence)
    evidence["reEnabledDelivery"] = True
    document = configuration(connection)
    for script in document["automation"]["scripts"]:
        script["enabled"] = False
    replace(connection, document)
    time.sleep(SETTLE_SECONDS)
    evidence["pendingAfterShutdown"] = pending_alarm_records()
    assert not evidence["pendingAfterDisable"], evidence
    assert not evidence["pendingAfterShutdown"], evidence


def intervals(connection, evidence):
    previous = clock_when()
    document = configuration(connection)
    set_alarm_interval(document, LONG_INTERVAL_MS)
    started = time.monotonic()
    replace(connection, document)
    save_evidence(evidence, "waiting_two_minute_interval")
    delivery = wait_delivery(previous, evidence, timeout=140)
    elapsed = time.monotonic() - started
    evidence["longIntervalSeconds"] = elapsed
    assert 110 <= elapsed <= 140, evidence
    document = configuration(connection)
    set_alarm_interval(document, DEFAULT_INTERVAL_MS)
    started = time.monotonic()
    replace(connection, document)
    save_evidence(evidence, "waiting_shortened_interval")
    wait_delivery(delivery["when"], evidence)
    elapsed = time.monotonic() - started
    evidence["shortIntervalSeconds"] = elapsed
    assert 50 <= elapsed <= 80, evidence


def duplicate_wakeup(connection, evidence):
    previous = clock_when()
    baseline = len(timer_records())
    broadcast = f"am broadcast --user 0 -n {ALARM_RECEIVER} -a {AUTOMATION_ACTION_ALARM_EVENT}"
    shell_probe = f"dikciz-test://alarm/{uuid.uuid4()}"
    # Android may report completion even when receiver delivery was denied.
    evidence["shellBroadcast"] = device_command(
        f'{broadcast} -d {shell_probe} 2>&1; printf "\\nexit=%s\\n" "$?"',
    )
    evidence["shellReceipt"] = broadcast_receipt(shell_probe)
    assert "SKIPPED" in evidence["shellReceipt"], evidence
    assert "Permission Denial" in evidence["shellReceipt"], evidence
    for _ in range(3):
        probe = f"dikciz-test://alarm/{uuid.uuid4()}"
        response = device_command(f"run-as {DIKCIZ_DEBUG_PACKAGE} {broadcast} -d {probe}")
        evidence.setdefault("ownUidBroadcasts", []).append(response)
        assert "Broadcast completed" in response, evidence
        receipt = broadcast_receipt(probe)
        evidence.setdefault("ownUidReceipts", []).append(receipt)
        assert "DELIVERED" in receipt, evidence
    time.sleep(SETTLE_SECONDS)
    assert len(timer_records()) == baseline, "Early wakeup dispatched before the deadline"
    assert clock_when() == previous, "Early wakeup posted a clock notification"
    wait_delivery(previous, evidence)
    assert len(timer_records()) == baseline + 1, evidence


def process_recovery(connection, evidence):
    previous = clock_when()
    device_command("am start -W -a android.settings.SETTINGS")
    old_pid = device_command(f"pidof {DIKCIZ_DEBUG_PACKAGE}").strip()
    assert old_pid.isdecimal() and int(old_pid) > 0, old_pid
    evidence["oldPid"] = old_pid
    save_evidence(evidence, "killing_only_dikciz_process")
    device_command(f"run-as {DIKCIZ_DEBUG_PACKAGE} kill -9 {old_pid}")
    new_pid = device_command(f"pidof {DIKCIZ_DEBUG_PACKAGE} || test $? -eq 1").strip()
    assert new_pid != old_pid, "Dikciz process was not killed"
    wait_delivery(previous, evidence, timeout=RECOVERY_TIMEOUT_SECONDS)
    new_pid = device_command(f"pidof {DIKCIZ_DEBUG_PACKAGE}").strip()
    evidence["newPid"] = new_pid
    assert new_pid.isdecimal() and new_pid != old_pid, evidence


def sleep_and_idle(connection, evidence):
    for force_idle in (False, True):
        baseline = len(timer_records())
        before = clock_when()
        device_command("input keyevent KEYCODE_SLEEP")
        if force_idle:
            response = device_command("cmd deviceidle force-idle deep")
            assert "Now forced in to deep idle mode" in response, response
        state = wait_for_asleep_observation()
        assert state["power"] == [POWER_ASLEEP_STATE], state
        if force_idle:
            assert state["idle"] == "IDLE", state
        save_evidence(evidence, "observing_deep_idle" if force_idle else "observing_screen_off")
        time.sleep(SLEEP_OBSERVATION_SECONDS)
        observed = {
            "forcedIdle": force_idle,
            "before": state,
            "after": power_observation(),
            "timerEvents": timer_records()[baseline:],
            "notificationBefore": before,
            "notificationAfter": clock_when(),
        }
        evidence.setdefault("sleepWindows", []).append(observed)
        device_command("cmd deviceidle unforce")
        device_command("input keyevent KEYCODE_WAKEUP")
        device_command("wm dismiss-keyguard")
        wait_delivery(observed["notificationAfter"], evidence, timeout=RECOVERY_TIMEOUT_SECONDS)


def invalid_interval(connection, evidence):
    original = configuration(connection)
    invalid = copy.deepcopy(original)
    set_alarm_interval(invalid, INVALID_INTERVALS[evidence["scenario"]])
    _, error = AUTOMATION.request_error_with_id(
        connection, AUTOMATION.TYPE_CONFIG_REPLACE, config=invalid,
    )
    evidence["rejection"] = error
    assert configuration(connection)["automation"] == original["automation"]


SCENARIOS = {
    "cancellation": cancellation,
    "intervals": intervals,
    "duplicate_wakeup": duplicate_wakeup,
    "process_recovery": process_recovery,
    "sleep_and_idle": sleep_and_idle,
    **{name: invalid_interval for name in INVALID_INTERVALS},
}


@pytest.mark.parametrize(
    "scenario",
    [name for name in SCENARIOS if name not in {"duplicate_wakeup", "cancellation", "process_recovery"}],
)
def test_alarm_lifecycle_on_shared_emulator(scenario):
    run_alarm_scenario(scenario)


def test_alarm_shutdown():
    run_alarm_scenario("cancellation")


def test_alarm_process_recovery():
    run_alarm_scenario("process_recovery")


def test_alarm_duplicate_wakeup_on_shared_emulator():
    run_alarm_scenario("duplicate_wakeup")


def run_alarm_scenario(scenario):
    evidence = {"scenario": scenario, "result": "running"}
    try:
        device_command("cmd deviceidle unforce")
        device_command("input keyevent KEYCODE_WAKEUP")
        device_command("wm dismiss-keyguard")
        device_command("cmd statusbar collapse")
        run_device_operation("home-start", PACKAGE_NAME=DIKCIZ_DEBUG_PACKAGE)
        with connected_websocket_control() as connection:
            AUTOMATION.request(connection, AUTOMATION.TYPE_RESET)
            ensure_notification_posting_permission(connection)
            device_command(f"cmd notification allow_listener {AUTOMATION_NOTIFICATION_COMPONENT}")
            save_evidence(evidence, "scenario_started")
            SCENARIOS[scenario](connection, evidence)
        evidence["result"] = "passed"
    finally:
        if evidence["result"] == "running":
            evidence["result"] = "failed"
        save_evidence(evidence, "cleanup")
        device_command("cmd deviceidle unforce")
        device_command("input keyevent KEYCODE_WAKEUP")
        device_command("wm dismiss-keyguard")
        run_device_operation("home-start", PACKAGE_NAME=DIKCIZ_DEBUG_PACKAGE)
        connection = wait_for_restarted_websocket_control()
        try:
            AUTOMATION.request(connection, AUTOMATION.TYPE_RESET)
        finally:
            connection.close()
        save_evidence(evidence, "finished")
