package org.fossify.home.dikciz

import org.json.JSONArray
import org.json.JSONObject

/** Finite choices for older definitions whose descriptions predate enum schemas. */
internal object DikcizCommandChoices {
    private val fields = mapOf(
        "accessibilityAction" to mapOf("action" to listOf("click", "longClick", "focus", "clearFocus", "scrollForward", "scrollBackward", "setText", "select")),
        "appAction" to mapOf("action" to listOf("launch", "addShortcut", "appInfo", "uninstall", "forceStop")),
        "contentWrite" to mapOf("operation" to listOf("create", "update", "delete")),
        "callControl" to mapOf("action" to listOf("state", "place", "answer", "end")),
        "callAudio" to mapOf("action" to listOf("status", "record", "speak")),
        "torchControl" to mapOf("action" to listOf("status", "on", "off")),
        "vibrationControl" to mapOf("action" to listOf("status", "vibrate", "waveform", "effect", "cancel")),
        "systemSetting" to mapOf("action" to listOf("get", "set"), "setting" to listOf("brightness", "automaticBrightness", "automaticRotation", "rotation", "screenTimeoutMilliseconds")),
        "speechRecognize" to mapOf("mode" to listOf("onDevice", "system")),
        "speechLanguages" to mapOf("mode" to listOf("onDevice", "system")),
        "speechModelDownload" to mapOf("mode" to listOf("onDevice", "system")),
        "notificationManage" to mapOf("action" to listOf("dismiss", "snooze", "reply")),
        "fileManage" to mapOf("action" to listOf("stat", "copy", "move", "delete", "search")),
        "usbTransfer" to mapOf("kind" to listOf("control", "bulk")),
        "geocode" to mapOf("action" to listOf("forward", "reverse")),
        "bluetoothScan" to mapOf("mode" to listOf("ble", "classic")),
        "bluetoothGatt" to mapOf("action" to listOf("discover", "read", "write", "readDescriptor", "writeDescriptor", "observe")),
        "deviceConnectionExchange" to mapOf("action" to listOf("control", "bulk", "interrupt", "transceive", "readNdef", "writeNdef", "discover", "read", "write", "subscribe", "unsubscribe", "notifications")),
        "chatControl" to mapOf("action" to listOf("status", "profiles", "profileSave", "models", "create", "list", "read", "rename", "settings", "configure", "delete", "send", "task", "stop", "subscribe", "show", "hide", "fullscreen")),
    )

    fun apply(tool: JSONObject) {
        val command = requireNotNull(DikcizMcpControlPlane.TOOL_COMMANDS[tool.getString("name")])
        val schema = tool.getJSONObject("inputSchema")
        val properties = schema.getJSONObject("properties")
        fields[command]?.forEach { (name, values) -> properties.getJSONObject(name).put("enum", JSONArray(values)) }
        if (command in setOf("contentQuery", "contentWrite", "contentBatch")) {
            val resource = if (command == "contentBatch") properties.getJSONObject("operations")
                .getJSONObject("items").getJSONObject("properties") else properties
            val resources = DikcizContentData.RESOURCES.filterValues { command == "contentQuery" || it.writable.isNotEmpty() }
            resource.getJSONObject("resource").put("enum", JSONArray(resources.keys.toList()))
            if (command == "contentBatch") resource.getJSONObject("operation").put("enum", JSONArray(listOf("create", "update", "delete")))
        }
    }
}
