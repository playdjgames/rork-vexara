package com.playdjgames.vexara.services

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.playdjgames.vexara.model.Achievement
import com.playdjgames.vexara.model.Difficulty
import com.playdjgames.vexara.model.LeaderboardEntry
import com.playdjgames.vexara.model.PlayerStats
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Durable player progress: high score, local top-5, lifetime stats, achievements and settings.
 * Uses the same keys as the iPhone build so a fresh install starts identically.
 */
class ProgressStore(context: Context) {
    private object Key {
        const val HIGH_SCORE = "ns.highScore"
        const val LEADERBOARD = "ns.leaderboard"
        const val STATS = "ns.stats"
        const val ACHIEVEMENTS = "ns.achievements"
        const val DIFFICULTY = "ns.difficulty"
        const val AUTO_FIRE = "ns.autoFire"
        const val MUTED = "ns.muted"
        const val HAPTICS = "ns.haptics"
        const val LAST_NAME = "ns.lastName"
    }

    companion object {
        const val LEADERBOARD_CAPACITY = 5
    }

    private val prefs: SharedPreferences = context.getSharedPreferences("vexara", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    var highScore: Int by mutableIntStateOf(prefs.getInt(Key.HIGH_SCORE, 0))
        private set

    var leaderboard: List<LeaderboardEntry> by mutableStateOf(loadLeaderboard())
        private set

    var stats: PlayerStats by mutableStateOf(loadStats())
        private set

    var unlocked: Set<String> by mutableStateOf(prefs.getStringSet(Key.ACHIEVEMENTS, emptySet())?.toSet() ?: emptySet())
        private set

    private var _difficulty by mutableStateOf(Difficulty.fromRaw(prefs.getString(Key.DIFFICULTY, null)) ?: Difficulty.NORMAL)
    var difficulty: Difficulty
        get() = _difficulty
        set(value) {
            _difficulty = value
            prefs.edit { putString(Key.DIFFICULTY, value.raw) }
        }

    private var _autoFire by mutableStateOf(prefs.getBoolean(Key.AUTO_FIRE, true))
    var autoFire: Boolean
        get() = _autoFire
        set(value) {
            _autoFire = value
            prefs.edit { putBoolean(Key.AUTO_FIRE, value) }
        }

    private var _isMuted by mutableStateOf(prefs.getBoolean(Key.MUTED, false))
    var isMuted: Boolean
        get() = _isMuted
        set(value) {
            _isMuted = value
            prefs.edit { putBoolean(Key.MUTED, value) }
            AudioEngine.isMuted = value
        }

    private var _hapticsEnabled by mutableStateOf(prefs.getBoolean(Key.HAPTICS, true))
    var hapticsEnabled: Boolean
        get() = _hapticsEnabled
        set(value) {
            _hapticsEnabled = value
            prefs.edit { putBoolean(Key.HAPTICS, value) }
            Haptics.enabled = value
        }

    var playerName: String = prefs.getString(Key.LAST_NAME, null) ?: "ACE"
        private set(value) {
            field = value
            prefs.edit { putString(Key.LAST_NAME, value) }
        }

    init {
        AudioEngine.isMuted = _isMuted
        Haptics.enabled = _hapticsEnabled
    }

    private fun loadLeaderboard(): List<LeaderboardEntry> {
        val raw = prefs.getString(Key.LEADERBOARD, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<LeaderboardEntry>>(raw).take(LEADERBOARD_CAPACITY)
        } catch (error: Exception) {
            emptyList()
        }
    }

    private fun loadStats(): PlayerStats {
        val raw = prefs.getString(Key.STATS, null) ?: return PlayerStats()
        return try {
            json.decodeFromString<PlayerStats>(raw)
        } catch (error: Exception) {
            PlayerStats()
        }
    }

    // Scores

    fun isHighScore(score: Int): Boolean = score > highScore && score > 0

    fun qualifiesForLeaderboard(score: Int): Boolean {
        if (score <= 0) return false
        if (leaderboard.size < LEADERBOARD_CAPACITY) return true
        return leaderboard.any { score > it.score }
    }

    fun submit(name: String, score: Int, wave: Int, difficulty: Difficulty) {
        val trimmed = name.trim()
        val clean = if (trimmed.isEmpty()) "ACE" else trimmed.take(8).uppercase()
        playerName = clean

        val entry = LeaderboardEntry(
            id = UUID.randomUUID().toString(),
            name = clean,
            score = score,
            wave = wave,
            difficulty = difficulty.raw,
            date = System.currentTimeMillis()
        )
        val updated = (leaderboard + entry)
            .sortedWith(compareByDescending<LeaderboardEntry> { it.score }.thenBy { it.date })
            .take(LEADERBOARD_CAPACITY)
        leaderboard = updated
        prefs.edit { putString(Key.LEADERBOARD, json.encodeToString(updated)) }
    }

    fun recordHighScore(score: Int) {
        if (score <= highScore) return
        highScore = score
        prefs.edit { putInt(Key.HIGH_SCORE, score) }
    }

    // Stats

    /** Folds one finished run into lifetime statistics. */
    fun recordRun(score: Int, wave: Int, kills: Int, bossKills: Int, rescues: Int, perfectWaves: Int) {
        val current = stats
        stats = current.copy(
            runsPlayed = current.runsPlayed + 1,
            totalKills = current.totalKills + kills,
            bossKills = current.bossKills + bossKills,
            rescues = current.rescues + rescues,
            perfectWaves = current.perfectWaves + perfectWaves,
            bestWave = maxOf(current.bestWave, wave),
            bestScore = maxOf(current.bestScore, score)
        )
        persistStats()
    }

    fun recordFormationClear(seconds: Double) {
        if (seconds >= stats.fastestFormationClear) return
        stats = stats.copy(fastestFormationClear = seconds)
        persistStats()
    }

    private fun persistStats() {
        prefs.edit { putString(Key.STATS, json.encodeToString(stats)) }
    }

    // Achievements

    fun isUnlocked(achievement: Achievement): Boolean = unlocked.contains(achievement.raw)

    /** Unlocks an achievement, returning true only the first time it fires. */
    fun unlock(achievement: Achievement): Boolean {
        if (unlocked.contains(achievement.raw)) return false
        val updated = unlocked + achievement.raw
        unlocked = updated
        prefs.edit { putStringSet(Key.ACHIEVEMENTS, updated) }
        return true
    }
}
