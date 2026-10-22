package org.fossify.home.dikciz

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.HandlerThread
import android.os.SystemClock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.json.JSONObject

internal object DikcizLocation {
    private const val DEFAULT_AGE = 300_000L
    private const val DEFAULT_TIMEOUT = 10_000L
    private const val GEOCODE_TIMEOUT = 2000L
    private const val NANOS_PER_MILLISECOND = 1_000_000L
    private const val MAXIMUM_PLACE_TEXT = 160

    fun read(context: Context, request: JSONObject, logger: DikcizLogger): JSONObject {
        val fine = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine) DikcizNativeCommands.requirePermission(context, Manifest.permission.ACCESS_COARSE_LOCATION, "locationForeground")
        val manager = context.getSystemService(LocationManager::class.java)
            ?: DikcizNativeCommands.failure(DikcizNativeCommands.ERROR_UNAVAILABLE, "Location service unavailable")
        val providers = manager.getProviders(true).filter { fine || it != LocationManager.GPS_PROVIDER }
        val maximumAge = request.optLong("maxAgeMilliseconds", DEFAULT_AGE)
        val cached = providers.mapNotNull { manager.getLastKnownLocation(it) }.maxByOrNull { it.elapsedRealtimeNanos }
        if (cached != null && age(cached) <= maximumAge) return document(context, cached, fine, "last_known", logger)
        val timeout = request.optLong("timeoutMilliseconds", DEFAULT_TIMEOUT)
        if (timeout == 0L) return unavailable(if (cached == null) "no_last_known_location" else "location_too_old")
        if (providers.isEmpty()) return unavailable("location_disabled")
        val fix = AtomicReference<Location?>()
        val complete = CountDownLatch(1)
        val thread = HandlerThread("dikciz-location-fix").apply { start() }
        val requestedAt = SystemClock.elapsedRealtimeNanos()
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (maximumAge == 0L && location.elapsedRealtimeNanos < requestedAt) return
                if (maximumAge > 0L && age(location) > maximumAge) return
                fix.set(Location(location))
                complete.countDown()
            }
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
            @Deprecated("Required on older Android")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }
        try {
            providers.forEach { manager.requestLocationUpdates(it, 0L, 0f, listener, thread.looper) }
            complete.await(timeout, TimeUnit.MILLISECONDS)
            val location = fix.get() ?: return unavailable("location_fix_timeout")
            return document(context, location, fine, "current_fix", logger)
        } finally {
            manager.removeUpdates(listener)
            thread.quitSafely()
        }
    }

    private fun age(location: Location): Long = maxOf(0L, SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / NANOS_PER_MILLISECOND

    private fun unavailable(reason: String): JSONObject = JSONObject().put("available", false).put("reason", reason)

    private fun document(context: Context, location: Location, fine: Boolean, source: String, logger: DikcizLogger): JSONObject = JSONObject()
        .put("available", true).put("source", source).put("provider", location.provider)
        .put("latitude", location.latitude).put("longitude", location.longitude)
        .put("accuracyMeters", if (location.hasAccuracy()) location.accuracy.toDouble() else JSONObject.NULL)
        .put("timestampMilliseconds", location.time).put("ageMilliseconds", age(location))
        .put("precision", if (fine) "precise" else "approximate")
        .put("placeName", placeName(context, location, logger) ?: JSONObject.NULL)

    private fun placeName(context: Context, location: Location, logger: DikcizLogger): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || !Geocoder.isPresent()) return null
        val result = AtomicReference<String?>()
        val complete = CountDownLatch(1)
        try {
            Geocoder(context).getFromLocation(location.latitude, location.longitude, 1, object : Geocoder.GeocodeListener {
            override fun onGeocode(addresses: MutableList<Address>) {
                addresses.firstOrNull()?.let { address ->
                    result.set(listOfNotNull(address.locality ?: address.subAdminArea, address.countryName)
                        .joinToString(", ").take(MAXIMUM_PLACE_TEXT).takeIf { it.isNotBlank() })
                }
                complete.countDown()
            }
            override fun onError(errorMessage: String?) { complete.countDown() }
        })
        } catch (exception: RuntimeException) {
            logger.warn("location_place_name_unavailable", mapOf("error_class" to exception.javaClass.simpleName))
            return null
        }
        complete.await(GEOCODE_TIMEOUT, TimeUnit.MILLISECONDS)
        return result.get()
    }
}
