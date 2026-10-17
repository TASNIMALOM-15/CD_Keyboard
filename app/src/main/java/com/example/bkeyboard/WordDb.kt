package com.example.bkeyboard

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Local-only word frequency + next-word (bigram) learning. Nothing leaves the device. */
class WordDb(ctx: Context) : SQLiteOpenHelper(ctx, "words.db", null, 1) {
    private val seed = listOf(
        "আমি", "তুমি", "আমরা", "তোমরা", "সে", "তারা", "এই", "এটা", "ওটা", "কি", "কী", "কেন", "কখন", "কোথায়",
        "কীভাবে", "ভালো", "খারাপ", "হ্যাঁ", "না", "ধন্যবাদ", "স্বাগতম", "বাংলাদেশ", "বাংলা", "ঢাকা", "আজ", "কাল",
        "এখন", "পরে", "আগে", "সাথে", "জন্য", "থেকে", "করে", "হবে", "আছে", "নেই", "যাব", "আসব", "খাব", "ভাই",
        "বোন", "বন্ধু", "মা", "বাবা", "সময়", "কাজ", "বাড়ি", "স্কুল", "অফিস", "টাকা", "পানি", "খাবার", "শুভ",
        "সকাল", "রাত", "দিন", "অনেক", "একটু", "আবার", "ঠিক", "আছি", "আপনি", "আপনার", "আমার", "তোমার",
        "hello", "thanks", "please", "good", "morning", "night", "the", "and", "you", "how", "are", "what"
    )

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE words(word TEXT PRIMARY KEY, freq INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE bigrams(prev TEXT, next TEXT, freq INTEGER NOT NULL, PRIMARY KEY(prev,next))")
        db.beginTransaction()
        try {
            for (w in seed) db.execSQL("INSERT OR IGNORE INTO words VALUES(?,1)", arrayOf<Any>(w))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    override fun onUpgrade(db: SQLiteDatabase, o: Int, n: Int) {}

    fun learn(w: String, prev: String) {
        if (w.length < 2) return
        val d = writableDatabase
        d.execSQL("INSERT OR IGNORE INTO words VALUES(?,0)", arrayOf<Any>(w))
        d.execSQL("UPDATE words SET freq=freq+1 WHERE word=?", arrayOf<Any>(w))
        if (prev.isNotEmpty()) {
            d.execSQL("INSERT OR IGNORE INTO bigrams VALUES(?,?,0)", arrayOf<Any>(prev, w))
            d.execSQL("UPDATE bigrams SET freq=freq+1 WHERE prev=? AND next=?", arrayOf<Any>(prev, w))
        }
    }

    fun suggest(prefix: String, n: Int): List<String> {
        val p = prefix.replace("%", "").replace("_", "")
        val out = ArrayList<String>()
        readableDatabase.rawQuery(
            "SELECT word FROM words WHERE word LIKE ? AND word<>? ORDER BY freq DESC LIMIT ?",
            arrayOf("$p%", prefix, n.toString())
        ).use { c -> while (c.moveToNext()) out += c.getString(0) }
        return out
    }

    fun next(prev: String, n: Int): List<String> {
        val out = ArrayList<String>()
        readableDatabase.rawQuery(
            "SELECT next FROM bigrams WHERE prev=? ORDER BY freq DESC LIMIT ?", arrayOf(prev, n.toString())
        ).use { c -> while (c.moveToNext()) out += c.getString(0) }
        return out
    }
}
