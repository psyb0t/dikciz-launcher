package org.fossify.home.dikciz

import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import org.json.JSONArray
import org.json.JSONObject

internal data class DikcizScriptLogSnapshot(
    val records: List<DikcizScriptLogRecord>,
    val isTruncated: Boolean,
)

internal data class DikcizScriptLogRecord(
    val timestamp: String,
    val level: String,
    val event: String,
    val scriptID: String,
    val scriptKind: String?,
    val policyID: String?,
    val eventType: String?,
    val actionType: String?,
    val outcome: String?,
    val reason: String?,
    val diagnostic: String?,
)

internal const val DIKCIZ_MAXIMUM_SCRIPT_LOG_RECORDS = 100

internal class DikcizLogger(
    private val component: String,
    private val logDirectory: File,
) {
    private var configuration = DEFAULT_CONFIGURATION
    private var lastCleanupDate: LocalDate? = null

    @Synchronized
    fun configure(logging: DikcizLoggingConfiguration) {
        if (configuration == logging) {
            return
        }
        configuration = logging
        lastCleanupDate = null
        info(
            EVENT_LOGGING_CONFIGURED,
            mapOf(
                FIELD_LEVEL to logging.level.persistedValue,
                FIELD_RETENTION_DAYS to logging.retentionDays,
                FIELD_MAXIMUM_TOTAL_BYTES to logging.maxTotalBytes,
                FIELD_NOTIFY_ON_SCRIPT_ERROR to logging.notifyOnScriptError,
                FIELD_SCRIPT_ERROR_NOTIFICATION_MINIMUM_INTERVAL_MILLISECONDS to
                    logging.scriptErrorNotificationMinimumIntervalMilliseconds,
            ),
        )
    }

    fun debug(event: String, fields: Map<String, Any> = emptyMap()) {
        write(DikcizLogLevel.Debug, event, fields)
    }

    fun info(event: String, fields: Map<String, Any> = emptyMap()) {
        write(DikcizLogLevel.Info, event, fields)
    }

    fun warn(event: String, fields: Map<String, Any> = emptyMap()) {
        write(DikcizLogLevel.Warn, event, fields)
    }

    fun error(event: String, fields: Map<String, Any> = emptyMap()) {
        write(DikcizLogLevel.Error, event, fields)
    }

    @Synchronized
    fun recentScriptLogSnapshot(maximumRecords: Int): DikcizScriptLogSnapshot {
        val recordLimit = maximumRecords.coerceIn(
            MINIMUM_SCRIPT_LOG_RECORDS,
            DIKCIZ_MAXIMUM_SCRIPT_LOG_RECORDS,
        )
        val records = mutableListOf<DikcizScriptLogRecord>()
        val logFiles = logFiles().sortedByDescending(DatedLogFile::date)
        var isTruncated = logFiles.size > MAXIMUM_SCRIPT_LOG_FILES
        for (logFile in logFiles.take(MAXIMUM_SCRIPT_LOG_FILES)) {
            if (records.size == recordLimit) {
                isTruncated = true
                break
            }
            if (logFile.file.length() > MAXIMUM_SCRIPT_LOG_READ_BYTES_PER_FILE) {
                isTruncated = true
            }
            readRecentScriptLogRecords(logFile.file).forEach { record ->
                if (records.size == recordLimit) {
                    isTruncated = true
                    return@forEach
                }
                records.add(record)
            }
        }
        return DikcizScriptLogSnapshot(records, isTruncated)
    }

    @Synchronized
    private fun write(level: DikcizLogLevel, event: String, fields: Map<String, Any>) {
        if (level.priority < configuration.level.priority) {
            return
        }
        val record = JSONObject()
            .put(FIELD_TIMESTAMP, Instant.now().toString())
            .put(FIELD_LEVEL, level.persistedValue)
            .put(FIELD_COMPONENT, component)
            .put(FIELD_EVENT, event)
        fields.forEach { (key, value) ->
            record.put(key, redactValue(key, value))
        }
        val serializedRecord = "$record$LINE_SEPARATOR"
        writeToLogcat(level, serializedRecord)
        writeToFile(serializedRecord)
    }

    private fun writeToLogcat(level: DikcizLogLevel, serializedRecord: String) {
        when (level) {
            DikcizLogLevel.Debug -> Log.d(component, serializedRecord)
            DikcizLogLevel.Info -> Log.i(component, serializedRecord)
            DikcizLogLevel.Warn -> Log.w(component, serializedRecord)
            DikcizLogLevel.Error -> Log.e(component, serializedRecord)
        }
    }

    private fun readRecentScriptLogRecords(file: File): List<DikcizScriptLogRecord> {
        val canonicalLogDirectory = try {
            logDirectory.canonicalFile
        } catch (_: Exception) {
            return emptyList()
        }
        val canonicalFile = try {
            file.canonicalFile
        } catch (_: Exception) {
            return emptyList()
        }
        if (canonicalFile.parentFile != canonicalLogDirectory || !canonicalFile.isFile) {
            return emptyList()
        }
        val fileLength = file.length()
        if (fileLength <= EMPTY_FILE_LENGTH) {
            return emptyList()
        }
        val byteCount = minOf(fileLength, MAXIMUM_SCRIPT_LOG_READ_BYTES_PER_FILE).toInt()
        val startOffset = fileLength - byteCount
        val content = try {
            FileInputStream(file).use { input ->
                input.channel.position(startOffset)
                val bytes = ByteArray(byteCount)
                val readCount = input.read(bytes)
                if (readCount <= EMPTY_FILE_LENGTH) {
                    return emptyList()
                }
                String(bytes, FIRST_BYTE_INDEX, readCount, Charsets.UTF_8)
            }
        } catch (_: Exception) {
            return emptyList()
        }
        val completeLines = if (startOffset > EMPTY_FILE_LENGTH) {
            content.substringAfter(LINE_SEPARATOR, EMPTY_STRING)
        } else {
            content
        }
        return completeLines.lineSequence()
            .filter { line -> line.length <= MAXIMUM_SCRIPT_LOG_LINE_CHARACTERS }
            .mapNotNull(::parseScriptLogRecord)
            .toList()
            .takeLast(DIKCIZ_MAXIMUM_SCRIPT_LOG_RECORDS)
            .asReversed()
    }

    private fun parseScriptLogRecord(line: String): DikcizScriptLogRecord? {
        val value = try {
            JSONObject(line)
        } catch (_: Exception) {
            return null
        }
        val timestamp = value.safeString(FIELD_TIMESTAMP, MAXIMUM_TIMESTAMP_CHARACTERS) ?: return null
        val level = value.safeString(FIELD_LEVEL, MAXIMUM_LEVEL_CHARACTERS) ?: return null
        val event = value.safeString(FIELD_EVENT, MAXIMUM_EVENT_CHARACTERS) ?: return null
        val scriptID = value.safeString(FIELD_SCRIPT_ID, MAXIMUM_SCRIPT_ID_CHARACTERS)
            ?: value.safeString(FIELD_COMPONENT, MAXIMUM_SCRIPT_ID_CHARACTERS)
            ?: return null
        return DikcizScriptLogRecord(
            timestamp = timestamp,
            level = level,
            event = event,
            scriptID = scriptID,
            scriptKind = value.safeString(FIELD_SCRIPT_KIND, MAXIMUM_FIELD_VALUE_CHARACTERS),
            policyID = value.safeString(FIELD_POLICY_ID, MAXIMUM_FIELD_VALUE_CHARACTERS),
            eventType = value.safeString(FIELD_EVENT_TYPE, MAXIMUM_FIELD_VALUE_CHARACTERS),
            actionType = value.safeString(FIELD_ACTION_TYPE, MAXIMUM_FIELD_VALUE_CHARACTERS),
            outcome = value.safeString(FIELD_OUTCOME, MAXIMUM_FIELD_VALUE_CHARACTERS),
            reason = value.safeString(FIELD_REASON, MAXIMUM_FIELD_VALUE_CHARACTERS),
            diagnostic = value.safeString(FIELD_DIAGNOSTIC, MAXIMUM_FIELD_VALUE_CHARACTERS),
        )
    }

    private fun JSONObject.safeString(key: String, maximumLength: Int): String? {
        val value = opt(key) as? String ?: return null
        val sanitized = value.filter { character -> character >= MINIMUM_DISPLAY_CHARACTER && character != DELETE_CHARACTER }
            .take(maximumLength)
        return sanitized.ifBlank { null }
    }

    private fun writeToFile(serializedRecord: String) {
        val today = LocalDate.now(ZoneOffset.UTC)
        try {
            if (!logDirectory.exists() && !logDirectory.mkdirs()) {
                Log.e(component, FILE_WRITE_FAILURE_MESSAGE)
                return
            }
            cleanupIfNeeded(today)
            FileOutputStream(logFile(today), APPEND_MODE).use { output ->
                output.write(serializedRecord.toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
        } catch (exception: Exception) {
            Log.e(component, FILE_WRITE_FAILURE_MESSAGE, exception)
        }
    }

    private fun cleanupIfNeeded(today: LocalDate) {
        if (lastCleanupDate == today) {
            return
        }
        val oldestRetainedDate = today.minusDays((configuration.retentionDays - 1).toLong())
        val retainedFiles = logFiles().filter { it.date >= oldestRetainedDate }
        logFiles()
            .filter { it.date < oldestRetainedDate }
            .forEach { deleteLogFile(it.file) }

        var totalBytes = retainedFiles.sumOf { it.file.length() }
        retainedFiles
            .sortedBy { it.date }
            .filter { it.date != today }
            .forEach { logFile ->
                if (totalBytes <= configuration.maxTotalBytes) {
                    return@forEach
                }
                val fileBytes = logFile.file.length()
                if (deleteLogFile(logFile.file)) {
                    totalBytes -= fileBytes
                }
            }
        lastCleanupDate = today
    }

    private fun logFiles(): List<DatedLogFile> {
        return logDirectory.listFiles()
            ?.mapNotNull { file ->
                if (!file.isFile) {
                    return@mapNotNull null
                }
                parseLogDate(file.name)?.let { date -> DatedLogFile(file, date) }
            }
            ?: emptyList()
    }

    private fun parseLogDate(fileName: String): LocalDate? {
        val match = LOG_FILE_NAME_PATTERN.matchEntire(fileName) ?: return null
        return try {
            LocalDate.parse(match.groupValues[1], DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private fun redactValue(key: String, value: Any?): Any {
        if (isSensitiveKey(key)) {
            return REDACTED_VALUE
        }
        return when (value) {
            null -> JSONObject.NULL
            is String -> redactText(value)
            is JSONObject -> redactObject(value)
            is Map<*, *> -> redactMap(value)
            is Iterable<*> -> redactIterable(value)
            is Array<*> -> redactIterable(value.asIterable())
            else -> value
        }
    }

    private fun redactText(value: String): String {
        return value
            .replace(SENSITIVE_ASSIGNMENT_PATTERN) { match ->
                "${match.groupValues[FIRST_CAPTURE_GROUP]}${match.groupValues[SECOND_CAPTURE_GROUP]}$REDACTED_VALUE"
            }
            .replace(BEARER_CREDENTIAL_PATTERN, "Bearer $REDACTED_VALUE")
    }

    private fun redactObject(value: JSONObject): JSONObject {
        val redacted = JSONObject()
        val keys = value.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            redacted.put(key, redactValue(key, value.opt(key)))
        }
        return redacted
    }

    private fun redactMap(value: Map<*, *>): JSONObject {
        return JSONObject().apply {
            value.forEach { (key, nestedValue) ->
                val nestedKey = key?.toString() ?: NULL_FIELD_KEY
                put(nestedKey, redactValue(nestedKey, nestedValue))
            }
        }
    }

    private fun redactIterable(value: Iterable<*>): JSONArray {
        return JSONArray().apply {
            value.forEach { nestedValue -> put(redactValue(EMPTY_FIELD_KEY, nestedValue)) }
        }
    }

    private fun isSensitiveKey(key: String): Boolean {
        return REDACTED_FIELD_NAME_FRAGMENTS.any { fragment ->
            key.contains(fragment, ignoreCase = true)
        }
    }

    private fun deleteLogFile(file: File): Boolean {
        if (file.delete()) {
            return true
        }
        Log.w(component, LOG_FILE_DELETE_FAILURE_MESSAGE)
        return false
    }

    private fun logFile(date: LocalDate): File {
        return File(logDirectory, "$date$LOG_FILE_SUFFIX")
    }

    private data class DatedLogFile(
        val file: File,
        val date: LocalDate,
    )

    private companion object {
        val DEFAULT_CONFIGURATION = DikcizLoggingConfiguration(
            level = DikcizLogLevel.Info,
            retentionDays = DikcizLoggingConfiguration.DEFAULT_RETENTION_DAYS,
            maxTotalBytes = DikcizLoggingConfiguration.DEFAULT_MAXIMUM_TOTAL_BYTES,
        )
        val LOG_FILE_NAME_PATTERN = Regex("^(\\d{4}-\\d{2}-\\d{2})\\.log$")
        val REDACTED_FIELD_NAME_FRAGMENTS = setOf(
            "api_key",
            "authorization",
            "cookie",
            "password",
            "secret",
            "token",
        )
        val BEARER_CREDENTIAL_PATTERN = Regex("(?i)bearer\\s+[^\\s,;]+")
        val SENSITIVE_ASSIGNMENT_PATTERN = Regex(
            "(?i)\\b(api[_-]?key|authorization|cookie|password|secret|token)\\b(\\s*[:=]\\s*)[^\\s,;]+",
        )
        const val APPEND_MODE = true
        const val DELETE_CHARACTER = '\u007f'
        const val EMPTY_FILE_LENGTH = 0L
        const val EMPTY_STRING = ""
        const val EVENT_LOGGING_CONFIGURED = "logging_configured"
        const val FIELD_ACTION_TYPE = "action_type"
        const val EMPTY_FIELD_KEY = ""
        const val FIELD_COMPONENT = "component"
        const val FIELD_DIAGNOSTIC = "diagnostic"
        const val FIELD_EVENT = "event"
        const val FIELD_EVENT_TYPE = "event_type"
        const val FIELD_LEVEL = "level"
        const val FIELD_MAXIMUM_TOTAL_BYTES = "max_total_bytes"
        const val FIELD_NOTIFY_ON_SCRIPT_ERROR = "notify_on_script_error"
        const val FIELD_OUTCOME = "outcome"
        const val FIELD_POLICY_ID = "policy_id"
        const val FIELD_REASON = "reason"
        const val FIELD_RETENTION_DAYS = "retention_days"
        const val FIELD_SCRIPT_ID = "script_id"
        const val FIELD_SCRIPT_ERROR_NOTIFICATION_MINIMUM_INTERVAL_MILLISECONDS =
            "script_error_notification_minimum_interval_milliseconds"
        const val FIELD_SCRIPT_KIND = "script_kind"
        const val FIELD_TIMESTAMP = "timestamp"
        const val FIRST_BYTE_INDEX = 0
        const val FILE_WRITE_FAILURE_MESSAGE = "persistent log write failed"
        const val FIRST_CAPTURE_GROUP = 1
        const val LINE_SEPARATOR = "\n"
        const val LOG_FILE_DELETE_FAILURE_MESSAGE = "persistent log cleanup failed"
        const val LOG_FILE_SUFFIX = ".log"
        const val MAXIMUM_EVENT_CHARACTERS = 96
        const val MAXIMUM_FIELD_VALUE_CHARACTERS = 256
        const val MAXIMUM_LEVEL_CHARACTERS = 16
        const val MAXIMUM_SCRIPT_ID_CHARACTERS = 256
        const val MAXIMUM_SCRIPT_LOG_LINE_CHARACTERS = 8_192
        const val MAXIMUM_SCRIPT_LOG_FILES = 90
        const val MAXIMUM_SCRIPT_LOG_READ_BYTES_PER_FILE = 65_536L
        const val MAXIMUM_TIMESTAMP_CHARACTERS = 64
        const val MINIMUM_DISPLAY_CHARACTER = ' '
        const val MINIMUM_SCRIPT_LOG_RECORDS = 1
        const val NULL_FIELD_KEY = "null"
        const val REDACTED_VALUE = "[REDACTED]"
        const val SECOND_CAPTURE_GROUP = 2
    }
}
