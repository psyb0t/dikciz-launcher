package org.fossify.home.dikciz

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject

internal class DikcizCompanionStore(context: Context) : SQLiteOpenHelper(context.applicationContext, "dikciz-companion.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE objects (kind TEXT NOT NULL,id TEXT NOT NULL,document TEXT NOT NULL,PRIMARY KEY(kind,id))")
        db.execSQL("CREATE TABLE route_points (trip TEXT NOT NULL,sequence INTEGER NOT NULL,document TEXT NOT NULL,PRIMARY KEY(trip,sequence))")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    fun get(kind: String, id: String): JSONObject? =
        readableDatabase.rawQuery("SELECT document FROM objects WHERE kind=? AND id=?", arrayOf(kind, id)).use {
            if (it.moveToFirst()) JSONObject(it.getString(0)) else null
        }
    fun all(kind: String): List<JSONObject> =
        readableDatabase.rawQuery("SELECT document FROM objects WHERE kind=? ORDER BY id", arrayOf(kind)).use {
            val values = mutableListOf<JSONObject>()
            while (it.moveToNext()) values.add(JSONObject(it.getString(0)))
            values
        }
    fun put(kind: String, id: String, value: JSONObject) {
        val row = writableDatabase.insertWithOnConflict("objects", null, ContentValues().apply {
            put("kind", kind); put("id", id); put("document", value.toString())
        }, SQLiteDatabase.CONFLICT_REPLACE)
        if (row == -1L) throw android.database.sqlite.SQLiteException("Companion record could not be saved")
    }
    fun delete(kind: String, id: String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("objects", "kind=? AND id=?", arrayOf(kind, id))
            if (kind == "trip") db.delete("route_points", "trip=?", arrayOf(id))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun snapshotKinds(kinds: Set<String>): org.json.JSONArray {
        val rows = org.json.JSONArray()
        for (kind in kinds.sorted()) {
            readableDatabase.rawQuery("SELECT id,document FROM objects WHERE kind=? ORDER BY id", arrayOf(kind)).use { cursor ->
                while (cursor.moveToNext()) rows.put(JSONObject().put("kind", kind).put("id", cursor.getString(0))
                    .put("document", JSONObject(cursor.getString(1))))
            }
        }
        return rows
    }

    fun replaceKinds(kinds: Set<String>, rows: org.json.JSONArray) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            kinds.forEach { kind -> db.delete("objects", "kind=?", arrayOf(kind)) }
            repeat(rows.length()) { index ->
                val row = rows.getJSONObject(index)
                require(row.getString("kind") in kinds)
                put(row.getString("kind"), row.getString("id"), row.getJSONObject("document"))
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun addPoint(trip: String, sequence: Int, value: JSONObject, summary: JSONObject) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.insertOrThrow("route_points", null, ContentValues().apply {
                put("trip", trip); put("sequence", sequence); put("document", value.toString())
            })
            put("trip", trip, summary)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun points(trip: String, offset: Int, limit: Int): List<JSONObject> =
        readableDatabase.rawQuery("SELECT document FROM route_points WHERE trip=? ORDER BY sequence LIMIT ? OFFSET ?",
            arrayOf(trip, limit.toString(), offset.toString())).use {
            val values = mutableListOf<JSONObject>()
            while (it.moveToNext()) values.add(JSONObject(it.getString(0)))
            values
        }
}
