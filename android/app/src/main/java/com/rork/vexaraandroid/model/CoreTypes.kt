package com.rork.vexaraandroid.model

import androidx.compose.ui.graphics.Color
import com.rork.vexaraandroid.R
import com.rork.vexaraandroid.ui.theme.Vex

/** Top-level flow state of a run. */
enum class GamePhase { TITLE, PLAYING, PAUSED, GAME_OVER }

enum class Difficulty(
    val raw: String,
    val title: String,
    val blurb: String,
    val accent: Color,
    val startingLives: Int,
    val speedScale: Float,
    val fireScale: Float,
    val diveScale: Float,
    val extraRows: Int,
    val bossHealthScale: Float,
    val scoreScale: Float
) {
    EASY("easy", "EASY", "Slower aliens, forgiving fire. Learn the patterns.", Color(0xFF4DFFB0), 5, 0.78f, 0.55f, 0.6f, -1, 0.7f, 0.7f),
    NORMAL("normal", "NORMAL", "The intended arcade balance.", Color(0xFF2FE8FF), 3, 1.0f, 1.0f, 1.0f, 0, 1.0f, 1.0f),
    HARD("hard", "HARD", "Faster dives, denser fire, tougher bosses.", Color(0xFFFFB020), 3, 1.18f, 1.45f, 1.4f, 0, 1.3f, 1.35f),
    ARCADE("arcade", "ARCADE", "Full cabinet cruelty. Two lives, relentless waves.", Color(0xFFFF2FD4), 2, 1.34f, 1.85f, 1.8f, 1, 1.55f, 1.7f);

    companion object {
        fun fromRaw(raw: String?): Difficulty? = entries.firstOrNull { it.raw == raw }
    }
}

enum class EnemyKind(
    val displayName: String,
    val sprite: Int,
    val health: Int,
    val points: Int,
    val radius: Float,
    val accent: Color,
    val dossier: String
) {
    SWARMER("SWARMER", R.drawable.insectoid_fighter_craft, 1, 100, 16f, Vex.magenta,
        "Cheap chitin fighters. They hold the grid and dive in packs."),
    DARTER("DARTER", R.drawable.alien_interceptor_sprite, 1, 200, 15f, Vex.ember,
        "Breaks formation instantly and knifes straight at your lane."),
    BOMBER("BOMBER", R.drawable.alien_bomber_sprite, 2, 300, 19f, Vex.amber,
        "Lobs slow arcing ordnance that tracks where you were."),
    GUARDIAN("GUARDIAN", R.drawable.alien_warship_sprite, 4, 500, 20f, Vex.violet,
        "Layered plating. Needs four clean hits before it cracks."),
    HUNTER("HUNTER", R.drawable.alien_manta_craft, 2, 400, 18f, Vex.acid,
        "Reads your drift and intercepts ahead of your movement."),
    WARDEN("SIPHON WARDEN", R.drawable.alien_station_boss, 6, 800, 26f, Color(0xFFFF5CE1),
        "Deploys a prism siphon. Linger in the beam and it takes your ship.")
}

enum class BossKind(
    val displayName: String,
    val sprite: Int,
    val baseHealth: Int,
    val accent: Color,
    val widthFraction: Float,
    val points: Int
) {
    VOID_QUEEN("THE VOID QUEEN", R.drawable.alien_mothership_boss, 220, Vex.magenta, 0.78f, 2000),
    HIVE_CORE("THE HIVE CORE", R.drawable.alien_station_boss, 280, Vex.amber, 0.66f, 3000),
    STAR_DEVOURER("THE STAR DEVOURER", R.drawable.alien_leviathan_boss, 340, Color(0xFF6FD9FF), 0.86f, 4000)
}

enum class PowerUpKind(
    val title: String,
    val detail: String,
    val accent: Color,
    val duration: Float
) {
    RAPID_FIRE("RAPID FIRE", "Cuts your reload to a stutter for 12 seconds.", Vex.amber, 12f),
    DOUBLE_SHOT("DOUBLE SHOT", "Two parallel bolts for 15 seconds.", Vex.cyan, 15f),
    TRIPLE_SHOT("TRIPLE SHOT", "A three-way spread for 15 seconds.", Vex.cyan, 15f),
    PLASMA("PLASMA", "Heavier bolts that punch through armour for 14 seconds.", Vex.magenta, 14f),
    SHIELD("SHIELD", "A prism barrier that eats exactly one hit.", Vex.acid, 0f),
    BLAST("BLAST", "Stores a charge. Fire it to vaporise everything nearby.", Vex.ember, 0f),
    SLOW_TIME("SLOW TIME", "Drags enemy movement to half speed for 8 seconds.", Vex.violet, 8f)
}
