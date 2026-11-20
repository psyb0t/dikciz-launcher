"""Verify raw sensor capture through the shared executor and live app delivery."""

import pytest

from . import AUTOMATION, device_command, fixture_document, wait_for_snapshot_node, websocket_control
from .test_custom_apps import app_factory, wait_state
from .test_entrypoint_parity import parity_html_configuration, wait_for_parity_response
from .test_native_data import native_call
from .test_remote_tokens import admin, bearer, call, create


@pytest.fixture
def sensor_token_owner(websocket_control):
    password = bearer.fresh_bearer_password()
    steps = ("launcher:controls", "launcher:controls:dikciz-settings",
             bearer.SEMANTIC_SETTINGS_AUTOMATION, bearer.SEMANTIC_SETTINGS_REMOTE_AUTH)
    try:
        for node in steps:
            wait_for_snapshot_node(websocket_control, node)
            AUTOMATION.request(websocket_control, AUTOMATION.TYPE_TAP, semanticId=node)
        wait_for_snapshot_node(websocket_control, bearer.SEMANTIC_REMOTE_AUTH_SET_PASSWORD)
        bearer.set_phone_password(websocket_control, password)
        yield password
    finally:
        device_command("rm -f /sdcard/Dikciz/control/remote-auth.json /sdcard/Dikciz/control/remote-auth.json.bak /sdcard/Dikciz/control/remote-auth.json.new")


def accelerometer(protocol="http"):
    readings = native_call(protocol, "sensorReadings", {"limit": 200})
    return next(item["sensorId"] for item in readings["items"] if item["type"] == 1 and item["available"])


def test_sensor_stream_cursor_parity(websocket_control):
    setup = {"sensorId": accelerometer(), "samplingPeriodMicroseconds": 5000, "capacity": 8,
             "durationMilliseconds": 20000, "deliveryIntervalMilliseconds": 100}
    stream = AUTOMATION.request(websocket_control, "sensorStreamStart", **setup)
    identity = {"sessionId": stream["sessionId"]}
    try:
        assert stream["requestedSamplingPeriodMicroseconds"] == 5000
        event = AUTOMATION.receive_event(websocket_control, "sensorStreamSamples")["fields"]
        assert event["sessionId"] == identity["sessionId"] and event["samples"]
        for protocol in ("http", "mcp", "websocket"):
            status = native_call(protocol, "sensorStreamStatus", identity)
            assert status["state"] == "active" and status["retainedSamples"] <= 8
            data = native_call(protocol, "sensorStreamRead", {**identity, "afterSequence": 0, "limit": 2})
            assert data["lostSamples"] > 0
            samples = data["samples"]
            assert len(samples) == 2
            assert samples[1]["sequence"] == samples[0]["sequence"] + 1
            assert int(samples[1]["timestampNanoseconds"]) > int(samples[0]["timestampNanoseconds"])
            assert len(samples[0]["values"]) == 3
            next_page = native_call(protocol, "sensorStreamRead", {**identity, "afterSequence": data["nextSequence"]})
            assert all(sample["sequence"] > data["nextSequence"] for sample in next_page["samples"])
        assert native_call("http", "sensorStreamStart", {**setup, "capacity": 0}, error="validation_failed")
    finally:
        assert native_call("mcp", "sensorStreamStop", identity)["state"] == "stopped"
    assert native_call("http", "sensorStreamStatus", identity)["state"] == "stopped"


def test_sensor_stream_scoped_ownership(websocket_control, sensor_token_owner):
    readings = call(sensor_token_owner, "sensorReadings", {"limit": 200})
    sensor_id = next(item["sensorId"] for item in readings["items"] if item["type"] == 1 and item["available"])
    grants = ["command.sensorStreamStart", "command.sensorStreamStatus",
              "command.sensorStreamRead", "command.sensorStreamStop"]
    first, first_id = create(sensor_token_owner, grants)
    second, _ = create(sensor_token_owner, grants)
    start_only, _ = create(sensor_token_owner, ["command.sensorStreamStart"])
    call(start_only, "sensorStreamStart", {"sensorId": sensor_id, "deliveryIntervalMilliseconds": 100},
         "http", "remote_permission_denied")
    session = call(first, "sensorStreamStart", {"sensorId": sensor_id, "durationMilliseconds": 10000})
    identity = {"sessionId": session["sessionId"]}
    try:
        assert call(first, "sensorStreamStatus", identity)["state"] == "active"
        for command in ("sensorStreamRead", "sensorStreamStop"):
            call(second, command, identity, "http", "sensor_session_not_found")
    finally:
        call(first, "sensorStreamStop", identity)
        admin(sensor_token_owner, "revoke", id=first_id)


@pytest.mark.parametrize("language", ["javascript", "lua"])
def test_sensor_stream_app_receives_native_batches(app_factory, language):
    if language == "javascript":
        source = """
function onEvent(event) {
    if (event.type === "fixture.start") {
        const response = context.request({type:"sensorStreamStart", sensorId:event.payload.sensorId,
            durationMilliseconds:7000, deliveryIntervalMilliseconds:100, background:true});
        return {result:response};
    }
    if (event.type === "app.sensor.samples" && event.payload.samples.length) {
        return {actions:[{type:"patchState", values:{received:true}}]};
    }
    if (event.type === "app.sensor.stopped") {
        return {actions:[{type:"patchState", values:{terminal:event.payload.state}}]};
    }
    return {};
}"""
    else:
        source = """
function on_event(event)
    if event.type == "fixture.start" then
        local response = context.request({type="sensorStreamStart", sensorId=event.payload.sensorId,
            durationMilliseconds=7000, deliveryIntervalMilliseconds=100, background=true})
        return {result=response}
    end
    if event.type == "app.sensor.samples" and #event.payload.samples > 0 then
        return {actions={{type="patchState", values={received=true}}}}
    end
    if event.type == "app.sensor.stopped" then
        return {actions={{type="patchState", values={terminal=event.payload.state}}}}
    end
    return {}
end
"""
    app_id, _ = app_factory(source=source, language=language)
    identity = {"appId": app_id}
    native_call("http", "customAppEvent", {**identity, "event": "app.sensor.samples"},
                error="validation_failed")
    response = native_call("http", "customAppEvent", {
        **identity, "event": "fixture.start", "payload": {"sensorId": accelerometer()},
    })
    assert response["result"]["type"] == "result", response
    wait_state(app_id, "received", True)
    wait_state(app_id, "terminal", "expired")


def test_sensor_stream_html_cursor(websocket_control):
    sensor_id = accelerometer()
    javascript = """
window.addEventListener("dikciz-ready", async () => {
    let session;
    let result;
    try {
        session = await dikciz.command("sensorStreamStart", {sensorId: SENSOR_ID, durationMilliseconds: 5000});
        await new Promise(resolve => setTimeout(resolve, 250));
        const page = await dikciz.command("sensorStreamRead", {sessionId: session.sessionId, limit: 2});
        result = {samples:page.samples.length, cursor:page.nextSequence};
    } catch (error) {
        result = {error:error.message};
    } finally {
        if (session) {
            const stopped = await dikciz.command("sensorStreamStop", {sessionId: session.sessionId});
            result.stopped = stopped.state;
        }
    }
    await selfWidget.patchState({parityResponse:result});
});""".replace("SENSOR_ID", str(sensor_id))
    configuration = parity_html_configuration(fixture_document(), javascript)
    AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_REPLACE, config=configuration)
    result = wait_for_parity_response(websocket_control)
    assert result["samples"] == 2 and result["cursor"] >= 2, result
    assert result["stopped"] == "stopped", result
