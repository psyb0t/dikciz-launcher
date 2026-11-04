package org.fossify.home.dikciz

import org.json.JSONArray
import org.json.JSONObject

internal data class DikcizTokenRules(
    val expiresAt: Long = 0,
    val requestsPerMinute: Int = 0,
    val smsAddresses: Set<String> = emptySet(),
) {
    fun isActive(): Boolean = expiresAt == 0L || System.currentTimeMillis() < expiresAt

    fun write(document: JSONObject): JSONObject = document
        .put(KEY_EXPIRES, expiresAt)
        .put(KEY_RATE, requestsPerMinute)
        .put(KEY_SMS, JSONArray(smsAddresses.sorted()))

    fun permits(type: String, request: JSONObject?): Boolean {
        if (smsAddresses.isEmpty()) return true
        if (type == DikcizNativeCommands.TYPE_QUERY && request?.optString("resource") == "sms") {
            return request.optJSONObject("filter")?.optString("address") in smsAddresses
        }
        if (type == DikcizNativeCommands.TYPE_SMS_SEND) {
            return request?.optString("recipient") in smsAddresses
        }
        return true
    }

    companion object {
        const val KEY_EXPIRES = "expiresAt"
        const val KEY_RATE = "requestsPerMinute"
        const val KEY_SMS = "smsAddresses"
        const val MAXIMUM_RATE = 10000
        const val MAXIMUM_ADDRESSES = 64
        val KEYS = setOf(KEY_EXPIRES, KEY_RATE, KEY_SMS)
        val FIELDS = mapOf(
            KEY_EXPIRES to JSONObject().put("type", "integer").put("minimum", 0).put("maximum", Long.MAX_VALUE)
                .put("description", "Expiry in epoch milliseconds. Zero means no expiry."),
            KEY_RATE to JSONObject().put("type", "integer").put("minimum", 0).put("maximum", MAXIMUM_RATE)
                .put("description", "Accepted commands per rolling minute across all entrypoints. Zero means unlimited."),
            KEY_SMS to JSONObject().put("type", "array").put("maxItems", MAXIMUM_ADDRESSES).put("uniqueItems", true)
                .put("items", JSONObject().put("type", "string").put("maxLength", 64))
                .put("description", "Optional exact SMS addresses. Restricted reads require filter.address; sending requires an exact recipient. Empty permits all addresses."),
        )

        fun parse(document: JSONObject, previous: DikcizTokenRules = DikcizTokenRules()): DikcizTokenRules {
            val expires = if (document.has(KEY_EXPIRES)) {
                DikcizNativeCommands.strictInteger(document.opt(KEY_EXPIRES), 0, Long.MAX_VALUE)
            } else previous.expiresAt
            val rate = if (document.has(KEY_RATE)) {
                DikcizNativeCommands.strictInteger(document.opt(KEY_RATE), 0, MAXIMUM_RATE.toLong()).toInt()
            } else previous.requestsPerMinute
            val addresses = if (document.has(KEY_SMS)) {
                val values = document.optJSONArray(KEY_SMS) ?: DikcizNativeCommands.invalid("smsAddresses must be an array")
                if (values.length() > MAXIMUM_ADDRESSES) DikcizNativeCommands.invalid("Too many SMS addresses")
                (0 until values.length()).map {
                    val value = values.opt(it) as? String ?: DikcizNativeCommands.invalid("Invalid SMS address")
                    if (value.isBlank() || value.length > 64 || value.any(Char::isISOControl)) {
                        DikcizNativeCommands.invalid("Invalid SMS address")
                    }
                    value
                }.also { if (it.distinct().size != it.size) DikcizNativeCommands.invalid("Duplicate SMS address") }.toSet()
            } else previous.smsAddresses
            return DikcizTokenRules(expires, rate, addresses)
        }
    }
}
