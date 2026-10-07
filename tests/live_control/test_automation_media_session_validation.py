"""Public configuration rejection coverage for media-session policy fields."""

from . import *
from .media_session import *


MEDIA_SESSION_INVALID_CONFIGURATION_CASES = media_session_invalid_configurations(fixture_document())


@pytest.mark.parametrize(
    "invalid",
    [configuration for _, configuration in MEDIA_SESSION_INVALID_CONFIGURATION_CASES],
    ids=[case_name for case_name, _ in MEDIA_SESSION_INVALID_CONFIGURATION_CASES],
)
def test_automation_media_session_policy_parser_rejects_invalid_public_configuration(
    websocket_control: socket.socket,
    invalid: dict[str, Any],
) -> None:
    original = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    with pytest.raises(AUTOMATION.WebSocketProtocolError):
        AUTOMATION.request(
            websocket_control,
            AUTOMATION.TYPE_CONFIG_REPLACE,
            config=invalid,
        )
    current = AUTOMATION.require_configuration(
        AUTOMATION.request(websocket_control, AUTOMATION.TYPE_CONFIG_GET),
    )
    assert current == original
