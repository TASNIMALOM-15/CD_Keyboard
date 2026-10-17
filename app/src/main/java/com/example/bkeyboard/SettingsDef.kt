package com.example.bkeyboard

sealed class Opt(val key: String, val title: String, val cat: String)
class Toggle(key: String, title: String, cat: String, val def: Boolean) : Opt(key, title, cat)
class Slider(key: String, title: String, cat: String, val min: Int, val max: Int, val def: Int) : Opt(key, title, cat)
class Choice(key: String, title: String, cat: String, val options: List<String>, val def: Int) : Opt(key, title, cat)

object SettingsDef {
    val all: List<Opt> = listOf(
        Toggle("number_row", "Number row", "Layout", true),
        Toggle("bn_digits", "Bengali digits (১২৩)", "Layout", false),
        Slider("key_height", "Key height (dp)", "Layout", 32, 80, 46),
        Slider("key_gap", "Key spacing (dp)", "Layout", 0, 10, 3),
        Slider("key_radius", "Key corner radius (dp)", "Layout", 0, 30, 8),
        Slider("text_size", "Key text size (sp)", "Layout", 12, 30, 18),
        Choice("one_hand", "One-handed mode", "Layout", listOf("Off", "Left", "Right"), 0),
        Slider("bottom_pad", "Bottom padding (dp)", "Layout", 0, 48, 0),
        Toggle("show_toolbar", "Show toolbar", "Layout", true),
        Toggle("show_suggest", "Show suggestions", "Layout", true),
        Slider("key_alpha", "Key transparency (opacity %)", "Appearance", 20, 100, 85),
        Slider("bg_alpha", "Custom background opacity %", "Appearance", 10, 100, 100),
        Choice("press_fx", "Key press effect", "Appearance", listOf("None", "Dim", "Shrink"), 1),
        Toggle("haptic", "Haptic feedback", "Feedback", true),
        Slider("haptic_ms", "Vibration length (ms)", "Feedback", 5, 50, 15),
        Toggle("sound", "Key sound", "Feedback", false),
        Slider("sound_vol", "Sound volume %", "Feedback", 0, 100, 50),
        Toggle("auto_caps", "Auto capitalise", "Typing", true),
        Toggle("double_space", "Double space = full stop", "Typing", true),
        Toggle("learn_words", "Learn words for suggestions", "Typing", true),
        Toggle("dot_to_dari", "'.' types '।' in Bengali modes", "Typing", true),
        Choice("voice_lang", "Voice language", "Voice", listOf("বাংলা", "English"), 0),
        Toggle("voice_offline", "Prefer offline recognition", "Voice", false),
        Toggle("voice_dari", "Add '।' after Bengali voice text", "Voice", true),
        Toggle("clip_monitor", "Save copied text to clipboard history", "Privacy", true),
        Toggle("skip_sensitive", "Skip items marked sensitive (passwords)", "Privacy", true),
        Toggle("incognito", "Incognito (don't learn words)", "Privacy", false)
    )
}
