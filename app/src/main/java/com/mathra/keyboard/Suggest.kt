package com.mathra.keyboard

import kotlin.math.abs

/**
 * Tiny on-device suggestion + autocorrect engine.
 * It uses a fixed built-in word list (ordered roughly by frequency). It does NOT learn from, log or save
 * anything the user types.
 */
object Suggest {

    private val WORDS: List<String> = (
        "the be to of and a in that have it for not on with he as you do at this but his by from they we say her she " +
        "or an will my one all would there their what so up out if about who get which go me when make can like time " +
        "no just him know take people into year your good some could them see other than then now look only come its " +
        "over think also back after use two how our work first well way even new want because any these give day most " +
        "us is are was were been has had did does said each tell very where much through before right too means old " +
        "same another great help home school today tomorrow tonight morning night hello hi hey thanks thank please " +
        "sorry okay yes yeah love friend friends happy birthday welcome beautiful amazing awesome message call phone " +
        "number money food water family sure maybe really always never again still little more many something anything " +
        "everything nothing someone going doing being having coming looking thinking wanting trying feeling meeting " +
        "working playing watching eating sleeping talking waiting reading writing listening later soon late early " +
        "better best worse worst bad nice cool fine busy free ready done finished started start stop wait come " +
        "tell told ask asked need needed feel felt find found keep kept let put turn move live believe bring " +
        "happen write wrote provide sit stand lose pay meet include continue set learn change lead understand watch " +
        "follow create speak read allow add spend grow open walk win offer remember consider appear buy serve die " +
        "send expect build stay fall cut reach kill remain suggest raise pass sell require report decide pull " +
        "week month years hours minutes seconds person place thing things world life hand part child eye woman " +
        "government company system program question problem fact point country game music movie video photo picture " +
        "book story news class teacher student office city town road car bus train flight hotel shop market " +
        "while during without within between under around every again against almost already although among " +
        "should would could might must shall cannot because though until whether however actually probably " +
        "where here there everywhere somewhere anywhere nowhere whose whom whenever wherever whatever"
        ).split(" ").filter { it.isNotEmpty() }.distinct()

    private val rank: HashMap<String, Int> = HashMap<String, Int>().also { m ->
        WORDS.forEachIndexed { i, w -> m[w] = i }
    }

    private val CONTRACTIONS = mapOf(
        "im" to "I'm", "ive" to "I've",
        "dont" to "don't", "cant" to "can't", "wont" to "won't", "didnt" to "didn't",
        "doesnt" to "doesn't", "isnt" to "isn't", "arent" to "aren't", "wasnt" to "wasn't",
        "werent" to "weren't", "couldnt" to "couldn't", "shouldnt" to "shouldn't",
        "wouldnt" to "wouldn't", "havent" to "haven't", "hasnt" to "hasn't", "youre" to "you're",
        "thats" to "that's", "whats" to "what's", "theyre" to "they're"
    )

    fun starters(before: String): List<String> {
        val t = before.trimEnd()
        val sentenceStart = t.isEmpty() || t.last() in ".!?\n"
        return if (sentenceStart) listOf("I", "The", "I'm") else listOf("the", "and", "to")
    }

    /** Up to [n] completions for [prefix] (strictly longer than the prefix). */
    fun complete(prefix: String, n: Int): List<String> {
        if (prefix.isEmpty()) return emptyList()
        val lp = prefix.lowercase()
        val out = ArrayList<String>(n)
        for (w in WORDS) {
            if (w.length > lp.length && w.startsWith(lp)) {
                out.add(matchCase(prefix, w))
                if (out.size == n) break
            }
        }
        return out
    }

    /** Returns a corrected word, or null if [word] should be left alone. */
    fun correct(word: String): String? {
        if (word.isEmpty()) return null
        if (word.any { it.code > 127 }) return null              // never touch non-English text
        val lw = word.lowercase()
        CONTRACTIONS[lw]?.let { return if (word[0].isUpperCase() && it[0] != 'I') it.replaceFirstChar { c -> c.uppercaseChar() } else it }
        if (lw == "i") return "I"
        if (word[0].isUpperCase() && word.length > 1) return null // probably a name
        if (lw.length < 3 || rank.containsKey(lw)) return null
        var best: String? = null
        var bestRank = Int.MAX_VALUE
        for (i in WORDS.indices) {
            val c = WORDS[i]
            if (c[0] != lw[0] || abs(c.length - lw.length) > 1) continue
            if (within1(lw, c) && i < bestRank) {
                best = c
                bestRank = i
                break // WORDS is frequency ordered, first hit is the best
            }
        }
        return best?.let { matchCase(word, it) }
    }

    private fun matchCase(typed: String, w: String): String = when {
        typed.length > 1 && typed.all { it.isUpperCase() } -> w.uppercase()
        typed[0].isUpperCase() -> w.replaceFirstChar { it.uppercaseChar() }
        else -> w
    }

    /** True when the two strings differ by at most one insertion, deletion, substitution or adjacent swap. */
    private fun within1(a: String, b: String): Boolean {
        if (a == b) return true
        val la = a.length
        val lb = b.length
        if (abs(la - lb) > 1) return false
        if (la == lb) {
            var i = 0
            while (i < la && a[i] == b[i]) i++
            if (i == la) return true
            if (a.substring(i + 1) == b.substring(i + 1)) return true
            if (i + 1 < la && a[i] == b[i + 1] && a[i + 1] == b[i] && a.substring(i + 2) == b.substring(i + 2)) return true
            return false
        }
        val s = if (la < lb) a else b
        val l = if (la < lb) b else a
        var i = 0
        while (i < s.length && s[i] == l[i]) i++
        return s.substring(i) == l.substring(i + 1)
    }
}
