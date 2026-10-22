"""Shared native intent URI validation across all entrypoints."""

from . import *


@pytest.mark.parametrize("protocol", ["websocket", "mcp", "http"])
def test_html_card_presets_native_intent_uri_is_shared_and_validated(websocket_control, protocol, monkeypatch):
    from . import test_remote_device_control as remote

    monkeypatch.setitem(remote.TOOLS, "intent", "dikciz_intent")
    arguments = {"intentType":"broadcast", "action":"eu.psyb0t.dikciz.fixture.PRESET_URI"}
    assert remote.remote_call(protocol, "intent", arguments)["dispatched"] is True
    # A private test broadcast exercises native URI dispatch without opening a browser.
    for uri in ("https://example.invalid/?q=fixture%20search", "x:" + "a" * 2046):
        result = remote.remote_call(protocol, "intent", {**arguments, "data":uri})
        assert result["dispatched"] is True
    for invalid in ("", "relative/path", "https://example.invalid/\n", "x:" + "a" * 2047):
        remote.remote_call(protocol, "intent", {**arguments, "data":invalid}, error="validation_failed")
