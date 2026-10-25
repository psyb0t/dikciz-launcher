package org.fossify.home.dikciz

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONArray
import org.json.JSONObject

internal class DikcizChatStore(context: Context) : SQLiteOpenHelper(context, DATABASE, null, VERSION) {
    init { setWriteAheadLoggingEnabled(true) }

    override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE profiles (id TEXT PRIMARY KEY, document TEXT NOT NULL, credential TEXT NOT NULL)")
        db.execSQL("CREATE TABLE conversations (id TEXT PRIMARY KEY, title TEXT NOT NULL, profile_id TEXT NOT NULL REFERENCES profiles(id), summary TEXT NOT NULL DEFAULT '', covered INTEGER NOT NULL DEFAULT 0, unread INTEGER NOT NULL DEFAULT 0, subscriptions TEXT NOT NULL DEFAULT '[]', settings TEXT NOT NULL DEFAULT '{}', updated INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE messages (id INTEGER PRIMARY KEY AUTOINCREMENT, conversation_id TEXT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE, turn_id TEXT NOT NULL, role TEXT NOT NULL, state TEXT NOT NULL, document TEXT NOT NULL DEFAULT '', blocks TEXT NOT NULL DEFAULT '[]', request_key TEXT UNIQUE)")
        db.execSQL("CREATE INDEX messages_conversation ON messages(conversation_id,id)")
        db.execSQL("CREATE INDEX messages_pending ON messages(state,id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion == 1 && newVersion == 2) {
            db.execSQL("ALTER TABLE conversations ADD COLUMN settings TEXT NOT NULL DEFAULT '{}'")
            return
        }
        error("Unsupported chat database upgrade: $oldVersion to $newVersion")
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
        return safe
    }

    fun createConversation(profileID: String, title: String, settings: JSONObject = JSONObject()): JSONObject {
        DikcizChatSettings.resolve(profile(profileID), settings)
        val id = UUID.randomUUID().toString()
        writableDatabase.insertOrThrow("conversations", null, ContentValues().apply {
            put("id", id); put("title", title); put("profile_id", profileID); put("settings", settings.toString()); put("updated", System.currentTimeMillis())
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
        patch.keys().forEach { name -> overrides.put(name, patch.get(name)) }
        DikcizChatSettings.resolve(profile(selected), overrides)
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
    fun enqueue(id: String, text: String, requestKey: String, source: String): JSONObject {
        conversation(id)
        val key = "$id:$requestKey"
        rows("SELECT id,turn_id,state,blocks FROM messages WHERE request_key=?", arrayOf(key)).firstOrNull()?.let {
            if (JSONArray(it.getString("blocks")).getJSONObject(0).getString("text") != text) {
                DikcizNativeCommands.failure("chat_request_conflict", "The request key already belongs to a different message")
            }
            it.remove("blocks")
            return it
        }
        val turnID = UUID.randomUUID().toString()
        val rowID = append(id, turnID, "user", "queued", "", JSONArray().put(JSONObject().put("kind", "text").put("text", text).put("source", source)), key)
        return JSONObject().put("id", rowID).put("turn_id", turnID).put("state", "queued")
    }

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
        const val VERSION = 2
        const val KEY_ALIAS = "dikciz-chat-credentials"
        const val CIPHER = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        const val IV_BYTES = 12
        val KEY_LOCK = Any()
    }
}
