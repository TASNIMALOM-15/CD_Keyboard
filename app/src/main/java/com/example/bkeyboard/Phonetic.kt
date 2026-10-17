package com.example.bkeyboard

/** Simple Avro-style phonetic transliteration (English letters -> Bengali). */
object Phonetic {
    private val marks = mapOf("ng" to "ং", "M" to "ং", "H" to "ঃ", "^" to "ঁ")
    private val cons = mapOf(
        "chh" to "ছ", "kh" to "খ", "gh" to "ঘ", "Ng" to "ঙ", "ch" to "চ", "jh" to "ঝ", "Th" to "ঠ", "Dh" to "ঢ",
        "th" to "থ", "dh" to "ধ", "ph" to "ফ", "bh" to "ভ", "sh" to "শ", "Sh" to "ষ", "rh" to "ঢ়",
        "k" to "ক", "g" to "গ", "c" to "চ", "j" to "জ", "T" to "ট", "D" to "ড", "N" to "ণ", "t" to "ত",
        "d" to "দ", "n" to "ন", "p" to "প", "f" to "ফ", "b" to "ব", "v" to "ভ", "m" to "ম", "z" to "য",
        "r" to "র", "l" to "ল", "s" to "স", "S" to "শ", "h" to "হ", "y" to "য়", "R" to "ড়", "x" to "ক্স", "q" to "ক"
    )
    private val vowels = mapOf(
        "rri" to ("ঋ" to "ৃ"), "oi" to ("ঐ" to "ৈ"), "ou" to ("ঔ" to "ৌ"), "aa" to ("আ" to "া"),
        "ii" to ("ঈ" to "ী"), "uu" to ("ঊ" to "ূ"), "a" to ("অ" to ""), "i" to ("ই" to "ি"),
        "u" to ("উ" to "ু"), "e" to ("এ" to "ে"), "o" to ("ও" to "ো"),
        "A" to ("আ" to "া"), "I" to ("ঈ" to "ী"), "U" to ("ঊ" to "ূ"), "E" to ("এ" to "ে"), "O" to ("ও" to "ো")
    )

    fun convert(s: String): String {
        val sb = StringBuilder()
        var i = 0
        var prevCons = false
        while (i < s.length) {
            var matched = false
            for (len in 3 downTo 1) {
                if (i + len > s.length) continue
                val sub = s.substring(i, i + len)
                val m = marks[sub]
                if (m != null) { sb.append(m); prevCons = false; i += len; matched = true; break }
                val c = cons[sub]
                if (c != null) {
                    if (prevCons && sub == "y") sb.append("্য")
                    else { if (prevCons) sb.append('্'); sb.append(c) }
                    prevCons = true; i += len; matched = true; break
                }
                val v = vowels[sub]
                if (v != null) {
                    sb.append(if (prevCons) v.second else v.first)
                    prevCons = false; i += len; matched = true; break
                }
            }
            if (!matched) { sb.append(s[i]); prevCons = false; i++ }
        }
        return sb.toString()
    }
}
