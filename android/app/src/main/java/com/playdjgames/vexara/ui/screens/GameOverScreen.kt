package com.playdjgames.vexara.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.playdjgames.vexara.R
import com.playdjgames.vexara.model.GameOverSummary
import com.playdjgames.vexara.ui.components.MenuBackdrop
import com.playdjgames.vexara.ui.components.NeonButton
import com.playdjgames.vexara.ui.components.NeonPanel
import com.playdjgames.vexara.ui.components.arcadeScore
import com.playdjgames.vexara.ui.theme.Vex
import com.playdjgames.vexara.ui.theme.displayStyle
import com.playdjgames.vexara.ui.theme.labelStyle
import com.playdjgames.vexara.ui.theme.numeralStyle

/** End of run: final score and a single Again button that starts a fresh run at wave 1. */
@Composable
fun GameOverScreen(
    summary: GameOverSummary,
    highScore: Int,
    onAgain: () -> Unit
) {
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { revealed = true }
    val reveal by animateFloatAsState(if (revealed) 1f else 0f, spring(dampingRatio = 0.8f, stiffness = 200f), label = "reveal")

    Box(Modifier.fillMaxSize()) {
        MenuBackdrop(imageRes = R.drawable.nebula_storm_bg, dim = 0.6f)

        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 24.dp)
                .graphicsLayer {
                    alpha = reveal
                    val s = 0.94f + 0.06f * reveal
                    scaleX = s
                    scaleY = s
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (summary.isNewHighScore) {
                Text(
                    "NEW HIGH SCORE!",
                    textAlign = TextAlign.Center,
                    style = displayStyle(34f, 3f, glow = Vex.amber, glowRadius = 20f).copy(
                        brush = Brush.verticalGradient(listOf(Color(0xFFFFF3C4), Vex.amber))
                    )
                )
            } else {
                Text("GAME OVER", style = displayStyle(44f, 5f, glow = Vex.danger, glowRadius = 18f))
            }

            Spacer(Modifier.height(24.dp))

            val scoreAccent = if (summary.isNewHighScore) Vex.amber else Vex.cyan
            NeonPanel(
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = 460.dp)
                    .semantics { contentDescription = "Score ${summary.score}" },
                accent = scoreAccent
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("SCORE", style = labelStyle(11f, 2f, Vex.textSecondary))
                    Text(arcadeScore(summary.score), style = numeralStyle(44f, glow = scoreAccent, glowRadius = 14f))
                    Text("WAVE ${summary.wave}", style = labelStyle(12f, 1.6f, Vex.magenta))
                    Text("HIGH SCORE ${arcadeScore(highScore)}", style = numeralStyle(12f, Vex.textSecondary))
                }
            }

            Spacer(Modifier.height(32.dp))

            NeonButton(
                "Again",
                modifier = Modifier.widthIn(max = 460.dp),
                accent = Vex.cyan,
                filled = true,
                showsChevron = false,
                onClick = onAgain
            )
        }
    }
}
