package com.example.bkeyboard

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

data class Clip(val id: Long, val text: String, val folder: Long, val tags: String, val fav: Boolean, val pin: Boolean, val created: Long)
data class Folder(val id: Long, val name: String, val parent: Long, val locked: Boolean)

/** Unlimited local clipboard history. Nothing is ever auto-deleted. */
class ClipDb(ctx: Context) : SQLiteOpenHelper(ctx, "clips.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE clips(id INTEGER PRIMARY KEY AUTOINCREMENT, text TEXT NOT NULL, folder INTEGER NOT NULL DEFAULT 0, tags TEXT NOT NULL DEFAULT '', fav INTEGER NOT NULL DEFAULT 0, pin INTEGER NOT NULL DEFAULT 0, created INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE folders(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, parent INTEGER NOT NULL DEFAULT 0, locked INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE INDEX idx_created ON clips(created)")
    }

    // Future versions must only ALTER/ADD here, never drop data.
    override fun onUpgrade(db: SQLiteDatabase, o: Int, n: Int) {}

    fun add(text: String) {
        val d = writableDatabase
        val c = d.rawQuery("SELECT id FROM clips WHERE text=? LIMIT 1", arrayOf(text))
        val id = if (c.moveToFirst()) c.getLong(0) else -1L
        c.close()
        if (id >= 0) d.execSQL("UPDATE clips SET created=? WHERE id=?", arrayOf<Any>(System.currentTimeMillis(), id))
        else {
            val v = ContentValues()
            v.put("text", text); v.put("created", System.currentTimeMillis())
            d.insert("clips", null, v)
        }
    }

    private fun read(c: android.database.Cursor): Clip =
        Clip(c.getLong(c.getColumnIndexOrThrow("id")), c.getString(c.getColumnIndexOrThrow("text")),
            c.getLong(c.getColumnIndexOrThrow("folder")), c.getString(c.getColumnIndexOrThrow("tags")),
            c.getInt(c.getColumnIndexOrThrow("fav")) == 1, c.getInt(c.getColumnIndexOrThrow("pin")) == 1,
            c.getLong(c.getColumnIndexOrThrow("created")))

    /** folder == null -> all (excluding private/locked folders). */
    fun list(q: String = "", folder: Long? = null, sort: Int = 0, limit: Int = 1000, favOnly: Boolean = false): List<Clip> {
        val where = ArrayList<String>()
        val args = ArrayList<String>()
        if (folder != null) { where += "folder=?"; args += folder.toString() }
        else where += "folder NOT IN (SELECT id FROM folders WHERE locked=1)"
        if (favOnly) where += "fav=1"
        if (q.isNotBlank()) { where += "(text LIKE ? OR tags LIKE ?)"; args += "%$q%"; args += "%$q%" }
        val order = "pin DESC, " + when (sort) { 1 -> "created ASC"; 2 -> "text COLLATE NOCASE ASC"; else -> "created DESC" }
        val out = ArrayList<Clip>()
        readableDatabase.query("clips", null, where.joinToString(" AND "),
            if (args.isEmpty()) null else args.toTypedArray(), null, null, order, limit.toString()
        ).use { c -> while (c.moveToNext()) out += read(c) }
        return out
    }

    private fun allClips(): List<Clip> {
        val out = ArrayList<Clip>()
        readableDatabase.query("clips", null, null, null, null, null, "id ASC").use { c -> while (c.moveToNext()) out += read(c) }
        return out
    }

    fun setFlag(id: Long, col: String, v: Boolean) { writableDatabase.execSQL("UPDATE clips SET $col=? WHERE id=?", arrayOf<Any>(if (v) 1 else 0, id)) }
    fun setFolder(id: Long, f: Long) { writableDatabase.execSQL("UPDATE clips SET folder=? WHERE id=?", arrayOf<Any>(f, id)) }
    fun setTags(id: Long, t: String) { writableDatabase.execSQL("UPDATE clips SET tags=? WHERE id=?", arrayOf<Any>(t, id)) }
    fun delete(id: Long) { writableDatabase.execSQL("DELETE FROM clips WHERE id=?", arrayOf<Any>(id)) }
    fun deleteUnmarked() { writableDatabase.execSQL("DELETE FROM clips WHERE pin=0 AND fav=0") }

    fun folders(): List<Folder> {
        val out = ArrayList<Folder>()
        readableDatabase.query("folders", null, null, null, null, null, "name COLLATE NOCASE").use { c ->
            while (c.moveToNext()) out += Folder(c.getLong(0), c.getString(1), c.getLong(2), c.getInt(3) == 1)
        }
        return out
    }

    fun addFolder(name: String, parent: Long, locked: Boolean): Long {
        val v = ContentValues(); v.put("name", name); v.put("parent", parent); v.put("locked", if (locked) 1 else 0)
        return writableDatabase.insert("folders", null, v)
    }

    fun deleteFolder(id: Long) {
        val d = writableDatabase
        d.execSQL("UPDATE clips SET folder=0 WHERE folder=?", arrayOf<Any>(id))
        d.execSQL("UPDATE folders SET parent=0 WHERE parent=?", arrayOf<Any>(id))
        d.execSQL("DELETE FROM folders WHERE id=?", arrayOf<Any>(id))
    }

    fun exportJson(): String {
        val o = JSONObject(); o.put("version", 1)
        val fa = JSONArray()
        for (f in folders()) fa.put(JSONObject().put("id", f.id).put("name", f.name).put("parent", f.parent).put("locked", f.locked))
        val ca = JSONArray()
        for (c in allClips()) ca.put(JSONObject().put("text", c.text).put("folder", c.folder).put("tags", c.tags)
            .put("fav", c.fav).put("pin", c.pin).put("created", c.created))
        o.put("folders", fa); o.put("clips", ca)
        return o.toString()
    }

    fun importJson(s: String): Int {
        val o = JSONObject(s)
        val d = writableDatabase
        val map = HashMap<Long, Long>(); map[0L] = 0L
        val fa = o.optJSONArray("folders") ?: JSONArray()
        val existing = folders()
        d.beginTransaction()
        var n = 0
        try {
            for (i in 0 until fa.length()) {
                val f = fa.getJSONObject(i)
                val ex = existing.firstOrNull { it.name == f.getString("name") }
                map[f.getLong("id")] = ex?.id ?: addFolder(f.getString("name"), 0, f.optBoolean("locked"))
            }
            for (i in 0 until fa.length()) {
                val f = fa.getJSONObject(i)
                val newId = map[f.getLong("id")] ?: continue
                val p = map[f.optLong("parent", 0)] ?: 0L
                d.execSQL("UPDATE folders SET parent=? WHERE id=? AND parent=0", arrayOf<Any>(p, newId))
            }
            val ca = o.optJSONArray("clips") ?: JSONArray()
            for (i in 0 until ca.length()) {
                val c = ca.getJSONObject(i)
                val t = c.getString("text")
                val chk = d.rawQuery("SELECT 1 FROM clips WHERE text=? LIMIT 1", arrayOf(t))
                val dup = chk.moveToFirst(); chk.close()
                if (dup) continue
                val v = ContentValues()
                v.put("text", t); v.put("folder", map[c.optLong("folder", 0)] ?: 0L); v.put("tags", c.optString("tags", ""))
                v.put("fav", if (c.optBoolean("fav")) 1 else 0); v.put("pin", if (c.optBoolean("pin")) 1 else 0)
                v.put("created", c.optLong("created", System.currentTimeMillis()))
                d.insert("clips", null, v); n++
            }
            d.setTransactionSuccessful()
        } finally { d.endTransaction() }
        return n
    }
}
