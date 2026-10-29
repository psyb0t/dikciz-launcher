package org.fossify.home.dikciz

import android.content.Context
import org.json.JSONObject

internal class DikcizChatModelLimits(context: Context) {
    private val catalog = context.assets.open("chat-model-limits.json").bufferedReader().use { JSONObject(it.readText()) }

    fun describe(protocol: String, model: String, discovered: JSONObject? = null): JSONObject {
        val bundled = catalog.optJSONObject(protocol)?.optJSONObject(model)
        var hasProviderLimits = false
        var hasCatalogLimits = false
        val result = JSONObject().put("id", model)
        for (field in TOKEN_FIELDS) {
            val supplied = positive(discovered?.opt(field))
            val fallback = positive(bundled?.opt(field))
            hasProviderLimits = hasProviderLimits || supplied != null
            hasCatalogLimits = hasCatalogLimits || (supplied == null && fallback != null)
            result.put(field, supplied ?: fallback ?: JSONObject.NULL)
        }
        return result.put("limitsSource", when {
            hasProviderLimits && hasCatalogLimits -> "provider+catalog"
            hasProviderLimits -> "provider"
            hasCatalogLimits -> "catalog"
            else -> "unknown"
        })
    }

    fun selection(current: JSONObject, requested: JSONObject, limits: JSONObject): JSONObject {
        val result = JSONObject(requested.toString())
        if (requested.opt("model") !is String || requested.getString("model").isBlank()) return result
        val context = positive(limits.opt("contextTokens"))
        val maximumOutput = positive(limits.opt("maxOutputTokens"))
        if (context == null && maximumOutput == null) return result
        if (!result.has("contextTokens") && context != null) result.put("contextTokens", context)
        if (!result.has("maxOutputTokens")) {
            val selectedContext = positive(result.opt("contextTokens")) ?: current.getInt("contextTokens")
            val margin = positiveOrZero(result.opt("safetyMarginTokens")) ?: current.getInt("safetyMarginTokens")
            val summary = positive(result.opt("summaryOutputTokens")) ?: current.getInt("summaryOutputTokens")
            val available = selectedContext - margin - summary - 1
            if (available > 0) {
                result.put("maxOutputTokens", minOf(current.getInt("maxOutputTokens"), maximumOutput ?: available, available))
            }
        }
        val output = positive(result.opt("maxOutputTokens")) ?: current.getInt("maxOutputTokens")
        if (!result.has("thinkingTokens") && current.getInt("thinkingTokens") >= output) {
            result.put("thinkingTokens", 0)
        }
        return result
    }

    companion object {
        private val TOKEN_FIELDS = listOf("contextTokens", "maxOutputTokens", "maxInputTokens")
        private const val MAXIMUM_MODEL_TOKENS = 10_000_000

        fun positive(value: Any?): Int? = positiveOrZero(value)?.takeIf { it > 0 }

        private fun positiveOrZero(value: Any?): Int? {
            if (value !is Number) return null
            val integer = value.toLong()
            if (integer !in 0..MAXIMUM_MODEL_TOKENS || value.toDouble() != integer.toDouble()) return null
            return integer.toInt()
        }

        fun providerModel(document: JSONObject): JSONObject {
            val id = document.opt("id")
            require(id is String && id.isNotBlank() && id.length <= 2048 && id.none { it < ' ' }) { "Invalid provider model id" }
            val context = listOf("contextTokens", "context_length", "context_window", "max_context_tokens", "max_input_tokens")
                .firstNotNullOfOrNull { positive(document.opt(it)) }
            val output = listOf("maxOutputTokens", "max_output_tokens", "max_tokens")
                .firstNotNullOfOrNull { positive(document.opt(it)) }
                ?: positive(document.optJSONObject("top_provider")?.opt("max_completion_tokens"))
            return JSONObject().put("id", id)
                .put("contextTokens", context ?: JSONObject.NULL)
                .put("maxOutputTokens", output ?: JSONObject.NULL)
                .put("maxInputTokens", positive(document.opt("max_input_tokens")) ?: JSONObject.NULL)
        }
    }
}
