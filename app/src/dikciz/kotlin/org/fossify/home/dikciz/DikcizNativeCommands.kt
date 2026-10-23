package org.fossify.home.dikciz

import org.json.JSONArray
import org.json.JSONObject

/** One discovery and validation contract for network and owner command bindings. */
internal object DikcizNativeCommands {
    const val MAXIMUM_LIMIT = 200
    const val DEFAULT_LIMIT = 50
    const val MAXIMUM_TEXT = 4096
    const val MAXIMUM_OFFSET = 1_000_000
    const val MAXIMUM_BATCH = 32
    const val MAXIMUM_COLUMNS = 32
    const val MAXIMUM_RESPONSE_BYTES = 524_288
    const val TYPE_STATUS = "nativeDataStatus"
    const val TYPE_QUERY = "contentQuery"
    const val TYPE_WRITE = "contentWrite"
    const val TYPE_BATCH = "contentBatch"
    const val TYPE_SMS_SEND = "smsSend"
    const val TYPE_SMS_STATUS = "smsStatus"
    const val TYPE_SIM_LIST = "simList"
    const val TYPE_CALL = "callControl"
    const val TYPE_CLIPBOARD_GET = "clipboardGet"
    const val TYPE_CLIPBOARD_SET = "clipboardSet"
    const val TYPE_SENSOR_READINGS = "sensorReadings"
    const val TYPE_CAMERAS = "cameraList"
    const val TYPE_CAMERA_CAPTURE = "cameraCapture"
    const val TYPE_MICROPHONE_RECORD = "microphoneRecord"
    const val TYPE_SPEAK = "speechSpeak"
    const val TYPE_SYNTHESIZE = "speechSynthesize"
    const val TYPE_SPEECH_STATUS = "speechRecognitionStatus"
    const val TYPE_SPEECH_RECOGNIZE = "speechRecognize"
    const val TYPE_SPEECH_STOP = "speechRecognitionStop"
    const val TYPE_CALL_AUDIO = "callAudio"
    const val TYPE_LOCATION = "locationGet"
    const val TYPE_NOTIFICATIONS = "notificationsList"
    const val TYPE_MEDIA = "mediaSessionsList"
    const val TYPE_DEVICE_STATE = "deviceState"
    const val TYPE_APP_ICON = "appIcon"
    const val KEY_COMPONENT = "component"
    const val KEY_ICON_SIZE = "sizePixels"
    const val DEFAULT_ICON_SIZE = 64
    const val MINIMUM_ICON_SIZE = 16
    const val MAXIMUM_ICON_SIZE = 128
    const val ERROR_UNAVAILABLE = "native_unavailable"
    const val ERROR_FAILED = "native_operation_failed"
    const val ERROR_NOT_FOUND = "native_record_not_found"
    const val ERROR_READ_ONLY = "native_read_only"
    const val KEY_RESOURCE = "resource"
    const val KEY_ID = "id"
    const val KEY_VALUES = "values"
    const val KEY_OPERATION = "operation"
    const val KEY_OPERATIONS = "operations"
    const val KEY_BACK_REFERENCES = "backReferences"
    const val KEY_COLUMNS = "columns"
    const val KEY_FILTER = "filter"
    const val KEY_QUERY = "query"
    const val KEY_LIMIT = "limit"
    const val KEY_OFFSET = "offset"
    const val KEY_ITEMS = "items"
    const val KEY_HAS_MORE = "hasMore"
    const val KEY_STATE = "state"
    const val KEY_REQUEST_KEY = "idempotencyKey"
    const val KEY_RECIPIENT = "recipient"
    const val KEY_BODY = "body"
    const val KEY_SUBSCRIPTION = "subscriptionId"
    const val KEY_TEXT = "text"
    const val OP_CREATE = "create"
    const val OP_UPDATE = "update"
    const val OP_DELETE = "delete"
    private const val TYPE_STRING = "string"
    private const val TYPE_INTEGER = "integer"
    private const val TYPE_OBJECT = "object"
    private const val TYPE_ARRAY = "array"
    private val ENVELOPE = setOf("type", "requestId")
    private val UUID_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9_.-]{0,79}")
    private val PHONE_PATTERN = Regex("[+]?[0-9]{3,32}")
    private val FORMATTED_PHONE_PATTERN = Regex("[+]?[0-9() .-]{3,63}")
    private val PAGE_FIELDS = linkedMapOf(
        KEY_LIMIT to integer(1, MAXIMUM_LIMIT, DEFAULT_LIMIT),
        KEY_OFFSET to integer(0, MAXIMUM_OFFSET, 0),
    )
    private val WRITE_FIELDS = linkedMapOf(
        KEY_RESOURCE to string("Resource from nativeDataStatus.resources.", 64),
        KEY_OPERATION to string("create, update or delete. Updates and deletes require one positive row id.", 16),
        KEY_ID to integer(1, Int.MAX_VALUE),
        KEY_VALUES to JSONObject().put("type", TYPE_OBJECT).put("description", "Allowlisted provider columns and primitive values. Unspecified columns remain unchanged."),
        KEY_BACK_REFERENCES to JSONObject().put("type", TYPE_OBJECT).put("description", "Batch-only foreign-key column to earlier create-operation index, within the same provider."),
    )
    data class Definition(
        val type: String,
        val tool: String,
        val description: String,
        val fields: Map<String, JSONObject>,
        val required: Set<String> = emptySet(),
        val readOnly: Boolean = true,
        val mainThread: Boolean = false,
    ) {
        fun toolDocument(): JSONObject {
            val properties = JSONObject()
            fields.forEach { (name, schema) -> properties.put(name, schema) }
            properties.put(DikcizPermissionRequests.KEY_AUTO_REQUEST, JSONObject().put("type", "boolean").put("default", false)
                .put("description", "Ask the owner to grant missing Android access. Never grants access or retries automatically."))
            return JSONObject().put("name", tool).put("title", type).put("description", description)
                .put("inputSchema", JSONObject().put("type", TYPE_OBJECT).put("properties", properties)
                    .put("required", JSONArray(required.toList())).put("additionalProperties", false))
                .put("annotations", JSONObject().put("readOnlyHint", readOnly).put("destructiveHint", !readOnly)
                    .put("openWorldHint", !readOnly))
        }
    }
    private val SPEECH_FIELDS = mapOf(KEY_TEXT to string("Text spoken by the phone.", 2000), "timeoutMilliseconds" to integer(1, 120_000, 30_000), "language" to string("BCP-47 language; defaults to engine selection.", 64), "voiceName" to string("Exact name from speechVoices. Voice selection takes precedence over language.", 256), "ratePercent" to integer(25, 400, 100), "pitchPercent" to integer(25, 400, 100))
    val DEFINITIONS = listOf(
        Definition(TYPE_STATUS, "dikciz_native_data_status", "Discover direct Android data resources, columns, operations and permission state. No UI automation or root.", emptyMap()),
        Definition(TYPE_QUERY, "dikciz_content_query", "Read one bounded page from an allowlisted Android provider. Filter values use bound arguments. Query is literal substring search. Follow hasMore using offset plus returned item count.",
            PAGE_FIELDS + mapOf(KEY_RESOURCE to WRITE_FIELDS.getValue(KEY_RESOURCE), KEY_QUERY to string("Literal substring matched against the resource search columns.", 256),
                KEY_FILTER to JSONObject().put("type", TYPE_OBJECT).put("description", "Exact equality filters on allowlisted columns. Null matches SQL NULL."),
                KEY_COLUMNS to JSONObject().put("type", TYPE_ARRAY).put("maxItems", MAXIMUM_COLUMNS).put("minItems", 1).put("items", string("Allowlisted column.", 64))), setOf(KEY_RESOURCE)),
        Definition(TYPE_WRITE, "dikciz_content_write", "Create, update or delete a single Android provider row. Native data status lists writable resources and columns. SMS history stays read-only. Android permissions and provider rules apply.", WRITE_FIELDS - KEY_BACK_REFERENCES, setOf(KEY_RESOURCE, KEY_OPERATION), false),
        Definition(TYPE_BATCH, "dikciz_content_batch", "Apply at most 32 validated writes atomically within one Android provider. Earlier create results may supply allowlisted foreign-key back references. No arbitrary URI or SQL.",
            mapOf(KEY_OPERATIONS to JSONObject().put("type", TYPE_ARRAY).put("minItems", 1).put("maxItems", MAXIMUM_BATCH)
                .put("items", JSONObject().put("type", TYPE_OBJECT).put("properties", JSONObject(WRITE_FIELDS)).put("required", JSONArray(listOf(KEY_RESOURCE, KEY_OPERATION))).put("additionalProperties", false))), setOf(KEY_OPERATIONS), false),
        Definition(TYPE_SMS_SEND, "dikciz_sms_send", "Submit an SMS through Android SmsManager. Required idempotencyKey prevents duplicate sends across retries and launcher restarts. Poll smsStatus for sent and delivery outcomes; accepted is not sent. Select a SIM explicitly when there is no default.",
            mapOf(KEY_REQUEST_KEY to string("Unique caller key. Reusing it with different content fails. Retained until app data is cleared.", 80), KEY_RECIPIENT to string("Dialable phone number. Spaces, parentheses, dots and hyphens are normalized.", 64), KEY_BODY to string("SMS body. Android splits multipart messages.", MAXIMUM_TEXT), KEY_SUBSCRIPTION to integer(0, Int.MAX_VALUE)), setOf(KEY_REQUEST_KEY, KEY_RECIPIENT, KEY_BODY), false),
        Definition(TYPE_SMS_STATUS, "dikciz_sms_status", "Read the persisted state for an SMS submitted by Dikciz, without returning its recipient or body. Delivery remains unknown until a carrier callback arrives.", mapOf(KEY_REQUEST_KEY to string("Key from smsSend.", 80)), setOf(KEY_REQUEST_KEY)),
        Definition(TYPE_SIM_LIST, "dikciz_sim_list", "List active SIM subscription IDs, slots and default SMS subscription, using granted phone-state access.", PAGE_FIELDS),
        Definition(TYPE_CALL, "dikciz_call_control", "Native phone operations: state, place, answer or end. Place requires recipient. Android may restrict operations by role or privilege. accepted never claims a connected call.", mapOf("action" to string("state, place, answer or end.", 16), KEY_RECIPIENT to string("Dialable number for place only. Common formatting separators are normalized.", 64)), setOf("action"), false),
        Definition(TYPE_CLIPBOARD_GET, "dikciz_clipboard_get", "Read clipboard text while Android permits the calling app to access it. Background restrictions return available=false.", emptyMap(), mainThread = true),
        Definition(TYPE_CLIPBOARD_SET, "dikciz_clipboard_set", "Set plain clipboard text through ClipboardManager.", mapOf(KEY_TEXT to string("Plain text, including an empty string.", MAXIMUM_TEXT)), setOf(KEY_TEXT), false, true),
        Definition(TYPE_LOCATION, "dikciz_location_get", "Read a recent permitted location or wait for a bounded new fix. Returns age, accuracy and source. timeoutMilliseconds=0 reads cache only; unavailable is explicit.", mapOf("maxAgeMilliseconds" to integer(0, Int.MAX_VALUE, 300_000), "timeoutMilliseconds" to integer(0, 120_000, 10_000))),
        Definition(TYPE_SENSOR_READINGS, "dikciz_sensor_readings", "Read a bounded sample window for all hardware sensors, including multiple sensors of one type. sensorId comes from this inventory. Follow hasMore. Each sensor reports actual values, accuracy, timestamp or an explicit unavailable reason. Registrations stop when the request ends.",
            PAGE_FIELDS + mapOf("sensorId" to integer(0, Int.MAX_VALUE), "timeoutMilliseconds" to integer(1, 5000, 1000))),
        Definition(TYPE_CAMERAS, "dikciz_camera_list", "List Android camera IDs and lens directions without starting capture.", emptyMap()),
        Definition(TYPE_CAMERA_CAPTURE, "dikciz_camera_capture", "Capture a JPEG with optional quality, orientation, sensor crop zoom, exposure compensation and autofocus controls. Returns written image path and completed Android frame metadata. Read cameraCapabilities first. Android camera access and foreground restrictions apply.",
            mapOf(
                "cameraId" to string("Camera ID from cameraList; defaults to the first camera.", 128),
                "maxDimension" to integer(320, 4096, 1280),
                "timeoutMilliseconds" to integer(1, 120_000, 30_000),
                "jpegQuality" to integer(1, 100, DikcizCameraControls.DEFAULT_JPEG_QUALITY),
                "jpegOrientation" to integer(0, 270),
                "zoomPercent" to integer(100, 10000),
                "exposureCompensation" to integer(-1000, 1000),
                "autofocusMode" to integer(0, 5),
            ), readOnly = false),
        Definition(TYPE_MICROPHONE_RECORD, "dikciz_microphone_record", "Record microphone audio to an AAC M4A file and return its path for fileRead/download. durationMilliseconds defaults to 3000. Requires microphone access and Android foreground eligibility. This is not cellular call capture.",
            mapOf("durationMilliseconds" to integer(1000, 120_000, 3000)), readOnly = false),
        Definition(TYPE_SPEAK, "dikciz_speech_speak", "Speak text through the Android text-to-speech engine and device audio output. Returns only after synthesis completes. Does not inject speech into cellular calls.",
            SPEECH_FIELDS, setOf(KEY_TEXT), false),
        Definition(TYPE_SYNTHESIZE, "dikciz_speech_synthesize", "Synthesize text into a saved WAV audio file without playing it. Shares voice, language, rate, pitch, timeout and cancellation with speechSpeak. Read the saved path through fileRead/download.", SPEECH_FIELDS, setOf(KEY_TEXT), readOnly = false),
        Definition(DikcizSpeechOutput.TYPE_VOICES, "dikciz_speech_voices", "List voices from the installed Android TTS engine, including language, quality, latency and network requirement. Follow hasMore.", PAGE_FIELDS),
        Definition(DikcizSpeechOutput.TYPE_STOP, "dikciz_speech_stop", "Stop active Dikciz text-to-speech output. The original request returns speech_cancelled. Idle is explicit.", emptyMap(), readOnly = false),
        Definition(TYPE_SPEECH_STATUS, "dikciz_speech_recognition_status", "Discover Android speech recognition and strict on-device availability without recording. Reports whether recognition is active.", emptyMap()),
        Definition(TYPE_SPEECH_RECOGNIZE, "dikciz_speech_recognize", "Recognize microphone speech and return text, alternatives and confidence. mode defaults to onDevice, which never falls back to a network recognizer. system explicitly allows the installed service to use its network backend. Requires microphone permission and foreground eligibility. Use speechRecognitionStop to cancel.",
            mapOf("language" to string("BCP-47 language tag, for example en-US; defaults to device language.", 64), "mode" to string("onDevice or system.", 16), "timeoutMilliseconds" to integer(1, 120_000, 30_000)), readOnly = false),
        Definition(TYPE_SPEECH_STOP, "dikciz_speech_recognition_stop", "Cancel the active speech recognition request and destroy its Android recognizer. Returns idle when no request is active.", emptyMap(), readOnly = false),
        Definition(TYPE_CALL_AUDIO, "dikciz_call_audio", "Read cellular call-audio capability status or request record/speak. Ordinary Android installations return call_audio_unsupported for capture and injection. Microphone and device speech are separate operations.",
            mapOf("action" to string("status, record or speak.", 16), KEY_TEXT to string("Requested call speech.", 2000)), setOf("action"), false),
        Definition(TYPE_NOTIFICATIONS, "dikciz_notifications_list", "Read current notifications directly through the enabled notification listener, including bounded text and short-lived action tokens.", PAGE_FIELDS),
        Definition(TYPE_MEDIA, "dikciz_media_sessions_list", "Read current media sessions, playback state and bounded metadata through granted notification-listener access.", PAGE_FIELDS),
        Definition(TYPE_APP_ICON, "dikciz_app_icon", "Read a launchable activity icon as a bounded PNG. Use a component from appCatalogue. sizePixels defaults to 64 and accepts 16 through 128. Works through the shared HTML and network command API.",
            mapOf(KEY_COMPONENT to string("Exact launchable component from appCatalogue.", 512), KEY_ICON_SIZE to integer(MINIMUM_ICON_SIZE, MAXIMUM_ICON_SIZE, DEFAULT_ICON_SIZE)), setOf(KEY_COMPONENT)),
        Definition(DikcizHardwareControl.TYPE_TORCH, "dikciz_torch_control", "Observe flash-camera torch state through bounded Android callbacks or submit on/off. Unknown and unavailable are explicit. Submission does not claim physical light verification.", DikcizTorchControl.FIELDS, setOf("action"), readOnly = false),
        Definition(DikcizHardwareControl.TYPE_VIBRATION, "dikciz_vibration_control", "Discover vibrator and predefined-effect support; submit a bounded pulse, waveform, predefined effect or cancellation. Android settings may suppress physical vibration.", DikcizHapticsControl.FIELDS, setOf("action"), readOnly = false),
        Definition(DikcizHardwareControl.TYPE_SETTINGS, "dikciz_system_setting", "Read or write an allowlisted system setting. Set requires owner-granted Modify system settings and returns read-back.", mapOf("action" to string("get or set.", 16), "setting" to string("brightness, automaticBrightness, automaticRotation, rotation or screenTimeoutMilliseconds.", 64), "value" to integer(0, Int.MAX_VALUE)), setOf("action", "setting"), readOnly = false),
        Definition(DikcizHardwareControl.TYPE_BLUETOOTH, "dikciz_bluetooth_devices", "List paired Bluetooth devices with actual bond state. Does not scan, pair or connect.", PAGE_FIELDS, emptySet(), readOnly = true),
        Definition(DikcizHardwareControl.TYPE_USB, "dikciz_usb_devices", "List attached USB host devices and per-device access. Does not request USB permission or transfer bytes.", PAGE_FIELDS, emptySet(), readOnly = true),
        Definition(DikcizHardwareControl.TYPE_NFC, "dikciz_nfc_status", "Read NFC adapter availability and enabled state without changing it.", emptyMap(), emptySet(), readOnly = true),
        Definition(DikcizHardwareControl.TYPE_WIFI, "dikciz_wifi_scan_results", "Read cached Wi-Fi scan results with Android timestamps. Requires location access; does not initiate a scan or connect.", PAGE_FIELDS, emptySet(), readOnly = true),
        Definition(DikcizHardwareControl.TYPE_AUDIO, "dikciz_audio_devices", "List Android audio input and output devices, sample rates and channel counts.", PAGE_FIELDS, emptySet(), readOnly = true),
        Definition(DikcizHardwareControl.TYPE_CAMERA, "dikciz_camera_capabilities", "Read JPEG and recorder sizes, camera frame-rate ranges, digital zoom, exposure range/EV step, sensor orientation and autofocus modes for one camera.", mapOf("cameraId" to string("Camera ID from cameraList.", 128)), setOf("cameraId"), readOnly = true),
        Definition(TYPE_DEVICE_STATE, "dikciz_device_state", "Read screen, battery, charging, audio, network, RAM, storage, CPU samples and bounded sensor inventory. System CPU may be unavailable under Android restrictions; sensor inventory does not claim live readings.", emptyMap()),
    ).plus(DikcizMicrophoneStreams.DEFINITIONS).plus(DikcizUssdControl.DEFINITION).plus(DikcizCameraVideo.DEFINITION).plus(DikcizEventJournal.DEFINITIONS).plus(DikcizShellSessions.DEFINITIONS).plus(DikcizSpeechModels.DEFINITIONS).plus(DikcizWifiControl.DEFINITIONS).plus(DikcizMicrophoneSession.DEFINITIONS).plus(DikcizAudioPlayback.DEFINITIONS).plus(DikcizNotificationControl.DEFINITIONS).plus(DikcizFileManagement.DEFINITIONS).plus(DikcizCommandJobs.DEFINITIONS).plus(DikcizUsbControl.DEFINITIONS).plus(DikcizLocationServices.DEFINITIONS).plus(DikcizNfcControl.DEFINITIONS).plus(DikcizBluetoothControl.DEFINITIONS).plus(DikcizGattControl.DEFINITIONS).associateBy(Definition::type)
    val FIELDS = DEFINITIONS.mapValues { (_, definition) -> definition.fields.keys }
    val REQUIRED_FIELDS = DEFINITIONS.mapValues { (_, definition) -> definition.required }
    val TOOLS = DEFINITIONS.values.associate { it.tool to it.type }
    val READ_ONLY_TYPES = DEFINITIONS.values.filter(Definition::readOnly).map(Definition::type).toSet()
    val BACKGROUND_TYPES = DEFINITIONS.values.filterNot(Definition::mainThread).map(Definition::type).toSet()

    fun validate(type: String, request: JSONObject, envelope: Boolean = false) {
        val definition = DEFINITIONS.getValue(type)
        validateFields(request, definition.fields, definition.required, if (envelope) ENVELOPE else emptySet())
        if (type in DikcizMicrophoneStreams.TYPES) DikcizMicrophoneStreams.validate(type, request)
        if (type in DikcizEventJournal.TYPES) DikcizEventJournal.validate(type, request)
        if (type in DikcizShellSessions.TYPES) DikcizShellSessions.validate(type, request)
        if (type in DikcizSpeechModels.TYPES) DikcizSpeechModels.validate(type, request)
        if (type in DikcizCommandJobs.TYPES) DikcizCommandJobs.validate(type, request)
        if (type in DikcizNfcControl.TYPES) DikcizNfcControl.validate(type, request)
        if (type in DikcizBluetoothControl.TYPES) DikcizBluetoothControl.validate(type, request)
        if (type == DikcizGattControl.TYPE) DikcizGattControl.validate(request)
        if (type == DikcizAudioPlayback.TYPE) DikcizAudioPlayback.validate(request)
        if (type == DikcizMicrophoneSession.TYPE) DikcizMicrophoneSession.validate(request)
        when (type) {
            DikcizUssdControl.TYPE -> DikcizUssdControl.validate(request)
            DikcizCameraVideo.TYPE -> DikcizCameraVideo.validate(request)
            TYPE_CAMERA_CAPTURE -> DikcizCameraControls.validate(request)
            DikcizLocationServices.TYPE_GEOCODE -> DikcizLocationServices.validate(request)
            DikcizUsbControl.TYPE_TRANSFER -> DikcizUsbControl.validate(request)
            DikcizFileManagement.TYPE -> DikcizFileManagement.validate(request)
            DikcizNotificationControl.TYPE -> DikcizNotificationControl.validate(request)
            DikcizHardwareControl.TYPE_TORCH -> DikcizTorchControl.validate(request)
            DikcizHardwareControl.TYPE_VIBRATION -> DikcizHapticsControl.validate(request)
            DikcizHardwareControl.TYPE_SETTINGS -> {
                val action = request.getString("action")
                if (action !in setOf("get", "set")) invalid("Unsupported settings action")
                val setting = DikcizHardwareControl.SETTINGS[request.getString("setting")] ?: invalid("Unsupported system setting")
                if (action == "set" && (!request.has("value") || request.getInt("value") !in setting.second)) invalid("Setting value is outside its allowed range")
                if (action == "get" && request.has("value")) invalid("Get does not accept a value")
            }

            TYPE_SPEAK, TYPE_SYNTHESIZE -> {
                if (request.has("language") && !Regex("[A-Za-z]{2,8}(-[A-Za-z0-9]{1,8})*").matches(request.getString("language"))) invalid("Invalid BCP-47 language tag")
                if (request.has("voiceName") && request.getString("voiceName").isBlank()) invalid("Voice name cannot be blank")
            }
            TYPE_SPEECH_RECOGNIZE -> validateRecognitionOptions(request)

            TYPE_CALL_AUDIO -> {
                if (request.getString("action") !in setOf("status", "record", "speak")) invalid("Unsupported call audio action")
                if (request.getString("action") == "speak" && request.optString(KEY_TEXT).isBlank()) invalid("Call speech requires text")
            }
            TYPE_APP_ICON -> if (android.content.ComponentName.unflattenFromString(request.getString(KEY_COMPONENT)) == null) invalid("Invalid app component")
            TYPE_QUERY -> DikcizContentData.validateQuery(request)
            TYPE_WRITE -> DikcizContentData.validateWrite(request, false)
            TYPE_BATCH -> {
                val operations = request.getJSONArray(KEY_OPERATIONS)
                if (operations.length() !in 1..MAXIMUM_BATCH) invalid()
                var authority: String? = null
                for (index in 0 until operations.length()) {
                    val operation = operations.optJSONObject(index) ?: invalid()
                    validateFields(operation, WRITE_FIELDS, setOf(KEY_RESOURCE, KEY_OPERATION))
                    val resource = DikcizContentData.validateWrite(operation, true)
                    if (authority != null && authority != resource.uri.authority) invalid("Batches must stay within one provider")
                    authority = resource.uri.authority
                    operation.optJSONObject(KEY_BACK_REFERENCES)?.let { references ->
                        references.keys().forEach { key ->
                            val prior = strictInteger(references.opt(key), 0, (index - 1).toLong())
                            val earlier = operations.getJSONObject(prior.toInt())
                            if (earlier.getString(KEY_OPERATION) != OP_CREATE ||
                                key !in resource.references || earlier.getString(KEY_RESOURCE) != resource.references[key]) invalid("Invalid batch foreign-key reference")
                        }
                    }
                }
            }
            TYPE_SMS_SEND -> {
                if (!UUID_PATTERN.matches(request.getString(KEY_REQUEST_KEY))) invalid("Invalid idempotencyKey")
                validatePhone(request.getString(KEY_RECIPIENT))
                if (request.getString(KEY_BODY).isBlank()) invalid("SMS body cannot be blank")
            }
            TYPE_SMS_STATUS -> if (!UUID_PATTERN.matches(request.getString(KEY_REQUEST_KEY))) invalid("Invalid idempotencyKey")
            TYPE_CALL -> {
                val action = request.getString("action")
                if (action !in setOf("state", "place", "answer", "end")) invalid("Unsupported call action")
                if (action == "place") validatePhone(request.optString(KEY_RECIPIENT))
                if (action != "place" && request.has(KEY_RECIPIENT)) invalid("Only place accepts recipient")
            }
        }
    }

    fun validateFields(request: JSONObject, fields: Map<String, JSONObject>, required: Set<String>, extra: Set<String> = emptySet()) {
        if (request.keys().asSequence().any { it !in fields && it !in extra } || required.any { !request.has(it) }) invalid()
        for ((key, schema) in fields) {
            if (!request.has(key)) continue
            val value = request.opt(key)
            when (schema.getString("type")) {
                TYPE_STRING -> if (value !is String || value.length > schema.optInt("maxLength", MAXIMUM_TEXT) || '\u0000' in value) invalid()
                "number" -> {
                    if (value !is Number) invalid()
                    val number = value.toDouble()
                    if (!number.isFinite() || number < schema.optDouble("minimum", -Double.MAX_VALUE) || number > schema.optDouble("maximum", Double.MAX_VALUE)) invalid()
                }
                "boolean" -> if (value !is Boolean) invalid()
                TYPE_INTEGER -> strictInteger(value, schema.optLong("minimum", 0), schema.optLong("maximum", Int.MAX_VALUE.toLong()))
                TYPE_OBJECT -> if (value !is JSONObject) invalid()
                TYPE_ARRAY -> if (value !is JSONArray) invalid()
            }
        }
    }

    fun validateRecognitionOptions(request: JSONObject) {
        if (request.optString("mode", DikcizSpeechRecognition.MODE_ON_DEVICE) !in setOf(DikcizSpeechRecognition.MODE_ON_DEVICE, DikcizSpeechRecognition.MODE_SYSTEM)) invalid("Unsupported speech recognition mode")
        if (request.has("language") && !Regex("[A-Za-z]{2,8}(-[A-Za-z0-9]{1,8})*").matches(request.getString("language"))) invalid("Invalid BCP-47 language tag")
    }

    fun strictInteger(value: Any?, minimum: Long, maximum: Long): Long {
        if (value !is Int && value !is Long) invalid()
        val number = (value as Number).toLong()
        if (number !in minimum..maximum) invalid()
        return number
    }

    fun invalid(message: String = "Invalid native command arguments"): Nothing = throw DikcizAutomationRequestException("validation_failed", message)
    fun failure(code: String, message: String): Nothing = throw DikcizAutomationRequestException(code, message)
    fun requirePermission(context: android.content.Context, permission: String, requirement: String) {
        if (context.checkSelfPermission(permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            failure("native_permission_" + requirement, "Android permission is required for this operation")
        }
    }
    fun page(items: List<JSONObject>, request: JSONObject): JSONObject = page(items, request) { it }

    fun <T> page(items: List<T>, request: JSONObject, encode: (T) -> JSONObject): JSONObject {
        val offset = request.optInt(KEY_OFFSET, 0)
        val limit = request.optInt(KEY_LIMIT, DEFAULT_LIMIT)
        return JSONObject().put(KEY_ITEMS, JSONArray(items.drop(offset).take(limit).map(encode)))
            .put(KEY_OFFSET, offset).put(KEY_LIMIT, limit).put(KEY_HAS_MORE, offset.toLong() + limit < items.size)
    }
    private fun validatePhone(value: String) {
        normalizedRecipient(value)
    }

    fun normalizedRecipient(value: String): String {
        if (!FORMATTED_PHONE_PATTERN.matches(value)) invalid("recipient must be a dialable phone number")
        val normalized = android.telephony.PhoneNumberUtils.normalizeNumber(value)
        if (!PHONE_PATTERN.matches(normalized)) invalid("recipient must contain a valid phone number")
        return normalized
    }
    private fun string(description: String, maximum: Int): JSONObject = JSONObject().put("type", TYPE_STRING).put("description", description).put("maxLength", maximum)
    private fun integer(minimum: Int, maximum: Int, default: Int? = null): JSONObject = JSONObject().put("type", TYPE_INTEGER).put("minimum", minimum).put("maximum", maximum).apply { default?.let { put("default", it) } }
}
