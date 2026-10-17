package com.example.bkeyboard

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast

/** Only used once to ask for the microphone permission. */
class VoiceActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (Build.VERSION.SDK_INT >= 23) requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1) else finish()
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        val ok = res.isNotEmpty() && res[0] == PackageManager.PERMISSION_GRANTED
        Toast.makeText(this, if (ok) "অনুমতি পাওয়া গেছে। এবার আবার 🎤 চাপুন।" else "মাইক্রোফোন অনুমতি ছাড়া ভয়েস টাইপিং চলবে না।", Toast.LENGTH_LONG).show()
        finish()
    }
}
