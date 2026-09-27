package com.rork.vexaraandroid.game

import androidx.compose.ui.geometry.Offset
import com.rork.vexaraandroid.model.DivePattern
import com.rork.vexaraandroid.model.BulletKind
import com.rork.vexaraandroid.model.Enemy
import com.rork.vexaraandroid.model.EnemyKind
import com.rork.vexaraandroid.model.EnemyState
import com.rork.vexaraandroid.model.EntryPath
import com.rork.vexaraandroid.model.EntryStyle
import com.rork.vexaraandroid.model.WaveDesigner
import com.rork.vexaraandroid.model.WavePlan
import com.rork.vexaraandroid.services.AudioEngine
import com.rork.vexaraandroid.services.Sfx
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

// Formation geometry

val GameEngine.formationColumns: Int get() = WaveDesigner.COLUMNS
val GameEngine.formationTop: Float get() = max(96f, height * 0.14f)
val GameEngine.formationRowSpacing: Float get() = 52f

val GameEngine.formationColumnSpacing: Float
    get() {
        val usable = width - 56f
        return min(48f, usable / formationColumns)
    }

/** Horizontal sway applied to the whole grid with an eased drift. */
val GameEngine.formationSway: Float
    get() {
        val amplitude = min(34f, width * 0.075f)
        return sin(formationPhase) * amplitude
    }

fun GameEngine.slotPosition(slot: Int): Offset {
    val row = slot / formationColumns
    val col = slot % formationColumns
    val mid = (formationColumns - 1) / 2f
    val x = width / 2f + (col - mid) * formationColumnSpacing + formationSway
    val y = formationTop + row * formationRowSpacing + formationDrop
    return Offset(x, y)
}

// Wave construction

fun GameEngine.buildFormation(plan: WavePlan) {
    enemies.clear()
    formationRowCount = plan.rows.size
    formationDrop = 0f
    formationPhase = 0f

    val styles = EntryStyle.entries
    val built = ArrayList<Enemy>()
    var order = 0

    plan.rows.forEachIndexed { rowIndex, row ->
        val count = min(row.count, formationColumns)
        // Centre each row inside the grid.
        val startCol = (formationColumns - count) / 2
        for (column in 0 until count) {
            val slot = rowIndex * formationColumns + (startCol + column)
            val target = slotPosition(slot)
            val style = styles[(rowIndex + column) % styles.size]
            val entry = EntryPath.make(style, target, width, height)

            val enemy = Enemy(
                id = makeId(),
                kind = row.kind,
                slot = slot,
                position = entry.p0,
                health = row.kind.health,
                maxHealth = row.kind.health,
                state = EnemyState.ENTERING,
                entry = entry
            )
            // Stagger arrivals so squadrons stream in rather than pop into place.
            enemy.t = -order * 0.075f
            enemy.pathSpeed = 0.62f
            enemy.side = if (column % 2 == 0) 1f else -1f
            enemy.fireTimer = rnd(2.5f, 7.5f)
            built.add(enemy)
            order += 1
        }
    }

    enemies.addAll(built)
    waveEnemiesSpawned = built.size
}

fun GameEngine.buildChallenge(plan: WavePlan) {
    enemies.clear()
    formationRowCount = 0
    val queue = ArrayList<ChallengeSpawn>()
    for (run in plan.challengeRuns) {
        for (index in 0 until run.count) {
            queue.add(ChallengeSpawn(run.delay + index * run.spacing, run.kind, run.path, run.side))
        }
    }
    queue.sortBy { it.time }
    challengeQueue.clear()
    challengeQueue.addAll(queue)
    challengeTotal = queue.size
    challengeSpawned = 0
}

// Enemy update

fun GameEngine.updateEnemies(dt: Float, realDt: Float) {
    val currentPlan = plan ?: return

    formationPhase += dt * 0.68f
    if (currentPlan.isFormation) {
        // The grid creeps downward as the wave drags on, but never into the player.
        val maxDrop = max(0f, height * 0.2f)
        formationDrop = min(maxDrop, formationDrop + dt * 1.6f)
    }

    if (currentPlan.isChallenge) spawnDueChallengeEnemies()

    val speedRamp = WaveDesigner.speedRamp(wave) * difficulty.speedScale

    for (enemy in enemies) {
        enemy.hitFlash = max(0f, enemy.hitFlash - realDt)
        enemy.spawnFade = min(1f, enemy.spawnFade + realDt * 3.2f)
        advanceEnemy(enemy, dt, speedRamp)
    }

    // Clean up anything that flew off the bottom during a challenge pass.
    enemies.removeAll { it.challenge != null && it.t >= 1f }

    if (!currentPlan.isChallenge) {
        scheduleDives(dt)
        scheduleSiphon(dt)
    }

    updateEnemyFire(realDt, currentPlan)
}

private fun GameEngine.advanceEnemy(enemy: Enemy, dt: Float, speedRamp: Float) {
    when (enemy.state) {
        EnemyState.ENTERING -> {
            enemy.t += dt * enemy.pathSpeed * speedRamp
            if (enemy.t >= 1f) {
                enemy.t = 1f
                enemy.state = EnemyState.FORMATION
                enemy.position = slotPosition(enemy.slot)
                enemy.rotation = 0f
            } else if (enemy.t > 0f) {
                val previous = enemy.position
                enemy.position = enemy.entry.point(enemy.t)
                enemy.rotation = heading(previous, enemy.position)
            }
        }

        EnemyState.FORMATION -> {
            enemy.position = slotPosition(enemy.slot)
            enemy.rotation += (0f - enemy.rotation) * min(1f, dt * 8f)
        }

        EnemyState.STREAMING -> {
            val path = enemy.challenge
            if (path == null) {
                enemy.state = EnemyState.FORMATION
                return
            }
            enemy.t += dt / path.duration * speedRamp
            val previous = enemy.position
            enemy.position = path.position(min(1f, enemy.t), width, height, enemy.side)
            enemy.rotation = heading(previous, enemy.position)
        }

        EnemyState.DIVING -> {
            enemy.t += dt / enemy.pattern.duration * speedRamp
            val previous = enemy.position
            enemy.position = enemy.pattern.position(
                min(1f, enemy.t), enemy.diveStart, enemy.diveTarget, width, height, enemy.side
            )
            enemy.rotation = heading(previous, enemy.position)

            if (enemy.t >= 1f) {
                // Loop around the screen edge and fly back to the grid.
                enemy.returnFrom = Offset(enemy.position.x.coerceIn(30f, width - 30f), -70f)
                enemy.position = enemy.returnFrom
                enemy.state = EnemyState.RETURNING
                enemy.t = 0f
            }
        }

        EnemyState.RETURNING -> {
            enemy.t += dt * 0.85f * speedRamp
            val target = slotPosition(enemy.slot)
            val progress = min(1f, enemy.t)
            val eased = 1f - (1f - progress).pow(3)
            val previous = enemy.position
            enemy.position = Offset(
                enemy.returnFrom.x + (target.x - enemy.returnFrom.x) * eased,
                enemy.returnFrom.y + (target.y - enemy.returnFrom.y) * eased
            )
            enemy.rotation = heading(previous, enemy.position)
            if (progress >= 1f) {
                enemy.state = EnemyState.FORMATION
                enemy.t = 1f
            }
        }

        EnemyState.SIPHON_DESCEND -> {
            enemy.t += dt * 0.8f
            val targetX = enemy.diveTarget.x
            val targetY = height * 0.44f
            val eased = min(1f, enemy.t)
            enemy.position = Offset(
                enemy.diveStart.x + (targetX - enemy.diveStart.x) * eased,
                enemy.diveStart.y + (targetY - enemy.diveStart.y) * eased
            )
            if (eased >= 1f) {
                enemy.state = EnemyState.SIPHON_BEAM
                enemy.t = 0f
                AudioEngine.play(Sfx.CAPTURE)
            }
        }

        EnemyState.SIPHON_BEAM -> {
            enemy.t += dt
            // Warden tracks the player slowly while the beam is live.
            val drift = (player.position.x - enemy.position.x) * min(1f, dt * 0.9f)
            enemy.position = Offset((enemy.position.x + drift).coerceIn(40f, width - 40f), enemy.position.y)
            if (enemy.t > 4.2f) {
                enemy.state = EnemyState.SIPHON_RETREAT
                enemy.returnFrom = enemy.position
                enemy.t = 0f
            }
        }

        EnemyState.SIPHON_RETREAT -> {
            enemy.t += dt * 0.7f * speedRamp
            val target = slotPosition(enemy.slot)
            val eased = 1f - (1f - min(1f, enemy.t)).pow(3)
            enemy.position = Offset(
                enemy.returnFrom.x + (target.x - enemy.returnFrom.x) * eased,
                enemy.returnFrom.y + (target.y - enemy.returnFrom.y) * eased
            )
            if (enemy.t >= 1f) {
                enemy.state = EnemyState.FORMATION
                enemy.t = 1f
            }
        }
    }
}

private fun heading(previous: Offset, current: Offset): Float {
    val dx = current.x - previous.x
    val dy = current.y - previous.y
    if (abs(dx) <= 0.001f && abs(dy) <= 0.001f) return 0f
    // Sprites face down by default, so measure the deviation from straight down.
    return -atan2(dx, dy)
}

// Challenge streaming

private fun GameEngine.spawnDueChallengeEnemies() {
    while (challengeSpawned < challengeQueue.size && challengeQueue[challengeSpawned].time <= waveClock) {
        val spawn = challengeQueue[challengeSpawned]
        challengeSpawned += 1

        val start = spawn.path.position(0f, width, height, spawn.side)
        val enemy = Enemy(
            id = makeId(),
            kind = spawn.kind,
            slot = 0,
            position = start,
            health = spawn.kind.health,
            maxHealth = spawn.kind.health,
            state = EnemyState.STREAMING,
            entry = EntryPath.flat(Offset.Zero)
        )
        enemy.challenge = spawn.path
        enemy.side = spawn.side
        enemy.t = 0f
        enemies.add(enemy)
    }
}

// Dive scheduling

private fun GameEngine.scheduleDives(dt: Float) {
    if (!player.alive) return

    diveTimer -= dt * WaveDesigner.aggression(wave) * difficulty.diveScale
    if (diveTimer > 0f) return

    val settled = enemies.filter { it.state == EnemyState.FORMATION && it.kind != EnemyKind.WARDEN }
    if (settled.isEmpty()) {
        diveTimer = 1.2f
        return
    }

    // Later waves send small squadrons instead of lone attackers.
    val desired = when {
        wave >= 7 -> Random.nextInt(1, 4)
        wave >= 3 -> Random.nextInt(1, 3)
        else -> 1
    }
    val groupSize = min(settled.size, desired)
    val chosen = settled.shuffled().take(groupSize)
    val pattern = pickPattern()

    chosen.forEachIndexed { offset, enemy ->
        enemy.state = EnemyState.DIVING
        enemy.t = -offset * 0.12f
        enemy.diveStart = enemy.position
        enemy.diveTarget = player.position
        enemy.pattern = if (enemy.kind == EnemyKind.DARTER) DivePattern.PLUNGE else pattern
        enemy.side = if (enemy.position.x < width / 2f) 1f else -1f
    }

    diveTimer = rnd(1.5f, 3.4f)
}

private fun GameEngine.pickPattern(): DivePattern {
    val pool = mutableListOf(DivePattern.SWOOP, DivePattern.ZIGZAG, DivePattern.PLUNGE)
    if (wave >= 3) pool.addAll(listOf(DivePattern.SPLIT, DivePattern.BOOMERANG))
    if (wave >= 6) pool.addAll(listOf(DivePattern.SPIRAL, DivePattern.ORBIT))
    return pool.pick()
}

// Siphon warden

private fun GameEngine.scheduleSiphon(dt: Float) {
    if (!player.alive || capturedShipEnemyId != null) return

    wardenTimer -= dt
    if (wardenTimer > 0f) return

    val warden = enemies.firstOrNull { it.kind == EnemyKind.WARDEN && it.state == EnemyState.FORMATION }
    if (warden == null) {
        wardenTimer = 4f
        return
    }

    warden.state = EnemyState.SIPHON_DESCEND
    warden.t = 0f
    warden.diveStart = warden.position
    warden.diveTarget = Offset(player.position.x, height * 0.44f)
    wardenTimer = rnd(13f, 19f)
}

/** Advances siphon capture progress for any warden whose beam covers the ship. */
fun GameEngine.updateSiphonBeams(dt: Float) {
    if (!player.alive || player.invulnerable > 0f || capturedShipEnemyId != null) return

    for (enemy in enemies) {
        if (enemy.state != EnemyState.SIPHON_BEAM) continue
        val halfWidth = 34f
        if (abs(player.position.x - enemy.position.x) >= halfWidth || player.position.y <= enemy.position.y) continue

        player.siphonProgress = min(1f, player.siphonProgress + dt * 0.62f)
        if (player.siphonProgress >= 1f) capturePlayerShip(enemy.id)
        return
    }
}

// Enemy fire

private fun GameEngine.updateEnemyFire(dt: Float, plan: WavePlan) {
    // Challenge waves are strictly a shooting gallery.
    if (plan.isChallenge || !player.alive) return

    val aggression = WaveDesigner.aggression(wave) * difficulty.fireScale
    val snapshot = ArrayList(enemies)
    for (enemy in snapshot) {
        if (enemy.state == EnemyState.ENTERING || enemy.state == EnemyState.SIPHON_BEAM) continue
        enemy.fireTimer -= dt * aggression
        if (enemy.fireTimer > 0f) continue
        fire(enemy)
        enemy.fireTimer = reloadInterval(enemy)
    }
}

private fun reloadInterval(enemy: Enemy): Float = when (enemy.kind) {
    EnemyKind.SWARMER -> rnd(4.5f, 9.5f)
    EnemyKind.DARTER -> rnd(3.0f, 6.5f)
    EnemyKind.BOMBER -> rnd(2.6f, 5.0f)
    EnemyKind.GUARDIAN -> rnd(3.4f, 6.0f)
    EnemyKind.HUNTER -> rnd(3.0f, 5.5f)
    EnemyKind.WARDEN -> rnd(4.0f, 7.0f)
}

private fun GameEngine.fire(enemy: Enemy) {
    val origin = Offset(enemy.position.x, enemy.position.y + enemy.kind.radius)

    when (enemy.kind) {
        EnemyKind.BOMBER -> spawnBullet(origin, rnd(-30f, 30f), 210f, BulletKind.ENEMY_BOMB)

        EnemyKind.HUNTER -> {
            // Leads the player's current drift instead of aiming where they are.
            val lead = player.position.x + player.velocityX * 0.42f
            val dx = lead - origin.x
            val dy = max(120f, player.position.y - origin.y)
            val length = max(1f, hypot(dx, dy))
            val speed = 330f
            spawnBullet(origin, dx / length * speed, dy / length * speed, BulletKind.ENEMY_SHARD)
        }

        EnemyKind.GUARDIAN -> {
            for (offset in listOf(-0.22f, 0f, 0.22f)) {
                spawnBullet(origin, sin(offset) * 300f, cos(offset) * 300f, BulletKind.ENEMY_BOLT)
            }
        }

        EnemyKind.WARDEN -> {
            for (offset in listOf(-0.34f, 0.34f)) {
                spawnBullet(origin, sin(offset) * 290f, cos(offset) * 290f, BulletKind.ENEMY_SHARD)
            }
        }

        else -> {
            val dx = player.position.x - origin.x
            val dy = max(100f, player.position.y - origin.y)
            val length = max(1f, hypot(dx, dy))
            val speed = if (enemy.isDiving) 400f else 300f
            spawnBullet(origin, dx / length * speed * 0.55f, dy / length * speed, BulletKind.ENEMY_BOLT)
        }
    }
}
