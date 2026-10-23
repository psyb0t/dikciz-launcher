package org.fossify.home.dikciz

import android.content.Context
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.file.AccessDeniedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.Base64
import org.json.JSONArray
import org.json.JSONObject

internal class DikcizDeviceFiles(
    context: Context,
    private val logger: DikcizLogger,
) {
    private val installer = DikcizApkInstaller(context.applicationContext, logger)
    private val writeLock = Any()

    init {
        // Android provisions app-specific external roots through Context, not mkdir.
        context.getExternalFilesDir(null)
    }

    fun execute(command: DikcizAutomationCommand): JSONObject {
        try {
            DikcizFileCommands.validate(command.type, command.request, true)
            return when (command.type) {
                DikcizFileCommands.TYPE_FILE_LIST -> list(command.request)
                DikcizFileCommands.TYPE_FILE_READ -> read(command.request)
                DikcizFileCommands.TYPE_FILE_WRITE -> synchronized(writeLock) { write(command.request) }
                DikcizFileCommands.TYPE_FILE_MKDIR -> synchronized(writeLock) { mkdir(command.request) }
                DikcizFileCommands.TYPE_APK_INSTALL -> installer.install(command.request)
                DikcizFileCommands.TYPE_APK_INSTALL_STATUS -> installer.status(command.request)
                else -> throw DikcizAutomationRequestException(
                    DikcizAutomationControlPlane.ERROR_UNKNOWN_COMMAND,
                    DikcizAutomationControlPlane.MESSAGE_UNKNOWN_COMMAND,
                )
            }
        } catch (exception: SecurityException) {
            throw DikcizFileCommands.failure(DikcizFileCommands.ERROR_ACCESS_DENIED, exception)
        } catch (exception: AccessDeniedException) {
            throw DikcizFileCommands.failure(DikcizFileCommands.ERROR_ACCESS_DENIED, exception)
        } catch (exception: NoSuchFileException) {
            throw DikcizFileCommands.failure(DikcizFileCommands.ERROR_NOT_FOUND, exception)
        } catch (exception: FileAlreadyExistsException) {
            throw DikcizFileCommands.failure(DikcizFileCommands.ERROR_EXISTS, exception)
        } catch (exception: IOException) {
            throw DikcizFileCommands.failure(DikcizFileCommands.ERROR_IO, exception)
        }
    }

    private fun list(request: JSONObject): JSONObject {
        val directory = DikcizFileCommands.file(request)
        val attributes = Files.readAttributes(directory.toPath(), BasicFileAttributes::class.java)
        if (!attributes.isDirectory) {
            throw DikcizFileCommands.failure(DikcizFileCommands.ERROR_NOT_DIRECTORY)
        }
        val offset = request.optLong(DikcizFileCommands.KEY_OFFSET, 0L)
        val limit = request.optInt(DikcizFileCommands.KEY_LIMIT, DikcizFileCommands.DEFAULT_LIST_LIMIT)
        val items = JSONArray()
        var hasMore: Boolean
        Files.newDirectoryStream(directory.toPath()).use { entries ->
            val iterator = entries.iterator()
            var skipped = 0L
            while (skipped < offset && iterator.hasNext()) {
                iterator.next()
                skipped++
            }
            while (items.length() < limit && iterator.hasNext()) {
                val entry = iterator.next().toFile()
                try {
                    items.put(metadata(entry))
                } catch (exception: IOException) {
                    logger.warn(EVENT_ENTRY_UNAVAILABLE, mapOf(FIELD_ERROR_CLASS to exception.javaClass.simpleName))
                    items.put(
                        JSONObject()
                            .put(DikcizFileCommands.KEY_PATH, entry.absolutePath)
                            .put(KEY_NAME, entry.name)
                            .put(KEY_ERROR, DikcizFileCommands.ERROR_ACCESS_DENIED),
                    )
                }
            }
            hasMore = iterator.hasNext()
        }
        return JSONObject()
            .put(DikcizFileCommands.KEY_PATH, directory.path)
            .put(KEY_PARENT, directory.parent ?: JSONObject.NULL)
            .put(KEY_ITEMS, items)
            .put(DikcizFileCommands.KEY_OFFSET, offset)
            .put(DikcizFileCommands.KEY_LIMIT, limit)
            .put(KEY_HAS_MORE, hasMore)
    }

    private fun read(request: JSONObject): JSONObject {
        val file = DikcizFileCommands.file(request)
        requireRegularFile(file)
        val offset = request.optLong(DikcizFileCommands.KEY_OFFSET, 0L)
        val limit = request.optInt(DikcizFileCommands.KEY_LIMIT, DikcizFileCommands.DEFAULT_CHUNK_BYTES)
        val buffer = ByteArray(limit + 1)
        var count = 0
        RandomAccessFile(file, READ_MODE).use { input ->
            input.seek(offset)
            while (count < buffer.size) {
                val read = input.read(buffer, count, buffer.size - count)
                if (read < 0) break
                count += read
            }
        }
        val bytesRead = minOf(count, limit)
        return metadata(file)
            .put(DikcizFileCommands.KEY_OFFSET, offset)
            .put(DikcizFileCommands.KEY_LIMIT, limit)
            .put(KEY_BYTES_READ, bytesRead)
            .put(KEY_NEXT_OFFSET, offset + bytesRead)
            .put(KEY_EOF, count <= limit)
            .put(DikcizFileCommands.KEY_DATA_BASE64, Base64.getEncoder().encodeToString(buffer.copyOf(bytesRead)))
    }

    private fun write(request: JSONObject): JSONObject {
        val file = DikcizFileCommands.file(request)
        val offset = request.optLong(DikcizFileCommands.KEY_OFFSET, 0L)
        val overwrite = request.optBoolean(DikcizFileCommands.KEY_OVERWRITE, false)
        val data = try {
            Base64.getDecoder().decode(request.getString(DikcizFileCommands.KEY_DATA_BASE64))
        } catch (exception: IllegalArgumentException) {
            throw DikcizFileCommands.failure(DikcizFileCommands.ERROR_BASE64, exception)
        }
        if (data.size > DikcizFileCommands.MAXIMUM_CHUNK_BYTES) {
            throw DikcizFileCommands.failure(DikcizFileCommands.ERROR_CHUNK_SIZE)
        }
        val options = if (offset > 0L) {
            requireRegularFile(file)
            if (file.length() != offset) {
                throw DikcizFileCommands.failure(DikcizFileCommands.ERROR_OFFSET)
            }
            arrayOf(StandardOpenOption.WRITE, StandardOpenOption.APPEND)
        } else if (overwrite) {
            arrayOf(StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
        } else {
            arrayOf(StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW)
        }
        Files.newOutputStream(file.toPath(), *options).use { output -> output.write(data) }
        logger.info(EVENT_FILE_WRITTEN, mapOf(KEY_BYTES_WRITTEN to data.size))
        return metadata(file)
            .put(KEY_BYTES_WRITTEN, data.size)
            .put(KEY_NEXT_OFFSET, offset + data.size)
    }

    private fun mkdir(request: JSONObject): JSONObject {
        val file = DikcizFileCommands.file(request)
        if (request.optBoolean(DikcizFileCommands.KEY_PARENTS, false)) {
            Files.createDirectories(file.toPath())
        } else if (!file.isDirectory) {
            Files.createDirectory(file.toPath())
        }
        logger.info(EVENT_DIRECTORY_CREATED)
        return metadata(file)
    }

    private fun requireRegularFile(file: File) {
        val attributes = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java)
        if (!attributes.isRegularFile) {
            throw DikcizFileCommands.failure(DikcizFileCommands.ERROR_NOT_FILE)
        }
    }

    private fun metadata(file: File): JSONObject {
        val attributes = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java)
        return JSONObject()
            .put(DikcizFileCommands.KEY_PATH, file.absolutePath)
            .put(KEY_NAME, file.name)
            .put(KEY_KIND, if (attributes.isDirectory) KIND_DIRECTORY else KIND_FILE)
            .put(KEY_SIZE, attributes.size())
            .put(KEY_MODIFIED_AT, attributes.lastModifiedTime().toMillis())
            .put(KEY_READABLE, file.canRead())
            .put(KEY_WRITABLE, file.canWrite())
    }

    companion object {
        private const val READ_MODE = "r"
        private const val KEY_NAME = "name"
        private const val KEY_ERROR = "error"
        private const val KEY_PARENT = "parent"
        private const val KEY_ITEMS = "items"
        private const val KEY_HAS_MORE = "hasMore"
        private const val KEY_BYTES_READ = "bytesRead"
        private const val KEY_BYTES_WRITTEN = "bytesWritten"
        private const val KEY_NEXT_OFFSET = "nextOffset"
        private const val KEY_EOF = "eof"
        private const val KEY_KIND = "kind"
        private const val KEY_SIZE = "size"
        private const val KEY_MODIFIED_AT = "modifiedAtMilliseconds"
        private const val KEY_READABLE = "readable"
        private const val KEY_WRITABLE = "writable"
        private const val KIND_DIRECTORY = "directory"
        private const val KIND_FILE = "file"
        private const val EVENT_ENTRY_UNAVAILABLE = "file_entry_unavailable"
        private const val EVENT_FILE_WRITTEN = "file_written"
        private const val EVENT_DIRECTORY_CREATED = "directory_created"
        private const val FIELD_ERROR_CLASS = "error_class"
    }
}

internal object DikcizFileCommands {
    const val TYPE_FILE_LIST = "fileList"
    const val TYPE_FILE_READ = "fileRead"
    const val TYPE_FILE_WRITE = "fileWrite"
    const val TYPE_FILE_MKDIR = "fileMkdir"
    const val TYPE_APK_INSTALL = "apkInstall"
    const val TYPE_APK_INSTALL_STATUS = "apkInstallStatus"
    const val KEY_PATH = "path"
    const val KEY_OFFSET = "offset"
    const val KEY_LIMIT = "limit"
    const val KEY_DATA_BASE64 = "dataBase64"
    const val KEY_OVERWRITE = "overwrite"
    const val KEY_PARENTS = "parents"
    const val KEY_SHA256 = "sha256"
    const val KEY_SESSION_ID = "sessionId"
    const val DEFAULT_LIST_LIMIT = 100
    const val MAXIMUM_LIST_LIMIT = 500
    const val DEFAULT_CHUNK_BYTES = 65_536
    const val MAXIMUM_CHUNK_BYTES = 262_144
    const val MAXIMUM_BASE64_CHARACTERS = (MAXIMUM_CHUNK_BYTES + 2) / 3 * 4
    const val MAXIMUM_PATH_CHARACTERS = 4096
    private const val MAXIMUM_JSON_INTEGER = 9_007_199_254_740_991L
    const val MAXIMUM_OFFSET = MAXIMUM_JSON_INTEGER - MAXIMUM_CHUNK_BYTES
    const val ERROR_ACCESS_DENIED = "file_access_denied"
    const val ERROR_NOT_FOUND = "file_not_found"
    const val ERROR_NOT_DIRECTORY = "not_a_directory"
    const val ERROR_NOT_FILE = "not_a_file"
    const val ERROR_EXISTS = "file_exists"
    const val ERROR_IO = "file_io_failed"
    const val ERROR_OFFSET = "file_offset_mismatch"
    const val ERROR_BASE64 = "invalid_base64"
    const val ERROR_CHUNK_SIZE = "file_chunk_too_large"
    val FIELDS = linkedMapOf(
        TYPE_FILE_LIST to setOf(KEY_PATH, KEY_OFFSET, KEY_LIMIT),
        TYPE_FILE_READ to setOf(KEY_PATH, KEY_OFFSET, KEY_LIMIT),
        TYPE_FILE_WRITE to setOf(KEY_PATH, KEY_OFFSET, KEY_DATA_BASE64, KEY_OVERWRITE),
        TYPE_FILE_MKDIR to setOf(KEY_PATH, KEY_PARENTS),
        TYPE_APK_INSTALL to setOf(KEY_PATH, KEY_SHA256),
        TYPE_APK_INSTALL_STATUS to setOf(KEY_SESSION_ID),
    )
    val REQUIRED_FIELDS = FIELDS.keys.associateWith { type ->
        when (type) {
            TYPE_FILE_WRITE -> setOf(KEY_PATH, KEY_DATA_BASE64)
            TYPE_APK_INSTALL_STATUS -> setOf(KEY_SESSION_ID)
            else -> setOf(KEY_PATH)
        }
    }
    val READ_ONLY_TYPES = setOf(TYPE_FILE_LIST, TYPE_FILE_READ, TYPE_APK_INSTALL_STATUS)
    val TOOLS = linkedMapOf(
        "dikciz_file_list" to TYPE_FILE_LIST,
        "dikciz_file_read" to TYPE_FILE_READ,
        "dikciz_file_write" to TYPE_FILE_WRITE,
        "dikciz_file_mkdir" to TYPE_FILE_MKDIR,
        "dikciz_apk_install" to TYPE_APK_INSTALL,
        "dikciz_apk_install_status" to TYPE_APK_INSTALL_STATUS,
    )
    private val SHA256_PATTERN = Regex("[a-fA-F0-9]{64}")
    private const val MESSAGE_INVALID = "invalid file command arguments"
    private const val MESSAGE_OPERATION_FAILED = "file operation failed"
    private const val MESSAGE_INVALID_PATH = "path must be a bounded absolute filesystem path"
    private val ENVELOPE_FIELDS = setOf(
        DikcizAutomationControlPlane.KEY_REQUEST_ID,
        DikcizAutomationControlPlane.KEY_TYPE,
    )

    fun validate(type: String, request: JSONObject, allowEnvelope: Boolean = false) {
        val allowed = FIELDS.getValue(type) + if (allowEnvelope) ENVELOPE_FIELDS else emptySet()
        if (request.keys().asSequence().any { it !in allowed } || REQUIRED_FIELDS.getValue(type).any { !request.has(it) }) {
            invalid()
        }
        if (type != TYPE_APK_INSTALL_STATUS) file(request, false)
        if (request.has(KEY_OFFSET)) integer(request, KEY_OFFSET, 0L, MAXIMUM_OFFSET)
        if (request.has(KEY_LIMIT)) {
            integer(request, KEY_LIMIT, 1L, if (type == TYPE_FILE_LIST) MAXIMUM_LIST_LIMIT.toLong() else MAXIMUM_CHUNK_BYTES.toLong())
        }
        for (key in listOf(KEY_OVERWRITE, KEY_PARENTS)) {
            if (request.has(key) && request.opt(key) !is Boolean) invalid()
        }
        if (request.has(KEY_DATA_BASE64)) {
            val data = request.opt(KEY_DATA_BASE64)
            if (data !is String || data.length > MAXIMUM_BASE64_CHARACTERS) invalid()
        }
        if (request.has(KEY_SHA256)) {
            val digest = request.opt(KEY_SHA256)
            if (digest !is String || !SHA256_PATTERN.matches(digest)) invalid()
        }
        if (type == TYPE_APK_INSTALL_STATUS) integer(request, KEY_SESSION_ID, 1L, Int.MAX_VALUE.toLong())
    }

    fun file(request: JSONObject, canonical: Boolean = true): File {
        val path = request.opt(KEY_PATH)
        if (path !is String || path.isBlank() || path.length > MAXIMUM_PATH_CHARACTERS || '\u0000' in path || !File(path).isAbsolute) {
            throw DikcizAutomationRequestException(DikcizAutomationControlPlane.ERROR_VALIDATION_FAILED, MESSAGE_INVALID_PATH)
        }
        val resolved = if (canonical) File(path).canonicalFile else File(path)
        if (isCredentialPath(resolved)) throw failure(ERROR_ACCESS_DENIED)
        return resolved
    }

    private fun credentialFiles(): List<File> {
        val record = File(android.os.Environment.getExternalStorageDirectory(), "Dikciz/" + DikcizRemoteAccessAuth.CONTROL_DIRECTORY_NAME + "/" + DikcizRemoteAccessAuth.RECORD_FILE_NAME).canonicalFile
        return listOf(record, File(record.path + ".bak"), File(record.path + ".new"))
    }

    fun isCredentialPath(file: File): Boolean = credentialFiles().any { it == file.canonicalFile }

    fun containsCredentials(file: File): Boolean = credentialFiles().any { credential ->
        credential.toPath().startsWith(file.canonicalFile.toPath())
    }

    fun failure(code: String, cause: Throwable? = null): DikcizAutomationRequestException {
        return DikcizAutomationRequestException(code, MESSAGE_OPERATION_FAILED).apply {
            if (cause != null) initCause(cause)
        }
    }

    private fun integer(request: JSONObject, key: String, minimum: Long, maximum: Long) {
        val value = request.opt(key)
        if (value !is Int && value !is Long) invalid()
        if ((value as Number).toLong() !in minimum..maximum) invalid()
    }

    private fun invalid(): Nothing {
        throw DikcizAutomationRequestException(DikcizAutomationControlPlane.ERROR_VALIDATION_FAILED, MESSAGE_INVALID)
    }
}
