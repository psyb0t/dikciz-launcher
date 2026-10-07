"""Shared setup and assertions for typed media-session automation tests."""

from . import *


MEDIA_SESSION_COMMAND_CASES = (
    ("play", False, None, "Fixture media session: track 1, playing, position 12000"),
    ("pause", True, None, "Fixture media session: track 1, paused, position 12000"),
    ("playPause", False, None, "Fixture media session: track 1, playing, position 12000"),
    ("skipNext", False, None, "Fixture media session: track 2, paused, position 12000"),
    ("skipPrevious", False, None, "Fixture media session: track 3, paused, position 12000"),
    ("seekTo", False, 42_000, "Fixture media session: track 1, paused, position 42000"),
)
MEDIA_SESSION_EVENT = "mediaSession"
MEDIA_SESSION_FILTER_INITIAL_STATE = {"packageName": "waiting"}
MEDIA_SESSION_FILTER_SOURCE = (
    'function on_event(event)\n'
    '  if event.type == "mediaSession" then\n'
    '    return { type = "patchState", values = { packageName = event.payload.packageName } }\n'
    '  end\n'
    'end'
)
MEDIA_SESSION_FIXTURE_ACTIVITY = (
    f"{CONFIGURABLE_PROVIDER_FIXTURE_EXPECTED_PACKAGE}{AUTOMATION_MEDIA_SESSION_TEST_ACTIVITY}"
)
MEDIA_SESSION_INITIAL_PLAYING_EXTRA = "fixtureInitialPlaying"
MEDIA_SESSION_MAXIMUM_POLICY_PACKAGES = 64
MEDIA_SESSION_MAXIMUM_POLICY_PACKAGES_EXCEEDED = tuple(
    f"eu.psyb0t.dikciz.fixture.limit{index}"
    for index in range(MEDIA_SESSION_MAXIMUM_POLICY_PACKAGES + 1)
)
MEDIA_SESSION_SECONDARY_FIXTURE_APK_ENVIRONMENT = "DIKCIZ_TEST_MEDIA_SESSION_SECONDARY_FIXTURE_APK"
MEDIA_SESSION_SECONDARY_FIXTURE_APK_PATH = (
    "tests/configurable-widget-provider/app/build-secondary/outputs/apk/debug/app-debug.apk"
)
MEDIA_SESSION_SECONDARY_FIXTURE_EXPECTED_PACKAGE = "eu.psyb0t.dikciz.fixture.secondary"
MEDIA_SESSION_SECONDARY_FIXTURE_PACKAGE_ENVIRONMENT = "DIKCIZ_TEST_MEDIA_SESSION_SECONDARY_FIXTURE_PACKAGE"
MEDIA_SESSION_DELIVERY_REJECTED_EVENT = "automation_delivery_rejected"
MEDIA_SESSION_RATE_LIMIT_COALESCING_KEY = "automation-media-session-rate-limit"
MEDIA_SESSION_RATE_LIMIT_INITIAL_STATE = {"deliveryTimestampMilliseconds": 0}
MEDIA_SESSION_RATE_LIMITED_REASON = "rate_limited"
MEDIA_SESSION_RATE_LIMIT_SOURCE = (
    'function on_event(event)\n'
    '  if event.type == "mediaSession" then\n'
    '    return { type = "patchState", values = { deliveryTimestampMilliseconds = event.timestampMilliseconds } }\n'
    '  end\n'
    'end'
)
MEDIA_SESSION_RATE_LIMIT_STATE_KEY = "deliveryTimestampMilliseconds"


def media_session_configuration(
    original: dict[str, Any],
    source: str,
    capabilities: list[str],
    actions: list[str],
    media_session_packages: list[str],
    minimum_interval_milliseconds: int = 0,
    coalescing_key: str = AUTOMATION_MEDIA_SESSION_COALESCING_KEY,
    subscription_packages: list[str] | None = None,
    initial_state: dict[str, Any] | None = None,
) -> dict[str, Any]:
    package_filter = (
        [CONFIGURABLE_PROVIDER_FIXTURE_EXPECTED_PACKAGE]
        if subscription_packages is None
        else subscription_packages
    )
    configuration = automation_configuration(
        original,
        source,
        AUTOMATION_MEDIA_SESSION_INITIAL_STATE if initial_state is None else initial_state,
        capabilities,
        actions,
        [
            {
                "event": MEDIA_SESSION_EVENT,
                "minimumIntervalMilliseconds": minimum_interval_milliseconds,
                "coalescingKey": coalescing_key,
                "packages": package_filter,
            },
        ],
    )
    configuration["automation"]["policies"][0]["mediaSessionPackages"] = media_session_packages
    return configuration


def media_session_invalid_configurations(original: dict[str, Any]) -> tuple[tuple[str, dict[str, Any]], ...]:
    metadata_without_metadata = media_session_configuration(
        original,
        MEDIA_SESSION_FILTER_SOURCE,
        ["mediaSessionsContent"],
        AUTOMATION_PATCH_STATE_ACTIONS,
        [],
    )
    policy_allowlist_without_action = media_session_configuration(
        original,
        MEDIA_SESSION_FILTER_SOURCE,
        AUTOMATION_MEDIA_SESSION_METADATA_CAPABILITIES,
        AUTOMATION_PATCH_STATE_ACTIONS,
        [CONFIGURABLE_PROVIDER_FIXTURE_EXPECTED_PACKAGE],
    )
    invalid_package_configurations = tuple(
        (
            case_name,
            media_session_configuration(
                original,
                MEDIA_SESSION_FILTER_SOURCE,
                AUTOMATION_MEDIA_SESSION_METADATA_CAPABILITIES,
                AUTOMATION_MEDIA_SESSION_ACTIONS,
                packages,
            ),
        )
        for case_name, packages in (
            ("wildcard", ["*"]),
            ("malformed", ["not a package"]),
            (
                "case_insensitive_duplicate",
                [
                    CONFIGURABLE_PROVIDER_FIXTURE_EXPECTED_PACKAGE,
                    CONFIGURABLE_PROVIDER_FIXTURE_EXPECTED_PACKAGE.upper(),
                ],
            ),
            ("maximum_plus_one", list(MEDIA_SESSION_MAXIMUM_POLICY_PACKAGES_EXCEEDED)),
        )
    )
    return (
        ("content_without_metadata", metadata_without_metadata),
        ("policy_allowlist_without_media_control", policy_allowlist_without_action),
        *invalid_package_configurations,
    )


def media_session_control_source(
    package_name: str,
    command: str,
    position_milliseconds: int | None,
) -> str:
    position_field = (
        ""
        if position_milliseconds is None
        else f", positionMilliseconds = {position_milliseconds}"
    )
    return (
        'function on_event(event)\n'
        '  if event.type == "mediaSession" then\n'
        '    return { type = "mediaControl", '
        f'packageName = "{package_name}", command = "{command}"{position_field} }}\n'
        '  end\n'
        'end'
    )


def start_fixture_media_session(package_name: str, initial_playing: bool = False) -> None:
    stop_fixture_media_session(package_name)
    initial_playing_value = str(initial_playing).lower()
    device_command(
        f"am start -W -n {package_name}/{MEDIA_SESSION_FIXTURE_ACTIVITY} "
        f"--ez {MEDIA_SESSION_INITIAL_PLAYING_EXTRA} {initial_playing_value}",
    )


def stop_fixture_media_session(package_name: str) -> None:
    device_command(f"am force-stop {package_name}")


def secondary_media_session_fixture_package() -> str:
    package_name = os.environ.get(MEDIA_SESSION_SECONDARY_FIXTURE_PACKAGE_ENVIRONMENT, "")
    if package_name == "":
        pytest.fail("the media-session selector did not provide its secondary fixture package")
    assert package_name == MEDIA_SESSION_SECONDARY_FIXTURE_EXPECTED_PACKAGE
    return package_name


def secondary_media_session_fixture_apk() -> str:
    apk_path = os.environ.get(MEDIA_SESSION_SECONDARY_FIXTURE_APK_ENVIRONMENT, "")
    if apk_path == "":
        pytest.fail("the media-session selector did not provide its secondary fixture APK")
    assert apk_path == MEDIA_SESSION_SECONDARY_FIXTURE_APK_PATH
    assert (LAB_ROOT / apk_path).is_file()
    return apk_path


def install_secondary_media_session_fixture() -> str:
    package_name = secondary_media_session_fixture_package()
    run_device_operation("install", APK=secondary_media_session_fixture_apk())
    return package_name


def uninstall_secondary_media_session_fixture(package_name: str) -> None:
    result = device_command(f"pm uninstall {package_name}")
    assert "Success" in result


def wait_for_notification_listener_access(connection: socket.socket) -> dict[str, Any]:
    deadline = time.monotonic() + AUTOMATION_MEDIA_SESSION_STATUS_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        status = AUTOMATION.request(connection, AUTOMATION.TYPE_AUTOMATION_STATUS)
        if status["androidAccess"]["notificationListener"] is True:
            return status
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError("notification-listener consent did not become active")


def wait_for_notification_listener_absence(connection: socket.socket) -> dict[str, Any]:
    deadline = time.monotonic() + AUTOMATION_MEDIA_SESSION_STATUS_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        status = AUTOMATION.request(connection, AUTOMATION.TYPE_AUTOMATION_STATUS)
        if status["androidAccess"]["notificationListener"] is False:
            return status
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError("notification-listener consent did not become inactive")


def wait_for_media_session_status(
    connection: socket.socket,
    expected_active_count: int,
) -> dict[str, Any]:
    deadline = time.monotonic() + AUTOMATION_MEDIA_SESSION_STATUS_TIMEOUT_SECONDS
    observed_count = None
    while time.monotonic() < deadline:
        status = AUTOMATION.request(connection, AUTOMATION.TYPE_AUTOMATION_STATUS)
        observed_count = status.get("activeMediaSessionCount")
        if observed_count == expected_active_count:
            return status
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError(f"media-session count did not become {expected_active_count}: {observed_count!r}")


def media_session_delivery_timestamp(configuration: dict[str, Any]) -> int:
    script = next(
        item
        for item in configuration["scripts"]
        if item["id"] == AUTOMATION_LIVE_SCRIPT_ID
    )
    timestamp = script["state"][MEDIA_SESSION_RATE_LIMIT_STATE_KEY]
    assert isinstance(timestamp, int)
    return timestamp


def media_session_script_state(configuration: dict[str, Any]) -> dict[str, Any]:
    script = next(
        item
        for item in configuration["scripts"]
        if item["id"] == AUTOMATION_LIVE_SCRIPT_ID
    )
    state = script["state"]
    assert isinstance(state, dict)
    return state


def wait_for_media_session_delivery_timestamp(connection: socket.socket) -> int:
    deadline = time.monotonic() + AUTOMATION_MEDIA_SESSION_STATUS_TIMEOUT_SECONDS
    observed_timestamp = 0
    while time.monotonic() < deadline:
        configuration = AUTOMATION.require_configuration(
            AUTOMATION.request(connection, AUTOMATION.TYPE_CONFIG_GET),
        )
        observed_timestamp = media_session_delivery_timestamp(configuration)
        if observed_timestamp > 0:
            return observed_timestamp
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError(f"media-session delivery timestamp did not update: {observed_timestamp}")
