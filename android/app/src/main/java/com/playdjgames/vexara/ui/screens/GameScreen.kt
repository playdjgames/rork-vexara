package com.playdjgames.vexara.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.playdjgames.vexara.R
import com.playdjgames.vexara.game.Battlefield
import com.playdjgames.vexara.game.GameEngine
import com.playdjgames.vexara.model.GamePhase
import com.playdjgames.vexara.ui.components.MenuBackdrop
import com.playdjgames.vexara.ui.theme.Vex
import kotlin.math.abs

/** The live battlefield: backdrop, canvas renderer, one-thumb controls, HUD and toast. */
@Composable
fun GameScreen(engine: GameEngine) {
    // Simulation clock: runs only while the phase is PLAYING.
    LaunchedEffect(engine.phase) {
        if (engine.phase != GamePhase.PLAYING) return@LaunchedEffect
        var last = 0L
        while (engine.phase == GamePhase.PLAYING) {
            withFrameNanos { now ->
                if (last != 0L) {
                    val dt = ((now - last) / 1_000_000_000f).coerceAtMost(1f / 30f)
                    engine.update(dt)
                }
                last = now
            }
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Vex.voidDeep)
            .safeDrawingPadding(),
        contentAlignment = Alignment.Center
    ) {
        // Letterboxes wide screens so the battlefield stays a vertical arcade screen.
        val playWidth = min(maxWidth, maxHeight * Vex.PLAYFIELD_ASPECT)
        val playHeight = maxHeight
        val letterboxed = playWidth < maxWidth - 1.dp

        LaunchedEffect(playWidth, playHeight) {
            engine.updateSize(playWidth.value, playHeight.value)
        }

        Box(
            Modifier
                .size(playWidth, playHeight)
                .clipToBounds()
                .then(if (letterboxed) Modifier.border(1.dp, Vex.magenta.copy(alpha = 0.18f)) else Modifier)
        ) {
            MenuBackdrop(
                imageRes = if (engine.isBossWave) R.drawable.nebula_storm_bg else R.drawable.space_nebula_bg,
                dim = if (engine.isBossWave) 0.46f else 0.38f
            )
            Battlefield(engine, Modifier.fillMaxSize())
            ControlSurface(engine)
            GameHud(engine, modifier = Modifier.padding(top = 4.dp))
        }

        AnimatedVisibility(
            visible = engine.achievementToast != null,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 20.dp)
                .padding(top = 80.dp)
        ) {
            engine.achievementToast?.let { AchievementToastView(it) }
        }
    }
}

/** Max finger travel (dp) and duration (ms) for a touch to count as a tap rather than a steer. */
private const val TAP_SLOP_DP = 12f
private const val TAP_MAX_MS = 260L

/**
 * One-thumb surface. Dragging steers relative to where the finger landed; a quick tap
 * anywhere fires the screen-clear blast. Shots fire automatically.
 */
@Composable
private fun ControlSurface(engine: GameEngine) {
    val density = LocalDensity.current.density
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(engine) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startTime = down.uptimeMillis
                    val anchorX = down.position.x / density
                    val anchorY = down.position.y / density
                    val shipAnchor = engine.player.position.x
                    engine.dragTarget = shipAnchor

                    var pointerId = down.id
                    var maxTravel = 0f
                    var endTime = startTime
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == pointerId } ?: event.changes.firstOrNull()
                        if (change == null) break
                        endTime = change.uptimeMillis
                        if (!change.pressed) break
                        pointerId = change.id
                        val dx = change.position.x / density - anchorX
                        val dy = change.position.y / density - anchorY
                        maxTravel = maxOf(maxTravel, abs(dx), abs(dy))
                        engine.dragTarget = shipAnchor + dx * 1.35f
                    }

                    engine.dragTarget = null
                    if (maxTravel < TAP_SLOP_DP && endTime - startTime <= TAP_MAX_MS) {
                        engine.triggerBlast()
                    }
                }
            }
    )
}
