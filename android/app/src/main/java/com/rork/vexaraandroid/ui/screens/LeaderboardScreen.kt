package com.rork.vexaraandroid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rork.vexaraandroid.model.Achievement
import com.rork.vexaraandroid.model.LeaderboardEntry
import com.rork.vexaraandroid.services.ProgressStore
import com.rork.vexaraandroid.ui.components.MenuBackdrop
import com.rork.vexaraandroid.ui.components.NeonButton
import com.rork.vexaraandroid.ui.components.NeonPanel
import com.rork.vexaraandroid.ui.components.VSpace
import com.rork.vexaraandroid.ui.components.arcadeScore
import com.rork.vexaraandroid.ui.components.icon
import com.rork.vexaraandroid.ui.components.uiTap
import com.rork.vexaraandroid.ui.theme.Vex
import com.rork.vexaraandroid.ui.theme.bodyStyle
import com.rork.vexaraandroid.ui.theme.displayStyle
import com.rork.vexaraandroid.ui.theme.labelStyle
import com.rork.vexaraandroid.ui.theme.numeralStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Local top-five plus the achievements roster, toggled with a segmented control. */
@Composable
fun LeaderboardScreen(store: ProgressStore, onDismiss: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Box(Modifier.fillMaxSize()) {
        MenuBackdrop(dim = 0.52f)

        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(
                Modifier.fillMaxWidth().padding(top = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("LEADERBOARD", style = displayStyle(30f, 4f, glow = Vex.magenta, glowRadius = 14f))
                Text("HIGH SCORE ${arcadeScore(store.highScore)}", style = numeralStyle(12f, Vex.textSecondary))
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("SCORES", "AWARDS").forEachIndexed { index, label ->
                    val selected = tab == index
                    val shape = RoundedCornerShape(50)
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(shape)
                            .background(if (selected) Vex.cyan else Vex.panel.copy(alpha = 0.5f))
                            .border(1.dp, if (selected) Color.Transparent else Vex.textSecondary.copy(alpha = 0.25f), shape)
                            .clickable {
                                uiTap()
                                tab = index
                            }
                            .semantics { role = Role.Tab }
                            .padding(vertical = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, style = labelStyle(12f, 1.8f, if (selected) Vex.voidDeep else Vex.textSecondary))
                    }
                }
            }

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(top = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (tab == 0) ScoresContent(store) else AchievementsContent(store)
                VSpace(10.dp)
            }

            NeonButton(
                "BACK", accent = Vex.cyan, filled = true, showsChevron = false, onClick = onDismiss,
                modifier = Modifier.padding(horizontal = 24.dp).padding(top = 4.dp, bottom = 14.dp)
            )
        }
    }
}

@Composable
private fun ScoresContent(store: ProgressStore) {
    if (store.leaderboard.isEmpty()) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 50.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Filled.EmojiEvents, null, tint = Vex.textSecondary.copy(alpha = 0.5f), modifier = Modifier.size(38.dp))
            Text("NO RUNS LOGGED", style = labelStyle(14f, 1.8f, Vex.textSecondary))
            Text(
                "Finish a run to claim the first slot on the cabinet.",
                textAlign = TextAlign.Center,
                style = bodyStyle(12f, Vex.textSecondary.copy(alpha = 0.7f))
            )
        }
    } else {
        store.leaderboard.forEachIndexed { index, entry -> ScoreRow(index + 1, entry) }
    }
}

private val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())

@Composable
private fun ScoreRow(rank: Int, entry: LeaderboardEntry) {
    val accent = when (rank) {
        1 -> Vex.amber
        2 -> Vex.cyan
        3 -> Vex.magenta
        else -> Vex.textSecondary
    }
    val top = rank <= 3
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Vex.panel.copy(alpha = if (top) 0.7f else 0.45f))
            .border(1.dp, accent.copy(alpha = if (top) 0.45f else 0.16f), shape)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "Rank $rank, ${entry.name}, ${entry.score} points, wave ${entry.wave}, ${entry.difficultyValue.title}"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("$rank", style = numeralStyle(16f, accent), textAlign = TextAlign.Center, modifier = Modifier.width(26.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(entry.name, style = labelStyle(14f, 1.6f))
            Text(
                "WAVE ${entry.wave}  ·  ${entry.difficultyValue.title}  ·  ${dateFormat.format(Date(entry.date))}",
                style = bodyStyle(10f),
                maxLines = 1
            )
        }
        Text(
            arcadeScore(entry.score),
            style = if (top) numeralStyle(17f, accent, glow = accent, glowRadius = 7f, intensity = 0.6f) else numeralStyle(17f, accent)
        )
    }
}

@Composable
private fun AchievementsContent(store: ProgressStore) {
    NeonPanel(Modifier.fillMaxWidth(), accent = Vex.violet) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val stats = store.stats
            Row {
                StatTile("RUNS", "${stats.runsPlayed}", Vex.cyan, Modifier.weight(1f))
                StatTile("KILLS", "${stats.totalKills}", Vex.magenta, Modifier.weight(1f))
                StatTile("BEST WAVE", "${stats.bestWave}", Vex.amber, Modifier.weight(1f))
            }
            Row {
                StatTile("BOSSES", "${stats.bossKills}", Vex.danger, Modifier.weight(1f))
                StatTile("RESCUES", "${stats.rescues}", Vex.acid, Modifier.weight(1f))
                StatTile("PERFECT", "${stats.perfectWaves}", Vex.violet, Modifier.weight(1f))
            }
        }
    }

    for (achievement in Achievement.entries) AchievementRow(achievement, store.isUnlocked(achievement))
}

@Composable
private fun AchievementRow(achievement: Achievement, unlocked: Boolean) {
    val accent = achievement.accent
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Vex.panel.copy(alpha = if (unlocked) 0.6f else 0.35f))
            .border(1.dp, (if (unlocked) accent else Vex.textSecondary).copy(alpha = if (unlocked) 0.35f else 0.12f), shape)
            .padding(12.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "${achievement.title}, ${if (unlocked) "unlocked" else "locked"}. ${achievement.detail}"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Vex.voidDeep.copy(alpha = 0.65f))
                .border(1.dp, (if (unlocked) accent else Vex.textSecondary).copy(alpha = if (unlocked) 0.6f else 0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (unlocked) achievement.icon else Icons.Filled.Lock,
                contentDescription = null,
                tint = if (unlocked) accent else Vex.textSecondary.copy(alpha = 0.5f),
                modifier = Modifier.size(19.dp)
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(achievement.title, style = labelStyle(12f, 1.4f, if (unlocked) Vex.textPrimary else Vex.textSecondary.copy(alpha = 0.7f)))
            Text(achievement.detail, style = bodyStyle(11f, Vex.textSecondary.copy(alpha = if (unlocked) 1f else 0.6f)))
        }
        if (unlocked) {
            Icon(Icons.Filled.Verified, contentDescription = null, tint = accent, modifier = Modifier.size(17.dp))
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(value, style = numeralStyle(17f, accent), maxLines = 1)
        Text(label, style = labelStyle(8f, 1f, Vex.textSecondary), maxLines = 1)
    }
}
