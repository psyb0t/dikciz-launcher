"""Bounded media-volume automation on the isolated Android emulator."""

from . import *


ACTION_MEDIA_VOLUME = "mediaVolume"
ACTION_PATCH_STATE = "patchState"
AUTOMATION_ACTION_COMPLETED_EVENT = "automation_action_completed"
AUTOMATION_ACTION_REJECTED_EVENT = "automation_action_rejected"
AUTOMATION_MEDIA_VOLUME_CAPABILITY = "battery"
AUTOMATION_MEDIA_VOLUME_BASELINE_PERCENT = 0
AUTOMATION_MEDIA_VOLUME_BATTERY_LEVEL = 37
AUTOMATION_MEDIA_VOLUME_EVENT = "battery"
AUTOMATION_MEDIA_VOLUME_PERCENT = 50
MEDIA_VOLUME_MUTABLE_ACCESS_KEY = "mediaVolumeMutable"
MEDIA_VOLUME_MINIMUM_INDEX = 0
MEDIA_VOLUME_PERCENT_SCALE = 100
MEDIA_VOLUME_QUERY_COMMAND = "cmd media_session volume --stream 3 --get"
MEDIA_VOLUME_ROUNDING_OFFSET = MEDIA_VOLUME_PERCENT_SCALE // 2
MEDIA_VOLUME_RESULT_PATTERN = re.compile(
    r"\[V\] volume is (?P<index>\d+) in range \[0\.\.(?P<maximum>\d+)\]",
)


def media_volume_source(level_percent: int) -> str:
    return (
        'function on_event(event)\n'
        f'  if event.type == "{AUTOMATION_MEDIA_VOLUME_EVENT}" and '
        f'event.payload.level == {AUTOMATION_MEDIA_VOLUME_BATTERY_LEVEL} then\n'
        f'    return {{ type = "setMediaVolume", levelPercent = {level_percent} }}\n'
        '  end\n'
        'end'
    )


AUTOMATION_MEDIA_VOLUME_SOURCE = media_volume_source(AUTOMATION_MEDIA_VOLUME_PERCENT)


def media_volume_configuration(configuration: dict, source: str) -> dict:
    return automation_configuration(
        configuration,
        source,
        {},
        [AUTOMATION_MEDIA_VOLUME_CAPABILITY],
        [ACTION_MEDIA_VOLUME],
        [
            {
                "event": AUTOMATION_MEDIA_VOLUME_EVENT,
                "minimumIntervalMilliseconds": 0,
                "coalescingKey": AUTOMATION_MEDIA_VOLUME_EVENT,
            },
        ],
    )


def media_volume() -> tuple[int, int]:
    matches = list(MEDIA_VOLUME_RESULT_PATTERN.finditer(device_command(MEDIA_VOLUME_QUERY_COMMAND)))
    assert len(matches) == 1
    match = matches[0]
    return int(match.group("index")), int(match.group("maximum"))


def wait_for_media_volume(expected_index: int, expected_maximum: int) -> None:
    deadline = time.monotonic() + CONFIGURATION_SAVE_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        index, maximum = media_volume()
        if index == expected_index and maximum == expected_maximum:
            return
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError(
        f"media volume did not become {expected_index} of {expected_maximum}: {media_volume()}",
    )


def media_volume_percent_for_index(index: int, maximum: int) -> int:
    matches = [
        percent
        for percent in range(MEDIA_VOLUME_PERCENT_SCALE + 1)
        if (maximum * percent + MEDIA_VOLUME_ROUNDING_OFFSET) // MEDIA_VOLUME_PERCENT_SCALE
        == index
    ]
    assert matches, f"no percentage maps to media volume {index} of {maximum}"
    return matches[0]


def wait_for_media_volume_action(record_count: int, event: str, outcome: str) -> None:
    deadline = time.monotonic() + CONFIGURATION_SAVE_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        records = device_log_records()[record_count:]
        if any(
            record.get("event") == event
            and record.get("event_type") == AUTOMATION_MEDIA_VOLUME_EVENT
            and record.get("action_type") == ACTION_MEDIA_VOLUME
            and record.get("outcome") == outcome
            for record in records
        ):
            return
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError(f"media-volume action did not report {event}: {outcome}")


def test_automation_media_volume_is_policy_gated_and_reads_back_the_real_emulator_index(
    websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    original_index, maximum_index = media_volume()
    restoration_percent = media_volume_percent_for_index(original_index, maximum_index)
    expected_index = (
        maximum_index * AUTOMATION_MEDIA_VOLUME_PERCENT + MEDIA_VOLUME_ROUNDING_OFFSET
    ) // MEDIA_VOLUME_PERCENT_SCALE
    assert expected_index != MEDIA_VOLUME_MINIMUM_INDEX
    baseline_configuration = media_volume_configuration(
        original,
        media_volume_source(AUTOMATION_MEDIA_VOLUME_BASELINE_PERCENT),
    )
    denied_configuration = automation_configuration(
        original,
        AUTOMATION_MEDIA_VOLUME_SOURCE,
        {},
        [AUTOMATION_MEDIA_VOLUME_CAPABILITY],
        [ACTION_PATCH_STATE],
        [
            {
                "event": AUTOMATION_MEDIA_VOLUME_EVENT,
                "minimumIntervalMilliseconds": 0,
                "coalescingKey": AUTOMATION_MEDIA_VOLUME_EVENT,
            },
        ],
    )
    allowed_configuration = media_volume_configuration(
        original,
        AUTOMATION_MEDIA_VOLUME_SOURCE,
    )
    try:
        status = AUTOMATION.request(websocket_control, AUTOMATION.TYPE_AUTOMATION_STATUS)
        assert ACTION_MEDIA_VOLUME in status["actions"]
        assert status["androidAccess"][MEDIA_VOLUME_MUTABLE_ACCESS_KEY] is True

        device_command("dumpsys battery reset")
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=baseline_configuration,
        )
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
        baseline_record_count = len(device_log_records())
        device_command(f"dumpsys battery set level {AUTOMATION_MEDIA_VOLUME_BATTERY_LEVEL}")
        wait_for_media_volume(MEDIA_VOLUME_MINIMUM_INDEX, maximum_index)
        wait_for_media_volume_action(
            baseline_record_count,
            AUTOMATION_ACTION_COMPLETED_EVENT,
            "executed",
        )

        device_command("dumpsys battery reset")
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=denied_configuration,
        )
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
        denied_record_count = len(device_log_records())
        device_command(f"dumpsys battery set level {AUTOMATION_MEDIA_VOLUME_BATTERY_LEVEL}")
        wait_for_media_volume_action(
            denied_record_count,
            AUTOMATION_ACTION_REJECTED_EVENT,
            "capability_denied",
        )
        assert media_volume() == (MEDIA_VOLUME_MINIMUM_INDEX, maximum_index)

        device_command("dumpsys battery reset")
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=allowed_configuration,
        )
        time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
        allowed_record_count = len(device_log_records())
        device_command(f"dumpsys battery set level {AUTOMATION_MEDIA_VOLUME_BATTERY_LEVEL}")
        wait_for_media_volume(expected_index, maximum_index)
        wait_for_media_volume_action(
            allowed_record_count,
            AUTOMATION_ACTION_COMPLETED_EVENT,
            "executed",
        )
    finally:
        try:
            device_command("dumpsys battery reset")
            restoration_configuration = media_volume_configuration(
                original,
                media_volume_source(restoration_percent),
            )
            AUTOMATION.request(
                websocket_control,
                AUTOMATION.TYPE_CONFIG_REPLACE,
                config=restoration_configuration,
            )
            time.sleep(CONFIGURATION_RELOAD_SETTLE_SECONDS)
            restore_record_count = len(device_log_records())
            device_command(f"dumpsys battery set level {AUTOMATION_MEDIA_VOLUME_BATTERY_LEVEL}")
            wait_for_media_volume(original_index, maximum_index)
            wait_for_media_volume_action(
                restore_record_count,
                AUTOMATION_ACTION_COMPLETED_EVENT,
                "executed",
            )
        finally:
            try:
                device_command("dumpsys battery reset")
            finally:
                AUTOMATION.request(
                    websocket_control,
                    AUTOMATION.TYPE_CONFIG_REPLACE,
                    config=original,
                )
