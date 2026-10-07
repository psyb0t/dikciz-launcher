package org.fossify.home.dikciz

import android.util.AtomicFile
import at.favre.lib.crypto.bcrypt.BCrypt
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
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

    @Synchronized
    fun status(): DikcizRemoteAccessAuthStatus {
        return readState().status(recordFile.absolutePath)
    }

    @Synchronized
    fun acceptsAuthorization(authorization: String?): Boolean {
        val state = readState()
        val record = state.record ?: return state is RecordState.Missing
        if (!record.enabled) {
            return true
        }
        val password = parseBearerPassword(authorization) ?: return false
        return verifies(record, password)
    }

    @Synchronized
    fun executeRemoteCommand(commandType: String, authorization: String?): JSONObject {
        if (commandType == DikcizAutomationControlPlane.TYPE_REMOTE_AUTH_STATUS) {
            return status().toJson()
        }
        val record = requireVerifiedRecord(authorization)
        val enabled = when (commandType) {
            DikcizAutomationControlPlane.TYPE_REMOTE_AUTH_ENABLE -> true
            DikcizAutomationControlPlane.TYPE_REMOTE_AUTH_DISABLE -> false
            else -> throw DikcizRemoteAccessAuthException(ERROR_UNKNOWN_COMMAND, MESSAGE_UNKNOWN_COMMAND)
        }
        writeRecord(record.copy(enabled = enabled))
        logger.info(
            EVENT_REMOTE_AUTH_CHANGED,
            mapOf(FIELD_ACTOR to ACTOR_REMOTE, FIELD_ENABLED to enabled),
        )
        return status().toJson()
    }

    @Synchronized
    fun setPasswordFromPhone(password: CharArray) {
        try {
            if (!isValidPassword(password)) {
                throw DikcizRemoteAccessAuthException(ERROR_INVALID_PASSWORD, MESSAGE_INVALID_PASSWORD)
            }
            val verifier = BCrypt.withDefaults().hashToString(BCRYPT_COST, password)
            writeRecord(AuthRecord(enabled = true, verifier = verifier))
            logger.info(
                EVENT_REMOTE_AUTH_CHANGED,
                mapOf(FIELD_ACTOR to ACTOR_PHONE, FIELD_ENABLED to true),
            )
        } finally {
            password.fill(PASSWORD_CLEAR_CHARACTER)
        }
    }

    @Synchronized
    fun setEnabledFromPhone(enabled: Boolean) {
        val record = requireValidRecord()
        writeRecord(record.copy(enabled = enabled))
        logger.info(
            EVENT_REMOTE_AUTH_CHANGED,
            mapOf(FIELD_ACTOR to ACTOR_PHONE, FIELD_ENABLED to enabled),
        )
    }

    @Synchronized
    fun clearPasswordFromPhone() {
        if (!recordFile.exists()) {
            return
        }
        readState()
        if (!recordFile.isFile || !recordFile.delete()) {
            throw DikcizRemoteAccessAuthException(ERROR_RECORD_WRITE, MESSAGE_CLEAR_FAILED)
        }
        logger.info(
            EVENT_REMOTE_AUTH_CLEARED,
            mapOf(FIELD_ACTOR to ACTOR_PHONE),
        )
    }

    private fun requireVerifiedRecord(authorization: String?): AuthRecord {
        val record = requireValidRecord()
        val password = parseBearerPassword(authorization)
            ?: throw DikcizRemoteAccessAuthException(ERROR_AUTH_REQUIRED, MESSAGE_AUTH_REQUIRED)
        if (!verifies(record, password)) {
            throw DikcizRemoteAccessAuthException(ERROR_AUTH_REQUIRED, MESSAGE_AUTH_REQUIRED)
        }
        return record
    }

    private fun requireValidRecord(): AuthRecord {
        return when (val state = readState()) {
            is RecordState.Valid -> state.record
            RecordState.Missing -> throw DikcizRemoteAccessAuthException(
                ERROR_PASSWORD_NOT_CONFIGURED,
                MESSAGE_PASSWORD_NOT_CONFIGURED,
            )
            RecordState.Invalid -> throw DikcizRemoteAccessAuthException(
                ERROR_RECORD_INVALID,
                MESSAGE_RECORD_INVALID,
            )
        }
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
        if (!recordFile.exists()) {
            return RecordState.Missing
        }
        if (!recordFile.isFile || recordFile.length() !in MINIMUM_RECORD_BYTES..MAXIMUM_RECORD_BYTES) {
            return RecordState.Invalid
        }
        val document = try {
            JSONObject(recordFile.readText(StandardCharsets.UTF_8))
        } catch (_: JSONException) {
            return RecordState.Invalid
        } catch (_: Exception) {
            return RecordState.Invalid
        }
        val expectedKeys = setOf(KEY_VERSION, KEY_ALGORITHM, KEY_COST, KEY_ENABLED, KEY_VERIFIER)
        if (document.keys().asSequence().toSet() != expectedKeys) {
            return RecordState.Invalid
        }
        if (document.opt(KEY_VERSION) != RECORD_VERSION ||
            document.opt(KEY_ALGORITHM) != BCRYPT_ALGORITHM ||
            document.opt(KEY_COST) != BCRYPT_COST
        ) {
            return RecordState.Invalid
        }
        val enabled = document.opt(KEY_ENABLED) as? Boolean ?: return RecordState.Invalid
        val verifier = document.opt(KEY_VERIFIER) as? String ?: return RecordState.Invalid
        if (!BCRYPT_VERIFIER_PATTERN.matches(verifier)) {
            return RecordState.Invalid
        }
        return RecordState.Valid(AuthRecord(enabled = enabled, verifier = verifier))
    }

    private data class AuthRecord(
        val enabled: Boolean,
        val verifier: String,
    ) {
        fun toJson(): JSONObject {
            return JSONObject()
                .put(KEY_VERSION, RECORD_VERSION)
                .put(KEY_ALGORITHM, BCRYPT_ALGORITHM)
                .put(KEY_COST, BCRYPT_COST)
                .put(KEY_ENABLED, enabled)
                .put(KEY_VERIFIER, verifier)
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
        const val MAXIMUM_RECORD_BYTES = 512L
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
