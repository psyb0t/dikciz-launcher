package org.fossify.home.dikciz

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.SystemClock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONObject

internal class DikcizWifiControl(
    private val context: Context,
    private val cachedResults: (JSONObject) -> JSONObject,
) {
    // The shared permission helper checks fine location before Wi-Fi calls.
    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun scan(request: JSONObject): JSONObject {
        DikcizNativeCommands.requirePermission(context, Manifest.permission.ACCESS_FINE_LOCATION, "locationForeground")
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
            ?: DikcizNativeCommands.failure(DikcizNativeCommands.ERROR_UNAVAILABLE, "Wi-Fi service unavailable")
        if (!wifi.isWifiEnabled && !wifi.isScanAlwaysAvailable) {
            DikcizNativeCommands.failure("wifi_scan_disabled", "Enable Wi-Fi or Android Wi-Fi scanning before requesting a scan")
        }
        val location = context.getSystemService(LocationManager::class.java)
        val locationEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) location?.isLocationEnabled == true
            else location?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true || location?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
        if (!locationEnabled) DikcizNativeCommands.failure("wifi_location_disabled", "Enable Android location services for Wi-Fi scan results")
        if (!busy.compareAndSet(false, true)) DikcizNativeCommands.failure("hardware_busy", "A Dikciz Wi-Fi scan request is already waiting")
        val completed = CountDownLatch(1)
        val active = AtomicBoolean(true)
        val updated = AtomicBoolean()
        val requestedAt = SystemClock.elapsedRealtimeNanos() / NANOSECONDS_PER_MICROSECOND
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (!active.get() || intent.action != WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) return
                updated.set(intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false))
                completed.countDown()
            }
        }
        var registered = false
        try {
            val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            else context.registerReceiver(receiver, filter)
            registered = true
            if (!wifi.startScan()) {
                DikcizNativeCommands.failure("wifi_scan_rejected", "Android rejected the scan request; throttling, radio policy or access restrictions may apply")
            }
            if (!completed.await(request.optLong(KEY_TIMEOUT, DEFAULT_TIMEOUT), TimeUnit.MILLISECONDS)) {
                DikcizNativeCommands.failure("wifi_scan_timeout", "No scan-completion broadcast arrived before the deadline")
            }
            if (!updated.get()) DikcizNativeCommands.failure("wifi_scan_failed", "Android reported that scan results were not updated")
            val result = cachedResults(request)
            val items = result.getJSONArray("items")
            for (index in 0 until items.length()) {
                val item = items.getJSONObject(index)
                item.put("observedAfterRequest", item.getLong("timestampMicroseconds") >= requestedAt)
            }
            return result.put("state", "observed").put("source", "scan_completion_cache")
                .put("requestedAtElapsedMicroseconds", requestedAt)
                .put("completedAtElapsedMicroseconds", SystemClock.elapsedRealtimeNanos() / NANOSECONDS_PER_MICROSECOND)
        } finally {
            active.set(false)
            try { if (registered) context.unregisterReceiver(receiver) }
            finally { busy.set(false) }
        }
    }

    companion object {
        const val TYPE = "wifiScan"
        private const val KEY_TIMEOUT = "timeoutMilliseconds"
        private const val DEFAULT_TIMEOUT = 30_000L
        private const val NANOSECONDS_PER_MICROSECOND = 1000L
        private val busy = AtomicBoolean()
        val DEFINITIONS = listOf(DikcizNativeCommands.Definition(
            TYPE, "dikciz_wifi_scan",
            "Request an Android Wi-Fi scan, wait for an updated-results broadcast and return the paginated cache with per-result freshness. Android can throttle or reject scans. Completion broadcasts are global, not caller-specific. Timeout releases the receiver but cannot cancel the platform scan. Fine location and enabled location services are required.",
            mapOf(
                KEY_TIMEOUT to integer(1, 120000).put("default", DEFAULT_TIMEOUT),
                "offset" to integer(0, DikcizNativeCommands.MAXIMUM_OFFSET).put("default", 0),
                "limit" to integer(1, DikcizNativeCommands.MAXIMUM_LIMIT).put("default", DikcizNativeCommands.DEFAULT_LIMIT),
            ), readOnly = false,
        ))

        private fun integer(minimum: Int, maximum: Int) = JSONObject().put("type", "integer").put("minimum", minimum).put("maximum", maximum)
    }
}
