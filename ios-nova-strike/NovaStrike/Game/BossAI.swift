import SwiftUI

extension GameEngine {

    // MARK: - Spawning

    func spawnBoss(_ kind: BossKind) {
        enemies.removeAll()
        formationRowCount = 0

        let width = size.width * kind.widthFraction
        let health = Int(Double(kind.baseHealth) * difficulty.bossHealthScale * (1 + Double(wave / 15) * 0.35))

        var newBoss = Boss(
            kind: kind,
            position: CGPoint(x: size.width / 2, y: -width * 0.5),
            health: health,
            maxHealth: health,
            width: width
        )

        switch kind {
        case .voidQueen:
            newBoss.cores = [
                BossCore(offset: CGPoint(x: 0, y: width * 0.06), radius: width * 0.1),
                BossCore(offset: CGPoint(x: -width * 0.28, y: -width * 0.02), radius: width * 0.062),
                BossCore(offset: CGPoint(x: width * 0.28, y: -width * 0.02), radius: width * 0.062)
            ]
        case .hiveCore:
            newBoss.cores = [BossCore(offset: .zero, radius: width * 0.13)]
            newBoss.pods = (0..<4).map { index in
                HivePod(
                    id: makeID(),
                    angle: Double(index) * (.pi / 2) + .pi / 4,
                    radius: width * 0.36,
                    health: 24,
                    maxHealth: 24,
                    fireTimer: Double.random(in: 1.5...3.5)
                )
            }
        case .starDevourer:
            newBoss.cores = [
                BossCore(offset: CGPoint(x: 0, y: width * 0.04), radius: width * 0.11),
                BossCore(offset: CGPoint(x: -width * 0.33, y: width * 0.1), radius: width * 0.055),
                BossCore(offset: CGPoint(x: width * 0.33, y: width * 0.1), radius: width * 0.055)
            ]
        }

        boss = newBoss
        syncBossHUD()
        shake = 16
    }

    // MARK: - Update

    func updateBoss(_ dt: Double, realDt: Double) {
        guard var current = boss else { return }

        current.hitFlash = max(0, current.hitFlash - realDt)

        if current.dying {
            current.deathTimer += realDt
            spawnDeathDebris(for: current)
            if current.deathTimer > 2.0 {
                completeBossDefeat(current)
                return
            }
            boss = current
            return
        }

        if current.entering {
            current.entryProgress += dt * 0.42
            let eased = 1 - pow(1 - min(1, current.entryProgress), 3)
            let restY = current.width * 0.42 + 40
            current.position.y = -current.width * 0.5 + (restY + current.width * 0.5) * CGFloat(eased)
            if current.entryProgress >= 1 {
                current.entering = false
                current.position.y = restY
            }
            boss = current
            syncBossHUD()
            return
        }

        // Lateral drift widens as the boss takes damage.
        current.driftPhase += dt * (0.5 + Double(current.phase) * 0.22)
        let amplitude = size.width * (0.16 + Double(current.phase) * 0.05)
        current.position.x = size.width / 2 + CGFloat(sin(current.driftPhase)) * amplitude
        current.position.x = min(max(current.position.x, current.width * 0.3), size.width - current.width * 0.3)

        updateBeam(&current, dt: dt, realDt: realDt)
        updatePods(&current, dt: dt, realDt: realDt)

        current.attackTimer -= dt * difficulty.fireScale
        if current.attackTimer <= 0, player.alive {
            performAttack(&current)
        }

        current.spawnTimer -= dt
        if current.spawnTimer <= 0 {
            spawnEscort(for: current)
            current.spawnTimer = escortInterval(for: current)
        }

        boss = current
        syncBossHUD()
    }

    private func escortInterval(for boss: Boss) -> Double {
        let base: Double = switch boss.kind {
        case .voidQueen: 6.0
        case .hiveCore: 8.0
        case .starDevourer: 9.0
        }
        return max(3.0, base - Double(boss.phase) * 1.4) / difficulty.diveScale
    }

    // MARK: - Attacks

    private func performAttack(_ boss: inout Boss) {
        switch boss.kind {
        case .voidQueen:
            voidQueenAttack(&boss)
        case .hiveCore:
            hiveCoreAttack(&boss)
        case .starDevourer:
            starDevourerAttack(&boss)
        }
        boss.volleyIndex += 1
    }

    private func voidQueenAttack(_ boss: inout Boss) {
        let origin = CGPoint(x: boss.position.x, y: boss.position.y + boss.width * 0.22)

        switch boss.volleyIndex % 3 {
        case 0:
            // Radial fan that widens with each phase.
            let count = 7 + boss.phase * 3
            let spread = 1.1 + Double(boss.phase) * 0.22
            for index in 0..<count {
                let t = Double(index) / Double(max(1, count - 1))
                let angle = -spread / 2 + spread * t
                spawnBullet(
                    at: origin,
                    velocity: CGVector(dx: sin(angle) * 275, dy: cos(angle) * 275),
                    kind: .enemyBolt
                )
            }
            boss.attackTimer = 2.4 - Double(boss.phase) * 0.35

        case 1:
            // Aimed triple at the player's lane.
            let dx = player.position.x - origin.x
            let dy = max(140, player.position.y - origin.y)
            let length = max(1, hypot(dx, dy))
            for offset in [-26.0, 0.0, 26.0] {
                spawnBullet(
                    at: CGPoint(x: origin.x + offset, y: origin.y),
                    velocity: CGVector(dx: Double(dx / length) * 360, dy: Double(dy / length) * 360),
                    kind: .enemyShard
                )
            }
            boss.attackTimer = 1.6 - Double(boss.phase) * 0.2

        default:
            // Spiral spray in the final phase only.
            if boss.phase >= 1 {
                let arms = 4
                for arm in 0..<arms {
                    let angle = Double(boss.volleyIndex) * 0.5 + Double(arm) * (.pi * 2 / Double(arms))
                    spawnBullet(
                        at: origin,
                        velocity: CGVector(dx: sin(angle) * 240, dy: abs(cos(angle)) * 240 + 90),
                        kind: .enemyBolt
                    )
                }
            }
            boss.attackTimer = 1.3
        }
    }

    private func hiveCoreAttack(_ boss: inout Boss) {
        let origin = CGPoint(x: boss.position.x, y: boss.position.y + boss.width * 0.2)
        let livePods = boss.pods.filter { $0.health > 0 }.count

        if livePods == 0 {
            // Stripped of pods, the core goes berserk with dense ring bursts.
            let count = 14
            for index in 0..<count {
                let angle = Double(index) / Double(count) * .pi * 2
                spawnBullet(
                    at: origin,
                    velocity: CGVector(dx: sin(angle) * 250, dy: cos(angle) * 250),
                    kind: .enemyShard
                )
            }
            boss.attackTimer = 1.9
        } else {
            for offset in [-0.5, -0.17, 0.17, 0.5] {
                spawnBullet(
                    at: origin,
                    velocity: CGVector(dx: sin(offset) * 300, dy: cos(offset) * 300),
                    kind: .enemyBolt
                )
            }
            boss.attackTimer = 2.2 - Double(boss.phase) * 0.3
        }
    }

    private func starDevourerAttack(_ boss: inout Boss) {
        // The devourer alternates between charging its sweeping beam and bomb volleys.
        if boss.beam == nil, boss.volleyIndex % 2 == 0 {
            let sweep = boss.phase >= 1 ? 1.15 : 0.8
            boss.beam = BossBeam(
                angle: player.position.x < boss.position.x ? -sweep / 2 : sweep / 2,
                charge: 1.1,
                active: 0,
                width: 30 + CGFloat(boss.phase) * 10,
                sweep: player.position.x < boss.position.x ? sweep : -sweep
            )
            boss.attackTimer = 4.2
            AudioEngine.shared.play(.bossAppear)
        } else {
            let origin = CGPoint(x: boss.position.x, y: boss.position.y + boss.width * 0.2)
            let count = 5 + boss.phase * 2
            for index in 0..<count {
                let t = Double(index) / Double(max(1, count - 1))
                let x = origin.x + CGFloat(t - 0.5) * boss.width * 0.72
                spawnBullet(
                    at: CGPoint(x: x, y: origin.y),
                    velocity: CGVector(dx: .random(in: -25...25), dy: 230),
                    kind: .enemyBomb
                )
            }
            boss.attackTimer = 2.5 - Double(boss.phase) * 0.35
        }
    }

    // MARK: - Sweeping beam

    private func updateBeam(_ boss: inout Boss, dt: Double, realDt: Double) {
        guard var beam = boss.beam else { return }

        if beam.charge > 0 {
            beam.charge -= dt
            if beam.charge <= 0 {
                beam.active = 2.6
                shake = max(shake, 10)
            }
            boss.beam = beam
            return
        }

        beam.active -= dt
        if beam.active <= 0 {
            boss.beam = nil
            return
        }

        // Sweep the beam across the playfield.
        beam.angle += beam.sweep * dt * 0.42

        let origin = CGPoint(x: boss.position.x, y: boss.position.y + boss.width * 0.18)

        // Collision: distance from the player to the beam's infinite ray.
        if player.alive, player.invulnerable <= 0 {
            let dirX = sin(beam.angle)
            let dirY = cos(beam.angle)
            let toPlayerX = Double(player.position.x - origin.x)
            let toPlayerY = Double(player.position.y - origin.y)
            let projection = toPlayerX * dirX + toPlayerY * dirY
            if projection > 0 {
                let closestX = toPlayerX - dirX * projection
                let closestY = toPlayerY - dirY * projection
                let distance = hypot(closestX, closestY)
                if distance < Double(beam.width * 0.5 + player.radius * 0.6) {
                    damagePlayer()
                }
            }
        }

        // Beam edge embers.
        if Int(elapsed * 60) % 2 == 0 {
            let distance = Double.random(in: 60...Double(size.height))
            let position = CGPoint(
                x: origin.x + CGFloat(sin(beam.angle) * distance),
                y: origin.y + CGFloat(cos(beam.angle) * distance)
            )
            particles.append(Particle(
                position: position,
                velocity: CGVector(dx: .random(in: -50...50), dy: .random(in: -50...50)),
                life: 0.35,
                maxLife: 0.35,
                size: .random(in: 2...5),
                color: boss.kind.accent,
                kind: .spark
            ))
        }

        boss.beam = beam
    }

    // MARK: - Hive pods

    private func updatePods(_ boss: inout Boss, dt: Double, realDt: Double) {
        guard boss.kind == .hiveCore, !boss.pods.isEmpty else { return }

        let spin = dt * (0.55 + Double(boss.phase) * 0.3)
        for index in boss.pods.indices {
            boss.pods[index].hitFlash = max(0, boss.pods[index].hitFlash - realDt)
            guard boss.pods[index].health > 0 else { continue }

            boss.pods[index].angle += spin
            // Pods drift outward as the fight escalates.
            if boss.phase >= 1, !boss.pods[index].detached {
                boss.pods[index].detached = true
                boss.pods[index].radius = boss.width * 0.48
            }

            boss.pods[index].fireTimer -= dt * difficulty.fireScale
            if boss.pods[index].fireTimer <= 0, player.alive {
                let origin = podPosition(boss: boss, pod: boss.pods[index])
                let dx = player.position.x - origin.x
                let dy = max(90, player.position.y - origin.y)
                let length = max(1, hypot(dx, dy))
                spawnBullet(
                    at: origin,
                    velocity: CGVector(dx: Double(dx / length) * 320, dy: Double(dy / length) * 320),
                    kind: .enemyBolt
                )
                boss.pods[index].fireTimer = Double.random(in: 2.2...4.0)
            }
        }
    }

    func podPosition(boss: Boss, pod: HivePod) -> CGPoint {
        CGPoint(
            x: boss.position.x + CGFloat(cos(pod.angle)) * pod.radius,
            y: boss.position.y + CGFloat(sin(pod.angle)) * pod.radius * 0.62
        )
    }

    // MARK: - Escorts

    private func spawnEscort(for boss: Boss) {
        let kinds: [EnemyKind] = switch boss.kind {
        case .voidQueen: [.swarmer, .swarmer, .darter]
        case .hiveCore: [.darter, .bomber]
        case .starDevourer: [.hunter, .darter]
        }

        let count = 2 + boss.phase
        for index in 0..<count {
            guard let kind = kinds.randomElement() else { continue }
            let side: CGFloat = index % 2 == 0 ? -1 : 1
            let origin = CGPoint(
                x: boss.position.x + side * boss.width * 0.38,
                y: boss.position.y + boss.width * 0.1
            )

            var enemy = Enemy(
                id: makeID(),
                kind: kind,
                slot: 0,
                position: origin,
                health: kind.health,
                maxHealth: kind.health,
                state: .diving,
                entry: EntryPath(p0: origin, p1: origin, p2: origin, p3: origin)
            )
            enemy.diveStart = origin
            enemy.diveTarget = CGPoint(x: player.position.x, y: player.position.y)
            enemy.pattern = [.swoop, .zigzag, .plunge, .spiral].randomElement() ?? .swoop
            enemy.side = side
            enemy.t = -Double(index) * 0.18
            enemy.fireTimer = Double.random(in: 1.2...3.0)
            appendEnemy(enemy)
        }

        emitSparks(at: boss.position, color: boss.kind.accent)
    }

    // MARK: - Damage

    /// Returns true when the bullet connected with the boss or one of its pods.
    func hitBossIfNeeded(with bullet: Bullet) -> Bool {
        guard var current = boss, !current.entering, !current.dying else { return false }

        // Pods shield the hive core until they are destroyed.
        if current.kind == .hiveCore {
            for index in current.pods.indices where current.pods[index].health > 0 {
                let position = podPosition(boss: current, pod: current.pods[index])
                let radius = current.width * 0.11
                if hypot(bullet.position.x - position.x, bullet.position.y - position.y) < radius + bullet.radius {
                    current.pods[index].health -= bullet.damage
                    current.pods[index].hitFlash = 0.16
                    AudioEngine.shared.play(.hit, rateVariation: true)
                    emitSparks(at: bullet.position, color: current.kind.accent)

                    if current.pods[index].health <= 0 {
                        emitExplosion(at: position, color: current.kind.accent, scale: 1.3, count: 34)
                        AudioEngine.shared.play(.enemyExplode)
                        shake = max(shake, 12)
                        let bonus = Int(600 * difficulty.scoreScale)
                        addScore(bonus)
                        addFloatingText("POD DOWN +\(bonus)", at: position, color: Theme.amber, size: 14)
                    }
                    boss = current
                    syncBossHUD()
                    return true
                }
            }
        }

        // Weak-point cores take extra damage; the rest of the hull resists.
        var connected = false
        var damage = bullet.damage

        for index in current.cores.indices {
            let position = CGPoint(
                x: current.position.x + current.cores[index].offset.x,
                y: current.position.y + current.cores[index].offset.y
            )
            if hypot(bullet.position.x - position.x, bullet.position.y - position.y) < current.cores[index].radius + bullet.radius {
                connected = true
                damage = bullet.damage * 3
                current.cores[index].flash = 0.2
                break
            }
        }

        if !connected {
            let halfWidth = current.width * 0.42
            let halfHeight = current.width * 0.28
            if abs(bullet.position.x - current.position.x) < halfWidth,
               abs(bullet.position.y - current.position.y) < halfHeight {
                connected = true
            }
        }

        guard connected else { return false }

        current.health -= damage
        current.hitFlash = 0.14
        AudioEngine.shared.play(.hit, rateVariation: true)
        emitSparks(at: bullet.position, color: current.kind.accent)

        if current.health <= 0 {
            current.dying = true
            current.deathTimer = 0
            current.beam = nil
            AudioEngine.shared.play(.playerExplode)
            Haptics.heavyBlast()
            shake = 30
            flashAlpha = 0.5
            applyHitStop(0.12)
        }

        boss = current
        syncBossHUD()
        return true
    }

    private func spawnDeathDebris(for boss: Boss) {
        guard Int(elapsed * 60) % 3 == 0 else { return }
        let position = CGPoint(
            x: boss.position.x + .random(in: -boss.width * 0.4...boss.width * 0.4),
            y: boss.position.y + .random(in: -boss.width * 0.22...boss.width * 0.22)
        )
        emitExplosion(at: position, color: boss.kind.accent, scale: 1.1, count: 16)
        shake = max(shake, 8)
    }

    private func completeBossDefeat(_ defeated: Boss) {
        boss = nil
        bossHUD = nil

        emitExplosion(at: defeated.position, color: defeated.kind.accent, scale: 3.4, count: 110)
        particles.append(Particle(
            position: defeated.position,
            velocity: .zero,
            life: 0.8,
            maxLife: 0.8,
            size: defeated.width * 1.4,
            color: defeated.kind.accent,
            kind: .ring
        ))
        shake = 34
        flashAlpha = 0.7

        bullets.removeAll { !$0.kind.isPlayer }
        registerBossKill()

        let points = Int(Double(defeated.kind.points) * difficulty.scoreScale)
        addScore(points)
        addFloatingText("\(defeated.kind.displayName) DOWN +\(points)", at: defeated.position, color: defeated.kind.accent, size: 17, rise: 28)

        // Guaranteed reward for the kill.
        spawnDrop(at: CGPoint(x: defeated.position.x - 40, y: defeated.position.y), kind: .shield)
        spawnDrop(at: CGPoint(x: defeated.position.x + 40, y: defeated.position.y), kind: .blast)
    }
}
