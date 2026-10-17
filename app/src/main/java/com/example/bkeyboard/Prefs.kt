package com.example.bkeyboard

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    fun sp(c: Context): SharedPreferences = c.getSharedPreferences("kb", Context.MODE_PRIVATE)
    fun int(c: Context, k: String, d: Int) = sp(c).getInt(k, d)
    fun bool(c: Context, k: String, d: Boolean) = sp(c).getBoolean(k, d)
    fun str(c: Context, k: String, d: String) = sp(c).getString(k, d) ?: d
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
