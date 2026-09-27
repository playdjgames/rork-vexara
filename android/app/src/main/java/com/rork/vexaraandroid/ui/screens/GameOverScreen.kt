package com.rork.vexaraandroid.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rork.vexaraandroid.R
import com.rork.vexaraandroid.game.GameEngine
import com.rork.vexaraandroid.model.GameOverSummary
import com.rork.vexaraandroid.services.Haptics
import com.rork.vexaraandroid.ui.components.MenuBackdrop
import com.rork.vexaraandroid.ui.components.NeonButton
import com.rork.vexaraandroid.ui.components.NeonPanel
import com.rork.vexaraandroid.ui.components.arcadeScore
import com.rork.vexaraandroid.ui.components.uiTap
import com.rork.vexaraandroid.ui.theme.Vex
import com.rork.vexaraandroid.ui.theme.displayStyle
import com.rork.vexaraandroid.ui.theme.labelStyle
import com.rork.vexaraandroid.ui.theme.numeralStyle

/** Holds end-of-run name entry so the system back button can commit it too. */
class GameOverEntryState(initialName: String) {
    var name by mutableStateOf(initialName)
    var submitted by mutableStateOf(false)
}

/** Saves a qualifying score once, whichever exit the player takes. */
fun commitScoreIfNeeded(engine: GameEngine, summary: GameOverSummary, entry: GameOverEntryState) {
    if (!summary.qualifiesForLeaderboard || entry.submitted) return
    engine.store.submit(entry.name, summary.score, summary.wave, engine.difficulty)
    entry.submitted = true
    Haptics.success()
}

/** End-of-run surface with stats, optional high-score celebration and name entry. */
@Composable
fun GameOverScreen(
    summary: GameOverSummary,
    engine: GameEngine,
    entry: GameOverEntryState,
    onRetry: () -> Unit,
    onMenu: () -> Unit
) {
    val focus = LocalFocusManager.current
    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { revealed = true }
    val reveal by animateFloatAsState(if (revealed) 1f else 0f, spring(dampingRatio = 0.8f, stiffness = 200f), label = "reveal")
    val needsEntry = summary.qualifiesForLeaderboard && !entry.submitted

    fun commit() {
        focus.clearFocus()
        commitScoreIfNeeded(engine, summary, entry)
    }

    Box(Modifier.fillMaxSize()) {
        MenuBackdrop(imageRes = R.drawable.nebula_storm_bg, dim = 0.6f)

        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .graphicsLayer {
                    alpha = reveal
                    val s = 0.94f + 0.06f * reveal
                    scaleX = s
                    scaleY = s
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Spacer(Modifier.height(28.dp))

            if (summary.isNewHighScore) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "NEW HIGH SCORE!",
                        textAlign = TextAlign.Center,
                        style = displayStyle(34f, 3f, glow = Vex.amber, glowRadius = 20f).copy(
                            brush = Brush.verticalGradient(listOf(Color(0xFFFFF3C4), Vex.amber))
                        )
                    )
                    Text("YOU TOPPED THE CABINET", style = labelStyle(11f, 2.2f, Vex.textSecondary))
                }
            } else {
                Text("GAME OVER", style = displayStyle(44f, 5f, glow = Vex.danger, glowRadius = 18f))
            }

            val scoreAccent = if (summary.isNewHighScore) Vex.amber else Vex.cyan
            NeonPanel(
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = 460.dp)
                    .semantics { contentDescription = "Final score ${summary.score}" },
                accent = scoreAccent
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("FINAL SCORE", style = labelStyle(11f, 2f, Vex.textSecondary))
                    Text(arcadeScore(summary.score), style = numeralStyle(40f, glow = scoreAccent, glowRadius = 14f))
                    Text("HIGH SCORE ${arcadeScore(engine.store.highScore)}", style = numeralStyle(12f, Vex.textSecondary))
                }
            }

            if (needsEntry) {
                NeonPanel(Modifier.fillMaxWidth().widthIn(max = 460.dp), accent = Vex.magenta) {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("YOU MADE THE TOP FIVE", style = labelStyle(12f, 1.8f, Vex.magenta))

                        val fieldShape = RoundedCornerShape(10.dp)
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(fieldShape)
                                .background(Vex.voidDeep.copy(alpha = 0.7f))
                                .border(1.dp, Vex.magenta.copy(alpha = 0.5f), fieldShape)
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (entry.name.isEmpty()) {
                                Text("ACE", style = numeralStyle(24f, Vex.textSecondary.copy(alpha = 0.5f)))
                            }
                            BasicTextField(
                                value = entry.name,
                                onValueChange = { entry.name = it.uppercase().take(8) },
                                singleLine = true,
                                textStyle = numeralStyle(24f).copy(textAlign = TextAlign.Center),
                                cursorBrush = SolidColor(Vex.magenta),
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Characters,
                                    autoCorrectEnabled = false,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(onDone = { commit() }),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .semantics { contentDescription = "Name for the leaderboard" }
                            )
                        }

                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Vex.magenta)
                                .clickable {
                                    uiTap()
                                    commit()
                                }
                                .semantics {
                                    role = Role.Button
                                    contentDescription = "Save score"
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("SAVE SCORE", style = labelStyle(14f, 2f, Vex.voidDeep))
                        }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().widthIn(max = 460.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatTile("WAVE", "${summary.wave}", Vex.magenta, Modifier.weight(1f))
                StatTile("KILLS", "${summary.kills}", Vex.cyan, Modifier.weight(1f))
                StatTile("BEST COMBO", "x${summary.bestCombo}", Vex.amber, Modifier.weight(1f))
            }

            Column(
                Modifier.fillMaxWidth().widthIn(max = 460.dp).padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                NeonButton("RETRY", accent = Vex.cyan, filled = true, showsChevron = false) {
                    commit()
                    onRetry()
                }
                NeonButton("MAIN MENU", accent = Vex.violet, showsChevron = false) {
                    commit()
                    onMenu()
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .clip(shape)
            .background(Vex.panel.copy(alpha = 0.6f))
            .border(1.dp, accent.copy(alpha = 0.32f), shape)
            .padding(vertical = 14.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$label: $value" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(value, style = numeralStyle(20f, accent), maxLines = 1)
        Text(label, style = labelStyle(9f, 1.2f, Vex.textSecondary), maxLines = 1)
    }
}
