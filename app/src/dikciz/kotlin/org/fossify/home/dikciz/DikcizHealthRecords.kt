package org.fossify.home.dikciz

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.*
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.reflect.KClass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

internal object DikcizHealthRecords {
    const val CATALOGUE = "healthRecordsCatalogue"
    const val READ = "healthRecordsRead"
    const val AGGREGATE = "healthRecordsAggregate"
    const val WRITE = "healthRecordsWrite"
    const val DELETE = "healthRecordsDelete"
    const val ACCESS = "healthPermissionsRequest"
    private const val MAX_RANGE = 366L * 24 * 60 * 60 * 1000
    private const val MAX_RESPONSE = 524288
    private const val NOTIFICATION = 7311
    data class Kind(val type: KClass<out Record>, val interval: Boolean, val values: Set<String>,
        val metrics: Map<String, AggregateMetric<*>> = emptyMap())
    val kinds = linkedMapOf(
        "steps" to Kind(StepsRecord::class, true, setOf("count"), mapOf("count" to StepsRecord.COUNT_TOTAL)),
        "distance" to Kind(DistanceRecord::class, true, setOf("meters"), mapOf("meters" to DistanceRecord.DISTANCE_TOTAL)),
        "activeCalories" to Kind(ActiveCaloriesBurnedRecord::class, true, setOf("kilocalories"), mapOf("kilocalories" to ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)),
        "totalCalories" to Kind(TotalCaloriesBurnedRecord::class, true, setOf("kilocalories"), mapOf("kilocalories" to TotalCaloriesBurnedRecord.ENERGY_TOTAL)),
        "heartRate" to Kind(HeartRateRecord::class, true, setOf("samples"), mapOf("average" to HeartRateRecord.BPM_AVG,"minimum" to HeartRateRecord.BPM_MIN,"maximum" to HeartRateRecord.BPM_MAX)),
        "sleep" to Kind(SleepSessionRecord::class, true, setOf("title", "notes", "stages"), mapOf("durationMilliseconds" to SleepSessionRecord.SLEEP_DURATION_TOTAL)),
        "exercise" to Kind(ExerciseSessionRecord::class, true, setOf("exerciseType", "title", "notes"), mapOf("durationMilliseconds" to ExerciseSessionRecord.EXERCISE_DURATION_TOTAL)),
        "weight" to Kind(WeightRecord::class, false, setOf("kilograms"), mapOf("kilogramsAverage" to WeightRecord.WEIGHT_AVG)),
        "height" to Kind(HeightRecord::class, false, setOf("meters"), mapOf("metersAverage" to HeightRecord.HEIGHT_AVG)),
        "hydration" to Kind(HydrationRecord::class, true, setOf("liters"), mapOf("liters" to HydrationRecord.VOLUME_TOTAL)),
        "bodyFat" to Kind(BodyFatRecord::class, false, setOf("percent")),
        "bodyTemperature" to Kind(BodyTemperatureRecord::class, false, setOf("celsius")),
        "bloodPressure" to Kind(BloodPressureRecord::class, false, setOf("systolicMmHg", "diastolicMmHg"), mapOf("systolicAverage" to BloodPressureRecord.SYSTOLIC_AVG,"diastolicAverage" to BloodPressureRecord.DIASTOLIC_AVG)),
        "bloodGlucose" to Kind(BloodGlucoseRecord::class, false, setOf("millimolesPerLiter")),
        "oxygenSaturation" to Kind(OxygenSaturationRecord::class, false, setOf("percent")),
        "floors" to Kind(FloorsClimbedRecord::class, true, setOf("floors"), mapOf("floors" to FloorsClimbedRecord.FLOORS_CLIMBED_TOTAL)),
    )
    private fun writeShape(name: String, kind: Kind): JSONObject {
        val properties = JSONObject()
        kind.values.forEach { key ->
            properties.put(key, when (key) {
                "title", "notes" -> JSONObject().put("type", "string").put("maxLength", 4000)
                "samples" -> JSONObject().put("type", "array").put("minItems", 1).put("maxItems", 2000)
                    .put("itemFields", JSONArray(listOf("timeMilliseconds:integer", "beatsPerMinute:integer(1..300)")))
                "stages" -> JSONObject().put("type", "array").put("maxItems", 2000)
                    .put("itemFields", JSONArray(listOf("startTimeMilliseconds:integer", "endTimeMilliseconds:integer", "stage:integer(0..7)")))
                "count", "exerciseType" -> JSONObject().put("type", "integer")
                else -> JSONObject().put("type", "number")
            })
        }
        val optional = setOf("title", "notes", "stages")
        return JSONObject().put("clientRecordId", "Required stable nonempty string")
            .put("clientRecordVersion", "Optional nonnegative integer")
            .put("timestampFields", JSONArray(if (kind.interval) listOf("startTimeMilliseconds", "endTimeMilliseconds") else listOf("timeMilliseconds")))
            .put("timestampUnits", "UTC Unix milliseconds").put("values", properties)
            .put("requiredValues", JSONArray(kind.values.filter { it !in optional }))
    }
    private fun kind(name: String) = kinds[name] ?: DikcizNativeCommands.invalid("Unknown health record kind")
    fun permissions(request: JSONObject): Set<String> {
        val names = request.getJSONArray("kinds")
        val access = request.getString("access")
        if (access !in setOf("read", "write", "readWrite")) DikcizNativeCommands.invalid("Choose read, write or readWrite")
        val permissions = mutableSetOf<String>()
        for (index in 0 until names.length()) {
            val type = kind(names.getString(index)).type
            if (access != "write") permissions.add(HealthPermission.getReadPermission(type))
            if (access != "read") permissions.add(HealthPermission.getWritePermission(type))
        }
        if (request.optBoolean("includeBackground")) permissions.add(HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND)
        if (request.optBoolean("includeHistory")) permissions.add(HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY)
        return permissions
    }
    fun execute(context: Context, type: String, request: JSONObject, autoRequest: Boolean): JSONObject = runBlocking(Dispatchers.IO) {
        val sdk = HealthConnectClient.getSdkStatus(context)
        if (type == CATALOGUE) {
            val grants = if (sdk == HealthConnectClient.SDK_AVAILABLE) HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions() else emptySet()
            return@runBlocking JSONObject().put("sdkStatus", sdk).put("available", sdk == HealthConnectClient.SDK_AVAILABLE)
                .put("items", JSONArray(kinds.map { (name, value) ->
                    JSONObject().put("kind", name).put("interval", value.interval).put("values", JSONArray(value.values.toList()))
                        .put("aggregateMetrics", JSONArray(value.metrics.keys.toList()))
                        .put("writeShape", writeShape(name, value))
                        .put("readPermission", HealthPermission.getReadPermission(value.type))
                        .put("writePermission", HealthPermission.getWritePermission(value.type))
                        .put("readGranted", HealthPermission.getReadPermission(value.type) in grants)
                        .put("writeGranted", HealthPermission.getWritePermission(value.type) in grants)
                }))
        }
        if (sdk != HealthConnectClient.SDK_AVAILABLE) DikcizNativeCommands.failure("health_provider_unavailable", "Health Connect must be installed/enabled on this Android device")
        if (type == ACCESS) return@runBlocking requestAccess(context, request)
        val spec = kind(request.getString("kind"))
        val write = type == WRITE || type == DELETE
        val required = if (write) HealthPermission.getWritePermission(spec.type) else HealthPermission.getReadPermission(spec.type)
        val client = HealthConnectClient.getOrCreate(context)
        if (required !in client.permissionController.getGrantedPermissions()) {
            val access = JSONObject().put("kinds", JSONArray(listOf(request.getString("kind")))).put("access", if (write) "write" else "read")
            val recovery = if (autoRequest) requestAccess(context, access) else JSONObject().put("state", "permission_required")
            return@runBlocking JSONObject().put("state", "permission_required").put("missingPermissions", JSONArray(listOf(required)))
                .put("permissionRequest", recovery.put("command", ACCESS).put("arguments", access).put("retryRequired", true))
        }
        when (type) {
            READ -> {
                val filter = range(request)
                val response = client.readRecords(ReadRecordsRequest(recordType = spec.type, timeRangeFilter = filter,
                    pageSize = request.optInt("limit", 50), pageToken = request.optString("pageToken").takeIf { it.isNotEmpty() }))
                val result = JSONObject().put("items", JSONArray(response.records.map(::record)))
                    .put("nextPageToken", response.pageToken ?: JSONObject.NULL).put("hasMore", response.pageToken != null)
                if (result.toString().toByteArray(Charsets.UTF_8).size > MAX_RESPONSE) DikcizNativeCommands.failure("health_response_too_large", "Reduce the page size or time range")
                result
            }
            AGGREGATE -> {
                if (spec.metrics.isEmpty()) DikcizNativeCommands.invalid("This record kind has no aggregate metrics; read its raw records")
                val result = client.aggregate(AggregateRequest(spec.metrics.values.toSet(), range(request)))
                val values = JSONObject()
                spec.metrics.forEach { (name, metric) -> values.put(name, unit(result[metric]) ?: JSONObject.NULL) }
                JSONObject().put("kind", request.getString("kind")).put("values", values)
                    .put("dataOrigins", JSONArray(result.dataOrigins.map { it.packageName }))
            }
            WRITE -> {
                val rows = request.getJSONArray("records")
                val records = (0 until rows.length()).map { buildRecord(request.getString("kind"), rows.getJSONObject(it)) }
                val ids = client.insertRecords(records).recordIdsList
                JSONObject().put("state", "written").put("recordIds", JSONArray(ids))
            }
            DELETE -> {
                val ids = request.getJSONArray("recordIds")
                client.deleteRecords(spec.type, (0 until ids.length()).map { ids.getString(it) }, emptyList())
                JSONObject().put("state", "deleted").put("count", ids.length()).put("ownerDataOnly", true)
            }
            else -> DikcizNativeCommands.invalid()
        }
    }
    fun validate(type: String, request: JSONObject) {
        if (type == CATALOGUE) return
        if (type == ACCESS) { permissions(request); return }
        val spec = kind(request.getString("kind"))
        if (type == DELETE && !request.optBoolean("confirm")) DikcizNativeCommands.invalid("Deleting selected health records requires confirm=true")
        if (type == READ || type == AGGREGATE) {
            range(request)
            if (type == AGGREGATE && spec.metrics.isEmpty()) DikcizNativeCommands.invalid("This kind has no aggregate metrics")
        }
        if (type == WRITE) {
            val rows=request.getJSONArray("records")
            val ids=mutableSetOf<String>()
            for (index in 0 until rows.length()) {
                val row=rows.getJSONObject(index)
                val required=if (spec.interval) setOf("startTimeMilliseconds","endTimeMilliseconds") else setOf("timeMilliseconds")
                val forbidden=if (spec.interval) setOf("timeMilliseconds") else setOf("startTimeMilliseconds","endTimeMilliseconds")
                if (required.any { !row.has(it) } || forbidden.any(row::has)) DikcizNativeCommands.invalid("Use the catalogue's exact timestamp fields")
                if (!ids.add(row.getString("clientRecordId"))) DikcizNativeCommands.invalid("Duplicate client record ID in batch")
                val values=row.getJSONObject("values")
                for (field in listOf("title","notes")) {
                    if (values.has(field) && (values.opt(field) !is String || values.getString(field).length>4000)) DikcizNativeCommands.invalid("Invalid health text")
                }
                if (values.has("exerciseType")) DikcizNativeCommands.strictInteger(values.opt("exerciseType"),0,1000)
                try { buildRecord(request.getString("kind"),row) }
                catch (exception: org.json.JSONException) { DikcizNativeCommands.invalid("Missing or malformed health value fields") }
            }
        }
    }
    private fun range(request: JSONObject): TimeRangeFilter {
        val start = request.getLong("startTimeMilliseconds"); val end = request.getLong("endTimeMilliseconds")
        if (end <= start || end - start > MAX_RANGE) DikcizNativeCommands.invalid("Choose a positive range of at most 366 days")
        return TimeRangeFilter.between(Instant.ofEpochMilli(start), Instant.ofEpochMilli(end))
    }
    private fun buildRecord(name: String, row: JSONObject): Record {
        val spec = kind(name)
        val values = row.getJSONObject("values")
        if (values.keys().asSequence().any { it !in spec.values }) DikcizNativeCommands.invalid("Unknown health value field")
        val id = row.getString("clientRecordId")
        if (!Regex("[A-Za-z0-9][A-Za-z0-9_.-]{0,79}").matches(id)) DikcizNativeCommands.invalid("Invalid client record id")
        val metadata = Metadata.manualEntry(id, row.optLong("clientRecordVersion", 0))
        val start = Instant.ofEpochMilli(row.getLong(if (spec.interval) "startTimeMilliseconds" else "timeMilliseconds"))
        val end = if (spec.interval) Instant.ofEpochMilli(row.getLong("endTimeMilliseconds")) else start
        if (spec.interval && (!end.isAfter(start) || Duration.between(start,end).toMillis()>MAX_RANGE)) DikcizNativeCommands.invalid("Invalid health record interval")
        fun number(key: String): Double {
            val value = values.opt(key)
            if (value !is Number || !value.toDouble().isFinite()) DikcizNativeCommands.invalid("Expected numeric health value")
            return value.toDouble()
        }
        val offset = ZoneOffset.UTC
        return try { when (name) {
            "steps" -> StepsRecord(startTime=start, startZoneOffset=offset, endTime=end, endZoneOffset=offset, metadata=metadata, count=DikcizNativeCommands.strictInteger(values.opt("count"),0,1000000))
            "distance" -> DistanceRecord(startTime=start, startZoneOffset=offset, endTime=end, endZoneOffset=offset, metadata=metadata, distance=Length.meters(number("meters")))
            "activeCalories" -> ActiveCaloriesBurnedRecord(startTime=start, startZoneOffset=offset, endTime=end, endZoneOffset=offset, metadata=metadata, energy=Energy.kilocalories(number("kilocalories")))
            "totalCalories" -> TotalCaloriesBurnedRecord(startTime=start, startZoneOffset=offset, endTime=end, endZoneOffset=offset, metadata=metadata, energy=Energy.kilocalories(number("kilocalories")))
            "heartRate" -> {
                val samples = values.getJSONArray("samples")
                if (samples.length() !in 1..2000) DikcizNativeCommands.invalid("Provide 1..2000 heart-rate samples")
                HeartRateRecord(startTime=start, startZoneOffset=offset, endTime=end, endZoneOffset=offset, metadata=metadata, samples=(0 until samples.length()).map {
                    val sample = samples.getJSONObject(it)
                    if (sample.keys().asSequence().toSet() != setOf("timeMilliseconds","beatsPerMinute")) DikcizNativeCommands.invalid("Invalid sample fields")
                    HeartRateRecord.Sample(Instant.ofEpochMilli(DikcizNativeCommands.strictInteger(sample.opt("timeMilliseconds"),0,Long.MAX_VALUE)),
                        DikcizNativeCommands.strictInteger(sample.opt("beatsPerMinute"),1,300))
                })
            }
            "sleep" -> SleepSessionRecord(start, offset, end, offset, metadata, values.optString("title").takeIf { it.isNotEmpty() },
                values.optString("notes").takeIf { it.isNotEmpty() }, values.optJSONArray("stages")?.let { stages ->
                    if (stages.length()>2000) DikcizNativeCommands.invalid("Too many sleep stages")
                    (0 until stages.length()).map {
                        val stage=stages.getJSONObject(it)
                        if (stage.keys().asSequence().toSet()!=setOf("startTimeMilliseconds","endTimeMilliseconds","stage")) DikcizNativeCommands.invalid("Invalid stage fields")
                        SleepSessionRecord.Stage(Instant.ofEpochMilli(DikcizNativeCommands.strictInteger(stage.opt("startTimeMilliseconds"),0,Long.MAX_VALUE)),Instant.ofEpochMilli(DikcizNativeCommands.strictInteger(stage.opt("endTimeMilliseconds"),0,Long.MAX_VALUE)),DikcizNativeCommands.strictInteger(stage.opt("stage"),0,7).toInt())
                    }
                }.orEmpty())
            "exercise" -> ExerciseSessionRecord(start, offset, end, offset, metadata, values.getInt("exerciseType"),
                title=values.optString("title").takeIf { it.isNotEmpty() },notes=values.optString("notes").takeIf { it.isNotEmpty() })
            "weight" -> WeightRecord(time=start, zoneOffset=offset, metadata=metadata, weight=Mass.kilograms(number("kilograms")))
            "height" -> HeightRecord(time=start, zoneOffset=offset, metadata=metadata, height=Length.meters(number("meters")))
            "hydration" -> HydrationRecord(startTime=start, startZoneOffset=offset, endTime=end, endZoneOffset=offset, metadata=metadata, volume=Volume.liters(number("liters")))
            "bodyFat" -> BodyFatRecord(time=start, zoneOffset=offset, metadata=metadata, percentage=Percentage(number("percent")))
            "bodyTemperature" -> BodyTemperatureRecord(start, offset, metadata, Temperature.celsius(number("celsius")))
            "bloodPressure" -> BloodPressureRecord(start, offset, metadata, Pressure.millimetersOfMercury(number("systolicMmHg")),Pressure.millimetersOfMercury(number("diastolicMmHg")))
            "bloodGlucose" -> BloodGlucoseRecord(start, offset, metadata, BloodGlucose.millimolesPerLiter(number("millimolesPerLiter")))
            "oxygenSaturation" -> OxygenSaturationRecord(time=start, zoneOffset=offset, metadata=metadata, percentage=Percentage(number("percent")))
            "floors" -> FloorsClimbedRecord(startTime=start, startZoneOffset=offset, endTime=end, endZoneOffset=offset, metadata=metadata, floors=number("floors"))
            else -> DikcizNativeCommands.invalid()
        } } catch (exception: IllegalArgumentException) { DikcizNativeCommands.invalid("Health record values are outside the SDK bounds") }
    }
    private fun unit(value: Any?): Any? = when (value) {
        is Length -> value.inMeters; is Mass -> value.inKilograms; is Energy -> value.inKilocalories
        is Volume -> value.inLiters; is Duration -> value.toMillis(); is Pressure -> value.inMillimetersOfMercury
        is Number, is String, is Boolean -> value
        else -> value?.toString()
    }
    private fun record(value: Record): JSONObject {
        val out = JSONObject().put("id", value.metadata.id).put("dataOrigin", value.metadata.dataOrigin.packageName)
            .put("clientRecordId", value.metadata.clientRecordId ?: JSONObject.NULL).put("clientRecordVersion", value.metadata.clientRecordVersion)
        val interval = when (value) {
            is StepsRecord -> value.startTime to value.endTime
            is DistanceRecord -> value.startTime to value.endTime
            is ActiveCaloriesBurnedRecord -> value.startTime to value.endTime
            is TotalCaloriesBurnedRecord -> value.startTime to value.endTime
            is HeartRateRecord -> value.startTime to value.endTime
            is SleepSessionRecord -> value.startTime to value.endTime
            is ExerciseSessionRecord -> value.startTime to value.endTime
            is HydrationRecord -> value.startTime to value.endTime
            is FloorsClimbedRecord -> value.startTime to value.endTime
            else -> null
        }
        interval?.let { out.put("startTimeMilliseconds",it.first.toEpochMilli()).put("endTimeMilliseconds",it.second.toEpochMilli()) }
        val time = when (value) {
            is WeightRecord -> value.time
            is HeightRecord -> value.time
            is BodyFatRecord -> value.time
            is BodyTemperatureRecord -> value.time
            is BloodPressureRecord -> value.time
            is BloodGlucoseRecord -> value.time
            is OxygenSaturationRecord -> value.time
            else -> null
        }
        time?.let { out.put("timeMilliseconds",it.toEpochMilli()) }
        val fields = when (value) {
            is StepsRecord -> JSONObject().put("count",value.count)
            is DistanceRecord -> JSONObject().put("meters",value.distance.inMeters)
            is ActiveCaloriesBurnedRecord -> JSONObject().put("kilocalories",value.energy.inKilocalories)
            is TotalCaloriesBurnedRecord -> JSONObject().put("kilocalories",value.energy.inKilocalories)
            is HeartRateRecord -> JSONObject().put("samples",JSONArray(value.samples.map { JSONObject().put("timeMilliseconds",it.time.toEpochMilli()).put("beatsPerMinute",it.beatsPerMinute) }))
            is SleepSessionRecord -> JSONObject().put("title",value.title ?: JSONObject.NULL).put("notes",value.notes ?: JSONObject.NULL)
                .put("stages",JSONArray(value.stages.map { JSONObject().put("startTimeMilliseconds",it.startTime.toEpochMilli()).put("endTimeMilliseconds",it.endTime.toEpochMilli()).put("stage",it.stage) }))
            is ExerciseSessionRecord -> JSONObject().put("exerciseType",value.exerciseType).put("title",value.title ?: JSONObject.NULL).put("notes",value.notes ?: JSONObject.NULL)
            is WeightRecord -> JSONObject().put("kilograms",value.weight.inKilograms)
            is HeightRecord -> JSONObject().put("meters",value.height.inMeters)
            is HydrationRecord -> JSONObject().put("liters",value.volume.inLiters)
            is BodyFatRecord -> JSONObject().put("percent",value.percentage.value)
            is BodyTemperatureRecord -> JSONObject().put("celsius",value.temperature.inCelsius)
            is BloodPressureRecord -> JSONObject().put("systolicMmHg",value.systolic.inMillimetersOfMercury).put("diastolicMmHg",value.diastolic.inMillimetersOfMercury)
            is BloodGlucoseRecord -> JSONObject().put("millimolesPerLiter",value.level.inMillimolesPerLiter)
            is OxygenSaturationRecord -> JSONObject().put("percent",value.percentage.value)
            is FloorsClimbedRecord -> JSONObject().put("floors",value.floors)
            else -> JSONObject()
        }
        return out.put("values",fields)
    }
    fun requestAccess(context: Context, request: JSONObject): JSONObject {
        val permissions = permissions(request)
        val intent = Intent(context, DikcizHealthAccessActivity::class.java).putStringArrayListExtra("permissions",ArrayList(permissions))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        var posted = false
        if (context is android.app.Activity && context.hasWindowFocus()) context.startActivity(intent)
        else {
            val manager=context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel("dikciz-health-access","Health data access",NotificationManager.IMPORTANCE_DEFAULT))
            if (manager.areNotificationsEnabled()) {
                val action=PendingIntent.getActivity(context,NOTIFICATION,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                manager.notify(NOTIFICATION,Notification.Builder(context,"dikciz-health-access").setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("Review Dikciz health access").setContentText("Tap to review the requested record permissions").setContentIntent(action).setAutoCancel(true).build())
                posted=true
            }
        }
        return JSONObject().put("state","user_action_required").put("requestedPermissions",JSONArray(permissions.toList()))
            .put("notificationPosted",posted).put("retryRequired",true).put("instructions","Review Health Connect access on your phone, then retry. No operation ran.")
    }
    private fun text(max: Int)=JSONObject().put("type","string").put("minLength",1).put("maxLength",max)
    private fun integer(min: Long,max: Long)=JSONObject().put("type","integer").put("minimum",min).put("maximum",max)
    private val identity=mapOf("kind" to text(32).put("enum",JSONArray(kinds.keys.toList())))
    private val range=mapOf("startTimeMilliseconds" to integer(0,Long.MAX_VALUE),"endTimeMilliseconds" to integer(1,Long.MAX_VALUE))
    private val row=JSONObject().put("type","object").put("properties",JSONObject(mapOf(
        "clientRecordId" to text(80),"clientRecordVersion" to integer(0,Long.MAX_VALUE),
        "timeMilliseconds" to integer(0,Long.MAX_VALUE),"startTimeMilliseconds" to integer(0,Long.MAX_VALUE),"endTimeMilliseconds" to integer(1,Long.MAX_VALUE),
        "values" to JSONObject().put("type","object")))).put("required",JSONArray(listOf("clientRecordId","values"))).put("additionalProperties",false)
    val DEFINITIONS=listOf(
        DikcizNativeCommands.Definition(CATALOGUE,"dikciz_health_records_catalogue","Discover 16 supported Health Connect record kinds, canonical value units, aggregate metric names, provider availability and exact read/write grants.",emptyMap()),
        DikcizNativeCommands.Definition(READ,"dikciz_health_records_read","Read one bounded page of a Health Connect kind in an epoch-millisecond range of at most 366 days. Follow nextPageToken. Android history/background access restrictions apply. Missing grants return a targeted healthPermissionsRequest recovery.",identity+range+mapOf("limit" to integer(1,200).put("default",50),"pageToken" to text(4096)),setOf("kind","startTimeMilliseconds","endTimeMilliseconds")),
        DikcizNativeCommands.Definition(AGGREGATE,"dikciz_health_records_aggregate","Read the provider's supported aggregate metrics, avoiding duplicate cumulative raw-source counting. Units match the catalogue. Null means no contributing records.",identity+range,setOf("kind","startTimeMilliseconds","endTimeMilliseconds")),
        DikcizNativeCommands.Definition(WRITE,"dikciz_health_records_write","Insert/upsert 1..50 manually entered records of one kind. Every record requires stable clientRecordId, values, and either timeMilliseconds or start/endTimeMilliseconds according to the catalogue. Optional nonnegative clientRecordVersion controls replacement. UTC offsets are used. Only this app's origin is written. Heart-rate samples use timeMilliseconds/beatsPerMinute; sleep stages use start/endTimeMilliseconds/stage.",identity+mapOf("records" to JSONObject().put("type","array").put("minItems",1).put("maxItems",50).put("items",row)),setOf("kind","records"),false),
        DikcizNativeCommands.Definition(DELETE,"dikciz_health_records_delete","Delete only the exact selected record IDs that Health Connect allows this app to delete. Never deletes all history. Requires confirm=true.",identity+mapOf("recordIds" to JSONObject().put("type","array").put("minItems",1).put("maxItems",50).put("uniqueItems",true).put("items",text(256)),"confirm" to JSONObject().put("type","boolean")),setOf("kind","recordIds","confirm"),false),
        DikcizNativeCommands.Definition(ACCESS,"dikciz_health_permissions_request","Ask the owner to review exact Health Connect record permissions. access is read/write/readWrite. Optional history/background access is separate. No silent grants or automatic retry.",mapOf("kinds" to JSONObject().put("type","array").put("minItems",1).put("maxItems",16).put("uniqueItems",true).put("items",identity.getValue("kind")),
            "access" to text(16).put("enum",JSONArray(listOf("read","write","readWrite"))),"includeBackground" to JSONObject().put("type","boolean"),"includeHistory" to JSONObject().put("type","boolean")),setOf("kinds","access"),false),
    )
    val TYPES=DEFINITIONS.map { it.type }.toSet()
}
