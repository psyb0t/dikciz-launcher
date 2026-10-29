package org.fossify.home.dikciz

import org.json.JSONObject

internal object DikcizChatCommands {
    const val TYPE = "chatControl"
    private fun text(maximum: Int) = JSONObject().put("type", "string").put("maxLength", maximum)
    val DEFINITION = DikcizNativeCommands.Definition(
        TYPE,
        "dikciz_chat",
        "Manage the on-device Dikciz assistant. Actions: status, profiles, profileSave, models, create, list, read, rename, settings, configure, delete, send, stop, subscribe, show, hide, fullscreen. A send is queued, not proof of completion. Poll read/status. Profile reads never return API keys. Save a connection without a model, discover its models, then configure each conversation with independent model and token settings. settings reads effective values and modelLimits. Selecting a known model fills context defaults and clamps the reply reserve; explicit token fields win. models exposes known context/output/input ceilings and limitsSource, using provider metadata before the bundled catalog. Unknown models keep manual budgets. configure patches settings and optionally changes profileId. Changing providers retains read-only access and resets other overrides. settings.readOnly defaults to false; true hides mutating tools and blocks their execution and permission prompts. subscribe replaces the conversation's event rules; each rule has id, subscription (canonical automation event subscription) and instructions. show/hide/fullscreen control the chat panel independently of the agent. Read the chat MCP guide for fields and examples.",
        mapOf(
            "action" to text(32), "conversationId" to text(80), "profileId" to text(80), "title" to text(256),
            "text" to text(32768), "requestKey" to text(80), "profile" to JSONObject().put("type", "object"),
            "settings" to JSONObject().put("type", "object"), "rules" to JSONObject().put("type", "array"), "fullScreen" to JSONObject().put("type", "boolean"),
            "limit" to JSONObject().put("type", "integer").put("minimum", 1).put("maximum", 200),
            "offset" to JSONObject().put("type", "integer").put("minimum", 0).put("maximum", 1000000),
        ),
        required = setOf("action"),
        readOnly = false,
    )

    fun validate(request: JSONObject) {
        val action = request.getString("action")
        val required = when (action) {
            "status", "profiles", "list", "hide", "show", "fullscreen" -> emptySet()
            "profileSave" -> setOf("profile")
            "models", "create" -> setOf("profileId")
            "read", "settings", "stop", "delete" -> setOf("conversationId")
            "configure" -> setOf("conversationId")
            "rename" -> setOf("conversationId", "title")
            "send" -> setOf("conversationId", "text", "requestKey")
            "subscribe" -> setOf("conversationId", "rules")
            else -> DikcizNativeCommands.invalid("Unknown chat action")
        }
        if (action == "configure" && !request.has("profileId") && !request.has("settings")) {
            DikcizNativeCommands.invalid("Provide profileId or conversation settings")
        }
        if (request.has("settings") && request.opt("settings") !is JSONObject) DikcizNativeCommands.invalid("settings must be an object")
        if (required.any { !request.has(it) }) DikcizNativeCommands.invalid("Missing chat action field")
        for (field in listOf("conversationId", "profileId", "requestKey")) {
            if (request.has(field) && !ID_PATTERN.matches(request.getString(field))) DikcizNativeCommands.invalid("Invalid $field")
        }
        for (field in listOf("text", "title")) {
            if (request.has(field) && request.getString(field).isBlank()) DikcizNativeCommands.invalid("$field must not be blank")
        }
        if (request.has("rules") && request.getJSONArray("rules").length() > MAXIMUM_RULES) DikcizNativeCommands.invalid("Too many event rules")
    }

    private const val MAXIMUM_RULES = 32
    private val ID_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9_.-]{0,79}")
}
