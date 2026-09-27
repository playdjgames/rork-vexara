package com.rork.vexaraandroid.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.rork.vexaraandroid.R
import com.rork.vexaraandroid.game.GameEngine
import com.rork.vexaraandroid.model.Difficulty
import com.rork.vexaraandroid.ui.components.CircleIconButton
import com.rork.vexaraandroid.ui.components.MenuBackdrop
import com.rork.vexaraandroid.ui.components.NeonButton
import com.rork.vexaraandroid.ui.components.VexaraWordmark
import com.rork.vexaraandroid.ui.theme.Vex

/** Root menu: the player's ship facing a looming formation, wordmark between, four actions below. */
@Composable
fun TitleScreen(
    engine: GameEngine,
    onStart: () -> Unit,
    onHowToPlay: () -> Unit,
    onLeaderboard: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "title")
    val formationDrift by transition.animateFloat(
        0f, 16f, infiniteRepeatable(tween(3400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "drift"
    )
    val shipBob by transition.animateFloat(
        0f, -10f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob"
    )
    val flarePulse by transition.animateFloat(
        1f, 1.35f, infiniteRepeatable(tween(500, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "flare"
    )

    Box(Modifier.fillMaxSize()) {
        MenuBackdrop(dim = 0.3f)

        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            val width = maxWidth
            Column(
                Modifier.fillMaxSize().padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AlienFormation(width, formationDrift, Modifier.padding(top = 18.dp))
                VexaraWordmark(
                    scale = minOf(1.05f, width.value / 390f),
                    modifier = Modifier.padding(top = 6.dp)
                )
                Spacer(Modifier.weight(1f).heightIn(min = 8.dp))
                PlayerShipHero(width, shipBob, flarePulse)
                Spacer(Modifier.weight(1f).heightIn(min = 8.dp))
                MenuButtons(engine, onStart, onHowToPlay, onLeaderboard, Modifier.padding(horizontal = 24.dp).padding(bottom = 8.dp))
            }

            Row(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 16.dp, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val muted = engine.store.isMuted
                CircleIconButton(
                    icon = if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    description = if (muted) "Unmute sound" else "Mute sound",
                    accent = if (muted) Vex.textSecondary else Vex.cyan
                ) { engine.store.isMuted = !muted }

                val auto = engine.store.autoFire
                CircleIconButton(
                    icon = if (auto) Icons.Filled.Bolt else Icons.Filled.FlashOff,
                    description = if (auto) "Auto-fire on" else "Auto-fire off",
                    accent = if (auto) Vex.amber else Vex.textSecondary
                ) {
                    engine.store.autoFire = !auto
                    engine.autoFire = engine.store.autoFire
                }
            }
        }
    }
}

@Composable
private fun AlienFormation(width: Dp, drift: Float, modifier: Modifier = Modifier) {
    val unit = min(46.dp, width / 9f)
    Column(
        modifier.offset(x = (drift - 8f).dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(unit * 0.16f)
    ) {
        // Lead mothership, flanked by a widening V of fighters.
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(unit * 2.2f)
                    .background(
                        Brush.radialGradient(listOf(Vex.magenta.copy(alpha = 0.45f), Vex.magenta.copy(alpha = 0f))),
                        RoundedCornerShape(50)
                    )
            )
            Image(painterResource(R.drawable.alien_mothership_boss), null, Modifier.size(unit * 2.5f))
        }
        for (row in 0 until 3) {
            Row(horizontalArrangement = Arrangement.spacedBy(unit * 0.28f)) {
                for (column in 0 until row + 3) {
                    val sprite = if (row == 1) R.drawable.alien_interceptor_sprite else R.drawable.insectoid_fighter_craft
                    Image(
                        painterResource(sprite),
                        null,
                        Modifier
                            .size(unit * 0.82f)
                            .offset(y = unit * 0.12f * (column % 2))
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerShipHero(width: Dp, bob: Float, flarePulse: Float) {
    val shipWidth = min(150.dp, width * 0.4f)
    Box(Modifier.offset(y = bob.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(shipWidth * 1.1f)
                .background(
                    Brush.radialGradient(listOf(Vex.cyan.copy(alpha = 0.35f), Vex.cyan.copy(alpha = 0f))),
                    RoundedCornerShape(50)
                )
        )
        Row(
            Modifier.offset(y = shipWidth * 0.52f),
            horizontalArrangement = Arrangement.spacedBy(shipWidth * 0.14f)
        ) {
            repeat(2) {
                Box(
                    Modifier
                        .width(shipWidth * 0.1f)
                        .height(shipWidth * 0.55f)
                        .graphicsLayer {
                            scaleY = flarePulse
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
                        }
                        .blur(2.dp)
                        .background(
                            Brush.verticalGradient(listOf(Vex.textPrimary, Vex.cyan, Vex.cyan.copy(alpha = 0f))),
                            RoundedCornerShape(50)
                        )
                )
            }
        }
        Image(painterResource(R.drawable.starfighter_top_view), null, Modifier.size(shipWidth))
    }
}

@Composable
private fun MenuButtons(
    engine: GameEngine,
    onStart: () -> Unit,
    onHowToPlay: () -> Unit,
    onLeaderboard: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        NeonButton("START GAME", accent = Vex.cyan, filled = true, onClick = onStart)
        NeonButton("HOW TO PLAY", accent = Vex.violet, onClick = onHowToPlay)
        NeonButton("LEADERBOARD", accent = Vex.magenta, onClick = onLeaderboard)
        val difficulty = engine.store.difficulty
        NeonButton("DIFFICULTY: ${difficulty.title}", accent = difficulty.accent) {
            val all = Difficulty.entries
            val next = all[(all.indexOf(difficulty) + 1) % all.size]
            engine.store.difficulty = next
            engine.difficulty = next
        }
    }
}
