package org.fossify.home.dikciz

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.Executors
import org.json.JSONException
import org.json.JSONObject

internal class DikcizApkInstaller(
    private val context: Context,
    private val logger: DikcizLogger,
) {
    @Synchronized
    @Suppress("DEPRECATION")
    fun install(request: JSONObject): JSONObject {
        val file = DikcizFileCommands.file(request)
        if (!file.isFile || !file.canRead()) {
            throw DikcizFileCommands.failure(DikcizFileCommands.ERROR_NOT_FILE)
        }
        val size = file.length()
        if (size !in MINIMUM_APK_BYTES..MAXIMUM_APK_BYTES) {
            throw failure(ERROR_INVALID_APK)
        }
        FileInputStream(file).use { input ->
            val signature = ByteArray(ZIP_SIGNATURE.size)
            if (input.read(signature) != signature.size || !signature.contentEquals(ZIP_SIGNATURE)) {
                throw failure(ERROR_INVALID_APK)
            }
        }
        val packageInfo = context.packageManager.getPackageArchiveInfo(file.path, 0)
            ?: throw failure(ERROR_INVALID_APK)
        if (!context.packageManager.canRequestPackageInstalls()) {
            logger.info(EVENT_PERMISSION_REQUESTED)
            return JSONObject()
                .put(KEY_STATE, STATE_PERMISSION_REQUIRED)
                .put(KEY_PACKAGE_NAME, packageInfo.packageName)
                .put(KEY_SETTINGS_OPENED, false)
        }
        val installer = context.packageManager.packageInstaller
        if (installer.mySessions.size >= MAXIMUM_ACTIVE_SESSIONS) throw failure(ERROR_INSTALL_BUSY)
        val parameters = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setSize(size)
            setAppPackageName(packageInfo.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
            }
        }
        val sessionID = installer.createSession(parameters)
        val state = JSONObject()
            .put(DikcizFileCommands.KEY_SESSION_ID, sessionID)
            .put(KEY_PACKAGE_NAME, packageInfo.packageName)
            .put(KEY_STATE, STATE_PREPARING)
            .put(KEY_UPDATED_AT, System.currentTimeMillis())
        try {
            save(context, sessionID, state, logger)
        } catch (exception: DikcizAutomationRequestException) {
            installer.abandonSession(sessionID)
            throw exception
        }
        val expectedChecksum = request.optString(DikcizFileCommands.KEY_SHA256).takeIf(String::isNotEmpty)
        stagingWorkers.execute { stage(file, size, sessionID, expectedChecksum, state) }
        logger.info(EVENT_INSTALL_PREPARING, mapOf(DikcizFileCommands.KEY_SESSION_ID to sessionID))
        return state
    }

    private fun stage(file: File, size: Long, sessionID: Int, expectedChecksum: String?, initial: JSONObject) {
        val installer = context.packageManager.packageInstaller
        var committed = false
        try {
            installer.openSession(sessionID).use { session ->
                val digest = MessageDigest.getInstance(SHA256_ALGORITHM)
                var copied = 0L
                FileInputStream(file).use { input ->
                    session.openWrite(APK_SESSION_FILE_NAME, 0L, size).use { output ->
                        val buffer = ByteArray(DikcizFileCommands.DEFAULT_CHUNK_BYTES)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            copied += count
                            if (copied > size || copied > MAXIMUM_APK_BYTES) throw failure(ERROR_INVALID_APK)
                            digest.update(buffer, 0, count)
                            output.write(buffer, 0, count)
                        }
                        if (copied != size) throw failure(ERROR_INVALID_APK)
                        session.fsync(output)
                    }
                }
                val checksum = digest.digest().joinToString(EMPTY_STRING) { byte -> HEX_BYTE_FORMAT.format(byte) }
                if (expectedChecksum != null && expectedChecksum.lowercase(Locale.ROOT) != checksum) {
                    throw failure(ERROR_CHECKSUM_MISMATCH)
                }
                val state = JSONObject(initial.toString())
                    .put(DikcizFileCommands.KEY_SHA256, checksum)
                    .put(KEY_BYTES, copied)
                    .put(KEY_STATE, STATE_SUBMITTED)
                    .put(KEY_UPDATED_AT, System.currentTimeMillis())
                save(context, sessionID, state, logger)
                val callback = Intent(context, DikcizApkInstallReceiver::class.java)
                    .setAction(ACTION_INSTALL_STATUS)
                    .putExtra(EXTRA_SESSION_ID, sessionID)
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
                val pendingIntent = PendingIntent.getBroadcast(context, sessionID, callback, flags)
                session.commit(pendingIntent.intentSender)
                committed = true
                logger.info(EVENT_INSTALL_SUBMITTED, mapOf(DikcizFileCommands.KEY_SESSION_ID to sessionID))
            }
        } catch (exception: Exception) {
            // Binder and staging failures must leave a terminal, pollable install outcome.
            val code = (exception as? DikcizAutomationRequestException)?.code ?: ERROR_STAGING_FAILED
            logger.error(EVENT_STAGING_FAILED, mapOf(KEY_REASON to code, DikcizFileCommands.KEY_SESSION_ID to sessionID))
            val state = JSONObject(initial.toString())
                .put(KEY_STATE, STATE_FAILED)
                .put(KEY_REASON, code)
                .put(KEY_UPDATED_AT, System.currentTimeMillis())
            try {
                save(context, sessionID, state, logger)
            } catch (saveException: DikcizAutomationRequestException) {
                logger.error(EVENT_STATUS_WRITE_FAILED)
            }
        } finally {
            if (!committed) {
                try {
                    installer.abandonSession(sessionID)
                } catch (exception: IllegalArgumentException) {
                    logger.warn(EVENT_SESSION_ABANDON_FAILED)
                } catch (exception: SecurityException) {
                    logger.warn(EVENT_SESSION_ABANDON_FAILED)
                }
            }
        }
    }

    fun status(request: JSONObject): JSONObject {
        return load(context, request.getInt(DikcizFileCommands.KEY_SESSION_ID), logger)
            ?: throw failure(ERROR_UNKNOWN_INSTALL)
    }

    companion object {
        const val ACTION_INSTALL_STATUS = "org.fossify.home.dikciz.APK_INSTALL_STATUS"
        const val EXTRA_SESSION_ID = "org.fossify.home.dikciz.apk.sessionId"
        const val KEY_STATE = "state"
        const val KEY_PACKAGE_NAME = "packageName"
        const val KEY_STATUS_CODE = "statusCode"
        const val KEY_MESSAGE = "message"
        const val KEY_UPDATED_AT = "updatedAtMilliseconds"
        const val STATE_PENDING_USER_ACTION = "pending_user_action"
        const val STATE_SUCCEEDED = "succeeded"
        const val STATE_FAILED = "failed"
        const val STATE_CANCELLED = "cancelled"
        private val stagingWorkers = Executors.newFixedThreadPool(MAXIMUM_ACTIVE_SESSIONS) { action ->
            Thread(action, STAGING_THREAD_NAME).apply { isDaemon = true }
        }
        private const val STAGING_THREAD_NAME = "dikciz-apk-staging"
        private const val STATE_PREPARING = "preparing"
        private const val KEY_REASON = "reason"
        private const val ERROR_STAGING_FAILED = "apk_staging_failed"
        private const val EVENT_INSTALL_PREPARING = "apk_install_preparing"
        private const val EVENT_STAGING_FAILED = "apk_staging_failed"
        private const val EVENT_STATUS_WRITE_FAILED = "apk_status_write_failed"
        private const val EVENT_PERMISSION_REQUESTED = "apk_install_permission_requested"
        private const val EVENT_INSTALL_SUBMITTED = "apk_install_submitted"
        private const val EVENT_SESSION_ABANDON_FAILED = "apk_install_abandon_failed"
        private const val EVENT_RECORD_CORRUPT = "apk_install_record_corrupt"
        const val EVENT_INSTALL_STATUS = "apk_install_status"
        const val EVENT_CONFIRMATION_FAILED = "apk_install_confirmation_failed"
        private const val PREFERENCES_NAME = "dikciz-apk-installs"
        private const val MINIMUM_APK_BYTES = 4L
        private const val MAXIMUM_APK_BYTES = 512L * 1024 * 1024
        private const val MAXIMUM_ACTIVE_SESSIONS = 2
        private const val MAXIMUM_STORED_SESSIONS = 64
        private const val MAXIMUM_MESSAGE_CHARACTERS = 1024
        private const val KEY_BYTES = "bytes"
        private const val KEY_SETTINGS_OPENED = "settingsRequested"
        private const val STATE_SUBMITTED = "submitted"
        private const val STATE_PERMISSION_REQUIRED = "permission_required"
        private const val PACKAGE_URI_PREFIX = "package:"
        private const val APK_SESSION_FILE_NAME = "base.apk"
        private const val SHA256_ALGORITHM = "SHA-256"
        private const val HEX_BYTE_FORMAT = "%02x"
        private const val EMPTY_STRING = ""
        private const val ERROR_INVALID_APK = "invalid_apk"
        private const val ERROR_INSTALL_BUSY = "apk_install_busy"
        private const val ERROR_CHECKSUM_MISMATCH = "apk_checksum_mismatch"
        private const val ERROR_UNKNOWN_INSTALL = "apk_install_not_found"
        private const val ERROR_STATE_WRITE = "apk_install_status_write_failed"
        private const val MESSAGE_INSTALL_FAILED = "APK installation request failed"
        private val ZIP_SIGNATURE = byteArrayOf(0x50, 0x4b, 0x03, 0x04)

        private fun failure(code: String): DikcizAutomationRequestException {
            return DikcizAutomationRequestException(code, MESSAGE_INSTALL_FAILED)
        }

        @Synchronized
        fun save(context: Context, sessionID: Int, state: JSONObject, logger: DikcizLogger) {
            val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            val edit = preferences.edit()
            val oldest = preferences.all.entries.sortedBy { entry ->
                parseRecord(entry.value as? String, logger)?.optLong(KEY_UPDATED_AT) ?: 0L
            }
            val additional = if (preferences.contains(sessionID.toString())) 0 else 1
            val overflow = (oldest.size + additional - MAXIMUM_STORED_SESSIONS).coerceAtLeast(0)
            oldest.take(overflow).forEach { entry -> edit.remove(entry.key) }
            if (!edit.putString(sessionID.toString(), state.toString()).commit()) throw failure(ERROR_STATE_WRITE)
        }

        fun load(context: Context, sessionID: Int, logger: DikcizLogger): JSONObject? {
            val record = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
                .all[sessionID.toString()] as? String
            return parseRecord(record, logger)
        }

        private fun parseRecord(record: String?, logger: DikcizLogger): JSONObject? {
            if (record == null) return null
            return try {
                JSONObject(record)
            } catch (exception: JSONException) {
                logger.warn(EVENT_RECORD_CORRUPT)
                null
            }
        }

        fun updateFromCallback(context: Context, intent: Intent, logger: DikcizLogger) {
            val sessionID = intent.getIntExtra(EXTRA_SESSION_ID, -1)
            val state = load(context, sessionID, logger) ?: return
            val code = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
            val outcome = when (code) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> STATE_PENDING_USER_ACTION
                PackageInstaller.STATUS_SUCCESS -> STATE_SUCCEEDED
                PackageInstaller.STATUS_FAILURE_ABORTED -> STATE_CANCELLED
                else -> STATE_FAILED
            }
            state.put(KEY_STATE, outcome)
                .put(KEY_STATUS_CODE, code)
                .put(KEY_UPDATED_AT, System.currentTimeMillis())
                .put(KEY_MESSAGE, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    ?.take(MAXIMUM_MESSAGE_CHARACTERS) ?: JSONObject.NULL)
            save(context, sessionID, state, logger)
            logger.info(EVENT_INSTALL_STATUS, mapOf(DikcizFileCommands.KEY_SESSION_ID to sessionID, KEY_STATE to outcome))
            if (code != PackageInstaller.STATUS_PENDING_USER_ACTION) return
            @Suppress("DEPRECATION")
            val confirmation = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
            if (confirmation == null) {
                logger.error(EVENT_CONFIRMATION_FAILED)
                return
            }
            try {
                context.startActivity(confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (exception: android.content.ActivityNotFoundException) {
                logger.error(EVENT_CONFIRMATION_FAILED, mapOf(KEY_STATE to exception.javaClass.simpleName))
            } catch (exception: SecurityException) {
                logger.error(EVENT_CONFIRMATION_FAILED, mapOf(KEY_STATE to exception.javaClass.simpleName))
            }
        }
    }
}

class DikcizApkInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DikcizApkInstaller.ACTION_INSTALL_STATUS) return
        val store = HomeConfigStore(context)
        val logger = DikcizLogger(LOG_COMPONENT, store.logDirectory)
        try {
            logger.configure(store.readLoggingConfiguration())
        } catch (exception: HomeConfigException) {
            logger.warn(EVENT_LOG_CONFIGURATION_UNAVAILABLE, mapOf(FIELD_ERROR_CLASS to exception.javaClass.simpleName))
        }
        try {
            DikcizApkInstaller.updateFromCallback(context, intent, logger)
        } catch (exception: DikcizAutomationRequestException) {
            logger.error(EVENT_CALLBACK_FAILED, mapOf(FIELD_REASON to exception.code))
        }
    }

    companion object {
        private const val LOG_COMPONENT = "apk_installer"
        private const val EVENT_LOG_CONFIGURATION_UNAVAILABLE = "apk_log_configuration_unavailable"
        private const val EVENT_CALLBACK_FAILED = "apk_install_callback_failed"
        private const val FIELD_ERROR_CLASS = "error_class"
        private const val FIELD_REASON = "reason"
    }
}
