package com.mathra.keyboard

import android.content.Context
import android.content.res.Configuration

class MTheme(
    val name: String,
    val dark: Boolean,
    val bgTop: Int, val bgBottom: Int,
    val keyTop: Int, val keyBottom: Int, val keySide: Int, val keyText: Int,
    val spTop: Int, val spBottom: Int, val spSide: Int, val spText: Int,
    val accent: Int,
    val glow: Int,
    val sparkle: IntArray
)

object Themes {
    private fun c(v: Long) = v.toInt()

    val all: List<MTheme> = listOf(
        MTheme(
            "Neon Teddy", true,
            c(0xFF0B0820), c(0xFF1A0B33),
            c(0xFF2E2660), c(0xFF191340), c(0xFF0A0618), c(0xFFEDE7FF),
            c(0xFF4B27A0), c(0xFF2A1560), c(0xFF120830), c(0xFFFFFFFF),
            c(0xFF00E5FF), c(0xFFB36BFF),
            intArrayOf(c(0xFFFF4FD8), c(0xFF00E5FF), c(0xFFFFE14D))
        ),
        MTheme(
            "Pink Dream", false,
            c(0xFFFFE3F1), c(0xFFFFC2E0),
            c(0xFFFFFFFF), c(0xFFFFE9F4), c(0xFFE79AC3), c(0xFF8A2B5C),
            c(0xFFFF9CCB), c(0xFFFF6FB1), c(0xFFC94C8A), c(0xFFFFFFFF),
            c(0xFFFF3D94), c(0xFFFF8FC2),
            intArrayOf(c(0xFFFF6FB1), c(0xFFFFB3D9), c(0xFFFF3D94))
        ),
        MTheme(
            "Blue Galaxy", true,
            c(0xFF050B2E), c(0xFF0E1C5C),
            c(0xFF26387F), c(0xFF141F58), c(0xFF070C30), c(0xFFE3EEFF),
            c(0xFF3D5AFE), c(0xFF2336B8), c(0xFF0F1A6B), c(0xFFFFFFFF),
            c(0xFF7DF9FF), c(0xFF5C7CFF),
            intArrayOf(c(0xFF7DF9FF), c(0xFFB388FF), c(0xFFFFFFFF))
        ),
        MTheme(
            "Black Gold", true,
            c(0xFF0A0A0A), c(0xFF1C1710),
            c(0xFF2E2E2E), c(0xFF161616), c(0xFF000000), c(0xFFFFD86B),
            c(0xFFE8B22E), c(0xFFB8860B), c(0xFF6B4E07), c(0xFF1A1200),
            c(0xFFFFC83D), c(0xFFFFC83D),
            intArrayOf(c(0xFFFFD700), c(0xFFFFF3B0), c(0xFFFFA000))
        ),
        MTheme(
            "Cute Pastel", false,
            c(0xFFFDF1E4), c(0xFFF8DCC4),
            c(0xFFFFFFFF), c(0xFFFFF3E6), c(0xFFD9B79A), c(0xFF6B4A33),
            c(0xFFFFC6A5), c(0xFFFFA98A), c(0xFFD98866), c(0xFF5A2E1B),
            c(0xFFC9803F), c(0xFFFFB38A),
            intArrayOf(c(0xFFFFB3C7), c(0xFFB5EAD7), c(0xFFFFE6A7))
        )
    )

    fun resolve(ctx: Context, prefs: Prefs): MTheme {
        if (prefs.followSystem) {
            val night = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
            return all[if (night) 0 else 4]
        }
        return all[prefs.themeIndex.coerceIn(0, all.size - 1)]
    }
}
