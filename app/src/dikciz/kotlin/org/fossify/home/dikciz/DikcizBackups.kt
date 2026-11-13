package org.fossify.home.dikciz

import android.content.Context
import android.util.AtomicFile
import android.util.Base64
import java.io.File
import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import org.json.JSONArray
import org.json.JSONObject

/** Encrypted portable snapshots. Restore preserves listener configuration and remote credentials. */
internal object DikcizBackups {
    const val TYPE_CREATE = "backupCreate"
    const val TYPE_PREVIEW = "backupPreview"
    const val TYPE_RESTORE = "backupRestore"
    const val TYPE_LIST = "backupsList"
    private const val WORK_DIRECTORY = "backup-work"
    private const val ARCHIVE_DIRECTORY = "backups"
    private const val MAXIMUM_ARCHIVE_BYTES = 67108864
    private const val MAXIMUM_DATA_BYTES = 33554432L
    private const val MAXIMUM_FILES = 4096
    private const val MAXIMUM_PATH_BYTES = 2048
    private const val PREVIEW_LIFETIME = 900000L
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128
    private const val KDF_ITERATIONS = 600000
    private val MAGIC = "DIKCIZB1".toByteArray(Charsets.US_ASCII)
    private val RANDOM = SecureRandom()
    private val LEGACY_ROOTS = setOf("pages", "scripts", "widget-library", "themes", "fonts", "wallpapers", "automation")
    private val ROOTS = LEGACY_ROOTS + setOf("apps", "assistant")
    private val ROOT_FILES = setOf("config.json", ".bundled-starter")
    private val restoreOwner = ThreadLocal<Boolean>()
    private val backupLock = Any()
    private val ID = Regex("[a-f0-9-]{36}")
    @Volatile private var protectedRoot: File? = null

    fun blocksConfiguration(context: Context): Boolean =
        restoreOwner.get() != true && File(context.filesDir, WORK_DIRECTORY + "/restore.json").isFile

    private fun <T> withRestoreOwner(action: () -> T): T {
        val previous = restoreOwner.get()
        restoreOwner.set(true)
        return try { action() } finally { restoreOwner.set(previous) }
    }

    fun attach(context: Context) { protectedRoot = File(context.filesDir, WORK_DIRECTORY).canonicalFile }

    fun isProtected(file: File): Boolean = protectedRoot?.let { file.canonicalFile.toPath().startsWith(it.toPath()) } == true
    fun containsProtected(file: File): Boolean = protectedRoot?.let { it.toPath().startsWith(file.canonicalFile.toPath()) } == true

    fun recover(context: Context) {
        // Ordinary UI reloads must not wait on a handler that needs the main thread.
        if (!File(context.filesDir, WORK_DIRECTORY + "/restore.json").exists()) return
        DikcizCustomApps.requireBackupCaller()
        synchronized(backupLock) { DikcizCustomApps.withBackupLock { recoverLocked(context) } }
    }

    private fun recoverLocked(context: Context) {
        try {
            recoverPending(context)
        } catch (exception: java.io.IOException) {
            DikcizNativeCommands.failure("backup_recovery_failed", "Restore storage is unavailable. Restore access before retrying.")
        } catch (exception: org.json.JSONException) {
            DikcizNativeCommands.failure("backup_recovery_failed", "Restore recovery record is damaged")
        }
    }

    private fun recoverPending(context: Context) {
        val work = work(context)
        val checkpoint = File(work, "restore.json")
        if (!checkpoint.exists()) return
        val record = try {
            if (checkpoint.length() > 1024) DikcizNativeCommands.failure("backup_recovery_failed", "Restore recovery record is oversized")
            JSONObject(checkpoint.readText())
        } catch (exception: org.json.JSONException) {
            DikcizNativeCommands.failure("backup_recovery_failed", "Restore recovery record is damaged")
        } catch (exception: java.io.IOException) {
            DikcizNativeCommands.failure("backup_recovery_failed", "Restore recovery record is unreadable")
        }
        val id = record.optString("id")
        if (record.keys().asSequence().toSet() !in setOf(setOf("id"), setOf("id", "version")) || record.has("version") && record.opt("version") != 2 || !ID.matches(id)) DikcizNativeCommands.failure("backup_recovery_failed", "Invalid restore recovery record")
        val roots = if (record.optInt("version", 1) == 2) ROOTS else LEGACY_ROOTS
        val chat = DikcizChatRuntime.get(context).store
        if (chat.committedRestoreID() != id) {
            val previous = File(work, "rollback/" + id)
            if (!previous.isDirectory || !File(previous, "config.json").isFile || File(previous, "config.json").length() == 0L) {
                DikcizNativeCommands.failure("backup_recovery_failed", "Restore rollback data is missing or incomplete")
            }
            HomeConfigStore(context).withConfigurationLock {
                if (record.optInt("version", 1) == 2) {
                    val runtimeFile = File(previous, "app-runtime.json")
                    if (!runtimeFile.isFile) DikcizNativeCommands.failure("backup_recovery_failed", "App runtime rollback data is missing")
                    val appRuntime = DikcizCustomApps.validateBackup(JSONArray(runtimeFile.readText()))
                    replacePublic(context, previous, roots)
                    DikcizCustomApps.restoreBackup(context, appRuntime, activate = false, rollback = true)
                } else replacePublic(context, previous, roots)
                AtomicFile(checkpoint).delete()
            }
        } else {
            AtomicFile(checkpoint).delete()
        }
        File(work, "rollback/" + id).deleteRecursively()
    }

    fun execute(context: Context, type: String, request: JSONObject): JSONObject {
        if (type == TYPE_LIST) return executeLocked(context, type, request)
        DikcizCustomApps.requireBackupCaller()
        return synchronized(backupLock) { executeLocked(context, type, request) }
    }

    private fun executeLocked(context: Context, type: String, request: JSONObject): JSONObject {
        work(context)
        if (type == TYPE_LIST) {
            val directory = File(root(context), ARCHIVE_DIRECTORY)
            val files = directory.listFiles()?.filter { it.isFile && it.extension == "dikciz-backup" }?.sortedByDescending(File::lastModified).orEmpty()
            return DikcizNativeCommands.page(files, request) {
                JSONObject().put("path", it.absolutePath).put("bytes", it.length()).put("modifiedAt", it.lastModified()).put("encrypted", true)
            }.put("total", files.size)
        }
        if (type == TYPE_RESTORE) return restore(context, request)
        val passphrase = request.getString("passphrase").toCharArray()
        if (passphrase.size !in 12..1024) DikcizNativeCommands.invalid("Use a backup passphrase with 12 to 1024 characters")
        return try {
            if (type == TYPE_CREATE) create(context, request, passphrase) else preview(context, request, passphrase)
        } finally { passphrase.fill('\u0000') }
    }

    private fun root(context: Context) = requireNotNull(HomeConfigStore(context).configurationFile.parentFile)
    private fun work(context: Context): File {
        val directory = File(context.filesDir, WORK_DIRECTORY).canonicalFile
        protectedRoot = directory
        if (!directory.isDirectory && !directory.mkdirs()) DikcizNativeCommands.failure("backup_storage_failed", "Could not create private backup staging")
        return directory
    }

    private fun create(context: Context, request: JSONObject, passphrase: CharArray): JSONObject {
        val config = HomeConfigStore(context)
        val snapshot = DikcizCustomApps.withBackupLock { config.withConfigurationLock {
            val files = snapshotFiles(root(context))
            JSONObject().put("version", 2).put("appRuntime", DikcizCustomApps.exportBackup(context)).put("createdAt", System.currentTimeMillis())
                .put("configuration", config.readConfigurationDocument()).put("files", files)
                .put("chat", DikcizChatRuntime.get(context).store.exportBackup(request.optBoolean("includeApiKeys", false)))
                .put("includesApiKeys", request.optBoolean("includeApiKeys", false))
        }
        }
        val plain = snapshot.toString().toByteArray(Charsets.UTF_8)
        if (plain.size > MAXIMUM_ARCHIVE_BYTES - HEADER_ALLOWANCE) DikcizNativeCommands.failure("backup_too_large", "Backup exceeds 64 MiB encoded capacity")
        val encoded = try { encrypt(plain, passphrase) } finally { plain.fill(0) }
        val directory = File(root(context), ARCHIVE_DIRECTORY)
        if (!directory.isDirectory && !directory.mkdirs()) DikcizNativeCommands.failure("backup_storage_failed", "Cannot create backup directory")
        val destination = File(directory, UUID.randomUUID().toString() + ".dikciz-backup")
        atomicWrite(destination, encoded)
        return JSONObject().put("path", destination.absolutePath).put("bytes", encoded.size).put("sha256", digest(encoded))
            .put("encrypted", true).put("includesApiKeys", snapshot.getBoolean("includesApiKeys"))
            .put("message", "Remote credentials and control listener settings are excluded.")
    }

    private fun snapshotFiles(source: File): JSONArray {
        val rows = JSONArray()
        var bytes = 0L
        for (file in source.walkTopDown().onEnter {
            it == source || it.relativeTo(source).invariantSeparatorsPath.substringBefore('/') in ROOTS
        }) {
            val path = file.relativeTo(source).invariantSeparatorsPath
            if (path.isEmpty()) continue
            val top = path.substringBefore('/')
            if (top !in ROOTS && path !in ROOT_FILES) continue
            requireOrdinary(file, source)
            if (!file.isFile) continue
            validatePath(path, ROOTS)
            bytes += file.length()
            if (bytes > MAXIMUM_DATA_BYTES || rows.length() >= MAXIMUM_FILES) {
                DikcizNativeCommands.failure("backup_too_large", "Backup data exceeds 32 MiB or 4096 files")
            }
            val data = file.inputStream().use { readBounded(it) }
            if (data.size.toLong() != file.length()) DikcizNativeCommands.failure("backup_changed", "A backup input changed while being read")
            rows.put(JSONObject().put("path", path).put("sha256", digest(data)).put("data", Base64.encodeToString(data, Base64.NO_WRAP)))
        }
        return rows
    }

    private fun preview(context: Context, request: JSONObject, passphrase: CharArray): JSONObject {
        val file = DikcizFileCommands.file(JSONObject().put("path", request.getString("path")))
        if (!file.isFile || file.length() > MAXIMUM_ARCHIVE_BYTES) DikcizNativeCommands.invalid("Invalid or oversized backup")
        val bytes = file.inputStream().use { readBounded(it) }
        if (bytes.size > MAXIMUM_ARCHIVE_BYTES) DikcizNativeCommands.invalid("Backup exceeds 64 MiB")
        val plain = try { decrypt(bytes, passphrase) } catch (exception: java.security.GeneralSecurityException) {
            DikcizNativeCommands.failure("backup_decryption_failed", "Wrong passphrase or damaged backup")
        }
        val snapshot = try { JSONObject(plain.toString(Charsets.UTF_8)) } finally { plain.fill(0) }
        val version = snapshot.opt("version")
        val expected = setOf("version", "createdAt", "configuration", "files", "chat", "includesApiKeys") + if (version == 2) setOf("appRuntime") else emptySet()
        if (snapshot.keys().asSequence().toSet() != expected || version !in setOf(1, 2) || snapshot.opt("includesApiKeys") !is Boolean) {
            DikcizNativeCommands.invalid("Unsupported backup format")
        }
        val id = UUID.randomUUID().toString()
        val previewRoot = File(work(context), "previews")
        previewRoot.listFiles()?.filter { it.isDirectory && ID.matches(it.name) }?.forEach { previous ->
            val metadata = File(previous, "preview.json")
            if (!metadata.exists() || System.currentTimeMillis() - metadata.lastModified() > PREVIEW_LIFETIME) {
                previous.deleteRecursively()
            }
        }
        if ((previewRoot.listFiles()?.size ?: 0) >= 4) DikcizNativeCommands.failure("backup_preview_capacity", "At most four restore previews can be staged")
        val stage = File(previewRoot, id)
        if (!stage.mkdirs()) DikcizNativeCommands.failure("backup_storage_failed", "Cannot stage backup")
        try {
            val files = snapshot.getJSONArray("files")
            if (files.length() > MAXIMUM_FILES) DikcizNativeCommands.invalid("Too many backup files")
            var total = 0L
            val paths = mutableSetOf<String>()
            val public = File(stage, "root/Dikciz")
            if (!public.mkdirs()) DikcizNativeCommands.failure("backup_storage_failed", "Cannot stage home")
            for (index in 0 until files.length()) {
                val entry = files.getJSONObject(index)
                if (entry.keys().asSequence().toSet() != setOf("path", "sha256", "data")) DikcizNativeCommands.invalid("Invalid file manifest")
                val path = entry.getString("path")
                validatePath(path, if (version == 2) ROOTS else LEGACY_ROOTS)
                if (!paths.add(path)) DikcizNativeCommands.invalid("Duplicate backup path")
                val data = Base64.decode(entry.getString("data"), Base64.DEFAULT)
                total += data.size
                if (total > MAXIMUM_DATA_BYTES || digest(data) != entry.getString("sha256")) DikcizNativeCommands.invalid("Invalid backup file bytes")
                val output = File(public, path)
                if (!output.parentFile!!.isDirectory && !output.parentFile!!.mkdirs()) DikcizNativeCommands.failure("backup_storage_failed", "Cannot stage backup file")
                atomicWrite(output, data)
            }
            if ("config.json" !in paths) DikcizNativeCommands.invalid("Backup has no home manifest")
            val stagedStore = HomeConfigStore(context, File(stage, "root"))
            val configuration = stagedStore.loadOrCreate()
            if (stagedStore.configurationDocument(configuration).toString() != snapshot.getJSONObject("configuration").toString()) {
                // JSON key order is not significant; compare a normalized round trip through the existing parser.
                val normalized = stagedStore.replaceConfigurationDocument(snapshot.getJSONObject("configuration"))
                if (!stagedStore.hasSameSerializedConfiguration(configuration, normalized)) DikcizNativeCommands.invalid("Backup manifest and assembled home disagree")
            }
            val chat = DikcizChatRuntime.get(context).store.validateBackup(snapshot.getJSONObject("chat"))
            atomicWrite(File(stage, "chat.json"), chat.toString().toByteArray(Charsets.UTF_8))
            val appStore = DikcizCustomAppStore(context, public)
            if (version == 2) {
                for (appId in appStore.ids()) appStore.read(appId)
                val appRuntime = DikcizCustomApps.validateBackup(snapshot.getJSONArray("appRuntime"))
                atomicWrite(File(stage, "app-runtime.json"), appRuntime.toString().toByteArray(Charsets.UTF_8))
            }
            val checksum = digest(bytes)
            val metadata = JSONObject().put("id", id).put("sha256", checksum).put("createdAt", System.currentTimeMillis())
                .put("files", files.length()).put("bytes", total).put("pages", configuration.pages.size)
                .put("widgets", configuration.pages.sumOf { it.widgets.size }).put("scripts", configuration.scripts.size)
                .put("conversations", chat.getJSONArray("conversations").length()).put("profiles", chat.getJSONArray("profiles").length())
                .put("includesApiKeys", snapshot.getBoolean("includesApiKeys")).put("version", version)
                .put("customApps", appStore.ids().size).put("assistantFiles", paths.count { it.startsWith("assistant/") })
            atomicWrite(File(stage, "preview.json"), metadata.toString().toByteArray(Charsets.UTF_8))
            return metadata.put("previewId", id).put("state", "ready_for_confirmation")
                .put("message", "Restore replaces home and chat, plus apps and assistant files in format 2. Older archives preserve those directories. Remote authentication and listener settings remain. Automation stays disabled unless activateAutomation=true. Imported pending app timers always require review.")
        } catch (exception: Exception) {
            stage.deleteRecursively()
            throw exception
        }
    }

    private fun restore(context: Context, request: JSONObject): JSONObject {
        val id = request.getString("previewId")
        if (!ID.matches(id) || request.opt("confirm") != true) DikcizNativeCommands.invalid("A previewId and confirm=true are required")
        val stage = File(work(context), "previews/" + id)
        val metadataFile = File(stage, "preview.json")
        if (!metadataFile.isFile) DikcizNativeCommands.failure("backup_preview_missing", "Preview is missing or expired")
        val metadata = JSONObject(metadataFile.readText())
        if (System.currentTimeMillis() - metadata.getLong("createdAt") > PREVIEW_LIFETIME) {
            stage.deleteRecursively()
            DikcizNativeCommands.failure("backup_preview_expired", "Preview expired. Preview the archive again.")
        }
        if (request.getString("sha256") != metadata.getString("sha256")) DikcizNativeCommands.invalid("Confirm the exact preview checksum")
        val runtime = DikcizChatRuntime.get(context)
        if (runtime.busy()) DikcizNativeCommands.failure("backup_busy", "Stop active assistant turns before restoring")
        val store = HomeConfigStore(context)
        return DikcizCustomApps.withBackupLock { runtime.withIdleState { store.withConfigurationLock {
            val rollback = File(work(context), "rollback/" + id)
            if (!rollback.mkdirs()) DikcizNativeCommands.failure("backup_storage_failed", "Cannot prepare restore rollback")
            val roots = if (metadata.optInt("version", 1) == 2) ROOTS else LEGACY_ROOTS
            copyPublic(root(context), rollback, roots)
            if (metadata.optInt("version", 1) == 2) atomicWrite(File(rollback, "app-runtime.json"),
                DikcizCustomApps.exportBackup(context).toString().toByteArray(Charsets.UTF_8))
            val checkpoint = File(work(context), "restore.json")
            atomicWrite(checkpoint, JSONObject().put("id", id).apply { if (metadata.optInt("version", 1) == 2) put("version", 2) }.toString().toByteArray(Charsets.UTF_8))
            try {
                val stagedStore = HomeConfigStore(context, File(stage, "root"))
                val configuration = stagedStore.loadOrCreate()
                if (!request.optBoolean("activateAutomation", false)) {
                    stagedStore.save(configuration.copy(scripts = configuration.scripts.map { it.copy(enabled = false) }))
                }
                withRestoreOwner {
                    replacePublic(context, File(stage, "root/Dikciz"), roots)
                    if (metadata.optInt("version", 1) == 2) DikcizCustomApps.restoreBackup(context,
                        JSONArray(File(stage, "app-runtime.json").readText()), request.optBoolean("activateAutomation", false))
                    store.loadOrCreate()
                    runtime.store.restoreBackup(JSONObject(File(stage, "chat.json").readText()), id, request.optBoolean("activateAutomation", false))
                    runtime.refreshAfterRestore()
                }
                AtomicFile(checkpoint).delete()
                DikcizCustomApps.refreshAfterBackup(context)
                rollback.deleteRecursively()
                stage.deleteRecursively()
                JSONObject().put("state", "restored").put("sha256", metadata.getString("sha256"))
                    .put("automationActivated", request.optBoolean("activateAutomation", false))
            } catch (exception: Exception) {
                recoverLocked(context)
                throw exception
            }
        } } }
    }

    private fun replacePublic(context: Context, source: File, roots: Set<String> = ROOTS) {
        val destination = root(context)
        for (name in roots + ROOT_FILES) {
            val old = File(destination, name)
            if (old.exists()) {
                requireOrdinary(old, destination)
                old.walkTopDown().forEach { requireOrdinary(it, destination) }
                if (!old.deleteRecursively()) DikcizNativeCommands.failure("backup_restore_failed", "Could not replace previous home data")
            }
        }
        copyPublic(source, destination, roots)
    }

    private fun copyPublic(source: File, destination: File, roots: Set<String> = ROOTS) {
        if (!destination.isDirectory && !destination.mkdirs()) DikcizNativeCommands.failure("backup_storage_failed", "Cannot prepare restore destination")
        for (name in roots + ROOT_FILES) {
            val input = File(source, name)
            if (!input.exists()) continue
            input.walkTopDown().forEach { requireOrdinary(it, source) }
            if (input.isDirectory) {
                if (!input.copyRecursively(File(destination, name), overwrite = false)) {
                    DikcizNativeCommands.failure("backup_storage_failed", "Could not copy a complete home backup")
                }
            } else input.copyTo(File(destination, name), overwrite = false)
        }
    }

    private fun requireOrdinary(file: File, root: File) {
        if (file.absoluteFile != file.canonicalFile || !file.canonicalFile.toPath().startsWith(root.canonicalFile.toPath())) {
            DikcizNativeCommands.invalid("Backup data must not contain symbolic links or escaped paths")
        }
    }

    private fun validatePath(path: String, roots: Set<String>) {
        val parts = path.split('/')
        if (path.toByteArray(Charsets.UTF_8).size > MAXIMUM_PATH_BYTES || parts.any { it.isEmpty() || it == "." || it == ".." || it.any(Char::isISOControl) } ||
            '\\' in path || (path !in ROOT_FILES && parts.first() !in roots)) DikcizNativeCommands.invalid("Unsafe backup path")
    }

    private fun readBounded(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (output.size().toLong() + count > MAXIMUM_ARCHIVE_BYTES) {
                DikcizNativeCommands.failure("backup_too_large", "Backup input exceeds 64 MiB")
            }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun atomicWrite(destination: File, data: ByteArray) {
        val atomic = AtomicFile(destination)
        val output = atomic.startWrite()
        try { output.write(data); atomic.finishWrite(output) }
        catch (exception: Exception) { atomic.failWrite(output); throw exception }
    }

    private fun cipher(mode: Int, password: CharArray, salt: ByteArray, iv: ByteArray): Cipher {
        val spec = PBEKeySpec(password, salt, KDF_ITERATIONS, KEY_BITS)
        val bytes = try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded } finally { spec.clearPassword() }
        return try {
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(mode, SecretKeySpec(bytes, "AES"), GCMParameterSpec(TAG_BITS, iv))
                updateAAD(MAGIC)
            }
        } finally { bytes.fill(0) }
    }

    private fun encrypt(plain: ByteArray, password: CharArray): ByteArray {
        val salt = ByteArray(SALT_BYTES).also(RANDOM::nextBytes)
        val iv = ByteArray(IV_BYTES).also(RANDOM::nextBytes)
        return MAGIC + salt + iv + cipher(Cipher.ENCRYPT_MODE, password, salt, iv).doFinal(plain)
    }

    private fun decrypt(bytes: ByteArray, password: CharArray): ByteArray {
        val offset = MAGIC.size + SALT_BYTES + IV_BYTES
        if (bytes.size <= offset || !bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            DikcizNativeCommands.invalid("Unsupported backup header")
        }
        return cipher(Cipher.DECRYPT_MODE, password,
            bytes.copyOfRange(MAGIC.size, MAGIC.size + SALT_BYTES), bytes.copyOfRange(MAGIC.size + SALT_BYTES, offset))
            .doFinal(bytes, offset, bytes.size - offset)
    }

    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private const val HEADER_ALLOWANCE = 64
    private fun text(size: Int) = JSONObject().put("type", "string").put("maxLength", size)
    private val PASSWORD = text(1024).put("minLength", 12).put("description", "Backup encryption passphrase. Never log or put it in prompts.")
    private val BOOLEAN = JSONObject().put("type", "boolean").put("default", false)
    val DEFINITIONS = listOf(
        DikcizNativeCommands.Definition(TYPE_CREATE, "dikciz_backup_create", "Create an AES-GCM encrypted portable home, widget-state, Lua, custom-app files/state/runtime, assistant skills/rules/agents, chat and provider-settings snapshot. Passphrase required. API keys excluded unless explicitly requested. Remote credentials and control ports always remain outside backups. Requires the matching explicit operation grant for scoped remote clients.",
            mapOf("passphrase" to PASSWORD, "includeApiKeys" to BOOLEAN), setOf("passphrase"), readOnly = false),
        DikcizNativeCommands.Definition(TYPE_PREVIEW, "dikciz_backup_preview", "Decrypt and validate an archive into protected staging without replacing current state. Returns counts, previewId and sha256 for explicit restore confirmation. Preview expires after 15 minutes. Requires the matching explicit operation grant for scoped remote clients.",
            mapOf("path" to text(4096), "passphrase" to PASSWORD), setOf("path", "passphrase"), readOnly = false),
        DikcizNativeCommands.Definition(TYPE_RESTORE, "dikciz_backup_restore", "Replace home and chat from a validated preview, with rollback recovery. Requires idle assistant, previewId, exact sha256 and confirm=true. Future automation remains disabled unless activateAutomation=true. Imported pending or claimed app timers always require review; active alerts and native sessions are not resumed. Remote auth and ports remain.",
            mapOf("previewId" to text(36), "sha256" to text(64), "confirm" to BOOLEAN, "activateAutomation" to BOOLEAN),
            setOf("previewId", "sha256", "confirm"), readOnly = false),
        DikcizNativeCommands.Definition(TYPE_LIST, "dikciz_backups_list", "List encrypted archive paths and metadata without reading contents. Requires the matching explicit operation grant for scoped remote clients.",
            mapOf("limit" to JSONObject().put("type", "integer").put("minimum", 1).put("maximum", 200),
                "offset" to JSONObject().put("type", "integer").put("minimum", 0).put("maximum", 1000000))),
    )
    val TYPES = setOf(TYPE_CREATE, TYPE_PREVIEW, TYPE_RESTORE, TYPE_LIST)
}
