package org.fossify.home.dikciz

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Base64
import java.io.ByteArrayOutputStream
import org.json.JSONObject

internal object DikcizAppIcon {
    fun read(context: Context, request: JSONObject): JSONObject {
        val component = ComponentName.unflattenFromString(request.getString(DikcizNativeCommands.KEY_COMPONENT))
            ?: DikcizNativeCommands.invalid("Invalid app component")
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .setPackage(component.packageName)
        val application = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .firstOrNull { resolved ->
                val info = resolved.activityInfo
                info != null && info.packageName != context.packageName &&
                    info.packageName == component.packageName && info.name == component.className
            } ?: DikcizNativeCommands.failure(DikcizNativeCommands.ERROR_NOT_FOUND, "Launchable app was not found")
        val size = request.optInt(DikcizNativeCommands.KEY_ICON_SIZE, DikcizNativeCommands.DEFAULT_ICON_SIZE)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val bytes = try {
            val icon = application.loadIcon(context.packageManager).mutate()
            icon.setBounds(0, 0, size, size)
            icon.draw(Canvas(bitmap))
            ByteArrayOutputStream().use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, output)) {
                    DikcizNativeCommands.failure(DikcizNativeCommands.ERROR_FAILED, "Could not encode app icon")
                }
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
        return JSONObject()
            .put(DikcizNativeCommands.KEY_COMPONENT, component.flattenToString())
            .put("mimeType", PNG_MIME_TYPE)
            .put("width", size)
            .put("height", size)
            .put("pngBase64", Base64.encodeToString(bytes, Base64.NO_WRAP))
    }

    private const val PNG_QUALITY = 100
    private const val PNG_MIME_TYPE = "image/png"
}
