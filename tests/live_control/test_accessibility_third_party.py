"""Cross-app accessibility actions against the installed fixture application."""

import importlib.util
import json

from . import *
from .test_native_data import native_call


COPY_SOURCE_TEXT = "Dikciz fixture café"
PASTE_TARGET_TEXT = "Replace this"
COPY_ACTION = "copy"
PASTE_ACTION = "paste"
FOCUS_ACTION = "focus"
ANDROID_REJECTED = "android_rejected"


def clipboard_action_args(snapshot, node, action):
    return {
        "snapshotId": snapshot["snapshotId"],
        "nodeId": node["nodeId"],
        "action": action,
    }


def check_fixture_clipboard(connection, package_name, mcp_arguments):
    def fixture_snapshot():
        return wait_for_third_party_snapshot(connection, package_name, FIXTURE_TOUCH_COUNT)

    def wait_for_action(text, action):
        deadline = time.monotonic() + ACCESSIBILITY_SERVICE_READY_TIMEOUT_SECONDS
        while time.monotonic() < deadline:
            snapshot = fixture_snapshot()
            node = third_party_visible_node(snapshot, package_name, text)
            if action in node["actions"]:
                return snapshot, node
            time.sleep(ACCESSIBILITY_SNAPSHOT_POLL_SECONDS)
        raise AssertionError(f"fixture field {text!r} did not advertise {action!r}")

    snapshot = fixture_snapshot()
    source = third_party_visible_node(snapshot, package_name, COPY_SOURCE_TEXT)
    assert native_call(
        "http", "accessibilityAction",
        clipboard_action_args(snapshot, source, COPY_ACTION),
    )["outcome"] == ANDROID_REJECTED
    assert AUTOMATION.request(
        connection, AUTOMATION.TYPE_ACCESSIBILITY_ACTION,
        **clipboard_action_args(snapshot, source, FOCUS_ACTION),
    )["outcome"] == ACCESSIBILITY_OUTCOME_EXECUTED
    snapshot, source = wait_for_action(COPY_SOURCE_TEXT, COPY_ACTION)

    session_id = MCP.initialize(mcp_arguments)
    try:
        MCP.notification_initialized(mcp_arguments, session_id)
        result = MCP.require_tool_result(MCP.call_tool(
            mcp_arguments, session_id, ACCESSIBILITY_MCP_ACTION_REQUEST_ID,
            MCP.TOOL_ACCESSIBILITY_ACTION,
            clipboard_action_args(snapshot, source, COPY_ACTION),
        ))
        assert result["outcome"] == ACCESSIBILITY_OUTCOME_EXECUTED
        assert "text" not in result
    finally:
        MCP.delete_session(mcp_arguments, session_id)

    snapshot = fixture_snapshot()
    target = third_party_visible_node(snapshot, package_name, PASTE_TARGET_TEXT)
    assert AUTOMATION.request(
        connection, AUTOMATION.TYPE_ACCESSIBILITY_ACTION,
        **clipboard_action_args(snapshot, target, FOCUS_ACTION),
    )["outcome"] == ACCESSIBILITY_OUTCOME_EXECUTED
    snapshot, target = wait_for_action(PASTE_TARGET_TEXT, PASTE_ACTION)
    arguments = clipboard_action_args(snapshot, target, PASTE_ACTION)
    native_call("http", "accessibilityAction", arguments | {"text": COPY_SOURCE_TEXT},
                error="accessibility_action_text_invalid")
    assert native_call("http", "accessibilityAction", arguments)["outcome"] == ACCESSIBILITY_OUTCOME_EXECUTED
    deadline = time.monotonic() + ACCESSIBILITY_SERVICE_READY_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        updated = fixture_snapshot()
        if any(
            node["viewIdResourceName"] == target["viewIdResourceName"]
            and node["text"] == COPY_SOURCE_TEXT
            and node["visibleToUser"]
            for node in updated["nodes"]
        ):
            return
        time.sleep(ACCESSIBILITY_SNAPSHOT_POLL_SECONDS)
    raise AssertionError("paste did not update the fixture target field")


FIXTURE_TOUCH_COUNT = ACCESSIBILITY_THIRD_PARTY_ACTION_COUNT_MCP + 1
TOUCH_COUNT_TEXT = "External touch count: {}"
TOUCH_PROBE_TAG = "DikcizTouchProbe"
TOUCH_PROBE_RESULT = "completed="
TOUCH_PROBE_TRACE = "accessibility-touch-probe.txt"
PROBE_SWIPE_DURATION_MS = 400
PROBE_SWIPE_INSET_PX = 24
PROBE_BLANK_AREA_OFFSET_PX = 500
PROBE_PINCH_SPACING_PX = 100
PROBE_LONG_HOLD_MS = 5_200
PROBE_DOUBLE_TAP_PRESS_MS = 60
PROBE_MAX_TAP_DRIFT_PX = 12.0
TOUCH_TRACE_TAG = "DikcizTouchTrace"
TOUCH_TRACE_LOG = "accessibility-touch-scorer.txt"
SCORER_TARGET_PX = 160
SCORER_SWIPE_HALF_PX = 260
SCORER_TAPS = 4
SCORER_SWIPES = 4
SCORER_DOUBLE_TAPS = 2
SCORER_SWIPE_MS = 380
SCORER_TAP_MS = 60


def check_fixture_touch(connection, package_name):
    snapshot = wait_for_third_party_snapshot(
        connection, package_name, ACCESSIBILITY_THIRD_PARTY_ACTION_COUNT_MCP,
    )
    third_party_visible_node(snapshot, package_name, TOUCH_COUNT_TEXT.format(0))
    target = third_party_action_node(snapshot, package_name)
    bounds = target["bounds"]
    result = AUTOMATION.request(
        connection, AUTOMATION.TYPE_AUTOMATION_DISPATCH,
        sourceWidgetAddress=ACCESSIBILITY_HTML_SOURCE_WIDGET_ADDRESS,
        actions=[{
            "type": ACCESSIBILITY_GESTURE_ACTION_CAPABILITY,
            "snapshotId": snapshot["snapshotId"],
            "gesture": ACCESSIBILITY_GESTURE_TAP,
            "x": (bounds["left"] + bounds["right"]) // 2,
            "y": (bounds["top"] + bounds["bottom"]) // 2,
            "targetWidth": bounds["right"] - bounds["left"],
            "targetHeight": bounds["bottom"] - bounds["top"],
        }],
    )
    assert result["actions"][0]["outcome"] == ACCESSIBILITY_OUTCOME_EXECUTED
    updated = wait_for_third_party_snapshot(connection, package_name, FIXTURE_TOUCH_COUNT)
    third_party_visible_node(updated, package_name, TOUCH_COUNT_TEXT.format(1))


def check_fixture_touch_probe(connection, package_name):
    pid = device_command(f"pidof {package_name}").strip()
    assert pid.isdecimal(), pid

    def read_probe():
        result = device_command(f"logcat -d --pid={pid} -s {TOUCH_PROBE_TAG}:I")
        return [
            line.split(TOUCH_PROBE_RESULT, 1)[1].split()
            for line in result.splitlines()
            if TOUCH_PROBE_RESULT in line
        ]

    traces = read_probe()
    assert len(traces) == 1, traces
    # A humanized tap may roll a few pixels while the finger settles, never more.
    first = dict(part.split("=", 1) for part in traces[0][1:])
    assert traces[0][0] == "true" and float(first["displacement_px"]) <= PROBE_MAX_TAP_DRIFT_PX, traces

    snapshot = wait_for_third_party_snapshot(connection, package_name, FIXTURE_TOUCH_COUNT)
    bounds = third_party_action_node(snapshot, package_name)["bounds"]
    x, y = center(bounds)
    result = native_call("http", "automationDispatch", {
        "sourceWidgetAddress": ACCESSIBILITY_HTML_SOURCE_WIDGET_ADDRESS,
        "actions": [{
            "type": ACCESSIBILITY_GESTURE_ACTION_CAPABILITY,
            "snapshotId": snapshot["snapshotId"],
            "gesture": "swipe",
            "startX": x,
            "startY": bounds["bottom"] + PROBE_SWIPE_INSET_PX,
            "endX": x,
            "endY": y,
            "durationMilliseconds": PROBE_SWIPE_DURATION_MS,
        }],
    })
    assert result["actions"][0]["outcome"] == ACCESSIBILITY_OUTCOME_EXECUTED
    deadline = time.monotonic() + ACCESSIBILITY_SERVICE_READY_TIMEOUT_SECONDS
    while len(traces) < 2 and time.monotonic() < deadline:
        traces = read_probe()
        if len(traces) < 2:
            time.sleep(ACCESSIBILITY_SNAPSHOT_POLL_SECONDS)
    assert len(traces) == 2 and traces[1][0] == "true", traces
    fields = dict(part.split("=", 1) for part in traces[1][1:])
    assert float(fields["displacement_px"]) > 0, fields
    assert int(fields["move_samples"]) > 0, fields
    assert int(fields["duration_ms"]) > 0, fields
    assert fields["android_verification"] in {"verified", "unverified", "unavailable"}, fields
    blank_y = y + PROBE_BLANK_AREA_OFFSET_PX
    spacing = PROBE_PINCH_SPACING_PX
    point = {"x": x, "y": blank_y}
    moving_fingers = {
        "startX": x - spacing, "startY": blank_y,
        "endX": x - spacing // 2, "endY": blank_y,
        "secondStartX": x + spacing, "secondStartY": blank_y,
        "secondEndX": x + spacing // 2, "secondEndY": blank_y,
    }

    # These checks pin the exact gesture each request names, so they opt out of humanizing.
    def dispatch(gesture, duration, coordinates, protocol="http", error=None):
        current = wait_for_third_party_snapshot(connection, package_name, FIXTURE_TOUCH_COUNT)
        result = native_call(protocol, "automationDispatch", {
            "sourceWidgetAddress": ACCESSIBILITY_HTML_SOURCE_WIDGET_ADDRESS,
            "actions": [{
                "type": ACCESSIBILITY_GESTURE_ACTION_CAPABILITY,
                "snapshotId": current["snapshotId"],
                "gesture": gesture,
                "durationMilliseconds": duration,
                "humanize": False,
                **coordinates,
            }],
        }, error=error)
        return result if error else result["actions"][0]["outcome"]

    assert dispatch("hold", 500, point | {"endX": x}, error="validation_failed")
    assert dispatch("twoFingerSwipe", PROBE_SWIPE_DURATION_MS, {}, error="validation_failed")
    assert dispatch("twoFingerSwipe", PROBE_SWIPE_DURATION_MS, moving_fingers | {
        "secondStartX": moving_fingers["startX"],
    }) == "accessibility_gesture_invalid"
    assert dispatch("doubleTap", 101, point) == "accessibility_gesture_invalid"
    assert dispatch("hold", 500, point | {"y": 10_000}) == "accessibility_gesture_invalid"
    assert len(read_probe()) == len(traces)
    assert dispatch("hold", PROBE_LONG_HOLD_MS, point) == ACCESSIBILITY_OUTCOME_EXECUTED
    assert dispatch("doubleTap", PROBE_DOUBLE_TAP_PRESS_MS, point) == ACCESSIBILITY_OUTCOME_EXECUTED
    assert dispatch("twoFingerSwipe", PROBE_SWIPE_DURATION_MS, moving_fingers) == ACCESSIBILITY_OUTCOME_EXECUTED
    assert dispatch("doubleTap", PROBE_DOUBLE_TAP_PRESS_MS, point, protocol="websocket") == ACCESSIBILITY_OUTCOME_EXECUTED
    spreading_fingers = moving_fingers | {
        "startX": moving_fingers["endX"],
        "endX": moving_fingers["startX"],
        "secondStartX": moving_fingers["secondEndX"],
        "secondEndX": moving_fingers["secondStartX"],
    }
    assert dispatch("twoFingerSwipe", PROBE_SWIPE_DURATION_MS, spreading_fingers, protocol="mcp") == ACCESSIBILITY_OUTCOME_EXECUTED
    expected_count = 9
    deadline = time.monotonic() + ACCESSIBILITY_SERVICE_READY_TIMEOUT_SECONDS
    while len(traces) < expected_count and time.monotonic() < deadline:
        traces = read_probe()
        if len(traces) < expected_count:
            time.sleep(ACCESSIBILITY_SNAPSHOT_POLL_SECONDS)
    assert len(traces) == expected_count, traces
    assert all(trace[0] == "true" for trace in traces), traces
    hold, double_first, double_second, pinch, ws_first, ws_second, spread = (
        dict(part.split("=", 1) for part in trace[1:]) for trace in traces[2:]
    )
    assert int(hold["duration_ms"]) >= 5_000 and hold["displacement_px"] == "0.0", hold
    assert all(int(tap["duration_ms"]) == PROBE_DOUBLE_TAP_PRESS_MS for tap in (double_first, double_second, ws_first, ws_second))
    for gesture in (pinch, spread):
        assert gesture["max_pointers"] == "2" and int(gesture["move_samples"]) > 0, gesture
    assert float(pinch["span_change_px"]) < 0, pinch
    assert float(spread["span_change_px"]) > 0, spread
    (ARTIFACT_DIRECTORY / TOUCH_PROBE_TRACE).write_text(
        "\n".join(TOUCH_PROBE_RESULT + " ".join(trace) for trace in traces) + "\n",
        encoding="utf-8",
    )


def load_touch_scorer():
    spec = importlib.util.spec_from_file_location("score_dikciz_touch", LAB_ROOT / "scripts/score-dikciz-touch.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def check_touch_scorer(connection, package_name):
    """The same session dispatched exact and humanized: the scorer flags only the exact one."""
    pid = device_command(f"pidof {package_name}").strip()
    scorer = load_touch_scorer()

    def trace_lines():
        return [
            line for line in device_command(f"logcat -d --pid={pid} -s {TOUCH_TRACE_TAG}:I").splitlines()
            if "samples=" in line
        ]

    snapshot = wait_for_third_party_snapshot(connection, package_name, FIXTURE_TOUCH_COUNT)
    bounds = third_party_action_node(snapshot, package_name)["bounds"]
    x, y = center(bounds)
    blank_y = y + PROBE_BLANK_AREA_OFFSET_PX
    point = {"x": x, "y": blank_y}
    swipe = {
        "startX": x - SCORER_SWIPE_HALF_PX, "startY": blank_y,
        "endX": x + SCORER_SWIPE_HALF_PX, "endY": blank_y - SCORER_SWIPE_HALF_PX // 4,
    }

    def session(humanize):
        before = len(trace_lines())
        fields = {"humanize": humanize}
        tap = point | ({"targetWidth": SCORER_TARGET_PX, "targetHeight": SCORER_TARGET_PX} if humanize else {})
        gestures = (
            [("tap", tap)] * SCORER_TAPS
            + [("swipe", swipe)] * SCORER_SWIPES
            + [("doubleTap", tap)] * SCORER_DOUBLE_TAPS
        )
        for gesture, coordinates in gestures:
            current = wait_for_third_party_snapshot(connection, package_name, FIXTURE_TOUCH_COUNT)
            duration = {} if humanize else {
                "durationMilliseconds": SCORER_SWIPE_MS if gesture == "swipe" else SCORER_TAP_MS,
            }
            result = native_call("http", "automationDispatch", {
                "sourceWidgetAddress": ACCESSIBILITY_HTML_SOURCE_WIDGET_ADDRESS,
                "actions": [{
                    "type": ACCESSIBILITY_GESTURE_ACTION_CAPABILITY,
                    "snapshotId": current["snapshotId"],
                    "gesture": gesture,
                    **fields, **duration, **coordinates,
                }],
            })
            assert result["actions"][0]["outcome"] == ACCESSIBILITY_OUTCOME_EXECUTED, result
        expected = before + SCORER_TAPS + SCORER_SWIPES + 2 * SCORER_DOUBLE_TAPS
        deadline = time.monotonic() + ACCESSIBILITY_SERVICE_READY_TIMEOUT_SECONDS
        lines = trace_lines()
        while len(lines) < expected and time.monotonic() < deadline:
            time.sleep(ACCESSIBILITY_SNAPSHOT_POLL_SECONDS)
            lines = trace_lines()
        assert len(lines) == expected, lines
        return lines[before:]

    exact = session(False)
    humanized = session(True)
    exact_report = scorer.score(scorer.parse(exact))
    human_report = scorer.score(scorer.parse(humanized))
    (ARTIFACT_DIRECTORY / TOUCH_TRACE_LOG).write_text(
        "# exact\n" + "\n".join(exact) + "\n# humanized\n" + "\n".join(humanized) + "\n"
        + json.dumps({"exact": exact_report, "humanized": human_report}, indent=2) + "\n",
        encoding="utf-8",
    )
    assert exact_report["verdict"] == "scripted", exact_report
    assert human_report["verdict"] == "pass", human_report


def third_party_component(package_name: str) -> str:
    return f"{package_name}/{ACCESSIBILITY_THIRD_PARTY_ACTIVITY_CLASS}"


def third_party_action_count_text(action_count: int) -> str:
    return ACCESSIBILITY_THIRD_PARTY_ACTION_COUNT_FORMAT.format(action_count)


def third_party_visible_node(
    snapshot: dict[str, Any],
    package_name: str,
    text: str,
) -> dict[str, Any]:
    for node in snapshot["nodes"]:
        if (
            node["packageName"] == package_name
            and node["visibleToUser"]
            and node["text"] == text
        ):
            return node
    raise AssertionError(f"fixture snapshot did not expose visible text: {text!r}")


def third_party_action_node(
    snapshot: dict[str, Any],
    package_name: str,
) -> dict[str, Any]:
    node = third_party_visible_node(
        snapshot,
        package_name,
        ACCESSIBILITY_THIRD_PARTY_ACTION_TEXT,
    )
    assert node["clickable"]
    assert node["enabled"]
    assert ACCESSIBILITY_ACTION_CLICK in node["actions"]
    return node


def wait_for_third_party_snapshot(
    connection: socket.socket,
    package_name: str,
    action_count: int,
) -> dict[str, Any]:
    expected_status = third_party_action_count_text(action_count)
    deadline = time.monotonic() + ACCESSIBILITY_SERVICE_READY_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        snapshot = AUTOMATION.request(connection, AUTOMATION.TYPE_ACCESSIBILITY_SNAPSHOT)
        if snapshot[ACCESSIBILITY_PACKAGE_NAME_KEY] == package_name and any(
            node["packageName"] == package_name
            and node["visibleToUser"]
            and node["text"] == expected_status
            for node in snapshot["nodes"]
        ):
            return snapshot
        time.sleep(ACCESSIBILITY_SNAPSHOT_POLL_SECONDS)
    visible_text = [node["text"] for node in snapshot["nodes"] if node["visibleToUser"]]
    raise AssertionError(f"fixture did not reach {expected_status!r}; visible text: {visible_text!r}")


def wait_for_mcp_third_party_snapshot(
    arguments: argparse.Namespace,
    session_id: str,
    package_name: str,
    action_count: int,
) -> dict[str, Any]:
    expected_status = third_party_action_count_text(action_count)
    deadline = time.monotonic() + ACCESSIBILITY_SERVICE_READY_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        snapshot = MCP.require_tool_result(
            MCP.call_tool(
                arguments,
                session_id,
                ACCESSIBILITY_MCP_SNAPSHOT_REQUEST_ID,
                MCP.TOOL_ACCESSIBILITY_SNAPSHOT,
            ),
        )
        if snapshot[ACCESSIBILITY_PACKAGE_NAME_KEY] != package_name:
            time.sleep(ACCESSIBILITY_SNAPSHOT_POLL_SECONDS)
            continue
        third_party_visible_node(snapshot, package_name, expected_status)
        return snapshot
    raise AssertionError("MCP did not expose the third-party fixture")


def assert_third_party_action_result(
    result: dict[str, Any],
    target: dict[str, Any],
    snapshot: dict[str, Any],
) -> None:
    assert result == {
        "outcome": ACCESSIBILITY_OUTCOME_EXECUTED,
        AUTOMATION.KEY_ACTION: ACCESSIBILITY_ACTION_CLICK,
        AUTOMATION.KEY_NODE_ID: target[AUTOMATION.KEY_NODE_ID],
        AUTOMATION.KEY_SNAPSHOT_ID: snapshot[AUTOMATION.KEY_SNAPSHOT_ID],
    }


def test_accessibility_service_acts_on_a_real_third_party_window_through_both_control_planes(
    websocket_control: socket.socket,
    installed_configurable_provider_fixture: None,
) -> None:
    package_name = configurable_provider_fixture_package()
    try:
        emulator_dikciz_accessibility_set(True)
        device_command(f"am force-stop {package_name}")
        device_command(f"am start -W -n {third_party_component(package_name)}")
        wait_for_foreground_application(package_name)

        websocket_snapshot = wait_for_third_party_snapshot(
            websocket_control,
            package_name,
            ACCESSIBILITY_THIRD_PARTY_ACTION_COUNT_INITIAL,
        )
        websocket_target = third_party_action_node(websocket_snapshot, package_name)
        websocket_result = AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_ACCESSIBILITY_ACTION,
            action=ACCESSIBILITY_ACTION_CLICK,
            nodeId=websocket_target[AUTOMATION.KEY_NODE_ID],
            snapshotId=websocket_snapshot[AUTOMATION.KEY_SNAPSHOT_ID],
        )
        assert_third_party_action_result(
            websocket_result,
            websocket_target,
            websocket_snapshot,
        )
        wait_for_third_party_snapshot(
            websocket_control,
            package_name,
            ACCESSIBILITY_THIRD_PARTY_ACTION_COUNT_WEBSOCKET,
        )

        arguments = argparse.Namespace(
            host=AUTOMATION_HOST,
            port=MCP_PORT,
            expected_control_port=DEVICE_MCP_PORT,
            mutate=False,
        )
        session_id = MCP.initialize(arguments)
        try:
            MCP.notification_initialized(arguments, session_id)
            mcp_snapshot = wait_for_mcp_third_party_snapshot(
                arguments,
                session_id,
                package_name,
                ACCESSIBILITY_THIRD_PARTY_ACTION_COUNT_WEBSOCKET,
            )
            mcp_target = third_party_action_node(mcp_snapshot, package_name)
            mcp_result = MCP.require_tool_result(
                MCP.call_tool(
                    arguments,
                    session_id,
                    ACCESSIBILITY_MCP_ACTION_REQUEST_ID,
                    MCP.TOOL_ACCESSIBILITY_ACTION,
                    {
                        AUTOMATION.KEY_ACTION: ACCESSIBILITY_ACTION_CLICK,
                        AUTOMATION.KEY_NODE_ID: mcp_target[AUTOMATION.KEY_NODE_ID],
                        AUTOMATION.KEY_SNAPSHOT_ID: mcp_snapshot[AUTOMATION.KEY_SNAPSHOT_ID],
                    },
                ),
            )
            assert_third_party_action_result(mcp_result, mcp_target, mcp_snapshot)
        finally:
            MCP.delete_session(arguments, session_id)

        wait_for_third_party_snapshot(
            websocket_control,
            package_name,
            ACCESSIBILITY_THIRD_PARTY_ACTION_COUNT_MCP,
        )
        run_device_operation("screenshot")
        screenshot_path = ARTIFACT_DIRECTORY / "screenshot.png"
        third_party_screenshot_path = (
            ARTIFACT_DIRECTORY / ACCESSIBILITY_THIRD_PARTY_ACTION_SCREENSHOT
        )
        shutil.copyfile(screenshot_path, third_party_screenshot_path)
        assert third_party_screenshot_path.stat().st_size > 0
        _, stale_error = AUTOMATION.request_error_with_id(
            websocket_control,
            AUTOMATION.TYPE_ACCESSIBILITY_ACTION,
            action=ACCESSIBILITY_ACTION_CLICK,
            nodeId=mcp_target[AUTOMATION.KEY_NODE_ID],
            snapshotId=mcp_snapshot[AUTOMATION.KEY_SNAPSHOT_ID],
        )
        assert stale_error[AUTOMATION.KEY_CODE] == ACCESSIBILITY_ERROR_SNAPSHOT_STALE
        expiration_snapshot = wait_for_third_party_snapshot(
            websocket_control,
            package_name,
            ACCESSIBILITY_THIRD_PARTY_ACTION_COUNT_MCP,
        )
        time.sleep(ACCESSIBILITY_SNAPSHOT_EXPIRY_WAIT_SECONDS)
        _, expired_error = AUTOMATION.request_error_with_id(
            websocket_control,
            AUTOMATION.TYPE_ACCESSIBILITY_ACTION,
            action=ACCESSIBILITY_ACTION_CLICK,
            nodeId=mcp_target[AUTOMATION.KEY_NODE_ID],
            snapshotId=expiration_snapshot[AUTOMATION.KEY_SNAPSHOT_ID],
        )
        assert expired_error[AUTOMATION.KEY_CODE] == ACCESSIBILITY_ERROR_SNAPSHOT_EXPIRED
        check_fixture_touch(websocket_control, package_name)
        check_fixture_touch_probe(websocket_control, package_name)
        check_touch_scorer(websocket_control, package_name)
        check_fixture_clipboard(websocket_control, package_name, arguments)
        run_device_operation("screenshot")
        shutil.copyfile(
            ARTIFACT_DIRECTORY / "screenshot.png",
            ARTIFACT_DIRECTORY / "accessibility-input-paste.png",
        )
    finally:
        try:
            emulator_dikciz_accessibility_set(False)
        finally:
            restore_system_home()
