package com.mathra.keyboard

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

class SettingsActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var status: TextView
    private val previews = ArrayList<ThemePreviewView>()
    private val animators = ArrayList<Animator>()
    private var sparkles: SparkleBackground? = null

    private val gold = 0xFFFFC83D.toInt()
    private val white = 0xFFFFFFFF.toInt()
    private val soft = 0xFFC9BCE8.toInt()

    private fun px(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            buildUi()
        } catch (e: Throwable) {
            CrashLog.record(this, "SettingsActivity.onCreate", e)
            setContentView(CrashLog.reportView(this, CrashLog.read(this) ?: e.toString(), null))
        }
    }

    private fun buildUi() {
        prefs = Prefs(this)

        val fx = prefs.effects
        val frame = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xFF120B2B.toInt(), 0xFF2A0F4A.toInt())
            )
        }
        if (fx) {
            sparkles = SparkleBackground(this).also { frame.addView(it, FrameLayout.LayoutParams(-1, -1)) }
        }
        val scroll = ScrollView(this).apply { isFillViewport = true }
        frame.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(18), px(24), px(18), px(40))
        }
        scroll.addView(col)

        // ---- header ----
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.mathra_logo)
            adjustViewBounds = true
            setOnTouchListener { _, e ->
                if (e.action == MotionEvent.ACTION_DOWN) burstAt(e.rawX, e.rawY)
                false
            }
        }
        col.addView(logo, LinearLayout.LayoutParams(px(150), px(150)).apply { gravity = Gravity.CENTER_HORIZONTAL })
        if (fx) breathe(logo, 3f, 1.06f, 1800)
        col.addView(ShimmerText(this).apply {
            text = "MATHRA KEYBOARD"
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(gold)
            gravity = Gravity.CENTER
            shimmerOn = fx
        })
        col.addView(label("Premium 3D Teddy Typing", 14f, soft, false).apply {
            gravity = Gravity.CENTER
            setPadding(0, px(2), 0, px(16))
        })

        // ---- setup ----
        val setup = card(col, "Get started")
        status = label("", 14f, white, false).apply { setPadding(0, px(6), 0, px(10)) }
        setup.addView(status)
        setup.addView(button("1  Enable MATHRA Keyboard") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        })
        setup.addView(button("2  Switch to MATHRA Keyboard") {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        })

        // ---- themes ----
        val themes = card(col, "Themes")
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        var rowLayout: LinearLayout? = null
        Themes.all.forEachIndexed { i, t ->
            if (i % 2 == 0) {
                rowLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                grid.addView(rowLayout, LinearLayout.LayoutParams(-1, px(118)).apply { topMargin = px(8) })
            }
            val pv = ThemePreviewView(this, t, i)
            pv.setOnClickListener {
                prefs.followSystem = false
                prefs.themeIndex = i
                refreshPreviews()
            }
            previews.add(pv)
            rowLayout!!.addView(pv, LinearLayout.LayoutParams(0, -1, 1f).apply {
                marginEnd = if (i % 2 == 0) px(8) else 0
            })
        }
        themes.addView(grid)
        toggle(themes, "Follow phone light/dark mode", "Cute Pastel in light mode, Neon Teddy in dark mode",
            { prefs.followSystem }, { prefs.followSystem = it; refreshPreviews() })

        // ---- animations ----
        val anim = card(col, "Typing animations")
        toggle(anim, "Pop typing effects", "3D key bounce and floating letters", { prefs.effects }, { prefs.effects = it })
        toggle(anim, "Sparkles & hearts", "Particle effects on keys, Space, Enter, Delete and Emoji",
            { prefs.particles }, { prefs.particles = it })
        toggle(anim, "Extra effects", "Ripple rings, bear bounce, neon press outline and combo bursts every 10 fast keys",
            { prefs.extraEffects }, { prefs.extraEffects = it })

        // ---- keyboard ----
        val kb = card(col, "Keyboard")
        toggle(kb, "Number row", "Always show 1-0 above the letters", { prefs.numberRow }, { prefs.numberRow = it })
        toggle(kb, "Suggestions", "Word suggestions from a built-in list (on-device)", { prefs.suggestions }, { prefs.suggestions = it })
        toggle(kb, "Autocorrect", "Fix common typos when you press space", { prefs.autoCorrect }, { prefs.autoCorrect = it })
        seek(kb, "Keyboard height", 80, 130, { prefs.heightPercent }, { prefs.heightPercent = it }, { "$it%" })

        // ---- sound & vibration ----
        val sv = card(col, "Sound & vibration")
        sv.addView(label("Typing sound", 15f, white, false))
        val rg = RadioGroup(this).apply { orientation = RadioGroup.VERTICAL }
        val names = arrayOf("Off", "System click", "Teddy pop")
        for (i in names.indices) {
            rg.addView(RadioButton(this).apply {
                text = names[i]
                setTextColor(white)
                id = 100 + i
                isChecked = prefs.soundStyle == i
            })
        }
        rg.setOnCheckedChangeListener { _, id -> prefs.soundStyle = id - 100 }
        sv.addView(rg)
        seek(sv, "Sound volume", 0, 100, { prefs.soundVolume }, { prefs.soundVolume = it }, { "$it%" })
        toggle(sv, "Haptic feedback", "Short vibration on each key", { prefs.haptic }, { prefs.haptic = it })
        seek(sv, "Vibration strength", 5, 40, { prefs.hapticMs }, { prefs.hapticMs = it }, { "$it ms" })

        // ---- try it ----
        val tryIt = card(col, "Try it")
        tryIt.addView(EditText(this).apply {
            hint = "Type here to test MATHRA KEYBOARD..."
            setHintTextColor(0x99FFFFFF.toInt())
            setTextColor(white)
            minLines = 2
            gravity = Gravity.TOP
            background = GradientDrawable().apply {
                setColor(0x33000000)
                cornerRadius = px(14).toFloat()
                setStroke(px(1), 0x55FFC83D)
            }
            setPadding(px(12), px(10), px(12), px(10))
        })
        tryIt.addView(label("Tip: tap the bear to change theme, long-press it for settings. Hold Shift and swipe the " +
            "space bar to select text. Long-press the space bar or the globe key to switch keyboards.",
            12f, soft, false).apply { setPadding(0, px(8), 0, 0) })

        // ---- privacy ----
        val pr = card(col, "Privacy")
        pr.addView(label(
            "MATHRA KEYBOARD does not collect, store or send what you type, including passwords and private messages. " +
                "The app has no internet permission and no analytics. The only permission is Vibrate, used for key haptics " +
                "(you can turn it off). Suggestions use a built-in word list on your device and are switched off in " +
                "password, e-mail, URL and number fields.\n\n" +
                "Like every Android keyboard, MATHRA KEYBOARD can technically see keystrokes because that is how a keyboard works. " +
                "Android shows a warning when you enable any keyboard.",
            13f, soft, false))

        // ---- about ----
        val about = card(col, "About")
        about.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_DOWN) burstAt(e.rawX, e.rawY)
            false
        }
        about.addView(label(
            "MATHRA KEYBOARD is a fast, private keyboard for English and Sinhala, made with love and a little " +
                "teddy-bear magic. Pick a theme, feel the sounds and sparkles, and type your way.",
            13f, soft, false
        ).apply { setPadding(0, 0, 0, px(8)) })

        val chips = listOf(
            "\uD83D\uDD12 No internet", "\uD83D\uDEAB No ads",
            "\uD83E\uDDF8 3D Teddy", "\uD83C\uDFA8 5 themes",
            "\uD83C\uDDF1\uD83C\uDDF0 Sinhala + English", "\u2728 Effects & sounds",
            "\uD83D\uDD0B Lightweight", "\uD83D\uDEE1 Zero trackers"
        )
        var chipIndex = 0
        for (r in chips.indices step 2) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (k in 0..1) {
                val ch = chip(chips[r + k])
                row.addView(ch, LinearLayout.LayoutParams(0, -2, 1f).apply {
                    setMargins(px(3), px(3), px(3), px(3))
                })
                if (fx) {
                    ch.alpha = 0f
                    ch.scaleX = 0.5f
                    ch.scaleY = 0.5f
                    ch.animate().alpha(1f).scaleX(1f).scaleY(1f)
                        .setStartDelay(650L + 90L * chipIndex).setDuration(420)
                        .setInterpolator(OvershootInterpolator(2f)).start()
                }
                chipIndex++
            }
            about.addView(row, LinearLayout.LayoutParams(-1, -2))
        }

        val madeBy = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, px(14), 0, 0)
        }
        madeBy.addView(label("Made with ", 13f, soft, false))
        val heart = label("\u2665", 17f, 0xFFFF4FD8.toInt(), true)
        madeBy.addView(heart)
        madeBy.addView(label(" by MATHRA", 13f, soft, false))
        about.addView(madeBy)
        if (fx) breathe(heart, 0f, 1.4f, 650)

        val ver = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
        about.addView(label("Version $ver  \u2022  Tap this card for a surprise \u2728", 11f, soft, false).apply {
            gravity = Gravity.CENTER
            setPadding(0, px(6), 0, 0)
        })

        // ---- entrance: cards float up one after another ----
        if (fx) {
            for (i in 0 until col.childCount) {
                val v = col.getChildAt(i)
                v.alpha = 0f
                v.translationY = px(26).toFloat()
                v.animate().alpha(1f).translationY(0f)
                    .setStartDelay(70L * i).setDuration(450)
                    .setInterpolator(DecelerateInterpolator()).start()
            }
        }

        setContentView(frame)
    }

    override fun onDestroy() {
        animators.forEach { it.cancel() }
        animators.clear()
        super.onDestroy()
    }

    private fun burstAt(rawX: Float, rawY: Float) {
        val sp = sparkles ?: return
        val loc = IntArray(2)
        sp.getLocationOnScreen(loc)
        sp.burst(rawX - loc[0], rawY - loc[1])
    }

    /** Gentle endless pulse: scales (and optionally wobbles) a view. */
    private fun breathe(v: View, rotation: Float, scale: Float, ms: Long) {
        val holders = ArrayList<PropertyValuesHolder>()
        holders.add(PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, scale))
        holders.add(PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, scale))
        if (rotation != 0f) holders.add(PropertyValuesHolder.ofFloat(View.ROTATION, -rotation, rotation))
        val a = ObjectAnimator.ofPropertyValuesHolder(v, *holders.toTypedArray())
        a.duration = ms
        a.repeatCount = ObjectAnimator.INFINITE
        a.repeatMode = ObjectAnimator.REVERSE
        a.start()
        animators.add(a)
    }

    private fun chip(t: String) = TextView(this).apply {
        text = t
        textSize = 12.5f
        setTextColor(white)
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            setColor(0x33000000)
            cornerRadius = px(14).toFloat()
            setStroke(px(1), 0x66FFC83D)
        }
        setPadding(px(6), px(9), px(6), px(9))
    }

    override fun onResume() {
        super.onResume()
        if (!::status.isInitialized) return
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_INPUT_METHODS) ?: ""
        val current = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: ""
        status.text = when {
            current.startsWith(packageName) -> "\u2705 MATHRA Keyboard is enabled and selected"
            enabled.contains(packageName) -> "\u2714 Enabled. Now switch to it (step 2)"
            else -> "\u26A0 Not enabled yet. Start with step 1"
        }
        refreshPreviews()
    }

    private fun refreshPreviews() = previews.forEach { it.invalidate() }

    // ---------- small UI builders ----------
    private fun label(t: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply {
        text = t
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun card(parent: LinearLayout, title: String): LinearLayout {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(0x22FFFFFF)
                cornerRadius = px(20).toFloat()
                setStroke(px(1), 0x33FFC83D)
            }
            setPadding(px(16), px(14), px(16), px(16))
        }
        c.addView(label(title, 17f, gold, true).apply { setPadding(0, 0, 0, px(6)) })
        parent.addView(c, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = px(14) })
        return c
    }

    private fun button(t: String, onClick: () -> Unit) = TextView(this).apply {
        text = t
        textSize = 15f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(0xFF1A1200.toInt())
        gravity = Gravity.CENTER
        background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xFFFFD86B.toInt(), 0xFFE8B22E.toInt())
        ).apply { cornerRadius = px(16).toFloat() }
        setPadding(px(12), px(12), px(12), px(12))
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(8) }
    }

    private fun toggle(parent: LinearLayout, title: String, desc: String, get: () -> Boolean, set: (Boolean) -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, px(6), 0, px(6))
        }
        val tv = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        tv.addView(label(title, 15f, white, false))
        tv.addView(label(desc, 12f, soft, false))
        row.addView(tv, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(Switch(this).apply {
            isChecked = get()
            setOnCheckedChangeListener { _, c -> set(c) }
        })
        parent.addView(row)
    }

    private fun seek(
        parent: LinearLayout, title: String, min: Int, max: Int,
        get: () -> Int, set: (Int) -> Unit, fmt: (Int) -> String
    ) {
        val tv = label("$title: ${fmt(get())}", 15f, white, false).apply { setPadding(0, px(8), 0, 0) }
        parent.addView(tv)
        parent.addView(SeekBar(this).apply {
            this.max = max - min
            progress = get() - min
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    set(p + min)
                    tv.text = "$title: ${fmt(p + min)}"
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        })
    }

    // =====================================================================
    // Shimmering gold title
    // =====================================================================
    private class ShimmerText(ctx: Context) : TextView(ctx) {
        var shimmerOn = true
        private var shader: LinearGradient? = null
        private var span = 1f
        private val m = Matrix()

        override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
            super.onSizeChanged(w, h, ow, oh)
            span = (w * 0.8f).coerceAtLeast(1f)
            shader = LinearGradient(
                0f, 0f, span, 0f,
                intArrayOf(0xFFFFC83D.toInt(), 0xFFFFF6C8.toInt(), 0xFFFFC83D.toInt()),
                floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.REPEAT
            )
        }

        override fun onDraw(canvas: Canvas) {
            val sh = shader
            if (shimmerOn && sh != null) {
                val t = SystemClock.uptimeMillis() / 2400f
                m.setTranslate((t - t.toInt()) * span, 0f)
                sh.setLocalMatrix(m)
                paint.shader = sh
                super.onDraw(canvas)
                postInvalidateOnAnimation()
            } else {
                super.onDraw(canvas)
            }
        }
    }

    // =====================================================================
    // Floating hearts / stars behind the page, plus tap bursts
    // =====================================================================
    private class SparkleBackground(ctx: Context) : View(ctx) {
        private val d = resources.displayMetrics.density
        private val glyphs = arrayOf("\u2726", "\u2665", "\u2605", "\u2736")
        private val colors = intArrayOf(0xFFFFC83D.toInt(), 0xFFFF4FD8.toInt(), 0xFF00E5FF.toInt(), 0xFFB36BFF.toInt())
        private val n = 24
        private val fx = FloatArray(n) { Random.nextFloat() }
        private val fy = FloatArray(n) { Random.nextFloat() }
        private val ph = FloatArray(n) { Random.nextFloat() * 6.28f }
        private val sz = FloatArray(n) { 9f + Random.nextFloat() * 13f }
        private val spd = FloatArray(n) { 6f + Random.nextFloat() * 10f }
        private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

        private val m = 48
        private val bX = FloatArray(m)
        private val bY = FloatArray(m)
        private val bVx = FloatArray(m)
        private val bVy = FloatArray(m)
        private val bLife = FloatArray(m)
        private val bGlyph = IntArray(m)
        private val bColor = IntArray(m)
        private var last = SystemClock.uptimeMillis()

        fun burst(x: Float, y: Float) {
            var spawned = 0
            for (i in 0 until m) {
                if (bLife[i] <= 0f && spawned < 20) {
                    val ang = Random.nextFloat() * 6.2831f
                    val v = (90f + Random.nextFloat() * 220f) * d
                    bX[i] = x
                    bY[i] = y
                    bVx[i] = kotlin.math.cos(ang) * v
                    bVy[i] = kotlin.math.sin(ang) * v - 120f * d
                    bLife[i] = 0.8f + Random.nextFloat() * 0.5f
                    bGlyph[i] = Random.nextInt(glyphs.size)
                    bColor[i] = colors[Random.nextInt(colors.size)]
                    spawned++
                }
            }
            invalidate()
        }

        override fun onDraw(c: Canvas) {
            val now = SystemClock.uptimeMillis()
            val dt = (now - last).coerceAtMost(50L) / 1000f
            last = now
            val t = now / 1000f
            val w = width.toFloat()
            val h = height.toFloat()
            if (h <= 0f) return

            for (i in 0 until n) {
                var y = (fy[i] * h - t * spd[i] * d) % h
                if (y < 0f) y += h
                val a = 0.5f + 0.5f * sin(t * 2f + ph[i])
                p.color = (colors[i % colors.size] and 0x00FFFFFF) or ((a * 150).toInt() shl 24)
                p.textSize = sz[i] * d
                c.drawText(glyphs[i % glyphs.size], fx[i] * w, y, p)
            }

            for (i in 0 until m) {
                if (bLife[i] <= 0f) continue
                bX[i] += bVx[i] * dt
                bY[i] += bVy[i] * dt
                bVy[i] += 520f * d * dt
                bLife[i] -= dt
                val a = (bLife[i] / 0.9f).coerceIn(0f, 1f)
                p.color = (bColor[i] and 0x00FFFFFF) or ((a * 255).toInt() shl 24)
                p.textSize = 18f * d
                c.drawText(glyphs[bGlyph[i]], bX[i], bY[i], p)
            }
            postInvalidateOnAnimation()
        }
    }

    // =====================================================================
    // Animated theme preview card
    // =====================================================================
    private inner class ThemePreviewView(ctx: Context, private val t: MTheme, private val index: Int) : View(ctx) {
        private val dp = resources.displayMetrics.density
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
        private val rect = RectF()
        private var bg: Shader? = null
        private val counts = intArrayOf(7, 7, 5)

        override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
            bg = LinearGradient(0f, 0f, 0f, h.toFloat(), t.bgTop, t.bgBottom, Shader.TileMode.CLAMP)
        }

        override fun onDraw(c: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            val now = SystemClock.uptimeMillis()
            val corner = 14 * dp

            fill.shader = bg
            rect.set(0f, 0f, w, h)
            c.drawRoundRect(rect, corner, corner, fill)
            fill.shader = null

            // header: bear + theme name
            Art.drawBear(c, 20 * dp, 18 * dp, 9 * dp, true)
            text.typeface = Typeface.DEFAULT_BOLD
            text.textSize = 11 * dp
            text.color = t.keyText
            text.textAlign = Paint.Align.LEFT
            c.drawText(t.name, 36 * dp, 22 * dp, text)
            text.textAlign = Paint.Align.CENTER

            // mini keys; one key at a time pops with a bounce and sends up a heart
            val pad = 8 * dp
            val gap = 3 * dp
            val keyW = (w - 2 * pad - 6 * gap) / 7f
            val top = 36 * dp
            val keyH = (h - top - pad - 2 * gap) / 3f
            val total = counts.sum()
            val slot = 420L
            val active = ((now / slot) % total).toInt()
            val u = (now % slot) / 1000f

            var n = 0
            for (r in 0..2) {
                val cnt = counts[r]
                val rowW = cnt * keyW + (cnt - 1) * gap
                var x = (w - rowW) / 2f
                val y = top + r * (keyH + gap)
                for (i in 0 until cnt) {
                    val isActive = n == active
                    val s = if (isActive) 1f + 0.28f * exp(-8f * u) * cos(20f * u) else 1f
                    val cx = x + keyW / 2f
                    val cy = y + keyH / 2f
                    c.save()
                    c.translate(cx, cy - (s - 1f) * keyH)
                    c.scale(s, s)
                    rect.set(-keyW / 2f, -keyH / 2f + 2 * dp, keyW / 2f, keyH / 2f)
                    fill.color = t.keySide
                    c.drawRoundRect(rect, 5 * dp, 5 * dp, fill)
                    rect.set(-keyW / 2f, -keyH / 2f, keyW / 2f, keyH / 2f - 2 * dp)
                    fill.color = if (isActive) t.spTop else t.keyTop
                    c.drawRoundRect(rect, 5 * dp, 5 * dp, fill)
                    c.restore()
                    if (isActive) {
                        val a = (255 * exp(-4f * u)).toInt()
                        text.typeface = Typeface.DEFAULT
                        text.textSize = 10 * dp
                        text.color = (t.sparkle[0] and 0x00FFFFFF) or (a shl 24)
                        c.drawText("\u2665", cx, cy - keyH * 0.6f - u * 60 * dp, text)
                    }
                    x += keyW + gap
                    n++
                }
            }

            // selection outline
            val selected = !prefs.followSystem && prefs.themeIndex == index
            if (selected) {
                stroke.strokeWidth = 3 * dp
                stroke.color = 0xFFFFC83D.toInt()
                rect.set(1.5f * dp, 1.5f * dp, w - 1.5f * dp, h - 1.5f * dp)
                c.drawRoundRect(rect, corner, corner, stroke)
            }

            postInvalidateDelayed(33)
        }
    }
}
