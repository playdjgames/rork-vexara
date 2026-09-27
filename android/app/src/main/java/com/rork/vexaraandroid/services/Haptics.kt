package com.rork.vexaraandroid.services

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Arcade feedback mapped to the closest Android vibration effects. */
object Haptics {
    var enabled: Boolean = true
    private var vibrator: Vibrator? = null

    fun prepare(context: Context) {
        if (vibrator != null) return
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun tap() = pulse(predefined = 2, ms = 10, amplitude = 90)      // EFFECT_TICK
    fun kill() = pulse(predefined = 2, ms = 12, amplitude = 110)
    fun hit() = pulse(predefined = 0, ms = 22, amplitude = 180)     // EFFECT_CLICK
    fun heavyBlast() = pulse(predefined = 5, ms = 45, amplitude = 255) // EFFECT_HEAVY_CLICK
    fun success() = pulse(predefined = 1, ms = 30, amplitude = 160) // EFFECT_DOUBLE_CLICK
    fun failure() = pulse(predefined = 5, ms = 60, amplitude = 230)

    private fun pulse(predefined: Int, ms: Long, amplitude: Int) {
        if (!enabled) return
        val target = vibrator ?: return
        if (!target.hasVibrator()) return
        try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                    target.vibrate(VibrationEffect.createPredefined(predefined))
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ->
                    target.vibrate(VibrationEffect.createOneShot(ms, amplitude))
                else -> {
                    @Suppress("DEPRECATION")
                    target.vibrate(ms)
                }
            }
        } catch (error: Exception) {
            // Vibration is best-effort feedback.
        }
    }
}
