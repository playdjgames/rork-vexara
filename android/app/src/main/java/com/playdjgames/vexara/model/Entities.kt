package com.playdjgames.vexara.model

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.playdjgames.vexara.ui.theme.Vex

class PlayerShip {
    var position: Offset = Offset.Zero
    var velocityX: Float = 0f
    var alive: Boolean = true
    var invulnerable: Float = 0f
    var respawnTimer: Float = 0f
    var shielded: Boolean = false
    var fireCooldown: Float = 0f
    var tilt: Float = 0f

    var rapidTimer: Float = 0f
    var plasmaTimer: Float = 0f
    var multiShot: Int = 1
    var multiTimer: Float = 0f
    var wingmateTimer: Float = 0f

    /** Progress (0-1) of a warden siphon currently locked onto the ship. */
    var siphonProgress: Float = 0f

    val radius: Float get() = 15f
}

enum class EnemyState { ENTERING, FORMATION, DIVING, RETURNING, STREAMING, SIPHON_DESCEND, SIPHON_BEAM, SIPHON_RETREAT }

class Enemy(
    val id: Int,
    var kind: EnemyKind,
    var slot: Int,
    var position: Offset,
    var health: Int,
    var maxHealth: Int,
    var state: EnemyState,
    var entry: EntryPath
) {
    var t: Float = 0f
    var pathSpeed: Float = 0.45f
    var diveStart: Offset = Offset.Zero
    var diveTarget: Offset = Offset.Zero
    var pattern: DivePattern = DivePattern.SWOOP
    var challenge: ChallengePath? = null
    var side: Float = 1f
    var rotation: Float = 0f
    var fireTimer: Float = 1.2f
    var hitFlash: Float = 0f
    var spawnFade: Float = 0f

    /** True while this warden is carrying the player's captured ship. */
    var holdsCapturedShip: Boolean = false
    var returnFrom: Offset = Offset.Zero

    val isDiving: Boolean
        get() = state == EnemyState.DIVING || state == EnemyState.SIPHON_DESCEND || state == EnemyState.SIPHON_BEAM
}

enum class BulletKind(val isPlayer: Boolean, val color: Color, val length: Float, val width: Float) {
    PULSE(true, Vex.cyan, 16f, 3.5f),
    PLASMA(true, Vex.magenta, 22f, 6f),
    WING(true, Vex.cyan, 16f, 3.5f),
    ENEMY_BOLT(false, Vex.ember, 14f, 4f),
    ENEMY_BOMB(false, Vex.amber, 11f, 9f),
    ENEMY_SHARD(false, Vex.danger, 10f, 4f)
}

class Bullet(
    val id: Int,
    var position: Offset,
    var vx: Float,
    var vy: Float,
    val kind: BulletKind,
    val damage: Int = 1,
    val piercing: Boolean = false
) {
    var life: Float = 0f
    val radius: Float get() = kind.width * 0.9f
}

enum class ParticleKind { SPARK, EMBER, RING, SHARD }

class Particle(
    var position: Offset,
    var vx: Float,
    var vy: Float,
    var life: Float,
    val maxLife: Float,
    val size: Float,
    val color: Color,
    val kind: ParticleKind,
    val drag: Float = 1.9f
)

class FloatingText(
    val id: Int,
    var position: Offset,
    val text: String,
    val color: Color,
    var life: Float,
    val maxLife: Float,
    val fontSize: Float,
    val rise: Float
)

class PowerUpDrop(
    val id: Int,
    var position: Offset,
    var vx: Float,
    var vy: Float,
    val kind: PowerUpKind
) {
    var life: Float = 0f
    var spin: Float = 0f
}

class BossCore(val offset: Offset, val radius: Float) {
    var flash: Float = 0f
}

class BossBeam(
    /** Angle in radians, measured from straight down. */
    var angle: Float,
    var charge: Float,
    var active: Float,
    val width: Float,
    val sweep: Float
)

class HivePod(
    val id: Int,
    var angle: Float,
    var radius: Float,
    var health: Int,
    val maxHealth: Int,
    var fireTimer: Float
) {
    var hitFlash: Float = 0f
    var detached: Boolean = false
}

class Boss(
    val kind: BossKind,
    var position: Offset,
    var health: Int,
    val maxHealth: Int,
    val width: Float
) {
    var entering: Boolean = true
    var entryProgress: Float = 0f
    var driftPhase: Float = 0f
    var attackTimer: Float = 2.2f
    var spawnTimer: Float = 5f
    var hitFlash: Float = 0f
    val cores: MutableList<BossCore> = ArrayList()
    var beam: BossBeam? = null
    val pods: MutableList<HivePod> = ArrayList()
    var volleyIndex: Int = 0
    var deathTimer: Float = 0f
    var dying: Boolean = false

    val phase: Int
        get() {
            val ratio = health.toFloat() / maxOf(1, maxHealth).toFloat()
            if (ratio > 0.66f) return 0
            if (ratio > 0.33f) return 1
            return 2
        }

    val healthFraction: Float
        get() = maxOf(0, health).toFloat() / maxOf(1, maxHealth).toFloat()
}

class Star(var position: Offset, val depth: Float, val size: Float, var twinkle: Float, val color: Color)

class Debris(var position: Offset, val depth: Float, val size: Float, val spin: Float, var rotation: Float)
