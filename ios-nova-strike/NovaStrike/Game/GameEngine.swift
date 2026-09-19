import SwiftUI
import Observation
import QuartzCore

/// The simulation core. Owns all gameplay state, runs a fixed display-link driven
/// update loop, and publishes render-ready state to SwiftUI via `@Observable`.
@Observable
final class GameEngine {
    // MARK: - Published run state

    var phase: GamePhase = .title
    var score: Int = 0
    var wave: Int = 0
    var lives: Int = 3
    var combo: Int = 1
    var bestCombo: Int = 1
    var blastCharges: Int = 1

    var waveSummary: WaveSummary?
    var gameOverSummary: GameOverSummary?
    var bossHUD: BossHUDState?
    var activeBadges: [PowerUpBadge] = []
    var achievementToast: AchievementToast?

    /// Banner shown at the start of each wave.
    var bannerTitle: String = ""
    var bannerSubtitle: String = ""
    var bannerTimer: Double = 0

    var comboFlashTimer: Double = 0
    var captureNoticeTimer: Double = 0
    var rescueNoticeTimer: Double = 0

    /// Incremented every simulation tick so `Canvas` re-renders.
    var frameToken: Int = 0

    // MARK: - Configuration

    var difficulty: Difficulty = .normal
    var autoFire: Bool = true

    private(set) var store: ProgressStore

    // MARK: - World

    private(set) var size: CGSize = CGSize(width: 390, height: 760)
    var player = PlayerShip()
    var enemies: [Enemy] = []
    var bullets: [Bullet] = []
    var particles: [Particle] = []
    var floatingTexts: [FloatingText] = []
    var drops: [PowerUpDrop] = []
    var boss: Boss?
    private(set) var stars: [Star] = []
    private(set) var debris: [Debris] = []

    /// Ship captured by a warden and currently rendered attached to it.
    private(set) var capturedShipEnemyID: Int?

    var shake: CGFloat = 0
    var flashAlpha: Double = 0
    var slowMoTimer: Double = 0
    var elapsed: Double = 0
    var isBossWave: Bool = false
    var isChallengeWave: Bool = false

    // MARK: - Internals

    private var nextID: Int = 1
    private var displayLink: CADisplayLink?
    private var lastTimestamp: CFTimeInterval = 0
    private var hitStop: Double = 0
    private var comboDecay: Double = 0

    private(set) var plan: WavePlan?
    var diveTimer: Double = 2.4
    var wardenTimer: Double = 6
    var waveClock: Double = 0
    var waveEnemiesDestroyed: Int = 0
    var waveEnemiesSpawned: Int = 0
    private var wavePointsEarned: Int = 0
    var waveWasHit: Bool = false
    private var waveEnded: Bool = false
    private var endWaveDelay: Double = 0
    private var intermissionDelay: Double = 0

    var challengeQueue: [ChallengeSpawn] = []
    var challengeSpawned: Int = 0
    var challengeTotal: Int = 0

    /// Formation grid state shared with the enemy AI extension.
    var formationRowCount: Int = 0
    var formationDrop: CGFloat = 0
    var formationPhase: Double = 0

    private var runKills: Int = 0
    private var runBossKills: Int = 0
    private var runRescues: Int = 0
    private var runPerfectWaves: Int = 0
    private var pendingAchievements: [Achievement] = []
    private var toastTimer: Double = 0

    /// Horizontal drag intent from the controls layer, in points per second.
    var inputVelocity: Double = 0
    var firePressed: Bool = false

    init(store: ProgressStore) {
        self.store = store
        self.difficulty = store.difficulty
        self.autoFire = store.autoFire
    }

    // MARK: - Layout

    func updateSize(_ newSize: CGSize) {
        guard newSize.width > 1, newSize.height > 1 else { return }
        let wasEmpty = stars.isEmpty
        let old = size
        size = newSize
        if wasEmpty {
            seedBackdrop()
        }
        if old.width > 1, abs(old.width - newSize.width) > 0.5 {
            let scale = newSize.width / old.width
            player.position.x *= scale
        }
        player.position.y = playerRestY
        player.position.x = min(max(player.position.x, edgeInset), size.width - edgeInset)
    }

    var playerRestY: CGFloat { size.height - 118 }
    var edgeInset: CGFloat { 26 }

    private func seedBackdrop() {
        stars = (0..<130).map { _ in
            let depth = Double.random(in: 0.25...1)
            return Star(
                position: CGPoint(x: .random(in: 0...size.width), y: .random(in: 0...size.height)),
                depth: depth,
                size: CGFloat(depth) * 2.1 + 0.5,
                twinkle: .random(in: 0...(.pi * 2)),
                color: [Theme.textPrimary, Theme.cyan, Theme.magenta, Theme.violet].randomElement() ?? Theme.textPrimary
            )
        }
        debris = (0..<9).map { _ in
            Debris(
                position: CGPoint(x: .random(in: 0...size.width), y: .random(in: 0...size.height)),
                depth: .random(in: 0.3...0.85),
                size: .random(in: 14...42),
                spin: .random(in: -0.35...0.35),
                rotation: .random(in: 0...(.pi * 2))
            )
        }
    }

    // MARK: - Lifecycle

    func startRun() {
        difficulty = store.difficulty
        autoFire = store.autoFire

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
        elapsed = 0

        enemies.removeAll()
        bullets.removeAll()
        particles.removeAll()
        floatingTexts.removeAll()
        drops.removeAll()
        boss = nil
        bossHUD = nil
        capturedShipEnemyID = nil
        waveSummary = nil
        gameOverSummary = nil
        activeBadges = []

        player = PlayerShip()
        player.position = CGPoint(x: size.width / 2, y: playerRestY)

        phase = .playing
        startWave(1)
        start()
    }

    func returnToTitle() {
        stop()
        phase = .title
        enemies.removeAll()
        bullets.removeAll()
        particles.removeAll()
        drops.removeAll()
        boss = nil
        bossHUD = nil
        AudioEngine.shared.playMusic(.menu)
    }

    func pause() {
        guard phase == .playing else { return }
        phase = .paused
        stop()
    }

    func resume() {
        guard phase == .paused else { return }
        phase = .playing
        start()
    }

    func start() {
        guard displayLink == nil else { return }
        lastTimestamp = 0
        let link = CADisplayLink(target: DisplayLinkProxy(engine: self), selector: #selector(DisplayLinkProxy.tick(_:)))
        link.preferredFrameRateRange = CAFrameRateRange(minimum: 60, maximum: 120, preferred: 120)
        link.add(to: .main, forMode: .common)
        displayLink = link
    }

    func stop() {
        displayLink?.invalidate()
        displayLink = nil
    }

    fileprivate func step(_ timestamp: CFTimeInterval) {
        guard lastTimestamp > 0 else {
            lastTimestamp = timestamp
            return
        }
        var dt = timestamp - lastTimestamp
        lastTimestamp = timestamp
        dt = min(dt, 1.0 / 30.0)
        update(dt)
    }

    // MARK: - Main update

    func update(_ rawDt: Double) {
        guard phase == .playing else { return }

        elapsed += rawDt
        advanceBackdrop(rawDt)
        decayTimers(rawDt)

        // Hit-stop freezes gameplay briefly for impact without stalling effects.
        if hitStop > 0 {
            hitStop -= rawDt
            updateParticles(rawDt)
            frameToken &+= 1
            return
        }

        let enemyScale = slowMoTimer > 0 ? 0.45 : 1.0
        let dt = rawDt

        waveClock += dt
        updatePlayer(dt)
        updateEnemies(dt * enemyScale, realDt: dt)
        updateSiphonBeams(dt)
        updateBoss(dt * enemyScale, realDt: dt)
        updateBullets(dt)
        updateDrops(dt)
        updateParticles(dt)
        updateFloatingTexts(dt)
        resolveCollisions()
        refreshBadges()
        checkWaveCompletion(dt)
        tickWaveIntermission(dt)

        frameToken &+= 1
    }

    private func decayTimers(_ dt: Double) {
        shake = max(0, shake - CGFloat(dt) * 34)
        flashAlpha = max(0, flashAlpha - dt * 3.4)
        slowMoTimer = max(0, slowMoTimer - dt)
        bannerTimer = max(0, bannerTimer - dt)
        comboFlashTimer = max(0, comboFlashTimer - dt)
        captureNoticeTimer = max(0, captureNoticeTimer - dt)
        rescueNoticeTimer = max(0, rescueNoticeTimer - dt)

        if comboDecay > 0 {
            comboDecay -= dt
            if comboDecay <= 0, combo > 1 {
                combo = 1
            }
        }

        if toastTimer > 0 {
            toastTimer -= dt
            if toastTimer <= 0 {
                achievementToast = nil
                presentNextAchievement()
            }
        } else if achievementToast == nil {
            presentNextAchievement()
        }
    }

    private func advanceBackdrop(_ dt: Double) {
        let base: CGFloat = isBossWave ? 26 : 18
        for index in stars.indices {
            stars[index].position.y += CGFloat(stars[index].depth) * base * CGFloat(dt)
            stars[index].twinkle += dt * 2.2
            if stars[index].position.y > size.height {
                stars[index].position.y = -4
                stars[index].position.x = .random(in: 0...size.width)
            }
        }
        for index in debris.indices {
            debris[index].position.y += CGFloat(debris[index].depth) * 12 * CGFloat(dt)
            debris[index].rotation += debris[index].spin * dt
            if debris[index].position.y > size.height + 60 {
                debris[index].position.y = -60
                debris[index].position.x = .random(in: 0...size.width)
            }
        }
    }

    // MARK: - Player

    private func updatePlayer(_ dt: Double) {
        if !player.alive {
            player.respawnTimer -= dt
            if player.respawnTimer <= 0 {
                respawnPlayer()
            }
            return
        }

        player.invulnerable = max(0, player.invulnerable - dt)
        player.rapidTimer = max(0, player.rapidTimer - dt)
        player.plasmaTimer = max(0, player.plasmaTimer - dt)
        player.wingmateTimer = max(0, player.wingmateTimer - dt)

        if player.multiTimer > 0 {
            player.multiTimer -= dt
            if player.multiTimer <= 0 {
                player.multiShot = 1
            }
        }

        // Horizontal movement with quick acceleration and firm damping.
        let target = inputVelocity
        player.velocityX += (target - player.velocityX) * min(1, dt * 22)
        player.position.x += player.velocityX * dt
        player.position.x = min(max(player.position.x, edgeInset), size.width - edgeInset)
        player.position.y = playerRestY

        let desiredTilt = max(-1, min(1, player.velocityX / 620))
        player.tilt += (desiredTilt - player.tilt) * min(1, dt * 12)

        // Siphon lock drains only while the player stays inside an active beam.
        if player.siphonProgress > 0 {
            player.siphonProgress = max(0, player.siphonProgress - dt * 0.6)
        }

        player.fireCooldown -= dt
        let shouldFire = autoFire || firePressed
        if shouldFire, player.fireCooldown <= 0 {
            fireWeapon()
        }
    }

    private func fireWeapon() {
        let rapid = player.rapidTimer > 0
        let interval = rapid ? 0.085 : 0.17
        player.fireCooldown = interval

        let plasma = player.plasmaTimer > 0
        let kind: BulletKind = plasma ? .plasma : .pulse
        let damage = plasma ? 3 : 1
        let speed: Double = plasma ? -900 : -1080
        let origin = CGPoint(x: player.position.x, y: player.position.y - 20)

        switch player.multiShot {
        case 2:
            spawnBullet(at: CGPoint(x: origin.x - 11, y: origin.y), velocity: CGVector(dx: 0, dy: speed), kind: kind, damage: damage, piercing: plasma)
            spawnBullet(at: CGPoint(x: origin.x + 11, y: origin.y), velocity: CGVector(dx: 0, dy: speed), kind: kind, damage: damage, piercing: plasma)
        case 3:
            spawnBullet(at: origin, velocity: CGVector(dx: 0, dy: speed), kind: kind, damage: damage, piercing: plasma)
            spawnBullet(at: CGPoint(x: origin.x - 13, y: origin.y + 5), velocity: CGVector(dx: -235, dy: speed * 0.95), kind: kind, damage: damage, piercing: plasma)
            spawnBullet(at: CGPoint(x: origin.x + 13, y: origin.y + 5), velocity: CGVector(dx: 235, dy: speed * 0.95), kind: kind, damage: damage, piercing: plasma)
        default:
            spawnBullet(at: origin, velocity: CGVector(dx: 0, dy: speed), kind: kind, damage: damage, piercing: plasma)
        }

        // Rescued wing-mates add flanking fire.
        if player.wingmateTimer > 0 {
            spawnBullet(at: CGPoint(x: player.position.x - 34, y: origin.y + 8), velocity: CGVector(dx: 0, dy: -980), kind: .wing, damage: 1)
            spawnBullet(at: CGPoint(x: player.position.x + 34, y: origin.y + 8), velocity: CGVector(dx: 0, dy: -980), kind: .wing, damage: 1)
        }

        AudioEngine.shared.play(.shoot, rateVariation: true)
    }

    private func respawnPlayer() {
        player.alive = true
        player.position = CGPoint(x: size.width / 2, y: playerRestY)
        player.velocityX = 0
        player.invulnerable = 2.2
        player.siphonProgress = 0
        player.multiShot = 1
        player.multiTimer = 0
        player.rapidTimer = 0
        player.plasmaTimer = 0
    }

    func triggerBlast() {
        guard phase == .playing, blastCharges > 0, player.alive else { return }
        blastCharges -= 1
        AudioEngine.shared.play(.blast)
        Haptics.heavyBlast()
        shake = 26
        flashAlpha = 0.65

        let center = player.position
        let radius: CGFloat = 260

        for index in enemies.indices.reversed() {
            let d = hypot(enemies[index].position.x - center.x, enemies[index].position.y - center.y)
            if d < radius {
                let enemy = enemies[index]
                enemies.remove(at: index)
                destroyEnemy(enemy, awardPoints: true, dropChance: 0.06)
            }
        }

        bullets.removeAll { !$0.kind.isPlayer }

        if var current = boss {
            current.health -= 26
            current.hitFlash = 0.3
            boss = current
            syncBossHUD()
        }

        for _ in 0..<70 {
            let angle = Double.random(in: 0...(.pi * 2))
            let speed = Double.random(in: 120...620)
            particles.append(Particle(
                position: center,
                velocity: CGVector(dx: cos(angle) * speed, dy: sin(angle) * speed),
                life: .random(in: 0.4...0.9),
                maxLife: 0.9,
                size: .random(in: 2...6),
                color: [Theme.ember, Theme.amber, Theme.cyan].randomElement() ?? Theme.ember,
                kind: .spark
            ))
        }
        particles.append(Particle(
            position: center,
            velocity: .zero,
            life: 0.5,
            maxLife: 0.5,
            size: radius,
            color: Theme.cyan,
            kind: .ring
        ))
    }

    // MARK: - Bullets

    func spawnBullet(at position: CGPoint, velocity: CGVector, kind: BulletKind, damage: Int = 1, piercing: Bool = false) {
        bullets.append(Bullet(id: makeID(), position: position, velocity: velocity, kind: kind, damage: damage, piercing: piercing))
    }

    private func updateBullets(_ dt: Double) {
        for index in bullets.indices {
            bullets[index].position.x += bullets[index].velocity.dx * dt
            bullets[index].position.y += bullets[index].velocity.dy * dt
            bullets[index].life += dt

            // Bombs arc slightly toward the player's lane.
            if bullets[index].kind == .enemyBomb {
                let dx = player.position.x - bullets[index].position.x
                bullets[index].velocity.dx += max(-40, min(40, dx)) * dt * 1.1
            }
        }
        bullets.removeAll { bullet in
            bullet.position.y < -60 || bullet.position.y > size.height + 60
                || bullet.position.x < -70 || bullet.position.x > size.width + 70
        }
    }

    // MARK: - Drops

    private func updateDrops(_ dt: Double) {
        for index in drops.indices {
            drops[index].position.x += drops[index].velocity.dx * dt
            drops[index].position.y += drops[index].velocity.dy * dt
            drops[index].life += dt
            drops[index].spin += dt * 2.4
            if drops[index].position.x < 18 || drops[index].position.x > size.width - 18 {
                drops[index].velocity.dx *= -1
            }
        }
        drops.removeAll { $0.position.y > size.height + 40 }
    }

    func spawnDrop(at position: CGPoint, kind: PowerUpKind) {
        drops.append(PowerUpDrop(
            id: makeID(),
            position: position,
            velocity: CGVector(dx: .random(in: -34...34), dy: .random(in: 92...132)),
            kind: kind
        ))
    }

    private func collect(_ drop: PowerUpDrop) {
        AudioEngine.shared.play(.powerUp)
        Haptics.success()
        addFloatingText(drop.kind.title, at: drop.position, color: drop.kind.accent, size: 15)

        switch drop.kind {
        case .rapidFire:
            player.rapidTimer = drop.kind.duration
        case .doubleShot:
            player.multiShot = max(player.multiShot, 2)
            player.multiTimer = drop.kind.duration
        case .tripleShot:
            player.multiShot = 3
            player.multiTimer = drop.kind.duration
        case .plasma:
            player.plasmaTimer = drop.kind.duration
        case .shield:
            player.shielded = true
        case .blast:
            blastCharges = min(3, blastCharges + 1)
        case .slowTime:
            slowMoTimer = drop.kind.duration
        }

        for _ in 0..<22 {
            let angle = Double.random(in: 0...(.pi * 2))
            let speed = Double.random(in: 60...220)
            particles.append(Particle(
                position: drop.position,
                velocity: CGVector(dx: cos(angle) * speed, dy: sin(angle) * speed),
                life: .random(in: 0.3...0.6),
                maxLife: 0.6,
                size: .random(in: 1.5...3.5),
                color: drop.kind.accent,
                kind: .spark
            ))
        }
    }

    // MARK: - Effects

    private func updateParticles(_ dt: Double) {
        for index in particles.indices {
            particles[index].life -= dt
            particles[index].position.x += particles[index].velocity.dx * dt
            particles[index].position.y += particles[index].velocity.dy * dt
            let damping = 1 - min(0.95, particles[index].drag * dt)
            particles[index].velocity.dx *= damping
            particles[index].velocity.dy *= damping
        }
        particles.removeAll { $0.life <= 0 }
        if particles.count > 460 {
            particles.removeFirst(particles.count - 460)
        }
    }

    private func updateFloatingTexts(_ dt: Double) {
        for index in floatingTexts.indices {
            floatingTexts[index].life -= dt
            floatingTexts[index].position.y -= floatingTexts[index].rise * CGFloat(dt)
        }
        floatingTexts.removeAll { $0.life <= 0 }
    }

    func addFloatingText(_ text: String, at position: CGPoint, color: Color, size fontSize: CGFloat = 13, rise: CGFloat = 46) {
        floatingTexts.append(FloatingText(
            id: makeID(),
            position: position,
            text: text,
            color: color,
            life: 0.95,
            maxLife: 0.95,
            fontSize: fontSize,
            rise: rise
        ))
    }

    func emitExplosion(at position: CGPoint, color: Color, scale: CGFloat = 1, count: Int = 26) {
        for _ in 0..<count {
            let angle = Double.random(in: 0...(.pi * 2))
            let speed = Double.random(in: 60...340) * Double(scale)
            particles.append(Particle(
                position: position,
                velocity: CGVector(dx: cos(angle) * speed, dy: sin(angle) * speed),
                life: .random(in: 0.28...0.72),
                maxLife: 0.72,
                size: .random(in: 1.6...4.4) * scale,
                color: Bool.random() ? color : Theme.textPrimary,
                kind: .spark
            ))
        }
        particles.append(Particle(
            position: position,
            velocity: .zero,
            life: 0.34,
            maxLife: 0.34,
            size: 46 * scale,
            color: color,
            kind: .ring
        ))
    }

    func applyHitStop(_ duration: Double) {
        hitStop = max(hitStop, duration)
    }

    // MARK: - Collisions

    private func resolveCollisions() {
        var removedBullets = Set<Int>()

        // Player shots against enemies.
        for bulletIndex in bullets.indices {
            let bullet = bullets[bulletIndex]
            guard bullet.kind.isPlayer, !removedBullets.contains(bullet.id) else { continue }

            for enemyIndex in enemies.indices {
                let enemy = enemies[enemyIndex]
                let r = enemy.kind.radius + bullet.radius
                if abs(enemy.position.x - bullet.position.x) < r,
                   abs(enemy.position.y - bullet.position.y) < r {
                    enemies[enemyIndex].health -= bullet.damage
                    enemies[enemyIndex].hitFlash = 0.14
                    if !bullet.piercing { removedBullets.insert(bullet.id) }

                    if enemies[enemyIndex].health <= 0 {
                        let dead = enemies.remove(at: enemyIndex)
                        destroyEnemy(dead, awardPoints: true, dropChance: dropChance(for: dead.kind))
                    } else {
                        AudioEngine.shared.play(.hit, rateVariation: true)
                        emitSparks(at: bullet.position, color: enemy.kind.accent)
                    }
                    break
                }
            }

            if removedBullets.contains(bullet.id) { continue }
            if hitBossIfNeeded(with: bullet) {
                if !bullet.piercing { removedBullets.insert(bullet.id) }
            }
        }

        // Enemy fire against the player.
        if player.alive, player.invulnerable <= 0 {
            for bullet in bullets where !bullet.kind.isPlayer {
                let r = player.radius + bullet.radius
                if abs(player.position.x - bullet.position.x) < r,
                   abs(player.position.y - bullet.position.y) < r {
                    removedBullets.insert(bullet.id)
                    damagePlayer()
                    break
                }
            }
        }

        // Ramming diving enemies.
        if player.alive, player.invulnerable <= 0 {
            for index in enemies.indices where enemies[index].isDiving {
                let r = player.radius + enemies[index].kind.radius * 0.8
                if abs(player.position.x - enemies[index].position.x) < r,
                   abs(player.position.y - enemies[index].position.y) < r {
                    let dead = enemies.remove(at: index)
                    destroyEnemy(dead, awardPoints: false, dropChance: 0)
                    damagePlayer()
                    break
                }
            }
        }

        if !removedBullets.isEmpty {
            bullets.removeAll { removedBullets.contains($0.id) }
        }

        // Power-up pickup.
        if player.alive {
            for index in drops.indices.reversed() {
                let d = hypot(drops[index].position.x - player.position.x, drops[index].position.y - player.position.y)
                if d < 34 {
                    let drop = drops.remove(at: index)
                    collect(drop)
                }
            }
        }

        checkBossContact()
    }

    /// Ramming the boss hull or a live hive pod destroys the player's ship.
    private func checkBossContact() {
        guard player.alive, player.invulnerable <= 0, let current = boss, !current.entering, !current.dying else {
            return
        }

        let halfWidth = current.width * 0.4
        let halfHeight = current.width * 0.26
        if abs(player.position.x - current.position.x) < halfWidth + player.radius,
           abs(player.position.y - current.position.y) < halfHeight + player.radius {
            damagePlayer()
            return
        }

        for pod in current.pods where pod.health > 0 {
            let position = podPosition(boss: current, pod: pod)
            if hypot(player.position.x - position.x, player.position.y - position.y) < current.width * 0.11 + player.radius {
                damagePlayer()
                return
            }
        }
    }

    func emitSparks(at position: CGPoint, color: Color) {
        for _ in 0..<7 {
            let angle = Double.random(in: 0...(.pi * 2))
            let speed = Double.random(in: 40...170)
            particles.append(Particle(
                position: position,
                velocity: CGVector(dx: cos(angle) * speed, dy: sin(angle) * speed),
                life: .random(in: 0.15...0.32),
                maxLife: 0.32,
                size: .random(in: 1.2...2.6),
                color: color,
                kind: .spark
            ))
        }
    }

    private func dropChance(for kind: EnemyKind) -> Double {
        switch kind {
        case .swarmer: 0.06
        case .darter: 0.09
        case .bomber: 0.14
        case .hunter: 0.16
        case .guardian: 0.28
        case .warden: 0.5
        }
    }

    /// Removes an enemy with full feedback, scoring, combo and drop handling.
    func destroyEnemy(_ enemy: Enemy, awardPoints: Bool, dropChance chance: Double) {
        emitExplosion(at: enemy.position, color: enemy.kind.accent)
        AudioEngine.shared.play(.enemyExplode, rateVariation: true)
        Haptics.kill()
        shake = max(shake, 5)

        if enemy.holdsCapturedShip {
            rescueCapturedShip(at: enemy.position)
        }

        waveEnemiesDestroyed += 1
        runKills += 1

        if awardPoints {
            bumpCombo()
            let base = Double(enemy.kind.points) * difficulty.scoreScale
            let gained = Int(base) * combo
            addScore(gained)
            wavePointsEarned += gained
            addFloatingText("\(gained)", at: enemy.position, color: enemy.kind.accent)
        }

        if Double.random(in: 0...1) < chance {
            spawnDrop(at: enemy.position, kind: randomPowerUp())
        }

        if runKills == 1 { queueAchievement(.firstBlood) }
        if store.stats.totalKills + runKills >= 100 { queueAchievement(.alienHunter) }
    }

    private func randomPowerUp() -> PowerUpKind {
        let pool: [PowerUpKind] = [
            .rapidFire, .rapidFire,
            .doubleShot, .doubleShot,
            .tripleShot,
            .plasma,
            .shield, .shield,
            .blast,
            .slowTime
        ]
        return pool.randomElement() ?? .rapidFire
    }

    private func bumpCombo() {
        combo = min(10, combo + 1)
        bestCombo = max(bestCombo, combo)
        comboDecay = 3.4
        if combo >= 2 {
            comboFlashTimer = 0.7
            AudioEngine.shared.play(.combo, rateVariation: true)
        }
    }

    func addScore(_ amount: Int) {
        let before = score
        score += amount
        if before < 100_000, score >= 100_000 {
            queueAchievement(.highRoller)
        }
    }

    func damagePlayer() {
        guard player.alive, player.invulnerable <= 0 else { return }

        waveWasHit = true
        combo = 1

        if player.shielded {
            player.shielded = false
            player.invulnerable = 1.1
            AudioEngine.shared.play(.hit)
            Haptics.hit()
            shake = 12
            addFloatingText("SHIELD DOWN", at: player.position, color: Theme.acid, size: 14)
            particles.append(Particle(position: player.position, velocity: .zero, life: 0.4, maxLife: 0.4, size: 70, color: Theme.acid, kind: .ring))
            return
        }

        killPlayer()
    }

    private func killPlayer() {
        player.alive = false
        player.respawnTimer = 1.7
        player.shielded = false
        player.siphonProgress = 0
        lives -= 1

        AudioEngine.shared.play(.playerExplode)
        Haptics.failure()
        shake = 24
        flashAlpha = 0.5
        applyHitStop(0.09)
        emitExplosion(at: player.position, color: Theme.cyan, scale: 1.6, count: 46)

        if lives <= 0 {
            endRun()
        }
    }

    // MARK: - Wave flow

    func startWave(_ number: Int) {
        wave = number
        let newPlan = WaveDesigner.plan(for: number, difficulty: difficulty)
        plan = newPlan

        waveClock = 0
        waveEnemiesDestroyed = 0
        waveEnemiesSpawned = 0
        wavePointsEarned = 0
        waveWasHit = false
        waveEnded = false
        endWaveDelay = 0
        diveTimer = 3.0
        wardenTimer = 7.0
        challengeSpawned = 0
        challengeQueue = []
        challengeTotal = 0

        isBossWave = newPlan.isBoss
        isChallengeWave = newPlan.isChallenge

        bannerTitle = newPlan.title
        bannerSubtitle = newPlan.subtitle
        bannerTimer = 2.4

        bullets.removeAll { !$0.kind.isPlayer }

        switch newPlan.style {
        case .formation:
            buildFormation(newPlan)
            AudioEngine.shared.playMusic(.battle)
        case .challenge:
            buildChallenge(newPlan)
            AudioEngine.shared.playMusic(.battle)
        case .boss(let kind):
            spawnBoss(kind)
            AudioEngine.shared.play(.bossAppear)
            AudioEngine.shared.playMusic(.boss)
        }

        if number >= 10 { queueAchievement(.deepRun) }
    }

    private func checkWaveCompletion(_ dt: Double) {
        // A run that ended this frame must not be overwritten by a wave clear.
        guard !waveEnded, phase == .playing, lives > 0, let currentPlan = plan else { return }

        let cleared: Bool
        switch currentPlan.style {
        case .formation:
            cleared = enemies.isEmpty && waveEnemiesSpawned > 0
        case .challenge:
            cleared = challengeSpawned >= challengeTotal && enemies.isEmpty
        case .boss:
            cleared = boss == nil && waveClock > 1
        }

        guard cleared else { return }

        endWaveDelay += dt
        guard endWaveDelay > 0.75 else { return }

        waveEnded = true
        finishWave(currentPlan)
    }

    private func finishWave(_ finished: WavePlan) {
        // Continuous flow: the run never halts between waves. Clear the field,
        // bank the bonus, flash a non-blocking summary, and roll straight on.
        let perfect = !waveWasHit
        if perfect {
            runPerfectWaves += 1
            queueAchievement(.perfectWave)
        }

        if case .formation = finished.style, waveClock < 25 {
            queueAchievement(.formationBreaker)
            store.recordFormationClear(seconds: waveClock)
        }

        let fullClear = finished.isChallenge && waveEnemiesDestroyed >= challengeTotal
        let clearPoints = Int(Double(waveEnemiesDestroyed) * 60 * difficulty.scoreScale)
        let perfectBonus = perfect ? Int(2500 * difficulty.scoreScale) : 0
        let comboBonus = Int(Double(bestCombo) * 180 * difficulty.scoreScale)
        let challengeBonus = fullClear ? Int(5000 * difficulty.scoreScale) : 0
        let total = clearPoints + perfectBonus + comboBonus + challengeBonus

        addScore(total)
        store.recordHighScore(score)

        let nextPlan = WaveDesigner.plan(for: wave + 1, difficulty: difficulty)

        waveSummary = WaveSummary(
            wave: wave,
            enemiesCleared: waveEnemiesDestroyed,
            clearPoints: clearPoints,
            perfect: perfect,
            perfectBonus: perfectBonus,
            comboBonus: comboBonus + challengeBonus,
            total: total,
            isChallenge: finished.isChallenge,
            challengeFullClear: fullClear,
            nextIsBoss: nextPlan.isBoss
        )

        blastCharges = min(3, blastCharges + 1)
        intermissionDelay = 0
        bullets.removeAll { !$0.kind.isPlayer }

        AudioEngine.shared.play(.waveComplete)
        Haptics.success()
    }

    /// Between-wave breather while the player keeps flying; the next wave simply arrives.
    private func tickWaveIntermission(_ dt: Double) {
        guard waveEnded, phase == .playing, lives > 0 else { return }
        intermissionDelay += dt
        guard intermissionDelay > 2.2 else { return }
        waveSummary = nil
        startWave(wave + 1)
    }

    private func endRun() {
        let isNewHigh = store.isHighScore(score)
        store.recordHighScore(score)
        store.recordRun(
            score: score,
            wave: wave,
            kills: runKills,
            bossKills: runBossKills,
            rescues: runRescues,
            perfectWaves: runPerfectWaves
        )

        gameOverSummary = GameOverSummary(
            score: score,
            wave: wave,
            kills: runKills,
            bestCombo: bestCombo,
            isNewHighScore: isNewHigh,
            qualifiesForLeaderboard: store.qualifiesForLeaderboard(score)
        )

        AudioEngine.shared.play(isNewHigh ? .highScore : .gameOver)
        phase = .gameOver
        stop()
    }

    // MARK: - Badges & achievements

    private func refreshBadges() {
        var badges: [PowerUpBadge] = []
        if player.rapidTimer > 0 {
            badges.append(PowerUpBadge(kind: .rapidFire, secondsLeft: Int(ceil(player.rapidTimer))))
        }
        if player.multiTimer > 0 {
            badges.append(PowerUpBadge(kind: player.multiShot >= 3 ? .tripleShot : .doubleShot, secondsLeft: Int(ceil(player.multiTimer))))
        }
        if player.plasmaTimer > 0 {
            badges.append(PowerUpBadge(kind: .plasma, secondsLeft: Int(ceil(player.plasmaTimer))))
        }
        if slowMoTimer > 0 {
            badges.append(PowerUpBadge(kind: .slowTime, secondsLeft: Int(ceil(slowMoTimer))))
        }
        if player.shielded {
            badges.append(PowerUpBadge(kind: .shield, secondsLeft: 0))
        }
        if badges != activeBadges {
            activeBadges = badges
        }
    }

    func queueAchievement(_ achievement: Achievement) {
        guard store.unlock(achievement) else { return }
        pendingAchievements.append(achievement)
    }

    private func presentNextAchievement() {
        guard achievementToast == nil, !pendingAchievements.isEmpty else { return }
        let next = pendingAchievements.removeFirst()
        achievementToast = AchievementToast(id: UUID(), achievement: next)
        toastTimer = 2.8
        AudioEngine.shared.play(.highScore)
    }

    // MARK: - Capture / rescue

    func capturePlayerShip(by enemyID: Int) {
        guard player.alive, capturedShipEnemyID == nil else { return }

        capturedShipEnemyID = enemyID
        if let index = enemies.firstIndex(where: { $0.id == enemyID }) {
            enemies[index].holdsCapturedShip = true
        }

        AudioEngine.shared.play(.capture)
        Haptics.failure()
        captureNoticeTimer = 2.6
        shake = 18
        flashAlpha = 0.4

        player.alive = false
        player.respawnTimer = 1.9
        player.siphonProgress = 0
        player.shielded = false
        lives -= 1
        waveWasHit = true
        combo = 1

        emitExplosion(at: player.position, color: Theme.magenta, scale: 1.2, count: 30)

        if lives <= 0 {
            endRun()
        }
    }

    private func rescueCapturedShip(at position: CGPoint) {
        guard capturedShipEnemyID != nil else { return }
        capturedShipEnemyID = nil
        runRescues += 1
        queueAchievement(.voidRescue)

        AudioEngine.shared.play(.rescue)
        Haptics.success()
        rescueNoticeTimer = 2.6
        player.wingmateTimer = 22

        let bonus = Int(1500 * difficulty.scoreScale)
        addScore(bonus)
        addFloatingText("SHIP RECOVERED +\(bonus)", at: position, color: Theme.cyan, size: 16, rise: 34)

        for _ in 0..<40 {
            let angle = Double.random(in: 0...(.pi * 2))
            let speed = Double.random(in: 80...300)
            particles.append(Particle(
                position: position,
                velocity: CGVector(dx: cos(angle) * speed, dy: sin(angle) * speed),
                life: .random(in: 0.35...0.8),
                maxLife: 0.8,
                size: .random(in: 2...5),
                color: Theme.cyan,
                kind: .spark
            ))
        }
    }

    // MARK: - Shared helpers used by extensions

    func makeID() -> Int {
        nextID += 1
        return nextID
    }

    func appendEnemy(_ enemy: Enemy) {
        enemies.append(enemy)
        waveEnemiesSpawned += 1
    }

    func registerBossKill() {
        runBossKills += 1
        queueAchievement(.bossSlayer)
    }

    func syncBossHUD() {
        guard let current = boss else {
            if bossHUD != nil { bossHUD = nil }
            return
        }
        let state = BossHUDState(
            name: current.kind.displayName,
            fraction: current.healthFraction,
            phase: current.phase,
            accentHex: current.kind == .voidQueen ? 0xFF2FD4 : (current.kind == .hiveCore ? 0xFFB020 : 0x6FD9FF)
        )
        if bossHUD != state {
            bossHUD = state
        }
    }
}

/// Keeps `CADisplayLink` from retaining the engine directly.
private final class DisplayLinkProxy {
    weak var engine: GameEngine?

    init(engine: GameEngine) {
        self.engine = engine
    }

    @objc func tick(_ link: CADisplayLink) {
        engine?.step(link.timestamp)
    }
}
