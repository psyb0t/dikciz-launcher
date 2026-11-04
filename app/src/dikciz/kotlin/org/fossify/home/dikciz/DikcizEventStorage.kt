package org.fossify.home.dikciz

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

internal class DikcizEventStorage(context: Context) : SQLiteOpenHelper(context, "dikciz-event-journal.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE journal_state (id INTEGER PRIMARY KEY, document TEXT NOT NULL)")
        db.execSQL("CREATE TABLE journal_entries (sequence INTEGER PRIMARY KEY, document TEXT NOT NULL)")
        db.execSQL("CREATE TABLE consumers (name TEXT PRIMARY KEY, cursor INTEGER NOT NULL)")
        db.insertOrThrow("journal_state", null, ContentValues().apply {
            put("id", 1)
            put("document", JSONObject().put("journalId", UUID.randomUUID().toString()).put("nextSequence", 1).toString())
        })
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun state(): JSONObject = readableDatabase.rawQuery("SELECT document FROM journal_state WHERE id = 1", null).use {
        check(it.moveToFirst())
        JSONObject(it.getString(0))
    }

    fun entries(): List<JSONObject> = readableDatabase.rawQuery("SELECT document FROM journal_entries ORDER BY sequence", null).use {
        val values = mutableListOf<JSONObject>()
        while (it.moveToNext()) values.add(JSONObject(it.getString(0)))
        values
    }

    fun save(state: JSONObject, entry: JSONObject? = null, firstSequence: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            if (entry != null) db.insertOrThrow("journal_entries", null, ContentValues().apply {
                put("sequence", entry.getLong("sequence")); put("document", entry.toString())
            })
            db.delete("journal_entries", "sequence < ?", arrayOf(firstSequence.toString()))
            db.update("journal_state", ContentValues().apply { put("document", state.toString()) }, "id = 1", null)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun cursor(name: String): Long = readableDatabase.rawQuery("SELECT cursor FROM consumers WHERE name = ?", arrayOf(name)).use {
        if (it.moveToFirst()) it.getLong(0) else 0L
    }

    fun acknowledge(name: String, cursor: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            if (cursor < cursor(name)) DikcizNativeCommands.failure("event_ack_regression", "Acknowledgement cannot move backwards")
            val count = db.rawQuery("SELECT COUNT(*) FROM consumers", null).use { it.moveToFirst(); it.getInt(0) }
            if (count >= MAXIMUM_CONSUMERS && !consumers().any { it.getString("name") == name }) {
                DikcizNativeCommands.failure("event_consumer_capacity", "At most 64 event consumers may exist")
            }
            db.insertWithOnConflict("consumers", null, ContentValues().apply { put("name", name); put("cursor", cursor) }, SQLiteDatabase.CONFLICT_REPLACE)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun consumers(): List<JSONObject> = readableDatabase.rawQuery("SELECT name, cursor FROM consumers ORDER BY name", null).use {
        val values = mutableListOf<JSONObject>()
        while (it.moveToNext()) values.add(JSONObject().put("name", it.getString(0)).put("cursor", it.getLong(1)))
        values
    }

    fun forget(name: String) { writableDatabase.delete("consumers", "name = ?", arrayOf(name)) }

    private companion object { const val MAXIMUM_CONSUMERS = 64 }
}
