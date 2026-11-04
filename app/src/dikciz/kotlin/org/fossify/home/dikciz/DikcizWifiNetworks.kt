package org.fossify.home.dikciz

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONArray
import org.json.JSONObject

internal object DikcizWifiNetworks {
    const val CONFIGURE="wifiNetworksConfigure"
    const val START="wifiConnectionStart"
    const val STATUS="wifiConnectionStatus"
    const val STOP="wifiConnectionStop"
    const val LIST="wifiConnectionsList"
    const val HOTSPOT_START="wifiHotspotStart"
    const val HOTSPOT_STATUS="wifiHotspotStatus"
    const val HOTSPOT_STOP="wifiHotspotStop"
    private const val MAX_REQUESTS=16
    private const val DEFAULT_TIMEOUT=30000
    private val handler=Handler(Looper.getMainLooper())
    private val timers=Executors.newSingleThreadScheduledExecutor()
    private data class Request(val id: String, val kind: String, val document: JSONObject,
        val active: AtomicBoolean=AtomicBoolean(true), var callback: ConnectivityManager.NetworkCallback?=null,
        var reservation: WifiManager.LocalOnlyHotspotReservation?=null)
    private val requests=ConcurrentHashMap<String,Request>()
    private fun manager(context: Context)=context.applicationContext.getSystemService(WifiManager::class.java)
        ?: DikcizNativeCommands.failure("native_unavailable","Wi-Fi service unavailable")
    private fun requireAccess(context: Context) {
        if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.TIRAMISU) DikcizNativeCommands.requirePermission(context,Manifest.permission.NEARBY_WIFI_DEVICES,"nearbyWifi")
        DikcizNativeCommands.requirePermission(context,Manifest.permission.ACCESS_FINE_LOCATION,"locationForeground")
    }
    private fun foreground(context: Context) {
        if (context !is Activity || !context.hasWindowFocus()) DikcizNativeCommands.failure("capture_foreground_required","Open and unlock Dikciz before requesting Wi-Fi connections")
    }
    private fun security(request: JSONObject): String {
        val value=request.optString("security","open")
        if (value !in setOf("open","wpa2","wpa3")) DikcizNativeCommands.invalid("Choose open, wpa2 or wpa3")
        val ssid=request.getString("ssid")
        if (ssid.toByteArray(Charsets.UTF_8).size !in 1..32 || ssid.any(Char::isISOControl)) DikcizNativeCommands.invalid("SSID must contain 1..32 UTF-8 bytes")
        if (value!="open") {
            val pass=request.optString("passphrase")
            if (pass.length !in 8..63 || pass.any { it.code !in 32..126 }) DikcizNativeCommands.invalid("Passphrase must contain 8..63 printable ASCII characters")
        } else if (request.has("passphrase")) DikcizNativeCommands.invalid("Open networks must not include a passphrase")
        return value
    }
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun suggestion(request: JSONObject): WifiNetworkSuggestion {
        val value=security(request)
        return WifiNetworkSuggestion.Builder().setSsid(request.getString("ssid")).apply {
            if (value=="wpa2") setWpa2Passphrase(request.getString("passphrase"))
            if (value=="wpa3") setWpa3Passphrase(request.getString("passphrase"))
        }.build()
    }
    private fun document(request: Request)=synchronized(request) { JSONObject(request.document.toString()).put("id",request.id).put("kind",request.kind).put("active",request.active.get()) }
    fun execute(context: Context,logger: DikcizLogger,type: String,request: JSONObject): JSONObject {
        if (Build.VERSION.SDK_INT<Build.VERSION_CODES.Q) DikcizNativeCommands.failure("unsupported_android_version","These Wi-Fi flows require Android 10 or newer")
        if (type==LIST) return DikcizNativeCommands.page(requests.values.sortedBy { it.id }.map(::document),request)
        if (type==STATUS) {
            val id=request.getString("id")
            requests[id]?.let { return document(it) }
            return DikcizCompanionStore(context).use { it.get("wifi-save",id) }
                ?: DikcizNativeCommands.failure("wifi_request_not_found","Wi-Fi request not found")
        }
        if (type==HOTSPOT_STATUS) return JSONObject().put("items",JSONArray(requests.values.filter { it.kind=="hotspot" }.map(::document)))
        if (type==STOP || type==HOTSPOT_STOP) {
            val row=requests[request.getString("id")] ?: DikcizNativeCommands.failure("wifi_request_not_found","Wi-Fi request not found")
            if (type==HOTSPOT_STOP && row.kind!="hotspot") DikcizNativeCommands.invalid("ID is not a hotspot")
            release(context,row,logger,"closed")
            return document(row)
        }
        if (type==START || type==CONFIGURE && request.optString("action")!="removeSuggestion") security(request)
        if (type==CONFIGURE && request.optString("action")=="removeSuggestion") {
            val ssid=request.getString("ssid")
            if (ssid.toByteArray(Charsets.UTF_8).size !in 1..32 || ssid.any(Char::isISOControl)) DikcizNativeCommands.invalid("Invalid SSID")
        }
        requireAccess(context)
        if (type==CONFIGURE) {
            val action=request.getString("action")
            val wifi=manager(context)
            if (action=="removeSuggestion") {
                if (Build.VERSION.SDK_INT<Build.VERSION_CODES.R) DikcizNativeCommands.failure("unsupported_android_version","Suggestion removal by SSID requires Android 11")
                val ssid=request.getString("ssid")
                val selected=wifi.networkSuggestions.filter { it.ssid==ssid }
                val status=wifi.removeNetworkSuggestions(selected)
                return JSONObject().put("state",if (status==WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) "removed" else "rejected").put("androidStatus",status).put("count",selected.size)
            }
            val suggestion=suggestion(request)
            if (action=="suggest") {
                val status=wifi.addNetworkSuggestions(listOf(suggestion))
                return JSONObject().put("state",if (status==WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS) "suggested" else "rejected")
                    .put("androidStatus",status).put("connected",false).put("instructions","Android chooses whether to use suggestions. Check actual network state.")
            }
            if (action!="save") DikcizNativeCommands.invalid("Choose suggest, removeSuggestion or save")
            if (Build.VERSION.SDK_INT<Build.VERSION_CODES.R) DikcizNativeCommands.failure("unsupported_android_version","Saving through Android approval requires Android 11")
            val id=UUID.randomUUID().toString()
            val state=JSONObject().put("id",id).put("ssid",request.getString("ssid")).put("state","user_action_required").put("createdAt",System.currentTimeMillis())
            DikcizCompanionStore(context).use { store ->
                val previous=store.all("wifi-save")
                previous.sortedBy { it.optLong("createdAt") }.take((previous.size-MAX_REQUESTS+1).coerceAtLeast(0)).forEach { store.delete("wifi-save",it.getString("id")) }
                store.put("wifi-save",id,state)
            }
            val intent=Intent(context,DikcizWifiApprovalActivity::class.java).setData(Uri.parse("dikciz-wifi://save/"+id))
                .putExtra("id",id).putParcelableArrayListExtra("networks",arrayListOf(suggestion)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (context is Activity && context.hasWindowFocus()) context.startActivity(intent)
            else {
                val notification=context.getSystemService(NotificationManager::class.java)
                notification.createNotificationChannel(NotificationChannel("dikciz-wifi-review","Wi-Fi approval",NotificationManager.IMPORTANCE_DEFAULT))
                if (notification.areNotificationsEnabled()) {
                    val pending=PendingIntent.getActivity(context,0,intent,PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                    notification.notify(id.hashCode(),Notification.Builder(context,"dikciz-wifi-review").setSmallIcon(android.R.drawable.ic_dialog_info)
                        .setContentTitle("Review Wi-Fi network").setContentText(request.getString("ssid")).setContentIntent(pending).setAutoCancel(true).build())
                }
            }
            return state.put("connected",false).put("instructions","Review the network on your phone. Read wifiConnectionStatus with this ID for the approval result.")
        }
        foreground(context)
        val id=UUID.randomUUID().toString()
        val duration=request.optLong("maximumDurationMilliseconds",300000)
        val row=Request(id,if (type==HOTSPOT_START) "hotspot" else "network",JSONObject().put("state","starting").put("createdAt",System.currentTimeMillis())
            .put("expiresAt",System.currentTimeMillis()+duration))
        synchronized(this) {
            if (requests.values.count { it.active.get() }>=4) DikcizNativeCommands.failure("wifi_request_capacity","At most four network requests can be active")
            if (requests.size>=MAX_REQUESTS) requests.values.filter { !it.active.get() }.take(requests.size-MAX_REQUESTS+1).forEach { requests.remove(it.id,it) }
            requests[id]=row
        }
        try {
            if (type==HOTSPOT_START) {
                if (requests.values.any { it.id!=id && it.kind=="hotspot" && it.active.get() }) DikcizNativeCommands.failure("hardware_busy","A hotspot request is already active")
                manager(context).startLocalOnlyHotspot(object : WifiManager.LocalOnlyHotspotCallback() {
                    override fun onStarted(reservation: WifiManager.LocalOnlyHotspotReservation) {
                        synchronized(row) {
                            if (!row.active.get()) { reservation.close(); return }
                            row.reservation=reservation
                            row.document.put("state","active").put("internetTethering",false)
                            if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.R) row.document.put("ssid",reservation.softApConfiguration.ssid)
                                .put("passphrase",reservation.softApConfiguration.passphrase ?: JSONObject.NULL)
                            else row.document.put("ssid",reservation.wifiConfiguration?.SSID ?: JSONObject.NULL)
                                .put("passphrase",reservation.wifiConfiguration?.preSharedKey ?: JSONObject.NULL)
                        }
                    }
                    override fun onStopped() { synchronized(row) { row.document.put("state","stopped"); row.active.set(false); row.reservation=null } }
                    override fun onFailed(reason: Int) { synchronized(row) { row.document.put("state","failed").put("androidReason",reason); row.active.set(false) } }
                },handler)
            } else {
                val mode=security(request)
                val specifier=WifiNetworkSpecifier.Builder().setSsid(request.getString("ssid")).apply {
                    if (mode=="wpa2") setWpa2Passphrase(request.getString("passphrase"))
                    if (mode=="wpa3") setWpa3Passphrase(request.getString("passphrase"))
                }.build()
                val network=NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).setNetworkSpecifier(specifier).build()
                val manager=context.getSystemService(ConnectivityManager::class.java)
                val callback=object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        synchronized(row) {
                            if (!row.active.get()) return
                            row.document.put("state","connected").put("networkHandle",network.networkHandle)
                                .put("defaultNetworkChanged",false).put("ssid",request.getString("ssid"))
                        }
                    }
                    override fun onUnavailable() { release(context,row,logger,"unavailable") }
                    override fun onLost(network: Network) { release(context,row,logger,"lost") }
                }
                row.callback=callback
                manager.requestNetwork(network,callback,request.optInt("timeoutMilliseconds",DEFAULT_TIMEOUT))
            }
            DikcizConnectionsService.start(context)
            timers.schedule({ release(context,row,logger,"expired") },duration,TimeUnit.MILLISECONDS)
            timers.schedule({
                synchronized(row) {
                    if (row.document.optString("state")=="starting") release(context,row,logger,"startup_timeout")
                }
            },request.optLong("timeoutMilliseconds",DEFAULT_TIMEOUT.toLong()),TimeUnit.MILLISECONDS)
            return document(row).put("instructions","Inspect this request's state; acceptance is not a connection. Local-only networks do not select the phone's internet default.")
        } catch (exception: SecurityException) {
            release(context,row,logger,"permission_lost")
            DikcizNativeCommands.failure("native_permission_nearbyWifi","Android Wi-Fi access was revoked or denied")
        } catch (exception: Exception) {
            release(context,row,logger,"failed")
            throw exception
        }
    }
    private fun release(context: Context,row: Request,logger: DikcizLogger,state: String) {
        if (!row.active.compareAndSet(true,false)) return
        synchronized(row) { row.document.put("state",state) }
        try { row.reservation?.close() } finally {
            row.reservation=null
            row.callback?.let {
                try { context.getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(it) }
                catch (exception: IllegalArgumentException) { logger.debug("wifi_request_already_released") }
            }
        }
    }
    fun completeSave(context: Context,id: String,resultCode: Int,results: IntArray?) {
        DikcizCompanionStore(context).use { store ->
            val value=store.get("wifi-save",id) ?: return
            value.put("state",if (resultCode==Activity.RESULT_OK && results?.all { it==0 }==true) "saved" else if (resultCode==Activity.RESULT_CANCELED) "cancelled" else "review_completed")
                .put("resultCode",resultCode).put("androidResults",results?.let { JSONArray(it.toList()) } ?: JSONObject.NULL).put("connected",false)
            store.put("wifi-save",id,value)
        }
    }
    fun hasActive()=requests.values.any { it.active.get() }
    fun closeAll(context: Context,logger: DikcizLogger) { requests.values.forEach { release(context,it,logger,"closed") } }
    private fun text(max: Int)=JSONObject().put("type","string").put("minLength",1).put("maxLength",max)
    private fun integer(min: Long,max: Long,default: Long)=JSONObject().put("type","integer").put("minimum",min).put("maximum",max).put("default",default)
    private val identity=mapOf("id" to text(80))
    private val network=mapOf("ssid" to text(32),"security" to text(16).put("enum",JSONArray(listOf("open","wpa2","wpa3"))),"passphrase" to text(63))
    private val lifetime=mapOf("maximumDurationMilliseconds" to integer(1000,3600000,300000),"timeoutMilliseconds" to integer(1000,120000,DEFAULT_TIMEOUT.toLong()))
    val DEFINITIONS=listOf(
        DikcizNativeCommands.Definition(CONFIGURE,"dikciz_wifi_networks_configure","Suggest a Wi-Fi network, remove this app's suggestions by SSID, or ask Android to save a network. Suggestions are not saved networks and never prove a connection. save requires owner approval and exposes its result through wifiConnectionStatus. Credentials are not logged or stored in public configuration.",
            network+mapOf("action" to text(32).put("enum",JSONArray(listOf("suggest","removeSuggestion","save")))),setOf("action","ssid"),false),
        DikcizNativeCommands.Definition(START,"dikciz_wifi_connection_start","Request an owner-approved local-only Wi-Fi connection and retain its callback. Android may reject or time out. Poll wifiConnectionStatus. A returned networkHandle identifies this process-scoped network; the phone's default internet route is not changed. Start with Dikciz foreground.",
            network+lifetime,setOf("ssid"),false),
        DikcizNativeCommands.Definition(STATUS,"dikciz_wifi_connection_status","Read actual callback state for a network request or Android save approval; does not connect.",identity,setOf("id")),
        DikcizNativeCommands.Definition(STOP,"dikciz_wifi_connection_stop","Release the exact local network request; does not remove saved Wi-Fi networks.",identity,setOf("id"),false),
        DikcizNativeCommands.Definition(LIST,"dikciz_wifi_connections_list","List retained local network/hotspot requests with pagination. Hotspot credentials are owner-only.",
            mapOf("offset" to integer(0,1000000,0),"limit" to integer(1,200,50))),
        DikcizNativeCommands.Definition(HOTSPOT_START,"dikciz_wifi_hotspot_start","Start a bounded Android local-only hotspot while foreground. Android selects SSID/passphrase and can reject the request. Poll wifiHotspotStatus. This does not enable cellular internet tethering, which ordinary apps cannot silently control.",lifetime,readOnly=false),
        DikcizNativeCommands.Definition(HOTSPOT_STATUS,"dikciz_wifi_hotspot_status","Read retained local-only hotspot state and owner credentials. Starting requires wifiHotspotStart.",emptyMap()),
        DikcizNativeCommands.Definition(HOTSPOT_STOP,"dikciz_wifi_hotspot_stop","Close an exact local-only hotspot reservation.",identity,setOf("id"),false),
    )
    val TYPES=DEFINITIONS.map { it.type }.toSet()
}
