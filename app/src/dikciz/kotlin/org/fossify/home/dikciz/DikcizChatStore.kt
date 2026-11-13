package org.fossify.home.dikciz

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONArray
import org.json.JSONObject

internal class DikcizChatStore(private val context: Context) : SQLiteOpenHelper(context, DATABASE, null, VERSION) {
    private val modelLimits = DikcizChatModelLimits(context)

    init { setWriteAheadLoggingEnabled(true) }

    override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE profiles (id TEXT PRIMARY KEY, document TEXT NOT NULL, credential TEXT NOT NULL)")
        db.execSQL("CREATE TABLE conversations (id TEXT PRIMARY KEY, title TEXT NOT NULL, profile_id TEXT NOT NULL REFERENCES profiles(id), summary TEXT NOT NULL DEFAULT '', covered INTEGER NOT NULL DEFAULT 0, unread INTEGER NOT NULL DEFAULT 0, subscriptions TEXT NOT NULL DEFAULT '[]', settings TEXT NOT NULL DEFAULT '{}', updated INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE messages (id INTEGER PRIMARY KEY AUTOINCREMENT, conversation_id TEXT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE, turn_id TEXT NOT NULL, role TEXT NOT NULL, state TEXT NOT NULL, document TEXT NOT NULL DEFAULT '', blocks TEXT NOT NULL DEFAULT '[]', request_key TEXT UNIQUE)")
        db.execSQL("CREATE INDEX messages_conversation ON messages(conversation_id,id)")
        db.execSQL("CREATE INDEX messages_pending ON messages(state,id)")
        createModelCatalog(db)
        db.execSQL("CREATE TABLE backup_state (id INTEGER PRIMARY KEY, restore_id TEXT NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        check(oldVersion in 1..3 && newVersion == VERSION) { "Unsupported chat database upgrade: $oldVersion to $newVersion" }
        if (oldVersion < 2) db.execSQL("ALTER TABLE conversations ADD COLUMN settings TEXT NOT NULL DEFAULT '{}'")
        if (oldVersion < 3) createModelCatalog(db)
        if (oldVersion < 4) db.execSQL("CREATE TABLE backup_state (id INTEGER PRIMARY KEY, restore_id TEXT NOT NULL)")
    }

    private fun createModelCatalog(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE model_catalog (profile_id TEXT PRIMARY KEY REFERENCES profiles(id), endpoint TEXT NOT NULL, document TEXT NOT NULL)")
    }

    fun recoverInterruptedTurns() {
        writableDatabase.execSQL("UPDATE messages SET state='interrupted' WHERE state IN ('running','executing')")
    }

    fun profiles(): JSONArray = JSONArray(rows("SELECT id,document FROM profiles ORDER BY id").map {
        JSONObject(it.getString("document")).put("id", it.getString("id"))
    })

    fun profile(id: String): JSONObject {
        val row = rows("SELECT document,credential FROM profiles WHERE id=?", arrayOf(id)).firstOrNull()
            ?: DikcizNativeCommands.failure("chat_profile_missing", "Configure a provider profile first")
        return JSONObject(row.getString("document")).put("apiKey", decrypt(row.getString("credential")))
    }

    @Synchronized
    fun saveProfile(document: JSONObject): JSONObject {
        val safe = JSONObject(document.toString())
        val id = safe.optString("id").ifBlank { UUID.randomUUID().toString() }
        val previous = rows("SELECT document,credential FROM profiles WHERE id=?", arrayOf(id)).firstOrNull()
        val oldDocument = previous?.let { JSONObject(it.getString("document")) }
        val endpointChanged = oldDocument != null && (oldDocument.optString("baseUrl") != safe.optString("baseUrl") || oldDocument.optString("protocol") != safe.optString("protocol"))
        val credential = when {
            safe.has("apiKey") -> encrypt(safe.getString("apiKey"))
            endpointChanged -> encrypt("")
            else -> previous?.getString("credential") ?: encrypt("")
        }
        safe.remove("apiKey")
        safe.put("id", id)
        val values = ContentValues().apply { put("document", safe.toString()); put("credential", credential) }
        if (writableDatabase.update("profiles", values, "id=?", arrayOf(id)) == 0) {
            values.put("id", id)
            writableDatabase.insertOrThrow("profiles", null, values)
        }
        writableDatabase.delete("model_catalog", "profile_id=?", arrayOf(id))
        return safe
    }

    @Synchronized
    fun rememberModels(profileID: String, expectedEndpoint: String, models: JSONArray): JSONArray {
        val provider = profile(profileID)
        if (endpoint(provider) != expectedEndpoint) DikcizNativeCommands.failure("chat_profile_changed", "Provider changed during discovery. Refresh its models.")
        writableDatabase.insertWithOnConflict("model_catalog", null, ContentValues().apply {
            put("profile_id", profileID); put("endpoint", expectedEndpoint); put("document", models.toString())
        }, SQLiteDatabase.CONFLICT_REPLACE).also { check(it != -1L) { "Could not save model discovery" } }
        return JSONArray((0 until models.length()).map { index ->
            val model = models.getJSONObject(index)
            modelLimits.describe(provider.getString("protocol"), model.getString("id"), model)
        })
    }

    fun endpoint(provider: JSONObject): String {
        val keyFingerprint = MessageDigest.getInstance("SHA-256").digest(provider.optString("apiKey").toByteArray())
            .joinToString("") { "%02x".format(it) }
        return listOf(provider.getString("protocol"), provider.getString("baseUrl"), keyFingerprint).joinToString("\n")
    }

    fun limits(provider: JSONObject, model: String = provider.optString("model")): JSONObject {
        val cached = rows("SELECT document FROM model_catalog WHERE profile_id=? AND endpoint=?", arrayOf(provider.getString("id"), endpoint(provider)))
            .firstOrNull()?.let { JSONArray(it.getString("document")) }
        val discovered = cached?.let { data -> (0 until data.length()).map(data::getJSONObject).firstOrNull { it.getString("id") == model } }
        return modelLimits.describe(provider.getString("protocol"), model, discovered)
    }

    fun inputBudget(provider: JSONObject, output: Int): Int {
        val known = limits(provider)
        val context = minOf(provider.getInt("contextTokens"), DikcizChatModelLimits.positive(known.opt("contextTokens")) ?: provider.getInt("contextTokens"))
        val input = DikcizChatModelLimits.positive(known.opt("maxInputTokens")) ?: context
        return minOf(context - output, input) - provider.getInt("safetyMarginTokens")
    }

    fun createConversation(profileID: String, title: String, settings: JSONObject = JSONObject()): JSONObject {
        val provider = profile(profileID)
        val requested = JSONObject(settings.toString()).put("model", settings.opt("model") ?: provider.optString("model"))
        val selected = modelLimits.selection(provider, requested, limits(provider, requested.optString("model")))
        DikcizChatSettings.resolve(provider, selected)
        val id = UUID.randomUUID().toString()
        writableDatabase.insertOrThrow("conversations", null, ContentValues().apply {
            put("id", id); put("title", title); put("profile_id", profileID); put("settings", selected.toString()); put("updated", System.currentTimeMillis())
        })
        return conversation(id)
    }

    fun conversations(): List<JSONObject> = rows("SELECT id,title,profile_id,unread,updated,subscriptions FROM conversations ORDER BY updated DESC,id")

    fun conversation(id: String): JSONObject = rows("SELECT * FROM conversations WHERE id=?", arrayOf(id)).firstOrNull()
        ?: DikcizNativeCommands.failure("chat_conversation_missing", "Conversation does not exist")

    fun effectiveProfile(id: String, requireModel: Boolean = false): JSONObject {
        val conversation = conversation(id)
        return DikcizChatSettings.resolve(
            profile(conversation.getString("profile_id")), JSONObject(conversation.getString("settings")), requireModel,
        )
    }

    fun settings(id: String): JSONObject {
        val current = conversation(id)
        val profile = effectiveProfile(id)
        return JSONObject().put("settings", DikcizChatSettings.execution(profile))
            .put("overrides", JSONObject(current.getString("settings")))
            .put("profileId", current.getString("profile_id")).put("protocol", profile.getString("protocol"))
            .put("modelLimits", limits(profile))
    }

    @Synchronized
    fun configure(id: String, profileID: String?, patch: JSONObject): JSONObject {
        val current = conversation(id)
        val selected = profileID ?: current.getString("profile_id")
        val overrides = if (selected != current.getString("profile_id")) {
            JSONObject().put(DikcizChatSettings.KEY_READ_ONLY, effectiveProfile(id).getBoolean(DikcizChatSettings.KEY_READ_ONLY))
        } else {
            JSONObject(current.getString("settings"))
        }
        val provider = profile(selected)
        val initial = JSONObject(provider.toString())
        overrides.keys().forEach { name -> initial.put(name, overrides.get(name)) }
        val requested = modelLimits.selection(initial, patch, limits(provider, patch.optString("model", initial.optString("model"))))
        requested.keys().forEach { name -> overrides.put(name, requested.get(name)) }
        DikcizChatSettings.resolve(provider, overrides)
        updateConversation(id, ContentValues().apply { put("profile_id", selected); put("settings", overrides.toString()) })
        return conversation(id)
    }

    fun messages(id: String): List<JSONObject> {
        conversation(id)
        return rows("SELECT * FROM messages WHERE conversation_id=? ORDER BY id", arrayOf(id))
    }

    fun displayMessages(id: String): List<JSONObject> {
        conversation(id)
        return rows("SELECT id,conversation_id,turn_id,role,state,blocks,request_key FROM messages WHERE conversation_id=? ORDER BY id", arrayOf(id))
    }

    fun updateConversation(id: String, values: ContentValues) {
        conversation(id)
        values.put("updated", System.currentTimeMillis())
        writableDatabase.update("conversations", values, "id=?", arrayOf(id))
    }

    fun deleteConversation(id: String) {
        conversation(id)
        writableDatabase.delete("conversations", "id=?", arrayOf(id))
    }

    @Synchronized
    fun enqueue(id: String, text: String, requestKey: String, source: String, task: JSONObject? = null): JSONObject {
        conversation(id)
        val key = "$id:$requestKey"
        rows("SELECT id,turn_id,state,blocks FROM messages WHERE request_key=?", arrayOf(key)).firstOrNull()?.let {
            val previous = JSONArray(it.getString("blocks")).getJSONObject(0)
            if (previous.getString("text") != text || previous.optString("source") != source ||
                previous.optJSONObject(DikcizChatTasks.KEY_TASK)?.optString(DikcizChatTasks.KEY_KIND) != task?.optString(DikcizChatTasks.KEY_KIND)
            ) {
                DikcizNativeCommands.failure("chat_request_conflict", "The request key already belongs to a different message")
            }
            it.remove("blocks")
            return it
        }
        val turnID = UUID.randomUUID().toString()
        val rowID = append(id, turnID, "user", "queued", "", JSONArray().put(JSONObject().put("kind", "text").put("text", text).put("source", source).apply { DikcizAutomationDebugger.parent()?.let { put("automationParentId", it) }; task?.let { put(DikcizChatTasks.KEY_TASK, it) } }), key)
        return JSONObject().put("id", rowID).put("turn_id", turnID).put("state", "queued")
    }

    fun request(id: String, requestKey: String): JSONObject? =
        rows("SELECT id FROM messages WHERE request_key=?", arrayOf("$id:$requestKey")).firstOrNull()

    fun nextQueued(): JSONObject? = rows("SELECT * FROM messages WHERE state='queued' ORDER BY id LIMIT 1").firstOrNull()

    fun append(conversationID: String, turnID: String, role: String, state: String, document: String, blocks: JSONArray, requestKey: String? = null): Long {
        return writableDatabase.insertOrThrow("messages", null, ContentValues().apply {
            put("conversation_id", conversationID); put("turn_id", turnID); put("role", role); put("state", state)
            put("document", document); put("blocks", blocks.toString()); requestKey?.let { put("request_key", it) }
        })
    }

    fun updateMessage(id: Long, state: String, document: String? = null, blocks: JSONArray? = null) {
        writableDatabase.update("messages", ContentValues().apply {
            put("state", state); document?.let { put("document", it) }; blocks?.let { put("blocks", it.toString()) }
        }, "id=?", arrayOf(id.toString()))
    }

    fun markUnread(id: String) {
        writableDatabase.execSQL("UPDATE conversations SET unread=unread+1,updated=? WHERE id=?", arrayOf<Any>(System.currentTimeMillis(), id))
    }

    fun interruptTurn(turnID: String, state: String) {
        writableDatabase.execSQL("UPDATE messages SET state=? WHERE turn_id=? AND state IN ('running','executing','queued')", arrayOf(state, turnID))
    }

    @Synchronized
    fun exportBackup(includeKeys: Boolean): JSONObject {
        for ((table, maximum) in mapOf("profiles" to 128, "conversations" to 1000, "messages" to 10000)) {
            readableDatabase.rawQuery("SELECT COUNT(*) FROM " + table, null).use { cursor ->
                cursor.moveToFirst()
                if (cursor.getLong(0) > maximum) DikcizNativeCommands.failure("backup_too_large", "Chat history exceeds backup record capacity")
            }
        }
        readableDatabase.rawQuery("SELECT COALESCE(SUM(length(document) + length(blocks)), 0) FROM messages", null).use { cursor ->
            cursor.moveToFirst()
            if (cursor.getLong(0) > 32L * 1024 * 1024) DikcizNativeCommands.failure("backup_too_large", "Chat history exceeds backup byte capacity")
        }
        val profiles = profiles()
        if (includeKeys) for (index in 0 until profiles.length()) {
            val item = profiles.getJSONObject(index)
            item.put("apiKey", profile(item.getString("id")).optString("apiKey"))
        }
        return JSONObject().put("profiles", profiles)
            .put("conversations", JSONArray(rows("SELECT * FROM conversations ORDER BY id")))
            .put("messages", JSONArray(rows("SELECT * FROM messages ORDER BY id")))
    }

    fun validateBackup(document: JSONObject): JSONObject {
        if (document.keys().asSequence().toSet() != setOf("profiles", "conversations", "messages")) {
            DikcizNativeCommands.invalid("Unknown chat backup fields")
        }
        val result = JSONObject(document.toString())
        val profiles = result.getJSONArray("profiles")
        val conversations = result.getJSONArray("conversations")
        val messages = result.getJSONArray("messages")
        if (profiles.length() > 128 || conversations.length() > 1000 || messages.length() > 10000) {
            DikcizNativeCommands.invalid("Chat backup exceeds its record limits")
        }
        val profileIds = mutableSetOf<String>()
        for (index in 0 until profiles.length()) {
            val profile = DikcizChatSettings.validate(profiles.getJSONObject(index))
            if (!profileIds.add(profile.getString("id"))) DikcizNativeCommands.invalid("Duplicate provider ID")
            profiles.put(index, profile)
        }
        val conversationIds = mutableSetOf<String>()
        for (index in 0 until conversations.length()) {
            val row = conversations.getJSONObject(index)
            if (row.keys().asSequence().toSet() != CONVERSATION_BACKUP_FIELDS ||
                !BACKUP_ID.matches(row.getString("id")) || !conversationIds.add(row.getString("id")) ||
                row.getString("profile_id") !in profileIds || row.getString("title").length > 256 ||
                row.getString("summary").length > 1000000) DikcizNativeCommands.invalid("Invalid conversation backup")
            val subscriptions = JSONArray(row.getString("subscriptions"))
            if (subscriptions.length() > 16) DikcizNativeCommands.invalid("Too many restored event rules")
            val subscriptionIds = mutableSetOf<String>()
            for (ruleIndex in 0 until subscriptions.length()) {
                val rule = subscriptions.getJSONObject(ruleIndex)
                if (rule.keys().asSequence().any { it !in setOf("id", "subscription", "instructions", "routineId") } ||
                    !BACKUP_ID.matches(rule.getString("id")) || !subscriptionIds.add(rule.getString("id")) ||
                    rule.getString("instructions").isBlank() || rule.getString("instructions").length > 12000) {
                    DikcizNativeCommands.invalid("Invalid restored event rule")
                }
                if (rule.has("routineId") && rule.optString("routineId") !in DikcizAssistantRoutines.ids) DikcizNativeCommands.invalid("Invalid restored routine")
                val subscription = rule.getJSONObject("subscription")
                HomeConfigStore(context).parseHtmlEventSubscriptions(JSONObject().put("eventSubscriptions", JSONArray().put(subscription)),
                    HomeConfigStore(context).loadOrCreate().limits)
            }
            val profile = (0 until profiles.length()).map(profiles::getJSONObject).first { it.getString("id") == row.getString("profile_id") }
            DikcizChatSettings.resolve(profile, JSONObject(row.getString("settings")))
            for (key in listOf("covered", "unread", "updated")) {
                DikcizNativeCommands.strictInteger(row.opt(key), 0, Long.MAX_VALUE)
            }
        }
        val messageIds = mutableSetOf<Long>()
        val keys = mutableSetOf<String>()
        for (index in 0 until messages.length()) {
            val row = messages.getJSONObject(index)
            if (row.keys().asSequence().toSet() != MESSAGE_BACKUP_FIELDS ||
                row.getString("conversation_id") !in conversationIds ||
                row.getString("role") !in setOf("user", "assistant", "tool") ||
                !messageIds.add(DikcizNativeCommands.strictInteger(row.opt("id"), 1, Long.MAX_VALUE)) ||
                !BACKUP_ID.matches(row.getString("turn_id"))) DikcizNativeCommands.invalid("Invalid message backup")
            if (row.getString("state") !in setOf("queued", "running", "executing", "complete", "failed", "cancelled", "interrupted")) {
                DikcizNativeCommands.invalid("Invalid restored message state")
            }
            JSONArray(row.getString("blocks"))
            if (row.getString("document").length > 1000000 || row.getString("blocks").length > 1000000) {
                DikcizNativeCommands.invalid("Message backup is too large")
            }
            if (!row.isNull("request_key") && !keys.add(row.getString("request_key"))) DikcizNativeCommands.invalid("Duplicate message request key")
        }
        return result
    }

    @Synchronized
    fun restoreBackup(document: JSONObject, restoreID: String, activateEvents: Boolean) {
        val normalized = validateBackup(document)
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("messages", null, null)
            db.delete("conversations", null, null)
            db.delete("model_catalog", null, null)
            db.delete("profiles", null, null)
            val profiles = normalized.getJSONArray("profiles")
            for (index in 0 until profiles.length()) {
                val profile = JSONObject(profiles.getJSONObject(index).toString())
                if (!profile.has("apiKey")) profile.put("apiKey", "")
                saveProfile(profile)
            }
            for ((table, fields) in listOf("conversations" to CONVERSATION_BACKUP_FIELDS, "messages" to MESSAGE_BACKUP_FIELDS)) {
                val rows = normalized.getJSONArray(table)
                for (index in 0 until rows.length()) {
                    val row = rows.getJSONObject(index)
                    if (table == "conversations" && !activateEvents) row.put("subscriptions", "[]")
                    if (table == "messages" && row.optString("state") !in setOf("complete", "failed", "cancelled", "interrupted")) {
                        row.put("state", "interrupted")
                    }
                    val values = ContentValues()
                    for (key in fields) {
                        val value = row.opt(key)
                        when (value) {
                            null, JSONObject.NULL -> values.putNull(key)
                            is Number -> values.put(key, value.toLong())
                            else -> values.put(key, value.toString())
                        }
                    }
                    db.insertOrThrow(table, null, values)
                }
            }
            db.insertWithOnConflict("backup_state", null, ContentValues().apply {
                put("id", 1); put("restore_id", restoreID)
            }, SQLiteDatabase.CONFLICT_REPLACE)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun committedRestoreID(): String? = readableDatabase.rawQuery("SELECT restore_id FROM backup_state WHERE id = 1", null).use {
        if (it.moveToFirst()) it.getString(0) else null
    }

    private val CONVERSATION_BACKUP_FIELDS = setOf("id", "title", "profile_id", "summary", "covered", "unread", "updated", "subscriptions", "settings")
    private val MESSAGE_BACKUP_FIELDS = setOf("id", "conversation_id", "turn_id", "role", "state", "document", "blocks", "request_key")
    private val BACKUP_ID = Regex("[A-Za-z0-9][A-Za-z0-9_.-]{0,79}")

    private fun rows(sql: String, arguments: Array<String> = emptyArray()): List<JSONObject> {
        return readableDatabase.rawQuery(sql, arguments).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(JSONObject().apply {
                        cursor.columnNames.forEachIndexed { index, name ->
                            put(name, when (cursor.getType(index)) {
                                android.database.Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
                                android.database.Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
                                else -> cursor.getString(index)
                            })
                        }
                    })
                }
            }
        }
    }

    private fun encryptionKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    private fun encrypt(value: String): String = synchronized(KEY_LOCK) {
        val cipher = Cipher.getInstance(CIPHER)
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey())
        Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String = synchronized(KEY_LOCK) {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(CIPHER)
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), GCMParameterSpec(TAG_BITS, bytes.copyOfRange(0, IV_BYTES)))
        String(cipher.doFinal(bytes.copyOfRange(IV_BYTES, bytes.size)), Charsets.UTF_8)
    }

    private companion object {
        const val DATABASE = "dikciz-chat.sqlite"
        const val VERSION = 4
        const val KEY_ALIAS = "dikciz-chat-credentials"
        const val CIPHER = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        const val IV_BYTES = 12
        val KEY_LOCK = Any()
    }
}
