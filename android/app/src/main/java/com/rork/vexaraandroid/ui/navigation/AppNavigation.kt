package com.rork.vexaraandroid.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.rork.vexaraandroid.game.GameEngine
import com.rork.vexaraandroid.model.GamePhase
import com.rork.vexaraandroid.services.AudioEngine
import com.rork.vexaraandroid.services.Haptics
import com.rork.vexaraandroid.services.MusicTrack
import com.rork.vexaraandroid.services.ProgressStore
import com.rork.vexaraandroid.ui.screens.GameOverEntryState
import com.rork.vexaraandroid.ui.screens.GameOverScreen
import com.rork.vexaraandroid.ui.screens.GameScreen
import com.rork.vexaraandroid.ui.screens.HowToPlayScreen
import com.rork.vexaraandroid.ui.screens.LeaderboardScreen
import com.rork.vexaraandroid.ui.screens.PauseOverlay
import com.rork.vexaraandroid.ui.screens.TitleScreen
import com.rork.vexaraandroid.ui.screens.commitScoreIfNeeded
import com.rork.vexaraandroid.ui.theme.Vex

private enum class Overlay { NONE, HOW_TO_PLAY, LEADERBOARD }

/**
 * Root shell. Full-bleed arcade product: no app bars. Each phase owns the whole screen and
 * transitions are crossfades. System back mirrors the iPhone's on-screen controls.
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
    var overlay by rememberSaveable { mutableStateOf(Overlay.NONE) }

    // Backgrounding mid-run should never cost the player a life.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, engine) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    if (engine.phase == GamePhase.PLAYING) engine.pause()
                    AudioEngine.suspend()
                }
                Lifecycle.Event.ON_RESUME -> AudioEngine.resumeFromBackground()
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
                GamePhase.TITLE -> TitleScreen(
                    engine = engine,
                    onStart = { engine.startRun() },
                    onHowToPlay = { overlay = Overlay.HOW_TO_PLAY },
                    onLeaderboard = { overlay = Overlay.LEADERBOARD }
                )
                GamePhase.PLAYING, GamePhase.PAUSED -> GameScreen(engine)
                GamePhase.GAME_OVER -> {
                    val summary = engine.gameOverSummary
                    if (summary != null) {
                        val entry = remember(summary) { GameOverEntryState(store.playerName) }
                        // Back on game over behaves like MAIN MENU, saving a qualifying score first.
                        BackHandler {
                            commitScoreIfNeeded(engine, summary, entry)
                            engine.returnToTitle()
                        }
                        GameOverScreen(
                            summary = summary,
                            engine = engine,
                            entry = entry,
                            onRetry = { engine.startRun() },
                            onMenu = { engine.returnToTitle() }
                        )
                    }
                }
            }
        }

        // Back during play opens the same pause menu as the pause button; back while paused resumes.
        BackHandler(enabled = phase == GamePhase.PLAYING) { engine.pause() }
        BackHandler(enabled = phase == GamePhase.PAUSED) { engine.resume() }

        AnimatedVisibility(visible = phase == GamePhase.PAUSED, enter = fadeIn(), exit = fadeOut()) {
            PauseOverlay(
                engine = engine,
                onResume = { engine.resume() },
                onRestart = { engine.startRun() },
                onQuit = { engine.returnToTitle() }
            )
        }

        AnimatedVisibility(visible = overlay == Overlay.HOW_TO_PLAY, enter = fadeIn(), exit = fadeOut()) {
            BackHandler { overlay = Overlay.NONE }
            HowToPlayScreen(onDismiss = { overlay = Overlay.NONE })
        }
        AnimatedVisibility(visible = overlay == Overlay.LEADERBOARD, enter = fadeIn(), exit = fadeOut()) {
            BackHandler { overlay = Overlay.NONE }
            LeaderboardScreen(store = store, onDismiss = { overlay = Overlay.NONE })
        }
    }
}
