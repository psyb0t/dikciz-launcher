"""Persistent asynchronous commands use the ordinary executor on every transport."""

import uuid
from . import *
from .test_native_data import native_call, PROTOCOLS
from .test_capability_expansion import wait_native_control


def finished_job(protocol, identifier):
    deadline = time.monotonic() + 20
    while True:
        result = native_call(protocol, "jobStatus", {"jobId": identifier})
        if result["state"] not in ("queued", "running"):
            return result
        assert time.monotonic() < deadline, result
        time.sleep(.1)


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_command_jobs_lifecycle(websocket_control, protocol):
    identifiers = []
    for index in range(7):
        key = "job-test-" + uuid.uuid4().hex
        request = {"command": {"type": "deviceState"}, "idempotencyKey": key}
        job = native_call(protocol, "jobStart", request)
        identifier = job["jobId"]
        identifiers.append(identifier)
        repeated = native_call(protocol, "jobStart", request)
        assert repeated["jobId"] == identifier and repeated["deduplicated"]
        native_call(protocol, "jobStart", {**request, "command": {"type": "nativeDataStatus"}}, error="job_idempotency_conflict")
        result = finished_job(protocol, identifier)
        assert result["state"] == "completed" and result["outcomeKnown"]
        assert result["response"]["type"] == "result"
        assert isinstance(result["response"]["result"]["screenInteractive"], bool)
        assert "idempotencyKey" not in result and "fingerprint" not in result
    seen = []
    offset = 0
    while True:
        page = native_call(protocol, "jobsList", {"limit": 3, "offset": offset})
        seen.extend(item["jobId"] for item in page["items"])
        assert all("response" not in item for item in page["items"])
        offset += len(page["items"])
        if not page["hasMore"]:
            assert offset == page["total"]
            break
    assert set(identifiers).issubset(seen) and len(set(seen)) == len(seen)
    assert native_call(protocol, "jobsList", {"offset": offset})["items"] == []
    assert native_call(protocol, "jobsList", {"offset": 1000000})["items"] == []
    for command in ({"type": "jobStart"}, {"type": "remoteAuthEnable"}, {"type": "missing"}, {"type": 1}):
        native_call(protocol, "jobStart", {"command": command, "idempotencyKey": "invalid"}, error="validation_failed")
    native_call(protocol, "jobStart", {"command": {"type": "deviceState"}, "idempotencyKey": "../bad"}, error="validation_failed")
    native_call(protocol, "jobStart", {"command": {"type": "deviceState"}, "idempotencyKey": "valid", "retentionHours": 0}, error="validation_failed")
    native_call(protocol, "jobStatus", {"jobId": str(uuid.uuid4())}, error="job_not_found")


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_command_jobs_cancellation_and_recovery(websocket_control, protocol):
    jobs = []
    try:
        for _ in range(3):
            request = {"command": {"type": "shell", "command": "sleep 30", "root": False, "timeoutMilliseconds": 60000}, "idempotencyKey": "job-wait-" + uuid.uuid4().hex}
            jobs.append(native_call(protocol, "jobStart", request)["jobId"])
        queued = native_call(protocol, "jobCancel", {"jobId": jobs[2]})
        assert queued["state"] == "cancelled" and queued["cancellationRequested"]
        for identifier in jobs[:2]:
            assert native_call(protocol, "jobCancel", {"jobId": identifier})["cancellationRequested"]
            assert finished_job(protocol, identifier)["state"] in ("failed", "cancelled")
        pending = native_call(protocol, "jobStart", {"command": {"type": "shell", "command": "sleep 30", "root": False, "timeoutMilliseconds": 60000}, "idempotencyKey": "job-restart-" + uuid.uuid4().hex})
        identifier = pending["jobId"]
        device_command("am force-stop " + DIKCIZ_DEBUG_PACKAGE)
        restore_system_home()
        wait_native_control(protocol)
        recovered = native_call(protocol, "jobStatus", {"jobId": identifier})
        assert recovered["state"] == "interrupted" and not recovered["outcomeKnown"]
    finally:
        for identifier in jobs:
            native_call("http", "jobCancel", {"jobId": identifier})


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_command_jobs_corrupt_storage_fails_consistently(websocket_control, protocol):
    identifier = str(uuid.uuid4())
    record_path = "files/command-jobs/" + identifier + ".json"
    sandbox = "run-as " + DIKCIZ_DEBUG_PACKAGE
    device_command(sandbox + " mkdir -p files/command-jobs")
    device_command(sandbox + " touch " + record_path)
    try:
        device_command("am force-stop " + DIKCIZ_DEBUG_PACKAGE)
        restore_system_home()
        wait_native_control(protocol)
        native_call(protocol, "jobsList", error=("job_storage_invalid", "native_operation_failed"))
        native_call(protocol, "jobsList", error="job_storage_invalid")
        device_command(sandbox + " rm " + record_path)
        assert isinstance(native_call(protocol, "jobsList")["items"], list)
    finally:
        device_command(sandbox + " rm -f " + record_path)
