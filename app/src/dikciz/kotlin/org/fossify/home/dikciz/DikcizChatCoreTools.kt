package org.fossify.home.dikciz

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import org.json.JSONArray
import org.json.JSONObject

internal object DikcizChatCoreTools {
    val names = setOf(
        "dikciz_home_get",
        "dikciz_select_page",
        "dikciz_widget_get",
        "dikciz_add_widget",
        "dikciz_automation_dispatch",
        "dikciz_automation_status",
        "dikciz_automation_simulate",
        "dikciz_automation_trigger",
        "dikciz_automation_history",
        "dikciz_script_logs",
        "dikciz_config_get",
        "dikciz_config_replace",
        "dikciz_custom_apps_list",
        "dikciz_custom_app_read",
        "dikciz_custom_app_install",
        "dikciz_custom_app_event",
        "dikciz_assistant_library_validate",
        "dikciz_assistant_library_manage",
    )

    fun descriptors(catalogue: JSONArray): List<ToolDescriptor> = (0 until catalogue.length())
        .map(catalogue::getJSONObject)
        .filter { it.getString("name") in names }
        .map { tool ->
            val schema = tool.getJSONObject("inputSchema")
            val parameters = parameters(schema)
            val required = required(schema)
            ToolDescriptor(
                tool.getString("name"),
                tool.getString("description"),
                requiredParameters = parameters.filter { it.name in required },
                optionalParameters = parameters.filterNot { it.name in required },
            )
        }

    private fun parameters(schema: JSONObject): List<ToolParameterDescriptor> {
        val properties = schema.optJSONObject("properties") ?: return emptyList()
        return properties.keys().asSequence().map { name ->
            val property = properties.getJSONObject(name)
            ToolParameterDescriptor(name, property.optString("description"), type(property))
        }.toList()
    }

    private fun required(schema: JSONObject): List<String> {
        val names = schema.optJSONArray("required") ?: return emptyList()
        return (0 until names.length()).map(names::getString)
    }

    private fun type(schema: JSONObject): ToolParameterType = when (schema.getString("type")) {
        "string" -> {
            val values = schema.optJSONArray("enum")
            if (values == null) ToolParameterType.String
            else ToolParameterType.Enum((0 until values.length()).map(values::getString).toTypedArray())
        }
        "boolean" -> ToolParameterType.Boolean
        "integer" -> ToolParameterType.Integer
        "number" -> ToolParameterType.Float
        "object" -> ToolParameterType.Object(
            parameters(schema),
            required(schema),
            schema.opt("additionalProperties") as? Boolean,
            schema.optJSONObject("additionalProperties")?.let(::type),
        )
        "array" -> ToolParameterType.List(
            schema.optJSONObject("items")?.let(::type)
                ?: ToolParameterType.Object(emptyList(), emptyList(), true),
        )
        else -> error("Unsupported chat tool parameter type")
    }
}
