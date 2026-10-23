package org.fossify.home.dikciz

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.GnssStatus
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONObject

internal class DikcizLocationServices(private val context: Context) {
    fun geocode(request: JSONObject): JSONObject {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) DikcizNativeCommands.failure("unsupported_android_version", "Bounded asynchronous geocoding requires Android 13 or newer")
        if (!Geocoder.isPresent()) DikcizNativeCommands.failure("geocoder_unavailable", "Android has no installed geocoding provider")
        val complete = CountDownLatch(1)
        val addresses = AtomicReference<List<Address>?>()
        val failed = AtomicReference(false)
        val callback = object : Geocoder.GeocodeListener {
            override fun onGeocode(result: MutableList<Address>) { addresses.set(result); complete.countDown() }
            override fun onError(errorMessage: String?) { failed.set(true); complete.countDown() }
        }
        val locale = if (request.has(KEY_LANGUAGE)) Locale.forLanguageTag(request.getString(KEY_LANGUAGE)) else Locale.getDefault()
        val geocoder = Geocoder(context, locale)
        val maximum = request.optInt("limit", DEFAULT_RESULTS)
        if (request.getString(KEY_ACTION) == ACTION_FORWARD) {
            geocoder.getFromLocationName(request.getString(KEY_QUERY), maximum, callback)
        } else {
            geocoder.getFromLocation(request.getDouble(KEY_LATITUDE), request.getDouble(KEY_LONGITUDE), maximum, callback)
        }
        if (!complete.await(request.optLong(KEY_TIMEOUT, DEFAULT_TIMEOUT), TimeUnit.MILLISECONDS)) {
            DikcizNativeCommands.failure("geocoder_timeout", "Android geocoder did not finish before the deadline")
        }
        if (failed.get()) DikcizNativeCommands.failure("geocoder_failed", "Android geocoding provider failed")
        val rows = addresses.get().orEmpty().take(maximum).map { address ->
            JSONObject().put(KEY_LATITUDE, if (address.hasLatitude()) address.latitude else JSONObject.NULL)
                .put(KEY_LONGITUDE, if (address.hasLongitude()) address.longitude else JSONObject.NULL)
                .put("address", (if (address.maxAddressLineIndex >= 0) address.getAddressLine(0)?.take(MAXIMUM_TEXT) else null) ?: JSONObject.NULL)
                .put("locality", address.locality?.take(MAXIMUM_TEXT) ?: JSONObject.NULL)
                .put("administrativeArea", address.adminArea?.take(MAXIMUM_TEXT) ?: JSONObject.NULL)
                .put("countryCode", address.countryCode?.take(MAXIMUM_TEXT) ?: JSONObject.NULL)
                .put("countryName", address.countryName?.take(MAXIMUM_TEXT) ?: JSONObject.NULL)
                .put("postalCode", address.postalCode?.take(MAXIMUM_TEXT) ?: JSONObject.NULL)
        }
        return JSONObject().put("items", org.json.JSONArray(rows)).put("limit", maximum).put("hasMore", false)
            .put("source", "android_geocoder").put(KEY_LANGUAGE, locale.toLanguageTag())
    }

    fun gnss(request: JSONObject): JSONObject {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            DikcizNativeCommands.failure("native_permission_locationForeground", "Precise location access is required for GNSS status")
        }
        val manager = context.getSystemService(LocationManager::class.java)
            ?: DikcizNativeCommands.failure(DikcizNativeCommands.ERROR_UNAVAILABLE, "Location service unavailable")
        if (!manager.allProviders.contains(LocationManager.GPS_PROVIDER)) return unavailable("no_gps_provider")
        val thread = HandlerThread("dikciz-gnss-status").apply { start() }
        val result = AtomicReference<GnssStatus?>()
        val complete = CountDownLatch(1)
        val callback = object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) { result.set(status); complete.countDown() }
        }
        try {
            if (!manager.registerGnssStatusCallback(callback, Handler(thread.looper))) return unavailable("registration_rejected")
            complete.await(request.optLong(KEY_TIMEOUT, 1000), TimeUnit.MILLISECONDS)
            val status = result.get() ?: return unavailable("no_status_during_window")
            val rows = (0 until status.satelliteCount).map { index ->
                JSONObject().put("svid", status.getSvid(index)).put("constellationType", status.getConstellationType(index))
                    .put("cn0DbHz", status.getCn0DbHz(index).toDouble()).put("elevationDegrees", status.getElevationDegrees(index).toDouble())
                    .put("azimuthDegrees", status.getAzimuthDegrees(index).toDouble()).put("usedInFix", status.usedInFix(index))
                    .put("hasEphemeris", status.hasEphemerisData(index)).put("hasAlmanac", status.hasAlmanacData(index))
                    .put("carrierFrequencyHz", if (status.hasCarrierFrequencyHz(index)) status.getCarrierFrequencyHz(index).toDouble() else JSONObject.NULL)
            }
            return DikcizNativeCommands.page(rows, request).put("available", true).put("total", rows.size)
                .put("observedAtMilliseconds", System.currentTimeMillis())
        } finally {
            manager.unregisterGnssStatusCallback(callback)
            thread.quitSafely()
        }
    }

    private fun unavailable(reason: String) = JSONObject().put("available", false).put("reason", reason)

    companion object {
        const val TYPE_GEOCODE = "geocode"
        const val TYPE_GNSS = "gnssStatus"
        private const val KEY_ACTION = "action"
        private const val KEY_QUERY = "query"
        private const val KEY_LATITUDE = "latitude"
        private const val KEY_LONGITUDE = "longitude"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_TIMEOUT = "timeoutMilliseconds"
        private const val ACTION_FORWARD = "forward"
        private const val ACTION_REVERSE = "reverse"
        private const val DEFAULT_RESULTS = 5
        private const val DEFAULT_TIMEOUT = 30_000L
        private const val MAXIMUM_TEXT = 1024
        val DEFINITIONS = listOf(
            DikcizNativeCommands.Definition(TYPE_GEOCODE, "dikciz_geocode", "Forward/reverse geocode through the installed Android provider with a bounded wait. The provider may send query/coordinates to its network service. Explicit failure when unavailable. Android 13+.", mapOf(
                KEY_ACTION to text("forward or reverse.", 16), KEY_QUERY to text("Place/address for forward lookup.", MAXIMUM_TEXT),
                KEY_LATITUDE to number(-90.0,90.0), KEY_LONGITUDE to number(-180.0,180.0), KEY_LANGUAGE to text("Optional BCP-47 language.",64),
                "limit" to integer(1,10,DEFAULT_RESULTS), KEY_TIMEOUT to integer(1,120000,30000)), setOf(KEY_ACTION)),
            DikcizNativeCommands.Definition(TYPE_GNSS, "dikciz_gnss_status", "Observe one GNSS satellite-status callback during a bounded window. Does not start GPS or invent a fix. Requires fine location; no status is explicit. Registrations are removed after the request.", mapOf(
                "limit" to integer(1,DikcizNativeCommands.MAXIMUM_LIMIT,DikcizNativeCommands.DEFAULT_LIMIT), "offset" to integer(0,DikcizNativeCommands.MAXIMUM_OFFSET,0), KEY_TIMEOUT to integer(1,5000,1000))),
        )

        fun validate(request: JSONObject) {
            val forward = request.getString(KEY_ACTION) == ACTION_FORWARD
            if (request.getString(KEY_ACTION) !in setOf(ACTION_FORWARD,ACTION_REVERSE)) DikcizNativeCommands.invalid("Unsupported geocoding action")
            if (forward) {
                if (!request.has(KEY_QUERY) || request.getString(KEY_QUERY).isBlank() || request.has(KEY_LATITUDE) || request.has(KEY_LONGITUDE)) DikcizNativeCommands.invalid("Forward geocoding requires query only")
            } else if (!request.has(KEY_LATITUDE) || !request.has(KEY_LONGITUDE) || request.has(KEY_QUERY)) {
                DikcizNativeCommands.invalid("Reverse geocoding requires latitude and longitude")
            }
            if (request.has(KEY_LANGUAGE) && !Regex("[A-Za-z]{2,8}(-[A-Za-z0-9]{1,8})*").matches(request.getString(KEY_LANGUAGE))) DikcizNativeCommands.invalid("Invalid language tag")
        }
        private fun text(description: String, maximum: Int) = JSONObject().put("type","string").put("description",description).put("maxLength",maximum)
        private fun integer(minimum: Int, maximum: Int, default: Int) = JSONObject().put("type","integer").put("minimum",minimum).put("maximum",maximum).put("default",default)
        private fun number(minimum: Double, maximum: Double) = JSONObject().put("type","number").put("minimum",minimum).put("maximum",maximum)
    }
}
