package com.example.bkeyboard

import android.app.Activity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView

class SettingsActivity : Activity() {
    private lateinit var list: LinearLayout

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        title = "Settings"
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(12), dp(8), dp(12), dp(8))
        val test = EditText(this); test.hint = "Live preview: এখানে টাইপ করুন"
        root.addView(test)
        val search = EditText(this); search.hint = "Search settings"
        root.addView(search)
        list = LinearLayout(this); list.orientation = LinearLayout.VERTICAL
        val sv = ScrollView(this); sv.addView(list)
        root.addView(sv, LinearLayout.LayoutParams(-1, 0, 1f))
        val reset = Button(this); reset.text = "Reset to default"
        reset.setOnClickListener { Prefs.sp(this).edit().clear().apply(); render("") }
        root.addView(reset)
        setContentView(root)
        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { render(s?.toString() ?: "") }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        render("")
    }

    private fun render(q: String) {
        list.removeAllViews()
        val sp = Prefs.sp(this)
        var lastCat = ""
        for (o in SettingsDef.all) {
            if (q.isNotEmpty() && !o.title.contains(q, true) && !o.cat.contains(q, true)) continue
            if (o.cat != lastCat) {
                lastCat = o.cat
                list.addView(TextView(this).apply { text = o.cat.uppercase(); textSize = 13f; setPadding(0, dp(16), 0, dp(4)) })
            }
            when (o) {
                is Toggle -> {
                    val s = Switch(this)
                    s.text = o.title
                    s.isChecked = sp.getBoolean(o.key, o.def)
                    s.setOnCheckedChangeListener { _, c -> sp.edit().putBoolean(o.key, c).apply() }
                    list.addView(s)
                }
                is Slider -> {
                    val tv = TextView(this)
                    val cur = sp.getInt(o.key, o.def)
                    tv.text = "${o.title}: $cur"
                    val sb = SeekBar(this)
                    sb.max = o.max - o.min
                    sb.progress = cur - o.min
                    sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                            tv.text = "${o.title}: ${p + o.min}"
                            if (fromUser) sp.edit().putInt(o.key, p + o.min).apply()
                        }
                        override fun onStartTrackingTouch(s: SeekBar?) {}
                        override fun onStopTrackingTouch(s: SeekBar?) {}
                    })
                    list.addView(tv); list.addView(sb)
                }
                is Choice -> {
                    list.addView(TextView(this).apply { text = o.title })
                    val s = Spinner(this)
                    s.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, o.options)
                    s.setSelection(sp.getInt(o.key, o.def))
                    s.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                            if (sp.getInt(o.key, o.def) != pos) sp.edit().putInt(o.key, pos).apply()
                        }
                        override fun onNothingSelected(p: AdapterView<*>?) {}
                    }
                    list.addView(s)
                }
            }
        }
    }
}
