package com.rork.vexaraandroid.game

import androidx.compose.ui.geometry.Offset
import com.rork.vexaraandroid.model.Boss
import com.rork.vexaraandroid.model.BossBeam
import com.rork.vexaraandroid.model.BossCore
import com.rork.vexaraandroid.model.BossKind
import com.rork.vexaraandroid.model.Bullet
import com.rork.vexaraandroid.model.BulletKind
import com.rork.vexaraandroid.model.DivePattern
import com.rork.vexaraandroid.model.Enemy
import com.rork.vexaraandroid.model.EnemyKind
import com.rork.vexaraandroid.model.EnemyState
import com.rork.vexaraandroid.model.EntryPath
import com.rork.vexaraandroid.model.HivePod
import com.rork.vexaraandroid.model.PI_F
import com.rork.vexaraandroid.model.Particle
import com.rork.vexaraandroid.model.ParticleKind
import com.rork.vexaraandroid.model.PowerUpKind
import com.rork.vexaraandroid.services.AudioEngine
import com.rork.vexaraandroid.services.Haptics
import com.rork.vexaraandroid.services.Sfx
import com.rork.vexaraandroid.ui.theme.Vex
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

// Spawning

fun GameEngine.spawnBoss(kind: BossKind) {
    enemies.clear()
    formationRowCount = 0

    val bossWidth = width * kind.widthFraction
    val health = (kind.baseHealth * difficulty.bossHealthScale * (1f + (wave / 15) * 0.35f)).toInt()

    val newBoss = Boss(kind, Offset(width / 2f, -bossWidth * 0.5f), health, health, bossWidth)

    when (kind) {
        BossKind.VOID_QUEEN -> newBoss.cores.addAll(
            listOf(
                BossCore(Offset(0f, bossWidth * 0.06f), bossWidth * 0.1f),
                BossCore(Offset(-bossWidth * 0.28f, -bossWidth * 0.02f), bossWidth * 0.062f),
                BossCore(Offset(bossWidth * 0.28f, -bossWidth * 0.02f), bossWidth * 0.062f)
            )
        )
        BossKind.HIVE_CORE -> {
            newBoss.cores.add(BossCore(Offset.Zero, bossWidth * 0.13f))
            for (index in 0 until 4) {
                newBoss.pods.add(
                    HivePod(
                        id = makeId(),
                        angle = index * (PI_F / 2f) + PI_F / 4f,
                        radius = bossWidth * 0.36f,
                        health = 24,
                        maxHealth = 24,
                        fireTimer = rnd(1.5f, 3.5f)
                    )
                )
            }
        }
        BossKind.STAR_DEVOURER -> newBoss.cores.addAll(
            listOf(
                BossCore(Offset(0f, bossWidth * 0.04f), bossWidth * 0.11f),
                BossCore(Offset(-bossWidth * 0.33f, bossWidth * 0.1f), bossWidth * 0.055f),
                BossCore(Offset(bossWidth * 0.33f, bossWidth * 0.1f), bossWidth * 0.055f)
            )
        )
    }

    boss = newBoss
    syncBossHud()
    shake = 16f
}

// Update

fun GameEngine.updateBoss(dt: Float, realDt: Float) {
    val current = boss ?: return

    current.hitFlash = max(0f, current.hitFlash - realDt)
    for (core in current.cores) core.flash = max(0f, core.flash - realDt)

    if (current.dying) {
        current.deathTimer += realDt
        spawnDeathDebris(current)
        if (current.deathTimer > 2.0f) completeBossDefeat(current)
        return
    }

    if (current.entering) {
        current.entryProgress += dt * 0.42f
        val eased = 1f - (1f - minOf(1f, current.entryProgress)).pow(3)
        val restY = current.width * 0.42f + 40f
        current.position = Offset(current.position.x, -current.width * 0.5f + (restY + current.width * 0.5f) * eased)
        if (current.entryProgress >= 1f) {
            current.entering = false
            current.position = Offset(current.position.x, restY)
        }
        syncBossHud()
        return
    }

    // Lateral drift widens as the boss takes damage.
    current.driftPhase += dt * (0.5f + current.phase * 0.22f)
    val amplitude = width * (0.16f + current.phase * 0.05f)
    val x = (width / 2f + sin(current.driftPhase) * amplitude)
        .coerceIn(current.width * 0.3f, maxOf(current.width * 0.3f, width - current.width * 0.3f))
    current.position = Offset(x, current.position.y)

    updateBeam(current, dt)
    updatePods(current, dt, realDt)

    current.attackTimer -= dt * difficulty.fireScale
    if (current.attackTimer <= 0f && player.alive) performAttack(current)

    current.spawnTimer -= dt
    if (current.spawnTimer <= 0f) {
        spawnEscort(current)
        current.spawnTimer = escortInterval(current)
    }

    syncBossHud()
}

private fun GameEngine.escortInterval(boss: Boss): Float {
    val base = when (boss.kind) {
        BossKind.VOID_QUEEN -> 6.0f
        BossKind.HIVE_CORE -> 8.0f
        BossKind.STAR_DEVOURER -> 9.0f
    }
    return max(3.0f, base - boss.phase * 1.4f) / difficulty.diveScale
}

// Attacks

private fun GameEngine.performAttack(boss: Boss) {
    when (boss.kind) {
        BossKind.VOID_QUEEN -> voidQueenAttack(boss)
        BossKind.HIVE_CORE -> hiveCoreAttack(boss)
        BossKind.STAR_DEVOURER -> starDevourerAttack(boss)
    }
    boss.volleyIndex += 1
}

private fun GameEngine.voidQueenAttack(boss: Boss) {
    val origin = Offset(boss.position.x, boss.position.y + boss.width * 0.22f)

    when (boss.volleyIndex % 3) {
        0 -> {
            // Radial fan that widens with each phase.
            val count = 7 + boss.phase * 3
            val spread = 1.1f + boss.phase * 0.22f
            for (index in 0 until count) {
                val t = index.toFloat() / maxOf(1, count - 1).toFloat()
                val angle = -spread / 2f + spread * t
                spawnBullet(origin, sin(angle) * 275f, cos(angle) * 275f, BulletKind.ENEMY_BOLT)
            }
            boss.attackTimer = 2.4f - boss.phase * 0.35f
        }
        1 -> {
            // Aimed triple at the player's lane.
            val dx = player.position.x - origin.x
            val dy = max(140f, player.position.y - origin.y)
            val length = max(1f, hypot(dx, dy))
            for (offset in listOf(-26f, 0f, 26f)) {
                spawnBullet(
                    Offset(origin.x + offset, origin.y),
                    dx / length * 360f, dy / length * 360f, BulletKind.ENEMY_SHARD
                )
            }
            boss.attackTimer = 1.6f - boss.phase * 0.2f
        }
        else -> {
            // Spiral spray once the fight escalates.
            if (boss.phase >= 1) {
                val arms = 4
                for (arm in 0 until arms) {
                    val angle = boss.volleyIndex * 0.5f + arm * (PI_F * 2f / arms)
                    spawnBullet(origin, sin(angle) * 240f, abs(cos(angle)) * 240f + 90f, BulletKind.ENEMY_BOLT)
                }
            }
            boss.attackTimer = 1.3f
        }
    }
}

private fun GameEngine.hiveCoreAttack(boss: Boss) {
    val origin = Offset(boss.position.x, boss.position.y + boss.width * 0.2f)
    val livePods = boss.pods.count { it.health > 0 }

    if (livePods == 0) {
        // Stripped of pods, the core goes berserk with dense ring bursts.
        val count = 14
        for (index in 0 until count) {
            val angle = index.toFloat() / count * PI_F * 2f
            spawnBullet(origin, sin(angle) * 250f, cos(angle) * 250f, BulletKind.ENEMY_SHARD)
        }
        boss.attackTimer = 1.9f
    } else {
        for (offset in listOf(-0.5f, -0.17f, 0.17f, 0.5f)) {
            spawnBullet(origin, sin(offset) * 300f, cos(offset) * 300f, BulletKind.ENEMY_BOLT)
        }
        boss.attackTimer = 2.2f - boss.phase * 0.3f
    }
}

private fun GameEngine.starDevourerAttack(boss: Boss) {
    // The devourer alternates between charging its sweeping beam and bomb volleys.
    if (boss.beam == null && boss.volleyIndex % 2 == 0) {
        val sweep = if (boss.phase >= 1) 1.15f else 0.8f
        val left = player.position.x < boss.position.x
        boss.beam = BossBeam(
            angle = if (left) -sweep / 2f else sweep / 2f,
            charge = 1.1f,
            active = 0f,
            width = 30f + boss.phase * 10f,
            sweep = if (left) sweep else -sweep
        )
        boss.attackTimer = 4.2f
        AudioEngine.play(Sfx.BOSS_APPEAR)
    } else {
        val origin = Offset(boss.position.x, boss.position.y + boss.width * 0.2f)
        val count = 5 + boss.phase * 2
        for (index in 0 until count) {
            val t = index.toFloat() / maxOf(1, count - 1).toFloat()
            val x = origin.x + (t - 0.5f) * boss.width * 0.72f
            spawnBullet(Offset(x, origin.y), rnd(-25f, 25f), 230f, BulletKind.ENEMY_BOMB)
        }
        boss.attackTimer = 2.5f - boss.phase * 0.35f
    }
}

// Sweeping beam

private fun GameEngine.updateBeam(boss: Boss, dt: Float) {
    val beam = boss.beam ?: return

    if (beam.charge > 0f) {
        beam.charge -= dt
        if (beam.charge <= 0f) {
            beam.active = 2.6f
            shake = max(shake, 10f)
        }
        return
    }

    beam.active -= dt
    if (beam.active <= 0f) {
        boss.beam = null
        return
    }

    // Sweep the beam across the playfield.
    beam.angle += beam.sweep * dt * 0.42f

    val origin = Offset(boss.position.x, boss.position.y + boss.width * 0.18f)

    // Collision: distance from the player to the beam's ray.
    if (player.alive && player.invulnerable <= 0f) {
        val dirX = sin(beam.angle)
        val dirY = cos(beam.angle)
        val toPlayerX = player.position.x - origin.x
        val toPlayerY = player.position.y - origin.y
        val projection = toPlayerX * dirX + toPlayerY * dirY
        if (projection > 0f) {
            val closestX = toPlayerX - dirX * projection
            val closestY = toPlayerY - dirY * projection
            if (hypot(closestX, closestY) < beam.width * 0.5f + player.radius * 0.6f) damagePlayer()
        }
    }

    // Beam edge embers.
    if ((elapsed * 60f).toInt() % 2 == 0) {
        val distance = rnd(60f, max(61f, height))
        val position = Offset(origin.x + sin(beam.angle) * distance, origin.y + cos(beam.angle) * distance)
        particles.add(
            Particle(position, rnd(-50f, 50f), rnd(-50f, 50f), 0.35f, 0.35f, rnd(2f, 5f), boss.kind.accent, ParticleKind.SPARK)
        )
    }
}

// Hive pods

private fun GameEngine.updatePods(boss: Boss, dt: Float, realDt: Float) {
    if (boss.kind != BossKind.HIVE_CORE || boss.pods.isEmpty()) return

    val spin = dt * (0.55f + boss.phase * 0.3f)
    for (pod in boss.pods) {
        pod.hitFlash = max(0f, pod.hitFlash - realDt)
        if (pod.health <= 0) continue

        pod.angle += spin
        // Pods drift outward as the fight escalates.
        if (boss.phase >= 1 && !pod.detached) {
            pod.detached = true
            pod.radius = boss.width * 0.48f
        }

        pod.fireTimer -= dt * difficulty.fireScale
        if (pod.fireTimer <= 0f && player.alive) {
            val origin = podPosition(boss, pod)
            val dx = player.position.x - origin.x
            val dy = max(90f, player.position.y - origin.y)
            val length = max(1f, hypot(dx, dy))
            spawnBullet(origin, dx / length * 320f, dy / length * 320f, BulletKind.ENEMY_BOLT)
            pod.fireTimer = rnd(2.2f, 4.0f)
        }
    }
}

fun podPosition(boss: Boss, pod: HivePod): Offset = Offset(
    boss.position.x + cos(pod.angle) * pod.radius,
    boss.position.y + sin(pod.angle) * pod.radius * 0.62f
)

// Escorts

private fun GameEngine.spawnEscort(boss: Boss) {
    val kinds = when (boss.kind) {
        BossKind.VOID_QUEEN -> listOf(EnemyKind.SWARMER, EnemyKind.SWARMER, EnemyKind.DARTER)
        BossKind.HIVE_CORE -> listOf(EnemyKind.DARTER, EnemyKind.BOMBER)
        BossKind.STAR_DEVOURER -> listOf(EnemyKind.HUNTER, EnemyKind.DARTER)
    }
    val patterns = listOf(DivePattern.SWOOP, DivePattern.ZIGZAG, DivePattern.PLUNGE, DivePattern.SPIRAL)

    val count = 2 + boss.phase
    for (index in 0 until count) {
        val kind = kinds.pick()
        val side = if (index % 2 == 0) -1f else 1f
        val origin = Offset(boss.position.x + side * boss.width * 0.38f, boss.position.y + boss.width * 0.1f)

        val enemy = Enemy(
            id = makeId(),
            kind = kind,
            slot = 0,
            position = origin,
            health = kind.health,
            maxHealth = kind.health,
            state = EnemyState.DIVING,
            entry = EntryPath.flat(origin)
        )
        enemy.diveStart = origin
        enemy.diveTarget = player.position
        enemy.pattern = patterns.pick()
        enemy.side = side
        enemy.t = -index * 0.18f
        enemy.fireTimer = rnd(1.2f, 3.0f)
        appendEnemy(enemy)
    }

    emitSparks(boss.position, boss.kind.accent)
}

// Damage

/** Returns true when the bullet connected with the boss or one of its pods. */
fun GameEngine.hitBossIfNeeded(bullet: Bullet): Boolean {
    val current = boss ?: return false
    if (current.entering || current.dying) return false

    // Pods shield the hive core until they are destroyed.
    if (current.kind == BossKind.HIVE_CORE) {
        for (pod in current.pods) {
            if (pod.health <= 0) continue
            val position = podPosition(current, pod)
            val radius = current.width * 0.11f
            if (hypot(bullet.position.x - position.x, bullet.position.y - position.y) < radius + bullet.radius) {
                pod.health -= bullet.damage
                pod.hitFlash = 0.16f
                AudioEngine.play(Sfx.HIT, rateVariation = true)
                emitSparks(bullet.position, current.kind.accent)

                if (pod.health <= 0) {
                    emitExplosion(position, current.kind.accent, 1.3f, 34)
                    AudioEngine.play(Sfx.ENEMY_EXPLODE)
                    shake = max(shake, 12f)
                    val bonus = (600 * difficulty.scoreScale).toInt()
                    addScore(bonus)
                    addFloatingText("POD DOWN +$bonus", position, Vex.amber, 14f)
                }
                syncBossHud()
                return true
            }
        }
    }

    // Weak-point cores take extra damage; the rest of the hull resists.
    var connected = false
    var damage = bullet.damage

    for (core in current.cores) {
        val cx = current.position.x + core.offset.x
        val cy = current.position.y + core.offset.y
        if (hypot(bullet.position.x - cx, bullet.position.y - cy) < core.radius + bullet.radius) {
            connected = true
            damage = bullet.damage * 3
            core.flash = 0.2f
            break
        }
    }

    if (!connected) {
        val halfWidth = current.width * 0.42f
        val halfHeight = current.width * 0.28f
        if (abs(bullet.position.x - current.position.x) < halfWidth &&
            abs(bullet.position.y - current.position.y) < halfHeight
        ) {
            connected = true
        }
    }

    if (!connected) return false

    current.health -= damage
    current.hitFlash = 0.14f
    AudioEngine.play(Sfx.HIT, rateVariation = true)
    emitSparks(bullet.position, current.kind.accent)

    if (current.health <= 0) {
        current.dying = true
        current.deathTimer = 0f
        current.beam = null
        AudioEngine.play(Sfx.PLAYER_EXPLODE)
        Haptics.heavyBlast()
        shake = 30f
        flashAlpha = 0.5f
        applyHitStop(0.12f)
    }

    syncBossHud()
    return true
}

private fun GameEngine.spawnDeathDebris(boss: Boss) {
    if ((elapsed * 60f).toInt() % 3 != 0) return
    val position = Offset(
        boss.position.x + rnd(-boss.width * 0.4f, boss.width * 0.4f),
        boss.position.y + rnd(-boss.width * 0.22f, boss.width * 0.22f)
    )
    emitExplosion(position, boss.kind.accent, 1.1f, 16)
    shake = max(shake, 8f)
}

private fun GameEngine.completeBossDefeat(defeated: Boss) {
    boss = null
    bossHud = null

    emitExplosion(defeated.position, defeated.kind.accent, 3.4f, 110)
    particles.add(
        Particle(defeated.position, 0f, 0f, 0.8f, 0.8f, defeated.width * 1.4f, defeated.kind.accent, ParticleKind.RING)
    )
    shake = 34f
    flashAlpha = 0.7f

    bullets.removeAll { !it.kind.isPlayer }
    registerBossKill()

    val points = (defeated.kind.points * difficulty.scoreScale).toInt()
    addScore(points)
    addFloatingText("${defeated.kind.displayName} DOWN +$points", defeated.position, defeated.kind.accent, 17f, 28f)

    // Guaranteed reward for the kill.
    spawnDrop(Offset(defeated.position.x - 40f, defeated.position.y), PowerUpKind.SHIELD)
    spawnDrop(Offset(defeated.position.x + 40f, defeated.position.y), PowerUpKind.BLAST)
}
