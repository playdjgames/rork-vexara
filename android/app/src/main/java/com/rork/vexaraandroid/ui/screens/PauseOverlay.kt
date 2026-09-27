package com.rork.vexaraandroid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rork.vexaraandroid.game.GameEngine
import com.rork.vexaraandroid.ui.components.NeonButton
import com.rork.vexaraandroid.ui.components.NeonPanel
import com.rork.vexaraandroid.ui.components.arcadeScore
import com.rork.vexaraandroid.ui.components.uiTap
import com.rork.vexaraandroid.ui.theme.Vex
import com.rork.vexaraandroid.ui.theme.displayStyle
import com.rork.vexaraandroid.ui.theme.labelStyle
import com.rork.vexaraandroid.ui.theme.numeralStyle

/** Modal pause surface layered over the frozen battlefield. */
@Composable
fun PauseOverlay(engine: GameEngine, onResume: () -> Unit, onRestart: () -> Unit, onQuit: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Vex.voidDeep.copy(alpha = 0.82f))
            // Swallow touches so the battlefield underneath stays frozen.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .safeDrawingPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .widthIn(max = 460.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 30.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text("PAUSED", style = displayStyle(40f, 6f, glow = Vex.cyan, glowRadius = 16f))

            NeonPanel(Modifier.fillMaxWidth(), accent = Vex.cyan) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    StatLine("SCORE", arcadeScore(engine.score), Vex.cyan)
                    Separator()
                    StatLine("WAVE", "${engine.wave}", Vex.magenta)
                    Separator()
                    StatLine("LIVES", "${maxOf(0, engine.lives)}", Vex.acid)
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val muted = engine.store.isMuted
                ToggleChip(
                    "SOUND", !muted,
                    if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    Vex.cyan, Modifier.weight(1f)
                ) { engine.store.isMuted = !muted }

                val auto = engine.store.autoFire
                ToggleChip(
                    "AUTO-FIRE", auto, if (auto) Icons.Filled.Bolt else Icons.Filled.FlashOff, Vex.amber, Modifier.weight(1f)
                ) {
                    engine.store.autoFire = !auto
                    engine.autoFire = engine.store.autoFire
                }

                val haptics = engine.store.hapticsEnabled
                ToggleChip("HAPTICS", haptics, Icons.Filled.GraphicEq, Vex.violet, Modifier.weight(1f)) {
                    engine.store.hapticsEnabled = !haptics
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                NeonButton("RESUME", accent = Vex.cyan, filled = true, showsChevron = false, onClick = onResume)
                NeonButton("RESTART RUN", accent = Vex.amber, showsChevron = false, onClick = onRestart)
                NeonButton("QUIT TO MENU", accent = Vex.danger, showsChevron = false, onClick = onQuit)
            }
        }
    }
}

@Composable
private fun Separator() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(1.dp)
            .background(Vex.textSecondary.copy(alpha = 0.14f))
    )
}

@Composable
private fun StatLine(label: String, value: String, accent: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 11.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$label: $value" },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = labelStyle(12f, 1.6f, Vex.textSecondary))
        Spacer(Modifier.weight(1f))
        Text(value, style = numeralStyle(17f, accent))
    }
}

@Composable
private fun ToggleChip(
    title: String,
    isOn: Boolean,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    val tint = if (isOn) accent else Vex.textSecondary.copy(alpha = 0.55f)
    Column(
        modifier
            .clip(shape)
            .background(Vex.panel.copy(alpha = if (isOn) 0.75f else 0.4f))
            .border(1.dp, (if (isOn) accent else Vex.textSecondary).copy(alpha = if (isOn) 0.55f else 0.2f), shape)
            .clickable {
                uiTap()
                onClick()
            }
            .semantics {
                role = Role.Switch
                contentDescription = "$title ${if (isOn) "on" else "off"}"
            }
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Text(title, style = labelStyle(9f, 1f, tint), maxLines = 1)
    }
}
