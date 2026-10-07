package org.fossify.home.dikciz

import org.json.JSONArray
import org.json.JSONObject

internal object DikcizControlStatus {
    fun document(
        isSafeMode: Boolean,
        safeModeAllowedCommandTypes: Set<String>,
        safeModeAllowedTapSemanticIDs: Set<String>,
    ): JSONObject {
        return JSONObject()
            .put(KEY_PROTOCOL_VERSION, DikcizAutomationControlPlane.PROTOCOL_VERSION)
            .put(KEY_SAFE_MODE, isSafeMode)
            .put(
                KEY_SAFE_MODE_ALLOWED_COMMANDS,
                JSONArray(safeModeAllowedCommandTypes.sorted()),
            )
            .put(
                KEY_SAFE_MODE_ALLOWED_TAP_IDS,
                JSONArray(safeModeAllowedTapSemanticIDs.sorted()),
            )
            .put(KEY_TRANSPORTS, transports())
            .put(KEY_LIMITS, limits())
            .put(KEY_COMMANDS, commandSpecifications())
            .put(KEY_EVENTS, eventSpecifications())
            .put(KEY_PLACEMENT_FAILURES, placementFailures())
    }

    private fun transports(): JSONObject {
        return JSONObject()
            .put(
                KEY_WEBSOCKET,
                JSONObject()
                    .put(KEY_PATH, DikcizAutomationControlPlane.CONTROL_PATH)
                    .put(KEY_PORT, DikcizAutomationControlPlane.CONTROL_PORT),
            )
            .put(
                KEY_MCP,
                JSONObject()
                    .put(KEY_PATH, DikcizMcpControlPlane.MCP_PATH)
                    .put(KEY_PORT, DikcizMcpControlPlane.CONTROL_PORT),
            )
    }

    private fun limits(): JSONObject {
        return JSONObject()
            .put(
                KEY_MAXIMUM_SEMANTIC_ID_CHARACTERS,
                DikcizAutomationControlPlane.MAXIMUM_SEMANTIC_ID_CHARACTERS,
            )
            .put(
                KEY_MAXIMUM_TEXT_CHARACTERS,
                DikcizAutomationControlPlane.MAXIMUM_TEXT_CHARACTERS,
            )
            .put(
                KEY_MAXIMUM_SHELL_COMMAND_CHARACTERS,
                DikcizAutomationControlPlane.MAXIMUM_SHELL_COMMAND_CHARACTERS,
            )
            .put(
                KEY_MAXIMUM_COMPONENT_CHARACTERS,
                DikcizAutomationControlPlane.MAXIMUM_COMPONENT_CHARACTERS,
            )
            .put(
                KEY_MAXIMUM_APP_QUERY_CHARACTERS,
                DikcizAutomationControlPlane.MAXIMUM_APP_QUERY_CHARACTERS,
            )
            .put(
                KEY_MAXIMUM_INTENT_ACTION_CHARACTERS,
                DikcizAutomationControlPlane.MAXIMUM_INTENT_ACTION_CHARACTERS,
            )
            .put(
                KEY_SCROLL_DELTA,
                range(
                    DikcizAutomationControlPlane.MINIMUM_SCROLL_DELTA,
                    DikcizAutomationControlPlane.MAXIMUM_SCROLL_DELTA,
                ),
            )
            .put(
                KEY_WAIT_TIMEOUT_MILLISECONDS,
                range(
                    DikcizAutomationControlPlane.MINIMUM_WAIT_TIMEOUT_MILLISECONDS,
                    DikcizAutomationControlPlane.MAXIMUM_WAIT_TIMEOUT_MILLISECONDS,
                ),
            )
    }

    private fun range(minimum: Int, maximum: Int): JSONObject {
        return JSONObject()
            .put(KEY_MINIMUM, minimum)
            .put(KEY_MAXIMUM, maximum)
    }

    private fun commandSpecifications(): JSONArray {
        return JSONArray().apply {
            DikcizAutomationControlPlane.COMMAND_KEYS.keys
                .sorted()
                .forEach { commandType ->
                    put(commandSpecification(commandType))
                }
        }
    }

    private fun commandSpecification(commandType: String): JSONObject {
        val fields = DikcizAutomationControlPlane.COMMAND_KEYS.getValue(commandType)
            .minus(DikcizAutomationControlPlane.KEY_REQUEST_ID)
            .minus(DikcizAutomationControlPlane.KEY_TYPE)
            .sorted()
        return JSONObject()
            .put(KEY_TYPE, commandType)
            .put(KEY_FIELDS, JSONArray(fields))
            .put(KEY_SIDE_EFFECT, sideEffect(commandType))
            .put(
                KEY_MCP_TOOL,
                DikcizMcpControlPlane.toolNameForCommand(commandType) ?: JSONObject.NULL,
            )
    }

    /**
     * The finite placement failure codes. Both planes report exactly these values, so a
     * client can branch on them without parsing a message.
     */
    private fun placementFailures(): JSONArray {
        return JSONArray(
            DikcizGridFailure.entries
                .map(DikcizGridFailure::persistedValue)
                .sorted(),
        )
    }

    private fun eventSpecifications(): JSONArray {
        return JSONArray().apply {
            eventSpecifications
                .sortedBy(EventSpecification::type)
                .forEach { specification ->
                    put(
                        JSONObject()
                            .put(KEY_TYPE, specification.type)
                            .put(KEY_REQUIRED_FIELDS, JSONArray(specification.requiredFields))
                            .put(KEY_OPTIONAL_FIELDS, JSONArray(specification.optionalFields)),
                    )
                }
        }
    }

    private fun sideEffect(commandType: String): String {
        return when (commandType) {
            DikcizAutomationControlPlane.TYPE_APP_CATALOGUE,
            DikcizAutomationControlPlane.TYPE_AUTOMATION_STATUS,
            DikcizAutomationControlPlane.TYPE_CONFIG_GET,
            DikcizAutomationControlPlane.TYPE_CONTROL_STATUS,
            DikcizAutomationControlPlane.TYPE_DIAGNOSTICS,
            DikcizAutomationControlPlane.TYPE_FIND,
            DikcizAutomationControlPlane.TYPE_HOME_GET,
            DikcizAutomationControlPlane.TYPE_REMOTE_AUTH_STATUS,
            DikcizAutomationControlPlane.TYPE_SCREENSHOT,
            DikcizAutomationControlPlane.TYPE_SNAPSHOT,
            DikcizAutomationControlPlane.TYPE_ACCESSIBILITY_SNAPSHOT,
            DikcizAutomationControlPlane.TYPE_SCRIPT_LOGS,
            DikcizAutomationControlPlane.TYPE_UI_DUMP,
            DikcizAutomationControlPlane.TYPE_WAIT_FOR,
            DikcizAutomationControlPlane.TYPE_WIDGET_GET,
            -> SIDE_EFFECT_READ

            DikcizAutomationControlPlane.TYPE_APP_ACTION,
            DikcizAutomationControlPlane.TYPE_INTENT,
            DikcizAutomationControlPlane.TYPE_LAUNCH_APP,
            DikcizAutomationControlPlane.TYPE_SHELL,
            DikcizAutomationControlPlane.TYPE_AUTOMATION_SERVICE_SYNC,
            -> SIDE_EFFECT_DEVICE

            DikcizAutomationControlPlane.TYPE_TAP,
            DikcizAutomationControlPlane.TYPE_ACCESSIBILITY_ACTION,
            -> SIDE_EFFECT_INTERACTIVE

            else -> SIDE_EFFECT_LAUNCHER
        }
    }

    private const val KEY_COMMANDS = "commands"
    private const val KEY_PLACEMENT_FAILURES = "placementFailures"
    private const val KEY_MAXIMUM_APP_QUERY_CHARACTERS = "maximumAppQueryCharacters"
    private const val KEY_MAXIMUM_COMPONENT_CHARACTERS = "maximumComponentCharacters"
    private const val KEY_EVENTS = "events"
    private const val KEY_FIELDS = "fields"
    private const val KEY_LIMITS = "limits"
    private const val KEY_MAXIMUM = "maximum"
    private const val KEY_MAXIMUM_INTENT_ACTION_CHARACTERS = "maximumIntentActionCharacters"
    private const val KEY_MAXIMUM_SEMANTIC_ID_CHARACTERS = "maximumSemanticIdCharacters"
    private const val KEY_MAXIMUM_SHELL_COMMAND_CHARACTERS = "maximumShellCommandCharacters"
    private const val KEY_MAXIMUM_TEXT_CHARACTERS = "maximumTextCharacters"
    private const val KEY_MCP = "mcp"
    private const val KEY_MCP_TOOL = "mcpTool"
    private const val KEY_MINIMUM = "minimum"
    private const val KEY_PATH = "path"
    private const val KEY_PORT = "port"
    private const val KEY_PROTOCOL_VERSION = "protocolVersion"
    private const val KEY_OPTIONAL_FIELDS = "optionalFields"
    private const val KEY_REQUIRED_FIELDS = "requiredFields"
    private const val KEY_SAFE_MODE = "safeMode"
    private const val KEY_SAFE_MODE_ALLOWED_COMMANDS = "safeModeAllowedCommands"
    private const val KEY_SAFE_MODE_ALLOWED_TAP_IDS = "safeModeAllowedTapIds"
    private const val KEY_SCROLL_DELTA = "scrollDelta"
    private const val KEY_SIDE_EFFECT = "sideEffect"
    private const val KEY_TRANSPORTS = "transports"
    private const val KEY_TYPE = "type"
    private const val KEY_WAIT_TIMEOUT_MILLISECONDS = "waitTimeoutMilliseconds"
    private const val KEY_WEBSOCKET = "webSocket"
    private const val SIDE_EFFECT_DEVICE = "device"
    private const val SIDE_EFFECT_INTERACTIVE = "interactive"
    private const val SIDE_EFFECT_LAUNCHER = "launcher"
    private const val SIDE_EFFECT_READ = "read"

    private data class EventSpecification(
        val type: String,
        val requiredFields: List<String>,
        val optionalFields: List<String> = emptyList(),
    )

    private val eventSpecifications = listOf(
        EventSpecification(
            type = DikcizAutomationControlPlane.EVENT_COMMAND_COMPLETED,
            requiredFields = listOf(
                DikcizAutomationControlPlane.KEY_REQUEST_ID,
                DikcizAutomationControlPlane.EVENT_FIELD_COMMAND_TYPE,
                DikcizAutomationControlPlane.EVENT_FIELD_OUTCOME,
            ),
            optionalFields = listOf(DikcizAutomationControlPlane.KEY_CODE),
        ),
        EventSpecification(
            type = DikcizAutomationControlPlane.EVENT_UI_RENDERED,
            requiredFields = listOf(
                DikcizAutomationControlPlane.EVENT_FIELD_SCREEN,
                DikcizAutomationControlPlane.EVENT_FIELD_SELECTED_PAGE_ID,
            ),
        ),
        EventSpecification(
            type = DikcizAutomationControlPlane.EVENT_HTML_WIDGET_EVENT,
            requiredFields = listOf(DikcizAutomationControlPlane.EVENT_FIELD_EVENT),
        ),
    )

}
