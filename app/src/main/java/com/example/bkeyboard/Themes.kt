package com.example.bkeyboard

import android.graphics.Color

// style: 0 = gradient/rect keys, 1 = round keys, 2 = neon outline keys
data class KbTheme(val name: String, val bg1: Int, val bg2: Int, val key: Int, val text: Int, val style: Int)

object Themes {
    val all: List<KbTheme> by lazy {
        val l = ArrayList<KbTheme>()
        l += KbTheme("AMOLED Black", 0xFF000000.toInt(), 0xFF000000.toInt(), 0xFF1C1C1E.toInt(), Color.WHITE, 0)
        l += KbTheme("Classic Light", 0xFFECEFF1.toInt(), 0xFFCFD8DC.toInt(), Color.WHITE, 0xFF202124.toInt(), 0)
        for (h in 0 until 360 step 10) {
            val a = Color.HSVToColor(floatArrayOf(h.toFloat(), 0.65f, 0.22f))
            val b = Color.HSVToColor(floatArrayOf(((h + 40) % 360).toFloat(), 0.75f, 0.50f))
            val k = Color.HSVToColor(floatArrayOf(h.toFloat(), 0.45f, 0.62f))
            l += KbTheme("Gradient $h", a, b, k, Color.WHITE, 0)
            l += KbTheme("Round $h", a, b, k, Color.WHITE, 1)
            val n = Color.HSVToColor(floatArrayOf(h.toFloat(), 1f, 1f))
            val nb = Color.HSVToColor(floatArrayOf(h.toFloat(), 0.8f, 0.16f))
            l += KbTheme("Neon $h", 0xFF05050A.toInt(), nb, n, Color.WHITE, 2)
        }
        l
    }
}
