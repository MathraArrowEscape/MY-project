package com.mathra.keyboard

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.SystemClock
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast

/**
 * MATHRA KEYBOARD input method.
 *
 * Privacy notes:
 *  - Text is only ever passed straight to the focused app through InputConnection.
 *  - Suggestions/autocorrect look at the few characters before the cursor, in memory, to build the current
 *    word. Nothing is logged, learned, stored or sent anywhere (the app has no INTERNET permission).
 *  - Suggestions and autocorrect are disabled in password, e-mail, URL, number and "no suggestions" fields.
 */
class MathraIME : InputMethodService(), KeyboardView.Listener {

    private lateinit var prefs: Prefs
    private var view: KeyboardView? = null

    private var noSuggest = false
    private var imeAction = EditorInfo.IME_ACTION_NONE
    private var customActionId = -1
    private var lastSpaceAt = 0L

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
    }

    override fun onCreateInputView(): View {
        return try {
            val v = KeyboardView(this)
            v.listener = this
            v.letterLayer = if (prefs.lang == 1) Layer.SI else Layer.EN
            view = v
            applyPrefs(v)
            v.showLayer(v.letterLayer)
            v
        } catch (e: Throwable) {
            CrashLog.record(this, "MathraIME.onCreateInputView", e)
            view = null
            android.widget.TextView(this).apply {
                text = "MATHRA keyboard error - open the MATHRA app to see details"
                setPadding(32, 48, 32, 48)
            }
        }
    }

    /** Landscape: keep the normal (not full-screen "extract") editing UI so the app stays visible. */
    override fun onEvaluateFullscreenMode(): Boolean = false

    private fun applyPrefs(v: KeyboardView) {
        v.theme = Themes.resolve(this, prefs)
        v.effectsOn = prefs.effects
        v.particlesOn = prefs.particles
        v.extraOn = prefs.extraEffects
        v.numberRow = prefs.numberRow
        v.heightScale = prefs.heightPercent / 100f
        v.feedback.soundStyle = prefs.soundStyle
        v.feedback.volume = prefs.soundVolume / 100f
        v.feedback.haptic = prefs.haptic
        v.feedback.hapticMs = prefs.hapticMs
        v.reload()
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        try {
            startInput(info)
        } catch (e: Throwable) {
            CrashLog.record(this, "MathraIME.onStartInputView", e)
        }
    }

    private fun startInput(info: EditorInfo) {
        val v = view ?: return
        applyPrefs(v)

        val type = info.inputType
        val cls = type and InputType.TYPE_MASK_CLASS
        val variation = type and InputType.TYPE_MASK_VARIATION
        val sensitive = cls == InputType.TYPE_CLASS_TEXT && (
            variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS ||
                variation == InputType.TYPE_TEXT_VARIATION_URI
            )
        noSuggest = cls != InputType.TYPE_CLASS_TEXT || sensitive ||
            (type and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0

        customActionId = if (info.actionLabel != null) info.actionId else -1
        imeAction = if ((info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) {
            EditorInfo.IME_ACTION_NONE
        } else {
            info.imeOptions and EditorInfo.IME_MASK_ACTION
        }
        v.setEnterLabel(
            when {
                customActionId != -1 -> info.actionLabel?.toString() ?: ""
                imeAction == EditorInfo.IME_ACTION_GO -> "Go"
                imeAction == EditorInfo.IME_ACTION_SEARCH -> "Search"
                imeAction == EditorInfo.IME_ACTION_SEND -> "Send"
                imeAction == EditorInfo.IME_ACTION_NEXT -> "Next"
                imeAction == EditorInfo.IME_ACTION_DONE -> "Done"
                imeAction == EditorInfo.IME_ACTION_PREVIOUS -> "Prev"
                else -> ""
            }
        )

        val numeric = cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE ||
            cls == InputType.TYPE_CLASS_DATETIME
        v.showLayer(if (numeric) Layer.SYM1 else v.letterLayer)
        refreshShift()
        updateSuggestions()
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        try {
            refreshShift()
            updateSuggestions()
        } catch (e: Throwable) {
            CrashLog.record(this, "MathraIME.onUpdateSelection", e)
        }
    }

    override fun onDestroy() {
        view?.feedback?.release()
        super.onDestroy()
    }

    // =====================================================================
    // KeyboardView.Listener
    // =====================================================================
    override fun onText(s: String) {
        val ic = currentInputConnection ?: return
        if (s.length == 1 && ".,!?;:".indexOf(s[0]) >= 0) maybeAutocorrect()
        ic.commitText(s, 1)
        refreshShift()
        updateSuggestions()
    }

    override fun onDelete() {
        val ic = currentInputConnection ?: return
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            ic.commitText("", 1)
        } else {
            val before = ic.getTextBeforeCursor(1, 0)
            if (before.isNullOrEmpty()) {
                sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)      // apps that don't expose text (terminals, games)
            } else {
                if (Build.VERSION.SDK_INT >= 24) {
                    ic.deleteSurroundingTextInCodePoints(1, 0)
                } else {
                    ic.deleteSurroundingText(1, 0)
                }
            }
        }
        refreshShift()
        updateSuggestions()
    }

    override fun onSpace() {
        val ic = currentInputConnection ?: return
        val now = SystemClock.uptimeMillis()
        val before = ic.getTextBeforeCursor(3, 0)?.toString() ?: ""
        val doubleSpace = now - lastSpaceAt < 400 && before.length >= 2 &&
            before.last() == ' ' && before[before.length - 2].isLetterOrDigit()
        if (doubleSpace && !noSuggest) {
            ic.deleteSurroundingText(1, 0)
            ic.commitText(". ", 1)               // double-space -> ". "
        } else {
            maybeAutocorrect()
            ic.commitText(" ", 1)
        }
        lastSpaceAt = now
        refreshShift()
        updateSuggestions()
    }

    override fun onEnter() {
        val ic = currentInputConnection ?: return
        maybeAutocorrect()
        when {
            customActionId != -1 -> ic.performEditorAction(customActionId)
            imeAction == EditorInfo.IME_ACTION_GO || imeAction == EditorInfo.IME_ACTION_SEARCH ||
                imeAction == EditorInfo.IME_ACTION_SEND || imeAction == EditorInfo.IME_ACTION_NEXT ||
                imeAction == EditorInfo.IME_ACTION_DONE || imeAction == EditorInfo.IME_ACTION_PREVIOUS ->
                ic.performEditorAction(imeAction)
            else -> sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
        refreshShift()
        updateSuggestions()
    }

    override fun onLayerChanged() {
        val v = view ?: return
        if (v.layer == Layer.EN) prefs.lang = 0
        if (v.layer == Layer.SI) prefs.lang = 1
        refreshShift()
        updateSuggestions()
    }

    override fun onSuggestionTap(index: Int) {
        val s = view?.suggestions?.get(index) ?: return
        if (s.isBlank()) return
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(32, 0)?.toString() ?: ""
        val w = currentWord(before)
        if (w.isNotEmpty()) ic.deleteSurroundingText(w.length, 0)
        ic.commitText("$s ", 1)
        lastSpaceAt = SystemClock.uptimeMillis()
        refreshShift()
        updateSuggestions()
    }

    /** Hold Shift and swipe the space bar to select text; without Shift the cursor just moves. */
    override fun onSpaceSwipe(steps: Int) {
        val ic = currentInputConnection ?: return
        val code = if (steps > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
        val meta = if ((view?.shift ?: 0) != 0) KeyEvent.META_SHIFT_ON else 0
        val t = SystemClock.uptimeMillis()
        ic.sendKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0, meta))
        ic.sendKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0, meta))
    }

    override fun onPicker() {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
    }

    override fun onSelectAll() {
        currentInputConnection?.performContextMenuAction(android.R.id.selectAll)
    }

    override fun onToolbar(id: Int, long: Boolean) {
        val v = view ?: return
        when (id) {
            KeyboardView.T_BRAND -> {
                if (long) {
                    openSettings()
                } else {
                    prefs.followSystem = false
                    prefs.themeIndex = (prefs.themeIndex + 1) % Themes.all.size
                    v.theme = Themes.resolve(this, prefs)
                    v.celebrate()
                }
            }
            KeyboardView.T_CLIP -> {
                // tap = paste, long press = copy the selection
                currentInputConnection?.performContextMenuAction(if (long) android.R.id.copy else android.R.id.paste)
                updateSuggestions()
            }
            KeyboardView.T_MIC -> startVoiceTyping()
            KeyboardView.T_SET -> openSettings()
        }
    }

    // =====================================================================
    // Helpers
    // =====================================================================
    private fun openSettings() {
        val i = Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(i)
    }

    /** Hands over to an installed voice-typing input method (e.g. Google voice typing) if there is one. */
    @Suppress("DEPRECATION")
    private fun startVoiceTyping() {
        try {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            for (im in imm.enabledInputMethodList) {
                for (st in imm.getEnabledInputMethodSubtypeList(im, true)) {
                    if (st.mode.equals("voice", ignoreCase = true)) {
                        if (Build.VERSION.SDK_INT >= 28) {
                            switchInputMethod(im.id, st)
                        } else {
                            imm.setInputMethodAndSubtype(window.window!!.attributes.token, im.id, st)
                        }
                        return
                    }
                }
            }
        } catch (e: Exception) {
            // fall through to the message below
        }
        Toast.makeText(this, "No voice-typing keyboard is enabled on this phone", Toast.LENGTH_SHORT).show()
    }

    private fun refreshShift() {
        val v = view ?: return
        if (v.layer != Layer.EN || v.shift == 2) return
        val info = currentInputEditorInfo ?: return
        val caps = if (info.inputType != 0) currentInputConnection?.getCursorCapsMode(info.inputType) ?: 0 else 0
        v.setShift(if (caps != 0) 1 else 0)
    }

    private fun currentWord(before: String): String {
        var i = before.length
        while (i > 0 && (before[i - 1].isLetter() || before[i - 1] == '\'')) i--
        while (i < before.length && before[i] == '\'') i++
        return before.substring(i)
    }

    private fun maybeAutocorrect() {
        val v = view ?: return
        if (!prefs.autoCorrect || noSuggest || v.layer != Layer.EN) return
        val ic = currentInputConnection ?: return
        if (!ic.getSelectedText(0).isNullOrEmpty()) return
        val before = ic.getTextBeforeCursor(32, 0)?.toString() ?: return
        val w = currentWord(before)
        if (w.isEmpty()) return
        val fix = Suggest.correct(w) ?: return
        if (fix == w) return
        ic.deleteSurroundingText(w.length, 0)
        ic.commitText(fix, 1)
    }

    private fun updateSuggestions() {
        val v = view ?: return
        if (!prefs.suggestions || noSuggest || v.layer != Layer.EN) {
            v.setSuggestions("", "", "")
            return
        }
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(32, 0)?.toString() ?: ""
        val w = currentWord(before)
        if (w.isEmpty()) {
            val s = Suggest.starters(before)
            v.setSuggestions(s[0], s[1], s[2])
        } else {
            val c = Suggest.complete(w, 2)
            v.setSuggestions(w, c.getOrElse(0) { "" }, c.getOrElse(1) { "" })
        }
    }
}
