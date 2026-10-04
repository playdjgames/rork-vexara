package com.playdjgames.vexara.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.playdjgames.vexara.model.Achievement
import com.playdjgames.vexara.model.AchievementToast
import com.playdjgames.vexara.model.Boss
import com.playdjgames.vexara.model.BossHudState
import com.playdjgames.vexara.model.BossKind
import com.playdjgames.vexara.model.Bullet
import com.playdjgames.vexara.model.BulletKind
import com.playdjgames.vexara.model.Debris
import com.playdjgames.vexara.model.Difficulty
import com.playdjgames.vexara.model.Enemy
import com.playdjgames.vexara.model.EnemyKind
import com.playdjgames.vexara.model.FloatingText
import com.playdjgames.vexara.model.GameOverSummary
import com.playdjgames.vexara.model.GamePhase
import com.playdjgames.vexara.model.PI_F
import com.playdjgames.vexara.model.Particle
import com.playdjgames.vexara.model.ParticleKind
import com.playdjgames.vexara.model.PlayerShip
import com.playdjgames.vexara.model.PowerUpBadge
import com.playdjgames.vexara.model.PowerUpDrop
import com.playdjgames.vexara.model.PowerUpKind
import com.playdjgames.vexara.model.Star
import com.playdjgames.vexara.model.WaveDesigner
import com.playdjgames.vexara.model.WavePlan
import com.playdjgames.vexara.model.WaveStyle
import com.playdjgames.vexara.model.WaveSummary
import com.playdjgames.vexara.services.AudioEngine
import com.playdjgames.vexara.services.Haptics
import com.playdjgames.vexara.services.MusicTrack
import com.playdjgames.vexara.services.ProgressStore
import com.playdjgames.vexara.services.Sfx
import com.playdjgames.vexara.ui.theme.Vex
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Uniform random float in [a, b]. */
fun rnd(a: Float, b: Float): Float = a + Random.nextFloat() * (b - a)

/** Random element of a non-empty list. */
fun <T> List<T>.pick(): T = this[Random.nextInt(size)]

/** One queued challenge-wave spawn. */
class ChallengeSpawn(val time: Float, val kind: EnemyKind, val path: com.playdjgames.vexara.model.ChallengePath, val side: Float)

/**
 * The simulation core. Owns all gameplay state; the game screen drives [update] from the
 * display frame clock while [phase] is PLAYING. HUD-facing values are Compose state.
 */
class GameEngine(val store: ProgressStore) {
    // Published run state

    var phase by mutableStateOf(GamePhase.TITLE)
    var score by mutableIntStateOf(0)
    var wave by mutableIntStateOf(0)
    var lives by mutableIntStateOf(3)
    var combo by mutableIntStateOf(1)
    var bestCombo: Int = 1
    var blastCharges by mutableIntStateOf(1)

    var waveSummary by mutableStateOf<WaveSummary?>(null)
    var gameOverSummary by mutableStateOf<GameOverSummary?>(null)
    var bossHud by mutableStateOf<BossHudState?>(null)
    var activeBadges by mutableStateOf<List<PowerUpBadge>>(emptyList())
    var achievementToast by mutableStateOf<AchievementToast?>(null)

    var bannerTitle by mutableStateOf("")
    var bannerSubtitle by mutableStateOf("")
    var bannerVisible by mutableStateOf(false)
    var comboVisible by mutableStateOf(false)
    var captureVisible by mutableStateOf(false)
    var rescueVisible by mutableStateOf(false)
    var isBossWave by mutableStateOf(false)

    /** Incremented every simulation tick so the canvas redraws. */
    var frameToken by mutableIntStateOf(0)

    internal var bannerTimer = 0f
    internal var comboFlashTimer = 0f
    internal var captureNoticeTimer = 0f
    internal var rescueNoticeTimer = 0f

    // Configuration

    var difficulty: Difficulty = store.difficulty
    var autoFire: Boolean = store.autoFire

    // World

    var width = 390f
        private set
    var height = 760f
        private set
    var player = PlayerShip()
    val enemies = ArrayList<Enemy>()
    val bullets = ArrayList<Bullet>()
    val particles = ArrayList<Particle>()
    val floatingTexts = ArrayList<FloatingText>()
    val drops = ArrayList<PowerUpDrop>()
    var boss: Boss? = null
    val stars = ArrayList<Star>()
    val debris = ArrayList<Debris>()

    /** Ship captured by a warden and currently rendered attached to it. */
    var capturedShipEnemyId: Int? = null
        internal set

    var shake = 0f
    var flashAlpha = 0f
    var slowMoTimer = 0f
    var elapsed = 0f
    var isChallengeWave = false

    // Internals

    private var nextId = 1
    private var hitStop = 0f
    private var comboDecay = 0f
    private var toastCounter = 0L

    var plan: WavePlan? = null
        private set
    internal var diveTimer = 2.4f
    internal var wardenTimer = 6f
    internal var waveClock = 0f
    internal var waveEnemiesDestroyed = 0
    internal var waveEnemiesSpawned = 0
    private var wavePointsEarned = 0
    internal var waveWasHit = false
    private var waveEnded = false
    private var endWaveDelay = 0f
    private var intermissionDelay = 0f

    internal val challengeQueue = ArrayList<ChallengeSpawn>()
    internal var challengeSpawned = 0
    internal var challengeTotal = 0

    internal var formationRowCount = 0
    internal var formationDrop = 0f
    internal var formationPhase = 0f

    private var runKills = 0
    private var runBossKills = 0
    private var runRescues = 0
    private var runPerfectWaves = 0
    private val pendingAchievements = ArrayList<Achievement>()
    private var toastTimer = 0f

    /** Ship x the finger is steering toward (relative drag), or null when not touching. */
    var dragTarget: Float? = null
    var firePressed = false
    private var fireHold = 0f

    // Layout

    fun updateSize(newWidth: Float, newHeight: Float) {
        if (newWidth <= 1f || newHeight <= 1f) return
        val wasEmpty = stars.isEmpty()
        val oldWidth = width
        width = newWidth
        height = newHeight
        if (wasEmpty) seedBackdrop()
        var x = player.position.x
        if (oldWidth > 1f && abs(oldWidth - newWidth) > 0.5f) x *= newWidth / oldWidth
        player.position = Offset(x.coerceIn(edgeInset, width - edgeInset), playerRestY)
    }

    val playerRestY: Float get() = height - 118f
    val edgeInset: Float get() = 26f

    private fun seedBackdrop() {
        stars.clear()
        val palette = listOf(Vex.textPrimary, Vex.cyan, Vex.magenta, Vex.violet)
        repeat(130) {
            val depth = rnd(0.25f, 1f)
            stars.add(
                Star(
                    position = Offset(rnd(0f, width), rnd(0f, height)),
                    depth = depth,
                    size = depth * 2.1f + 0.5f,
                    twinkle = rnd(0f, PI_F * 2f),
                    color = palette.pick()
                )
            )
        }
        debris.clear()
        repeat(9) {
            debris.add(
                Debris(
                    position = Offset(rnd(0f, width), rnd(0f, height)),
                    depth = rnd(0.3f, 0.85f),
                    size = rnd(14f, 42f),
                    spin = rnd(-0.35f, 0.35f),
                    rotation = rnd(0f, PI_F * 2f)
                )
            )
        }
    }

    // Lifecycle

    fun startRun() {
        difficulty = store.difficulty
        autoFire = true

        score = 0
        wave = 0
        lives = difficulty.startingLives
        combo = 1
        bestCombo = 1
        blastCharges = 1
        runKills = 0
        runBossKills = 0
        runRescues = 0
        runPerfectWaves = 0
        elapsed = 0f
        comboDecay = 0f
        hitStop = 0f
        shake = 0f
        flashAlpha = 0f
        slowMoTimer = 0f

        enemies.clear()
        bullets.clear()
        particles.clear()
        floatingTexts.clear()
        drops.clear()
        boss = null
        bossHud = null
        capturedShipEnemyId = null
        waveSummary = null
        gameOverSummary = null
        activeBadges = emptyList()
        dragTarget = null
        firePressed = false

        player = PlayerShip()
        player.position = Offset(width / 2f, playerRestY)

        phase = GamePhase.PLAYING
        startWave(1)
    }

    fun returnToTitle() {
        phase = GamePhase.TITLE
        enemies.clear()
        bullets.clear()
        particles.clear()
        drops.clear()
        boss = null
        bossHud = null
        AudioEngine.playMusic(MusicTrack.MENU)
    }

    fun pause() {
        if (phase != GamePhase.PLAYING) return
        dragTarget = null
        firePressed = false
        phase = GamePhase.PAUSED
    }

    fun resume() {
        if (phase != GamePhase.PAUSED) return
        phase = GamePhase.PLAYING
    }

    // Main update

    fun update(rawDt: Float) {
        if (phase != GamePhase.PLAYING) return

        elapsed += rawDt
        advanceBackdrop(rawDt)
        decayTimers(rawDt)

        // Hit-stop freezes gameplay briefly for impact without stalling effects.
        if (hitStop > 0f) {
            hitStop -= rawDt
            updateParticles(rawDt)
            frameToken += 1
            return
        }

        val enemyScale = if (slowMoTimer > 0f) 0.45f else 1f
        val dt = rawDt

        waveClock += dt
        updatePlayer(dt)
        updateEnemies(dt * enemyScale, dt)
        updateSiphonBeams(dt)
        updateBoss(dt * enemyScale, dt)
        updateBullets(dt)
        updateDrops(dt)
        updateParticles(dt)
        updateFloatingTexts(dt)
        resolveCollisions()
        refreshBadges()
        checkWaveCompletion(dt)
        tickWaveIntermission(dt)

        frameToken += 1
    }

    private fun decayTimers(dt: Float) {
        shake = max(0f, shake - dt * 34f)
        flashAlpha = max(0f, flashAlpha - dt * 3.4f)
        slowMoTimer = max(0f, slowMoTimer - dt)
        bannerTimer = max(0f, bannerTimer - dt)
        comboFlashTimer = max(0f, comboFlashTimer - dt)
        captureNoticeTimer = max(0f, captureNoticeTimer - dt)
        rescueNoticeTimer = max(0f, rescueNoticeTimer - dt)
        fireHold = max(0f, fireHold - dt)

        bannerVisible = bannerTimer > 0f
        comboVisible = combo >= 2 && comboFlashTimer > 0f
        captureVisible = captureNoticeTimer > 0f
        rescueVisible = rescueNoticeTimer > 0f

        if (comboDecay > 0f) {
            comboDecay -= dt
            if (comboDecay <= 0f && combo > 1) combo = 1
        }

        if (toastTimer > 0f) {
            toastTimer -= dt
            if (toastTimer <= 0f) {
                achievementToast = null
                presentNextAchievement()
            }
        } else if (achievementToast == null) {
            presentNextAchievement()
        }
    }

    private fun advanceBackdrop(dt: Float) {
        val base = if (isBossWave) 26f else 18f
        for (star in stars) {
            var y = star.position.y + star.depth * base * dt
            var x = star.position.x
            star.twinkle += dt * 2.2f
            if (y > height) {
                y = -4f
                x = rnd(0f, width)
            }
            star.position = Offset(x, y)
        }
        for (piece in debris) {
            var y = piece.position.y + piece.depth * 12f * dt
            var x = piece.position.x
            piece.rotation += piece.spin * dt
            if (y > height + 60f) {
                y = -60f
                x = rnd(0f, width)
            }
            piece.position = Offset(x, y)
        }
    }

    // Player

    private fun updatePlayer(dt: Float) {
        if (!player.alive) {
            player.respawnTimer -= dt
            if (player.respawnTimer <= 0f) respawnPlayer()
            return
        }

        player.invulnerable = max(0f, player.invulnerable - dt)
        player.rapidTimer = max(0f, player.rapidTimer - dt)
        player.plasmaTimer = max(0f, player.plasmaTimer - dt)
        player.wingmateTimer = max(0f, player.wingmateTimer - dt)

        if (player.multiTimer > 0f) {
            player.multiTimer -= dt
            if (player.multiTimer <= 0f) player.multiShot = 1
        }

        // Horizontal movement with quick acceleration and firm damping.
        val target = dragTarget?.let { (it - player.position.x) * 12f } ?: 0f
        player.velocityX += (target - player.velocityX) * min(1f, dt * 22f)
        val x = (player.position.x + player.velocityX * dt).coerceIn(edgeInset, width - edgeInset)
        player.position = Offset(x, playerRestY)

        val desiredTilt = (player.velocityX / 620f).coerceIn(-1f, 1f)
        player.tilt += (desiredTilt - player.tilt) * min(1f, dt * 12f)

        // Siphon lock drains only while the player stays inside an active beam.
        if (player.siphonProgress > 0f) {
            player.siphonProgress = max(0f, player.siphonProgress - dt * 0.6f)
        }

        player.fireCooldown -= dt
        val shouldFire = autoFire || firePressed || fireHold > 0f
        if (shouldFire && player.fireCooldown <= 0f) fireWeapon()
    }

    /** Keeps manual fire alive briefly after a quick tap so taps always shoot. */
    fun registerTapFire() {
        fireHold = 0.12f
    }

    private fun fireWeapon() {
        val rapid = player.rapidTimer > 0f
        player.fireCooldown = if (rapid) 0.085f else 0.17f

        val plasma = player.plasmaTimer > 0f
        val kind = if (plasma) BulletKind.PLASMA else BulletKind.PULSE
        val damage = if (plasma) 3 else 1
        val speed = if (plasma) -900f else -1080f
        val ox = player.position.x
        val oy = player.position.y - 20f

        when (player.multiShot) {
            2 -> {
                spawnBullet(Offset(ox - 11f, oy), 0f, speed, kind, damage, plasma)
                spawnBullet(Offset(ox + 11f, oy), 0f, speed, kind, damage, plasma)
            }
            3 -> {
                spawnBullet(Offset(ox, oy), 0f, speed, kind, damage, plasma)
                spawnBullet(Offset(ox - 13f, oy + 5f), -235f, speed * 0.95f, kind, damage, plasma)
                spawnBullet(Offset(ox + 13f, oy + 5f), 235f, speed * 0.95f, kind, damage, plasma)
            }
            else -> spawnBullet(Offset(ox, oy), 0f, speed, kind, damage, plasma)
        }

        // Rescued wing-mates add flanking fire.
        if (player.wingmateTimer > 0f) {
            spawnBullet(Offset(ox - 34f, oy + 8f), 0f, -980f, BulletKind.WING, 1)
            spawnBullet(Offset(ox + 34f, oy + 8f), 0f, -980f, BulletKind.WING, 1)
        }

        AudioEngine.play(Sfx.SHOOT, rateVariation = true)
    }

    private fun respawnPlayer() {
        player.alive = true
        player.position = Offset(width / 2f, playerRestY)
        player.velocityX = 0f
        player.invulnerable = 2.2f
        player.siphonProgress = 0f
        player.multiShot = 1
        player.multiTimer = 0f
        player.rapidTimer = 0f
        player.plasmaTimer = 0f
    }

    fun triggerBlast() {
        if (phase != GamePhase.PLAYING || blastCharges <= 0 || !player.alive) return
        blastCharges -= 1
        AudioEngine.play(Sfx.BLAST)
        Haptics.heavyBlast()
        shake = 26f
        flashAlpha = 0.65f

        val center = player.position
        val radius = 260f

        for (index in enemies.indices.reversed()) {
            val enemy = enemies[index]
            val d = hypot(enemy.position.x - center.x, enemy.position.y - center.y)
            if (d < radius) {
                enemies.removeAt(index)
                destroyEnemy(enemy, awardPoints = true, chance = 0.06f)
            }
        }

        bullets.removeAll { !it.kind.isPlayer }

        boss?.let { current ->
            current.health -= 26
            current.hitFlash = 0.3f
            syncBossHud()
        }

        val colors = listOf(Vex.ember, Vex.amber, Vex.cyan)
        repeat(70) {
            val angle = rnd(0f, PI_F * 2f)
            val speed = rnd(120f, 620f)
            particles.add(
                Particle(center, cos(angle) * speed, sin(angle) * speed, rnd(0.4f, 0.9f), 0.9f, rnd(2f, 6f), colors.pick(), ParticleKind.SPARK)
            )
        }
        particles.add(Particle(center, 0f, 0f, 0.5f, 0.5f, radius, Vex.cyan, ParticleKind.RING))
    }

    // Bullets

    fun spawnBullet(position: Offset, vx: Float, vy: Float, kind: BulletKind, damage: Int = 1, piercing: Boolean = false) {
        bullets.add(Bullet(makeId(), position, vx, vy, kind, damage, piercing))
    }

    private fun updateBullets(dt: Float) {
        for (bullet in bullets) {
            bullet.position = Offset(bullet.position.x + bullet.vx * dt, bullet.position.y + bullet.vy * dt)
            bullet.life += dt
            // Bombs arc slightly toward the player's lane.
            if (bullet.kind == BulletKind.ENEMY_BOMB) {
                val dx = player.position.x - bullet.position.x
                bullet.vx += dx.coerceIn(-40f, 40f) * dt * 1.1f
            }
        }
        bullets.removeAll { b ->
            b.position.y < -60f || b.position.y > height + 60f || b.position.x < -70f || b.position.x > width + 70f
        }
    }

    // Drops

    private fun updateDrops(dt: Float) {
        for (drop in drops) {
            drop.position = Offset(drop.position.x + drop.vx * dt, drop.position.y + drop.vy * dt)
            drop.life += dt
            drop.spin += dt * 2.4f
            if (drop.position.x < 18f || drop.position.x > width - 18f) drop.vx *= -1f
        }
        drops.removeAll { it.position.y > height + 40f }
    }

    fun spawnDrop(position: Offset, kind: PowerUpKind) {
        drops.add(PowerUpDrop(makeId(), position, rnd(-34f, 34f), rnd(92f, 132f), kind))
    }

    private fun collect(drop: PowerUpDrop) {
        AudioEngine.play(Sfx.POWER_UP)
        Haptics.success()
        addFloatingText(drop.kind.title, drop.position, drop.kind.accent, 15f)

        when (drop.kind) {
            PowerUpKind.RAPID_FIRE -> player.rapidTimer = drop.kind.duration
            PowerUpKind.DOUBLE_SHOT -> {
                player.multiShot = max(player.multiShot, 2)
                player.multiTimer = drop.kind.duration
            }
            PowerUpKind.TRIPLE_SHOT -> {
                player.multiShot = 3
                player.multiTimer = drop.kind.duration
            }
            PowerUpKind.PLASMA -> player.plasmaTimer = drop.kind.duration
            PowerUpKind.SHIELD -> player.shielded = true
            PowerUpKind.BLAST -> blastCharges = min(3, blastCharges + 1)
            PowerUpKind.SLOW_TIME -> slowMoTimer = drop.kind.duration
        }

        repeat(22) {
            val angle = rnd(0f, PI_F * 2f)
            val speed = rnd(60f, 220f)
            particles.add(
                Particle(drop.position, cos(angle) * speed, sin(angle) * speed, rnd(0.3f, 0.6f), 0.6f, rnd(1.5f, 3.5f), drop.kind.accent, ParticleKind.SPARK)
            )
        }
    }

    // Effects

    private fun updateParticles(dt: Float) {
        for (p in particles) {
            p.life -= dt
            p.position = Offset(p.position.x + p.vx * dt, p.position.y + p.vy * dt)
            val damping = 1f - min(0.95f, p.drag * dt)
            p.vx *= damping
            p.vy *= damping
        }
        particles.removeAll { it.life <= 0f }
        if (particles.size > 460) particles.subList(0, particles.size - 460).clear()
    }

    private fun updateFloatingTexts(dt: Float) {
        for (item in floatingTexts) {
            item.life -= dt
            item.position = Offset(item.position.x, item.position.y - item.rise * dt)
        }
        floatingTexts.removeAll { it.life <= 0f }
    }

    fun addFloatingText(text: String, position: Offset, color: Color, size: Float = 13f, rise: Float = 46f) {
        floatingTexts.add(FloatingText(makeId(), position, text, color, 0.95f, 0.95f, size, rise))
    }

    fun emitExplosion(position: Offset, color: Color, scale: Float = 1f, count: Int = 26) {
        repeat(count) {
            val angle = rnd(0f, PI_F * 2f)
            val speed = rnd(60f, 340f) * scale
            particles.add(
                Particle(
                    position, cos(angle) * speed, sin(angle) * speed, rnd(0.28f, 0.72f), 0.72f,
                    rnd(1.6f, 4.4f) * scale, if (Random.nextBoolean()) color else Vex.textPrimary, ParticleKind.SPARK
                )
            )
        }
        particles.add(Particle(position, 0f, 0f, 0.34f, 0.34f, 46f * scale, color, ParticleKind.RING))
    }

    fun applyHitStop(duration: Float) {
        hitStop = max(hitStop, duration)
    }

    // Collisions

    private fun resolveCollisions() {
        val removedBullets = HashSet<Int>()

        // Player shots against enemies.
        val snapshot = ArrayList(bullets)
        for (bullet in snapshot) {
            if (!bullet.kind.isPlayer || removedBullets.contains(bullet.id)) continue

            for (enemyIndex in enemies.indices) {
                val enemy = enemies[enemyIndex]
                val r = enemy.kind.radius + bullet.radius
                if (abs(enemy.position.x - bullet.position.x) < r && abs(enemy.position.y - bullet.position.y) < r) {
                    enemy.health -= bullet.damage
                    enemy.hitFlash = 0.14f
                    if (!bullet.piercing) removedBullets.add(bullet.id)

                    if (enemy.health <= 0) {
                        enemies.removeAt(enemyIndex)
                        destroyEnemy(enemy, awardPoints = true, chance = dropChance(enemy.kind))
                    } else {
                        AudioEngine.play(Sfx.HIT, rateVariation = true)
                        emitSparks(bullet.position, enemy.kind.accent)
                    }
                    break
                }
            }

            if (removedBullets.contains(bullet.id)) continue
            if (hitBossIfNeeded(bullet)) {
                if (!bullet.piercing) removedBullets.add(bullet.id)
            }
        }

        // Enemy fire against the player.
        if (player.alive && player.invulnerable <= 0f) {
            for (bullet in bullets) {
                if (bullet.kind.isPlayer) continue
                val r = player.radius + bullet.radius
                if (abs(player.position.x - bullet.position.x) < r && abs(player.position.y - bullet.position.y) < r) {
                    removedBullets.add(bullet.id)
                    damagePlayer()
                    break
                }
            }
        }

        // Ramming diving enemies.
        if (player.alive && player.invulnerable <= 0f) {
            for (index in enemies.indices) {
                val enemy = enemies[index]
                if (!enemy.isDiving) continue
                val r = player.radius + enemy.kind.radius * 0.8f
                if (abs(player.position.x - enemy.position.x) < r && abs(player.position.y - enemy.position.y) < r) {
                    enemies.removeAt(index)
                    destroyEnemy(enemy, awardPoints = false, chance = 0f)
                    damagePlayer()
                    break
                }
            }
        }

        if (removedBullets.isNotEmpty()) bullets.removeAll { removedBullets.contains(it.id) }

        // Power-up pickup.
        if (player.alive) {
            for (index in drops.indices.reversed()) {
                val drop = drops[index]
                val d = hypot(drop.position.x - player.position.x, drop.position.y - player.position.y)
                if (d < 34f) {
                    drops.removeAt(index)
                    collect(drop)
                }
            }
        }

        checkBossContact()
    }

    /** Ramming the boss hull or a live hive pod destroys the player's ship. */
    private fun checkBossContact() {
        val current = boss ?: return
        if (!player.alive || player.invulnerable > 0f || current.entering || current.dying) return

        val halfWidth = current.width * 0.4f
        val halfHeight = current.width * 0.26f
        if (abs(player.position.x - current.position.x) < halfWidth + player.radius &&
            abs(player.position.y - current.position.y) < halfHeight + player.radius
        ) {
            damagePlayer()
            return
        }

        for (pod in current.pods) {
            if (pod.health <= 0) continue
            val position = podPosition(current, pod)
            if (hypot(player.position.x - position.x, player.position.y - position.y) < current.width * 0.11f + player.radius) {
                damagePlayer()
                return
            }
        }
    }

    fun emitSparks(position: Offset, color: Color) {
        repeat(7) {
            val angle = rnd(0f, PI_F * 2f)
            val speed = rnd(40f, 170f)
            particles.add(
                Particle(position, cos(angle) * speed, sin(angle) * speed, rnd(0.15f, 0.32f), 0.32f, rnd(1.2f, 2.6f), color, ParticleKind.SPARK)
            )
        }
    }

    private fun dropChance(kind: EnemyKind): Float = when (kind) {
        EnemyKind.SWARMER -> 0.06f
        EnemyKind.DARTER -> 0.09f
        EnemyKind.BOMBER -> 0.14f
        EnemyKind.HUNTER -> 0.16f
        EnemyKind.GUARDIAN -> 0.28f
        EnemyKind.WARDEN -> 0.5f
    }

    /** Removes an enemy with full feedback, scoring, combo and drop handling. */
    fun destroyEnemy(enemy: Enemy, awardPoints: Boolean, chance: Float) {
        emitExplosion(enemy.position, enemy.kind.accent)
        AudioEngine.play(Sfx.ENEMY_EXPLODE, rateVariation = true)
        Haptics.kill()
        shake = max(shake, 5f)

        if (enemy.holdsCapturedShip) rescueCapturedShip(enemy.position)

        waveEnemiesDestroyed += 1
        runKills += 1

        if (awardPoints) {
            bumpCombo()
            val base = enemy.kind.points * difficulty.scoreScale
            val gained = base.toInt() * combo
            addScore(gained)
            wavePointsEarned += gained
            addFloatingText("$gained", enemy.position, enemy.kind.accent)
        }

        if (Random.nextFloat() < chance) spawnDrop(enemy.position, randomPowerUp())

        if (runKills == 1) queueAchievement(Achievement.FIRST_BLOOD)
        if (store.stats.totalKills + runKills >= 100) queueAchievement(Achievement.ALIEN_HUNTER)
    }

    private val powerUpPool = listOf(
        PowerUpKind.RAPID_FIRE, PowerUpKind.RAPID_FIRE,
        PowerUpKind.DOUBLE_SHOT, PowerUpKind.DOUBLE_SHOT,
        PowerUpKind.TRIPLE_SHOT,
        PowerUpKind.PLASMA,
        PowerUpKind.SHIELD, PowerUpKind.SHIELD,
        PowerUpKind.BLAST,
        PowerUpKind.SLOW_TIME
    )

    private fun randomPowerUp(): PowerUpKind = powerUpPool.pick()

    private fun bumpCombo() {
        combo = min(10, combo + 1)
        bestCombo = max(bestCombo, combo)
        comboDecay = 3.4f
        if (combo >= 2) {
            comboFlashTimer = 0.7f
            AudioEngine.play(Sfx.COMBO, rateVariation = true)
        }
    }

    fun addScore(amount: Int) {
        val before = score
        score += amount
        if (before < 100_000 && score >= 100_000) queueAchievement(Achievement.HIGH_ROLLER)
    }

    fun damagePlayer() {
        if (!player.alive || player.invulnerable > 0f) return

        waveWasHit = true
        combo = 1

        if (player.shielded) {
            player.shielded = false
            player.invulnerable = 1.1f
            AudioEngine.play(Sfx.HIT)
            Haptics.hit()
            shake = 12f
            addFloatingText("SHIELD DOWN", player.position, Vex.acid, 14f)
            particles.add(Particle(player.position, 0f, 0f, 0.4f, 0.4f, 70f, Vex.acid, ParticleKind.RING))
            return
        }

        killPlayer()
    }

    private fun killPlayer() {
        player.alive = false
        player.respawnTimer = 1.7f
        player.shielded = false
        player.siphonProgress = 0f
        lives -= 1

        AudioEngine.play(Sfx.PLAYER_EXPLODE)
        Haptics.failure()
        shake = 24f
        flashAlpha = 0.5f
        applyHitStop(0.09f)
        emitExplosion(player.position, Vex.cyan, 1.6f, 46)

        if (lives <= 0) endRun()
    }

    // Wave flow

    fun startWave(number: Int) {
        wave = number
        val newPlan = WaveDesigner.plan(number, difficulty)
        plan = newPlan

        waveClock = 0f
        waveEnemiesDestroyed = 0
        waveEnemiesSpawned = 0
        wavePointsEarned = 0
        waveWasHit = false
        waveEnded = false
        endWaveDelay = 0f
        diveTimer = 3.0f
        wardenTimer = 7.0f
        challengeSpawned = 0
        challengeQueue.clear()
        challengeTotal = 0

        isBossWave = newPlan.isBoss
        isChallengeWave = newPlan.isChallenge

        bannerTitle = newPlan.title
        bannerSubtitle = newPlan.subtitle
        bannerTimer = 2.4f
        bannerVisible = true

        bullets.removeAll { !it.kind.isPlayer }

        when (val style = newPlan.style) {
            WaveStyle.Formation -> {
                buildFormation(newPlan)
                AudioEngine.playMusic(MusicTrack.BATTLE)
            }
            WaveStyle.Challenge -> {
                buildChallenge(newPlan)
                AudioEngine.playMusic(MusicTrack.BATTLE)
            }
            is WaveStyle.BossWave -> {
                spawnBoss(style.kind)
                AudioEngine.play(Sfx.BOSS_APPEAR)
                AudioEngine.playMusic(MusicTrack.BOSS)
            }
        }

        if (number >= 10) queueAchievement(Achievement.DEEP_RUN)
    }

    private fun checkWaveCompletion(dt: Float) {
        // A run that ended this frame must not be overwritten by a wave clear.
        val currentPlan = plan ?: return
        if (waveEnded || phase != GamePhase.PLAYING || lives <= 0) return

        val cleared = when (currentPlan.style) {
            WaveStyle.Formation -> enemies.isEmpty() && waveEnemiesSpawned > 0
            WaveStyle.Challenge -> challengeSpawned >= challengeTotal && enemies.isEmpty()
            is WaveStyle.BossWave -> boss == null && waveClock > 1f
        }
        if (!cleared) return

        endWaveDelay += dt
        if (endWaveDelay <= 0.75f) return

        waveEnded = true
        finishWave(currentPlan)
    }

    private fun finishWave(finished: WavePlan) {
        // Continuous flow: the run never halts between waves.
        val perfect = !waveWasHit
        if (perfect) {
            runPerfectWaves += 1
            queueAchievement(Achievement.PERFECT_WAVE)
        }

        if (finished.isFormation && waveClock < 25f) {
            queueAchievement(Achievement.FORMATION_BREAKER)
            store.recordFormationClear(waveClock.toDouble())
        }

        val scale = difficulty.scoreScale
        val fullClear = finished.isChallenge && waveEnemiesDestroyed >= challengeTotal
        val clearPoints = (waveEnemiesDestroyed * 60 * scale).toInt()
        val perfectBonus = if (perfect) (2500 * scale).toInt() else 0
        val comboBonus = (bestCombo * 180 * scale).toInt()
        val challengeBonus = if (fullClear) (5000 * scale).toInt() else 0
        val total = clearPoints + perfectBonus + comboBonus + challengeBonus

        addScore(total)
        store.recordHighScore(score)

        val nextPlan = WaveDesigner.plan(wave + 1, difficulty)

        waveSummary = WaveSummary(
            wave = wave,
            enemiesCleared = waveEnemiesDestroyed,
            clearPoints = clearPoints,
            perfect = perfect,
            perfectBonus = perfectBonus,
            comboBonus = comboBonus + challengeBonus,
            total = total,
            isChallenge = finished.isChallenge,
            challengeFullClear = fullClear,
            nextIsBoss = nextPlan.isBoss
        )

        blastCharges = min(3, blastCharges + 1)
        intermissionDelay = 0f
        bullets.removeAll { !it.kind.isPlayer }

        AudioEngine.play(Sfx.WAVE_COMPLETE)
        Haptics.success()
    }

    /** Between-wave breather while the player keeps flying; the next wave simply arrives. */
    private fun tickWaveIntermission(dt: Float) {
        if (!waveEnded || phase != GamePhase.PLAYING || lives <= 0) return
        intermissionDelay += dt
        if (intermissionDelay <= 2.2f) return
        waveSummary = null
        startWave(wave + 1)
    }

    private fun endRun() {
        val isNewHigh = store.isHighScore(score)
        store.recordHighScore(score)
        store.recordRun(score, wave, runKills, runBossKills, runRescues, runPerfectWaves)

        gameOverSummary = GameOverSummary(
            score = score,
            wave = wave,
            kills = runKills,
            bestCombo = bestCombo,
            isNewHighScore = isNewHigh,
            qualifiesForLeaderboard = store.qualifiesForLeaderboard(score)
        )

        AudioEngine.play(if (isNewHigh) Sfx.HIGH_SCORE else Sfx.GAME_OVER)
        dragTarget = null
        firePressed = false
        phase = GamePhase.GAME_OVER
    }

    // Badges & achievements

    private fun refreshBadges() {
        val badges = ArrayList<PowerUpBadge>(5)
        if (player.rapidTimer > 0f) badges.add(PowerUpBadge(PowerUpKind.RAPID_FIRE, ceil(player.rapidTimer).toInt()))
        if (player.multiTimer > 0f) {
            badges.add(
                PowerUpBadge(
                    if (player.multiShot >= 3) PowerUpKind.TRIPLE_SHOT else PowerUpKind.DOUBLE_SHOT,
                    ceil(player.multiTimer).toInt()
                )
            )
        }
        if (player.plasmaTimer > 0f) badges.add(PowerUpBadge(PowerUpKind.PLASMA, ceil(player.plasmaTimer).toInt()))
        if (slowMoTimer > 0f) badges.add(PowerUpBadge(PowerUpKind.SLOW_TIME, ceil(slowMoTimer).toInt()))
        if (player.shielded) badges.add(PowerUpBadge(PowerUpKind.SHIELD, 0))
        if (badges != activeBadges) activeBadges = badges
    }

    fun queueAchievement(achievement: Achievement) {
        if (!store.unlock(achievement)) return
        pendingAchievements.add(achievement)
    }

    private fun presentNextAchievement() {
        if (achievementToast != null || pendingAchievements.isEmpty()) return
        val next = pendingAchievements.removeAt(0)
        toastCounter += 1
        achievementToast = AchievementToast(toastCounter, next)
        toastTimer = 2.8f
        AudioEngine.play(Sfx.HIGH_SCORE)
    }

    // Capture / rescue

    fun capturePlayerShip(enemyId: Int) {
        if (!player.alive || capturedShipEnemyId != null) return

        capturedShipEnemyId = enemyId
        enemies.firstOrNull { it.id == enemyId }?.holdsCapturedShip = true

        AudioEngine.play(Sfx.CAPTURE)
        Haptics.failure()
        captureNoticeTimer = 2.6f
        captureVisible = true
        shake = 18f
        flashAlpha = 0.4f

        player.alive = false
        player.respawnTimer = 1.9f
        player.siphonProgress = 0f
        player.shielded = false
        lives -= 1
        waveWasHit = true
        combo = 1

        emitExplosion(player.position, Vex.magenta, 1.2f, 30)

        if (lives <= 0) endRun()
    }

    private fun rescueCapturedShip(position: Offset) {
        if (capturedShipEnemyId == null) return
        capturedShipEnemyId = null
        runRescues += 1
        queueAchievement(Achievement.VOID_RESCUE)

        AudioEngine.play(Sfx.RESCUE)
        Haptics.success()
        rescueNoticeTimer = 2.6f
        rescueVisible = true
        player.wingmateTimer = 22f

        val bonus = (1500 * difficulty.scoreScale).toInt()
        addScore(bonus)
        addFloatingText("SHIP RECOVERED +$bonus", position, Vex.cyan, 16f, 34f)

        repeat(40) {
            val angle = rnd(0f, PI_F * 2f)
            val speed = rnd(80f, 300f)
            particles.add(
                Particle(position, cos(angle) * speed, sin(angle) * speed, rnd(0.35f, 0.8f), 0.8f, rnd(2f, 5f), Vex.cyan, ParticleKind.SPARK)
            )
        }
    }

    // Shared helpers

    fun makeId(): Int {
        nextId += 1
        return nextId
    }

    fun appendEnemy(enemy: Enemy) {
        enemies.add(enemy)
        waveEnemiesSpawned += 1
    }

    fun registerBossKill() {
        runBossKills += 1
        queueAchievement(Achievement.BOSS_SLAYER)
    }

    fun syncBossHud() {
        val current = boss
        if (current == null) {
            if (bossHud != null) bossHud = null
            return
        }
        val accent = when (current.kind) {
            BossKind.VOID_QUEEN -> Color(0xFFFF2FD4)
            BossKind.HIVE_CORE -> Color(0xFFFFB020)
            BossKind.STAR_DEVOURER -> Color(0xFF6FD9FF)
        }
        val state = BossHudState(current.kind.displayName, current.healthFraction, current.phase, accent)
        if (bossHud != state) bossHud = state
    }
}
