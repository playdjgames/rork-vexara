package com.rork.vexaraandroid.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flare
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rork.vexaraandroid.game.GameEngine
import com.rork.vexaraandroid.model.AchievementToast
import com.rork.vexaraandroid.model.BossHudState
import com.rork.vexaraandroid.model.WaveSummary
import com.rork.vexaraandroid.ui.components.CircleIconButton
import com.rork.vexaraandroid.ui.components.HudPill
import com.rork.vexaraandroid.ui.components.arcadeScore
import com.rork.vexaraandroid.ui.components.icon
import com.rork.vexaraandroid.ui.components.neonHalo
import com.rork.vexaraandroid.ui.theme.Vex
import com.rork.vexaraandroid.ui.theme.displayStyle
import com.rork.vexaraandroid.ui.theme.labelStyle
import com.rork.vexaraandroid.ui.theme.numeralStyle

/** Arcade HUD drawn over the battlefield: score row, boss bar, callouts, badges, bottom pills. */
@Composable
fun GameHud(engine: GameEngine, onPause: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        TopRow(engine, onPause)
        AnimatedVisibility(
            visible = engine.bossHud != null,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut()
        ) {
            engine.bossHud?.let { BossBar(it, Modifier.padding(top = 10.dp)) }
        }
        Spacer(Modifier.weight(1f))
        CenterCallouts(engine)
        Spacer(Modifier.weight(1f))
        BadgeRow(engine)
        BottomRow(engine)
    }
}

@Composable
private fun TopRow(engine: GameEngine, onPause: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        HudPill(accent = Vex.cyan, modifier = Modifier.semantics { contentDescription = "Score ${engine.score}" }) {
            Column {
                Text("SCORE", style = labelStyle(9f, 1.6f, Vex.cyan.copy(alpha = 0.85f)))
                Text(arcadeScore(engine.score), style = numeralStyle(18f, glow = Vex.cyan, glowRadius = 6f, intensity = 0.5f))
            }
        }

        Spacer(Modifier.weight(1f))

        if (engine.bossHud == null) {
            HudPill(accent = Vex.magenta, modifier = Modifier.semantics { contentDescription = "Wave ${engine.wave}" }) {
                Text("WAVE ${engine.wave}", style = labelStyle(13f, 1.4f, glow = Vex.magenta, glowRadius = 6f, intensity = 0.5f))
            }
        }

        Spacer(Modifier.weight(1f))

        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val high = maxOf(engine.store.highScore, engine.score)
            HudPill(accent = Vex.violet, modifier = Modifier.semantics { contentDescription = "High score $high" }) {
                Column(horizontalAlignment = Alignment.End) {
                    Text("HIGH SCORE", style = labelStyle(9f, 1.4f, Vex.violet.copy(alpha = 0.9f)))
                    Text(arcadeScore(high), style = numeralStyle(15f))
                }
            }
            CircleIconButton(icon = Icons.Filled.Pause, description = "Pause", onClick = onPause)
        }
    }
}

@Composable
private fun BossBar(boss: BossHudState, modifier: Modifier = Modifier) {
    val fraction by animateFloatAsState(boss.fraction, label = "bossHealth")
    Column(
        modifier.fillMaxWidth().semantics { contentDescription = "${boss.name}, ${(boss.fraction * 100).toInt()} percent health" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(boss.name, style = labelStyle(15f, 3f, glow = boss.accent, glowRadius = 10f))
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(50))
                .background(Vex.voidDeep.copy(alpha = 0.8f))
                .border(1.dp, boss.accent.copy(alpha = 0.6f), RoundedCornerShape(50))
        ) {
            val full = maxWidth
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(full * fraction.coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(listOf(Vex.danger, boss.accent)))
            )
            for (mark in listOf(0.33f, 0.66f)) {
                Box(
                    Modifier
                        .offset(x = full * mark)
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(Vex.voidDeep.copy(alpha = 0.75f))
                )
            }
        }
        Text(
            "${(boss.fraction * 100).toInt()}%",
            style = numeralStyle(11f, boss.accent),
            textAlign = TextAlign.End,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun CenterCallouts(engine: GameEngine) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        val summary = engine.waveSummary
        AnimatedVisibility(
            visible = summary != null,
            enter = scaleIn(initialScale = 0.85f) + fadeIn(),
            exit = scaleOut(targetScale = 0.85f) + fadeOut()
        ) {
            summary?.let { WaveClearBanner(it) }
        }

        AnimatedVisibility(
            visible = engine.bannerVisible,
            enter = scaleIn(initialScale = 0.8f) + fadeIn(),
            exit = scaleOut(targetScale = 0.8f) + fadeOut()
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    engine.bannerTitle,
                    textAlign = TextAlign.Center,
                    style = displayStyle(
                        if (engine.isBossWave) 34f else 30f, 4f,
                        glow = if (engine.isBossWave) Vex.danger else Vex.cyan, glowRadius = 16f
                    )
                )
                Text(engine.bannerSubtitle, textAlign = TextAlign.Center, style = labelStyle(12f, 2.4f, Vex.textSecondary))
            }
        }

        AnimatedVisibility(
            visible = engine.comboVisible,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut()
        ) {
            val pop = 1f + minOf(0.25f, engine.comboFlashTimer * 0.3f)
            Text(
                "COMBO x${engine.combo}",
                style = displayStyle(30f, 3f, glow = Vex.cyan, glowRadius = 14f).copy(
                    brush = Brush.verticalGradient(listOf(Vex.textPrimary, Vex.cyan))
                ),
                modifier = Modifier.graphicsLayer {
                    scaleX = pop
                    scaleY = pop
                }
            )
        }

        AnimatedVisibility(visible = engine.captureVisible, enter = scaleIn(initialScale = 0.85f) + fadeIn(), exit = fadeOut()) {
            CalloutBanner("SHIP CAPTURED", "DESTROY THE WARDEN TO RECOVER IT", Vex.magenta)
        }
        AnimatedVisibility(visible = engine.rescueVisible, enter = scaleIn(initialScale = 0.85f) + fadeIn(), exit = fadeOut()) {
            CalloutBanner("SHIP RECOVERED", "WING-MATES ONLINE", Vex.cyan)
        }
    }
}

@Composable
private fun WaveClearBanner(summary: WaveSummary) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            if (summary.isChallenge) "CHALLENGE CLEAR" else "WAVE ${summary.wave} CLEAR",
            textAlign = TextAlign.Center,
            style = displayStyle(26f, 4f, glow = Vex.cyan, glowRadius = 14f)
        )
        Text("+${summary.total} BONUS", style = numeralStyle(16f, Vex.cyan, glow = Vex.cyan, glowRadius = 8f, intensity = 0.6f))
        if (summary.perfect || summary.challengeFullClear) {
            Text(
                if (summary.challengeFullClear) "FULL CLEAR" else "PERFECT WAVE",
                style = labelStyle(10f, 2.2f, Vex.amber)
            )
        }
        if (summary.nextIsBoss) {
            Text("BOSS INCOMING", style = labelStyle(10f, 2.4f, Vex.danger))
        }
    }
}

@Composable
private fun CalloutBanner(title: String, detail: String, accent: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, textAlign = TextAlign.Center, style = displayStyle(24f, 3f, glow = accent, glowRadius = 12f))
        Text(detail, textAlign = TextAlign.Center, style = labelStyle(10f, 1.8f, Vex.textSecondary))
    }
}

@Composable
private fun BadgeRow(engine: GameEngine) {
    val badges = engine.activeBadges
    if (badges.isEmpty()) return
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .semantics { contentDescription = "Active power-ups" },
        horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally)
    ) {
        for (badge in badges) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Vex.voidDeep.copy(alpha = 0.7f))
                    .border(1.dp, badge.kind.accent.copy(alpha = 0.6f), RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(badge.kind.icon, contentDescription = null, tint = badge.kind.accent, modifier = Modifier.size(12.dp))
                if (badge.secondsLeft > 0) {
                    Text("${badge.secondsLeft}", style = numeralStyle(10f, badge.kind.accent))
                }
            }
        }
    }
}

@Composable
private fun BottomRow(engine: GameEngine) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val lives = maxOf(0, engine.lives)
        HudPill(accent = Vex.cyan, modifier = Modifier.semantics { contentDescription = "$lives lives remaining" }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.Shield, contentDescription = null, tint = Vex.cyan, modifier = Modifier.size(15.dp))
                AnimatedContent(targetState = lives, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "lives") {
                    Text("$it", style = numeralStyle(17f))
                }
            }
        }

        Spacer(Modifier.weight(1f))

        val charges = engine.blastCharges
        val ready = charges > 0
        val tint = if (ready) Vex.ember else Vex.textSecondary.copy(alpha = 0.5f)
        Row(
            Modifier
                .heightIn(min = 44.dp)
                .then(if (ready) Modifier.neonHalo(Vex.ember, 0.9f) else Modifier)
                .clip(RoundedCornerShape(50))
                .background(Vex.voidDeep.copy(alpha = 0.66f))
                .border(1.dp, (if (ready) Vex.ember else Vex.textSecondary).copy(alpha = 0.55f), RoundedCornerShape(50))
                .clickable(enabled = ready) { engine.triggerBlast() }
                .semantics {
                    role = Role.Button
                    contentDescription = "Fire blast, $charges charges"
                }
                .padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(Icons.Filled.Flare, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Text("$charges", style = numeralStyle(17f, tint))
        }
    }
}

/** Transient achievement unlock toast. */
@Composable
fun AchievementToastView(toast: AchievementToast, modifier: Modifier = Modifier) {
    val accent = toast.achievement.accent
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .fillMaxWidth()
            .neonHalo(accent, 0.6f, corner = 16.dp)
            .clip(shape)
            .background(Vex.panel.copy(alpha = 0.9f))
            .border(1.dp, accent.copy(alpha = 0.5f), shape)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .semantics { contentDescription = "Achievement unlocked: ${toast.achievement.title}" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Vex.voidDeep.copy(alpha = 0.8f))
                .border(1.dp, accent.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(toast.achievement.icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("ACHIEVEMENT UNLOCKED", style = labelStyle(9f, 1.6f, accent))
            Text(toast.achievement.title, style = labelStyle(15f, 1.4f))
        }
    }
}
