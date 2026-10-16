package com.mathra.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.SparseArray
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * The whole keyboard is ONE custom View that draws with Canvas. That keeps it light on memory/battery:
 * no view hierarchy, no per-frame allocations in onDraw, and animation frames are only requested while
 * something is actually animating.
 */
class KeyboardView(context: Context) : View(context) {

    interface Listener {
        fun onText(s: String)
        fun onDelete()
        fun onSpace()
        fun onEnter()
        fun onLayerChanged()
        fun onSuggestionTap(index: Int)
        fun onToolbar(id: Int, long: Boolean)
        fun onSpaceSwipe(steps: Int)
        fun onPicker()
        fun onSelectAll()
    }

    companion object {
        const val T_BRAND = 1
        const val T_CLIP = 2
        const val T_MIC = 3
        const val T_SET = 4
        const val T_SUG = 10
        private const val POP = 0.16f
    }

    var listener: Listener? = null

    var theme: MTheme = Themes.all[0]
        set(value) {
            field = value
            if (width > 0 && height > 0) buildShaders()
            invalidate()
        }

    var layer: Layer = Layer.EN
        private set
    var letterLayer: Layer = Layer.EN
    var shift = 0
        private set

    private var siPage = 0
    private var emojiCat = 0
    private var enterLabel = ""

    var numberRow = true
    var effectsOn = true
    var particlesOn = true
    var extraOn = true   // ripple rings, bear bounce, combo bursts
    var heightScale = 1f

    val suggestions = arrayOf("", "", "")
    val feedback = Feedback(context)

    // ---------- metrics ----------
    private val dp = resources.displayMetrics.density
    private val sp = dp * resources.configuration.fontScale
    private val toolbarH = 44 * dp
    private val padSide = 3 * dp
    private val padTop = 2 * dp
    private val padBottom = 6 * dp
    private val gap = 4 * dp
    private val iconW = 38 * dp

    // ---------- drawing tools (allocated once) ----------
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val rect = RectF()
    private val path = Path()
    private val tfMedium: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val tfBold: Typeface = Typeface.DEFAULT_BOLD

    private var bgShader: Shader? = null
    private var keyShader: Shader? = null
    private var spShader: Shader? = null

    private var rows: List<List<Key>> = emptyList()
    private var kh = 0f
    private var sugLeft = 0f
    private var sugRight = 0f

    private val handler = Handler(Looper.getMainLooper())
    private var lastShiftTap = 0L
    private var lastFrame = 0L

    // ---------- floating letters + particles ----------
    private class FL {
        var on = false
        var t0 = 0L
        var x = 0f
        var y = 0f
        var w = 0f
        var h = 0f
        var s = ""
    }

    private class P {
        var on = false
        var t0 = 0L
        var x = 0f
        var y = 0f
        var vx = 0f
        var vy = 0f
        var g = 0f
        var life = 0f
        var size = 0f
        var color = 0
        var glyph = "\u2726"
    }

    private val fls = Array(8) { FL() }
    private var flIdx = 0
    private val ps = Array(56) { P() }
    private var pIdx = 0
    private val glyphs = arrayOf("\u2726", "\u2665", "\u2605")

    // faint background sparkles: x fraction, y fraction, glyph index, size (dp)
    private val decor = arrayOf(
        floatArrayOf(0.05f, 0.10f, 1f, 10f), floatArrayOf(0.30f, 0.06f, 0f, 9f),
        floatArrayOf(0.62f, 0.12f, 2f, 9f), floatArrayOf(0.88f, 0.07f, 1f, 11f),
        floatArrayOf(0.15f, 0.55f, 0f, 9f), floatArrayOf(0.50f, 0.98f, 1f, 10f),
        floatArrayOf(0.80f, 0.60f, 2f, 9f), floatArrayOf(0.97f, 0.92f, 0f, 10f)
    )

    // ---------- touch state ----------
    private inner class Ptr {
        var key: Key? = null
        var tool = 0
        var downX = 0f
        var lastX = 0f
        var acc = 0f
        var swiped = false
        var longFired = false
        val longRun = Runnable { fireLong(this) }
        val repRun: Runnable = object : Runnable {
            override fun run() {
                val k = key ?: return
                if (k.code != Code.DEL || !k.down) return
                listener?.onDelete()
                spawnFor(k)
                feedback.key(2)
                handler.postDelayed(this, 45)
            }
        }
    }

    private val ptrs = SparseArray<Ptr>()

    // ---------- extra effects: ripples, bear bounce, typing combo ----------
    private class RP {
        var on = false
        var t0 = 0L
        var x = 0f
        var y = 0f
        var color = 0
    }

    private val rps = Array(6) { RP() }
    private var rpIdx = 0
    private var bearT0 = 0L
    private var combo = 0
    private var lastKeyAt = 0L

    init {
        contentDescription = "MATHRA keyboard"
        rebuild()
    }

    // =====================================================================
    // Public API used by the input service
    // =====================================================================
    fun reload() {
        rebuild()
        requestLayout()
        invalidate()
    }

    fun showLayer(l: Layer) {
        layer = l
        if (l == Layer.EN || l == Layer.SI) letterLayer = l
        if (l != Layer.EN) shift = 0
        rebuild()
        invalidate()
        listener?.onLayerChanged()
    }

    fun setShift(v: Int) {
        if (shift != v) {
            shift = v
            invalidate()
        }
    }

    fun setEnterLabel(s: String) {
        if (enterLabel != s) {
            enterLabel = s
            invalidate()
        }
    }

    fun setSuggestions(a: String, b: String, c: String) {
        if (suggestions[0] == a && suggestions[1] == b && suggestions[2] == c) return
        suggestions[0] = a
        suggestions[1] = b
        suggestions[2] = c
        invalidate()
    }

    /** Little sparkle burst (used when the theme changes). */
    fun celebrate() {
        if (!effectsOn) return
        val cx = toolbarH / 2f
        val cy = toolbarH / 2f
        repeat(14) {
            val a = Random.nextFloat() * 6.2831f
            val sp0 = r(60f, 170f) * dp
            emit(cx, cy, cos(a) * sp0, sin(a) * sp0, r(0.5f, 0.9f), r(10f, 16f) * sp,
                theme.sparkle[it % 3], glyphs[it % 3], 60f * dp)
        }
        invalidate()
    }

    // =====================================================================
    // Layout
    // =====================================================================
    private fun rebuild() {
        rows = Layouts.build(layer, numberRow, siPage, emojiCat)
        layoutKeys()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val baseRows = if (numberRow) 5 else 4
        var rowH = 50f * dp * heightScale
        val maxTotal = resources.displayMetrics.heightPixels * 0.55f   // keep landscape usable
        rowH = min(rowH, (maxTotal - toolbarH - padTop - padBottom) / baseRows)
        val h = toolbarH + padTop + padBottom + rowH * baseRows
        setMeasuredDimension(w, h.toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        sugLeft = toolbarH
        sugRight = w - iconW * 3
        layoutKeys()
    }

    private fun layoutKeys() {
        if (width == 0 || height == 0 || rows.isEmpty()) return
        val areaTop = toolbarH + padTop
        val areaH = height - areaTop - padBottom
        kh = areaH / rows.size
        val aw = width - 2 * padSide
        for ((ri, row) in rows.withIndex()) {
            var total = 0f
            for (k in row) total += k.weight
            var x = padSide
            for (k in row) {
                val kw = aw * k.weight / total
                k.x = x + gap / 2
                k.w = kw - gap
                k.y = areaTop + ri * kh + gap / 2
                k.h = kh - gap
                x += kw
            }
        }
        buildShaders()
    }

    private fun buildShaders() {
        val t = theme
        bgShader = LinearGradient(0f, 0f, 0f, height.toFloat(), t.bgTop, t.bgBottom, Shader.TileMode.CLAMP)
        val h = if (kh > 0) kh else 40 * dp
        keyShader = LinearGradient(0f, 0f, 0f, h, t.keyTop, t.keyBottom, Shader.TileMode.CLAMP)
        spShader = LinearGradient(0f, 0f, 0f, h, t.spTop, t.spBottom, Shader.TileMode.CLAMP)
    }

    private fun keyAt(x: Float, y: Float): Key? {
        if (rows.isEmpty() || kh <= 0f) return null
        val areaTop = toolbarH + padTop
        val ri = ((y - areaTop) / kh).toInt().coerceIn(0, rows.size - 1)
        var best: Key? = null
        var bestScore = Float.MAX_VALUE
        for (k in rows[ri]) {
            if (k.code == Code.SPACER) continue
            val dx = if (x < k.x) k.x - x else if (x > k.x + k.w) x - (k.x + k.w) else 0f
            val score = dx * 1000f + abs(x - (k.x + k.w / 2))
            if (score < bestScore) {
                bestScore = score
                best = k
            }
        }
        return best
    }

    private fun toolAt(x: Float): Int {
        if (x < toolbarH) return T_BRAND
        if (x > width - iconW) return T_SET
        if (x > width - 2 * iconW) return T_MIC
        if (x > width - 3 * iconW) return T_CLIP
        val slotW = (sugRight - sugLeft) / 3f
        val slot = ((x - sugLeft) / slotW).toInt().coerceIn(0, 2)
        return if (suggestions[slot].isNotBlank()) T_SUG + slot else 0
    }

    // =====================================================================
    // Drawing
    // =====================================================================
    private fun withAlpha(color: Int, a: Int): Int = (color and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24)

    private fun isSpecial(k: Key): Boolean = when {
        k.code <= Code.TAB0 && k.code > Code.TAB0 - 20 -> (Code.TAB0 - k.code) == emojiCat
        else -> k.code == Code.SHIFT || k.code == Code.DEL || k.code == Code.ENTER || k.code == Code.SYM ||
            k.code == Code.ABC || k.code == Code.LANG || k.code == Code.SYM2 || k.code == Code.PAGE ||
            k.code == Code.EMOJI
    }

    private fun isAnimating(k: Key, now: Long) = k.down || (k.relStart >= 0 && now - k.relStart < 520)

    private fun keyScale(k: Key, now: Long): Float {
        val pop = if (effectsOn) POP else 0.03f
        if (k.down) {
            val t = (now - k.popStart) / 1000f
            return 1f + pop * (1f - exp(-t * 35f))
        }
        if (k.relStart >= 0) {
            val t = (now - k.relStart) / 1000f
            if (t < 0.5f) {
                val amp = if (effectsOn) k.relAmp else k.relAmp * 0.2f
                return 1f + amp * exp(-9f * t) * cos(22f * t)
            }
        }
        return 1f
    }

    private var drawFailed = false

    override fun onDraw(c: Canvas) {
        try {
            drawAll(c)
        } catch (e: Throwable) {
            if (!drawFailed) {
                drawFailed = true
                CrashLog.record(context, "KeyboardView.onDraw", e)
            }
        }
    }

    private fun drawAll(c: Canvas) {
        val now = SystemClock.uptimeMillis()
        val dt = if (lastFrame == 0L) 0.016f else ((now - lastFrame) / 1000f).coerceIn(0f, 0.05f)
        lastFrame = now
        var animating = false

        // background
        fill.shader = bgShader
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fill)
        fill.shader = null
        drawDecor(c)
        drawToolbar(c)

        // keys at rest first, animated keys on top so the "pop" overlaps neighbours
        for (pass in 0..1) {
            for (row in rows) {
                for (k in row) {
                    if (k.code == Code.SPACER) continue
                    val anim = isAnimating(k, now)
                    if ((pass == 1) != anim) continue
                    if (anim) animating = true
                    drawKey(c, k, if (anim) keyScale(k, now) else 1f)
                }
            }
        }

        if (drawFloatLetters(c, now)) animating = true
        if (drawParticles(c, now, dt)) animating = true
        if (drawRipples(c, now)) animating = true
        if (bearT0 > 0 && now - bearT0 < 650) animating = true

        if (animating) postInvalidateOnAnimation() else lastFrame = 0L
    }

    private fun drawDecor(c: Canvas) {
        val a = if (theme.dark) 50 else 70
        text.typeface = Typeface.DEFAULT
        for ((i, d) in decor.withIndex()) {
            text.color = withAlpha(theme.sparkle[i % 3], a)
            text.textSize = d[3] * sp
            c.drawText(glyphs[d[2].toInt()], d[0] * width, d[1] * height + text.textSize / 3f, text)
        }
    }

    private fun drawToolbar(c: Canvas) {
        // bear button (tap = next theme, long press = settings)
        stroke.strokeWidth = 1.5f * dp
        stroke.color = withAlpha(theme.accent, 140)
        c.drawCircle(toolbarH / 2f, toolbarH / 2f + 1 * dp, toolbarH * 0.38f, stroke)
        val bt = (SystemClock.uptimeMillis() - bearT0) / 1000f
        val bx = toolbarH / 2f
        val by = toolbarH / 2f + 2 * dp
        if (bearT0 > 0 && bt < 0.65f) {
            c.save()
            c.rotate(10f * exp(-6f * bt) * sin(18f * bt), bx, by)
            val bs = 1f + 0.2f * exp(-7f * bt) * cos(20f * bt)
            c.scale(bs, bs, bx, by)
            Art.drawBear(c, bx, by, toolbarH * 0.27f, true)
            c.restore()
        } else {
            Art.drawBear(c, bx, by, toolbarH * 0.27f, true)
        }

        // right-hand shortcut icons
        text.typeface = Typeface.DEFAULT
        text.color = theme.keyText
        text.textSize = 18 * sp
        val iy = toolbarH / 2f - (text.ascent() + text.descent()) / 2f
        c.drawText("\u2699\uFE0F", width - iconW / 2f, iy, text)
        c.drawText("\uD83C\uDFA4", width - iconW * 1.5f, iy, text)
        c.drawText("\uD83D\uDCCB", width - iconW * 2.5f, iy, text)

        // suggestion strip or brand
        val any = suggestions[0].isNotBlank() || suggestions[1].isNotBlank() || suggestions[2].isNotBlank()
        if (any) {
            val slotW = (sugRight - sugLeft) / 3f
            text.typeface = tfMedium
            text.textSize = 15 * sp
            text.color = theme.keyText
            val ty = toolbarH / 2f - (text.ascent() + text.descent()) / 2f
            for (i in 0..2) {
                val cx = sugLeft + slotW * (i + 0.5f)
                val s = suggestions[i]
                if (s.isNotBlank()) {
                    text.color = if (i == 1) theme.accent else theme.keyText
                    c.drawText(ellipsize(s, slotW - 12 * dp), cx, ty, text)
                }
                if (i > 0) {
                    stroke.strokeWidth = 1f * dp
                    stroke.color = withAlpha(theme.keyText, 50)
                    c.drawLine(sugLeft + slotW * i, toolbarH * 0.25f, sugLeft + slotW * i, toolbarH * 0.75f, stroke)
                }
            }
        } else {
            text.typeface = tfBold
            text.textSize = 13 * sp
            text.letterSpacing = 0.12f
            text.color = theme.accent
            val ty = toolbarH / 2f - (text.ascent() + text.descent()) / 2f
            c.drawText("\uD83D\uDC51 MATHRA KEYBOARD", (sugLeft + sugRight) / 2f, ty, text)
            text.letterSpacing = 0f
        }
    }

    private fun ellipsize(s: String, maxW: Float): String {
        if (text.measureText(s) <= maxW) return s
        var n = s.length
        while (n > 1 && text.measureText(s, 0, n) + text.measureText("\u2026") > maxW) n--
        return s.substring(0, n) + "\u2026"
    }

    private fun drawKey(c: Canvas, k: Key, s: Float) {
        val depth = 3 * dp
        val special = isSpecial(k)
        val lift = -(s - 1f) * k.h * 0.55f
        c.save()
        c.translate(k.x + k.w / 2f, k.y + k.h / 2f + lift)
        c.scale(s, s)
        c.translate(-k.w / 2f, -k.h / 2f)

        val r = min(k.h, k.w) * 0.26f
        val fh = k.h - depth

        // 3D side / shadow
        fill.color = if (special) theme.spSide else theme.keySide
        rect.set(0f, depth, k.w, k.h)
        c.drawRoundRect(rect, r, r, fill)

        // glossy face
        fill.shader = if (special) spShader else keyShader
        rect.set(0f, 0f, k.w, fh)
        c.drawRoundRect(rect, r, r, fill)
        fill.shader = null

        // gloss highlight
        fill.color = Color.argb(if (theme.dark) 34 else 110, 255, 255, 255)
        rect.set(2 * dp, 1.5f * dp, k.w - 2 * dp, fh * 0.46f)
        c.drawRoundRect(rect, r * 0.8f, r * 0.8f, fill)

        // neon rim on dark themes
        if (theme.dark) {
            stroke.strokeWidth = 1f * dp
            stroke.color = withAlpha(theme.glow, 110)
            rect.set(0.5f * dp, 0.5f * dp, k.w - 0.5f * dp, fh - 0.5f * dp)
            c.drawRoundRect(rect, r, r, stroke)
        }

        // pressed glow
        if (k.down) {
            fill.color = Color.argb(60, 255, 255, 255)
            rect.set(0f, 0f, k.w, fh)
            c.drawRoundRect(rect, r, r, fill)
            if (extraOn && effectsOn) {
                stroke.strokeWidth = 2f * dp
                stroke.color = withAlpha(theme.accent, 200)
                c.drawRoundRect(rect, r, r, stroke)
            }
        }

        drawKeyContent(c, k, fh, special)
        c.restore()
    }

    private fun drawKeyContent(c: Canvas, k: Key, fh: Float, special: Boolean) {
        val col = if (special) theme.spText else theme.keyText
        val cx = k.w / 2f
        val cy = fh / 2f
        stroke.color = col
        stroke.strokeWidth = 2 * dp

        when (k.code) {
            Code.SHIFT -> {
                val s = min(k.w, fh) * 0.22f
                path.reset()
                path.moveTo(cx, cy - s)
                path.lineTo(cx + s, cy)
                path.lineTo(cx + s * 0.4f, cy)
                path.lineTo(cx + s * 0.4f, cy + s)
                path.lineTo(cx - s * 0.4f, cy + s)
                path.lineTo(cx - s * 0.4f, cy)
                path.lineTo(cx - s, cy)
                path.close()
                if (shift != 0) {
                    fill.color = col
                    c.drawPath(path, fill)
                    if (shift == 2) {
                        c.drawLine(cx - s * 0.4f, cy + s * 1.35f, cx + s * 0.4f, cy + s * 1.35f, stroke)
                    }
                } else {
                    c.drawPath(path, stroke)
                }
            }
            Code.DEL -> {
                val s = min(k.w, fh) * 0.2f
                path.reset()
                path.moveTo(cx - s * 1.3f, cy)
                path.lineTo(cx - s * 0.5f, cy - s * 0.9f)
                path.lineTo(cx + s * 1.3f, cy - s * 0.9f)
                path.lineTo(cx + s * 1.3f, cy + s * 0.9f)
                path.lineTo(cx - s * 0.5f, cy + s * 0.9f)
                path.close()
                c.drawPath(path, stroke)
                c.drawLine(cx - s * 0.1f, cy - s * 0.4f, cx + s * 0.7f, cy + s * 0.4f, stroke)
                c.drawLine(cx + s * 0.7f, cy - s * 0.4f, cx - s * 0.1f, cy + s * 0.4f, stroke)
            }
            Code.ENTER -> {
                if (enterLabel.isEmpty()) {
                    val s = min(k.w, fh) * 0.2f
                    path.reset()
                    path.moveTo(cx + s, cy - s)
                    path.lineTo(cx + s, cy + s * 0.2f)
                    path.lineTo(cx - s, cy + s * 0.2f)
                    c.drawPath(path, stroke)
                    path.reset()
                    path.moveTo(cx - s + s * 0.6f, cy - s * 0.4f)
                    path.lineTo(cx - s, cy + s * 0.2f)
                    path.lineTo(cx - s + s * 0.6f, cy + s * 0.8f)
                    c.drawPath(path, stroke)
                } else {
                    drawLabel(c, enterLabel, cx, cy, fh * 0.3f, col, false)
                }
            }
            Code.SPACE -> {
                val bearR = fh * 0.3f
                text.typeface = tfBold
                text.textSize = fh * 0.26f
                text.letterSpacing = 0.15f
                val tw = text.measureText("MATHRA")
                val total = bearR * 2f + 6 * dp + tw
                if (k.w > total + 24 * dp) {
                    val x0 = cx - total / 2f
                    Art.drawBear(c, x0 + bearR, cy + 1 * dp, bearR)
                    text.color = withAlpha(theme.accent, 230)
                    text.textAlign = Paint.Align.LEFT
                    c.drawText("MATHRA", x0 + bearR * 2f + 6 * dp, cy - (text.ascent() + text.descent()) / 2f, text)
                    text.textAlign = Paint.Align.CENTER
                }
                text.letterSpacing = 0f
            }
            else -> {
                val label = k.labelFor(shift)
                val emojiGrid = layer == Layer.EMOJI && k.code == 0
                val size = when {
                    emojiGrid -> min(k.h * 0.58f, k.w * 0.62f)
                    k.code <= Code.TAB0 && k.code > Code.TAB0 - 20 -> min(k.h * 0.5f, k.w * 0.55f)
                    label.length > 2 && k.code != 0 -> fh * 0.3f
                    layer == Layer.SI -> fh * 0.42f
                    else -> fh * 0.46f
                }
                drawLabel(c, label, cx, cy, size, col, !emojiGrid)
                // long-press hint (digit on the top row when the number row is hidden)
                if (k.alt != null && !numberRow && layer == Layer.EN) {
                    text.typeface = Typeface.DEFAULT
                    text.textSize = fh * 0.24f
                    text.color = withAlpha(col, 150)
                    text.textAlign = Paint.Align.RIGHT
                    c.drawText(k.alt, k.w - 5 * dp, fh * 0.3f, text)
                    text.textAlign = Paint.Align.CENTER
                }
            }
        }
    }

    private fun drawLabel(c: Canvas, s: String, cx: Float, cy: Float, size: Float, color: Int, medium: Boolean) {
        text.typeface = if (medium) tfMedium else Typeface.DEFAULT
        text.textSize = size
        text.color = color
        c.drawText(s, cx, cy - (text.ascent() + text.descent()) / 2f, text)
    }

    private fun drawFloatLetters(c: Canvas, now: Long): Boolean {
        var any = false
        for (f in fls) {
            if (!f.on) continue
            val t = (now - f.t0) / 480f
            if (t >= 1f) {
                f.on = false
                continue
            }
            any = true
            val e = 1f - (1f - t) * (1f - t)
            val a = (255 * (1f - t * t * t)).toInt()
            val bw = f.w * 1.15f
            val bh = f.h * 1.05f
            var cy = f.y - bh * 0.62f - e * 26 * dp
            if (cy < bh / 2f + 2 * dp) cy = bh / 2f + 2 * dp
            rect.set(f.x - bw / 2f, cy - bh / 2f, f.x + bw / 2f, cy + bh / 2f)
            fill.color = withAlpha(theme.spTop, a)
            c.drawRoundRect(rect, bh * 0.3f, bh * 0.3f, fill)
            stroke.strokeWidth = 1.5f * dp
            stroke.color = withAlpha(theme.accent, a)
            c.drawRoundRect(rect, bh * 0.3f, bh * 0.3f, stroke)
            text.typeface = tfBold
            text.textSize = bh * 0.58f
            text.color = withAlpha(theme.spText, a)
            c.drawText(f.s, f.x, cy - (text.ascent() + text.descent()) / 2f, text)
        }
        return any
    }

    private fun drawRipples(c: Canvas, now: Long): Boolean {
        var any = false
        for (p in rps) {
            if (!p.on) continue
            val t = (now - p.t0) / 450f
            if (t >= 1f) {
                p.on = false
                continue
            }
            any = true
            stroke.strokeWidth = 2f * dp
            stroke.color = withAlpha(p.color, (190 * (1f - t)).toInt())
            c.drawCircle(p.x, p.y, (8f + t * 44f) * dp, stroke)
        }
        return any
    }

    /** Ripple ring + bear bounce + "combo" burst every 10 quick key presses. */
    private fun extraFx(k: Key) {
        if (!effectsOn || !extraOn || k.code == Code.SPACER) return
        val now = SystemClock.uptimeMillis()
        val p = rps[rpIdx]
        rpIdx = (rpIdx + 1) % rps.size
        p.on = true
        p.t0 = now
        p.x = k.x + k.w / 2f
        p.y = k.y + k.h / 2f
        p.color = theme.sparkle[(combo + 1) % 3]
        bearT0 = now
        combo = if (now - lastKeyAt < 500) combo + 1 else 1
        lastKeyAt = now
        if (combo % 10 == 0 && particlesOn) {
            val bx = toolbarH / 2f
            val by = toolbarH / 2f
            emit(bx + 14 * dp, by, 0f, -50f * dp, 1.0f, 15f * sp, theme.accent, "x$combo", 0f)
            repeat(10) {
                val a = Random.nextFloat() * 6.2831f
                val s0 = r(70f, 170f) * dp
                emit(bx, by, cos(a) * s0, sin(a) * s0, r(0.6f, 1.0f), r(10f, 15f) * sp,
                    theme.sparkle[it % 3], glyphs[(it + 1) % 3], 80f * dp)
            }
        }
    }

    private fun drawParticles(c: Canvas, now: Long, dt: Float): Boolean {
        var any = false
        text.typeface = Typeface.DEFAULT
        for (p in ps) {
            if (!p.on) continue
            val age = (now - p.t0) / 1000f
            if (age >= p.life) {
                p.on = false
                continue
            }
            any = true
            p.vy += p.g * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            val a = (255 * (1f - age / p.life)).toInt()
            text.color = withAlpha(p.color, a)
            text.textSize = p.size
            c.drawText(p.glyph, p.x, p.y, text)
        }
        return any
    }

    // =====================================================================
    // Effects
    // =====================================================================
    private fun r(a: Float, b: Float) = a + (b - a) * Random.nextFloat()

    private fun emit(x: Float, y: Float, vx: Float, vy: Float, life: Float, size: Float, color: Int, glyph: String, g: Float) {
        val p = ps[pIdx]
        pIdx = (pIdx + 1) % ps.size
        p.on = true
        p.t0 = SystemClock.uptimeMillis()
        p.x = x
        p.y = y
        p.vx = vx
        p.vy = vy
        p.life = life
        p.size = size
        p.color = color
        p.glyph = glyph
        p.g = g
    }

    private fun spawnFor(k: Key) {
        if (!effectsOn || !particlesOn) return
        val cols = theme.sparkle
        val cx = k.x + k.w / 2f
        val top = k.y + k.h * 0.3f
        when {
            k.code == Code.SPACE -> repeat(6) {
                emit(k.x + r(0.15f, 0.85f) * k.w, top, r(-30f, 30f) * dp, r(-110f, -50f) * dp,
                    r(0.5f, 0.9f), r(9f, 13f) * sp, cols[it % 3], glyphs[0], 40f * dp)
            }
            k.code == Code.ENTER -> repeat(12) {
                val a = Random.nextFloat() * 6.2831f
                val s0 = r(90f, 200f) * dp
                emit(cx, k.y + k.h / 2f, cos(a) * s0, sin(a) * s0, r(0.5f, 0.9f), r(10f, 15f) * sp,
                    cols[it % 3], glyphs[2], 200f * dp)
            }
            k.code == Code.DEL -> repeat(5) {
                emit(cx + r(-6f, 6f) * dp, k.y + k.h / 2f, r(-90f, -20f) * dp, r(-40f, 20f) * dp,
                    r(0.35f, 0.6f), r(8f, 12f) * sp, theme.keyText, "\u2022", 150f * dp)
            }
            k.code == Code.EMOJI || (layer == Layer.EMOJI && k.code == 0) -> repeat(5) {
                emit(cx + r(-10f, 10f) * dp, top, r(-25f, 25f) * dp, r(-160f, -90f) * dp,
                    r(0.6f, 1.0f), r(11f, 16f) * sp, cols[it % 3], glyphs[1], 30f * dp)
            }
            k.code == 0 -> repeat(3) {
                emit(cx + r(-8f, 8f) * dp, top, r(-40f, 40f) * dp, r(-130f, -70f) * dp,
                    r(0.45f, 0.8f), r(9f, 13f) * sp, cols[it % 3], glyphs[it % 2], 110f * dp)
            }
        }
    }

    private fun floatLetter(k: Key) {
        if (!effectsOn || k.code != 0 || layer == Layer.EMOJI) return
        val f = fls[flIdx]
        flIdx = (flIdx + 1) % fls.size
        f.on = true
        f.t0 = SystemClock.uptimeMillis()
        f.x = k.x + k.w / 2f
        f.y = k.y
        f.w = k.w
        f.h = k.h
        f.s = k.labelFor(shift).replace("\u25CC", "")
    }

    // =====================================================================
    // Touch handling (multi-touch so fast two-thumb typing works)
    // =====================================================================
    override fun onTouchEvent(e: MotionEvent): Boolean {
        try {
            handleTouch(e)
        } catch (t: Throwable) {
            CrashLog.record(context, "KeyboardView.onTouchEvent", t)
        }
        return true
    }

    private fun handleTouch(e: MotionEvent) {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                down(e.getPointerId(i), e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) move(e.getPointerId(i), e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val i = e.actionIndex
                up(e.getPointerId(i), false)
            }
            MotionEvent.ACTION_CANCEL -> {
                for (i in ptrs.size() - 1 downTo 0) up(ptrs.keyAt(i), true)
            }
        }
    }

    private fun down(id: Int, x: Float, y: Float) {
        val p = Ptr()
        ptrs.put(id, p)
        p.downX = x
        p.lastX = x
        if (y < toolbarH) {
            p.tool = toolAt(x)
            if (p.tool == T_BRAND || p.tool == T_CLIP) handler.postDelayed(p.longRun, 450)
            return
        }
        val k = keyAt(x, y) ?: return
        press(p, k)
    }

    private fun kindOf(k: Key) = when (k.code) {
        Code.SPACE -> 1
        Code.DEL -> 2
        Code.ENTER -> 3
        else -> 0
    }

    private fun press(p: Ptr, k: Key) {
        p.key = k
        k.down = true
        k.popStart = SystemClock.uptimeMillis()
        k.relStart = -1L
        feedback.key(kindOf(k))
        when {
            k.code == Code.DEL -> {
                listener?.onDelete()
                handler.postDelayed(p.repRun, 400)
            }
            k.code == Code.SPACE || k.code == Code.LANG || k.code == Code.SHIFT -> handler.postDelayed(p.longRun, 400)
            k.code == 0 && k.alt != null -> handler.postDelayed(p.longRun, 380)
        }
        floatLetter(k)
        spawnFor(k)
        extraFx(k)
        invalidate()
    }

    private fun releaseVisual(k: Key) {
        val now = SystemClock.uptimeMillis()
        val t = (now - k.popStart) / 1000f
        val pop = if (effectsOn) POP else 0.03f
        k.relAmp = pop * (1f - exp(-t * 35f))
        k.relStart = now
        k.down = false
    }

    private fun move(id: Int, x: Float, y: Float) {
        val p = ptrs.get(id) ?: return
        val k = p.key ?: return
        if (p.longFired) return
        if (k.code == Code.SPACE) {
            if (!p.swiped && abs(x - p.downX) > 18 * dp) {
                p.swiped = true
                handler.removeCallbacks(p.longRun)
                p.lastX = x
            }
            if (p.swiped) {
                p.acc += x - p.lastX
                p.lastX = x
                val step = 14 * dp
                while (p.acc > step) {
                    listener?.onSpaceSwipe(1)
                    p.acc -= step
                }
                while (p.acc < -step) {
                    listener?.onSpaceSwipe(-1)
                    p.acc += step
                }
            }
            return
        }
        if (k.code == Code.DEL) return
        val m = 8 * dp
        if (x < k.x - m || x > k.x + k.w + m || y < k.y - m || y > k.y + k.h + m) {
            val nk = keyAt(x, y)
            if (nk != null && nk !== k) {
                handler.removeCallbacks(p.longRun)
                releaseVisual(k)
                press(p, nk)
            }
        }
    }

    private fun up(id: Int, cancel: Boolean) {
        val p = ptrs.get(id) ?: return
        ptrs.remove(id)
        handler.removeCallbacks(p.longRun)
        handler.removeCallbacks(p.repRun)
        val k = p.key
        if (k == null) {
            if (!cancel && !p.longFired && p.tool != 0) {
                if (p.tool >= T_SUG) listener?.onSuggestionTap(p.tool - T_SUG)
                else listener?.onToolbar(p.tool, false)
            }
            return
        }
        releaseVisual(k)
        invalidate()
        if (!cancel && !p.longFired && !p.swiped) commit(k)
    }

    private fun fireLong(p: Ptr) {
        val k = p.key
        if (k == null) {
            if (p.tool == T_BRAND || p.tool == T_CLIP) {
                p.longFired = true
                feedback.key(1)
                listener?.onToolbar(p.tool, true)
            }
            return
        }
        when {
            k.code == Code.SPACE || k.code == Code.LANG -> {
                p.longFired = true
                listener?.onPicker()
            }
            k.code == Code.SHIFT -> {
                p.longFired = true
                listener?.onSelectAll()
            }
            k.code == 0 && k.alt != null -> {
                p.longFired = true
                listener?.onText(k.alt)
                spawnFor(k)
            }
        }
        invalidate()
    }

    private fun commit(k: Key) {
        when {
            k.code == 0 -> {
                val t = k.textFor(if (layer == Layer.EN) shift else 0)
                if (shift == 1 && layer == Layer.EN) shift = 0
                listener?.onText(t)
            }
            k.code == Code.SPACE -> listener?.onSpace()
            k.code == Code.ENTER -> listener?.onEnter()
            k.code == Code.DEL -> { /* handled on press so holding repeats */ }
            k.code == Code.SHIFT -> {
                val now = SystemClock.uptimeMillis()
                shift = when {
                    now - lastShiftTap < 300 -> 2
                    shift == 0 -> 1
                    else -> 0
                }
                lastShiftTap = now
                invalidate()
            }
            k.code == Code.SYM -> showLayer(Layer.SYM1)
            k.code == Code.SYM2 -> showLayer(Layer.SYM2)
            k.code == Code.ABC -> showLayer(letterLayer)
            k.code == Code.EMOJI -> showLayer(Layer.EMOJI)
            k.code == Code.LANG -> showLayer(if (layer == Layer.SI) Layer.EN else Layer.SI)
            k.code == Code.PAGE -> {
                siPage = (siPage + 1) % 4
                rebuild()
                invalidate()
            }
            k.code <= Code.TAB0 && k.code > Code.TAB0 - 20 -> {
                emojiCat = Code.TAB0 - k.code
                rebuild()
                invalidate()
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacksAndMessages(null)
        for (i in 0 until ptrs.size()) ptrs.valueAt(i).key?.down = false
        ptrs.clear()
    }
}
