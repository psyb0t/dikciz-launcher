package org.fossify.home.dikciz

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.FutureTask
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject

/** Persisted results survive disconnects; interrupted operations are never replayed. */
internal object DikcizCommandJobs {
    const val TYPE_START = "jobStart"
    const val TYPE_STATUS = "jobStatus"
    const val TYPE_LIST = "jobsList"
    const val TYPE_CANCEL = "jobCancel"
    private const val KEY_JOB = "jobId"
    private const val KEY_COMMAND = "command"
    private const val KEY_RETENTION = "retentionHours"
    private const val KEY_STATE = "state"
    private const val KEY_CREATED = "createdAtMilliseconds"
    private const val KEY_EXPIRES = "expiresAtMilliseconds"
    private const val KEY_FINGERPRINT = "fingerprint"
    private const val KEY_CANCEL = "cancellationRequested"
    private const val KEY_REQUEST = "idempotencyKey"
    private const val STATE_QUEUED = "queued"
    private const val STATE_RUNNING = "running"
    private const val STATE_COMPLETED = "completed"
    private const val STATE_FAILED = "failed"
    private const val STATE_CANCELLED = "cancelled"
    private const val STATE_INTERRUPTED = "interrupted"
    private const val MAXIMUM_RECORDS = 256
    private const val HOUR_MILLISECONDS = 3_600_000L
    private const val MAXIMUM_RECORD_BYTES = 1_048_576
    private val keyPattern = Regex("[A-Za-z0-9][A-Za-z0-9_.-]{0,79}")
    private val jobPattern = Regex("[a-f0-9-]{36}")
    private val records = LinkedHashMap<String, JSONObject>()
    private val tasks = ConcurrentHashMap<String, FutureTask<Unit>>()
    private val executor = ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS, LinkedBlockingQueue(MAXIMUM_RECORDS)) { task ->
        Thread(task, "dikciz-command-job").apply { isDaemon = true }
    }
    private var directory: File? = null

    val DEFINITIONS = listOf(
        DikcizNativeCommands.Definition(TYPE_START, "dikciz_job_start",
            "Persist and run one ordinary Dikciz command asynchronously. Required idempotencyKey prevents duplicate submissions. Results survive disconnect and process death; interrupted work is never replayed automatically. Poll jobStatus for the original complete response including permission recovery. Jobs cannot start other jobs or change remote authentication.",
            mapOf(KEY_COMMAND to JSONObject().put("type", "object").put("description", "Full ordinary command with type and its fields, optionally autoRequestPermissions."),
                KEY_REQUEST to text("Unique caller key; changed content with the same key conflicts.", 80),
                KEY_RETENTION to integer(1, 720, 168)), setOf(KEY_COMMAND, KEY_REQUEST), readOnly = false),
        DikcizNativeCommands.Definition(TYPE_STATUS, "dikciz_job_status", "Read persisted job state and its full original command response. interrupted means outcome unknown after process loss. No automatic retry.", mapOf(KEY_JOB to text("Job ID from jobStart.", 36)), setOf(KEY_JOB)),
        DikcizNativeCommands.Definition(TYPE_LIST, "dikciz_jobs_list", "List retained job metadata newest first. Includes queued/running/completed/failed/cancelled/interrupted state and cancellation requests; results are read separately with jobStatus.", mapOf("offset" to integer(0, DikcizNativeCommands.MAXIMUM_OFFSET, 0), "limit" to integer(1, DikcizNativeCommands.MAXIMUM_LIMIT, DikcizNativeCommands.DEFAULT_LIMIT))),
        DikcizNativeCommands.Definition(TYPE_CANCEL, "dikciz_job_cancel", "Cancel queued work or request interruption of running work. Cancellation never rolls back side effects or guarantees that Android stopped an operation. Read jobStatus for the final outcome.", mapOf(KEY_JOB to text("Job ID from jobStart.", 36)), setOf(KEY_JOB), readOnly = false),
    )
    val TYPES = setOf(TYPE_START, TYPE_STATUS, TYPE_LIST, TYPE_CANCEL)

    @Synchronized
    fun execute(context: Context, logger: DikcizLogger, type: String, request: JSONObject, autoRequestPermissions: Boolean): JSONObject {
        initialize(context, logger)
        prune()
        return when (type) {
            TYPE_START -> start(request, logger, autoRequestPermissions)
            TYPE_LIST -> {
                val items = records.values.sortedByDescending { it.getLong(KEY_CREATED) }.map(::metadata)
                DikcizNativeCommands.page(items, request).put("total", items.size)
            }
            TYPE_STATUS -> JSONObject(record(request).toString()).apply { remove(KEY_FINGERPRINT); remove(KEY_REQUEST) }
            TYPE_CANCEL -> cancel(record(request))
            else -> DikcizNativeCommands.invalid()
        }
    }

    private fun initialize(context: Context, logger: DikcizLogger) {
        if (directory != null) return
        val root = File(context.filesDir, "command-jobs")
        if (!root.isDirectory && !root.mkdirs()) DikcizNativeCommands.failure("job_storage_failed", "Cannot create command job storage")
        val loaded = LinkedHashMap<String, JSONObject>()
        try {
            val files = root.listFiles()
                ?: DikcizNativeCommands.failure("job_storage_failed", "Cannot read command job storage")
            val candidates = files.filter { it.extension == "json" && jobPattern.matches(it.nameWithoutExtension) }
            if (candidates.size > MAXIMUM_RECORDS) DikcizNativeCommands.failure("job_storage_invalid", "Stored job capacity exceeded")
            candidates.forEach { file ->
                if (file.length() > MAXIMUM_RECORD_BYTES) DikcizNativeCommands.failure("job_storage_invalid", "A stored job exceeds its size limit")
                val record = JSONObject(AtomicFile(file).readFully().toString(Charsets.UTF_8))
                val id = record.getString(KEY_JOB)
                if (id != file.nameWithoutExtension ||
                    record.getString(KEY_STATE) !in setOf(STATE_QUEUED, STATE_RUNNING, STATE_COMPLETED, STATE_FAILED, STATE_CANCELLED, STATE_INTERRUPTED) ||
                    !keyPattern.matches(record.getString(KEY_REQUEST)) ||
                    !Regex("[a-f0-9]{64}").matches(record.getString(KEY_FINGERPRINT)) ||
                    record.getLong(KEY_CREATED) < 0 || record.getLong(KEY_EXPIRES) < record.getLong(KEY_CREATED)) {
                    DikcizNativeCommands.failure("job_storage_invalid", "A stored job has invalid metadata")
                }
                record.getBoolean(KEY_CANCEL)
                if (record.getString(KEY_STATE) in setOf(STATE_RUNNING, STATE_QUEUED)) {
                    record.put(KEY_STATE, STATE_INTERRUPTED).put("outcomeKnown", false).put("finishedAtMilliseconds", System.currentTimeMillis())
                    persist(record, root)
                    logger.warn("command_job_interrupted", mapOf("job_id" to id))
                }
                loaded[id] = record
            }
        } catch (exception: Exception) {
            if (exception is DikcizAutomationRequestException) throw exception
            throw DikcizAutomationRequestException("job_storage_invalid", "Cannot load retained command jobs").apply { initCause(exception) }
        }
        records.putAll(loaded)
        directory = root
    }

    private fun start(request: JSONObject, logger: DikcizLogger, autoRequestPermissions: Boolean): JSONObject {
        val key = request.getString(KEY_REQUEST)
        val command = JSONObject(request.getJSONObject(KEY_COMMAND).toString()).apply { remove("requestId") }
        if (autoRequestPermissions && !command.has(DikcizPermissionRequests.KEY_AUTO_REQUEST)) command.put(DikcizPermissionRequests.KEY_AUTO_REQUEST, true)
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical(command).toString().toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        records.values.firstOrNull { it.getString(KEY_REQUEST) == key }?.let { existing ->
            if (existing.getString(KEY_FINGERPRINT) != digest) DikcizNativeCommands.failure("job_idempotency_conflict", "This idempotency key was used for a different command")
            return metadata(existing).put("deduplicated", true)
        }
        if (records.size >= MAXIMUM_RECORDS) DikcizNativeCommands.failure("job_capacity", "Retained job capacity reached; wait for retention expiry")
        val now = System.currentTimeMillis()
        val record = JSONObject().put(KEY_JOB, UUID.randomUUID().toString()).put(KEY_REQUEST, key).put(KEY_FINGERPRINT, digest)
            .put(KEY_STATE, STATE_QUEUED).put(KEY_CREATED, now).put(KEY_EXPIRES, now + request.optLong(KEY_RETENTION, 168) * HOUR_MILLISECONDS)
            .put(KEY_CANCEL, false).put("commandType", command.getString("type")).put("outcomeKnown", false)
        val id = record.getString(KEY_JOB)
        persist(record)
        records[id] = record
        val task = FutureTask<Unit> {
            try {
                synchronized(this) {
                    if (record.getString(KEY_STATE) == STATE_CANCELLED) return@FutureTask Unit
                    record.put(KEY_STATE, STATE_RUNNING).put("startedAtMilliseconds", System.currentTimeMillis())
                    persist(record)
                }
                logger.info("command_job_started", mapOf("job_id" to id, "command_type" to command.getString("type")))
                val reply = DikcizLocalCommands.execute("job:" + id, command)
                synchronized(this) {
                    val response = reply.response
                    record.put("response", response).put("outcomeKnown", true)
                        .put(KEY_STATE, if (response.optString("type") == "result") STATE_COMPLETED else STATE_FAILED)
                    finish(record)
                }
                reply.delivered()
                logger.info("command_job_finished", mapOf("job_id" to id, "state" to record.getString(KEY_STATE)))
            } catch (exception: Exception) {
                synchronized(this) {
                    record.remove("response")
                    record.put(KEY_STATE, STATE_FAILED).put("outcomeKnown", false).put("error", "job_execution_failed")
                    finish(record)
                }
                logger.error("command_job_failed", mapOf("job_id" to id, "error_class" to exception.javaClass.simpleName))
            } finally {
                tasks.remove(id)
                Thread.interrupted()
            }
        }
        tasks[id] = task
        executor.execute(task)
        return metadata(record).put("deduplicated", false)
    }

    private fun cancel(record: JSONObject): JSONObject {
        val id = record.getString(KEY_JOB)
        if (record.getString(KEY_STATE) == STATE_QUEUED) {
            record.put(KEY_CANCEL, true).put(KEY_STATE, STATE_CANCELLED).put("outcomeKnown", true)
            finish(record)
            tasks.remove(id)?.cancel(false)
        } else if (record.getString(KEY_STATE) == STATE_RUNNING) {
            record.put(KEY_CANCEL, true)
            persist(record)
            tasks[id]?.cancel(true)
        }
        return metadata(record)
    }

    private fun finish(record: JSONObject) {
        record.put("finishedAtMilliseconds", System.currentTimeMillis())
        persist(record)
    }

    private fun persist(record: JSONObject, root: File = requireNotNull(directory)) {
        val bytes = record.toString().toByteArray(Charsets.UTF_8)
        if (bytes.size > MAXIMUM_RECORD_BYTES) DikcizNativeCommands.failure("job_result_too_large", "Job result exceeds retained record capacity")
        val file = AtomicFile(File(root, record.getString(KEY_JOB) + ".json"))
        val stream = file.startWrite()
        try {
            stream.write(bytes)
            file.finishWrite(stream)
        } catch (exception: Exception) {
            file.failWrite(stream)
            throw exception
        }
    }

    private fun record(request: JSONObject): JSONObject = records[request.getString(KEY_JOB)]
        ?: DikcizNativeCommands.failure("job_not_found", "No retained command job with that ID")

    private fun metadata(record: JSONObject) = JSONObject(record.toString()).apply {
        remove("response"); remove(KEY_FINGERPRINT); remove(KEY_REQUEST)
    }

    private fun prune() {
        val now = System.currentTimeMillis()
        val expired = records.filterValues { it.getLong(KEY_EXPIRES) <= now && it.getString(KEY_STATE) !in setOf(STATE_QUEUED, STATE_RUNNING) }.keys
        expired.forEach { id ->
            AtomicFile(File(directory, id + ".json")).delete()
            records.remove(id)
        }
    }

    private fun canonical(value: Any?): Any? = when (value) {
        is JSONObject -> JSONObject().apply { value.keys().asSequence().sorted().forEach { put(it, canonical(value.get(it))) } }
        is JSONArray -> JSONArray((0 until value.length()).map { canonical(value.get(it)) })
        else -> value
    }

    fun validate(type: String, request: JSONObject) {
        if (type == TYPE_START) {
            if (!keyPattern.matches(request.getString(KEY_REQUEST))) DikcizNativeCommands.invalid("Invalid job idempotency key")
            val command = request.getJSONObject(KEY_COMMAND)
            val commandType = command.opt("type")
            if (commandType !is String || commandType !in DikcizAutomationControlPlane.COMMAND_KEYS || commandType in TYPES || commandType.startsWith("remoteAuth")) {
                DikcizNativeCommands.invalid("Unsupported nested job command")
            }
        }
        if (type in setOf(TYPE_STATUS, TYPE_CANCEL) && !jobPattern.matches(request.getString(KEY_JOB))) DikcizNativeCommands.invalid("Invalid job ID")
    }

    private fun text(description: String, maximum: Int) = JSONObject().put("type", "string").put("description", description).put("maxLength", maximum)
    private fun integer(minimum: Int, maximum: Int, default: Int) = JSONObject().put("type", "integer").put("minimum", minimum).put("maximum", maximum).put("default", default)
}
