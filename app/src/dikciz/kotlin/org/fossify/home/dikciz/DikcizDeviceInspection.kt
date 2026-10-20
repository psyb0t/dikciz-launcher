package org.fossify.home.dikciz

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.os.SystemClock
import org.json.JSONObject

internal object DikcizDeviceInspection {
    const val ERROR_UNSUPPORTED_TRANSPORT = "unsupported_transport"
    const val TYPE_DEVICE_INFO = "deviceInfo"
    const val TYPE_PACKAGE_INFO = "packageInfo"
    const val TYPE_RESTART = "restart"
    const val TYPE_LAUNCHER_HANDOFF = "launcherHandoff"
    val LAUNCHER_PACKAGES = setOf("eu.psyb0t.dikciz.launcher", "eu.psyb0t.dikciz.launcher.debug")
    val RESPONSE_COMMAND_TYPES = setOf(TYPE_RESTART, TYPE_LAUNCHER_HANDOFF)
    const val TYPE_DISPLAY_SCREENSHOT = "displayScreenshot"
    const val KEY_APPLICATION = "application"
    const val KEY_PACKAGE_NAME = "packageName"
    const val KEY_INSTANCE_ID = "instanceId"
    const val MAXIMUM_PACKAGE_NAME_CHARACTERS = 255
    private const val ERROR_PACKAGE_NOT_FOUND = "package_not_found"
    private val PACKAGE_NAME_PATTERN = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)*$")

    fun validatePackageName(request: JSONObject): String {
        val packageName = request.opt(KEY_PACKAGE_NAME)
        if (packageName !is String || packageName.length > MAXIMUM_PACKAGE_NAME_CHARACTERS ||
            !PACKAGE_NAME_PATTERN.matches(packageName)
        ) {
            throw DikcizAutomationRequestException(
                DikcizAutomationControlPlane.ERROR_VALIDATION_FAILED,
                "packageName must be a valid Android package name of at most 255 characters",
            )
        }
        return packageName
    }

    fun application(context: Context, instanceID: String): JSONObject {
        return packageInfo(context, context.packageName)
            .put("processId", Process.myPid())
            .put(KEY_INSTANCE_ID, instanceID)
    }

    fun deviceInfo(context: Context, instanceID: String): JSONObject {
        return JSONObject()
            .put(KEY_APPLICATION, application(context, instanceID))
            .put(
                "android",
                JSONObject()
                    .put("sdkInt", Build.VERSION.SDK_INT)
                    .put("release", Build.VERSION.RELEASE)
                    .put("manufacturer", Build.MANUFACTURER)
                    .put("model", Build.MODEL),
            )
            .put("uptimeMilliseconds", SystemClock.elapsedRealtime())
    }

    @Suppress("DEPRECATION")
    fun packageInfo(context: Context, packageName: String): JSONObject {
        val info = try {
            context.packageManager.getPackageInfo(packageName, PackageManager.MATCH_DISABLED_COMPONENTS)
        } catch (_: PackageManager.NameNotFoundException) {
            throw DikcizAutomationRequestException(ERROR_PACKAGE_NOT_FOUND, "package is not installed or visible")
        }
        val application = info.applicationInfo
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            info.versionCode.toLong()
        }
        return JSONObject()
            .put(KEY_PACKAGE_NAME, info.packageName)
            .put("versionName", info.versionName ?: JSONObject.NULL)
            .put("versionCode", versionCode)
            .put("enabled", application?.enabled ?: false)
            .put("debuggable", application != null && application.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0)
            .put("targetSdk", application?.targetSdkVersion ?: JSONObject.NULL)
            .put(
                "launchComponent",
                context.packageManager.getLaunchIntentForPackage(packageName)?.component?.flattenToString()
                    ?: JSONObject.NULL,
            )
    }
}
