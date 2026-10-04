package com.playdjgames.vexara.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.playdjgames.vexara.game.GameEngine
import com.playdjgames.vexara.model.GamePhase
import com.playdjgames.vexara.services.AudioEngine
import com.playdjgames.vexara.services.Haptics
import com.playdjgames.vexara.services.MusicTrack
import com.playdjgames.vexara.services.ProgressStore
import com.playdjgames.vexara.ui.screens.GameOverScreen
import com.playdjgames.vexara.ui.screens.GameScreen
import com.playdjgames.vexara.ui.screens.TitleScreen
import com.playdjgames.vexara.ui.theme.Vex

/**
 * Root shell with exactly three states: title (Play), the run, and game over (Again).
 * Leaving the app mid-run freezes the simulation and it picks up again on return.
 */
@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val store = remember { ProgressStore(context.applicationContext) }
    val engine = remember {
        AudioEngine.preload(context.applicationContext)
        Haptics.prepare(context.applicationContext)
        AudioEngine.playMusic(MusicTrack.MENU)
        GameEngine(store)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, engine) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    engine.pause()
                    AudioEngine.suspend()
                }
                Lifecycle.Event.ON_RESUME -> {
                    AudioEngine.resumeFromBackground()
                    engine.resume()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(Modifier.fillMaxSize().background(Vex.voidDeep)) {
        val phase = engine.phase
        val screen = if (phase == GamePhase.PAUSED) GamePhase.PLAYING else phase

        Crossfade(targetState = screen, animationSpec = tween(300), label = "phase") { target ->
            when (target) {
                GamePhase.TITLE -> TitleScreen(onPlay = { engine.startRun() })
                GamePhase.PLAYING, GamePhase.PAUSED -> GameScreen(engine)
                GamePhase.GAME_OVER -> {
                    val summary = engine.gameOverSummary
                    if (summary != null) {
                        GameOverScreen(
                            summary = summary,
                            highScore = store.highScore,
                            onAgain = { engine.startRun() }
                        )
                    }
                }
            }
        }

        // System back from a run or the score screen returns to the title.
        BackHandler(enabled = phase != GamePhase.TITLE) { engine.returnToTitle() }
    }
}
