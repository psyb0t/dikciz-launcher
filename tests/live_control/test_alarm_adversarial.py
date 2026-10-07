"""Adversarial scheduler checks through the installed app and public state."""

import copy
import json
import time

import pytest

from . import (
    ARTIFACT_DIRECTORY, AUTOMATION, AUTOMATION_LIVE_POLICY_ID, AUTOMATION_LIVE_SCRIPT_ID,
    automation_configuration, connected_websocket_control, device_command,
    device_log_records, wait_for_automation_script_state,
)
from .alarm_lifecycle_support import (
    ALARM_EVENT, DEFAULT_INTERVAL_MS, DELIVERY_TIMEOUT_SECONDS, LONG_INTERVAL_MS,
    configuration, replace,
)


SECOND_SCRIPT_ID = "alarm-adversarial-second"
POLL_SECONDS = 1
MAXIMUM_INTERVAL_MS = 86_400_000
NON_MULTIPLE_INTERVAL_MS = 90_000
COUNTER_SOURCE = '''function on_event(event)
  return {type="patchState", values={count=context.state.count + 1}}
end'''
ERROR_SOURCE = 'function on_event(event) error("alarm-fixture-failure") end'
INVALID_VALUES = {
    "missing": None,
    "negative": -1,
    "fractional": 60_000.5,
    "boolean": True,
    "empty": "",
    "object": {},
    "array": [],
    "overflow": 2 ** 63,
}


def counter_config(connection):
    original = configuration(connection)
    original["scripts"] = [
        script for script in original["scripts"]
        if script["id"] not in {AUTOMATION_LIVE_SCRIPT_ID, SECOND_SCRIPT_ID}
    ]
    return automation_configuration(
        original, COUNTER_SOURCE, {"count": 0},
        [ALARM_EVENT], ["patchState"], [{
            "event": ALARM_EVENT, "minimumIntervalMilliseconds": 0,
            "alarmIntervalMilliseconds": DEFAULT_INTERVAL_MS,
        }],
    )


def counts(connection):
    return {
        script["id"]: script["state"]["count"]
        for script in configuration(connection)["scripts"]
        if script["id"] in {AUTOMATION_LIVE_SCRIPT_ID, SECOND_SCRIPT_ID}
    }


def add_second(document, interval=DEFAULT_INTERVAL_MS):
    script = copy.deepcopy(document["scripts"][-1])
    script["id"] = SECOND_SCRIPT_ID
    document["scripts"].append(script)
    wrapper = copy.deepcopy(document["automation"]["scripts"][0])
    wrapper["scriptId"] = SECOND_SCRIPT_ID
    wrapper["subscriptions"][0]["alarmIntervalMilliseconds"] = interval
    wrapper["subscriptions"][0]["minimumIntervalMilliseconds"] = 0
    document["automation"]["scripts"].append(wrapper)


def wait_count(connection, script_id, expected):
    wait_for_automation_script_state(
        connection, script_id, "count", expected,
        timeout_seconds=DELIVERY_TIMEOUT_SECONDS,
    )


def mixed_intervals(connection, evidence):
    document = counter_config(connection)
    add_second(document, NON_MULTIPLE_INTERVAL_MS)
    replace(connection, document)
    wait_count(connection, AUTOMATION_LIVE_SCRIPT_ID, 1)
    time.sleep(POLL_SECONDS)
    evidence["firstMinuteCounts"] = counts(connection)
    assert evidence["firstMinuteCounts"][SECOND_SCRIPT_ID] == 0, evidence
    wait_count(connection, SECOND_SCRIPT_ID, 1)
    evidence["secondMinuteCounts"] = counts(connection)
    assert evidence["secondMinuteCounts"][AUTOMATION_LIVE_SCRIPT_ID] == 2, evidence


def delivery_minimum(connection, evidence):
    document = counter_config(connection)
    document["automation"]["scripts"][0]["subscriptions"][0][
        "minimumIntervalMilliseconds"
    ] = LONG_INTERVAL_MS
    add_second(document)
    replace(connection, document)
    wait_count(connection, SECOND_SCRIPT_ID, 1)
    evidence["firstMinuteCounts"] = counts(connection)
    wait_count(connection, SECOND_SCRIPT_ID, 2)
    time.sleep(POLL_SECONDS)
    evidence["secondMinuteCounts"] = counts(connection)
    assert evidence["secondMinuteCounts"][AUTOMATION_LIVE_SCRIPT_ID] == 1, evidence


def failure_isolation(connection, evidence):
    document = counter_config(connection)
    add_second(document)
    document["scripts"][-2]["source"] = ERROR_SOURCE
    baseline = len(device_log_records())
    replace(connection, document)
    wait_count(connection, SECOND_SCRIPT_ID, 1)
    evidence["counts"] = counts(connection)
    path = f"/sdcard/Dikciz/scripts/{AUTOMATION_LIVE_SCRIPT_ID}/run-status.json"
    evidence["failedRun"] = json.loads(device_command(f"cat {path}"))
    evidence["scriptLogs"] = [
        record for record in device_log_records()[baseline:]
        if record.get("script_id") == AUTOMATION_LIVE_SCRIPT_ID
    ]
    assert evidence["counts"][AUTOMATION_LIVE_SCRIPT_ID] == 0, evidence
    assert evidence["failedRun"]["outcome"] == "failed", evidence
    assert any(
        record.get("event") == "automation_delivery_rejected"
        for record in evidence["scriptLogs"]
    ), "Script failure had no public rejection record"


def validate_interval(connection, evidence, case):
    original = configuration(connection)
    document = counter_config(connection)
    subscription = document["automation"]["scripts"][0]["subscriptions"][0]
    if case == "maximum":
        subscription["alarmIntervalMilliseconds"] = MAXIMUM_INTERVAL_MS
        accepted = replace(connection, document)
        evidence["accepted"] = accepted["automation"]
        assert accepted["automation"] == document["automation"]
        return
    if case == "missing":
        del subscription["alarmIntervalMilliseconds"]
    else:
        subscription["alarmIntervalMilliseconds"] = INVALID_VALUES[case]
    _, evidence["error"] = AUTOMATION.request_error_with_id(
        connection, AUTOMATION.TYPE_CONFIG_REPLACE, config=document,
    )
    assert evidence["error"]["code"] == "validation_failed", evidence
    assert "alarmIntervalMilliseconds" in evidence["error"]["message"], evidence
    assert configuration(connection)["automation"] == original["automation"]


SCENARIOS = {
    "mixed_intervals": mixed_intervals,
    "delivery_minimum": delivery_minimum,
    "failure_isolation": failure_isolation,
}


def reset_without_counter_fixtures(connection):
    AUTOMATION.request(connection, AUTOMATION.TYPE_RESET)
    document = configuration(connection)
    fixture_ids = {AUTOMATION_LIVE_SCRIPT_ID, SECOND_SCRIPT_ID}
    document["scripts"] = [
        script for script in document["scripts"] if script["id"] not in fixture_ids
    ]
    document["automation"]["scripts"] = [
        script for script in document["automation"]["scripts"]
        if script["scriptId"] not in fixture_ids
    ]
    document["automation"]["policies"] = [
        policy for policy in document["automation"]["policies"]
        if policy["id"] != AUTOMATION_LIVE_POLICY_ID
    ]
    replace(connection, document)


@pytest.mark.parametrize("case", SCENARIOS)
def test_alarm_adversarial_execution(case):
    run_case(case)


def run_case(case):
    evidence = {"case": case, "result": "failed"}
    with connected_websocket_control() as connection:
        reset_without_counter_fixtures(connection)
        try:
            if case in SCENARIOS:
                SCENARIOS[case](connection, evidence)
            else:
                validate_interval(connection, evidence, case)
            evidence["result"] = "passed"
        finally:
            artifact = ARTIFACT_DIRECTORY / f"alarm-adversarial-{case}.json"
            artifact.write_text(json.dumps(evidence, indent=2), encoding="utf-8")
            reset_without_counter_fixtures(connection)
