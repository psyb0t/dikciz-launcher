"""Configured JPEG capture through the shared native command target."""

import base64

from . import *
from .test_native_data import PROTOCOLS, grant_permissions, native_call
from .test_capability_expansion import wait_native_control


@pytest.mark.parametrize("protocol", PROTOCOLS)
def test_camera_controls_capture_and_validation(websocket_control, protocol):
    grant_permissions(("android.permission.CAMERA",))
    restore_system_home()
    wait_native_control(protocol)
    cameras = native_call(protocol, "cameraList")["items"]
    assert cameras
    camera_id = cameras[0]["cameraId"]
    capabilities = native_call(protocol, "cameraCapabilities", {"cameraId": camera_id})
    requested = {"cameraId": camera_id, "jpegQuality": 81, "jpegOrientation": 90, "zoomPercent": 100}
    modes = capabilities["autofocusModes"]
    if modes:
        requested["autofocusMode"] = modes[0]
    lower, upper = capabilities["exposureCompensationMinimum"], capabilities["exposureCompensationMaximum"]
    if lower is not None and upper is not None and lower <= 0 <= upper:
        requested["exposureCompensation"] = 0
    photo = native_call(protocol, "cameraCapture", requested)
    try:
        assert photo["state"] == "saved" and photo["bytes"] > 0
        assert photo["cameraId"] == camera_id and photo["width"] > 0 and photo["height"] > 0
        controls, actual = photo["requestedControls"], photo["captureMetadata"]
        for key, value in requested.items():
            if key != "cameraId":
                assert controls[key] == value
        assert actual["frameNumber"] >= 0 and actual["sensorTimestampNanoseconds"] > 0
        assert actual["jpegQuality"] == 81 and actual["jpegOrientation"] == 90
        assert actual["cropRegion"]["right"] > actual["cropRegion"]["left"]
        prefix = native_call(protocol, "fileRead", {"path": photo["path"], "limit": 64})
        assert base64.b64decode(prefix["dataBase64"]).startswith(b"\xff\xd8")
    finally:
        native_call(protocol, "fileManage", {"action": "delete", "path": photo["path"]})
    for fields in (
        {"jpegQuality": 0}, {"jpegQuality": 101}, {"jpegQuality": True}, {"jpegQuality": "81"},
        {"jpegQuality": None}, {"jpegOrientation": 45}, {"jpegOrientation": 360},
        {"zoomPercent": 99}, {"zoomPercent": 10001}, {"zoomPercent": 100.5},
        {"exposureCompensation": 1001}, {"autofocusMode": 6}, {"autofocusMode": -1},
    ):
        native_call(protocol, "cameraCapture", fields, error="validation_failed")
    native_call(protocol, "cameraCapture", {"cameraId": camera_id, "exposureCompensation": 1000},
                error="camera_control_unsupported")
    if capabilities["maximumDigitalZoom"] < 100:
        native_call(protocol, "cameraCapture", {"cameraId": camera_id, "zoomPercent": 10000},
                    error="camera_control_unsupported")
    native_call(protocol, "cameraCapture", {"cameraId": "missing-camera"}, error="native_record_not_found")
