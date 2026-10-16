package com.mathra.keyboard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.sin
import kotlin.random.Random

class SplashActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private val goNext = Runnable {
        startActivity(Intent(this, SettingsActivity::class.java))
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun px(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val crash = CrashLog.read(this)
        if (crash != null) {
            setContentView(CrashLog.reportView(this, crash) {
                CrashLog.clear(this)
                recreate()
            })
            return
        }

        val root = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xFF000000.toInt(), 0xFF1A0B33.toInt())
            )
        }
        root.addView(SparkleView(this), FrameLayout.LayoutParams(-1, -1))

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.mathra_logo)
            alpha = 0f
            scaleX = 0.4f
            scaleY = 0.4f
        }
        val title = TextView(this).apply {
            text = "MATHRA KEYBOARD"
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFFFFC83D.toInt())
            letterSpacing = 0.08f
            gravity = Gravity.CENTER
            alpha = 0f
            translationY = px(20).toFloat()
        }
        val sub = TextView(this).apply {
            text = "Premium 3D Teddy Typing"
            textSize = 14f
            setTextColor(0xFFC9BCE8.toInt())
            gravity = Gravity.CENTER
            alpha = 0f
        }
        col.addView(logo, LinearLayout.LayoutParams(px(230), px(230)))
        col.addView(title, LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(16) })
        col.addView(sub, LinearLayout.LayoutParams(-2, -2).apply { topMargin = px(4) })
        root.addView(col, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)

        logo.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(750)
            .setInterpolator(OvershootInterpolator(1.7f)).start()
        title.animate().alpha(1f).translationY(0f).setStartDelay(450).setDuration(500).start()
        sub.animate().alpha(1f).setStartDelay(750).setDuration(500).start()

        handler.postDelayed(goNext, 2000)
    }

    override fun onDestroy() {
        handler.removeCallbacks(goNext)
        super.onDestroy()
    }

    /** Twinkling hearts, stars and sparkles behind the logo. */
    private class SparkleView(ctx: Context) : View(ctx) {
        private val glyphs = arrayOf("\u2726", "\u2665", "\u2605")
        private val colors = intArrayOf(0xFFFFC83D.toInt(), 0xFFFF4FD8.toInt(), 0xFF00E5FF.toInt(), 0xFFB36BFF.toInt())
        private val n = 26
        private val fx = FloatArray(n) { Random.nextFloat() }
        private val fy = FloatArray(n) { Random.nextFloat() }
        private val ph = FloatArray(n) { Random.nextFloat() * 6.28f }
        private val sz = FloatArray(n) { 10f + Random.nextFloat() * 14f }
        private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
        private val d = resources.displayMetrics.density

        override fun onDraw(c: Canvas) {
            val t = SystemClock.uptimeMillis() / 1000f
            for (i in 0 until n) {
                val a = (0.5f + 0.5f * sin(t * 2.2f + ph[i]))
                p.color = (colors[i % colors.size] and 0x00FFFFFF) or ((a * 200).toInt() shl 24)
                p.textSize = sz[i] * d
                c.drawText(glyphs[i % 3], fx[i] * width, fy[i] * height - (t * 8f * d) % 20f, p)
            }
            postInvalidateOnAnimation()
        }
    }
}
