package org.fossify.home.dikciz

import org.json.JSONArray
import org.json.JSONObject

internal object DikcizPackageCommands {
    const val INSTALL_SET="apkInstallSet"
    const val CANCEL="apkInstallCancel"
    private fun text(maximum: Int)=JSONObject().put("type","string").put("minLength",1).put("maxLength",maximum)
    private val apk=JSONObject().put("type","object").put("properties",JSONObject(mapOf("path" to text(4096),"sha256" to text(64))))
        .put("required",JSONArray(listOf("path"))).put("additionalProperties",false)
    val DEFINITIONS=listOf(
        DikcizNativeCommands.Definition(INSTALL_SET,"dikciz_apk_install_set",
            "Install one base APK followed by its split APKs in one Android PackageInstaller transaction. apks contains 1..32 unique files, at most 512 MiB each and 1 GiB total; optional SHA-256 is checked while staging. The first file must be a base APK. Android validates split names, signatures, versions and compatibility at commit and retains owner confirmation. Poll apkInstallStatus for actual outcome; queued is not installed.",
            mapOf("apks" to JSONObject().put("type","array").put("minItems",1).put("maxItems",32).put("items",apk)),setOf("apks"),false),
        DikcizNativeCommands.Definition(CANCEL,"dikciz_apk_install_cancel",
            "Abandon a still-cancellable Dikciz-owned APK installation session, including staging and pending confirmation. Returns cancellationAccepted=false when terminal or too late. Poll apkInstallStatus for the actual outcome. Never uninstalls an installed package.",
            mapOf("sessionId" to JSONObject().put("type","integer").put("minimum",1).put("maximum",Int.MAX_VALUE)),setOf("sessionId"),false),
    )
    val TYPES=DEFINITIONS.map { it.type }.toSet()
}
