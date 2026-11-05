package org.fossify.home.dikciz

import android.content.Context
import org.json.JSONObject

/** Compatibility commands delegate decisions to the same editable app backend used by custom apps. */
internal object DikcizBundledSafety {
    fun execute(context: Context, type: String, request: JSONObject): JSONObject {
        val id = DikcizCustomApps.SAFETY_ID
        if (type == DikcizJourneys.STATUS) {
            val app = DikcizCustomApps.execute(context, DikcizCustomAppCommands.READ, JSONObject().put("type", DikcizCustomAppCommands.READ).put("appId", id))
            val state = app.getJSONObject("state")
            return JSONObject().put("plan", state.optJSONObject("plan") ?: JSONObject.NULL).put("run", state.optJSONObject("run") ?: JSONObject.NULL)
                .put("backgroundMonitoring", DikcizCustomAppService.running && DikcizCustomApps.pending(context, id))
                .put("warning", app.getJSONObject("alert")).put("appId", id).put("enabled", app.getBoolean("enabled"))
                .put("appRun", app.get("run")).put("timers", app.getJSONArray("timers"))
        }
        val event = when (type) {
            DikcizJourneys.PLAN -> "plan.save"
            DikcizJourneys.PREVIEW -> "plan.preview"
            DikcizJourneys.CONTROL -> request.getString("action")
            else -> DikcizNativeCommands.invalid()
        }
        val payload = JSONObject(request.toString()).apply { remove("type"); remove("requestId"); remove("autoRequestPermissions") }
        val reply = if (type == DikcizJourneys.PREVIEW) DikcizCustomApps.query(context, id, event, payload)
        else DikcizCustomApps.execute(context, DikcizCustomAppCommands.EVENT, JSONObject().put("type", DikcizCustomAppCommands.EVENT)
            .put("appId", id).put("event", event).put("payload", payload))
        val result = reply.getJSONObject("result")
        result.optJSONObject("error")?.let {
            throw DikcizAutomationRequestException(it.getString("code"), it.getString("message"))
        }
        return result
    }
}
