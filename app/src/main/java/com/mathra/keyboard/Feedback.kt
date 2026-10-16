package com.mathra.keyboard

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlin.math.sin

/**
 * Key sound + haptics.
 *  soundStyle: 0 = off, 1 = system click, 2 = "Teddy pop" (a tiny tone generated in code, no audio files).
 */
class Feedback(ctx: Context) {
    private val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    @Suppress("DEPRECATION")
    private val vib: Vibrator? = try {
        if (Build.VERSION.SDK_INT >= 31) {
            (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    } catch (e: Exception) {
        null
    }

    var soundStyle = 2
    var volume = 0.6f
    var haptic = true
    var hapticMs = 12

    private var tracks: Array<AudioTrack?>? = null

    /** kind: 0 = character, 1 = space, 2 = delete, 3 = enter */
    fun key(kind: Int) {
        if (haptic) buzz(if (kind == 0) hapticMs else hapticMs + 6)
        when (soundStyle) {
            1 -> {
                val fx = when (kind) {
                    1 -> AudioManager.FX_KEYPRESS_SPACEBAR
                    2 -> AudioManager.FX_KEYPRESS_DELETE
                    3 -> AudioManager.FX_KEYPRESS_RETURN
                    else -> AudioManager.FX_KEYPRESS_STANDARD
                }
                am.playSoundEffect(fx, volume)
            }
            2 -> pop(kind)
        }
    }

    private fun buzz(ms: Int) {
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                vib?.vibrate(VibrationEffect.createOneShot(ms.toLong(), VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vib?.vibrate(ms.toLong())
            }
        } catch (e: Throwable) {
            // vibration is best-effort
        }
    }

    private fun pop(kind: Int) {
        if (am.ringerMode != AudioManager.RINGER_MODE_NORMAL) return   // respect silent / vibrate mode
        val t = tracks ?: arrayOf(tone(1000f, 420f), tone(620f, 260f), tone(480f, 300f)).also { tracks = it }
        val track = t[if (kind == 0) 0 else if (kind == 2) 2 else 1] ?: return
        try {
            track.stop()
            track.setPlaybackHeadPosition(0)
            track.setVolume(volume)
            track.play()
        } catch (e: Throwable) {
            // ignore
        }
    }

    private fun tone(f0: Float, f1: Float): AudioTrack? = try {
        if (Build.VERSION.SDK_INT < 23) throw IllegalStateException("AudioTrack.Builder needs API 23")
        val sr = 22050
        val n = (sr * 0.06f).toInt()
        val data = ShortArray(n)
        var phase = 0.0
        for (i in 0 until n) {
            val t = i.toFloat() / n
            val f = f0 + (f1 - f0) * t
            phase += 2.0 * Math.PI * f / sr
            val attack = if (i < 40) i / 40f else 1f
            val env = (1f - t) * (1f - t) * attack
            data[i] = (sin(phase) * env * 9000.0).toInt().toShort()
        }
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sr)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(n * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(data, 0, n)
        track
    } catch (e: Throwable) {
        null
    }

    fun release() {
        tracks?.forEach { try { it?.release() } catch (e: Exception) { } }
        tracks = null
    }
}
