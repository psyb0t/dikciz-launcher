"""Live custom-event delivery across Lua, HTML, and native notifications."""

from . import *


CUSTOM_EVENT_FROM_HTML = "fixture.html.ready"
CUSTOM_EVENT_FROM_LUA = "fixture.lua.ping"
CUSTOM_EVENT_SCRIPT_ID = AUTOMATION_LIVE_SCRIPT_ID
CUSTOM_EVENT_WIDGET_ID = "custom-event-bridge"
CUSTOM_EVENT_WIDGET_ADDRESS = f"1H1V-{CUSTOM_EVENT_WIDGET_ID}"
CUSTOM_EVENT_HTML_MESSAGE = "HTML emitted this event"
CUSTOM_EVENT_LUA_MESSAGE = "Lua emitted this event"
CUSTOM_EVENT_NOTIFICATION_TAG = "dikciz-custom-event-test"
CUSTOM_EVENT_NOTIFICATION_TITLE = "Dikciz event-driven notification"
CUSTOM_EVENT_NOTIFICATION_TEXT = "Delivered through the notification listener"

CUSTOM_EVENT_HTML = "<article class=\"fixture-card\"><h2>Custom events</h2><p id=\"event-status\">Waiting for Lua.</p></article>"
CUSTOM_EVENT_CSS = ".fixture-card{box-sizing:border-box;min-height:100%;padding:16px;border:1px solid #53667e;border-radius:18px;background:#172232;color:#edf3fb;font:15px/1.45 system-ui,sans-serif}.fixture-card h2,.fixture-card p{margin:0}.fixture-card p{margin-top:10px;color:#c7d3e2}"
CUSTOM_EVENT_JAVASCRIPT = """(() => {
  const status = document.getElementById('event-status');
  const emittedKey = 'dikciz-custom-event-bridge-emitted';
  window.addEventListener('dikciz-ready', async () => {
    window.dikciz.onEvent(event => {
      if (event.type !== 'fixture.lua.ping') return;
      status.textContent = event.payload.message;
      selfWidget.patchState({luaMessage: event.payload.message});
    });
    if (sessionStorage.getItem(emittedKey)) return;
    sessionStorage.setItem(emittedKey, 'true');
    await window.dikciz.emitEvent('fixture.html.ready', {message: 'HTML emitted this event'});
  });
})();"""
CUSTOM_EVENT_LUA_SOURCE = """function on_event(event)
  if event.type == "fixture.html.ready" then
    return {
      type = "patchState",
      values = { htmlMessage = event.payload.message }
    }
  end
  if event.type == "manual" then
    return {
      type = "emitEvent",
      event = "fixture.lua.ping",
      payload = { message = "Lua emitted this event" }
    }
  end
end"""


def custom_event_configuration(original: dict[str, Any]) -> dict[str, Any]:
    configured = copy.deepcopy(original)
    configuration_page(configured, FIXTURE_HOME_PAGE_ID)["widgets"].append(
        {
            "id": CUSTOM_EVENT_WIDGET_ID,
            "type": "html",
            "title": "Custom event bridge",
            "html": CUSTOM_EVENT_HTML,
            "css": CUSTOM_EVENT_CSS,
            "javascript": CUSTOM_EVENT_JAVASCRIPT,
            "state": {},
            "eventSubscriptions": [
                {
                    "event": CUSTOM_EVENT_FROM_LUA,
                    "minimumIntervalMilliseconds": 0,
                },
            ],
            "enabled": True,
            "cell": {"column": 0, "row": 2, "columnSpan": 4, "rowSpan": 1},
        },
    )
    configured["scripts"].append(
        {
            "id": CUSTOM_EVENT_SCRIPT_ID,
            "title": "Custom event fixture",
            "enabled": True,
            "apiVersion": LUA_SCRIPT_API_VERSION,
            "source": CUSTOM_EVENT_LUA_SOURCE,
            "state": {},
        },
    )
    configured["automation"] = {
        "apiVersion": 1,
        "policies": [
            {
                "id": AUTOMATION_LIVE_POLICY_ID,
                "title": "Custom event fixture policy",
                "enabled": True,
                "capabilities": ["manualTrigger"],
                "actions": ["emitEvent", "patchState"],
            },
        ],
        "scripts": [
            {
                "scriptId": CUSTOM_EVENT_SCRIPT_ID,
                "policyId": AUTOMATION_LIVE_POLICY_ID,
                "enabled": True,
                "subscriptions": [
                    {"event": "manual", "minimumIntervalMilliseconds": 0},
                    {"event": CUSTOM_EVENT_FROM_HTML, "minimumIntervalMilliseconds": 0},
                ],
            },
        ],
    }
    return configured


def wait_for_widget_state_value(
    connection: socket.socket,
    widget_id: str,
    state_key: str,
    expected_value: Any,
) -> dict[str, Any]:
    deadline = time.monotonic() + CONFIGURATION_SAVE_TIMEOUT_SECONDS
    observed_value: Any = None
    while time.monotonic() < deadline:
        configuration = AUTOMATION.require_configuration(
            AUTOMATION.request(connection, AUTOMATION.TYPE_CONFIG_GET),
        )
        widget = configuration_widget(configuration, widget_id)
        state = widget.get("state")
        if isinstance(state, dict):
            observed_value = state.get(state_key)
        if observed_value == expected_value:
            return configuration
        time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
    raise AssertionError(
        f"widget state did not update: {widget_id}.{state_key} observed={observed_value!r}",
    )


def default_command_deck(configuration: dict[str, Any]) -> dict[str, Any]:
    return configuration_widget(configuration, "command-deck")


def test_html_lua_and_notification_events_flow_through_the_live_event_bus(
    websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    configured = custom_event_configuration(original)
    try:
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=configured,
        )
        wait_for_automation_script_state(
            websocket_control,
            CUSTOM_EVENT_SCRIPT_ID,
            "htmlMessage",
            CUSTOM_EVENT_HTML_MESSAGE,
        )
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_AUTOMATION_TRIGGER,
            scriptId=CUSTOM_EVENT_SCRIPT_ID,
        )
        received = wait_for_widget_state_value(
            websocket_control,
            CUSTOM_EVENT_WIDGET_ID,
            "luaMessage",
            CUSTOM_EVENT_LUA_MESSAGE,
        )
        widget = configuration_widget(received, CUSTOM_EVENT_WIDGET_ID)
        assert widget["eventSubscriptions"] == [
            {"event": CUSTOM_EVENT_FROM_LUA, "minimumIntervalMilliseconds": 0},
        ]

        _, rejected = AUTOMATION.request_error_with_id(
            websocket_control,
            AUTOMATION.TYPE_AUTOMATION_DISPATCH,
            sourceWidgetAddress=CUSTOM_EVENT_WIDGET_ADDRESS,
            actions=[{"type": "emitEvent", "event": "", "payload": {}}],
        )
        assert rejected["code"] == "validation_failed"
    finally:
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=original,
        )
    assert_default_notification_deck_tracks_real_events(websocket_control)


def assert_default_notification_deck_tracks_real_events(
    websocket_control: socket.socket,
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    try:
        default = AUTOMATION.require_configuration(
            AUTOMATION.request(websocket_control, AUTOMATION.TYPE_RESET),
        )
        subscriptions = default_command_deck(default)["eventSubscriptions"]
        assert {subscription["event"] for subscription in subscriptions} >= {
            "notificationPosted",
            "notificationRemoved",
            "notification.digest.updated",
        }
        device_command(f"cmd notification allow_listener {AUTOMATION_NOTIFICATION_COMPONENT}")
        deadline = time.monotonic() + CONFIGURATION_SAVE_TIMEOUT_SECONDS
        while time.monotonic() < deadline:
            status = AUTOMATION.request(websocket_control, AUTOMATION.TYPE_AUTOMATION_STATUS)
            if status["androidAccess"]["notificationListener"] is True:
                break
            time.sleep(CONTROL_PLANE_RETRY_INTERVAL_SECONDS)
        else:
            raise AssertionError("notification listener did not become active")
        device_command(
            "cmd notification post "
            f"-t '{CUSTOM_EVENT_NOTIFICATION_TITLE}' {CUSTOM_EVENT_NOTIFICATION_TAG} "
            f"'{CUSTOM_EVENT_NOTIFICATION_TEXT}'",
        )
        updated = wait_for_widget_state_value(
            websocket_control,
            "command-deck",
            "notificationCount",
            1,
        )
        state = default_command_deck(updated)["state"]
        assert CUSTOM_EVENT_NOTIFICATION_TITLE in state["body"]
        run_device_operation("screenshot")
        assert (ARTIFACT_DIRECTORY / "screenshot.png").stat().st_size > 0
    finally:
        try:
            device_command(f"cmd notification disallow_listener {AUTOMATION_NOTIFICATION_COMPONENT}")
        finally:
            AUTOMATION.request(
                websocket_control,
                AUTOMATION.TYPE_CONFIG_REPLACE,
                config=original,
            )
