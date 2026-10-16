package com.mathra.keyboard

enum class Layer { EN, SYM1, SYM2, SI, EMOJI }

object Code {
    const val SHIFT = -1
    const val DEL = -2
    const val SPACE = -3
    const val ENTER = -4
    const val SYM = -5
    const val ABC = -6
    const val EMOJI = -7
    const val LANG = -8
    const val SYM2 = -9
    const val PAGE = -10
    const val TAB0 = -30      // emoji category tabs: TAB0 - index
    const val SPACER = -99
}

class Key(
    val label: String,
    val code: Int = 0,
    val weight: Float = 1f,
    val upper: String? = null,   // label/text when Shift is on
    val alt: String? = null,     // long-press character
    val commit: String? = null   // text to insert when it differs from the label (Sinhala signs)
) {
    var x = 0f
    var y = 0f
    var w = 0f
    var h = 0f
    var down = false
    var popStart = 0L
    var relStart = -1L
    var relAmp = 0f

    fun labelFor(shift: Int): String = if (shift != 0 && upper != null) upper else label
    fun textFor(shift: Int): String = if (shift != 0 && upper != null) upper else (commit ?: label)
}

object Layouts {

    private fun spacer(w: Float) = Key("", Code.SPACER, w)
    private fun tokens(s: String) = s.split(" ").filter { it.isNotEmpty() }.map { Key(it) }

    private fun center(keys: List<Key>, units: Float = 10f): List<Key> {
        val used = keys.sumOf { it.weight.toDouble() }.toFloat()
        val pad = (units - used) / 2f
        return if (pad > 0.01f) listOf(spacer(pad)) + keys + listOf(spacer(pad)) else keys
    }

    private fun letters(s: String, digits: String?) = s.mapIndexed { i, ch ->
        Key(ch.toString(), 0, 1f, ch.uppercaseChar().toString(), digits?.get(i)?.toString())
    }

    fun build(layer: Layer, numberRow: Boolean, siPage: Int, emojiCat: Int): List<List<Key>> = when (layer) {
        Layer.EN -> english(numberRow)
        Layer.SYM1 -> symbols1()
        Layer.SYM2 -> symbols2()
        Layer.SI -> sinhala(siPage)
        Layer.EMOJI -> emoji(emojiCat)
    }

    // ---------- Bottom rows ----------
    private fun bottomLetters(langLabel: String) = listOf(
        Key("?123", Code.SYM, 1.4f),
        Key(langLabel, Code.LANG, 1f),
        Key(",", 0, 0.9f),
        Key("MATHRA", Code.SPACE, 3.6f),
        Key(".", 0, 0.9f),
        Key("\uD83D\uDE0A", Code.EMOJI, 1f),
        Key("", Code.ENTER, 1.4f)
    )

    private fun bottomSymbols() = listOf(
        Key("ABC", Code.ABC, 1.4f),
        Key(",", 0, 1f),
        Key("\uD83D\uDE0A", Code.EMOJI, 1f),
        Key("MATHRA", Code.SPACE, 4f),
        Key(".", 0, 1f),
        Key("", Code.ENTER, 1.4f)
    )

    // ---------- English ----------
    private fun english(numberRow: Boolean): List<List<Key>> {
        val rows = ArrayList<List<Key>>()
        if (numberRow) rows.add("1234567890".map { Key(it.toString()) })
        rows.add(letters("qwertyuiop", if (numberRow) null else "1234567890"))
        rows.add(center(letters("asdfghjkl", null)))
        rows.add(listOf(Key("", Code.SHIFT, 1.5f)) + letters("zxcvbnm", null) + listOf(Key("", Code.DEL, 1.5f)))
        rows.add(bottomLetters("\u0DC3\u0DD2"))   // "සි" = switch to Sinhala
        return rows
    }

    // ---------- Symbols ----------
    private fun symbols1(): List<List<Key>> {
        val rows = ArrayList<List<Key>>()
        rows.add("1234567890".map { Key(it.toString()) })
        rows.add(tokens("@ # $ _ & - + ( ) /"))
        rows.add(listOf(Key("=\\<", Code.SYM2, 1.5f)) + tokens("* \" ' : ; ! ?") + listOf(Key("", Code.DEL, 1.5f)))
        rows.add(bottomSymbols())
        return rows
    }

    private fun symbols2(): List<List<Key>> {
        val rows = ArrayList<List<Key>>()
        rows.add(tokens("[ ] { } # % ^ * + ="))
        rows.add(tokens("_ \\ | ~ < > \u20AC \u00A3 \u00A5 \u2022"))
        rows.add(listOf(Key("?123", Code.SYM, 1.5f)) + tokens(". , ? ! ' \" \u2026") + listOf(Key("", Code.DEL, 1.5f)))
        rows.add(bottomSymbols())
        return rows
    }

    // ---------- Sinhala (direct Unicode layout, 4 pages) ----------
    private val siPageLabels = arrayOf("\u0D9A", "\u0D9B", "\u0D85", "\u25CC\u0DCF")   // ක ඛ අ ◌ා

    private fun sign(s: String) = Key("\u25CC$s", 0, 1f, null, null, s)
    private fun signs(s: String) = s.split(" ").filter { it.isNotEmpty() }.map { sign(it) }

    private fun sinhala(page: Int): List<List<Key>> {
        val p = page.coerceIn(0, 3)
        val r1: List<Key>
        val r2: List<Key>
        val r3: List<Key>
        when (p) {
            0 -> {
                r1 = tokens("\u0D9A \u0D9C \u0DA0 \u0DA2 \u0DA7 \u0DA9 \u0DAD \u0DAF \u0DB4 \u0DB6")
                r2 = tokens("\u0DB8 \u0DB1 \u0DBA \u0DBB \u0DBD \u0DC0 \u0DC3 \u0DC4 \u0DC5")
                r3 = tokens("\u0DA4 \u0DAB \u0DC1 \u0DC2 \u0D9F \u0DAC \u0DB3")
            }
            1 -> {
                r1 = tokens("\u0D9B \u0D9D \u0DA1 \u0DA3 \u0DA8 \u0DAA \u0DAE \u0DB0 \u0DB5 \u0DB7")
                r2 = tokens("\u0DB9 \u0DA5 \u0DC6 \u0D9E \u0DA6")
                r3 = tokens("\u0D82 \u0D83 \u0DF4")
            }
            2 -> {
                r1 = tokens("\u0D85 \u0D86 \u0D87 \u0D88 \u0D89 \u0D8A \u0D8B \u0D8C \u0D91 \u0D92")
                r2 = tokens("\u0D94 \u0D95 \u0D93 \u0D96 \u0D8D \u0D8E \u0D8F \u0D90")
                r3 = tokens("\u0D82 \u0D83 \u0DF4")
            }
            else -> {
                r1 = signs("\u0DCF \u0DD0 \u0DD1 \u0DD2 \u0DD3 \u0DD4 \u0DD6 \u0DD8 \u0DF2 \u0DD9")
                r2 = signs("\u0DDA \u0DDB \u0DDC \u0DDD \u0DDE \u0DCA \u0DDF")
                r3 = tokens("\u0D82 \u0D83 \u0DF4")
            }
        }
        val rows = ArrayList<List<Key>>()
        rows.add(center(r1))
        rows.add(center(r2))
        rows.add(listOf(Key(siPageLabels[(p + 1) % 4], Code.PAGE, 1.5f)) + center(r3, 7f) + listOf(Key("", Code.DEL, 1.5f)))
        rows.add(bottomLetters("EN"))
        return rows
    }

    // ---------- Emoji ----------
    val emojiTabs = listOf("\uD83D\uDE00", "\uD83E\uDDF8", "\uD83C\uDF54", "\u26BD", "\uD83C\uDF08", "\u2764\uFE0F")

    private val emojiSets: List<List<String>> = listOf(
        // smileys
        "\uD83D\uDE00 \uD83D\uDE03 \uD83D\uDE04 \uD83D\uDE01 \uD83D\uDE06 \uD83D\uDE05 \uD83D\uDE02 \uD83E\uDD23 \uD83D\uDE0A \uD83D\uDE07 \uD83D\uDE42 \uD83D\uDE09 \uD83D\uDE0D \uD83E\uDD70 \uD83D\uDE18 \uD83D\uDE0B \uD83D\uDE1B \uD83D\uDE1C \uD83E\uDD2A \uD83D\uDE0E \uD83E\uDD29 \uD83E\uDD73 \uD83D\uDE0F \uD83D\uDE22",
        // teddy + animals
        "\uD83E\uDDF8 \uD83D\uDC3B \uD83D\uDC3C \uD83D\uDC28 \uD83D\uDC2F \uD83E\uDD81 \uD83D\uDC36 \uD83D\uDC31 \uD83D\uDC2D \uD83D\uDC39 \uD83D\uDC30 \uD83E\uDD8A \uD83D\uDC38 \uD83D\uDC35 \uD83D\uDC14 \uD83D\uDC27 \uD83D\uDC26 \uD83E\uDD84 \uD83D\uDC1D \uD83E\uDD8B \uD83D\uDC22 \uD83D\uDC19 \uD83D\uDC2C \uD83D\uDC33",
        // food
        "\uD83C\uDF4E \uD83C\uDF4A \uD83C\uDF4B \uD83C\uDF4C \uD83C\uDF49 \uD83C\uDF47 \uD83C\uDF53 \uD83C\uDF52 \uD83C\uDF51 \uD83E\uDD6D \uD83C\uDF4D \uD83E\uDD5D \uD83C\uDF54 \uD83C\uDF5F \uD83C\uDF55 \uD83C\uDF2D \uD83C\uDF2E \uD83C\uDF7F \uD83C\uDF69 \uD83C\uDF6A \uD83C\uDF82 \uD83C\uDF70 \uD83C\uDF6B \uD83C\uDF6D",
        // fun
        "\u26BD \uD83C\uDFC0 \uD83C\uDFC8 \u26BE \uD83C\uDFBE \uD83C\uDFD0 \uD83C\uDFAE \uD83C\uDFAF \uD83C\uDFB2 \uD83C\uDFA8 \uD83C\uDFA4 \uD83C\uDFA7 \uD83C\uDFB8 \uD83C\uDFB9 \uD83C\uDFAC \uD83D\uDCF7 \uD83D\uDCBB \uD83D\uDCF1 \uD83C\uDF81 \uD83C\uDF88 \uD83C\uDF89 \uD83C\uDFC6 \uD83D\uDE80 \uD83D\uDE97",
        // nature
        "\uD83C\uDF1E \uD83C\uDF19 \u2B50 \uD83C\uDF1F \u2728 \u26A1 \uD83D\uDD25 \uD83C\uDF08 \uD83C\uDF24 \uD83C\uDF27 \u26C4 \uD83C\uDF38 \uD83C\uDF39 \uD83C\uDF3B \uD83C\uDF3C \uD83C\uDF37 \uD83C\uDF34 \uD83C\uDF40 \uD83C\uDF41 \uD83C\uDF0A \uD83C\uDF0D \uD83C\uDFD6 \uD83C\uDFDD \uD83C\uDF0B",
        // hearts + gestures
        "\u2764\uFE0F \uD83E\uDDE1 \uD83D\uDC9B \uD83D\uDC9A \uD83D\uDC99 \uD83D\uDC9C \uD83D\uDDA4 \uD83E\uDD0D \uD83E\uDD0E \uD83D\uDC96 \uD83D\uDC97 \uD83D\uDC93 \uD83D\uDC95 \uD83D\uDC9E \uD83D\uDC98 \uD83D\uDC9D \uD83D\uDC8B \uD83D\uDC4D \uD83D\uDC4F \uD83D\uDE4F \uD83D\uDCAA \uD83D\uDC4B \uD83E\uDD1D \u270C\uFE0F"
    ).map { s -> s.split(" ").filter { it.isNotEmpty() } }

    private fun emoji(cat: Int): List<List<Key>> {
        val c = cat.coerceIn(0, emojiSets.size - 1)
        val rows = ArrayList<List<Key>>()
        rows.add(emojiTabs.mapIndexed { i, e -> Key(e, Code.TAB0 - i, 1f) })
        for (chunk in emojiSets[c].chunked(8).take(3)) {
            val keys = chunk.map { Key(it) }.toMutableList()
            while (keys.size < 8) keys.add(spacer(1f))
            rows.add(keys)
        }
        rows.add(
            listOf(
                Key("ABC", Code.ABC, 1.6f),
                Key("MATHRA", Code.SPACE, 4.8f),
                Key("", Code.DEL, 1.6f),
                Key("", Code.ENTER, 1.6f)
            )
        )
        return rows
    }
}
