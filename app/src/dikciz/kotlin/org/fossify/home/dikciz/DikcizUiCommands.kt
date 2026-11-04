package org.fossify.home.dikciz

import org.json.JSONObject

internal object DikcizUiCommands {
    fun execute(owner: String, type: String, fields: JSONObject): JSONObject {
        val request = JSONObject(fields.toString()).put("type", type)
        val reply = DikcizLocalCommands.execute(owner, request)
        val response = reply.response
        if (response.optString("type") != "result") {
            throw DikcizAutomationRequestException(response.optString("code", "native_operation_failed"),
                response.optString("message", "Phone command failed"))
        }
        reply.delivered()
        return response.getJSONObject("result")
    }
}
