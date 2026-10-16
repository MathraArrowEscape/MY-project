package com.mathra.keyboard

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/** Tiny vector teddy bear, drawn with Canvas primitives (no image assets, no allocations per call). */
object Art {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()

    fun drawBear(c: Canvas, cx: Float, cy: Float, r: Float, cap: Boolean = false) {
        p.style = Paint.Style.FILL
        p.shader = null

        // ears
        p.color = 0xFFB9722F.toInt()
        c.drawCircle(cx - r * 0.72f, cy - r * 0.72f, r * 0.42f, p)
        c.drawCircle(cx + r * 0.72f, cy - r * 0.72f, r * 0.42f, p)
        p.color = 0xFFF2C09B.toInt()
        c.drawCircle(cx - r * 0.72f, cy - r * 0.72f, r * 0.22f, p)
        c.drawCircle(cx + r * 0.72f, cy - r * 0.72f, r * 0.22f, p)

        // head
        p.color = 0xFFD58A45.toInt()
        c.drawCircle(cx, cy, r, p)

        // muzzle
        p.color = 0xFFF8DDBF.toInt()
        rect.set(cx - r * 0.45f, cy + r * 0.05f, cx + r * 0.45f, cy + r * 0.68f)
        c.drawOval(rect, p)

        // eyes + shine
        p.color = 0xFF2B1B12.toInt()
        c.drawCircle(cx - r * 0.38f, cy - r * 0.15f, r * 0.13f, p)
        c.drawCircle(cx + r * 0.38f, cy - r * 0.15f, r * 0.13f, p)
        p.color = Color.WHITE
        c.drawCircle(cx - r * 0.34f, cy - r * 0.19f, r * 0.045f, p)
        c.drawCircle(cx + r * 0.42f, cy - r * 0.19f, r * 0.045f, p)

        // nose
        p.color = 0xFF4A2A1A.toInt()
        rect.set(cx - r * 0.14f, cy + r * 0.08f, cx + r * 0.14f, cy + r * 0.26f)
        c.drawOval(rect, p)

        // smile
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.06f
        p.strokeCap = Paint.Cap.ROUND
        rect.set(cx - r * 0.22f, cy + r * 0.2f, cx, cy + r * 0.46f)
        c.drawArc(rect, 20f, 140f, false, p)
        rect.set(cx, cy + r * 0.2f, cx + r * 0.22f, cy + r * 0.46f)
        c.drawArc(rect, 20f, 140f, false, p)
        p.style = Paint.Style.FILL

        if (cap) {
            // black cap dome
            p.color = 0xFF14141C.toInt()
            rect.set(cx - r * 0.98f, cy - r * 1.1f, cx + r * 0.98f, cy + r * 0.3f)
            c.drawArc(rect, 180f, 180f, true, p)
            // brim
            rect.set(cx - r * 0.2f, cy - r * 0.5f, cx + r * 1.15f, cy - r * 0.3f)
            c.drawRoundRect(rect, r * 0.1f, r * 0.1f, p)
            // gold crown on the cap
            p.color = 0xFFFFC83D.toInt()
            val w = r * 0.6f
            val x0 = cx - w / 2f
            val base = cy - r * 0.62f
            val top = cy - r * 0.98f
            val mid = cy - r * 0.78f
            path.reset()
            path.moveTo(x0, base)
            path.lineTo(x0, top)
            path.lineTo(x0 + w * 0.25f, mid)
            path.lineTo(x0 + w * 0.5f, top)
            path.lineTo(x0 + w * 0.75f, mid)
            path.lineTo(x0 + w, top)
            path.lineTo(x0 + w, base)
            path.close()
            c.drawPath(path, p)
        }
    }
}
