package com.rork.vexaraandroid.ui.screens

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
import com.rork.vexaraandroid.R
import com.rork.vexaraandroid.game.Battlefield
import com.rork.vexaraandroid.game.GameEngine
import com.rork.vexaraandroid.model.GamePhase
import com.rork.vexaraandroid.ui.components.MenuBackdrop
import com.rork.vexaraandroid.ui.theme.Vex

/** The live battlefield: backdrop, canvas renderer, drag controls, HUD and toast. */
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
            GameHud(engine, onPause = { engine.pause() }, modifier = Modifier.padding(top = 4.dp))
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

/** Full-playfield drag surface: relative steering, plus tap/hold fire when auto-fire is off. */
@Composable
private fun ControlSurface(engine: GameEngine) {
    val density = LocalDensity.current.density
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(engine) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val anchor = down.position.x / density
                    val shipAnchor = engine.player.position.x
                    if (!engine.autoFire) engine.firePressed = true
                    engine.dragTarget = shipAnchor

                    var pointerId = down.id
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == pointerId } ?: event.changes.firstOrNull()
                        if (change == null || !change.pressed) break
                        pointerId = change.id
                        val delta = change.position.x / density - anchor
                        engine.dragTarget = shipAnchor + delta * 1.35f
                    }

                    engine.dragTarget = null
                    if (!engine.autoFire) engine.registerTapFire()
                    engine.firePressed = false
                }
            }
    )
}
