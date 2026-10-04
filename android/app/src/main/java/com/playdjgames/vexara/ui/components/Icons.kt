package com.playdjgames.vexara.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flare
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.KeyboardDoubleArrowUp
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.ui.graphics.vector.ImageVector
import com.playdjgames.vexara.model.Achievement
import com.playdjgames.vexara.model.PowerUpKind

/** Android glyphs standing in for the iPhone's SF Symbols. */
val PowerUpKind.icon: ImageVector
    get() = when (this) {
        PowerUpKind.RAPID_FIRE -> Icons.Filled.Bolt
        PowerUpKind.DOUBLE_SHOT -> Icons.Filled.DragHandle
        PowerUpKind.TRIPLE_SHOT -> Icons.Filled.KeyboardDoubleArrowUp
        PowerUpKind.PLASMA -> Icons.Filled.LocalFireDepartment
        PowerUpKind.SHIELD -> Icons.Filled.Shield
        PowerUpKind.BLAST -> Icons.Filled.Flare
        PowerUpKind.SLOW_TIME -> Icons.Filled.HourglassBottom
    }

val Achievement.icon: ImageVector
    get() = when (this) {
        Achievement.FIRST_BLOOD -> Icons.Filled.GpsFixed
        Achievement.ALIEN_HUNTER -> Icons.Filled.TrackChanges
        Achievement.FORMATION_BREAKER -> Icons.Filled.GridView
        Achievement.BOSS_SLAYER -> Icons.Filled.EmojiEvents
        Achievement.PERFECT_WAVE -> Icons.Filled.AutoAwesome
        Achievement.HIGH_ROLLER -> Icons.AutoMirrored.Filled.TrendingUp
        Achievement.VOID_RESCUE -> Icons.Filled.Autorenew
        Achievement.DEEP_RUN -> Icons.Filled.LocalFireDepartment
    }
