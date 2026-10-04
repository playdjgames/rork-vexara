package com.playdjgames.vexara.model

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

const val PI_F: Float = PI.toFloat()

private fun easeIn(t: Float): Float = t * t
private fun easeOut(t: Float): Float = 1f - (1f - t) * (1f - t)
private fun easeInOut(t: Float): Float =
    if (t < 0.5f) 2f * t * t else 1f - (-2f * t + 2f).pow(2) / 2f

enum class EntryStyle { SWEEP_LEFT, SWEEP_RIGHT, RISE_LEFT, RISE_RIGHT, PLUNGE_CENTER }

/** Cubic bezier flight path used when enemies swoop in from off-screen to their formation slot. */
class EntryPath(val p0: Offset, val p1: Offset, val p2: Offset, val p3: Offset) {
    fun point(t: Float): Offset {
        val u = 1f - t
        val a = u * u * u
        val b = 3f * u * u * t
        val c = 3f * u * t * t
        val d = t * t * t
        return Offset(
            a * p0.x + b * p1.x + c * p2.x + d * p3.x,
            a * p0.y + b * p1.y + c * p2.y + d * p3.y
        )
    }

    companion object {
        fun flat(point: Offset): EntryPath = EntryPath(point, point, point, point)

        fun make(style: EntryStyle, target: Offset, w: Float, h: Float): EntryPath = when (style) {
            EntryStyle.SWEEP_LEFT -> EntryPath(
                Offset(-70f, h * 0.12f), Offset(w * 0.34f, -40f), Offset(w * 0.92f, h * 0.42f), target
            )
            EntryStyle.SWEEP_RIGHT -> EntryPath(
                Offset(w + 70f, h * 0.12f), Offset(w * 0.66f, -40f), Offset(w * 0.08f, h * 0.42f), target
            )
            EntryStyle.RISE_LEFT -> EntryPath(
                Offset(w * 0.18f, h + 80f), Offset(-60f, h * 0.52f), Offset(w * 0.2f, -60f), target
            )
            EntryStyle.RISE_RIGHT -> EntryPath(
                Offset(w * 0.82f, h + 80f), Offset(w + 60f, h * 0.52f), Offset(w * 0.8f, -60f), target
            )
            EntryStyle.PLUNGE_CENTER -> EntryPath(
                Offset(w * 0.5f, -90f),
                Offset(w * 0.5f, h * 0.46f),
                Offset(if (target.x < w * 0.5f) w * 0.06f else w * 0.94f, h * 0.3f),
                target
            )
        }
    }
}

/** Attack trajectories enemies take when they break formation. */
enum class DivePattern(val duration: Float) {
    SWOOP(2.5f), ZIGZAG(2.9f), SPIRAL(3.0f), SPLIT(2.7f), ORBIT(3.2f), PLUNGE(1.9f), BOOMERANG(3.1f);

    /** Whether the dive leaves through the top of the screen instead of the bottom. */
    val exitsUpward: Boolean get() = this == BOOMERANG

    fun position(t: Float, start: Offset, target: Offset, w: Float, h: Float, side: Float): Offset {
        val exitY = h + 110f
        val travel = exitY - start.y
        val s = side
        return when (this) {
            SWOOP -> Offset(
                start.x + sin(t * PI_F * 1.7f) * 165f * s + (target.x - start.x) * t * 0.5f,
                start.y + travel * easeIn(t)
            )
            ZIGZAG -> Offset(
                start.x + sin(t * PI_F * 7f) * 58f * s + (target.x - start.x) * t * 0.55f,
                start.y + travel * t
            )
            SPIRAL -> {
                val r = 26f + t * 138f
                val a = t * PI_F * 4.2f * s
                Offset(start.x + cos(a) * r, start.y + travel * easeIn(t) + sin(a) * 24f)
            }
            SPLIT -> {
                val out = min(1f, t / 0.38f)
                val outX = start.x + 155f * s * sin(out * PI_F / 2f)
                val cut = max(0f, (t - 0.38f) / 0.62f)
                Offset(outX + (target.x - outX) * easeOut(cut), start.y + travel * easeIn(t))
            }
            ORBIT -> {
                val a = t * PI_F * 2f * s
                val r = 128f * (1f - t * 0.35f)
                Offset(start.x + sin(a) * r, start.y + travel * t + (1f - cos(a)) * 34f)
            }
            PLUNGE -> Offset(
                start.x + (target.x - start.x) * easeOut(min(1f, t * 1.6f)),
                start.y + travel * easeIn(t)
            )
            BOOMERANG -> {
                val low = h * 0.78f
                val y = if (t <= 0.55f) {
                    start.y + (low - start.y) * easeOut(t / 0.55f)
                } else {
                    val u = (t - 0.55f) / 0.45f
                    low + (-120f - low) * easeIn(u)
                }
                Offset(start.x + sin(t * PI_F * 2f) * 132f * s, y)
            }
        }
    }
}

/** Elaborate non-stop routes used by bonus challenge waves. */
enum class ChallengePath(val duration: Float) {
    FIGURE_EIGHT(7.6f), DOUBLE_LOOP(8.2f), SERPENTINE(6.4f), CROSS_DOWN(5.8f), BLOOM(8.6f);

    fun position(t: Float, w: Float, h: Float, side: Float): Offset {
        val s = side
        return when (this) {
            FIGURE_EIGHT -> {
                val a = t * PI_F * 2f
                Offset(w * 0.5f + sin(a * 2f) * w * 0.36f * s, -80f + (h + 160f) * t + sin(a) * h * 0.1f)
            }
            DOUBLE_LOOP -> {
                val a = t * PI_F * 4f * s
                Offset(w * 0.5f + cos(a) * w * 0.34f, -80f + (h + 160f) * easeInOut(t) + sin(a) * 40f)
            }
            SERPENTINE -> Offset(
                w * 0.5f + sin(t * PI_F * 3.2f) * w * 0.42f * s,
                -80f + (h + 160f) * t
            )
            CROSS_DOWN -> {
                val startX = if (side > 0f) -60f else w + 60f
                val endX = if (side > 0f) w + 60f else -60f
                Offset(
                    startX + (endX - startX) * t,
                    -60f + (h + 130f) * easeInOut(t) + sin(t * PI_F * 2f) * 60f
                )
            }
            BLOOM -> {
                val a = t * PI_F * 2.4f * s
                val r = w * 0.12f + t * w * 0.38f
                Offset(w * 0.5f + cos(a) * r, h * 0.26f + sin(a) * r * 0.62f + t * h * 0.5f - 60f)
            }
        }
    }
}
