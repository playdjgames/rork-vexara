package com.rork.vexaraandroid.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowCircleDown
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flare
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.SwipeLeft
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.rork.vexaraandroid.model.EnemyKind
import com.rork.vexaraandroid.model.PowerUpKind
import com.rork.vexaraandroid.ui.components.MenuBackdrop
import com.rork.vexaraandroid.ui.components.NeonButton
import com.rork.vexaraandroid.ui.components.NeonPanel
import com.rork.vexaraandroid.ui.components.VSpace
import com.rork.vexaraandroid.ui.components.icon
import com.rork.vexaraandroid.ui.theme.Vex
import com.rork.vexaraandroid.ui.theme.bodyStyle
import com.rork.vexaraandroid.ui.theme.displayStyle
import com.rork.vexaraandroid.ui.theme.labelStyle
import com.rork.vexaraandroid.ui.theme.numeralStyle

/** Briefing: controls, enemy dossier, power-up legend, the siphon mechanic and scoring. */
@Composable
fun HowToPlayScreen(onDismiss: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        MenuBackdrop(dim = 0.52f)

        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(
                Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("HOW TO PLAY", style = displayStyle(30f, 4f, glow = Vex.violet, glowRadius = 14f))
                Text("SURVIVE THE FORMATIONS", style = labelStyle(10f, 2.2f, Vex.textSecondary))
            }

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                Section("CONTROLS", Vex.cyan) {
                    NeonPanel(Modifier.fillMaxWidth(), accent = Vex.cyan) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Bullet(Icons.Filled.SwipeLeft, "DRAG ANYWHERE", "Slide your thumb to fly. Steering is relative, so your finger never covers the ship.", Vex.cyan)
                            Bullet(Icons.Filled.Bolt, "AUTO-FIRE", "On by default. Turn it off from the menu or pause screen to tap-fire manually.", Vex.amber)
                            Bullet(Icons.Filled.Flare, "BLAST", "Tap the bottom-right charge to vaporise everything near you. Earn more from drops and wave clears.", Vex.ember)
                        }
                    }
                }

                Section("HOSTILE DOSSIER", Vex.magenta) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (kind in EnemyKind.entries) EnemyRow(kind)
                    }
                }

                Section("POWER-UPS", Vex.acid) {
                    NeonPanel(Modifier.fillMaxWidth(), accent = Vex.acid) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                            for (kind in PowerUpKind.entries) PowerUpRow(kind)
                        }
                    }
                }

                Section("THE VOID SIPHON", Vex.violet) {
                    NeonPanel(Modifier.fillMaxWidth(), accent = Vex.violet) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Bullet(Icons.Filled.TrackChanges, "THE BEAM", "A Siphon Warden drops low and opens a prism beam. Linger inside it and a ring closes around your ship.", Vex.magenta)
                            Bullet(Icons.Filled.ArrowCircleDown, "IF IT LOCKS", "Your ship is taken and you lose a life. The captured hull stays clamped beneath the warden.", Vex.danger)
                            Bullet(Icons.Filled.Autorenew, "THE RESCUE", "Destroy that warden to recover your ship. It rejoins as two wing-mates flying escort with extra guns.", Vex.cyan)
                        }
                    }
                }

                Section("SCORING", Vex.amber) {
                    NeonPanel(Modifier.fillMaxWidth(), accent = Vex.amber) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Bullet(Icons.Filled.LocalFireDepartment, "COMBOS", "Consecutive kills raise your multiplier up to x10. Taking a hit resets it to x1.", Vex.amber)
                            Bullet(Icons.Filled.AutoAwesome, "PERFECT WAVE", "Clear a wave without being hit for a large bonus.", Vex.acid)
                            Bullet(Icons.Filled.Stars, "CHALLENGE WAVES", "Every fourth wave the aliens fly elaborate routes and never shoot. Clear them all for a huge payout.", Vex.cyan)
                            Bullet(Icons.Filled.EmojiEvents, "BOSSES", "Every fifth wave. Shoot the glowing cores for triple damage.", Vex.magenta)
                        }
                    }
                }
                VSpace(12.dp)
            }

            NeonButton(
                "GOT IT", accent = Vex.cyan, filled = true, showsChevron = false, onClick = onDismiss,
                modifier = Modifier.padding(horizontal = 24.dp).padding(top = 8.dp, bottom = 14.dp)
            )
        }
    }
}

@Composable
private fun Section(title: String, accent: Color, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = labelStyle(13f, 2.4f, accent, glow = accent, glowRadius = 6f, intensity = 0.5f))
        content()
    }
}

@Composable
private fun Bullet(icon: ImageVector, title: String, detail: String, accent: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.width(24.dp).size(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = labelStyle(12f, 1.4f))
            Text(detail, style = bodyStyle(12f))
        }
    }
}

@Composable
private fun EnemyRow(kind: EnemyKind) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Vex.panel.copy(alpha = 0.55f))
            .border(1.dp, kind.accent.copy(alpha = 0.28f), shape)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Image(painterResource(kind.sprite), contentDescription = null, modifier = Modifier.size(38.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(kind.displayName, style = labelStyle(12f, 1.4f, kind.accent))
                Text("${kind.points} PTS", style = numeralStyle(10f, Vex.textSecondary))
            }
            Text(kind.dossier, style = bodyStyle(11f))
        }
    }
}

@Composable
private fun PowerUpRow(kind: PowerUpKind) {
    Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
        val shape = RoundedCornerShape(7.dp)
        Box(
            Modifier
                .size(26.dp)
                .clip(shape)
                .background(Vex.voidDeep.copy(alpha = 0.7f))
                .border(1.dp, kind.accent.copy(alpha = 0.6f), shape),
            contentAlignment = Alignment.Center
        ) {
            Icon(kind.icon, contentDescription = null, tint = kind.accent, modifier = Modifier.size(15.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(kind.title, style = labelStyle(11f, 1.2f))
            Text(kind.detail, style = bodyStyle(11f))
        }
    }
}
