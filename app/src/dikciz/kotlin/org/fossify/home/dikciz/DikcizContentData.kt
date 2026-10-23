package org.fossify.home.dikciz

import android.Manifest
import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Telephony
import java.nio.charset.StandardCharsets
import java.util.Calendar
import org.json.JSONArray
import org.json.JSONObject

internal class DikcizContentData(private val context: Context) {
    fun status(): JSONObject = JSONObject().put("resources", JSONArray(RESOURCES.map { (name, resource) ->
        JSONObject().put("resource", name).put("columns", JSONArray(resource.columns.toList()))
            .put("writableColumns", JSONArray(resource.writable.toList())).put("requiredCreateFields", JSONArray(resource.requiredCreate.toList()))
            .put("foreignKeys", JSONObject(resource.references)).put("searchColumns", JSONArray(resource.search.toList()))
            .put("requirementId", resource.requirement)
            .put("readGranted", context.checkSelfPermission(resource.permission) == android.content.pm.PackageManager.PERMISSION_GRANTED)
            .put("writeGranted", resource.writePermission != null && context.checkSelfPermission(resource.writePermission) == android.content.pm.PackageManager.PERMISSION_GRANTED)
    })).put("maximumPageSize", DikcizNativeCommands.MAXIMUM_LIMIT).put("maximumBatchSize", DikcizNativeCommands.MAXIMUM_BATCH)
        .put("maximumFieldCharacters", DikcizNativeCommands.MAXIMUM_TEXT).put("maximumPageBytes", DikcizNativeCommands.MAXIMUM_RESPONSE_BYTES)

    fun query(request: JSONObject): JSONObject {
        val resource = resource(request)
        DikcizNativeCommands.requirePermission(context, resource.permission, resource.requirement)
        val columns = request.optJSONArray(DikcizNativeCommands.KEY_COLUMNS)?.let { array ->
            (0 until array.length()).map(array::getString)
        } ?: resource.columns.take(DEFAULT_PROJECTION_COLUMNS)
        val terms = mutableListOf<String>()
        val arguments = mutableListOf<String>()
        request.optJSONObject(DikcizNativeCommands.KEY_FILTER)?.let { filters ->
            filters.keys().asSequence().sorted().forEach { column ->
                if (filters.isNull(column)) terms += "$column IS NULL" else {
                    terms += "$column = ?"
                    arguments += when (val value = filters.get(column)) { is Boolean -> if (value) "1" else "0"; else -> value.toString() }
                }
            }
        }
        request.optString(DikcizNativeCommands.KEY_QUERY).takeIf(String::isNotEmpty)?.let { query ->
            val escaped = query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
            terms += resource.search.joinToString(" OR ", "(", ")") { column -> "$column LIKE ? ESCAPE '\\'" }
            resource.search.forEach { arguments += "%$escaped%" }
        }
        val limit = request.optInt(DikcizNativeCommands.KEY_LIMIT, DikcizNativeCommands.DEFAULT_LIMIT)
        val offset = request.optInt(DikcizNativeCommands.KEY_OFFSET, 0)
        val items = JSONArray()
        var hasMore = false
        var bytes = 0
        val isCalendarInstances = request.getString(DikcizNativeCommands.KEY_RESOURCE) == RESOURCE_CALENDAR_INSTANCES
        val rangeStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val rangeEnd = (rangeStart.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, AGENDA_DAYS) }
        val queryUri = if (isCalendarInstances) {
            resource.uri.buildUpon().also { builder ->
                ContentUris.appendId(builder, rangeStart.timeInMillis)
                ContentUris.appendId(builder, rangeEnd.timeInMillis)
            }.build()
        } else resource.uri
        val sortOrder = if (isCalendarInstances) "begin ASC, end ASC, event_id ASC" else "_id ASC"
        context.contentResolver.query(queryUri, columns.toTypedArray(), terms.takeIf { it.isNotEmpty() }?.joinToString(" AND "),
            arguments.takeIf { it.isNotEmpty() }?.toTypedArray(), sortOrder)?.use { cursor ->
            cursor.moveToPosition(offset - 1)
            while (cursor.moveToNext()) {
                if (items.length() == limit) { hasMore = true; break }
                val row = JSONObject()
                val truncated = JSONArray()
                columns.forEachIndexed { index, column ->
                    val value: Any = when (cursor.getType(index)) {
                        Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
                        Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
                        Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index)
                        Cursor.FIELD_TYPE_STRING -> cursor.getString(index).let { text ->
                            if (text.length > DikcizNativeCommands.MAXIMUM_TEXT) truncated.put(column)
                            text.take(DikcizNativeCommands.MAXIMUM_TEXT)
                        }
                        else -> throw DikcizAutomationRequestException(DikcizNativeCommands.ERROR_FAILED, "Binary provider columns are not exposed")
                    }
                    row.put(column, value)
                }
                if (truncated.length() > 0) row.put("truncatedFields", truncated)
                val rowBytes = row.toString().toByteArray(StandardCharsets.UTF_8).size
                if (bytes + rowBytes > DikcizNativeCommands.MAXIMUM_RESPONSE_BYTES) { hasMore = true; break }
                items.put(row)
                bytes += rowBytes
            }
        } ?: DikcizNativeCommands.failure(DikcizNativeCommands.ERROR_UNAVAILABLE, "Android provider returned no cursor")
        val result = JSONObject().put(DikcizNativeCommands.KEY_RESOURCE, request.getString(DikcizNativeCommands.KEY_RESOURCE))
            .put(DikcizNativeCommands.KEY_ITEMS, items).put(DikcizNativeCommands.KEY_LIMIT, limit)
            .put(DikcizNativeCommands.KEY_OFFSET, offset).put(DikcizNativeCommands.KEY_HAS_MORE, hasMore)
        if (isCalendarInstances) {
            result.put("rangeStartMilliseconds", rangeStart.timeInMillis).put("rangeEndMilliseconds", rangeEnd.timeInMillis)
        }
        return result
    }

    fun write(request: JSONObject, batch: Boolean): JSONObject {
        val inputs = if (batch) request.getJSONArray(DikcizNativeCommands.KEY_OPERATIONS) else JSONArray().put(request)
        val operations = ArrayList<ContentProviderOperation>()
        var authority: String? = null
        for (index in 0 until inputs.length()) {
            val input = inputs.getJSONObject(index)
            val resource = resource(input)
            val permission = resource.writePermission ?: DikcizNativeCommands.failure(DikcizNativeCommands.ERROR_READ_ONLY, "Resource is read-only")
            DikcizNativeCommands.requirePermission(context, permission, resource.requirement)
            authority = resource.uri.authority
            val operation = input.getString(DikcizNativeCommands.KEY_OPERATION)
            val uri = if (operation == DikcizNativeCommands.OP_CREATE) resource.uri else ContentUris.withAppendedId(resource.uri, input.getLong(DikcizNativeCommands.KEY_ID))
            val builder = when (operation) {
                DikcizNativeCommands.OP_CREATE -> ContentProviderOperation.newInsert(uri)
                DikcizNativeCommands.OP_UPDATE -> ContentProviderOperation.newUpdate(uri).withExpectedCount(1)
                else -> ContentProviderOperation.newDelete(uri).withExpectedCount(1)
            }
            input.optJSONObject(DikcizNativeCommands.KEY_VALUES)?.let { values ->
                val content = ContentValues()
                values.keys().forEach { key ->
                    when (val value = values.get(key)) {
                        JSONObject.NULL -> content.putNull(key)
                        is String -> content.put(key, value)
                        is Int -> content.put(key, value)
                        is Long -> content.put(key, value)
                        is Boolean -> content.put(key, if (value) 1 else 0)
                    }
                }
                builder.withValues(content)
            }
            input.optJSONObject(DikcizNativeCommands.KEY_BACK_REFERENCES)?.let { references ->
                references.keys().forEach { key -> builder.withValueBackReference(key, references.getInt(key)) }
            }
            operations += builder.build()
        }
        val results = context.contentResolver.applyBatch(requireNotNull(authority), operations)
        return JSONObject().put("results", JSONArray(results.map { result ->
            JSONObject().put("id", result.uri?.lastPathSegment?.toLongOrNull() ?: JSONObject.NULL)
                .put("count", result.count ?: JSONObject.NULL)
        }))
    }

    data class Resource(
        val uri: Uri,
        val columns: Set<String>,
        val permission: String,
        val requirement: String,
        val writable: Set<String> = emptySet(),
        val writePermission: String? = null,
        val requiredCreate: Set<String> = emptySet(),
        val search: Set<String> = emptySet(),
        val references: Map<String, String> = emptyMap(),
        val numeric: Set<String> = emptySet(),
    )
    companion object {
        private fun mediaPermission(resource: String): String {
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
                return Manifest.permission.READ_EXTERNAL_STORAGE
            }
            return when (resource) {
                "images" -> Manifest.permission.READ_MEDIA_IMAGES
                "audio" -> Manifest.permission.READ_MEDIA_AUDIO
                else -> Manifest.permission.READ_MEDIA_VIDEO
            }
        }

        private const val RESOURCE_CALENDAR_INSTANCES = "calendarInstances"
        private const val AGENDA_DAYS = 7
        private const val DEFAULT_PROJECTION_COLUMNS = 12
        private const val DATA_COLUMN_COUNT = 10
        private const val MAXIMUM_FILTERS = 16
        private const val MAXIMUM_PROVIDER_NUMBER = 9_007_199_254_740_991L
        private const val REQUIREMENT_CONTACTS = "contacts"
        private const val REQUIREMENT_CALENDAR = "calendar"
        private val CONTACT_DATA = (1..DATA_COLUMN_COUNT).map { "data$it" }.toSet()
        val RESOURCES = linkedMapOf(
            "contacts" to Resource(ContactsContract.Contacts.CONTENT_URI, setOf("_id", "display_name", "has_phone_number", "starred", "lookup"), Manifest.permission.READ_CONTACTS, REQUIREMENT_CONTACTS,
                search = setOf("display_name")),
            "phones" to Resource(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, setOf("_id", "contact_id", "raw_contact_id", "display_name", "data1", "data2", "data3", "data4", "is_primary"), Manifest.permission.READ_CONTACTS, REQUIREMENT_CONTACTS,
                search = setOf("display_name", "data1")),
            "emails" to Resource(ContactsContract.CommonDataKinds.Email.CONTENT_URI, setOf("_id", "contact_id", "raw_contact_id", "display_name", "data1", "data2", "data3"), Manifest.permission.READ_CONTACTS, REQUIREMENT_CONTACTS,
                search = setOf("display_name", "data1")),
            "rawContacts" to Resource(ContactsContract.RawContacts.CONTENT_URI, setOf("_id", "contact_id", "account_name", "account_type", "starred"), Manifest.permission.READ_CONTACTS, REQUIREMENT_CONTACTS,
                setOf("account_name", "account_type", "starred"), Manifest.permission.WRITE_CONTACTS, numeric = setOf("starred")),
            "contactData" to Resource(ContactsContract.Data.CONTENT_URI, setOf("_id", "contact_id", "raw_contact_id", "display_name", "mimetype") + CONTACT_DATA, Manifest.permission.READ_CONTACTS, REQUIREMENT_CONTACTS,
                CONTACT_DATA + setOf("raw_contact_id", "mimetype", "is_primary"), Manifest.permission.WRITE_CONTACTS,
                setOf("raw_contact_id", "mimetype"), references = mapOf("raw_contact_id" to "rawContacts"), numeric = setOf("raw_contact_id", "is_primary")),
            "calendars" to Resource(CalendarContract.Calendars.CONTENT_URI, setOf("_id", "calendar_displayName", "name", "account_name", "account_type", "calendar_access_level", "calendar_timezone", "visible"), Manifest.permission.READ_CALENDAR, REQUIREMENT_CALENDAR,
                search = setOf("calendar_displayName", "name")),
            "calendarEvents" to Resource(CalendarContract.Events.CONTENT_URI, setOf("_id", "calendar_id", "title", "dtstart", "dtend", "eventTimezone", "allDay", "description", "eventLocation", "rrule", "duration", "deleted"), Manifest.permission.READ_CALENDAR, REQUIREMENT_CALENDAR,
                setOf("calendar_id", "title", "dtstart", "dtend", "eventTimezone", "allDay", "description", "eventLocation", "rrule", "duration"), Manifest.permission.WRITE_CALENDAR,
                setOf("calendar_id", "title", "dtstart", "eventTimezone"), search = setOf("title", "description"), numeric = setOf("calendar_id", "dtstart", "dtend", "allDay")),
            RESOURCE_CALENDAR_INSTANCES to Resource(CalendarContract.Instances.CONTENT_URI,
                setOf("event_id", "title", "begin", "end", "allDay", "eventLocation", "calendar_id", "visible"),
                Manifest.permission.READ_CALENDAR, REQUIREMENT_CALENDAR, search = setOf("title", "eventLocation")),
            "calendarReminders" to Resource(CalendarContract.Reminders.CONTENT_URI, setOf("_id", "event_id", "minutes", "method"), Manifest.permission.READ_CALENDAR, REQUIREMENT_CALENDAR,
                setOf("event_id", "minutes", "method"), Manifest.permission.WRITE_CALENDAR, setOf("event_id", "minutes", "method"), references = mapOf("event_id" to "calendarEvents"), numeric = setOf("event_id", "minutes", "method")),
            "sms" to Resource(Telephony.Sms.CONTENT_URI, setOf("_id", "thread_id", "address", "body", "date", "date_sent", "type", "status", "read", "sub_id"), Manifest.permission.READ_SMS, "sms", search = setOf("address", "body")),
            "callLog" to Resource(CallLog.Calls.CONTENT_URI, setOf("_id", "number", "date", "duration", "type", "name", "subscription_id", "subscription_component_name"), Manifest.permission.READ_CALL_LOG, "callHistory", search = setOf("number", "name")),
            "images" to Resource(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, setOf("_id", "_display_name", "mime_type", "_size", "date_added", "date_modified", "width", "height"), mediaPermission("images"), "mediaLibrary", search = setOf("_display_name")),
            "audio" to Resource(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, setOf("_id", "_display_name", "mime_type", "_size", "date_added", "title", "artist", "album", "duration"), mediaPermission("audio"), "mediaLibrary", search = setOf("_display_name", "title", "artist")),
            "videos" to Resource(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, setOf("_id", "_display_name", "mime_type", "_size", "date_added", "duration", "width", "height"), mediaPermission("videos"), "mediaLibrary", search = setOf("_display_name")),
        )
        private val CONTACT_MIME_TYPES = setOf(ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE, ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE, ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE, ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE)

        fun resource(request: JSONObject): Resource = RESOURCES[request.optString(DikcizNativeCommands.KEY_RESOURCE)]
            ?: DikcizNativeCommands.invalid("Unknown native data resource")
        fun validateQuery(request: JSONObject) {
            val resource = resource(request)
            request.optJSONArray(DikcizNativeCommands.KEY_COLUMNS)?.let { columns ->
                if (columns.length() !in 1..DikcizNativeCommands.MAXIMUM_COLUMNS) DikcizNativeCommands.invalid()
                val names = (0 until columns.length()).map { columns.opt(it) }
                if (names.any { it !is String || it !in resource.columns } || names.distinct().size != names.size) DikcizNativeCommands.invalid("Unsupported or repeated projection column")
            }
            request.optJSONObject(DikcizNativeCommands.KEY_FILTER)?.let { filters ->
                if (filters.length() > MAXIMUM_FILTERS) DikcizNativeCommands.invalid()
                filters.keys().forEach { key ->
                    if (key !in resource.columns) DikcizNativeCommands.invalid("Unsupported filter column")
                    validateValue(filters.get(key))
                }
            }
            if (request.has(DikcizNativeCommands.KEY_QUERY) && resource.search.isEmpty()) DikcizNativeCommands.invalid("This resource does not support text search")
        }
        fun validateWrite(request: JSONObject, batch: Boolean): Resource {
            val resource = resource(request)
            if (resource.writePermission == null) DikcizNativeCommands.failure(DikcizNativeCommands.ERROR_READ_ONLY, "Resource is read-only")
            val operation = request.getString(DikcizNativeCommands.KEY_OPERATION)
            if (operation !in setOf(DikcizNativeCommands.OP_CREATE, DikcizNativeCommands.OP_UPDATE, DikcizNativeCommands.OP_DELETE)) DikcizNativeCommands.invalid("Unsupported provider write operation")
            if ((operation == DikcizNativeCommands.OP_CREATE) == request.has(DikcizNativeCommands.KEY_ID)) DikcizNativeCommands.invalid("Updates and deletes require one row id; create must omit id")
            val values = request.optJSONObject(DikcizNativeCommands.KEY_VALUES)
            val references = request.optJSONObject(DikcizNativeCommands.KEY_BACK_REFERENCES)
            if (operation == DikcizNativeCommands.OP_DELETE) {
                if (values != null || references != null) DikcizNativeCommands.invalid()
                return resource
            }
            if (values == null || (values.length() == 0 && (references?.length() ?: 0) == 0)) DikcizNativeCommands.invalid("Writes require values")
            values.keys().forEach { key ->
                if (key !in resource.writable) DikcizNativeCommands.invalid("Unsupported write column")
                val value = values.get(key)
                validateValue(value)
                if (key in resource.numeric && value != JSONObject.NULL && value !is Int && value !is Long && value !is Boolean) DikcizNativeCommands.invalid("Numeric column requires an integer")
                if (key !in resource.numeric && value != JSONObject.NULL && value !is String) DikcizNativeCommands.invalid("Text column requires a string")
                if (key == "mimetype" && value !in CONTACT_MIME_TYPES) DikcizNativeCommands.invalid("Unsupported contact data MIME type")
            }
            if (references != null) {
                if (!batch || references.keys().asSequence().any { it !in resource.references || values.has(it) }) DikcizNativeCommands.invalid("Invalid back references")
            }
            if (operation == DikcizNativeCommands.OP_CREATE && resource.requiredCreate.any { (values.isNull(it) || !values.has(it)) && references?.has(it) != true }) DikcizNativeCommands.invalid("Missing required provider values")
            if (!values.isNull("dtstart") && !values.isNull("dtend") && values.getLong("dtend") < values.getLong("dtstart")) DikcizNativeCommands.invalid("Event end precedes its start")
            return resource
        }
        private fun validateValue(value: Any?) {
            when (value) {
                JSONObject.NULL -> Unit
                is String -> if (value.length > DikcizNativeCommands.MAXIMUM_TEXT || '\u0000' in value) DikcizNativeCommands.invalid()
                is Int, is Long -> DikcizNativeCommands.strictInteger(value, -MAXIMUM_PROVIDER_NUMBER, MAXIMUM_PROVIDER_NUMBER)
                is Boolean -> Unit
                else -> DikcizNativeCommands.invalid("Provider values must be primitive strings, integers, booleans or null")
            }
        }
    }
}
