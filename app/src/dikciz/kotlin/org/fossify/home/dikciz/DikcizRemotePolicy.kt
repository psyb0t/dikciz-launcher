package org.fossify.home.dikciz

import org.fossify.home.dikciz.DikcizAutomationControlPlane.Companion.KEY_ACTION
import org.fossify.home.dikciz.DikcizAutomationControlPlane.Companion.KEY_REQUEST_ID
import org.fossify.home.dikciz.DikcizAutomationControlPlane.Companion.KEY_TYPE
import org.fossify.home.dikciz.DikcizRemoteAccessAuth.Companion.KEY_ENABLED
import org.json.JSONArray
import org.json.JSONObject

/** Verified access travels outside request JSON and is checked again at execution. */
internal class DikcizRemoteAccess(
    val id: String,
    val name: String,
    val permissions: Set<String>,
    private val current: () -> Boolean,
) {
    val isOwner: Boolean get() = DikcizRemotePolicy.OWNER in permissions
    fun isCurrent(): Boolean = current()
    fun requireCurrent() {
        if (!isCurrent()) throw DikcizAutomationRequestException(DikcizRemoteAccessAuth.ERROR_AUTH_REQUIRED, "Remote credential was changed, disabled or revoked")
    }
    fun requireCommand(type: String, request: JSONObject? = null) {
        requireCurrent()
        if (!DikcizRemotePolicy.permits(this, type, request)) {
            throw DikcizAutomationRequestException(DikcizRemotePolicy.ERROR_DENIED, "This token does not grant access to this operation")
        }
    }
    fun toJson() = JSONObject().put(DikcizRemotePolicy.KEY_ID, id).put(DikcizRemotePolicy.KEY_NAME, name).put("owner", isOwner)
        .put(DikcizRemotePolicy.KEY_PERMISSIONS, JSONArray(permissions.sorted()))
}

internal object DikcizRemotePolicy {
    const val OWNER = "owner"
    const val PERMISSION_SMS_READ = "sms.read"
    const val PERMISSION_SMS_SEND = "sms.send"
    const val PERMISSION_SIM_READ = "sim.read"
    const val PERMISSION_CONTACTS_READ = "contacts.read"
    const val PERMISSION_CONTACTS_WRITE = "contacts.write"
    const val PERMISSION_CALENDAR_READ = "calendar.read"
    const val PERMISSION_CALENDAR_WRITE = "calendar.write"
    const val PERMISSION_CALLS_READ = "calls.read"
    const val PERMISSION_CALLS_CONTROL = "calls.control"
    const val PERMISSION_SENSORS_READ = "sensors.read"
    const val PERMISSION_SCREEN_CAPTURE = "screen.capture"
    const val PERMISSION_UI_READ = "ui.read"
    const val PERMISSION_APPS_READ = "apps.read"
    const val PERMISSION_APPS_LAUNCH = "apps.launch"
    const val PERMISSION_DEVICE_READ = "device.read"
    const val PERMISSION_DEVICE_CONTROL = "device.control"
    const val PERMISSION_LOCATION_READ = "location.read"
    const val PERMISSION_NOTIFICATIONS_READ = "notifications.read"
    const val PERMISSION_NOTIFICATIONS_CONTROL = "notifications.control"
    const val PERMISSION_MEDIA_READ = "media.read"
    const val PERMISSION_FILES_READ = "files.read"
    const val PERMISSION_LOGS_READ = "logs.read"
    const val PERMISSION_EVENTS_READ = "events.read"
    const val PERMISSION_SEARCH_WRITE = "search.write"
    const val PERMISSION_SPEECH_OUTPUT = "speech.output"
    const val PERMISSION_BLUETOOTH_READ = "bluetooth.read"
    const val PERMISSION_WIFI_READ = "wifi.read"
    const val PERMISSION_USB_READ = "usb.read"
    const val PERMISSION_NFC_READ = "nfc.read"
    const val ACTION_CATALOG = "catalog"
    const val ACTION_LIST = "list"
    const val ACTION_CREATE = "create"
    const val ACTION_UPDATE = "update"
    const val ACTION_REVOKE = "revoke"
    const val KEY_ID = "id"
    const val KEY_NAME = "name"
    const val KEY_PERMISSIONS = "permissions"
    const val KEY_TOKEN = "token"
    const val KEY_TOKENS = "tokens"
    const val KEY_DIGEST = "digest"
    const val KEY_CREATED_AT = "createdAt"
    const val ERROR_DENIED = "remote_permission_denied"
    const val TYPE_INFO = "remoteAccessInfo"
    const val TYPE_TOKENS = "remoteTokens"
    const val SCOPED_TOOLS_COLLECTION = "tools@scoped"
    private const val TOOLS_COLLECTION = "tools"
    private const val DOCS_COLLECTION = "docs"
    const val UI_CONTROL = "ui.control"
    const val SEARCH_READ = "search.read"
    const val MAXIMUM_NAME_CHARACTERS = 80
    const val ID_CHARACTERS = 16
    private val ID_PATTERN = Regex("[a-f0-9]{16}")

    data class Grant(val id: String, val title: String, val description: String, val tools: Set<String>)
    private fun grant(id: String, title: String, description: String, vararg tools: String) =
        Grant(id, title, description, tools.toSet())
    val GRANTS = listOf(
        grant(OWNER, "Full owner access", "All operations, including shell, SDK, scripting, configuration, installation and access-token administration."),
        grant(PERMISSION_SMS_READ, "Read SMS", "Read SMS history and sender/message data through the SMS provider."),
        grant(PERMISSION_SMS_SEND, "Send SMS", "Send tracked SMS, read send receipts and select a SIM. Does not grant SMS history access.", "dikciz_sms_send", "dikciz_sms_status", "dikciz_sim_list"),
        grant(PERMISSION_SIM_READ, "Read SIMs and own numbers", "Read SIM subscriptions and own phone numbers when Android permits.", "dikciz_sim_list"),
        grant(PERMISSION_CONTACTS_READ, "Read contacts", "Read contacts, phone numbers, email addresses and contact data."),
        grant(PERMISSION_CONTACTS_WRITE, "Edit contacts", "Create, change or delete contact records. Does not grant contact reads."),
        grant(PERMISSION_CALENDAR_READ, "Read calendars", "Read calendars, agenda entries and reminders."),
        grant(PERMISSION_CALENDAR_WRITE, "Edit calendars", "Create, change or delete calendar records."),
        grant(PERMISSION_CALLS_READ, "Read calls", "Read call history and current call state."),
        grant(PERMISSION_CALLS_CONTROL, "Control calls", "Read state and place, answer or end calls."),
        grant(PERMISSION_SENSORS_READ, "Read sensors", "Read sensor inventory and bounded live samples.", "dikciz_sensor_readings"),
        grant(PERMISSION_SCREEN_CAPTURE, "Capture screenshots", "Read screenshots of the launcher and other visible apps.", "dikciz_screenshot", "dikciz_display_screenshot"),
        grant(PERMISSION_UI_READ, "Read UI components", "Read visible launcher and Accessibility structures, text and semantic IDs.", "dikciz_snapshot", "dikciz_ui_dump", "dikciz_accessibility_snapshot", "dikciz_find", "dikciz_wait_for"),
        grant(UI_CONTROL, "Read and control other apps", "Broad UI authority, including actions such as messaging inside other apps. Does not permit changing Dikciz itself.", "dikciz_snapshot", "dikciz_ui_dump", "dikciz_accessibility_snapshot", "dikciz_find", "dikciz_wait_for", "dikciz_accessibility_action"),
        grant(PERMISSION_APPS_READ, "Read installed apps", "Read launchable apps, icons and package metadata.", "dikciz_app_catalogue", "dikciz_app_icon", "dikciz_package_info"),
        grant(PERMISSION_APPS_LAUNCH, "Open apps", "Read app metadata, open apps and show their Android app-info screen.", "dikciz_app_catalogue", "dikciz_app_icon", "dikciz_package_info", "dikciz_launch_app"),
        grant(PERMISSION_DEVICE_READ, "Read device measurements", "Read battery, memory/storage, audio and camera inventories and device state.", "dikciz_device_state", "dikciz_audio_devices", "dikciz_camera_list", "dikciz_camera_capabilities"),
        grant(PERMISSION_DEVICE_CONTROL, "Control device settings", "Control torch, vibration, brightness, rotation and screen timeout.", "dikciz_torch_control", "dikciz_vibration_control", "dikciz_system_setting"),
        grant(PERMISSION_LOCATION_READ, "Read location", "Read permitted fixes, geocoding and satellite observations.", "dikciz_location_get", "dikciz_geocode", "dikciz_gnss_status"),
        grant(PERMISSION_NOTIFICATIONS_READ, "Read notifications", "Read notification contents and action metadata.", "dikciz_notifications_list"),
        grant(PERMISSION_NOTIFICATIONS_CONTROL, "Act on notifications", "Read notifications, dismiss, snooze and send inline replies, which can send messages.", "dikciz_notifications_list", "dikciz_notification_manage"),
        grant(PERMISSION_MEDIA_READ, "Read media", "Read media provider metadata and active media sessions.", "dikciz_media_sessions_list"),
        grant(PERMISSION_FILES_READ, "Read files", "Read and search all files Dikciz can access, excluding protected credentials.", "dikciz_file_list", "dikciz_file_read"),
        grant(PERMISSION_LOGS_READ, "Read script logs", "Read the sanitized script-log snapshot, which may contain script data.", "dikciz_script_logs"),
        grant(PERMISSION_EVENTS_READ, "Read all automation events", "Read dispatched event history and live launcher broadcasts, including payloads from every permitted Android source.", "dikciz_event_journal_status", "dikciz_event_journal_read"),
        grant(SEARCH_READ, "Read owner search collections", "Read explicitly indexed owner text in every custom search collection."),
        grant(PERMISSION_SEARCH_WRITE, "Edit owner search collections", "Upsert, remove or clear custom indexes. Built-in documentation and tools remain protected.", "dikciz_search_index"),
        grant(PERMISSION_SPEECH_OUTPUT, "Speak text", "Speak text, list TTS voices and stop speech output.", "dikciz_speech_speak", "dikciz_speech_voices", "dikciz_speech_stop"),
        grant(PERMISSION_BLUETOOTH_READ, "Read Bluetooth devices", "Read paired devices and perform bounded nearby-device scans.", "dikciz_bluetooth_devices", "dikciz_bluetooth_scan"),
        grant(PERMISSION_WIFI_READ, "Read Wi-Fi networks", "Read cached Wi-Fi networks and request a bounded fresh scan.", "dikciz_wifi_scan_results", "dikciz_wifi_scan"),
        grant(PERMISSION_USB_READ, "Read USB inventory", "Read attached USB device and interface metadata.", "dikciz_usb_devices"),
        grant(PERMISSION_NFC_READ, "Read NFC tags", "Read NFC status and bounded presented-tag data. No writes or raw transactions.", "dikciz_nfc_status", "dikciz_nfc_read"),
    )
    val PERMISSION_IDS = GRANTS.map { it.id }.toSet()
    private val BASE_TOOLS = setOf(
        "dikciz_device_info", "dikciz_control_status", "dikciz_native_data_status", "dikciz_remote_auth_status",
        "dikciz_remote_access_info", "dikciz_search_query", "dikciz_search_read", "dikciz_search_collections",
    )
    private val CONTACT_RESOURCES = setOf("contacts", "phones", "emails", "rawContacts", "contactData")
    private val CALENDAR_RESOURCES = setOf("calendars", "calendarEvents", "calendarInstances", "calendarReminders")
    private val MEDIA_RESOURCES = setOf("images", "audio", "videos")

    val DEFINITIONS = listOf(
        DikcizNativeCommands.Definition(TYPE_INFO, "dikciz_remote_access_info",
            "Read the verified caller identity and effective remote-token grants. Local owner bindings need no bearer token. Does not return credentials or verifiers.", emptyMap()),
        DikcizNativeCommands.Definition(TYPE_TOKENS, "dikciz_remote_tokens",
            "Owner-only remote access-token administration. catalog lists finite permission grants. list returns metadata; create requires name and permissions and returns its generated token once. update requires id and optional name/permissions/enabled; revoke requires id. Authentication must be enabled before creating tokens. Existing tokens, including disabled tokens, prevent disabling or clearing authentication. Local owner bindings and full-owner credentials can administer tokens. Scoped credentials cannot.",
            mapOf(
                KEY_ACTION to text(16).put("enum", JSONArray(listOf(ACTION_CATALOG, ACTION_LIST, ACTION_CREATE, ACTION_UPDATE, ACTION_REVOKE))),
                KEY_ID to text(ID_CHARACTERS).put("pattern", "^[a-f0-9]{16}$"),
                KEY_NAME to text(MAXIMUM_NAME_CHARACTERS).put("minLength", 1),
                KEY_PERMISSIONS to JSONObject().put(KEY_TYPE, "array").put("minItems", 1).put("maxItems", PERMISSION_IDS.size)
                    .put("uniqueItems", true).put("items", text(64).put("enum", JSONArray(PERMISSION_IDS.sorted()))),
                KEY_ENABLED to JSONObject().put(KEY_TYPE, "boolean"),
            ), setOf(KEY_ACTION), readOnly = false),
    )

    fun catalogue() = JSONArray(GRANTS.map {
        JSONObject().put(KEY_ID, it.id).put("title", it.title).put("description", it.description)
    })

    fun validate(request: JSONObject) {
        val action = request.optString(KEY_ACTION)
        val fields = when (action) {
            ACTION_CATALOG, ACTION_LIST -> setOf(KEY_ACTION)
            ACTION_CREATE -> setOf(KEY_ACTION, KEY_NAME, KEY_PERMISSIONS)
            ACTION_UPDATE -> setOf(KEY_ACTION, KEY_ID, KEY_NAME, KEY_PERMISSIONS, KEY_ENABLED)
            ACTION_REVOKE -> setOf(KEY_ACTION, KEY_ID)
            else -> DikcizNativeCommands.invalid("Unknown access-token action")
        }
        if (request.keys().asSequence().any { it !in fields && it !in setOf(KEY_TYPE, KEY_REQUEST_ID) }) DikcizNativeCommands.invalid()
        if (action in setOf(ACTION_CREATE, ACTION_UPDATE) && request.has(KEY_NAME)) validateName(request.getString(KEY_NAME))
        if (action == ACTION_CREATE && (!request.has(KEY_NAME) || !request.has(KEY_PERMISSIONS))) DikcizNativeCommands.invalid("create requires name and permissions")
        if (action in setOf(ACTION_UPDATE, ACTION_REVOKE) && !ID_PATTERN.matches(request.optString(KEY_ID))) DikcizNativeCommands.invalid("Invalid token ID")
        if (action == ACTION_UPDATE && fields.minus(setOf(KEY_ACTION, KEY_ID)).none(request::has)) DikcizNativeCommands.invalid("Provide a token update")
        if (request.has(KEY_PERMISSIONS)) permissionSet(request.getJSONArray(KEY_PERMISSIONS))
    }
    fun validateName(name: String) {
        if (name.isBlank() || name.length > MAXIMUM_NAME_CHARACTERS || name.any(Char::isISOControl)) DikcizNativeCommands.invalid("Token name must contain 1 to 80 printable characters")
    }
    fun permissionSet(array: JSONArray): Set<String> {
        val values = (0 until array.length()).map { array.opt(it) as? String ?: DikcizNativeCommands.invalid("Invalid permission grant") }
        if (values.isEmpty() || values.size > PERMISSION_IDS.size || values.distinct().size != values.size ||
            values.any { it !in PERMISSION_IDS } || (OWNER in values && values.size != 1)) {
            DikcizNativeCommands.invalid("Choose unique known grants; owner must be selected alone")
        }
        return values.toSet()
    }

    fun resources(access: DikcizRemoteAccess, write: Boolean): Set<String> = buildSet {
        if ((if (write) PERMISSION_CONTACTS_WRITE else PERMISSION_CONTACTS_READ) in access.permissions) {
            addAll(if (write) setOf("rawContacts", "contactData") else CONTACT_RESOURCES)
        }
        if ((if (write) PERMISSION_CALENDAR_WRITE else PERMISSION_CALENDAR_READ) in access.permissions) addAll(CALENDAR_RESOURCES)
        if (!write && PERMISSION_SMS_READ in access.permissions) add("sms")
        if (!write && PERMISSION_CALLS_READ in access.permissions) add("callLog")
        if (!write && PERMISSION_MEDIA_READ in access.permissions) addAll(MEDIA_RESOURCES)
    }

    fun permits(access: DikcizRemoteAccess, type: String, request: JSONObject?): Boolean {
        if (access.isOwner) return true
        val tool = DikcizMcpControlPlane.toolNameForCommand(type) ?: return false
        val general = tool in BASE_TOOLS || GRANTS.any { it.id in access.permissions && tool in it.tools }
        return when (tool) {
            "dikciz_content_query" -> resources(access, false).let { it.isNotEmpty() && (request == null || request.optString("resource") in it) }
            "dikciz_content_write" -> resources(access, true).let { it.isNotEmpty() && (request == null || request.optString("resource") in it) }
            "dikciz_content_batch" -> resources(access, true).let { allowed ->
                allowed.isNotEmpty() && (request == null || request.optJSONArray("operations")?.let { operations ->
                    (0 until operations.length()).all { operations.optJSONObject(it)?.optString("resource") in allowed }
                } == true)
            }
            "dikciz_call_control" -> callActions(access).let { it.isNotEmpty() && (request == null || request.optString(KEY_ACTION) in it) }
            "dikciz_app_action" -> PERMISSION_APPS_LAUNCH in access.permissions && (request == null || request.optString(KEY_ACTION) in APP_ACTIONS)
            "dikciz_file_manage" -> PERMISSION_FILES_READ in access.permissions && (request == null || request.optString(KEY_ACTION) in FILE_ACTIONS)
            "dikciz_search_query", "dikciz_search_read" -> request == null || request.optString("collection", DOCS_COLLECTION) in setOf(DOCS_COLLECTION, TOOLS_COLLECTION) || SEARCH_READ in access.permissions
            else -> general
        }
    }

    fun filterCatalogue(document: JSONObject, access: DikcizRemoteAccess?): JSONObject {
        if (access == null || access.isOwner) return document
        access.requireCurrent()
        val tools = document.getJSONArray("tools")
        val visible = JSONArray()
        for (position in 0 until tools.length()) {
            val tool = JSONObject(tools.getJSONObject(position).toString())
            val type = DikcizMcpControlPlane.TOOL_COMMANDS[tool.getString(KEY_NAME)] ?: continue
            if (!permits(access, type, null)) continue
            val properties = tool.getJSONObject("inputSchema").getJSONObject("properties")
            when (tool.getString(KEY_NAME)) {
                "dikciz_content_query" -> properties.getJSONObject("resource").put("enum", JSONArray(resources(access, false).sorted()))
                "dikciz_content_write" -> properties.getJSONObject("resource").put("enum", JSONArray(resources(access, true).sorted()))
                "dikciz_content_batch" -> properties.getJSONObject("operations").getJSONObject("items")
                    .getJSONObject("properties").getJSONObject("resource").put("enum", JSONArray(resources(access, true).sorted()))
                "dikciz_call_control" -> properties.getJSONObject(KEY_ACTION).put("enum", JSONArray(callActions(access).sorted()))
                "dikciz_app_action" -> properties.getJSONObject(KEY_ACTION).put("enum", JSONArray(APP_ACTIONS.sorted()))
                "dikciz_file_manage" -> properties.getJSONObject(KEY_ACTION).put("enum", JSONArray(FILE_ACTIONS.sorted()))
            }
            val name = tool.getString(KEY_NAME)
            if (name == "dikciz_file_manage" || (name == "dikciz_call_control" && callActions(access) == setOf("state"))) {
                tool.getJSONObject("annotations").put("readOnlyHint", true).put("destructiveHint", false)
                tool.put("description", if (name == "dikciz_file_manage") {
                    "Read file metadata or search accessible files. Only stat and search are permitted by this token. Protected credential paths remain inaccessible."
                } else "Read current native call state. This token permits action state only. Android permissions still apply.")
            }
            if (name == "dikciz_sim_list" && PERMISSION_SIM_READ !in access.permissions) {
                tool.put("description", "Read active SIM subscription selection metadata with pagination. This token cannot read own phone numbers or request their permission. READ_PHONE_STATE is required.")
            }
            visible.put(tool)
        }
        access.requireCurrent()
        return JSONObject(document.toString()).put("tools", visible)
    }

    fun filterResult(type: String, result: JSONObject, access: DikcizRemoteAccess?): JSONObject {
        if (access == null || access.isOwner) return result
        access.requireCurrent()
        when (type) {
            "nativeDataStatus" -> {
                val rows = result.getJSONArray("resources")
                val readable = resources(access, false)
                val writable = resources(access, true)
                result.put("resources", JSONArray((0 until rows.length()).map(rows::getJSONObject).filter {
                    it.getString("resource") in readable || it.getString("resource") in writable
                }.map {
                    if (it.getString("resource") !in writable) {
                        it.put("writableColumns", JSONArray()).put("requiredCreateFields", JSONArray()).put("writeGranted", false)
                    }
                    if (it.getString("resource") !in readable) it.put("readGranted", false)
                    it
                }))
            }
            "controlStatus" -> {
                val commands = result.getJSONArray("commands")
                result.put("commands", JSONArray((0 until commands.length()).map(commands::getJSONObject).filter {
                    permits(access, it.getString(KEY_TYPE), null)
                }))
                result.put("events", if (PERMISSION_EVENTS_READ in access.permissions) result.getJSONArray("events") else JSONArray())
                val safe = result.optJSONArray("safeModeAllowedCommands")
                if (safe != null) result.put("safeModeAllowedCommands", JSONArray((0 until safe.length()).map(safe::getString).filter {
                    permits(access, it, null)
                }))
            }
            "simList" -> if (PERMISSION_SIM_READ !in access.permissions) {
                val rows = result.getJSONArray("items")
                for (position in 0 until rows.length()) rows.getJSONObject(position)
                    .put("phoneNumber", JSONObject.NULL).put("phoneNumberStatus", "access_denied")
                result.put("phoneNumberPermissionGranted", false).put("phoneNumbersAllowed", false)
                result.remove(DikcizPermissionRequests.KEY_RECOVERY)
            }
        }
        return result
    }
    private fun callActions(access: DikcizRemoteAccess): Set<String> = when {
        PERMISSION_CALLS_CONTROL in access.permissions -> setOf("state", "place", "answer", "end")
        PERMISSION_CALLS_READ in access.permissions -> setOf("state")
        else -> emptySet()
    }
    private fun text(maximum: Int) = JSONObject().put(KEY_TYPE, "string").put("maxLength", maximum)
    private val APP_ACTIONS = setOf("launch", "appInfo")
    private val FILE_ACTIONS = setOf("stat", "search")
}
