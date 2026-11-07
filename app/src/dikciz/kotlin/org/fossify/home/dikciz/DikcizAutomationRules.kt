package org.fossify.home.dikciz

import android.content.Context
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

internal object DikcizAutomationRules {
    const val TYPE_PREVIEW = "automationRulePreview"
    const val TYPE_SAVE = "automationRuleSave"
    const val TYPE_LIST = "automationRulesList"
    const val TYPE_DELETE = "automationRuleDelete"
    private const val NAMESPACE = "automationRules"
    private val OPERATORS = setOf("always", "eq", "ne", "gt", "gte", "lt", "lte")
    private val RULE_KEYS = setOf("id", "title", "enabled", "event", "customEventName", "minimumIntervalMilliseconds",
        "alarmIntervalMilliseconds", "sensorType", "conditionField", "conditionOperator", "conditionValue", "commands")
    private val COMMAND_TYPES = setOf(DikcizNativeCommands.TYPE_SPEAK, DikcizHardwareControl.TYPE_VIBRATION, DikcizHardwareControl.TYPE_TORCH)

    fun execute(context: Context, type: String, request: JSONObject): JSONObject {
        val store = HomeConfigStore(context)
        if (type == TYPE_LIST) {
            val configuration = store.loadOrCreate()
            val saved = configuration.rootNamespaces.optJSONObject(NAMESPACE) ?: JSONObject()
            val rows = saved.keys().asSequence().sorted().map { id ->
                JSONObject(saved.getJSONObject(id).toString()).put("enabled", configuration.scripts.any { it.id == id && it.enabled })
            }.toList()
            return DikcizNativeCommands.page(rows, request).put("total", rows.size)
        }
        if (type == TYPE_DELETE) return store.withConfigurationLock {
            val configuration = store.loadOrCreate()
            val id = request.getString("id")
            val definitions = JSONObject(configuration.rootNamespaces.optJSONObject(NAMESPACE)?.toString() ?: "{}")
            if (!definitions.has(id)) DikcizNativeCommands.failure("native_record_not_found", "Visual rule not found")
            definitions.remove(id)
            store.save(configuration.copy(
                scripts = configuration.scripts.filterNot { it.id == id },
                rootNamespaces = JSONObject(configuration.rootNamespaces.toString()).put(NAMESPACE, definitions),
                automation = configuration.automation.copy(
                    policies = configuration.automation.policies.filterNot { it.id == id },
                    scripts = configuration.automation.scripts.filterNot { it.scriptID == id },
                ),
            ))
            JSONObject().put("id", id).put("deleted", true)
        }
        val rule = request.getJSONObject("rule")
        validateRule(rule)
        val source = source(rule)
        if (type == TYPE_PREVIEW) {
            val commands = JSONArray()
            val event = eventType(rule)
            val result = DikcizLuaRuntime().executeEvent(
                DikcizLuaScript("rule-preview", "Rule preview", true, source),
                DikcizAutomationEvent(event, "rule-preview", System.currentTimeMillis(), "rule-preview",
                    request.optJSONObject("sample") ?: JSONObject(), customEventName = rule.optString("customEventName").takeIf(String::isNotBlank)),
                store.loadOrCreate().limits, JSONObject(), null, JSONObject(),
            ) { raw ->
                commands.put(JSONObject(raw))
                JSONObject().put("type", "result").put("result", JSONObject().put("state", "simulated")).toString()
            }
            if (result is DikcizLuaAutomationResult.Failure) {
                DikcizNativeCommands.failure("automation_rule_invalid", result.diagnostic ?: result.reason.persistedValue)
            }
            return JSONObject().put("simulated", true).put("commands", commands).put("matched", commands.length() > 0)
                .put("source", source).put("message", "No Android commands were executed.")
        }
        return store.withConfigurationLock {
            val configuration = store.loadOrCreate()
            val id = rule.optString("id").ifBlank { "rule-" + UUID.randomUUID().toString() }
            val definitions = JSONObject(configuration.rootNamespaces.optJSONObject(NAMESPACE)?.toString() ?: "{}")
            if (configuration.scripts.any { it.id == id } && !definitions.has(id)) {
                DikcizNativeCommands.failure("automation_rule_conflict", "This ID belongs to a script outside the rule editor")
            }
            val savedRule = JSONObject(rule.toString()).put("id", id).put("enabled", rule.optBoolean("enabled", false))
            definitions.put(id, savedRule)
            val event = eventType(rule)
            val subscription = DikcizAutomationSubscription(
                event, rule.optLong("minimumIntervalMilliseconds", DEFAULT_INTERVAL), null,
                alarmIntervalMilliseconds = if (event == DikcizAutomationEventType.Alarm) rule.optLong("alarmIntervalMilliseconds", DEFAULT_INTERVAL) else null,
                sensorTypes = if (event == DikcizAutomationEventType.Sensor) listOf(rule.getInt("sensorType")) else emptyList(),
                samplingPeriodMicroseconds = if (event == DikcizAutomationEventType.Sensor) SENSOR_PERIOD_MICROSECONDS else null,
                locationPrecision = if (event == DikcizAutomationEventType.Location) DikcizLocationPrecision.Approximate else null,
                customEventName = if (event == DikcizAutomationEventType.Custom) rule.getString("customEventName") else null,
            )
            val script = DikcizLuaScript(id, rule.getString("title"), savedRule.getBoolean("enabled"), source)
            val policy = DikcizAutomationPolicy(id, rule.getString("title"), true, setOf(event.capability), emptySet())
            store.save(configuration.copy(
                scripts = configuration.scripts.filterNot { it.id == id } + script,
                rootNamespaces = JSONObject(configuration.rootNamespaces.toString()).put(NAMESPACE, definitions),
                automation = configuration.automation.copy(
                    policies = configuration.automation.policies.filterNot { it.id == id } + policy,
                    scripts = configuration.automation.scripts.filterNot { it.scriptID == id } +
                        DikcizAutomationScript(id, id, true, listOf(subscription)),
                ),
            ))
            JSONObject().put("rule", savedRule).put("scriptId", id).put("state", "saved")
        }
    }

    private fun eventType(rule: JSONObject) = DikcizAutomationEventType.fromPersistedValue(rule.getString("event"))
        ?: DikcizNativeCommands.invalid("Unknown automation event")

    private fun validateRule(rule: JSONObject) {
        if (rule.keys().asSequence().any { it !in RULE_KEYS }) DikcizNativeCommands.invalid("Unknown rule field")
        val title = rule.opt("title") as? String ?: DikcizNativeCommands.invalid("A rule title is required")
        if (title.isBlank() || title.length > MAXIMUM_TITLE) DikcizNativeCommands.invalid("Rule title must contain 1 to 120 characters")
        if (rule.has("id") && !ID_PATTERN.matches(rule.getString("id"))) DikcizNativeCommands.invalid("Invalid rule ID")
        if (rule.has("enabled") && rule.opt("enabled") !is Boolean) DikcizNativeCommands.invalid("enabled must be boolean")
        val event = eventType(rule)
        if (event !in EVENTS) DikcizNativeCommands.invalid("This event needs an advanced subscription. Use the script configuration editor.")
        if (event == DikcizAutomationEventType.Custom && !DikcizAutomationEventNames.isValidCustomName(rule.optString("customEventName"))) {
            DikcizNativeCommands.invalid("Provide a custom event name")
        }
        for (key in listOf("minimumIntervalMilliseconds", "alarmIntervalMilliseconds")) {
            if (rule.has(key)) DikcizNativeCommands.strictInteger(rule.opt(key), 1000, MAXIMUM_INTERVAL)
        }
        if (event == DikcizAutomationEventType.Alarm && rule.optLong("alarmIntervalMilliseconds", DEFAULT_INTERVAL) < DEFAULT_INTERVAL) {
            DikcizNativeCommands.invalid("Alarm interval must be at least 60 seconds")
        }
        if (event == DikcizAutomationEventType.Sensor) DikcizNativeCommands.strictInteger(rule.opt("sensorType"), 1, Int.MAX_VALUE.toLong())
        val op = rule.optString("conditionOperator", "always")
        if (op !in OPERATORS) DikcizNativeCommands.invalid("Unknown condition operator")
        if (op != "always") {
            if (!FIELD_PATTERN.matches(rule.optString("conditionField"))) DikcizNativeCommands.invalid("Choose one payload field")
            val value = rule.opt("conditionValue")
            if (value !is String && value !is Boolean && value !is Number) DikcizNativeCommands.invalid("Provide a primitive condition value")
            if (value is Number && !value.toDouble().isFinite()) DikcizNativeCommands.invalid("Condition number must be finite")
            if (value is String && value.length > MAXIMUM_TITLE) DikcizNativeCommands.invalid("Condition text is too long")
            if (op !in setOf("eq", "ne") && value !is Number) DikcizNativeCommands.invalid("Ordered conditions require a number")
        }
        val commands = rule.optJSONArray("commands") ?: DikcizNativeCommands.invalid("Provide commands")
        if (commands.length() !in 1..MAXIMUM_COMMANDS) DikcizNativeCommands.invalid("Choose 1 to 8 commands")
        for (index in 0 until commands.length()) {
            val command = commands.optJSONObject(index) ?: DikcizNativeCommands.invalid()
            val type = command.optString("type")
            if (type !in COMMAND_TYPES) DikcizNativeCommands.invalid("Visual rules support speechSpeak, vibrationControl and torchControl")
            DikcizNativeCommands.validate(type, command, envelope = true)
            if (type == DikcizHardwareControl.TYPE_TORCH && command.optString("action") == "on" && !command.has("durationMilliseconds")) {
                DikcizNativeCommands.invalid("Rule torch actions require a bounded duration")
            }
        }
    }

    private fun source(rule: JSONObject): String {
        val op = rule.optString("conditionOperator", "always")
        val condition = if (op == "always") "" else {
            val value = rule.get("conditionValue")
            val literal = if (value is String) luaString(value) else value.toString()
            val operator = mapOf("eq" to "==", "ne" to "~=", "gt" to ">", "gte" to ">=", "lt" to "<", "lte" to "<=").getValue(op)
            val comparison = if (op in setOf("eq", "ne")) "value $operator $literal" else "type(value) == \"number\" and value $operator $literal"
            "  local value = event.payload[" + luaString(rule.getString("conditionField")) + "]\n  if not ($comparison) then return {status = \"Condition not matched\"} end\n"
        }
        val commands = rule.getJSONArray("commands")
        val body = (0 until commands.length()).joinToString("\n") { index ->
            "  local reply = context.request(" + luaString(commands.getJSONObject(index).toString()) + ")\n" +
                "  if reply.type ~= \"result\" then return {status = \"Command rejected: \" .. tostring(reply.code)} end"
        }
        return "function on_event(event)\n" + condition + body + "\n  return {status = \"Commands completed\"}\nend\n"
    }

    private fun luaString(value: String): String {
        var equals = "="
        while (value.contains("]" + equals + "]")) equals += "="
        return "string.sub([" + equals + "[x" + value + "]" + equals + "], 2)"
    }

    val EVENTS = listOf(DikcizAutomationEventType.Custom, DikcizAutomationEventType.Time, DikcizAutomationEventType.Battery,
        DikcizAutomationEventType.Charging, DikcizAutomationEventType.Alarm, DikcizAutomationEventType.Location, DikcizAutomationEventType.Sensor)
    private const val MAXIMUM_TITLE = 120
    private const val MAXIMUM_COMMANDS = 8
    private const val DEFAULT_INTERVAL = 60000L
    private const val MAXIMUM_INTERVAL = 86400000L
    private const val SENSOR_PERIOD_MICROSECONDS = 200000
    private val ID_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9_.-]{0,79}")
    private val FIELD_PATTERN = Regex("[A-Za-z][A-Za-z0-9_]{0,63}")
    private fun text(maximum: Int) = JSONObject().put("type", "string").put("maxLength", maximum)
    private fun integer(minimum: Long, maximum: Long) = JSONObject().put("type", "integer").put("minimum", minimum).put("maximum", maximum)
    private val RULE_FIELD = mapOf("rule" to JSONObject().put("type", "object").put("additionalProperties", false)
        .put("required", JSONArray(listOf("title", "event", "commands")))
        .put("properties", JSONObject()
            .put("id", text(80).put("description", "Omit to create. Existing visual rule ID to update."))
            .put("title", text(MAXIMUM_TITLE).put("minLength", 1))
            .put("enabled", JSONObject().put("type", "boolean").put("default", false))
            .put("event", text(32).put("enum", JSONArray(EVENTS.map { it.persistedValue })))
            .put("customEventName", text(128).put("description", "Required for event=custom."))
            .put("minimumIntervalMilliseconds", integer(1000, MAXIMUM_INTERVAL).put("default", DEFAULT_INTERVAL))
            .put("alarmIntervalMilliseconds", integer(1000, MAXIMUM_INTERVAL).put("default", DEFAULT_INTERVAL))
            .put("sensorType", integer(1, Int.MAX_VALUE.toLong()).put("description", "Required for event=sensor. Android sensor type, not inventory ID."))
            .put("conditionField", text(64).put("description", "One top-level event payload key. Required unless conditionOperator=always."))
            .put("conditionOperator", text(16).put("enum", JSONArray(OPERATORS.sorted())).put("default", "always"))
            .put("conditionValue", JSONObject().put("oneOf", JSONArray(listOf(
                JSONObject().put("type", "number"), text(MAXIMUM_TITLE), JSONObject().put("type", "boolean")))))
            .put("commands", JSONObject().put("type", "array").put("minItems", 1).put("maxItems", MAXIMUM_COMMANDS)
                .put("items", JSONObject().put("oneOf", JSONArray(COMMAND_TYPES.sorted().map { type ->
                    val definition = when (type) {
                        DikcizNativeCommands.TYPE_SPEAK -> JSONObject().put("text", text(2000))
                        DikcizHardwareControl.TYPE_VIBRATION -> JSONObject(DikcizHapticsControl.FIELDS)
                        else -> JSONObject(DikcizTorchControl.FIELDS)
                    }
                    definition.put("type", text(64).put("const", type))
                    JSONObject().put("type", "object").put("additionalProperties", false).put("properties", definition)
                        .put("required", JSONArray(if (type == DikcizNativeCommands.TYPE_SPEAK) listOf("type", "text") else listOf("type", "action")))
                }))))))
    val DEFINITIONS = listOf(
        DikcizNativeCommands.Definition(TYPE_DELETE, "dikciz_automation_rule_delete",
            "Delete one saved visual rule, its Lua script and owned subscription. In-flight work may finish. Requires the matching explicit operation grant for scoped remote clients.",
            mapOf("id" to text(80)), setOf("id"), readOnly = false),
        DikcizNativeCommands.Definition(TYPE_PREVIEW, "dikciz_automation_rule_preview",
            "Compile a visual rule into Lua and simulate it with the optional sample payload. Returns proposed commands without touching Android. Requires the matching explicit operation grant for scoped remote clients.",
            RULE_FIELD + ("sample" to JSONObject().put("type", "object")), setOf("rule")),
        DikcizNativeCommands.Definition(TYPE_SAVE, "dikciz_automation_rule_save",
            "Validate and save a visual automation rule, Lua source and least-source-capability subscription. Disabled by default. Explicit enabled=true activates future events after reload. Requires the matching explicit operation grant for scoped remote clients.",
            RULE_FIELD, setOf("rule"), readOnly = false),
        DikcizNativeCommands.Definition(TYPE_LIST, "dikciz_automation_rules_list",
            "List saved visual rule definitions. Lua remains editable through the script editor.",
            mapOf("limit" to JSONObject().put("type", "integer").put("minimum", 1).put("maximum", 200),
                "offset" to JSONObject().put("type", "integer").put("minimum", 0).put("maximum", 1000000))),
    )
    val TYPES = setOf(TYPE_PREVIEW, TYPE_SAVE, TYPE_LIST, TYPE_DELETE)
}
