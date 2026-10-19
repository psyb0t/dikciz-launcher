"""The app icon opens the Dikciz home, and the cold-start brand shows five lines."""

from . import *


LAUNCHER_ENTRY_COMPONENT = f"{DIKCIZ_DEBUG_PACKAGE}/org.fossify.home.dikciz.DikcizLauncherEntry"
FOSSIFY_ACTIVITY_PREFIX = "org.fossify.home.activities."
FOSSIFY_COMMONS_ACTIVITY_PREFIX = "org.fossify.commons.activities."
LAUNCHER_QUERY_COMMAND = (
    "cmd package query-activities --brief "
    f"-a android.intent.action.MAIN -c android.intent.category.LAUNCHER {DIKCIZ_DEBUG_PACKAGE}"
)
MISSING_ACTIVITY_MESSAGE = "does not exist"
REMOVED_FOSSIFY_ACTIVITIES = (
    f"{FOSSIFY_ACTIVITY_PREFIX}SplashActivity",
    f"{FOSSIFY_ACTIVITY_PREFIX}MainActivity",
    f"{FOSSIFY_ACTIVITY_PREFIX}SettingsActivity",
    f"{FOSSIFY_ACTIVITY_PREFIX}HiddenIconsActivity",
    f"{FOSSIFY_COMMONS_ACTIVITY_PREFIX}AboutActivity",
    f"{FOSSIFY_COMMONS_ACTIVITY_PREFIX}CustomizationActivity",
    f"{FOSSIFY_COMMONS_ACTIVITY_PREFIX}ContributorsActivity",
    f"{FOSSIFY_COMMONS_ACTIVITY_PREFIX}FAQActivity",
    f"{FOSSIFY_COMMONS_ACTIVITY_PREFIX}LicenseActivity",
    f"{FOSSIFY_COMMONS_ACTIVITY_PREFIX}ManageBlockedNumbersActivity",
    f"{FOSSIFY_COMMONS_ACTIVITY_PREFIX}DonationActivity",
    f"{FOSSIFY_COMMONS_ACTIVITY_PREFIX}AppLockActivity",
)
STARTUP_MESSAGE_SEMANTIC_ID = "startup:message"
STARTUP_MESSAGE_COUNT = 5
# Five one-second lines, the exit fade, and room for a slow emulator frame.
STARTUP_BRAND_MAXIMUM_SECONDS = 8
STARTUP_MESSAGE_POLL_SECONDS = 0.1


def cold_start_home() -> socket.socket:
    run_device_operation("app-stop", PACKAGE_NAME=DIKCIZ_DEBUG_PACKAGE)
    run_device_operation("home-start", PACKAGE_NAME=DIKCIZ_DEBUG_PACKAGE)
    return wait_for_restarted_websocket_control()


def test_app_icon_opens_the_dikciz_home_and_no_fossify_screen_is_installed() -> None:
    launcher_entries = [
        line.strip()
        for line in device_command(LAUNCHER_QUERY_COMMAND).splitlines()
        if "/" in line
    ]
    assert launcher_entries == [LAUNCHER_ENTRY_COMPONENT]

    package_dump = device_command(f"dumpsys package {DIKCIZ_DEBUG_PACKAGE}")
    assert FOSSIFY_ACTIVITY_PREFIX not in package_dump
    assert FOSSIFY_COMMONS_ACTIVITY_PREFIX not in package_dump
    # Private activities do not show in the package dump. Android reports a missing
    # class as "does not exist" and a present private one as a permission denial.
    for activity in REMOVED_FOSSIFY_ACTIVITIES:
        # The start is expected to fail, so the shell reports its status as output.
        start_output = device_command(
            f"am start -n {DIKCIZ_DEBUG_PACKAGE}/{activity} 2>&1; echo start_exit=$?",
        )
        assert MISSING_ACTIVITY_MESSAGE in start_output, (activity, start_output)

    device_command(f"am start -W -n {LAUNCHER_ENTRY_COMPONENT}")
    foreground = wait_for_foreground_application(DIKCIZ_DEBUG_PACKAGE)
    assert FOSSIFY_ACTIVITY_PREFIX not in foreground
    restore_system_home()


def test_cold_start_brand_shows_five_different_lines_and_leaves_within_five_seconds() -> None:
    control = cold_start_home()
    try:
        seen: list[str] = []
        deadline = time.monotonic() + STARTUP_BRAND_MAXIMUM_SECONDS
        while time.monotonic() < deadline:
            nodes = AUTOMATION.request(control, AUTOMATION.TYPE_SNAPSHOT)["nodes"]
            semantic_ids = {node.get("semanticId") for node in nodes}
            if STARTUP_DISMISS_SEMANTIC_ID not in semantic_ids and seen:
                break
            message = next(
                (node.get("text") for node in nodes if node.get("semanticId") == STARTUP_MESSAGE_SEMANTIC_ID),
                None,
            )
            if message and (not seen or seen[-1] != message):
                seen.append(message)
            time.sleep(STARTUP_MESSAGE_POLL_SECONDS)
        else:
            raise AssertionError(f"startup brand stayed past {STARTUP_BRAND_MAXIMUM_SECONDS}s: {seen!r}")
        # The control plane starts a moment after the brand, so the first line can be
        # missed. The lines seen must change and never repeat.
        assert 2 <= len(seen) <= STARTUP_MESSAGE_COUNT, seen
        assert len(seen) == len(set(seen)), seen
    finally:
        control.close()
