package com.example.bkeyboard

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Online translator (MyMemory free API). Text is sent ONLY when the Translate button is pressed. */
class TranslateActivity : Activity() {
    private val langs = listOf("Auto-detect" to "Autodetect", "বাংলা" to "bn", "English" to "en", "हिन्दी" to "hi",
        "العربية" to "ar", "اردو" to "ur", "Español" to "es", "Français" to "fr", "Deutsch" to "de", "中文" to "zh-CN", "日本語" to "ja")

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        title = "Translator"
        val l = LinearLayout(this); l.orientation = LinearLayout.VERTICAL; l.setPadding(dp(16), dp(16), dp(16), dp(16))
        val sv = ScrollView(this); sv.addView(l); setContentView(sv)
        l.addView(TextView(this).apply { text = "⚠ ইন্টারনেট প্রয়োজন। টেক্সট MyMemory সার্ভারে পাঠানো হয় (সর্বোচ্চ ৫০০ অক্ষর)।" })
        val input = EditText(this); input.hint = "Text"; input.minLines = 3
        try {
            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()?.let { input.setText(it.take(500)) }
        } catch (e: Exception) {}
        l.addView(input)
        val src = Spinner(this); src.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, langs.map { it.first })
        val dst = Spinner(this); dst.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, langs.drop(1).map { it.first })
        dst.setSelection(1)
        l.addView(TextView(this).apply { text = "From" }); l.addView(src)
        l.addView(TextView(this).apply { text = "To" }); l.addView(dst)
        val out = TextView(this); out.textSize = 18f; out.setTextIsSelectable(true); out.setPadding(0, dp(16), 0, dp(16))
        val go = Button(this); go.text = "Translate"
        go.setOnClickListener {
            val q = input.text.toString().take(500)
            if (q.isBlank()) return@setOnClickListener
            val pair = langs[src.selectedItemPosition].second + "%7C" + langs.drop(1)[dst.selectedItemPosition].second
            out.text = "..."
            Thread {
                val res = try {
                    val u = URL("https://api.mymemory.translated.net/get?q=" + URLEncoder.encode(q, "UTF-8") + "&langpair=" + pair)
                    val c = u.openConnection() as HttpURLConnection
                    c.connectTimeout = 10000; c.readTimeout = 15000
                    val body = c.inputStream.bufferedReader().use { it.readText() }
                    JSONObject(body).getJSONObject("responseData").getString("translatedText")
                } catch (e: Exception) { "Error: ইন্টারনেট/সার্ভার সমস্যা (${e.message})" }
                runOnUiThread { out.text = res }
            }.start()
        }
        val copy = Button(this); copy.text = "Copy result"
        copy.setOnClickListener {
            (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("t", out.text))
            Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
        }
        l.addView(go); l.addView(out); l.addView(copy)
    }
}
