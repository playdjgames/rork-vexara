package com.playdjgames.vexara.model

import kotlin.math.min

/** A single grid row in a formation blueprint. */
class FormationRow(val kind: EnemyKind, val count: Int)

sealed interface WaveStyle {
    data object Formation : WaveStyle
    data object Challenge : WaveStyle
    data class BossWave(val kind: BossKind) : WaveStyle
}

/** One squadron pass in a challenge wave. */
class ChallengeRun(
    val kind: EnemyKind,
    val path: ChallengePath,
    val count: Int,
    val delay: Float,
    val spacing: Float,
    val side: Float
)

/** Everything needed to build one wave. */
class WavePlan(
    val index: Int,
    val style: WaveStyle,
    val rows: List<FormationRow>,
    val challengeRuns: List<ChallengeRun>,
    val title: String,
    val subtitle: String
) {
    val isBoss: Boolean get() = style is WaveStyle.BossWave
    val isChallenge: Boolean get() = style == WaveStyle.Challenge
    val isFormation: Boolean get() = style == WaveStyle.Formation
}

object WaveDesigner {
    const val COLUMNS = 8
    const val BOSS_EVERY = 5
    const val CHALLENGE_EVERY = 4

    /** Builds the plan for a given wave number. Waves loop their structure but keep scaling. */
    fun plan(wave: Int, difficulty: Difficulty): WavePlan {
        if (wave % BOSS_EVERY == 0) {
            val bosses = BossKind.entries
            val boss = bosses[((wave / BOSS_EVERY) - 1) % bosses.size]
            return WavePlan(wave, WaveStyle.BossWave(boss), emptyList(), emptyList(), "WARNING", boss.displayName)
        }
        if (wave % CHALLENGE_EVERY == 0) {
            return WavePlan(
                wave, WaveStyle.Challenge, emptyList(), challengeRuns(wave),
                "CHALLENGE WAVE", "NO ENEMY FIRE · CLEAR THEM ALL"
            )
        }
        return WavePlan(
            wave, WaveStyle.Formation, formationRows(wave, difficulty), emptyList(),
            "WAVE $wave", formationSubtitle(wave)
        )
    }

    private fun formationSubtitle(wave: Int): String = when (wave) {
        1 -> "INCOMING FORMATION"
        2 -> "THEY DIVE NOW"
        3 -> "MIXED SQUADRONS"
        6 -> "ARMOURED ESCORT"
        7 -> "HUNTERS ON THE GRID"
        else -> if (wave >= 11) "DEEP SPACE ASSAULT" else "HOLD THE LINE"
    }

    private fun formationRows(wave: Int, difficulty: Difficulty): List<FormationRow> {
        val cycle = ((wave - 1) % 10) + 1
        val rows: MutableList<FormationRow> = when (cycle) {
            1 -> mutableListOf(FormationRow(EnemyKind.SWARMER, 6), FormationRow(EnemyKind.SWARMER, 8))
            2 -> mutableListOf(
                FormationRow(EnemyKind.DARTER, 6), FormationRow(EnemyKind.SWARMER, 8), FormationRow(EnemyKind.SWARMER, 8)
            )
            3 -> mutableListOf(
                FormationRow(EnemyKind.BOMBER, 4), FormationRow(EnemyKind.DARTER, 8), FormationRow(EnemyKind.SWARMER, 8)
            )
            5 -> mutableListOf(
                FormationRow(EnemyKind.GUARDIAN, 4), FormationRow(EnemyKind.BOMBER, 6), FormationRow(EnemyKind.SWARMER, 8)
            )
            6 -> mutableListOf(
                FormationRow(EnemyKind.GUARDIAN, 4), FormationRow(EnemyKind.HUNTER, 6),
                FormationRow(EnemyKind.DARTER, 8), FormationRow(EnemyKind.SWARMER, 8)
            )
            7 -> mutableListOf(
                FormationRow(EnemyKind.WARDEN, 2), FormationRow(EnemyKind.HUNTER, 6),
                FormationRow(EnemyKind.BOMBER, 6), FormationRow(EnemyKind.SWARMER, 8)
            )
            9 -> mutableListOf(
                FormationRow(EnemyKind.WARDEN, 2), FormationRow(EnemyKind.GUARDIAN, 6),
                FormationRow(EnemyKind.HUNTER, 8), FormationRow(EnemyKind.DARTER, 8)
            )
            else -> mutableListOf(
                FormationRow(EnemyKind.BOMBER, 6), FormationRow(EnemyKind.DARTER, 8), FormationRow(EnemyKind.SWARMER, 8)
            )
        }

        val loop = (wave - 1) / 10
        if (loop >= 1) rows.add(0, FormationRow(EnemyKind.GUARDIAN, 4))
        if (loop >= 2) rows.add(0, FormationRow(EnemyKind.WARDEN, 2))

        val extra = difficulty.extraRows
        if (extra > 0) {
            rows.add(FormationRow(EnemyKind.SWARMER, 8))
        } else if (extra < 0 && rows.size > 2) {
            rows.removeAt(rows.size - 1)
        }
        return rows
    }

    private fun challengeRuns(wave: Int): List<ChallengeRun> {
        val variant = ((wave / CHALLENGE_EVERY) - 1) % 3
        return when (variant) {
            0 -> listOf(
                ChallengeRun(EnemyKind.SWARMER, ChallengePath.FIGURE_EIGHT, 8, 0.0f, 0.26f, 1f),
                ChallengeRun(EnemyKind.SWARMER, ChallengePath.FIGURE_EIGHT, 8, 1.6f, 0.26f, -1f),
                ChallengeRun(EnemyKind.DARTER, ChallengePath.CROSS_DOWN, 6, 4.4f, 0.3f, 1f),
                ChallengeRun(EnemyKind.DARTER, ChallengePath.CROSS_DOWN, 6, 5.2f, 0.3f, -1f)
            )
            1 -> listOf(
                ChallengeRun(EnemyKind.DARTER, ChallengePath.DOUBLE_LOOP, 10, 0.0f, 0.24f, 1f),
                ChallengeRun(EnemyKind.SWARMER, ChallengePath.SERPENTINE, 8, 3.0f, 0.22f, -1f),
                ChallengeRun(EnemyKind.SWARMER, ChallengePath.SERPENTINE, 8, 4.0f, 0.22f, 1f),
                ChallengeRun(EnemyKind.BOMBER, ChallengePath.BLOOM, 6, 7.0f, 0.34f, 1f)
            )
            else -> listOf(
                ChallengeRun(EnemyKind.SWARMER, ChallengePath.BLOOM, 10, 0.0f, 0.2f, 1f),
                ChallengeRun(EnemyKind.HUNTER, ChallengePath.FIGURE_EIGHT, 6, 3.2f, 0.3f, -1f),
                ChallengeRun(EnemyKind.DARTER, ChallengePath.DOUBLE_LOOP, 8, 5.6f, 0.24f, 1f),
                ChallengeRun(EnemyKind.DARTER, ChallengePath.CROSS_DOWN, 6, 8.4f, 0.26f, -1f)
            )
        }
    }

    /** Enemy aggression scales with depth but saturates so it never becomes unfair. */
    fun aggression(wave: Int): Float = min(2.4f, 1.0f + (wave - 1) * 0.11f)

    fun speedRamp(wave: Int): Float = min(1.75f, 1.0f + (wave - 1) * 0.045f)
}
