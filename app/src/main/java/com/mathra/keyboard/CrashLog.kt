package com.mathra.keyboard

import android.app.Activity
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Saves the last crash/error to a private file on the phone (never sent anywhere) so the next launch can
 * show it on screen. It holds only the error stack trace, never typed text.
 */
object CrashLog {
    private const val FILE = "last_crash.txt"

    fun install(ctx: Context) {
        val app = ctx.applicationContext
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            record(app, "Uncaught crash on thread ${t.name}", e)
            prev?.uncaughtException(t, e)
        }
    }

    fun record(ctx: Context, where: String, e: Throwable) {
        try {
            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            val head = "MATHRA KEYBOARD error\n$where\n" +
                "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}\n\n"
            File(ctx.applicationContext.filesDir, FILE).writeText(head + sw.toString())
        } catch (t: Throwable) {
            // nothing more we can do
        }
    }

    fun read(ctx: Context): String? = try {
        val f = File(ctx.applicationContext.filesDir, FILE)
        if (f.exists()) f.readText() else null
    } catch (t: Throwable) {
        null
    }

    fun clear(ctx: Context) {
        try { File(ctx.applicationContext.filesDir, FILE).delete() } catch (t: Throwable) { }
    }

    /** Plain full-screen error report with Copy (and optional Continue) buttons. */
    fun reportView(act: Activity, text: String, onContinue: (() -> Unit)?): View {
        val d = act.resources.displayMetrics.density
        fun px(v: Int) = (v * d).toInt()
        val col = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF120B2B.toInt())
            setPadding(px(16), px(32), px(16), px(16))
        }
        col.addView(TextView(act).apply {
            this.text = "MATHRA KEYBOARD hit an error"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFFFFC83D.toInt())
        })
        col.addView(TextView(act).apply {
            this.text = "Tap Copy and send this text to the developer so it can be fixed."
            textSize = 13f
            setTextColor(0xFFC9BCE8.toInt())
            setPadding(0, px(4), 0, px(8))
        })
        val sv = ScrollView(act)
        sv.addView(TextView(act).apply {
            this.text = text
            textSize = 11f
            setTextColor(0xFFFFFFFF.toInt())
            setTextIsSelectable(true)
        })
        col.addView(sv, LinearLayout.LayoutParams(-1, 0, 1f))
        val row = LinearLayout(act).apply { gravity = Gravity.CENTER }
        row.addView(Button(act).apply {
            this.text = "Copy"
            setOnClickListener {
                val cm = act.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("MATHRA error", text))
            }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        if (onContinue != null) {
            row.addView(Button(act).apply {
                this.text = "Continue"
                setOnClickListener { onContinue() }
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        col.addView(row, LinearLayout.LayoutParams(-1, -2))
        return col
    }
}

class MathraApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
    }
}
