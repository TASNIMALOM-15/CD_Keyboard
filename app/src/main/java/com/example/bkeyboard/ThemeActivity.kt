package com.example.bkeyboard

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File

class ThemeActivity : Activity() {
    private lateinit var col: LinearLayout

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        title = "Themes & Fonts"
        val sv = ScrollView(this)
        col = LinearLayout(this); col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(12), dp(12), dp(12), dp(12))
        sv.addView(col)
        setContentView(sv)
        render()
    }

    private fun btn(t: String, a: () -> Unit) {
        val b = Button(this); b.text = t; b.setOnClickListener { a() }; col.addView(b)
    }

    private fun pick(type: String, code: Int) {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(type), code)
    }

    private fun render() {
        col.removeAllViews()
        col.addView(EditText(this).apply { hint = "Preview: এখানে টাইপ করুন" })
        btn("Choose background image") { pick("image/*", 4) }
        btn("Remove background image") { Prefs.sp(this).edit().putBoolean("custom_bg", false).apply(); toast("Removed") }
        col.addView(TextView(this).apply { text = "FONTS"; setPadding(0, dp(16), 0, dp(4)) })
        btn("Import font (TTF / OTF)") { pick("*/*", 5) }
        btn("Use default font") { Prefs.sp(this).edit().putString("font", "").apply(); toast("Default font") }
        val dir = File(filesDir, "fonts")
        dir.listFiles()?.sortedBy { it.name }?.forEach { f ->
            val tv = TextView(this)
            tv.text = "${f.name}\nআমি বাংলায় গান গাই / The quick brown fox"
            tv.textSize = 18f
            try { tv.typeface = Typeface.createFromFile(f) } catch (e: Exception) {}
            tv.setPadding(dp(8), dp(8), dp(8), dp(8))
            tv.setOnClickListener {
                Prefs.sp(this).edit().putString("font", f.name).apply(); toast("Using ${f.name}")
            }
            tv.setOnLongClickListener { f.delete(); render(); true }
            col.addView(tv)
        }
        col.addView(TextView(this).apply { text = "ট্যাপ = ব্যবহার করুন, লং-প্রেস = মুছুন"; textSize = 12f })
        col.addView(TextView(this).apply { text = "THEMES (${Themes.all.size})"; setPadding(0, dp(16), 0, dp(4)) })
        Themes.all.forEachIndexed { i, t ->
            val tv = TextView(this)
            tv.text = "  ${t.name}"
            tv.setTextColor(t.text)
            tv.textSize = 16f
            tv.gravity = android.view.Gravity.CENTER_VERTICAL
            val g = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(t.bg1, t.bg2, t.key))
            g.cornerRadius = if (t.style == 1) 999f else dp(8).toFloat()
            if (t.style == 2) g.setStroke(dp(2), t.key)
            tv.background = g
            val lp = LinearLayout.LayoutParams(-1, dp(48)); lp.setMargins(0, dp(4), 0, dp(4))
            tv.layoutParams = lp
            tv.setOnClickListener { Prefs.sp(this).edit().putInt("theme", i).apply(); toast("Theme: ${t.name}") }
            col.addView(tv)
        }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        val uri: Uri = data?.data ?: return
        if (res != RESULT_OK) return
        try {
            if (req == 4) {
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
                var s = 1
                while (o.outWidth / s > 1440) s *= 2
                val o2 = BitmapFactory.Options().apply { inSampleSize = s }
                val bmp = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o2) }
                if (bmp == null) { toast("Image error"); return }
                File(filesDir, "bg.jpg").outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
                Prefs.sp(this).edit().putBoolean("custom_bg", true).apply()
                toast("Background set")
            } else if (req == 5) {
                var name = "font_${System.currentTimeMillis()}.ttf"
                contentResolver.query(uri, null, null, null, null)?.use { c ->
                    if (c.moveToFirst()) { val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME); if (i >= 0) name = c.getString(i) }
                }
                val dir = File(filesDir, "fonts"); dir.mkdirs()
                val out = File(dir, name.replace("/", "_"))
                contentResolver.openInputStream(uri)?.use { i -> out.outputStream().use { o -> i.copyTo(o) } }
                try { Typeface.createFromFile(out) } catch (e: Exception) { out.delete(); toast("Invalid font file"); return }
                Prefs.sp(this).edit().putString("font", out.name).apply()
                toast("Font imported"); render()
            }
        } catch (e: Exception) { toast("Error: ${e.message}") }
    }
}
