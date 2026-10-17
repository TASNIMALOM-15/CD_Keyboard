package com.example.bkeyboard

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        title = "BanglaKeys"
        val sv = ScrollView(this)
        val l = LinearLayout(this)
        l.orientation = LinearLayout.VERTICAL
        l.setPadding(dp(16), dp(16), dp(16), dp(16))
        sv.addView(l)
        setContentView(sv)
        fun btn(t: String, a: () -> Unit) {
            val bt = Button(this); bt.text = t; bt.setOnClickListener { a() }; l.addView(bt)
        }
        l.addView(TextView(this).apply { text = "BanglaKeys — বাংলা/ইংরেজি কিবোর্ড + আনলিমিটেড Clipboard"; textSize = 18f })
        btn("১. কিবোর্ড চালু করুন (Enable)") { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        btn("২. কিবোর্ড বেছে নিন (Select)") { (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker() }
        btn("📋 Clipboard Manager") { startActivity(Intent(this, ClipboardActivity::class.java)) }
        btn("🎨 Themes, Background & Fonts") { startActivity(Intent(this, ThemeActivity::class.java)) }
        btn("⚙ Settings") { startActivity(Intent(this, SettingsActivity::class.java)) }
        btn("🌐 Translator (ইন্টারনেট লাগে)") { startActivity(Intent(this, TranslateActivity::class.java)) }
        l.addView(EditText(this).apply { hint = "এখানে টাইপ করে টেস্ট করুন" })
        l.addView(TextView(this).apply {
            textSize = 14f
            setPadding(0, dp(16), 0, 0)
            text = "কী কাজ করে / সীমাবদ্ধতা:\n" +
                "• ভয়েস টাইপিং: ফোনের Google Speech Services ব্যবহার করে। বাংলা অফলাইন কাজ করবে কি না নির্ভর করে ফোনে বাংলা অফলাইন ভাষা প্যাক ডাউনলোড করা আছে কি না (Google app → Settings → Voice → Offline speech recognition)। প্রথমবার মাইক বাটনে চাপলে মাইক্রোফোন পারমিশন চাইবে।\n" +
                "• Translator: ইন্টারনেট প্রয়োজন (MyMemory ফ্রি API)। শুধু আপনি 'Translate' চাপলে টেক্সট পাঠানো হয়।\n" +
                "• Floating keyboard Android IME-তে নির্ভরযোগ্যভাবে সম্ভব নয়, তাই নেই। Swipe typing নেই।\n" +
                "• Clipboard: কিবোর্ড চালু ও সিলেক্টেড থাকলে কপি করা টেক্সট সেভ হয় (Android 10+ সীমাবদ্ধতা)।"
        })
    }
}
