package org.fossify.home.dikciz

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.MediaStore
import android.provider.Settings

internal object DikcizStarterApps {
    private const val HOME_PAGE = "home"
    private const val DOCK_ITEMS = 4
    private val STORE_PACKAGES = listOf("dev.imranr.obtainium", "org.fdroid.fdroid", "com.android.vending")

    fun populate(context: Context, configuration: HomeConfiguration): HomeConfiguration {
        val applications = DikcizAppCatalogue.load(context.packageManager, context.packageName)
        val slots = listOf(
            findHandler(context, Intent(Intent.ACTION_DIAL), applications),
            findHandler(context, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA), applications),
            findHandler(context, Intent(Settings.ACTION_SETTINGS), applications),
            STORE_PACKAGES.firstNotNullOfOrNull { packageName ->
                applications.firstOrNull { it.component.packageName == packageName }
            },
        )
        val span = (configuration.nativeGrid.columns / DOCK_ITEMS).coerceAtLeast(1)
        val shortcuts = slots.mapIndexedNotNull { index, application ->
            application ?: return@mapIndexedNotNull null
            AppHomeWidget(
                id = "dock-$index",
                title = application.label,
                component = application.component.flattenToString(),
                enabled = true,
                style = DikcizStyle(padding = DikcizBoxEdges(all = 16)),
                cell = DikcizGridRectangle(
                    column = index * span,
                    row = configuration.nativeGrid.rows - 1,
                    columnSpan = span,
                    rowSpan = 1,
                ),
            )
        }
        return configuration.copy(pages = configuration.pages.map { page ->
            if (page.id == HOME_PAGE) page.copy(widgets = page.widgets + shortcuts) else page
        })
    }

    private fun findHandler(
        context: Context,
        intent: Intent,
        applications: List<DikcizLaunchableApp>,
    ): DikcizLaunchableApp? {
        val manager = context.packageManager
        val preferred = manager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        applications.firstOrNull { it.component.packageName == preferred }?.let { return it }
        val handlers = manager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .mapNotNull { it.activityInfo?.packageName }
        return handlers.firstNotNullOfOrNull { packageName ->
            applications.firstOrNull { it.component.packageName == packageName }
        }
    }
}
