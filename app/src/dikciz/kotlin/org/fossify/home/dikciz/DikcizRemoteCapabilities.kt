package org.fossify.home.dikciz

import org.json.JSONArray
import org.json.JSONObject

/** Explicit operation grants derive from the same schemas used for discovery. */
internal object DikcizRemoteCapabilities {
    private const val PREFIX = "command."
    private const val RESOURCE = "resource"
    private fun selectorFields(properties: JSONObject): List<String> {
        val nested = properties.optJSONObject("operations")?.optJSONObject("items")?.optJSONObject("properties")
        return (properties.keys().asSequence().toList() + (nested?.keys()?.asSequence()?.toList() ?: emptyList()))
            .distinct().sortedWith(compareBy<String> { if (it == RESOURCE) 0 else 1 }.thenBy { it })
            .filter { selectorSchema(properties, it)?.optString("type") == "string" && selectorSchema(properties, it)?.has("enum") == true }
    }

    data class Entry(
        val id: String,
        val command: String,
        val tool: String,
        val selectors: Map<String, String>,
        val description: String,
    ) {
        fun matches(request: JSONObject?): Boolean = request == null || selectors.all { (field, value) ->
            if (!request.has(field) && request.optJSONArray("operations") != null) {
                val operations = request.getJSONArray("operations")
                operations.length() > 0 && (0 until operations.length()).all {
                    operations.optJSONObject(it)?.optString(field) == value
                }
            } else request.opt(field) == value
        }

        fun grant() = DikcizRemotePolicy.Grant(
            id,
            command + if (selectors.isEmpty()) " (all operations)" else selectors.entries.joinToString(prefix = ": ") { "${it.key}=${it.value}" },
            description,
            setOf(tool),
        )

        fun document() = JSONObject().put("id", id).put("command", command).put("mcpTool", tool)
            .put("selectors", JSONObject(selectors)).put("description", description)
    }

    val entries: List<Entry> by lazy {
        val tools = DikcizMcpControlPlane.toolCatalogue(includePermissions = false).getJSONArray("tools")
        buildList {
            for (index in 0 until tools.length()) {
                val tool = tools.getJSONObject(index)
                val name = tool.getString("name")
                val command = requireNotNull(DikcizMcpControlPlane.TOOL_COMMANDS[name])
                val properties = tool.getJSONObject("inputSchema").getJSONObject("properties")
                val combinations = mutableListOf<Map<String, String>>(emptyMap())
                for (field in selectorFields(properties)) {
                    val values = selectorSchema(properties, field)?.optJSONArray("enum") ?: continue
                    val choices = (0 until values.length()).mapNotNull { values.opt(it) as? String }
                    combinations += combinations.toList().flatMap { selected -> choices.map { selected + (field to it) } }
                }
                for (selectors in combinations) {
                    val id = PREFIX + command + selectors.entries.joinToString("") { ".${it.key}.${it.value}" }
                    val boundary = if (selectors.isEmpty()) "All accepted arguments and actions." else
                        "Requires explicit " + selectors.entries.joinToString { "${it.key}=${it.value}" } + "."
                    add(Entry(id, command, name, selectors,
                        "$boundary ${tool.getString("description")} Operation scope is device-wide, not a per-file, app or session sandbox. Code, shell, SDK, configuration and credential administration can delegate broader authority. Android access still applies."))
                }
            }
        }
    }

    private val byCommand by lazy { entries.groupBy(Entry::command) }

    private fun selectorSchema(properties: JSONObject, field: String): JSONObject? =
        properties.optJSONObject(field) ?: properties.optJSONObject("operations")
            ?.optJSONObject("items")?.optJSONObject("properties")?.optJSONObject(field)

    fun permits(access: DikcizRemoteAccess, type: String, request: JSONObject?): Boolean {
        val allowed = byCommand[type].orEmpty().filter { it.id in access.permissions }
        if (request == null) return allowed.isNotEmpty()
        val operations = request.optJSONArray("operations")
        if (operations != null && type == "contentBatch") {
            return operations.length() > 0 && (0 until operations.length()).all { position ->
                val single = JSONObject(request.toString()).put("operations", JSONArray().put(operations.get(position)))
                allowed.any { it.matches(single) }
            }
        }
        return allowed.any { it.matches(request) }
    }

    fun forCommand(type: String) = JSONArray(byCommand[type].orEmpty().map(Entry::document))

    fun filterSchema(type: String, schema: JSONObject, access: DikcizRemoteAccess) {
        val properties = schema.getJSONObject("properties")
        val choices = selectorFields(properties).associateWith { field ->
            val values = selectorSchema(properties, field)?.optJSONArray("enum")
            if (values == null) emptyList() else (0 until values.length()).mapNotNull { values.opt(it) as? String }
        }.filterValues { it.isNotEmpty() }
        var candidates = listOf(JSONObject())
        for ((field, values) in choices) {
            candidates = candidates.flatMap { previous -> values.map { value ->
                JSONObject(previous.toString()).apply {
                    if (properties.has(field)) put(field, value)
                    else {
                        val operations = optJSONArray("operations") ?: JSONArray().put(JSONObject()).also { put("operations", it) }
                        operations.getJSONObject(0).put(field, value)
                    }
                }
            } }
        }
        val permitted = candidates.filter { DikcizRemotePolicy.permits(access, type, it) }
        val required = schema.optJSONArray("required") ?: JSONArray().also { schema.put("required", it) }
        for (field in choices.keys.filter(properties::has)) {
            if (permitted.isNotEmpty() && permitted.none { candidate ->
                    DikcizRemotePolicy.permits(access, type, JSONObject(candidate.toString()).apply { remove(field) })
                } && (0 until required.length()).none { required.getString(it) == field }) required.put(field)
        }
        if (permitted.size < candidates.size && permitted.isNotEmpty()) {
            val alternatives = JSONArray(permitted.map { candidate ->
                val selected = candidate.optJSONArray("operations")?.getJSONObject(0) ?: candidate
                JSONObject().put("properties", JSONObject().apply {
                    selected.keys().forEach { field -> put(field, JSONObject().put("const", selected.get(field))) }
                }).put("required", JSONArray(selected.keys().asSequence().toList()))
            })
            if (properties.has("operations")) properties.getJSONObject("operations").getJSONObject("items").put("anyOf", alternatives)
            else schema.put("allOf", JSONArray().put(JSONObject().put("anyOf", alternatives)))
        }
        for (field in choices.keys) {
            val values = permitted.map { request ->
                if (properties.has(field)) request.getString(field)
                else request.getJSONArray("operations").getJSONObject(0).getString(field)
            }.distinct().sorted()
            selectorSchema(properties, field)?.put("enum", JSONArray(values))
        }
    }
}
