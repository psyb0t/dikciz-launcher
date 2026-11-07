package org.fossify.home.dikciz

import android.util.AtomicFile
import android.util.Base64
import at.favre.lib.crypto.bcrypt.BCrypt
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import org.fossify.home.dikciz.DikcizAutomationControlPlane.Companion.KEY_ACTION
import org.fossify.home.dikciz.DikcizRemotePolicy.ACTION_CATALOG
import org.fossify.home.dikciz.DikcizRemotePolicy.ACTION_CREATE
import org.fossify.home.dikciz.DikcizRemotePolicy.ACTION_LIST
import org.fossify.home.dikciz.DikcizRemotePolicy.ACTION_REVOKE
import org.fossify.home.dikciz.DikcizRemotePolicy.ACTION_UPDATE
import org.fossify.home.dikciz.DikcizRemotePolicy.KEY_CREATED_AT
import org.fossify.home.dikciz.DikcizRemotePolicy.KEY_DIGEST
import org.fossify.home.dikciz.DikcizRemotePolicy.KEY_ID
import org.fossify.home.dikciz.DikcizRemotePolicy.KEY_NAME
import org.fossify.home.dikciz.DikcizRemotePolicy.KEY_PERMISSIONS
import org.fossify.home.dikciz.DikcizRemotePolicy.KEY_TOKEN
import org.fossify.home.dikciz.DikcizRemotePolicy.KEY_TOKENS
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal data class DikcizRemoteAccessAuthStatus(
    val configured: Boolean,
    val enabled: Boolean,
    val valid: Boolean,
    val state: String,
    val file: String,
) {
    fun toJson(): JSONObject {
        return JSONObject()
            .put(KEY_CONFIGURED, configured)
            .put(KEY_ENABLED, enabled)
            .put(KEY_VALID, valid)
            .put(KEY_STATE, state)
            .put(KEY_FILE, file)
    }

    private companion object {
        const val KEY_CONFIGURED = "configured"
        const val KEY_ENABLED = "enabled"
        const val KEY_FILE = "file"
        const val KEY_STATE = "state"
        const val KEY_VALID = "valid"
    }
}

internal class DikcizRemoteAccessAuthException(
    val code: String,
    message: String,
) : IllegalArgumentException(message)

internal class DikcizRemoteAccessAuth(
    configurationDirectory: File,
    private val logger: DikcizLogger,
) {
    private val recordFile = File(configurationDirectory, "$CONTROL_DIRECTORY_NAME/$RECORD_FILE_NAME")

    private val lockFile = File(recordFile.parentFile, "$RECORD_FILE_NAME.lock")

    private fun <T> locked(write: Boolean = false, action: () -> T): T = synchronized(RECORD_LOCK) {
        if (!write && !recordExists()) return@synchronized action()
        val parent = recordFile.parentFile
            ?: throw DikcizRemoteAccessAuthException(ERROR_RECORD_WRITE, MESSAGE_RECORD_WRITE_FAILED)
        if (!parent.exists() && !parent.mkdirs()) {
            throw DikcizRemoteAccessAuthException(ERROR_RECORD_WRITE, MESSAGE_RECORD_WRITE_FAILED)
        }
        FileOutputStream(lockFile, true).channel.use { channel ->
            channel.lock().use { action() }
        }
    }

    private fun recordExists() = recordFile.exists() ||
        File(recordFile.path + ".bak").exists() || File(recordFile.path + ".new").exists()

    fun status(): DikcizRemoteAccessAuthStatus = locked { readState().status(recordFile.absolutePath) }

    fun acceptsAuthorization(authorization: String?): Boolean = resolve(authorization) != null

    fun authorizationMonitor(authorization: String?): () -> Boolean {
        val access = resolve(authorization)
        return { access?.isCurrent() == true }
    }

    fun resolve(authorization: String?): DikcizRemoteAccess? = locked {
        val initial = readState()
        val record = initial.record
        val secret = parseBearerPassword(authorization)
        val token = secret?.let { value ->
            val id = TOKEN_PATTERN.matchEntire(value)?.groupValues?.get(1)
            record?.tokens?.find { it.id == id && it.enabled && it.rules.isActive() && digestMatches(it.digest, value) }
        }
        if (token != null && record?.enabled == true) {
            return@locked DikcizRemoteAccess(token.id, token.name, token.permissions, token.rules) {
                locked {
                    val current = readState().record
                    val present = current?.tokens?.find { it.id == token.id }
                    current?.enabled == true && present == token && token.rules.isActive()
                }
            }
        }
        val owner = when {
            initial is RecordState.Missing -> secret == null || !TOKEN_PATTERN.matches(secret)
            record == null -> false
            record.enabled -> secret != null && verifies(record, secret)
            else -> secret == null || !TOKEN_PATTERN.matches(secret)
        }
        if (!owner) return@locked null
        DikcizRemoteAccess(OWNER_ID, "Owner", setOf(DikcizRemotePolicy.OWNER)) {
            locked {
                val current = readState()
                when {
                    initial is RecordState.Missing -> current is RecordState.Missing
                    record == null -> false
                    else -> current.record?.let {
                        it.enabled == record.enabled && it.verifier == record.verifier
                    } == true
                }
            }
        }
    }

    fun executeRemoteCommand(
        commandType: String,
        authorization: String?,
        request: JSONObject = JSONObject(),
    ): JSONObject = locked(write = true) {
        if (commandType == DikcizAutomationControlPlane.TYPE_REMOTE_AUTH_STATUS) {
            return@locked readState().status(recordFile.absolutePath).toJson()
        }
        if (commandType == DikcizRemotePolicy.TYPE_INFO) {
            return@locked ((if (authorization == null) {
                DikcizRemoteAccess(OWNER_ID, "Local owner", setOf(DikcizRemotePolicy.OWNER)) { true }
            } else resolveUnlocked(authorization))?.toJson()
                ?: throw DikcizRemoteAccessAuthException(ERROR_AUTH_REQUIRED, MESSAGE_AUTH_REQUIRED))
        }
        if (commandType == DikcizRemotePolicy.TYPE_TOKENS) {
            requireCapability(authorization, commandType, request, localAllowed = true)
            return@locked manageTokens(request, ACTOR_REMOTE)
        }
        requireCapability(authorization, commandType, request, localAllowed = false)
        val record = requireValidRecord()
        val enabled = when (commandType) {
            DikcizAutomationControlPlane.TYPE_REMOTE_AUTH_ENABLE -> true
            DikcizAutomationControlPlane.TYPE_REMOTE_AUTH_DISABLE -> false
            else -> throw DikcizRemoteAccessAuthException(ERROR_UNKNOWN_COMMAND, MESSAGE_UNKNOWN_COMMAND)
        }
        requireMayDisable(record, enabled)
        writeRecord(record.copy(enabled = enabled))
        logger.info(EVENT_REMOTE_AUTH_CHANGED, mapOf(FIELD_ACTOR to ACTOR_REMOTE, FIELD_ENABLED to enabled))
        record.copy(enabled = enabled).let { RecordState.Valid(it).status(recordFile.absolutePath).toJson() }
    }

    private fun resolveUnlocked(authorization: String?): DikcizRemoteAccess? {
        val record = readState().record
        val secret = parseBearerPassword(authorization)
        if (record == null) {
            if (readState() !is RecordState.Missing) return null
            return DikcizRemoteAccess(OWNER_ID, "Owner", setOf(DikcizRemotePolicy.OWNER)) { true }
        }
        val token = secret?.let { value -> record.tokens.find { it.enabled && it.rules.isActive() && digestMatches(it.digest, value) } }
        if (token != null && record.enabled) return DikcizRemoteAccess(token.id, token.name, token.permissions, token.rules) { true }
        if (!record.enabled || (secret != null && verifies(record, secret))) {
            return DikcizRemoteAccess(OWNER_ID, "Owner", setOf(DikcizRemotePolicy.OWNER)) { true }
        }
        return null
    }

    private fun requireCapability(authorization: String?, type: String, request: JSONObject, localAllowed: Boolean) {
        if (localAllowed && authorization == null) return
        val access = resolveUnlocked(authorization)
            ?: throw DikcizRemoteAccessAuthException(ERROR_AUTH_REQUIRED, MESSAGE_AUTH_REQUIRED)
        access.requireCommand(type, request)
    }

    fun tokenCommandFromPhone(request: JSONObject): JSONObject = locked(write = true) {
        manageTokens(request, ACTOR_PHONE)
    }

    private fun manageTokens(request: JSONObject, actor: String): JSONObject {
        DikcizNativeCommands.validate(DikcizRemotePolicy.TYPE_TOKENS, request, envelope = true)
        val action = request.getString(KEY_ACTION)
        if (action == ACTION_CATALOG) return JSONObject().put(KEY_PERMISSIONS, DikcizRemotePolicy.catalogue())
        val record = requireValidRecord()
        if (action == ACTION_LIST) return JSONObject().put(KEY_TOKENS, JSONArray(record.tokens.map(TokenRecord::metadata)))
        if (!record.enabled) throw DikcizRemoteAccessAuthException(
            ERROR_AUTH_REQUIRED, "Enable owner authentication before managing tokens",
        )
        var createdSecret: String? = null
        var changed: TokenRecord? = null
        val updated = record.tokens.toMutableList()
        when (action) {
            ACTION_CREATE -> {
                if (updated.size >= MAXIMUM_TOKENS) throw DikcizRemoteAccessAuthException(
                    "remote_token_limit", "At most $MAXIMUM_TOKENS access tokens may be configured",
                )
                var id: String
                do { id = randomHex(TOKEN_ID_BYTES) } while (updated.any { it.id == id })
                createdSecret = "dkt1.$id." + Base64.encodeToString(
                    ByteArray(TOKEN_SECRET_BYTES).also(RANDOM::nextBytes),
                    Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
                )
                changed = TokenRecord(
                    id, request.getString(KEY_NAME).trim(),
                    DikcizRemotePolicy.permissionSet(request.getJSONArray(KEY_PERMISSIONS)),
                    true, System.currentTimeMillis(), digest(createdSecret), DikcizTokenRules.parse(request),
                )
                updated.add(changed)
            }
            ACTION_UPDATE, ACTION_REVOKE, DikcizRemotePolicy.ACTION_ROTATE -> {
                val index = updated.indexOfFirst { it.id == request.getString(KEY_ID) }
                if (index < 0) throw DikcizRemoteAccessAuthException("remote_token_not_found", "Access token not found")
                val previous = updated[index]
                if (action == ACTION_REVOKE) {
                    updated.removeAt(index)
                } else if (action == DikcizRemotePolicy.ACTION_ROTATE) {
                    createdSecret = "dkt1.${previous.id}." + Base64.encodeToString(
                        ByteArray(TOKEN_SECRET_BYTES).also(RANDOM::nextBytes),
                        Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
                    )
                    changed = previous.copy(digest = digest(createdSecret))
                    updated[index] = changed
                } else {
                    changed = previous.copy(
                        name = if (request.has(KEY_NAME)) request.getString(KEY_NAME).trim() else previous.name,
                        permissions = if (request.has(KEY_PERMISSIONS)) {
                            DikcizRemotePolicy.permissionSet(request.getJSONArray(KEY_PERMISSIONS))
                        } else previous.permissions,
                        enabled = if (request.has("enabled")) request.getBoolean("enabled") else previous.enabled,
                        rules = DikcizTokenRules.parse(request, previous.rules),
                    )
                    updated[index] = changed
                }
            }
        }
        writeRecord(record.copy(tokens = updated))
        logger.info("remote_token_changed", mapOf(
            FIELD_ACTOR to actor, KEY_ACTION to action, KEY_ID to (changed?.id ?: request.optString(KEY_ID)),
            KEY_PERMISSIONS to changed?.permissions.orEmpty().sorted(), FIELD_ENABLED to (changed?.enabled ?: false),
        ))
        return JSONObject().put(KEY_ACTION, action).put(KEY_TOKENS, JSONArray(updated.map(TokenRecord::metadata))).apply {
            changed?.let { put("item", it.metadata()) }
            createdSecret?.let { put(KEY_TOKEN, it) }
        }
    }

    fun setPasswordFromPhone(password: CharArray) {
        try {
            if (!isValidPassword(password)) {
                throw DikcizRemoteAccessAuthException(ERROR_INVALID_PASSWORD, MESSAGE_INVALID_PASSWORD)
            }
            val verifier = BCrypt.withDefaults().hashToString(BCRYPT_COST, password)
            locked(write = true) {
                writeRecord(AuthRecord(enabled = true, verifier = verifier, tokens = readState().record?.tokens.orEmpty()))
            }
            logger.info(EVENT_REMOTE_AUTH_CHANGED, mapOf(FIELD_ACTOR to ACTOR_PHONE, FIELD_ENABLED to true))
        } finally {
            password.fill(PASSWORD_CLEAR_CHARACTER)
        }
    }

    fun setEnabledFromPhone(enabled: Boolean) = locked(write = true) {
        val record = requireValidRecord()
        requireMayDisable(record, enabled)
        writeRecord(record.copy(enabled = enabled))
        logger.info(EVENT_REMOTE_AUTH_CHANGED, mapOf(FIELD_ACTOR to ACTOR_PHONE, FIELD_ENABLED to enabled))
    }

    fun clearPasswordFromPhone() = locked(write = true) {
        if (!recordExists()) return@locked
        readState().record?.let { requireMayDisable(it, false) }
        try {
            AtomicFile(recordFile).delete()
            if (recordExists()) throw IllegalStateException(MESSAGE_CLEAR_FAILED)
        } catch (_: Exception) {
            throw DikcizRemoteAccessAuthException(ERROR_RECORD_WRITE, MESSAGE_CLEAR_FAILED)
        }
        logger.info(EVENT_REMOTE_AUTH_CLEARED, mapOf(FIELD_ACTOR to ACTOR_PHONE))
    }

    private fun requireMayDisable(record: AuthRecord, enabled: Boolean) {
        if (!enabled && record.tokens.isNotEmpty()) throw DikcizRemoteAccessAuthException(
            "remote_auth_tokens_exist", "Revoke every access token before disabling or clearing authentication",
        )
    }

    private fun requireValidRecord(): AuthRecord = when (val state = readState()) {
        is RecordState.Valid -> state.record
        RecordState.Missing -> throw DikcizRemoteAccessAuthException(ERROR_PASSWORD_NOT_CONFIGURED, MESSAGE_PASSWORD_NOT_CONFIGURED)
        RecordState.Invalid -> throw DikcizRemoteAccessAuthException(ERROR_RECORD_INVALID, MESSAGE_RECORD_INVALID)
    }

    private fun verifies(record: AuthRecord, password: String): Boolean {
        val passwordCharacters = password.toCharArray()
        return try {
            BCrypt.verifyer().verify(passwordCharacters, record.verifier).verified
        } catch (_: IllegalArgumentException) {
            false
        } finally {
            passwordCharacters.fill(PASSWORD_CLEAR_CHARACTER)
        }
    }

    private fun parseBearerPassword(authorization: String?): String? {
        if (authorization == null || authorization.length > MAXIMUM_AUTHORIZATION_CHARACTERS) {
            return null
        }
        val separator = authorization.indexOf(SPACE)
        if (separator <= 0 || authorization.indexOf(SPACE, separator + 1) >= 0) {
            return null
        }
        if (!authorization.substring(0, separator).equals(BEARER_SCHEME, ignoreCase = true)) {
            return null
        }
        val password = authorization.substring(separator + 1)
        val passwordCharacters = password.toCharArray()
        return try {
            password.takeIf { isValidPassword(passwordCharacters) }
        } finally {
            passwordCharacters.fill(PASSWORD_CLEAR_CHARACTER)
        }
    }

    private fun isValidPassword(password: CharArray): Boolean {
        if (password.size !in MINIMUM_PASSWORD_CHARACTERS..MAXIMUM_PASSWORD_CHARACTERS) {
            return false
        }
        return password.all(::isBearerPasswordCharacter)
    }

    private fun isBearerPasswordCharacter(character: Char): Boolean {
        return character in LOWERCASE_LETTERS ||
            character in UPPERCASE_LETTERS ||
            character in DIGITS ||
            character in BEARER_PASSWORD_SYMBOLS
    }

    private fun writeRecord(record: AuthRecord) {
        val parent = recordFile.parentFile
            ?: throw DikcizRemoteAccessAuthException(ERROR_RECORD_WRITE, MESSAGE_RECORD_WRITE_FAILED)
        if (!parent.exists() && !parent.mkdirs()) {
            throw DikcizRemoteAccessAuthException(ERROR_RECORD_WRITE, MESSAGE_RECORD_WRITE_FAILED)
        }
        if (!parent.isDirectory) {
            throw DikcizRemoteAccessAuthException(ERROR_RECORD_WRITE, MESSAGE_RECORD_WRITE_FAILED)
        }
        val serialized = record.toJson().toString(JSON_INDENTATION_SPACES)
        if (serialized.toByteArray(StandardCharsets.UTF_8).size > MAXIMUM_RECORD_BYTES) {
            throw DikcizRemoteAccessAuthException(ERROR_RECORD_WRITE, "Access-token record exceeds storage capacity")
        }
        val atomicFile = AtomicFile(recordFile)
        var output: FileOutputStream? = null
        try {
            output = atomicFile.startWrite()
            output.write(serialized.toByteArray(StandardCharsets.UTF_8))
            output.fd.sync()
            atomicFile.finishWrite(output)
        } catch (exception: Exception) {
            output?.let(atomicFile::failWrite)
            throw DikcizRemoteAccessAuthException(ERROR_RECORD_WRITE, MESSAGE_RECORD_WRITE_FAILED)
        }
    }

    private fun readState(): RecordState {
        if (!recordExists()) return RecordState.Missing
        val document = try {
            val bytes = AtomicFile(recordFile).openRead().use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    if (output.size() > MAXIMUM_RECORD_BYTES) return RecordState.Invalid
                }
                output.toByteArray()
            }
            if (bytes.size < MINIMUM_RECORD_BYTES) return RecordState.Invalid
            JSONObject(String(bytes, StandardCharsets.UTF_8))
        } catch (_: JSONException) {
            return RecordState.Invalid
        } catch (_: Exception) {
            return RecordState.Invalid
        }
        val version = document.opt(KEY_VERSION)
        if (version != RECORD_VERSION && version != TOKEN_RECORD_VERSION) return RecordState.Invalid
        val expectedKeys = setOf(KEY_VERSION, KEY_ALGORITHM, KEY_COST, KEY_ENABLED, KEY_VERIFIER) +
            if (version == TOKEN_RECORD_VERSION) setOf(KEY_TOKENS) else emptySet()
        if (document.keys().asSequence().toSet() != expectedKeys) {
            return RecordState.Invalid
        }
        if (document.opt(KEY_ALGORITHM) != BCRYPT_ALGORITHM ||
            document.opt(KEY_COST) != BCRYPT_COST
        ) {
            return RecordState.Invalid
        }
        val enabled = document.opt(KEY_ENABLED) as? Boolean ?: return RecordState.Invalid
        val verifier = document.opt(KEY_VERIFIER) as? String ?: return RecordState.Invalid
        if (!BCRYPT_VERIFIER_PATTERN.matches(verifier)) {
            return RecordState.Invalid
        }
        val tokens = try {
            if (version == TOKEN_RECORD_VERSION) {
                val rows = document.getJSONArray(KEY_TOKENS)
                if (rows.length() > MAXIMUM_TOKENS) return RecordState.Invalid
                (0 until rows.length()).map { TokenRecord.parse(rows.getJSONObject(it)) }.also {
                    if (it.map(TokenRecord::id).distinct().size != it.size || (!enabled && it.isNotEmpty())) return RecordState.Invalid
                }
            } else emptyList()
        } catch (_: Exception) {
            return RecordState.Invalid
        }
        return RecordState.Valid(AuthRecord(enabled = enabled, verifier = verifier, tokens = tokens))
    }

    private data class AuthRecord(
        val enabled: Boolean,
        val verifier: String,
        val tokens: List<TokenRecord> = emptyList(),
    ) {
        fun toJson(): JSONObject {
            return JSONObject()
                .put(KEY_VERSION, if (tokens.isEmpty()) RECORD_VERSION else TOKEN_RECORD_VERSION)
                .put(KEY_ALGORITHM, BCRYPT_ALGORITHM)
                .put(KEY_COST, BCRYPT_COST)
                .put(KEY_ENABLED, enabled)
                .put(KEY_VERIFIER, verifier)
                .apply { if (tokens.isNotEmpty()) put(KEY_TOKENS, JSONArray(tokens.map(TokenRecord::toJson))) }
        }
    }

    private data class TokenRecord(
        val id: String,
        val name: String,
        val permissions: Set<String>,
        val enabled: Boolean,
        val createdAt: Long,
        val digest: String,
        val rules: DikcizTokenRules = DikcizTokenRules(),
    ) {
        fun metadata() = rules.write(JSONObject().put(KEY_ID, id).put(KEY_NAME, name)
            .put(KEY_PERMISSIONS, JSONArray(permissions.sorted())).put("enabled", enabled).put(KEY_CREATED_AT, createdAt))
            .put("expired", !rules.isActive())
            .put("lastUsedAt", DikcizOperations.lastUsed(id) ?: JSONObject.NULL)
        fun toJson() = metadata().apply { remove("expired"); remove("lastUsedAt") }.put(KEY_DIGEST, digest)

        companion object {
            fun parse(document: JSONObject): TokenRecord {
                val required = setOf(KEY_ID, KEY_NAME, KEY_PERMISSIONS, "enabled", KEY_CREATED_AT, KEY_DIGEST)
                val keys = document.keys().asSequence().toSet()
                require(keys.containsAll(required) && keys.all { it in required || it in DikcizTokenRules.KEYS })
                val id = document.opt(KEY_ID) as? String ?: error("Invalid token ID")
                require(Regex("[a-f0-9]{16}").matches(id))
                val name = document.opt(KEY_NAME) as? String ?: error("Invalid token name")
                DikcizRemotePolicy.validateName(name)
                val permissions = DikcizRemotePolicy.permissionSet(document.getJSONArray(KEY_PERMISSIONS))
                val enabled = document.opt("enabled") as? Boolean ?: error("Invalid token enabled flag")
                val timestamp = document.opt(KEY_CREATED_AT)
                require(timestamp is Long || timestamp is Int)
                val createdAt = (timestamp as Number).toLong()
                require(createdAt > 0)
                val digest = document.opt(KEY_DIGEST) as? String ?: error("Invalid token digest")
                require(Regex("[a-f0-9]{64}").matches(digest))
                return TokenRecord(id, name, permissions, enabled, createdAt, digest, DikcizTokenRules.parse(document))
            }
        }
    }

    private sealed interface RecordState {
        val record: AuthRecord?

        fun status(file: String): DikcizRemoteAccessAuthStatus

        data object Missing : RecordState {
            override val record: AuthRecord? = null

            override fun status(file: String): DikcizRemoteAccessAuthStatus {
                return DikcizRemoteAccessAuthStatus(false, false, true, STATE_UNCONFIGURED, file)
            }
        }

        data object Invalid : RecordState {
            override val record: AuthRecord? = null

            override fun status(file: String): DikcizRemoteAccessAuthStatus {
                return DikcizRemoteAccessAuthStatus(true, true, false, STATE_INVALID, file)
            }
        }

        data class Valid(
            override val record: AuthRecord,
        ) : RecordState {
            override fun status(file: String): DikcizRemoteAccessAuthStatus {
                val state = if (record.enabled) STATE_ENABLED else STATE_DISABLED
                return DikcizRemoteAccessAuthStatus(true, record.enabled, true, state, file)
            }
        }
    }

    internal companion object {
        const val ACTOR_PHONE = "phone"
        const val ACTOR_REMOTE = "remote"
        const val BEARER_SCHEME = "Bearer"
        const val BEARER_PASSWORD_SYMBOLS = "-._~+/="
        const val BCRYPT_ALGORITHM = "bcrypt"
        const val BCRYPT_COST = 12
        const val CONTROL_DIRECTORY_NAME = "control"
        const val DIGITS = "0123456789"
        const val ERROR_AUTH_REQUIRED = "remote_auth_required"
        const val ERROR_INVALID_PASSWORD = "remote_auth_password_invalid"
        const val ERROR_PASSWORD_NOT_CONFIGURED = "remote_auth_password_not_configured"
        const val ERROR_RECORD_INVALID = "remote_auth_record_invalid"
        const val ERROR_RECORD_WRITE = "remote_auth_record_write_failed"
        const val ERROR_UNKNOWN_COMMAND = "remote_auth_unknown_command"
        const val EVENT_REMOTE_AUTH_CHANGED = "remote_auth_changed"
        const val EVENT_REMOTE_AUTH_CLEARED = "remote_auth_cleared"
        const val FIELD_ACTOR = "actor"
        const val FIELD_ENABLED = "enabled"
        const val JSON_INDENTATION_SPACES = 2
        const val KEY_ALGORITHM = "algorithm"
        const val KEY_COST = "cost"
        const val KEY_ENABLED = "enabled"
        const val KEY_VERIFIER = "verifier"
        const val KEY_VERSION = "version"
        const val LOWERCASE_LETTERS = "abcdefghijklmnopqrstuvwxyz"
        const val MAXIMUM_AUTHORIZATION_CHARACTERS = 256
        const val MAXIMUM_PASSWORD_CHARACTERS = 72
        const val MAXIMUM_RECORD_BYTES = 8388608L
        const val MESSAGE_AUTH_REQUIRED = "a valid remote bearer credential is required"
        const val MESSAGE_CLEAR_FAILED = "could not clear the remote auth record"
        const val MESSAGE_INVALID_PASSWORD = "password must use 12 to 72 bearer-safe ASCII characters"
        const val MESSAGE_PASSWORD_NOT_CONFIGURED = "no remote password is configured"
        const val MESSAGE_RECORD_INVALID = "the remote auth record is invalid"
        const val MESSAGE_RECORD_WRITE_FAILED = "could not save the remote auth record"
        const val MESSAGE_UNKNOWN_COMMAND = "unknown remote auth command"
        const val MINIMUM_PASSWORD_CHARACTERS = 12
        const val MINIMUM_RECORD_BYTES = 2L
        const val PASSWORD_CLEAR_CHARACTER = '\u0000'
        const val RECORD_FILE_NAME = "remote-auth.json"
        private val RECORD_LOCK = Any()
        private val RANDOM = SecureRandom()
        private val TOKEN_PATTERN = Regex("dkt1\\.([a-f0-9]{16})\\.[A-Za-z0-9_-]{43}")
        private const val OWNER_ID = "owner"
        private const val TOKEN_RECORD_VERSION = 2
        private const val MAXIMUM_TOKENS = 32
        private const val TOKEN_ID_BYTES = 8
        private const val TOKEN_SECRET_BYTES = 32

        private fun randomHex(bytes: Int) = ByteArray(bytes).also(RANDOM::nextBytes).joinToString("") { "%02x".format(it) }
        // Generated tokens carry 256 random bits; SHA-256 verifies keys, while human passwords retain bcrypt.
        private fun digest(secret: String) = MessageDigest.getInstance("SHA-256")
            .digest(secret.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
        private fun digestMatches(expected: String, secret: String) = MessageDigest.isEqual(
            expected.toByteArray(StandardCharsets.US_ASCII),
            digest(secret).toByteArray(StandardCharsets.US_ASCII),
        )
        const val RECORD_VERSION = 1
        const val SPACE = ' '
        const val STATE_DISABLED = "disabled"
        const val STATE_ENABLED = "enabled"
        const val STATE_INVALID = "invalid"
        const val STATE_UNCONFIGURED = "unconfigured"
        const val UPPERCASE_LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val BCRYPT_VERIFIER_PATTERN = Regex("^\\$2a\\$12\\$[./A-Za-z0-9]{53}$")
    }
}
