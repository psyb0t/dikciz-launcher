"""Real foreground camera video, optional microphone and cancellation cleanup."""

import base64
import uuid

from . import *
from .test_native_data import PROTOCOLS, native_call, grant_permissions
from .test_capability_expansion import wait_native_control

CAPTURES = "/sdcard/Android/data/" + DIKCIZ_DEBUG_PACKAGE + "/files/captures"


def video_files():
    return {row["name"] for row in native_call("http", "fileList", {"path": CAPTURES})["items"] if row["name"].startswith("video-")}


def verify_video(protocol, result, audio):
    try:
        assert result["state"] == "saved" and result["mimeType"] == "video/mp4"
        assert result["durationMilliseconds"] > 0 and result["width"] > 0 and result["height"] > 0
        assert result["observedCaptureFrames"] > 0 and result["hasAudio"] is audio
        header = base64.b64decode(native_call(protocol, "fileRead", {"path": result["path"], "limit": 64})["dataBase64"])
        assert b"ftyp" in header
    finally:
        native_call(protocol, "fileManage", {"action": "delete", "path": result["path"]})


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_camera_video_finalized_capture(websocket_control, protocol):
    grant_permissions(("android.permission.CAMERA", "android.permission.RECORD_AUDIO"))
    restore_system_home()
    wait_native_control(protocol)
    camera_id = native_call(protocol, "cameraList")["items"][0]["cameraId"]
    info = native_call(protocol, "cameraCapabilities", {"cameraId": camera_id})
    assert info["videoSizes"] and info["frameRateRanges"]
    result = native_call(protocol, "cameraVideoRecord", {"cameraId": camera_id, "durationMilliseconds": 1000, "maxDimension": 640})
    verify_video(protocol, result, False)
    result = native_call(protocol, "cameraVideoRecord", {"cameraId": camera_id, "durationMilliseconds": 1000,
        "maxDimension": 640, "withAudio": True, "orientation": 90})
    assert result["rotation"] == 90
    verify_video(protocol, result, True)
    for fields in (
        {"orientation": 45}, {"durationMilliseconds": 999}, {"durationMilliseconds": 120001},
        {"durationMilliseconds": 40000}, {"durationMilliseconds": 3000, "timeoutMilliseconds": 2000},
        {"maxDimension": 319}, {"maxDimension": 1921}, {"bitRate": 499999}, {"bitRate": 20000001},
        {"frameRate": 4}, {"frameRate": 61}, {"withAudio": "true"}, {"withAudio": None},
        {"timeoutMilliseconds": 300001}, {"durationMilliseconds": True},
    ):
        native_call(protocol, "cameraVideoRecord", fields, error="validation_failed")
    native_call(protocol, "cameraVideoRecord", {"cameraId": "missing"}, error="native_record_not_found")


def test_camera_video_permission_deadline_and_reuse(websocket_control):
    device_command("pm revoke " + DIKCIZ_DEBUG_PACKAGE + " android.permission.CAMERA")
    restore_system_home()
    wait_native_control("http")
    denied = native_call("http", "cameraVideoRecord", error="native_permission_cameraAndMicrophone")
    assert denied["permissionRequest"]["requirementId"] == "cameraAndMicrophone"
    grant_permissions(("android.permission.CAMERA",))
    device_command("pm revoke " + DIKCIZ_DEBUG_PACKAGE + " android.permission.RECORD_AUDIO")
    restore_system_home()
    wait_native_control("http")
    native_call("http", "cameraVideoRecord", {"withAudio": True}, error="native_permission_cameraAndMicrophone")
    native_call("http", "fileMkdir", {"path": CAPTURES})
    before = video_files()
    native_call("http", "cameraVideoRecord", {"durationMilliseconds": 1000, "timeoutMilliseconds": 1000, "maxDimension": 640},
                error="capture_timeout")
    assert video_files() == before
    result = native_call("http", "cameraVideoRecord", {"durationMilliseconds": 1000, "maxDimension": 640})
    verify_video("http", result, False)


def test_camera_video_job_cancel_and_hardware_ownership(websocket_control):
    grant_permissions(("android.permission.CAMERA", "android.permission.RECORD_AUDIO"))
    restore_system_home()
    wait_native_control("http")
    native_call("http", "fileMkdir", {"path": CAPTURES})
    before = video_files()
    job = native_call("http", "jobStart", {"idempotencyKey": "video-" + uuid.uuid4().hex,
        "command": {"type": "cameraVideoRecord", "durationMilliseconds": 15000, "maxDimension": 640, "withAudio": True}})
    identifier = job["jobId"]
    try:
        deadline = time.monotonic() + 15
        while video_files() == before:
            status = native_call("http", "jobStatus", {"jobId": identifier})
            assert status["state"] not in ("failed", "cancelled", "completed"), status
            assert time.monotonic() < deadline, status
            time.sleep(.1)
        native_call("mcp", "cameraCapture", error="hardware_busy")
        native_call("websocket", "microphoneRecord", {"durationMilliseconds": 1000}, error="hardware_busy")
        assert native_call("http", "jobCancel", {"jobId": identifier})["cancellationRequested"]
        deadline = time.monotonic() + 15
        while video_files() != before:
            assert time.monotonic() < deadline
            time.sleep(.1)
        result = native_call("http", "cameraVideoRecord", {"durationMilliseconds": 1000, "maxDimension": 640, "withAudio": True})
        verify_video("http", result, True)
    finally:
        native_call("http", "jobCancel", {"jobId": identifier})


def test_camera_video_html_binding(websocket_control):
    from .test_entrypoint_parity import parity_html_configuration, wait_for_parity_response
    original = AUTOMATION.require_configuration(AUTOMATION.request(websocket_control, "configGet"))
    grant_permissions(("android.permission.CAMERA",))
    restore_system_home()
    wait_native_control("http")
    javascript = """window.addEventListener('dikciz-ready', async () => {
      try {
        const video = await dikciz.command('cameraVideoRecord', {durationMilliseconds:1000,maxDimension:640});
        await selfWidget.patchState({parityResponse:{video}});
      } catch (error) {
        await selfWidget.patchState({parityResponse:{failure:{code:error.code,message:error.message}}});
      }
    });"""
    try:
        AUTOMATION.request(websocket_control, "configReplace", config=parity_html_configuration(original, javascript))
        result = wait_for_parity_response(websocket_control)
        assert "failure" not in result, result
        verify_video("http", result["video"], False)
    finally:
        AUTOMATION.request(websocket_control, "configReplace", config=original)


def test_camera_video_lua_binding(websocket_control):
    original = AUTOMATION.require_configuration(AUTOMATION.request(websocket_control, "configGet"))
    grant_permissions(("android.permission.CAMERA",))
    restore_system_home()
    wait_native_control("http")
    source = """function on_event(event)
      if event.type ~= 'fixture.video' then return end
      local video = context.requestJson({type='cameraVideoRecord',durationMilliseconds=1000,maxDimension=640})
      return {actions={{type='patchState',values={video=video}}}}
    end"""
    configured = automation_configuration(original, source, {}, ["manualTrigger"], ["patchState"],
        [{"event": "fixture.video", "minimumIntervalMilliseconds": 0}])
    try:
        AUTOMATION.request(websocket_control, "configReplace", config=configured)
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
        AUTOMATION.request(websocket_control, "automationDispatch", sourceWidgetAddress="1H1V-welcome",
            actions=[{"type": "emitEvent", "event": "fixture.video", "payload": {}}])
        deadline = time.monotonic() + 20
        while True:
            document = AUTOMATION.require_configuration(AUTOMATION.request(websocket_control, "configGet"))
            state = next(script["state"] for script in document["scripts"] if script["id"] == AUTOMATION_LIVE_SCRIPT_ID)
            if "video" in state:
                response = json.loads(state["video"])
                assert response["type"] == "result", response
                verify_video("http", response["result"], False)
                break
            assert time.monotonic() < deadline, state
            time.sleep(.1)
    finally:
        AUTOMATION.request(websocket_control, "configReplace", config=original)


def test_camera_video_foreground_loss(websocket_control):
    grant_permissions(("android.permission.CAMERA",))
    restore_system_home()
    wait_native_control("http")
    native_call("http", "fileMkdir", {"path": CAPTURES})
    before = video_files()
    job = native_call("http", "jobStart", {"idempotencyKey": "video-front-" + uuid.uuid4().hex,
        "command": {"type": "cameraVideoRecord", "durationMilliseconds": 15000, "maxDimension": 640}})
    identifier = job["jobId"]
    try:
        deadline = time.monotonic() + 15
        while video_files() == before:
            status = native_call("http", "jobStatus", {"jobId": identifier})
            assert status["state"] not in ("failed", "completed"), status
            assert time.monotonic() < deadline
            time.sleep(.1)
        device_command("am start -a android.settings.SETTINGS")
        deadline = time.monotonic() + 15
        while True:
            status = native_call("http", "jobStatus", {"jobId": identifier})
            if status["state"] == "failed":
                assert status["response"]["code"] == "capture_foreground_required", status
                break
            assert time.monotonic() < deadline, status
            time.sleep(.1)
        assert video_files() == before
    finally:
        native_call("http", "jobCancel", {"jobId": identifier})
        restore_system_home()
