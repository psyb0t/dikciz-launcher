package org.fossify.home.dikciz

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import org.fossify.home.R

internal class DikcizWidgetPickerCatalogue(
    private val context: Context,
    private val appWidgetManager: AppWidgetManager,
    private val logger: DikcizLogger,
    private val loadLaunchableApps: () -> List<DikcizLaunchableApp>,
) {
    fun widgetPickerCategories(): List<WidgetPickerCategory> {
        val nativeEntries = buildList {
            add(
                NativeWidgetPickerEntry(
                    NativeWidgetType.Html,
                    context.getString(R.string.dikciz_html_widget_title),
                ),
            )
            add(
                NativeWidgetPickerEntry(
                    NativeWidgetType.ScriptDashboard,
                    context.getString(R.string.dikciz_script_dashboard_widget_title),
                ),
            )
        }
        val dikcizCategory = WidgetPickerCategory(
            automationID = PICKER_CATEGORY_DIKCIZ,
            label = context.getString(R.string.dikciz_widget_picker_dikciz_category),
            icon = context.applicationInfo.loadIcon(context.packageManager),
            entries = nativeEntries,
        )
        val applications = pickerWidgetApplications()
        if (applications.isEmpty()) {
            return listOf(dikcizCategory)
        }
        return listOf(
            dikcizCategory,
            WidgetPickerCategory(
                automationID = PICKER_CATEGORY_APPS,
                label = context.getString(R.string.dikciz_widget_picker_apps_category),
                icon = context.packageManager.defaultActivityIcon,
                entries = emptyList(),
                applications = applications,
            ),
        )
    }

    fun appShortcutPickerCategories(): List<WidgetPickerCategory> {
        return loadLaunchableApps()
            .groupBy { application -> application.component.packageName }
            .mapNotNull(::appShortcutPickerCategory)
    }

    fun providerWidgetTitle(providerInfo: AppWidgetProviderInfo): String {
        return providerInfo.loadLabel(context.packageManager)?.toString().orEmpty()
            .ifBlank { providerInfo.provider.className }
    }

    fun categoryMatchesQuery(
        category: WidgetPickerCategory,
        entry: WidgetPickerEntry,
        query: String,
    ): Boolean {
        if (query.isBlank()) {
            return true
        }
        return category.label.contains(query, ignoreCase = true) ||
            entry.label.contains(query, ignoreCase = true)
    }

    fun applicationMatchesQuery(
        category: WidgetPickerCategory,
        application: WidgetPickerApplication,
        query: String,
    ): Boolean {
        if (query.isBlank()) {
            return true
        }
        return category.label.contains(query, ignoreCase = true) ||
            application.label.contains(query, ignoreCase = true) ||
            application.entries.any { entry -> entry.label.contains(query, ignoreCase = true) }
    }

    private fun pickerWidgetApplications(): List<WidgetPickerApplication> {
        val providersByPackage = appWidgetManager.installedProviders
            .asSequence()
            .filter { providerInfo -> providerInfo.provider.packageName != context.packageName }
            .map(::providerWidgetPickerEntry)
            .groupBy { entry -> entry.providerInfo.provider.packageName }
        return providersByPackage.mapNotNull { (packageName, providers) ->
            pickerWidgetApplication(packageName, providers)
        }.sortedWith(
            compareBy(String.CASE_INSENSITIVE_ORDER) { application -> application.label },
        )
    }

    private fun pickerWidgetApplication(
        packageName: String,
        providers: List<ProviderWidgetPickerEntry>,
    ): WidgetPickerApplication? {
        val applicationInfo = try {
            context.packageManager.getApplicationInfo(packageName, NO_PACKAGE_MANAGER_FLAGS)
        } catch (exception: PackageManager.NameNotFoundException) {
            logger.warn(
                EVENT_WIDGET_PICKER_CATEGORY_SKIPPED,
                mapOf(
                    FIELD_COMPONENT_PACKAGE to packageName,
                    FIELD_ERROR_CLASS to exception::class.java.simpleName,
                ),
            )
            return null
        }
        val label = context.packageManager.getApplicationLabel(applicationInfo).toString().trim()
        if (label.isEmpty()) {
            logger.warn(
                EVENT_WIDGET_PICKER_CATEGORY_SKIPPED,
                mapOf(FIELD_COMPONENT_PACKAGE to packageName),
            )
            return null
        }
        return WidgetPickerApplication(
            packageName = packageName,
            label = label,
            icon = context.packageManager.getApplicationIcon(applicationInfo),
            providers = providers.sortedWith(
                compareBy(String.CASE_INSENSITIVE_ORDER) { entry -> entry.label },
            ),
        )
    }

    private fun providerWidgetPickerEntry(
        providerInfo: AppWidgetProviderInfo,
    ): ProviderWidgetPickerEntry {
        return ProviderWidgetPickerEntry(
            providerInfo = providerInfo,
            label = providerWidgetTitle(providerInfo),
            preview = providerWidgetPreview(providerInfo),
        )
    }

    private fun providerWidgetPreview(providerInfo: AppWidgetProviderInfo): Drawable? {
        return try {
            providerInfo.loadPreviewImage(context, context.resources.displayMetrics.densityDpi)
        } catch (exception: Resources.NotFoundException) {
            logger.warn(
                EVENT_WIDGET_PICKER_PREVIEW_UNAVAILABLE,
                mapOf(
                    FIELD_COMPONENT_PACKAGE to providerInfo.provider.packageName,
                    FIELD_ERROR_CLASS to exception::class.java.simpleName,
                ),
            )
            null
        } catch (exception: SecurityException) {
            logger.warn(
                EVENT_WIDGET_PICKER_PREVIEW_UNAVAILABLE,
                mapOf(
                    FIELD_COMPONENT_PACKAGE to providerInfo.provider.packageName,
                    FIELD_ERROR_CLASS to exception::class.java.simpleName,
                ),
            )
            null
        }
    }

    private fun appShortcutPickerCategory(
        packageEntry: Map.Entry<String, List<DikcizLaunchableApp>>,
    ): WidgetPickerCategory? {
        val packageName = packageEntry.key
        val applicationInfo = try {
            context.packageManager.getApplicationInfo(packageName, NO_PACKAGE_MANAGER_FLAGS)
        } catch (exception: PackageManager.NameNotFoundException) {
            logger.warn(
                EVENT_APP_SHORTCUT_CATEGORY_SKIPPED,
                mapOf(FIELD_ERROR_CLASS to exception::class.java.simpleName),
            )
            return null
        }
        return WidgetPickerCategory(
            automationID = packageName,
            label = context.packageManager.getApplicationLabel(applicationInfo).toString(),
            icon = context.packageManager.getApplicationIcon(applicationInfo),
            entries = packageEntry.value.map(::AppWidgetPickerEntry),
        )
    }

    private companion object {
        const val EVENT_APP_SHORTCUT_CATEGORY_SKIPPED = "app_shortcut_category_skipped"
        const val EVENT_WIDGET_PICKER_CATEGORY_SKIPPED = "widget_picker_category_skipped"
        const val EVENT_WIDGET_PICKER_PREVIEW_UNAVAILABLE = "widget_picker_preview_unavailable"
        const val FIELD_COMPONENT_PACKAGE = "component_package"
        const val FIELD_ERROR_CLASS = "error_class"
        const val NO_PACKAGE_MANAGER_FLAGS = 0
        const val PICKER_CATEGORY_APPS = "apps"
        const val PICKER_CATEGORY_DIKCIZ = "dikciz"
    }
}
