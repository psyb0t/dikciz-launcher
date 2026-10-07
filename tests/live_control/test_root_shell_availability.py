"""Root-shell execution scope through the live control planes."""

from . import *


def root_shell_mcp_result() -> dict[str, Any]:
    arguments = argparse.Namespace(
        host=AUTOMATION_HOST,
        port=MCP_PORT,
        expected_control_port=DEVICE_MCP_PORT,
        mutate=False,
    )
    session_id = MCP.initialize(arguments)
    try:
        MCP.notification_initialized(arguments, session_id)
        return MCP.call_tool(
            arguments,
            session_id,
            ROOT_SHELL_MCP_REQUEST_ID,
            MCP.TOOL_SHELL,
            {
                MCP.KEY_COMMAND: ROOT_SHELL_COMMAND,
                MCP.KEY_ROOT: True,
            },
        )
    finally:
        MCP.delete_session(arguments, session_id)


def test_nonroot_shell_is_rejected_without_sandbox_fallback(
    websocket_control: socket.socket,
) -> None:
    app_shell = AUTOMATION.request(
        websocket_control,
        AUTOMATION.TYPE_SHELL,
        **{
            AUTOMATION.KEY_COMMAND: ROOT_SHELL_COMMAND,
            AUTOMATION.KEY_ROOT: False,
        },
    )
    assert app_shell[AUTOMATION.KEY_EXECUTION_SCOPE] == ROOT_SHELL_APP_SCOPE
    assert app_shell[AUTOMATION.KEY_EXIT_CODE] == 0
    assert app_shell[AUTOMATION.KEY_OUTPUT].strip().isdigit()

    _, websocket_error = AUTOMATION.request_error_with_id(
        websocket_control,
        AUTOMATION.TYPE_SHELL,
        **{
            AUTOMATION.KEY_COMMAND: ROOT_SHELL_COMMAND,
            AUTOMATION.KEY_ROOT: True,
        },
    )
    assert websocket_error[AUTOMATION.KEY_CODE] == ROOT_SHELL_ERROR_CODE
    assert websocket_error[AUTOMATION.KEY_MESSAGE] == ROOT_SHELL_ERROR_MESSAGE

    mcp_result = root_shell_mcp_result()
    assert mcp_result[MCP.KEY_RESULT_TYPE] == ROOT_SHELL_MCP_RESULT_TYPE
    assert mcp_result[MCP.KEY_IS_ERROR] is True
    assert mcp_result[MCP.KEY_CONTENT] == [
            {
                MCP.KEY_TYPE: ROOT_SHELL_MCP_CONTENT_TYPE,
                AUTOMATION.KEY_TEXT: ROOT_SHELL_ERROR_MESSAGE,
            },
    ]
