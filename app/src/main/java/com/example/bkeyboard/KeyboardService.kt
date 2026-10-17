package com.example.bkeyboard

import android.Manifest
import android.content.ClipboardManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File

class KeyboardService : InputMethodService() {
    companion object { var instance: KeyboardService? = null }
    enum class Mode { EN, PH, BN, BNV, SYM, EMOJI, CLIP }

    private var mode = Mode.EN
    private var shift = false
    private val composing = StringBuilder()
    private val wordBuf = StringBuilder()
    private var lastWord = ""
    private lateinit var root: LinearLayout
    private lateinit var db: ClipDb
    private lateinit var wordDb: WordDb
    private val handler = Handler(Looper.getMainLooper())
    private var theme: KbTheme = Themes.all[0]
    private var tf: Typeface? = null
    private var gap = 0
    private var keyH = 0
    private var sugg: Array<TextView>? = null
    private val suggWords = arrayOf("", "", "")
    private val micViews = ArrayList<TextView>()
    private var recognizer: SpeechRecognizer? = null
    private var listening = false
    private var lastClip = ""
    private var bgCache: Bitmap? = null
    private var bgStamp = 0L
    private var repeatRunnable: Runnable? = null

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { captureClip() }
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> handler.post { rebuild() } }

    private val bnRows = listOf(
        listOf("ক", "খ", "গ", "ঘ", "ঙ", "চ", "ছ", "জ", "ঝ", "ঞ"),
        listOf("ট", "ঠ", "ড", "ঢ", "ণ", "ত", "থ", "দ", "ধ", "ন"),
        listOf("প", "ফ", "ব", "ভ", "ম", "য", "র", "ল", "শ", "ষ"),
        listOf("স", "হ", "ড়", "ঢ়", "য়", "ৎ", "ং", "ঃ", "ঁ", "্")
    )
    private val bnvRows = listOf(
        listOf("অ", "আ", "ই", "ঈ", "উ", "ঊ", "ঋ", "এ", "ঐ", "ও"),
        listOf("ঔ", "া", "ি", "ী", "ু", "ূ", "ৃ", "ে", "ৈ", "ো"),
        listOf("ৌ", "্", "ং", "ঃ", "ঁ", "ৎ", "।", ",", "?", "!")
    )
    private val emojis = ("😀 😃 😄 😁 😆 😅 😂 🤣 😊 😇 🙂 😉 😍 🥰 😘 😋 😛 😜 🤪 😎 🤩 🥳 😏 😒 😞 😔 😟 😕 🙁 😣 😖 😫 😩 🥺 😢 😭 😤 😠 😡 🤯 😳 🥵 🥶 😱 😨 😰 😥 😓 🤗 🤔 🤭 🤫 😶 😐 😑 😬 🙄 😯 😮 😲 🥱 😴 🤤 😪 😵 🤐 🥴 🤢 🤮 🤧 😷 🤒 🤕 👍 👎 👌 ✌️ 🤞 🤟 🤘 👏 🙌 🙏 💪 ❤️ 🧡 💛 💚 💙 💜 🖤 💔 🔥 ✨ 🎉 🎂 🌹 🌙 ⭐ ☀️ 🌧️ 🇧🇩").split(" ")

    override fun onCreate() {
        super.onCreate()
        instance = this
        db = ClipDb(this)
        wordDb = WordDb(this)
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).addPrimaryClipChangedListener(clipListener)
        Prefs.sp(this).registerOnSharedPreferenceChangeListener(prefListener)
    }

    override fun onDestroy() {
        instance = null
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).removePrimaryClipChangedListener(clipListener)
        Prefs.sp(this).unregisterOnSharedPreferenceChangeListener(prefListener)
        recognizer?.destroy()
        super.onDestroy()
    }

    override fun onEvaluateFullscreenMode() = false

    override fun onCreateInputView(): View {
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        rebuild()
        return root
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        composing.setLength(0); wordBuf.setLength(0)
        shift = false
        if (mode == Mode.EN && Prefs.bool(this, "auto_caps", true)) {
            val b = currentInputConnection?.getTextBeforeCursor(3, 0)?.toString() ?: ""
            if (b.isEmpty() || (b.endsWith(" ") && b.trimEnd(' ').endsWith("."))) shift = true
        }
        captureClip()
        rebuild()
    }

    override fun onFinishInput() {
        currentInputConnection?.finishComposingText()
        composing.setLength(0)
        super.onFinishInput()
    }

    // ---------- Clipboard capture ----------
    private fun captureClip() {
        try {
            if (!Prefs.bool(this, "clip_monitor", true)) return
            val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip = cm.primaryClip ?: return
            if (Prefs.bool(this, "skip_sensitive", true) &&
                clip.description.extras?.getBoolean("android.content.extra.IS_SENSITIVE") == true) return
            val t = clip.getItemAt(0)?.coerceToText(this)?.toString() ?: return
            if (t.isBlank() || t == lastClip) return
            lastClip = t
            db.add(t)
        } catch (e: Exception) { /* clipboard may be unavailable when not focused */ }
    }

    // ---------- UI building ----------
    private fun loadFont(): Typeface? {
        val f = Prefs.str(this, "font", "")
        if (f.isEmpty()) return null
        val file = File(filesDir, "fonts/$f")
        return try { if (file.exists()) Typeface.createFromFile(file) else null } catch (e: Exception) { null }
    }

    private fun customBg(): Drawable? {
        if (!Prefs.bool(this, "custom_bg", false)) return null
        val f = File(filesDir, "bg.jpg")
        if (!f.exists()) return null
        if (bgCache == null || bgStamp != f.lastModified()) {
            bgCache = BitmapFactory.decodeFile(f.path); bgStamp = f.lastModified()
        }
        val b = bgCache ?: return null
        val d = BitmapDrawable(resources, b)
        d.alpha = Prefs.int(this, "bg_alpha", 100) * 255 / 100
        return d
    }

    private fun rebuild() {
        if (!::root.isInitialized) return
        theme = Themes.all[Prefs.int(this, "theme", 2).coerceIn(0, Themes.all.size - 1)]
        tf = loadFont()
        gap = dp(Prefs.int(this, "key_gap", 3))
        keyH = dp(Prefs.int(this, "key_height", 46))
        root.background = customBg() ?: GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(theme.bg1, theme.bg2))
        val oh = Prefs.int(this, "one_hand", 0)
        val side = if (oh == 0) 0 else (resources.displayMetrics.widthPixels * 0.3f).toInt()
        root.setPadding(gap * 2 + (if (oh == 2) side else 0), gap * 2,
            gap * 2 + (if (oh == 1) side else 0), gap * 2 + dp(Prefs.int(this, "bottom_pad", 0)))
        root.removeAllViews()
        micViews.clear()
        if (Prefs.bool(this, "show_toolbar", true)) addToolbar()
        val typing = mode != Mode.EMOJI && mode != Mode.CLIP && mode != Mode.SYM
        if (typing && Prefs.bool(this, "show_suggest", true)) addSuggestBar() else sugg = null
        when (mode) {
            Mode.EN, Mode.PH -> buildLatin()
            Mode.BN -> buildBn(bnRows)
            Mode.BNV -> buildBn(bnvRows)
            Mode.SYM -> buildSym()
            Mode.EMOJI -> buildEmoji()
            Mode.CLIP -> buildClip()
        }
        updateSuggestions()
    }

    private fun keyDrawable(special: Boolean): GradientDrawable {
        val g = GradientDrawable()
        var base = theme.key
        if (special) base = Color.rgb((Color.red(base) * 0.75f).toInt(), (Color.green(base) * 0.75f).toInt(), (Color.blue(base) * 0.75f).toInt())
        if (theme.style == 2) { g.setColor(Color.TRANSPARENT); g.setStroke(dp(2), base) } else g.setColor(base)
        g.cornerRadius = if (theme.style == 1) 999f else dp(Prefs.int(this, "key_radius", 8)).toFloat()
        g.alpha = Prefs.int(this, "key_alpha", 85) * 255 / 100
        return g
    }

    private fun key(label: String, w: Float = 1f, special: Boolean = false, repeat: Boolean = false, action: () -> Unit): TextView {
        val tv = TextView(this)
        tv.text = label
        tv.gravity = Gravity.CENTER
        tv.setTextColor(theme.text)
        tv.textSize = Prefs.int(this, "text_size", 18).toFloat()
        tf?.let { tv.typeface = it }
        tv.background = keyDrawable(special)
        val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, w)
        lp.setMargins(gap / 2, gap / 2, gap / 2, gap / 2)
        tv.layoutParams = lp
        tv.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    pressFx(v, true); feedback()
                    action()
                    if (repeat) startRepeat(action)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { pressFx(v, false); stopRepeat() }
            }
            true
        }
        return tv
    }

    private fun startRepeat(a: () -> Unit) {
        stopRepeat()
        val r = object : Runnable { override fun run() { a(); handler.postDelayed(this, 50) } }
        repeatRunnable = r
        handler.postDelayed(r, 400)
    }

    private fun stopRepeat() { repeatRunnable?.let { handler.removeCallbacks(it) }; repeatRunnable = null }

    private fun pressFx(v: View, down: Boolean) {
        when (Prefs.int(this, "press_fx", 1)) {
            1 -> v.alpha = if (down) 0.6f else 1f
            2 -> { val s = if (down) 0.92f else 1f; v.scaleX = s; v.scaleY = s }
        }
    }

    @Suppress("DEPRECATION")
    private fun feedback() {
        if (Prefs.bool(this, "haptic", true)) {
            val ms = Prefs.int(this, "haptic_ms", 15)
            val vb = getSystemService(VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= 26) vb.vibrate(VibrationEffect.createOneShot(ms.toLong(), VibrationEffect.DEFAULT_AMPLITUDE))
            else vb.vibrate(ms.toLong())
        }
        if (Prefs.bool(this, "sound", false)) {
            (getSystemService(AUDIO_SERVICE) as AudioManager)
                .playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, Prefs.int(this, "sound_vol", 50) / 100f)
        }
    }

    private fun addRow(keys: List<View>, h: Int = keyH) {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        for (k in keys) r.addView(k)
        root.addView(r, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h))
    }

    private fun addToolbar() {
        val keys = ArrayList<View>()
        keys += key("📋") { mode = Mode.CLIP; rebuild() }
        keys += key("😀") { mode = Mode.EMOJI; rebuild() }
        keys += key(if (Prefs.int(this, "voice_lang", 0) == 0) "🎤বাং" else "🎤EN") { toggleVoiceLang() }
        keys += key("🌐") { openActivity(TranslateActivity::class.java) }
        keys += key("⚙") { openActivity(SettingsActivity::class.java) }
        addRow(keys, (keyH * 0.8f).toInt())
    }

    private fun openActivity(c: Class<*>) {
        startActivity(Intent(this, c).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun addSuggestBar() {
        val arr = Array(3) { i ->
            key("", 1f) { pickSuggestion(suggWords[i]) }.also { it.textSize = 16f }
        }
        sugg = arr
        addRow(arr.toList(), (keyH * 0.8f).toInt())
    }

    private fun buildLatin() {
        val rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        numberRow()
        addRow(rows[0].map { letterKey(it) })
        addRow(rows[1].map { letterKey(it) })
        val r3 = ArrayList<View>()
        r3 += key("⇧", 1.5f, true) { shift = !shift; rebuild() }
        rows[2].forEach { r3 += letterKey(it) }
        r3 += key("⌫", 1.5f, true, true) { backspace() }
        addRow(r3)
        bottomRow()
    }

    private fun letterKey(c: Char): TextView {
        val label = if (shift) c.uppercaseChar().toString() else c.toString()
        return key(label) { typeLetter(label) }
    }

    private fun numberRow() {
        if (!Prefs.bool(this, "number_row", true)) return
        val bn = Prefs.bool(this, "bn_digits", false)
        val lat = "1234567890"
        val ben = "১২৩৪৫৬৭৮৯০"
        addRow(lat.indices.map { i ->
            val s = if (bn) ben[i].toString() else lat[i].toString()
            key(s) { commitSymbol(s) }
        })
    }

    private fun buildBn(rows: List<List<String>>) {
        numberRow()
        for ((i, r) in rows.withIndex()) {
            val keys = ArrayList<View>()
            r.forEach { c -> keys += key(c) { typeBn(c) } }
            if (i == rows.size - 1) keys += key("⌫", 1.5f, true, true) { backspace() }
            addRow(keys)
        }
        bottomRow()
    }

    private fun buildSym() {
        numberRow()
        val rows = listOf("@#৳_&-+()/", "*\"':;!?%=,", "\\|~<>{}[]^")
        for (r in rows) addRow(r.map { ch -> key(ch.toString()) { commitSymbol(ch.toString()) } })
        val last = ArrayList<View>()
        last += key("ABC", 1.5f, true) { mode = Mode.EN; rebuild() }
        listOf("€", "£", "\$", "।", ",", ".").forEach { s -> last += key(s) { commitSymbol(s) } }
        last += key("⌫", 1.5f, true, true) { backspace() }
        addRow(last)
    }

    private fun buildEmoji() {
        val sv = ScrollView(this)
        val col = LinearLayout(this); col.orientation = LinearLayout.VERTICAL
        sv.addView(col)
        emojis.chunked(8).forEach { chunk ->
            val r = LinearLayout(this)
            chunk.forEach { e ->
                val t = TextView(this); t.text = e; t.textSize = 24f; t.gravity = Gravity.CENTER
                t.setOnClickListener { feedback(); commitSymbol(e) }
                r.addView(t, LinearLayout.LayoutParams(0, keyH, 1f))
            }
            col.addView(r)
        }
        root.addView(sv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, keyH * 4))
        addRow(listOf(key("ABC", 1.5f, true) { mode = Mode.EN; rebuild() }, key("⌫", 1.5f, true, true) { backspace() }))
    }

    private fun buildClip() {
        val sv = ScrollView(this)
        val col = LinearLayout(this); col.orientation = LinearLayout.VERTICAL
        sv.addView(col)
        val clips = db.list(limit = 60)
        if (clips.isEmpty()) {
            val t = TextView(this); t.text = "Clipboard খালি। কিছু কপি করুন।"; t.setTextColor(theme.text); t.setPadding(dp(12), dp(12), dp(12), dp(12))
            col.addView(t)
        }
        for (c in clips) {
            val t = TextView(this)
            t.text = (if (c.pin) "📌 " else "") + (if (c.fav) "⭐ " else "") + c.text.take(100).replace("\n", " ")
            t.setTextColor(theme.text); t.maxLines = 2
            t.setPadding(dp(12), dp(10), dp(12), dp(10))
            t.background = keyDrawable(false)
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.setMargins(0, gap, 0, gap)
            t.layoutParams = lp
            t.setOnClickListener { feedback(); commitSymbol(c.text) }
            col.addView(t)
        }
        root.addView(sv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, keyH * 4))
        addRow(listOf(
            key("ABC", 1.5f, true) { mode = Mode.EN; rebuild() },
            key("Manage", 2f, true) { openActivity(ClipboardActivity::class.java) },
            key("⌫", 1.5f, true, true) { backspace() }))
    }

    private fun bottomRow() {
        val langLabel = when (mode) { Mode.EN -> "EN"; Mode.PH -> "ফো"; else -> "বাং" }
        val keys = ArrayList<View>()
        keys += key("?123", 1.3f, true) { mode = Mode.SYM; rebuild() }
        keys += key(langLabel, 1.2f, true) { cycleLang() }
        if (mode == Mode.BN) keys += key("স্বর", 1.2f, true) { mode = Mode.BNV; rebuild() }
        if (mode == Mode.BNV) keys += key("ব্যঞ্জন", 1.4f, true) { mode = Mode.BN; rebuild() }
        val mic = key(if (listening) "🔴" else "🎤", 1f, true) { toggleVoice() }
        micViews += mic
        keys += mic
        keys += key(if (mode == Mode.EN) "English" else "বাংলা", 3.5f) { typeSeparator(" ") }
        keys += key(".", 1f) { typeSeparator(".") }
        keys += key("⏎", 1.3f, true) { enter() }
        addRow(keys)
    }

    private fun cycleLang() {
        commitPhonetic()
        mode = when (mode) { Mode.EN -> Mode.PH; Mode.PH -> Mode.BN; else -> Mode.EN }
        shift = false
        rebuild()
    }

    // ---------- Typing logic ----------
    private fun typeLetter(s: String) {
        val ic = currentInputConnection ?: return
        if (mode == Mode.PH) {
            composing.append(s)
            ic.setComposingText(Phonetic.convert(composing.toString()), 1)
        } else {
            ic.commitText(s, 1)
            wordBuf.append(s)
        }
        if (shift) { shift = false; rebuild() } else updateSuggestions()
    }

    private fun typeBn(s: String) {
        currentInputConnection?.commitText(s, 1)
        wordBuf.append(s)
        updateSuggestions()
    }

    private fun commitPhonetic() {
        if (composing.isEmpty()) return
        val bn = Phonetic.convert(composing.toString())
        currentInputConnection?.commitText(bn, 1)
        composing.setLength(0)
        wordBuf.setLength(0); wordBuf.append(bn)
    }

    private fun learnWord() {
        if (wordBuf.isNotEmpty()) {
            val w = wordBuf.toString()
            if (Prefs.bool(this, "learn_words", true) && !Prefs.bool(this, "incognito", false)) wordDb.learn(w, lastWord)
            lastWord = w
            wordBuf.setLength(0)
        }
    }

    private fun commitSymbol(s: String) {
        commitPhonetic(); learnWord()
        currentInputConnection?.commitText(s, 1)
        updateSuggestions()
    }

    private fun typeSeparator(sIn: String) {
        val ic = currentInputConnection ?: return
        commitPhonetic(); learnWord()
        val bnMode = mode == Mode.PH || mode == Mode.BN || mode == Mode.BNV
        var s = sIn
        if (s == "." && bnMode && Prefs.bool(this, "dot_to_dari", true)) s = "।"
        if (s == " " && Prefs.bool(this, "double_space", true)) {
            val b = ic.getTextBeforeCursor(2, 0)?.toString()
            if (b != null && b.length == 2 && b[1] == ' ' && !b[0].isWhitespace() && b[0] !in ".,।!?;:") {
                ic.deleteSurroundingText(1, 0)
                ic.commitText(if (bnMode) "। " else ". ", 1)
                updateSuggestions(); return
            }
        }
        ic.commitText(s, 1)
        if (s == "." || s == "।") lastWord = ""
        updateSuggestions()
    }

    private fun backspace() {
        val ic = currentInputConnection ?: return
        if (composing.isNotEmpty()) {
            composing.deleteCharAt(composing.length - 1)
            if (composing.isEmpty()) ic.commitText("", 1)
            else ic.setComposingText(Phonetic.convert(composing.toString()), 1)
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            if (wordBuf.isNotEmpty()) wordBuf.deleteCharAt(wordBuf.length - 1)
        }
        updateSuggestions()
    }

    private fun enter() {
        val ic = currentInputConnection ?: return
        commitPhonetic(); learnWord()
        val ei = currentInputEditorInfo
        if (ei != null) {
            val action = ei.imeOptions and EditorInfo.IME_MASK_ACTION
            val multi = (ei.inputType and android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0
            if (!multi && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED &&
                (ei.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0) {
                ic.performEditorAction(action); return
            }
        }
        sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
    }

    // ---------- Suggestions ----------
    private fun updateSuggestions() {
        val s = sugg ?: return
        val prefix = if (mode == Mode.PH && composing.isNotEmpty()) Phonetic.convert(composing.toString()) else wordBuf.toString()
        val list: List<String> = try {
            if (prefix.isNotEmpty()) wordDb.suggest(prefix, 3)
            else if (lastWord.isNotEmpty()) wordDb.next(lastWord, 3) else emptyList()
        } catch (e: Exception) { emptyList() }
        for (i in 0 until 3) {
            suggWords[i] = list.getOrElse(i) { "" }
            s[i].text = suggWords[i]
        }
    }

    private fun pickSuggestion(w: String) {
        if (w.isEmpty()) return
        val ic = currentInputConnection ?: return
        if (composing.isNotEmpty()) { ic.commitText("$w ", 1); composing.setLength(0) }
        else { if (wordBuf.isNotEmpty()) ic.deleteSurroundingText(wordBuf.length, 0); ic.commitText("$w ", 1) }
        wordBuf.setLength(0)
        if (Prefs.bool(this, "learn_words", true) && !Prefs.bool(this, "incognito", false)) wordDb.learn(w, lastWord)
        lastWord = w
        updateSuggestions()
    }

    // ---------- Voice typing (in-keyboard, no app switch once permission granted) ----------
    private fun toggleVoiceLang() {
        val sp = Prefs.sp(this)
        sp.edit().putInt("voice_lang", if (sp.getInt("voice_lang", 0) == 0) 1 else 0).apply()
    }

    private fun setMic(on: Boolean) { micViews.forEach { it.text = if (on) "🔴" else "🎤" } }

    private fun toggleVoice() {
        if (checkCallingOrSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            openActivity(VoiceActivity::class.java); return
        }
        if (listening) { recognizer?.stopListening(); return }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "এই ফোনে Speech Recognition সার্ভিস নেই (Google app / Speech Services ইনস্টল করুন)", Toast.LENGTH_LONG).show(); return
        }
        commitPhonetic(); learnWord()
        val bn = Prefs.int(this, "voice_lang", 0) == 0
        val r = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onError(error: Int) {
                endVoice()
                Toast.makeText(this@KeyboardService, "Voice error: $error", Toast.LENGTH_SHORT).show()
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val t = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!t.isNullOrEmpty()) currentInputConnection?.setComposingText(t, 1)
            }
            override fun onResults(results: Bundle?) {
                var t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!t.isNullOrEmpty()) {
                    if (bn && Prefs.bool(this@KeyboardService, "voice_dari", true) && t.last() !in ".?!।") t += "।"
                    currentInputConnection?.commitText("$t ", 1)
                } else currentInputConnection?.finishComposingText()
                endVoice()
            }
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (bn) "bn-BD" else "en-US")
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, Prefs.bool(this, "voice_offline", false))
        listening = true
        setMic(true)
        r.startListening(i)
    }

    private fun endVoice() {
        listening = false
        setMic(false)
        recognizer?.destroy()
        recognizer = null
    }
}
