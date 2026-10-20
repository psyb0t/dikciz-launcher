"""Android Back dismisses the drawer in either navigation mode."""

import time

from . import *
from .test_app_drawer import open_drawer_through_control


NAVIGATION_OVERLAY_PREFIX = "com.android.internal.systemui.navbar."
NAVIGATION_MODES = ("threebutton", "gestural")
NAVIGATION_MODE_VALUES = {"threebutton": 0, "gestural": 2}
NAVIGATION_SETTLE_SECONDS = 2


@pytest.mark.parametrize("navigation", NAVIGATION_MODES)
def test_launcher_regression_back_callback_closes_drawer(websocket_control, navigation):
    connection = None
    try:
        device_command(f"cmd overlay enable-exclusive --category {NAVIGATION_OVERLAY_PREFIX}{navigation}")
        device_command(f"settings put secure navigation_mode {NAVIGATION_MODE_VALUES[navigation]}")
        time.sleep(NAVIGATION_SETTLE_SECONDS)
        connection = wait_for_restarted_websocket_control()
        for _ in range(2):
            open_drawer_through_control()
            activity = device_command(f"dumpsys activity {DIKCIZ_DEBUG_PACKAGE}")
            (ARTIFACT_DIRECTORY / f"back-{navigation}-activity.txt").write_text(activity)
            device_command("input keyevent KEYCODE_BACK")
            wait_for_snapshot_node(connection, APP_DRAWER_SEARCH_SEMANTIC_ID, should_exist=False)
            wait_for_physical_description_bounds(FIXTURE_WIDGET_TITLES["welcome"])
        run_device_operation("screenshot")
    finally:
        diagnostics = device_command("dumpsys activity service com.android.systemui/.SystemUIService com.android.systemui.navigationbar.NavigationBarControllerImpl")
        (ARTIFACT_DIRECTORY / f"back-{navigation}-systemui.txt").write_text(diagnostics)
        run_device_operation("screenshot")
        screenshot = ARTIFACT_DIRECTORY / "screenshot.png"
        (ARTIFACT_DIRECTORY / f"back-{navigation}.png").write_bytes(screenshot.read_bytes())
        if connection is not None:
            connection.close()
        device_command("settings put secure navigation_mode 0")
        device_command(f"cmd overlay enable-exclusive --category {NAVIGATION_OVERLAY_PREFIX}threebutton")
        time.sleep(NAVIGATION_SETTLE_SECONDS)
