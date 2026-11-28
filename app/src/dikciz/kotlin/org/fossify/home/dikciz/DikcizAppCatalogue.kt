package org.fossify.home.dikciz

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

private const val MATCHING_LABEL_ORDER = 0

internal data class DikcizLaunchableApp(
    val label: String,
    val component: ComponentName,
    val icon: Drawable,
)

internal object DikcizAppCatalogue {
    fun load(
        packageManager: PackageManager,
        launcherPackageName: String,
    ): List<DikcizLaunchableApp> = load(packageManager, launcherPackageName, null, emptySet())

    fun groupMembers(
        packageManager: PackageManager,
        launcherPackageName: String,
        savedComponents: List<String>,
    ): List<DikcizLaunchableApp> {
        val components = savedComponents.mapNotNull(ComponentName::unflattenFromString)
        val applications = components.groupBy { it.packageName }.flatMap { (packageName, members) ->
            load(packageManager, launcherPackageName, packageName, members.toSet())
        }.associateBy(DikcizLaunchableApp::component)
        return components.mapNotNull(applications::get)
    }

    private fun load(
        packageManager: PackageManager,
        launcherPackageName: String,
        packageFilter: String?,
        components: Set<ComponentName>,
    ): List<DikcizLaunchableApp> {
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            if (packageFilter != null) setPackage(packageFilter)
        }
        return packageManager.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
            .asSequence()
            .mapNotNull { resolveInfo ->
                val activityInfo = resolveInfo.activityInfo ?: return@mapNotNull null
                if (activityInfo.packageName == launcherPackageName) {
                    return@mapNotNull null
                }
                val component = ComponentName(activityInfo.packageName, activityInfo.name)
                if (components.isNotEmpty() && component !in components) {
                    return@mapNotNull null
                }
                val label = resolveInfo.loadLabel(packageManager).toString().trim()
                if (label.isEmpty()) {
                    return@mapNotNull null
                }
                DikcizLaunchableApp(
                    label = label,
                    component = component,
                    icon = resolveInfo.loadIcon(packageManager),
                )
            }
            .distinctBy { it.component.flattenToString() }
            .sortedWith(
                Comparator { first, second ->
                    val labelOrder = first.label.compareTo(second.label, ignoreCase = true)
                    if (labelOrder != MATCHING_LABEL_ORDER) {
                        labelOrder
                    } else {
                        first.component.flattenToString().compareTo(second.component.flattenToString())
                    }
                }
            )
            .toList()
    }

    fun find(
        packageManager: PackageManager,
        launcherPackageName: String,
        component: ComponentName,
    ): DikcizLaunchableApp? {
        return load(packageManager, launcherPackageName).firstOrNull { it.component == component }
    }

    fun matchesQuery(query: String, vararg values: String): Boolean {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) {
            return true
        }
        return values.any { value -> value.contains(normalizedQuery, ignoreCase = true) }
    }
}
