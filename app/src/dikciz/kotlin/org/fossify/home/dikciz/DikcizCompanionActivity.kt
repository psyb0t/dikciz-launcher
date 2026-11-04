package org.fossify.home.dikciz

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import java.lang.ref.WeakReference
import java.util.UUID
import java.util.concurrent.Executors
import org.json.JSONArray
import org.json.JSONObject

internal class DikcizCompanionActivity : Activity() {
    private val worker=Executors.newSingleThreadExecutor()
    private val handler=Handler(Looper.getMainLooper())
    private val views=linkedMapOf<String,View>()
    private val fields=linkedMapOf<String,EditText>()
    private lateinit var content: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var status: TextView
    private var tab="Safety"
    private var mode=""
    private var draft=JSONObject()
    private var contacts=JSONArray()
    private var selectedImages=mutableListOf<String>()
    private var modal: AlertDialog?=null
    private var generation=0
    private var busy=false
    private var visible=false
    private var labelId=0
    private var safetyRun: JSONObject?=null
    private var safetyLive: TextView?=null
    private var polling=false
    private val refreshSafety=object : Runnable {
        override fun run() {
            if (!visible) return
            val run=safetyRun
            val remaining=run?.optLong("deadlineElapsed",0)?.minus(android.os.SystemClock.elapsedRealtime()) ?: 0
            safetyLive?.text=if (run?.optString("state") in setOf("armed","warning","active")) {
                run!!.getString("state").replace('_',' ')+" · next deadline in "+((remaining.coerceAtLeast(0)+999)/1000)+" seconds"
            } else run?.optString("state","Not armed")?.replace('_',' ') ?: "Not armed"
            if (tab=="Safety" && mode.isEmpty() && !busy && !polling && modal?.isShowing!=true) {
                val expected=generation
                polling=true
                worker.execute {
                    try {
                        val result=DikcizUiCommands.execute("settings:companion",DikcizJourneys.STATUS,JSONObject())
                        handler.post {
                            polling=false
                            if (!visible || isDestroyed || expected!=generation) return@post
                            val next=result.optJSONObject("run")
                            val changed=next?.optString("state")!=safetyRun?.optString("state")
                            safetyRun=next
                            if (changed) render()
                        }
                    } catch (exception: Exception) {
                        handler.post { polling=false; if (visible) status.text="Safety status could not refresh. Open Activity for details." }
                    }
                }
            }
            handler.postDelayed(this,1000)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tab=savedInstanceState?.getString("tab") ?: "Safety"
        mode=savedInstanceState?.getString("mode") ?: ""
        draft=JSONObject(savedInstanceState?.getString("draft") ?: "{}")
        contacts=JSONArray(savedInstanceState?.getString("contacts") ?: "[]")
        selectedImages=savedInstanceState?.getStringArrayList("images")?.toMutableList() ?: mutableListOf()
        WindowCompat.setDecorFitsSystemWindows(window,false)
        val root=column().apply { setBackgroundColor(DikcizUiStyle.BACKGROUND); setPadding(dp(20),dp(12),dp(20),0) }
        val header=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL }
        header.addView(label("Phone companion",24),LinearLayout.LayoutParams(0,-2,1f))
        header.addView(button("companion:close","Close") { finish() })
        root.addView(header)
        val tabs=LinearLayout(this)
        for (name in TABS) tabs.addView(button("companion:tab:"+name.lowercase(),name) {
            mode=""; draft=JSONObject(); tab=name; render()
        })
        root.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled=false; addView(tabs) })
        status=label("",14).apply { accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE }
        views["companion:status"]=status; root.addView(status)
        content=column()
        scroll=ScrollView(this).apply { addView(content); isFillViewport=true; contentDescription="Companion content" }
        root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view,insets ->
            val bars=insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val keyboard=insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(dp(20)+bars.left,dp(12)+bars.top,dp(20)+bars.right,maxOf(bars.bottom,keyboard.bottom)); insets
        }
        render()
    }
    override fun onResume() { super.onResume(); foreground=WeakReference(this); visible=true; handler.post(refreshSafety) }
    override fun onPause() { visible=false; handler.removeCallbacks(refreshSafety); if (foreground?.get()===this) foreground=null; super.onPause() }
    override fun onDestroy() { worker.shutdown(); super.onDestroy() }
    override fun onSaveInstanceState(out: Bundle) {
        captureDraft()
        out.putString("tab",tab); out.putString("mode",mode); out.putString("draft",draft.toString())
        out.putString("contacts",contacts.toString()); out.putStringArrayList("images",ArrayList(selectedImages))
        super.onSaveInstanceState(out)
    }
    private fun captureDraft() { fields.forEach { (key,value) -> if (key!="passphrase") draft.put(key,value.text.toString()) } }
    private fun render() {
        generation++
        fields.clear(); labelId=0
        TABS.forEach { name ->
            (views["companion:tab:"+name.lowercase()] as? Button)?.let {
                it.isSelected=name==tab
                DikcizUiStyle.button(it,name==tab)
            }
        }
        views.keys.filter { !it.startsWith("companion:tab:") && it !in setOf("companion:close","companion:status") }.forEach(views::remove)
        content.removeAllViews(); safetyLive=null; scroll.scrollTo(0,0)
        if (mode.isNotEmpty()) {
            content.addView(button("companion:back","Back") { mode=""; draft=JSONObject(); render() })
            when (mode) {
                "plan" -> planForm()
                "fence" -> fenceForm()
                "connection" -> connectionForm()
                "wifi" -> wifiForm()
                "route" -> route()
            }
            return
        }
        when (tab) {
            "Safety" -> safety()
            "Places" -> places()
            "Trips" -> trips()
            "Devices" -> devices()
            "Scan" -> scan()
            "Health" -> health()
            "Wi-Fi" -> wifi()
        }
    }
    private fun request(type: String,arguments: JSONObject=JSONObject(),done: (JSONObject)->Unit) {
        if (busy) { status.text="Wait for the current operation"; return }
        val expected=generation
        busy=true; status.text="Working…"
        worker.execute {
            try {
                val result=DikcizUiCommands.execute("settings:companion",type,arguments)
                handler.post {
                    busy=false
                    if (isDestroyed || isFinishing) return@post
                    if (expected!=generation) { render(); return@post }
                    status.text=result.optString("state","Ready").replace('_',' ').replaceFirstChar(Char::uppercase)
                    done(result)
                }
            } catch (exception: Exception) {
                handler.post {
                    busy=false
                    if (!isDestroyed) status.text=exception.message ?: "Operation failed. Check Activity and resources."
                }
            }
        }
    }
    private fun safety() {
        content.addView(label("Check-ins and SOS",22))
        content.addView(label("Choose exact contacts, review a message and arm monitoring when you need it. Android background restrictions still apply. No calls are placed.",15))
        request(DikcizJourneys.STATUS) { result ->
            val plan=result.optJSONObject("plan")
            val run=result.optJSONObject("run")
            safetyRun=run
            val state=run?.optString("state") ?: "not armed"
            safetyLive=label(state.replace('_',' '),18).also { it.visibility=if (state in setOf("armed","warning","active")) View.VISIBLE else View.GONE; content.addView(it) }
            card("Current state",state.replace('_',' ')).apply {
                run?.optLong("deadlineAt",0)?.takeIf { it>0 && state in setOf("armed","warning","active") }?.let { addView(label("Next deadline: "+java.time.Instant.ofEpochMilli(it),14)) }
                addView(button("companion:sos:edit","Edit safety plan") {
                    draft=plan?.let { JSONObject(it.toString()) } ?: JSONObject()
                    contacts=plan?.optJSONArray("contacts")?.let { JSONArray(it.toString()) } ?: JSONArray()
                    mode="plan"; render()
                })
            }
            if (plan==null) return@request
            val recipients=plan.getJSONArray("contacts")
            card("Contacts", (0 until recipients.length()).joinToString("\n") {
                recipients.getJSONObject(it).optString("label",recipients.getJSONObject(it).getString("address"))
            })
            content.addView(button("companion:sos:preview","Preview message") {
                request(DikcizJourneys.PREVIEW) { preview ->
                    show(AlertDialog.Builder(this).setTitle("Preview only")
                        .setMessage(preview.getString("message")+"\n\nNo SMS was submitted.").setPositiveButton("Close",null).show())
                }
            })
            if (state in setOf("armed","warning","active")) {
                content.addView(button("companion:sos:check-in","I'm safe. Reset check-in") { request(DikcizJourneys.CONTROL,JSONObject().put("action","checkIn")) { render() } })
                content.addView(button("companion:sos:cancel","Cancel future SOS updates") { request(DikcizJourneys.CONTROL,JSONObject().put("action","cancel")) { render() } })
            } else {
                content.addView(button("companion:sos:arm","Arm missed check-in",true) {
                    confirm("Arm check-in?", "If you miss the deadline, the cancellation grace runs before SMS updates go to these contacts.") {
                        request(DikcizJourneys.CONTROL,JSONObject().put("action","arm").put("confirm",true).put("autoRequestPermissions",true)) { render() }
                    }
                })
                content.addView(button("companion:sos:trigger","Start SOS countdown") {
                    confirm("Start SOS?", "SMS updates begin after "+plan.getInt("graceSeconds")+" seconds. You can cancel before then.") {
                        request(DikcizJourneys.CONTROL,JSONObject().put("action","trigger").put("confirm",true).put("autoRequestPermissions",true)) { render() }
                    }
                })
            }
            run?.optJSONArray("receipts")?.let { receipts ->
                if (receipts.length()>0) card("Submitted updates",receipts.length().toString()+" recipient outcomes. Delivery remains dependent on carrier callbacks.")
            }
        }
    }
    private fun planForm() {
        content.addView(label("Safety plan",22))
        content.addView(label("Saving stays inactive. Arming and triggering are separate actions.",15))
        for (index in 0 until contacts.length()) {
            val contact=contacts.getJSONObject(index)
            card(contact.optString("label","Contact"),contact.getString("address")).addView(button("companion:contact:remove:"+index,"Remove contact") {
                captureDraft(); contacts.remove(index); render()
            })
        }
        content.addView(button("companion:contact:pick","Choose contact") {
            captureDraft()
            startActivityForResult(Intent(Intent.ACTION_PICK,ContactsContract.CommonDataKinds.Phone.CONTENT_URI),CONTACT_REQUEST)
        })
        content.addView(button("companion:contact:manual","Enter a number") { manualContact() })
        input("checkInMinutes","Check-in interval, minutes",(draft.optLong("checkInSeconds",3600)/60).toString(),true)
        input("graceSeconds","Cancellation grace, seconds",draft.optInt("graceSeconds",60).toString(),true)
        input("updateMinutes","Update interval, minutes",(draft.optLong("updateSeconds",600)/60).toString(),true)
        input("maximumUpdates","Maximum updates per recipient",draft.optInt("maximumUpdates",3).toString(),true)
        input("message","Message",draft.optString("message","I need help. Last known location: {location}. Accuracy: {accuracyMeters} m. Fix time: {timestamp}."),multiline=true)
        content.addView(button("companion:plan:save","Save inactive plan",true) {
            val plan=JSONObject().put("contacts",contacts).put("checkInSeconds",long("checkInMinutes")*60)
                .put("graceSeconds",long("graceSeconds")).put("updateSeconds",long("updateMinutes")*60)
                .put("maximumUpdates",long("maximumUpdates")).put("message",text("message"))
            draft.opt("subscriptionId")?.let { plan.put("subscriptionId",it) }
            request(DikcizJourneys.PLAN,plan) { mode=""; render() }
        })
    }
    private fun manualContact() {
        val body=column(); val name=EditText(this).apply { hint="Name"; contentDescription="Contact label" }
        val phone=EditText(this).apply { hint="Phone number"; contentDescription="Contact number"; inputType=InputType.TYPE_CLASS_PHONE }
        body.addView(name); body.addView(phone)
        val dialog=AlertDialog.Builder(this).setTitle("Add SOS contact").setView(body).setPositiveButton("Add",null).setNegativeButton("Cancel",null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try { addContact(name.text.toString(),DikcizNativeCommands.normalizedRecipient(phone.text.toString())); dialog.dismiss() }
                catch (exception: DikcizAutomationRequestException) { phone.error=exception.message }
            }
        }
        dialog.show(); show(dialog)
    }
    private fun addContact(name: String,address: String) {
        if (contacts.length()>=10) DikcizNativeCommands.invalid("At most ten contacts")
        if ((0 until contacts.length()).any { contacts.getJSONObject(it).getString("address")==address }) DikcizNativeCommands.invalid("Contact already selected")
        captureDraft(); contacts.put(JSONObject().put("label",name.ifBlank { address }).put("address",address)); render()
    }
    private fun places() {
        content.addView(label("Places",22))
        content.addView(label("Accurate fixes trigger enter, exit and optional dwell events. An uncertain boundary stays unchanged.",15))
        content.addView(button("companion:fence:new","Add place",true) { mode="fence"; draft=JSONObject(); render() })
        request(DikcizJourneys.FENCES) { result ->
            val rows=result.getJSONArray("items")
            if (rows.length()==0) card("No places yet","Save a place, then enable it when you want location events.")
            for (index in 0 until rows.length()) {
                val fence=rows.getJSONObject(index)
                val box=card(fence.getString("title"),fence.getInt("radiusMeters").toString()+" m · "+if (fence.getBoolean("enabled")) fence.getString("state") else "Paused")
                box.addView(button("companion:fence:"+fence.getString("id"),"Edit place") { mode="fence"; draft=JSONObject(fence.toString()); render() })
                box.addView(button("companion:fence:delete:"+fence.getString("id"),"Remove place") {
                    confirm("Remove place?","Future events for this place stop.") { request(DikcizJourneys.FENCE_DELETE,JSONObject().put("id",fence.getString("id"))) { render() } }
                })
            }
        }
    }
    private fun fenceForm() {
        content.addView(label("Place",22))
        input("title","Place name",draft.optString("title"))
        input("latitude","Latitude",draft.optString("latitude"),true)
        input("longitude","Longitude",draft.optString("longitude"),true)
        content.addView(button("companion:fence:locate","Use my location") {
            captureDraft()
            request("locationGet",JSONObject().put("timeoutMilliseconds",10000).put("autoRequestPermissions",true)) {
                if (!it.optBoolean("available")) { status.text="Location unavailable"; return@request }
                draft.put("latitude",it.getDouble("latitude")).put("longitude",it.getDouble("longitude")); render()
            }
        })
        input("radiusMeters","Radius, meters",draft.optString("radiusMeters","250"),true)
        input("dwellSeconds","Dwell, seconds. Zero disables",(draft.optLong("dwellMilliseconds",0)/1000).toString(),true)
        val enabled=CheckBox(this).apply { text="Enable location events"; isChecked=draft.optBoolean("enabled"); setTextColor(DikcizUiStyle.FOREGROUND) }
        views["companion:fence:enabled"]=enabled; content.addView(enabled)
        content.addView(button("companion:fence:save","Save place",true) {
            val value=JSONObject().put("title",text("title")).put("latitude",number("latitude")).put("longitude",number("longitude"))
                .put("radiusMeters",number("radiusMeters")).put("dwellMilliseconds",long("dwellSeconds")*1000).put("enabled",enabled.isChecked).put("autoRequestPermissions",true)
            draft.optString("id").takeIf { it.isNotBlank() }?.let { value.put("id",it) }
            request(DikcizJourneys.FENCE_SAVE,value) { mode=""; render() }
        })
    }
    private fun trips() {
        content.addView(label("Journeys",22))
        input("tripTitle","Journey name","Journey")
        content.addView(button("companion:trip:start","Start recording",true) {
            request(DikcizJourneys.TRIP_CONTROL,JSONObject().put("action","start").put("title",text("tripTitle")).put("autoRequestPermissions",true)) { render() }
        })
        request(DikcizJourneys.TRIPS) { result ->
            val rows=result.getJSONArray("items")
            if (rows.length()==0) card("No journeys yet","Recording saves real location points locally. It does not message anyone.")
            for (index in 0 until rows.length()) {
                val trip=rows.getJSONObject(index)
                val id=trip.getString("id")
                val box=card(trip.getString("title"),trip.getString("state").replace('_',' ')+" · "+trip.getInt("pointCount")+" points · "+trip.getDouble("distanceMeters").toInt()+" m")
                if (trip.getString("state")=="recording") box.addView(button("companion:trip:stop:"+id,"Stop recording") {
                    request(DikcizJourneys.TRIP_CONTROL,JSONObject().put("action","stop").put("id",id)) { render() }
                })
                box.addView(button("companion:trip:read:"+id,"View route") { draft=JSONObject().put("id",id); mode="route"; render() })
                if (trip.getString("state")!="recording") box.addView(button("companion:trip:delete:"+id,"Delete history") {
                    confirm("Delete journey?","Its route points are removed too.") { request(DikcizJourneys.TRIP_DELETE,JSONObject().put("id",id).put("confirm",true)) { render() } }
                })
            }
        }
    }
    private fun route() {
        content.addView(label("Route preview",22))
        val points=JSONArray()
        var summary: TextView?=null
        var preview: DikcizRoutePreview?=null
        fun load() {
            request(DikcizJourneys.TRIP_READ,JSONObject().put("id",draft.getString("id")).put("offset",points.length()).put("limit",200)) { result ->
                val rows=result.getJSONArray("items")
                for (index in 0 until rows.length()) points.put(rows.getJSONObject(index))
                preview?.let(content::removeView)
                summary?.let(content::removeView)
                preview=DikcizRoutePreview(this,points).also { content.addView(it,LinearLayout.LayoutParams(-1,dp(300))) }
                summary=label("Showing "+points.length()+" of "+result.getInt("total")+" recorded points.",14).also { content.addView(it) }
                views.remove("companion:route:more")?.let(content::removeView)
                if (result.getBoolean("hasMore")) content.addView(button("companion:route:more","Load more route points") { load() })
            }
        }
        load()
    }
    private fun devices() {
        content.addView(label("Device connections",22))
        content.addView(label("Keep one connection for several commands. Expiry, Stop and process death release it.",15))
        content.addView(button("companion:connection:new","Connect a device",true) { mode="connection"; draft=JSONObject(); render() })
        request(DikcizDeviceConnections.LIST) { result ->
            val rows=result.getJSONArray("items")
            if (rows.length()==0) card("No connections","Pair a Bluetooth adapter or attach a USB device first. NFC needs a presented tag.")
            for (index in 0 until rows.length()) {
                val row=rows.getJSONObject(index)
                val box=card(row.getString("kind").uppercase(),if (row.getBoolean("active")) "Connected" else "Closed")
                if (row.getBoolean("active")) box.addView(button("companion:connection:close:"+row.getString("id"),"Close connection") {
                    request(DikcizDeviceConnections.CLOSE,JSONObject().put("id",row.getString("id"))) { render() }
                })
            }
        }
    }
    private fun connectionForm() {
        choice("kind","Connection",listOf("rfcomm","gatt","usb","nfc"),draft.optString("kind","rfcomm")) {
            captureDraft(); draft.put("kind",it); render()
        }
        val kind=draft.optString("kind","rfcomm")
        when (kind) {
            "rfcomm","gatt" -> {
                input("address","Bluetooth address",draft.optString("address"))
                if (kind=="rfcomm") input("serviceUuid","Service UUID",draft.optString("serviceUuid","00001101-0000-1000-8000-00805f9b34fb"))
            }
            "usb" -> { input("deviceId","USB device ID",draft.optString("deviceId"),true); input("interfaceIndex","Interface index",draft.optString("interfaceIndex","0"),true) }
            "nfc" -> choice("technology","Technology",listOf("IsoDep","Ndef","NfcA","NfcB","NfcF","NfcV","MifareClassic","MifareUltralight"),draft.optString("technology","IsoDep")) { draft.put("technology",it) }
        }
        content.addView(button("companion:connection:open","Connect",true) {
            val value=JSONObject().put("kind",kind).put("autoRequestPermissions",true)
            when (kind) {
                "rfcomm","gatt" -> { value.put("address",text("address")); if (kind=="rfcomm") value.put("serviceUuid",text("serviceUuid")) }
                "usb" -> value.put("deviceId",long("deviceId")).put("interfaceIndex",long("interfaceIndex"))
                "nfc" -> value.put("technology",draft.optString("technology","IsoDep"))
            }
            request(DikcizDeviceConnections.OPEN,value) { result -> if (result.optString("state")!="permission_required") { mode=""; render() } }
        })
    }
    private fun scan() {
        content.addView(label("Read images and scan documents",22))
        content.addView(label("Text and barcode recognition runs on the phone. Decoded links stay as text. Google ML Kit may send SDK performance and usage metrics. See the image recognition guide for terms.",15))
        content.addView(button("companion:scan:pick","Choose images",true) {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE)
                .putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true),IMAGE_REQUEST)
        })
        if (selectedImages.isEmpty()) { card("No images selected","Choose up to twenty images. Camera captures can also be read through the shared tools."); return }
        card("Selected images",selectedImages.size.toString()+" local copies")
        content.addView(button("companion:scan:read","Read first image") {
            request(DikcizVision.READ,JSONObject().put("path",selectedImages.first())) { result ->
                val text=result.optString("text").ifBlank { "No text found." }
                val codes=result.optJSONArray("barcodes") ?: JSONArray()
                val values=(0 until codes.length()).joinToString("\n") { codes.getJSONObject(it).optString("rawValue") }
                card("Recognition result",text+(if (values.isBlank()) "" else "\n\nBarcodes\n"+values))
            }
        })
        content.addView(button("companion:scan:pdf","Create cleaned PDF") {
            request(DikcizVision.DOCUMENT,JSONObject().put("pages",JSONArray(selectedImages.map { JSONObject().put("path",it) }))) { result ->
                card("PDF saved",result.getString("path")).addView(button("companion:scan:copy","Copy saved path") {
                    request("clipboardSet",JSONObject().put("text",result.getString("path"))) { status.text="Path copied" }
                })
            }
        })
        content.addView(button("companion:scan:clear","Clear selection") { selectedImages.clear(); render() })
    }
    private fun health() {
        content.addView(label("Health Connect",22))
        content.addView(label("Choose the record type and review access. Data stays under Health Connect's permissions and history limits.",15))
        choice("healthKind","Record type",DikcizHealthRecords.kinds.keys.toList(),draft.optString("healthKind","steps")) { draft.put("healthKind",it) }
        content.addView(button("companion:health:read-access","Review read access") {
            request(DikcizHealthRecords.ACCESS,JSONObject().put("kinds",JSONArray(listOf(draft.optString("healthKind","steps")))).put("access","read")) { }
        })
        content.addView(button("companion:health:write-access","Review write access") {
            confirm("Allow health writes?","Only grant write access when you intend to add or change your health records.") {
                request(DikcizHealthRecords.ACCESS,JSONObject().put("kinds",JSONArray(listOf(draft.optString("healthKind","steps")))).put("access","write")) { }
            }
        })
        content.addView(button("companion:health:today","Read today's records") {
            val now=java.time.Instant.now()
            val start=java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()
            request(DikcizHealthRecords.READ,JSONObject().put("kind",draft.optString("healthKind","steps"))
                .put("startTimeMilliseconds",start.toEpochMilli()).put("endTimeMilliseconds",now.toEpochMilli()).put("limit",20)) { result ->
                if (result.optString("state")=="permission_required") { status.text="Review read access first"; return@request }
                val rows=result.getJSONArray("items")
                if (rows.length()==0) card("No records","No records were returned for this day and access.")
                for (index in 0 until rows.length()) {
                    val row=rows.getJSONObject(index)
                    card(row.optString("dataOrigin","Health Connect"),row.getJSONObject("values").keys().asSequence().joinToString("\n") {
                        it+": "+row.getJSONObject("values").opt(it)
                    })
                }
                if (result.optBoolean("hasMore")) content.addView(label("More records are available through healthRecordsRead.",14))
            }
        })
        request(DikcizHealthRecords.CATALOGUE) { result ->
            status.text=if (result.optBoolean("available")) "Health Connect available" else "Health Connect unavailable on this device"
        }
    }
    private fun wifi() {
        content.addView(label("Wi-Fi",22))
        content.addView(label("Android approves saved networks and local connections. Local-only hotspots do not share cellular internet.",15))
        content.addView(button("companion:wifi:new","Select or save a network",true) { mode="wifi"; draft=JSONObject(); render() })
        content.addView(button("companion:wifi:hotspot","Start local-only hotspot") {
            request(DikcizWifiNetworks.HOTSPOT_START,JSONObject().put("autoRequestPermissions",true)) { render() }
        })
        request(DikcizWifiNetworks.LIST) { result ->
            val rows=result.getJSONArray("items")
            for (index in 0 until rows.length()) {
                val row=rows.getJSONObject(index)
                val box=card(row.optString("ssid",row.getString("kind")),row.getString("state").replace('_',' '))
                row.optString("passphrase").takeIf { it.isNotBlank() && it!="null" }?.let { box.addView(label("Password: "+it,16)) }
                if (row.optBoolean("active")) box.addView(button("companion:wifi:stop:"+row.getString("id"),"Stop request") {
                    request(DikcizWifiNetworks.STOP,JSONObject().put("id",row.getString("id"))) { render() }
                })
            }
        }
    }
    private fun wifiForm() {
        content.addView(label("Network",22))
        input("ssid","Wi-Fi name",draft.optString("ssid"))
        choice("security","Security",listOf("open","wpa2","wpa3"),draft.optString("security","open")) {
            captureDraft(); draft.put("security",it); render()
        }
        val security=draft.optString("security","open")
        if (security!="open") input("passphrase","Password","",password=true)
        fun connection(): JSONObject=JSONObject().put("ssid",text("ssid")).put("security",security).put("autoRequestPermissions",true)
            .apply { if (security!="open") put("passphrase",text("passphrase")) }
        content.addView(button("companion:wifi:save","Ask Android to save",true) {
            request(DikcizWifiNetworks.CONFIGURE,connection().put("action","save")) { }
        })
        content.addView(button("companion:wifi:local","Request local connection") {
            request(DikcizWifiNetworks.START,connection()) { mode=""; render() }
        })
        content.addView(button("companion:wifi:suggest","Suggest for automatic selection") {
            request(DikcizWifiNetworks.CONFIGURE,connection().put("action","suggest")) { }
        })
    }
    @Deprecated("Android result bridge")
    override fun onActivityResult(requestCode: Int,resultCode: Int,data: Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if (resultCode!=RESULT_OK || data==null) return
        if (requestCode==CONTACT_REQUEST) {
            try {
                val uri=data.data ?: return
                if (uri.scheme!="content" || uri.authority!=ContactsContract.AUTHORITY) throw IllegalArgumentException("Unsupported contact provider")
                contentResolver.query(uri,arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER,ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME),null,null,null)?.use { cursor ->
                    if (cursor.moveToFirst()) addContact(cursor.getString(1).orEmpty(),DikcizNativeCommands.normalizedRecipient(cursor.getString(0).orEmpty()))
                }
            } catch (exception: Exception) { status.text=exception.message ?: "Contact could not be added" }
        }
        if (requestCode==IMAGE_REQUEST) {
            val uris=if (data.clipData!=null) (0 until data.clipData!!.itemCount).map { data.clipData!!.getItemAt(it).uri } else listOfNotNull(data.data)
            if (uris.size !in 1..20) { status.text="Choose one to twenty images"; return }
            if (busy) return
            busy=true; status.text="Copying selected images…"
            worker.execute {
                val copied=mutableListOf<java.io.File>()
                try {
                    val root=getExternalFilesDir("captures") ?: filesDir
                    for (uri in uris) {
                        if (uri.scheme!="content") throw IllegalArgumentException("Choose images through the document picker")
                        val target=java.io.File(root,"selected-"+UUID.randomUUID()+".img")
                        copied.add(target)
                        contentResolver.openInputStream(uri)?.use { input ->
                            target.outputStream().use { output ->
                                val buffer=ByteArray(8192); var length=0L
                                while (true) {
                                    val count=input.read(buffer); if (count<0) break
                                    length+=count
                                    if (length>33554432) throw IllegalArgumentException("An image exceeds 32 MiB")
                                    output.write(buffer,0,count)
                                }
                            }
                        } ?: throw IllegalArgumentException("Image could not be read")
                    }
                    handler.post { busy=false; if (!isDestroyed) { selectedImages=copied.map { it.absolutePath }.toMutableList(); mode=""; tab="Scan"; render() } }
                } catch (exception: Exception) {
                    for (file in copied) if (file.exists() && !file.delete()) android.util.Log.w("DikcizCompanion","Partial image cleanup failed")
                    handler.post { busy=false; if (!isDestroyed) status.text=exception.message ?: "Image copy failed" }
                }
            }
        }
    }
    private fun input(key: String,title: String,initial: String,numeric: Boolean=false,multiline: Boolean=false,password: Boolean=false): EditText {
        content.addView(label(title,14))
        return EditText(this).apply {
            contentDescription=title
            setText(draft.optString(key,initial))
            setTextColor(DikcizUiStyle.FOREGROUND); setHintTextColor(DikcizUiStyle.MUTED); textSize=16f
            minHeight=dp(48); setPadding(dp(12),dp(10),dp(12),dp(10))
            background=android.graphics.drawable.GradientDrawable().apply { cornerRadius=dp(12).toFloat(); setColor(DikcizUiStyle.SURFACE) }
            inputType=when {
                password -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                numeric -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
                multiline -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                else -> InputType.TYPE_CLASS_TEXT
            }
            if (password) isSaveEnabled=false
            if (multiline) minLines=3
            fields[key]=this; views["companion:field:"+key]=this
            content.addView(this,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(8) })
        }
    }
    private fun choice(key: String,title: String,values: List<String>,selected: String,changed: (String)->Unit) {
        content.addView(label(title,14))
        val picker=Spinner(this).apply {
            contentDescription=title
            adapter=ArrayAdapter(this@DikcizCompanionActivity,android.R.layout.simple_spinner_dropdown_item,values)
            setSelection(values.indexOf(selected).coerceAtLeast(0))
            minimumHeight=dp(48)
            onItemSelectedListener=object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?,view: View?,position: Int,id: Long) {
                    val value=values[position]
                    if (draft.optString(key,selected)!=value) changed(value)
                }
                override fun onNothingSelected(parent: AdapterView<*>?)=Unit
            }
        }
        views["companion:choice:"+key]=picker; content.addView(picker)
    }
    private fun text(key: String)=fields.getValue(key).text.toString()
    private fun long(key: String)=text(key).toLongOrNull() ?: throw IllegalArgumentException("Enter a whole number for "+fields.getValue(key).contentDescription)
    private fun number(key: String)=text(key).toDoubleOrNull()?.takeIf { it.isFinite() } ?: throw IllegalArgumentException("Enter a number for "+fields.getValue(key).contentDescription)
    private fun column()=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
    private fun label(text: String,size: Int)=TextView(this).apply {
        this.text=text; textSize=size.toFloat(); setTextColor(if (size<16) DikcizUiStyle.MUTED else DikcizUiStyle.FOREGROUND)
        setPadding(0,dp(6),0,dp(6)); views["companion:label:"+labelId++]=this
    }
    private fun card(title: String,text: String)=column().apply {
        setPadding(dp(14),dp(12),dp(14),dp(12))
        background=android.graphics.drawable.GradientDrawable().apply { cornerRadius=dp(16).toFloat(); setColor(DikcizUiStyle.SURFACE) }
        addView(label(title,18)); addView(label(text,15))
        content.addView(this,LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(8); bottomMargin=dp(8) })
    }
    private fun button(id: String,label: String,primary: Boolean=false,action: ()->Unit)=Button(this).apply {
        text=label; contentDescription=label; DikcizUiStyle.button(this,primary); views[id]=this
        setOnClickListener {
            try { action() } catch (exception: Exception) {
                status.text=exception.message ?: "The action could not be completed"
            }
        }
    }
    private fun confirm(title: String,message: String,action: ()->Unit) {
        show(AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("Confirm") { _,_ -> action() }
            .setNegativeButton("Cancel",null).show())
    }
    private fun show(dialog: AlertDialog) {
        modal=dialog; DikcizUiStyle.dialog(dialog)
        dialog.setOnDismissListener { if (modal===dialog) modal=null }
    }
    fun semanticViews(): Map<String,View> {
        val dialog=modal?.takeIf { it.isShowing } ?: return views
        val result=linkedMapOf<String,View>()
        for ((name,id) in mapOf("confirm" to AlertDialog.BUTTON_POSITIVE,"cancel" to AlertDialog.BUTTON_NEGATIVE)) {
            dialog.getButton(id)?.takeIf { it.visibility==View.VISIBLE }?.let { result["companion:dialog:"+name]=it }
        }
        fun children(view: View) {
            if (view is EditText && view.contentDescription!=null) result["companion:dialog:"+view.contentDescription.toString().lowercase().replace(' ','-')]=view
            if (view is android.view.ViewGroup) for (index in 0 until view.childCount) children(view.getChildAt(index))
        }
        children(dialog.window!!.decorView)
        return result
    }
    fun foregroundDialog()=modal?.takeIf { it.isShowing }
    fun scrollState()=JSONObject().put("y",scroll.scrollY).put("range",content.height).put("extent",scroll.height)
    fun scrollBy(delta: Int) { scroll.scrollBy(0,delta) }
    fun scrollTo(view: View) { view.requestRectangleOnScreen(Rect(0,0,view.width,view.height),true) }
    private fun dp(value: Int)=(value*resources.displayMetrics.density).toInt()
    companion object {
        private val TABS=listOf("Safety","Places","Trips","Devices","Scan","Health","Wi-Fi")
        private const val CONTACT_REQUEST=7314
        private const val IMAGE_REQUEST=7315
        private var foreground: WeakReference<DikcizCompanionActivity>?=null
        fun current()=foreground?.get()?.takeUnless { it.isDestroyed || it.isFinishing }
    }
}
