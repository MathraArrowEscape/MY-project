package com.mathra.keyboard

import android.content.Context

/**
 * Keyboard settings only (theme, toggles, sliders). Typed text is NEVER written here or anywhere else.
 */
class Prefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("mathra_prefs", Context.MODE_PRIVATE)

    private fun bool(key: String, def: Boolean) = sp.getBoolean(key, def)
    private fun int(key: String, def: Int) = sp.getInt(key, def)
    private fun put(key: String, v: Boolean) = sp.edit().putBoolean(key, v).apply()
    private fun put(key: String, v: Int) = sp.edit().putInt(key, v).apply()

    var themeIndex: Int
        get() = int("theme", 0)
        set(v) = put("theme", v)

    /** When true: light -> Cute Pastel, dark -> Neon Teddy (follows the phone's light/dark mode). */
    var followSystem: Boolean
        get() = bool("follow_system", false)
        set(v) = put("follow_system", v)

    var effects: Boolean
        get() = bool("effects", true)
        set(v) = put("effects", v)

    var extraEffects: Boolean
        get() = bool("extra_effects", true)
        set(v) = put("extra_effects", v)

    var particles: Boolean
        get() = bool("particles", true)
        set(v) = put("particles", v)

    /** 0 = off, 1 = system click, 2 = Teddy pop (synthesised, no audio files). */
    var soundStyle: Int
        get() = int("sound_style", 2)
        set(v) = put("sound_style", v)

    var soundVolume: Int
        get() = int("sound_volume", 60)
        set(v) = put("sound_volume", v)

    var haptic: Boolean
        get() = bool("haptic", true)
        set(v) = put("haptic", v)

    var hapticMs: Int
        get() = int("haptic_ms", 12)
        set(v) = put("haptic_ms", v)

    /** Keyboard height in percent (80..130). */
    var heightPercent: Int
        get() = int("height_percent", 100)
        set(v) = put("height_percent", v)

    var numberRow: Boolean
        get() = bool("number_row", true)
        set(v) = put("number_row", v)

    var suggestions: Boolean
        get() = bool("suggestions", true)
        set(v) = put("suggestions", v)

    var autoCorrect: Boolean
        get() = bool("autocorrect", true)
        set(v) = put("autocorrect", v)

    /** 0 = English, 1 = Sinhala (last used letter layout). */
    var lang: Int
        get() = int("lang", 0)
        set(v) = put("lang", v)
}
