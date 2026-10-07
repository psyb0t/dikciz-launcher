package org.fossify.home.dikciz

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import java.io.IOException
import org.fossify.home.R

internal enum class DikcizAppActionType(
    val persistedValue: String,
    val semanticAction: String,
    val labelResourceID: Int,
    val isDestructive: Boolean,
) {
    Launch("launch", "launch", R.string.dikciz_app_action_launch, false),
    AddShortcut("addShortcut", "add-shortcut", R.string.dikciz_app_action_add_shortcut, false),
    AppInfo("appInfo", "app-info", R.string.dikciz_app_action_app_info, false),
    Uninstall("uninstall", "uninstall", R.string.dikciz_app_action_uninstall, true),
    ForceStop("forceStop", "force-stop", R.string.dikciz_app_action_force_stop, true),
    ;

    companion object {
        fun fromPersistedValue(value: String): DikcizAppActionType? {
            return entries.firstOrNull { it.persistedValue == value }
        }

        val PERSISTED_VALUES: List<String> = entries.map(DikcizAppActionType::persistedValue)
    }
}

internal data class DikcizAppActionResult(
    val action: DikcizAppActionType,
    val component: ComponentName,
    val outcome: String,
    val exitCode: Int? = null,
) {
    val isSuccessful: Boolean
        get() = outcome in DikcizAppActionOutcome.SUCCESSFUL_OUTCOMES
}

internal object DikcizAppActionOutcome {
    const val APP_INFO_OPENED = "app_info_opened"
    const val ANDROID_REJECTED = "android_rejected"
    const val FORCE_STOP_FAILED = "force_stop_failed"
    const val FORCE_STOPPED = "force_stopped"
    const val LAUNCHED = "launched"
    const val ROOT_UNAVAILABLE = "root_unavailable"
    const val SHORTCUT_ADDED = "shortcut_added"
    const val SHORTCUT_REJECTED = "shortcut_rejected"
    const val TARGET_UNAVAILABLE = "target_unavailable"
    const val UNINSTALL_NOT_PERMITTED = "uninstall_not_permitted"
    const val UNINSTALL_REQUESTED = "uninstall_requested"

    val SUCCESSFUL_OUTCOMES = setOf(
        APP_INFO_OPENED,
        FORCE_STOPPED,
        LAUNCHED,
        SHORTCUT_ADDED,
        UNINSTALL_REQUESTED,
    )
}

/**
 * Accepts only Android identifier shapes. Force stop interpolates a package name into a
 * privileged shell command, so an unvalidated value would be a command-injection path.
 */
internal object DikcizAppIdentifiers {
    fun parseComponent(value: String, maximumCharacters: Int): ComponentName? {
        if (value.length > maximumCharacters) {
            return null
        }
        val component = ComponentName.unflattenFromString(value) ?: return null
        if (!isValidPackageName(component.packageName)) {
            return null
        }
        if (!CLASS_NAME_PATTERN.matches(component.className)) {
            return null
        }
        return component
    }

    fun isValidPackageName(value: String): Boolean = PACKAGE_NAME_PATTERN.matches(value)

    private val PACKAGE_NAME_PATTERN = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)*$")
    private val CLASS_NAME_PATTERN = Regex("^[A-Za-z][A-Za-z0-9_$]*(\\.[A-Za-z0-9_$]+)*$")
}

/**
 * Runs one launcher app action and reports a finite outcome. Force stop is the only
 * privileged path and it never falls back to the app shell when root is missing.
 */
internal class DikcizAppActionRunner(
    private val context: Context,
    private val packageManager: PackageManager,
    private val logger: DikcizLogger,
    private val privilegedShell: DikcizPrivilegedShell,
    private val launchComponent: (ComponentName) -> Boolean,
    private val addShortcut: (DikcizLaunchableApp) -> Boolean,
) {
    fun run(action: DikcizAppActionType, application: DikcizLaunchableApp): DikcizAppActionResult {
        val component = application.component
        logger.debug(
            EVENT_APP_ACTION_REQUESTED,
            mapOf(
                FIELD_ACTION to action.persistedValue,
                FIELD_COMPONENT_PACKAGE to component.packageName,
            ),
        )
        val outcome = when (action) {
            DikcizAppActionType.Launch -> runLaunch(component)
            DikcizAppActionType.AddShortcut -> runAddShortcut(application)
            DikcizAppActionType.AppInfo -> runAppInfo(component.packageName)
            DikcizAppActionType.Uninstall -> runUninstall(component.packageName)
            DikcizAppActionType.ForceStop -> return runForceStop(action, component)
        }
        return report(DikcizAppActionResult(action, component, outcome))
    }

    fun isRootAvailable(): Boolean = privilegedShell.isRootAvailable()

    private fun runLaunch(component: ComponentName): String {
        if (!launchComponent(component)) {
            return DikcizAppActionOutcome.TARGET_UNAVAILABLE
        }
        return DikcizAppActionOutcome.LAUNCHED
    }

    private fun runAddShortcut(application: DikcizLaunchableApp): String {
        if (!addShortcut(application)) {
            return DikcizAppActionOutcome.SHORTCUT_REJECTED
        }
        return DikcizAppActionOutcome.SHORTCUT_ADDED
    }

    private fun runAppInfo(packageName: String): String {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(packageUri(packageName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!startAndroidActivity(intent)) {
            return DikcizAppActionOutcome.ANDROID_REJECTED
        }
        return DikcizAppActionOutcome.APP_INFO_OPENED
    }

    private fun runUninstall(packageName: String): String {
        if (!isUninstallable(packageName)) {
            return DikcizAppActionOutcome.UNINSTALL_NOT_PERMITTED
        }
        val intent = Intent(Intent.ACTION_DELETE)
            .setData(packageUri(packageName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!startAndroidActivity(intent)) {
            return DikcizAppActionOutcome.ANDROID_REJECTED
        }
        return DikcizAppActionOutcome.UNINSTALL_REQUESTED
    }

    private fun runForceStop(
        action: DikcizAppActionType,
        component: ComponentName,
    ): DikcizAppActionResult {
        val packageName = component.packageName
        if (!DikcizAppIdentifiers.isValidPackageName(packageName)) {
            return report(
                DikcizAppActionResult(action, component, DikcizAppActionOutcome.TARGET_UNAVAILABLE),
            )
        }
        if (!privilegedShell.isRootAvailable()) {
            return report(
                DikcizAppActionResult(action, component, DikcizAppActionOutcome.ROOT_UNAVAILABLE),
            )
        }
        val shellResult = try {
            privilegedShell.run(FORCE_STOP_COMMAND_PREFIX + packageName, useRoot = true)
        } catch (exception: IOException) {
            logger.warn(
                EVENT_APP_ACTION_REJECTED,
                mapOf(
                    FIELD_ACTION to action.persistedValue,
                    FIELD_COMPONENT_PACKAGE to packageName,
                    FIELD_REASON to REASON_ROOT_SHELL_UNREACHABLE,
                ),
            )
            return report(
                DikcizAppActionResult(action, component, DikcizAppActionOutcome.ROOT_UNAVAILABLE),
            )
        } catch (exception: DikcizAutomationRequestException) {
            logger.warn(
                EVENT_APP_ACTION_REJECTED,
                mapOf(
                    FIELD_ACTION to action.persistedValue,
                    FIELD_COMPONENT_PACKAGE to packageName,
                    FIELD_REASON to exception.code,
                ),
            )
            return report(
                DikcizAppActionResult(action, component, DikcizAppActionOutcome.FORCE_STOP_FAILED),
            )
        }
        val outcome = if (shellResult.exitCode == DikcizPrivilegedShell.SHELL_EXIT_CODE_SUCCESS) {
            DikcizAppActionOutcome.FORCE_STOPPED
        } else {
            DikcizAppActionOutcome.FORCE_STOP_FAILED
        }
        return report(
            DikcizAppActionResult(action, component, outcome, exitCode = shellResult.exitCode),
        )
    }

    private fun isUninstallable(packageName: String): Boolean {
        val applicationInfo = try {
            packageManager.getApplicationInfo(packageName, NO_PACKAGE_FLAGS)
        } catch (_: PackageManager.NameNotFoundException) {
            return false
        }
        val isSystemApp = (applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != NO_PACKAGE_FLAGS
        val isUpdatedSystemApp =
            (applicationInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != NO_PACKAGE_FLAGS
        return !isSystemApp || isUpdatedSystemApp
    }

    private fun startAndroidActivity(intent: Intent): Boolean {
        return try {
            context.startActivity(intent)
            true
        } catch (exception: ActivityNotFoundException) {
            logger.warn(
                EVENT_APP_ACTION_REJECTED,
                mapOf(
                    FIELD_ERROR_CLASS to exception::class.java.simpleName,
                    FIELD_REASON to REASON_ANDROID_ACTIVITY_UNAVAILABLE,
                ),
            )
            false
        } catch (exception: SecurityException) {
            logger.warn(
                EVENT_APP_ACTION_REJECTED,
                mapOf(
                    FIELD_ERROR_CLASS to exception::class.java.simpleName,
                    FIELD_REASON to REASON_ANDROID_DENIED,
                ),
            )
            false
        }
    }

    private fun packageUri(packageName: String): Uri {
        return Uri.fromParts(PACKAGE_URI_SCHEME, packageName, null)
    }

    private fun report(result: DikcizAppActionResult): DikcizAppActionResult {
        val fields = mutableMapOf<String, Any>(
            FIELD_ACTION to result.action.persistedValue,
            FIELD_COMPONENT_PACKAGE to result.component.packageName,
            FIELD_OUTCOME to result.outcome,
        )
        result.exitCode?.let { exitCode -> fields[FIELD_EXIT_CODE] = exitCode }
        if (result.isSuccessful) {
            logger.info(EVENT_APP_ACTION_COMPLETED, fields)
        } else {
            logger.warn(EVENT_APP_ACTION_COMPLETED, fields)
        }
        return result
    }

    private companion object {
        const val EVENT_APP_ACTION_COMPLETED = "app_action_completed"
        const val EVENT_APP_ACTION_REJECTED = "app_action_rejected"
        const val EVENT_APP_ACTION_REQUESTED = "app_action_requested"
        const val FIELD_ACTION = "action"
        const val FIELD_COMPONENT_PACKAGE = "component_package"
        const val FIELD_ERROR_CLASS = "error_class"
        const val FIELD_EXIT_CODE = "exit_code"
        const val FIELD_OUTCOME = "outcome"
        const val FIELD_REASON = "reason"
        const val FORCE_STOP_COMMAND_PREFIX = "am force-stop "
        const val NO_PACKAGE_FLAGS = 0
        const val PACKAGE_URI_SCHEME = "package"
        const val REASON_ANDROID_ACTIVITY_UNAVAILABLE = "android_activity_unavailable"
        const val REASON_ANDROID_DENIED = "android_denied"
        const val REASON_ROOT_SHELL_UNREACHABLE = "root_shell_unreachable"
    }
}
