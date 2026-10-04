package com.playdjgames.vexara.model

import androidx.compose.ui.graphics.Color
import com.playdjgames.vexara.ui.theme.Vex
import kotlinx.serialization.Serializable

@Serializable
data class LeaderboardEntry(
    val id: String,
    val name: String,
    val score: Int,
    val wave: Int,
    val difficulty: String,
    val date: Long
) {
    val difficultyValue: Difficulty get() = Difficulty.fromRaw(difficulty) ?: Difficulty.NORMAL
}

@Serializable
data class PlayerStats(
    val totalKills: Int = 0,
    val bossKills: Int = 0,
    val rescues: Int = 0,
    val perfectWaves: Int = 0,
    val bestWave: Int = 0,
    val bestScore: Int = 0,
    val runsPlayed: Int = 0,
    val fastestFormationClear: Double = Double.MAX_VALUE
)

enum class Achievement(val raw: String, val title: String, val detail: String, val accent: Color) {
    FIRST_BLOOD("firstBlood", "FIRST BLOOD", "Destroy your first enemy.", Vex.cyan),
    ALIEN_HUNTER("alienHunter", "ALIEN HUNTER", "Destroy 100 enemies across all runs.", Vex.magenta),
    FORMATION_BREAKER("formationBreaker", "FORMATION BREAKER", "Clear a full formation in under 25 seconds.", Vex.violet),
    BOSS_SLAYER("bossSlayer", "BOSS SLAYER", "Defeat your first boss.", Vex.amber),
    PERFECT_WAVE("perfectWave", "PERFECT WAVE", "Complete a wave without being hit.", Vex.acid),
    HIGH_ROLLER("highRoller", "HIGH ROLLER", "Reach 100,000 points in a single run.", Vex.amber),
    VOID_RESCUE("voidRescue", "VOID RESCUE", "Recover a ship captured by a Siphon Warden.", Vex.cyan),
    DEEP_RUN("deepRun", "DEEP RUN", "Reach wave 10.", Vex.ember)
}

data class WaveSummary(
    val wave: Int,
    val enemiesCleared: Int,
    val clearPoints: Int,
    val perfect: Boolean,
    val perfectBonus: Int,
    val comboBonus: Int,
    val total: Int,
    val isChallenge: Boolean,
    val challengeFullClear: Boolean,
    val nextIsBoss: Boolean
)

data class GameOverSummary(
    val score: Int,
    val wave: Int,
    val kills: Int,
    val bestCombo: Int,
    val isNewHighScore: Boolean,
    val qualifiesForLeaderboard: Boolean
)

data class PowerUpBadge(val kind: PowerUpKind, val secondsLeft: Int)

data class BossHudState(val name: String, val fraction: Float, val phase: Int, val accent: Color)

data class AchievementToast(val id: Long, val achievement: Achievement)
