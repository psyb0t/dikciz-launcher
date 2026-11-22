"""Android location registration follows current custom-app demand."""

import time

from . import DIKCIZ_DEBUG_PACKAGE, device_command, websocket_control
from .test_custom_apps import app_factory
from .test_native_data import grant_permissions, native_call
from .test_sensor_streams import accelerometer

LOCATION_PERMISSIONS = (
    "android.permission.ACCESS_COARSE_LOCATION",
    "android.permission.ACCESS_FINE_LOCATION",
    "android.permission.ACCESS_BACKGROUND_LOCATION",
)
SOURCE_TIMEOUT_SECONDS = 10
POLL_SECONDS = 0.25


def gps_registered():
    lines = device_command("dumpsys location").splitlines()
    provider = next((index for index, line in enumerate(lines) if line.strip() == "gps provider:"), None)
    assert provider is not None, "Android did not report its GPS provider"
    header = lines[provider]
    indentation = len(header) - len(header.lstrip())
    active = []
    for line in lines[provider + 1:]:
        stripped = line.strip()
        if not stripped:
            continue
        if len(line) - len(line.lstrip()) <= indentation:
            break
        active.append(stripped)
    return any(DIKCIZ_DEBUG_PACKAGE in line and "Request[" in line for line in active)


def wait_for_gps(expected):
    deadline = time.monotonic() + SOURCE_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        if gps_registered() == expected:
            return
        time.sleep(POLL_SECONDS)
    raise AssertionError(f"GPS registration did not become {expected}")


def test_idle_resources_release_gps_when_demand_or_stream_ends(websocket_control, app_factory):
    grant_permissions(LOCATION_PERMISSIONS)
    source = """
function onEvent(event) {
    if (event.type === "fixture.start") {
        const started = context.request({type:"sensorStreamStart", sensorId:event.payload.sensorId,
            durationMilliseconds:45000, background:true});
        return {result:started};
    }
    if (event.type === "fixture.stop") {
        const stopped = context.request({type:"sensorStreamStop", sessionId:event.payload.sessionId});
        return {result:stopped};
    }
    return {};
}
"""
    app_id, fields = app_factory(source=source)
    fields["manifest"]["locationWhileStreaming"] = "needsLocation"
    fields["manifest"]["eventSubscriptions"] = [
        {"event": "location", "locationPrecision": "precise", "minimumIntervalMilliseconds": 2000},
    ]
    native_call("http", "customAppInstall", fields)
    identity = {"appId": app_id}

    def request(event, payload):
        reply = native_call("http", "customAppEvent", {**identity, "event": event, "payload": payload})
        assert reply["result"]["type"] == "result", reply
        return reply["result"]["result"]

    def demand(value):
        native_call("http", "customAppState", {
            **identity, "action": "replace", "state": {"needsLocation": value},
        })

    demand(True)
    wait_for_gps(False)
    demand(False)
    stream = request("fixture.start", {"sensorId": accelerometer()})
    session = {"sessionId": stream["sessionId"]}
    try:
        wait_for_gps(False)
        demand(True)
        wait_for_gps(True)
        demand(False)
        wait_for_gps(False)
        demand(True)
        wait_for_gps(True)
    finally:
        request("fixture.stop", session)
    wait_for_gps(False)
