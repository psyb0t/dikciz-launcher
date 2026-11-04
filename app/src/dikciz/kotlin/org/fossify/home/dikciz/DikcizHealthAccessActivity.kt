package org.fossify.home.dikciz

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission

internal class DikcizHealthAccessActivity : Activity() {
    private val contract=PermissionController.createRequestPermissionResultContract()
    private lateinit var permissions: Set<String>
    private lateinit var status: TextView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val allowed=DikcizHealthRecords.kinds.values.flatMap {
            listOf(HealthPermission.getReadPermission(it.type),HealthPermission.getWritePermission(it.type))
        }.toSet()+setOf(HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND,HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY)
        permissions=intent.getStringArrayListExtra("permissions").orEmpty().toSet()
        if (permissions.isEmpty() || !allowed.containsAll(permissions)) { finish(); return }
        val body=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL; setPadding(32,48,32,32); setBackgroundColor(DikcizUiStyle.BACKGROUND)
        }
        status=TextView(this).apply { text="Review health data access"; textSize=24f; setTextColor(DikcizUiStyle.FOREGROUND) }
        body.addView(status)
        body.addView(TextView(this).apply {
            text="Only the requested types will be presented to Health Connect. Return here after reviewing access, then retry the original command.\n\n"+permissions.sorted().joinToString("\n") { it.removePrefix("android.permission.health.").lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase) }
            textSize=16f; setTextColor(DikcizUiStyle.FOREGROUND); setPadding(0,24,0,24)
        })
        body.addView(Button(this).apply {
            text="Review in Health Connect"; contentDescription="Review requested health access"; DikcizUiStyle.button(this,true)
            setOnClickListener {
                try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        requestPermissions(permissions.toTypedArray(),REQUEST)
                    } else startActivityForResult(contract.createIntent(this@DikcizHealthAccessActivity,permissions),REQUEST)
                } catch (exception: android.content.ActivityNotFoundException) {
                    status.text="Health Connect permission review is unavailable on this device"
                } catch (exception: SecurityException) {
                    status.text="Android rejected the requested health access"
                }
            }
        })
        body.addView(Button(this).apply { text="Close"; DikcizUiStyle.button(this); setOnClickListener { finish() } })
        setContentView(ScrollView(this).apply { addView(body) })
    }
    override fun onRequestPermissionsResult(requestCode: Int, requested: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode,requested,results)
        if (requestCode==REQUEST) {
            status.text=if (requested.isNotEmpty() && results.size==requested.size &&
                results.all { it==android.content.pm.PackageManager.PERMISSION_GRANTED }) "Requested access granted"
                else "Some access remains unavailable"
        }
    }
    @Deprecated("Android result bridge")
    override fun onActivityResult(requestCode: Int,resultCode: Int,data: Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if (requestCode==REQUEST) {
            val granted=contract.parseResult(resultCode,data)
            status.text=if (granted.containsAll(permissions)) "Requested access granted" else "Some access remains unavailable"
        }
    }
    private companion object { const val REQUEST=7311 }
}
