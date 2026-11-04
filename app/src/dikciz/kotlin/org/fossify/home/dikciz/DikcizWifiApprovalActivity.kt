package org.fossify.home.dikciz

import android.app.Activity
import android.content.Intent
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

internal class DikcizWifiApprovalActivity : Activity() {
    private lateinit var requestId: String
    private lateinit var status: TextView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestId=intent.getStringExtra("id") ?: run { finish(); return }
        val pending=DikcizCompanionStore(this).use { it.get("wifi-save",requestId) }
        if (pending==null || System.currentTimeMillis()-pending.optLong("createdAt")>900000 || pending.optString("state")!="user_action_required") {
            finish(); return
        }
        val body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(32,64,32,32); setBackgroundColor(DikcizUiStyle.BACKGROUND) }
        status=TextView(this).apply { text="Save "+pending.getString("ssid")+"?"; textSize=24f; setTextColor(DikcizUiStyle.FOREGROUND) }
        body.addView(status)
        body.addView(TextView(this).apply { text="Android will ask you to approve this saved network. Saving does not guarantee a connection."; textSize=16f; setTextColor(DikcizUiStyle.FOREGROUND); setPadding(0,24,0,24) })
        body.addView(Button(this).apply {
            text="Review in Android"; contentDescription="Review Wi-Fi network"; DikcizUiStyle.button(this,true)
            setOnClickListener {
                val current=DikcizCompanionStore(this@DikcizWifiApprovalActivity).use { it.get("wifi-save",requestId) }
                if (current==null || current.optString("state")!="user_action_required" ||
                    System.currentTimeMillis()-current.optLong("createdAt") !in 0..900000) {
                    status.text="This Wi-Fi request expired. Start a new request."
                    isEnabled=false
                    return@setOnClickListener
                }
                if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.R) {
                    val networks=intent.getParcelableArrayListExtra<WifiNetworkSuggestion>("networks")
                    startActivityForResult(Intent(Settings.ACTION_WIFI_ADD_NETWORKS).putParcelableArrayListExtra(Settings.EXTRA_WIFI_NETWORK_LIST,networks),REQUEST)
                }
            }
        })
        body.addView(Button(this).apply { text="Cancel"; DikcizUiStyle.button(this); setOnClickListener {
            DikcizWifiNetworks.completeSave(this@DikcizWifiApprovalActivity,requestId,RESULT_CANCELED,null); finish()
        } })
        setContentView(body)
    }
    @Deprecated("Android result bridge")
    override fun onActivityResult(requestCode: Int,resultCode: Int,data: Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if (requestCode==REQUEST) {
            DikcizWifiNetworks.completeSave(this,requestId,resultCode,data?.getIntArrayExtra(Settings.EXTRA_WIFI_NETWORK_RESULT_LIST))
            status.text=if (resultCode==RESULT_OK) "Android review completed" else "Network was not saved"
        }
    }
    private companion object { const val REQUEST=7312 }
}
