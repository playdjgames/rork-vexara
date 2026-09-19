import SwiftUI

/// One queued challenge-wave spawn.
struct ChallengeSpawn {
    var time: Double
    var kind: EnemyKind
    var path: ChallengePath
    var side: CGFloat
}

extension GameEngine {

    // MARK: - Formation geometry

    var formationColumns: Int { WaveDesigner.columns }
    var formationTop: CGFloat { max(96, size.height * 0.14) }
    var formationRowSpacing: CGFloat { 52 }

    var formationColumnSpacing: CGFloat {
        let usable = size.width - 56
        return min(48, usable / CGFloat(formationColumns))
    }

    /// Horizontal sway applied to the whole grid, Galaga-style but with an eased drift.
    var formationSway: CGFloat {
        let amplitude = min(34, size.width * 0.075)
        return CGFloat(sin(formationPhase)) * amplitude
    }

    func slotPosition(_ slot: Int) -> CGPoint {
        let row = slot / formationColumns
        let col = slot % formationColumns
        let mid = Double(formationColumns - 1) / 2
        let x = size.width / 2 + CGFloat(Double(col) - mid) * formationColumnSpacing + formationSway
        let y = formationTop + CGFloat(row) * formationRowSpacing + formationDrop
        return CGPoint(x: x, y: y)
    }

    // MARK: - Wave construction

    func buildFormation(_ plan: WavePlan) {
        enemies.removeAll()
        formationRowCount = plan.rows.count
        formationDrop = 0
        formationPhase = 0

        let styles = EntryPath.Style.allCases
        var built: [Enemy] = []
        var order = 0

        for (rowIndex, row) in plan.rows.enumerated() {
            let count = min(row.count, formationColumns)
            // Centre each row inside the grid.
            let startCol = (formationColumns - count) / 2

            for column in 0..<count {
                let slot = rowIndex * formationColumns + (startCol + column)
                let target = slotPosition(slot)
                let style = styles[(rowIndex + column) % styles.count]
                let entry = EntryPath.make(style: style, target: target, size: size)

                var enemy = Enemy(
                    id: makeID(),
                    kind: row.kind,
                    slot: slot,
                    position: entry.p0,
                    health: row.kind.health,
                    maxHealth: row.kind.health,
                    state: .entering,
                    entry: entry
                )
                // Stagger arrivals so squadrons stream in rather than pop into place.
                enemy.t = -Double(order) * 0.075
                enemy.pathSpeed = 0.62
                enemy.side = column % 2 == 0 ? 1 : -1
                enemy.fireTimer = Double.random(in: 2.5...7.5)
                built.append(enemy)
                order += 1
            }
        }

        enemies = built
        waveEnemiesSpawned = built.count
    }

    func buildChallenge(_ plan: WavePlan) {
        enemies.removeAll()
        formationRowCount = 0
        var queue: [ChallengeSpawn] = []

        for run in plan.challengeRuns {
            for index in 0..<run.count {
                queue.append(ChallengeSpawn(
                    time: run.delay + Double(index) * run.spacing,
                    kind: run.kind,
                    path: run.path,
                    side: run.side
                ))
            }
        }

        queue.sort { $0.time < $1.time }
        challengeQueue = queue
        challengeTotal = queue.count
        challengeSpawned = 0
    }

    // MARK: - Enemy update

    func updateEnemies(_ dt: Double, realDt: Double) {
        guard let plan else { return }

        formationPhase += dt * 0.68
        if plan.style == .formation {
            // The grid creeps downward as the wave drags on, but never into the player.
            let maxDrop = max(0, size.height * 0.2)
            formationDrop = min(maxDrop, formationDrop + CGFloat(dt) * 1.6)
        }

        if plan.isChallenge {
            spawnDueChallengeEnemies()
        }

        let speedRamp = WaveDesigner.speedRamp(for: wave) * difficulty.speedScale

        for index in enemies.indices {
            enemies[index].hitFlash = max(0, enemies[index].hitFlash - realDt)
            enemies[index].spawnFade = min(1, enemies[index].spawnFade + realDt * 3.2)
            advanceEnemy(&enemies[index], dt: dt, speedRamp: speedRamp, plan: plan)
        }

        // Clean up anything that flew off the bottom during a challenge pass.
        enemies.removeAll { enemy in
            enemy.challenge != nil && enemy.t >= 1
        }

        if !plan.isChallenge {
            scheduleDives(dt)
            scheduleSiphon(dt)
        }

        updateEnemyFire(realDt, plan: plan)
    }

    private func advanceEnemy(_ enemy: inout Enemy, dt: Double, speedRamp: Double, plan: WavePlan) {
        switch enemy.state {
        case .entering:
            enemy.t += dt * enemy.pathSpeed * speedRamp
            if enemy.t >= 1 {
                enemy.t = 1
                enemy.state = .formation
                enemy.position = slotPosition(enemy.slot)
                enemy.rotation = 0
            } else if enemy.t > 0 {
                let previous = enemy.position
                enemy.position = enemy.entry.point(enemy.t)
                enemy.rotation = heading(from: previous, to: enemy.position)
            }

        case .formation:
            enemy.position = slotPosition(enemy.slot)
            enemy.rotation += (0 - enemy.rotation) * min(1, dt * 8)

        case .streaming:
            guard let path = enemy.challenge else {
                enemy.state = .formation
                return
            }
            enemy.t += dt / path.duration * speedRamp
            let previous = enemy.position
            enemy.position = path.position(t: min(1, enemy.t), size: size, side: enemy.side)
            enemy.rotation = heading(from: previous, to: enemy.position)

        case .diving:
            enemy.t += dt / enemy.pattern.duration * speedRamp
            let previous = enemy.position
            enemy.position = enemy.pattern.position(
                t: min(1, enemy.t),
                start: enemy.diveStart,
                target: enemy.diveTarget,
                size: size,
                side: enemy.side
            )
            enemy.rotation = heading(from: previous, to: enemy.position)

            if enemy.t >= 1 {
                // Loop around the screen edge and fly back to the grid.
                enemy.returnFrom = CGPoint(
                    x: min(max(enemy.position.x, 30), size.width - 30),
                    y: enemy.pattern.exitsUpward ? -70 : -70
                )
                enemy.position = enemy.returnFrom
                enemy.state = .returning
                enemy.t = 0
            }

        case .returning:
            enemy.t += dt * 0.85 * speedRamp
            let target = slotPosition(enemy.slot)
            let progress = min(1, enemy.t)
            let eased = 1 - pow(1 - progress, 3)
            let previous = enemy.position
            enemy.position = CGPoint(
                x: enemy.returnFrom.x + (target.x - enemy.returnFrom.x) * CGFloat(eased),
                y: enemy.returnFrom.y + (target.y - enemy.returnFrom.y) * CGFloat(eased)
            )
            enemy.rotation = heading(from: previous, to: enemy.position)
            if progress >= 1 {
                enemy.state = .formation
                enemy.t = 1
            }

        case .siphonDescend:
            enemy.t += dt * 0.8
            let target = CGPoint(x: enemy.diveTarget.x, y: size.height * 0.44)
            let eased = min(1, enemy.t)
            enemy.position = CGPoint(
                x: enemy.diveStart.x + (target.x - enemy.diveStart.x) * CGFloat(eased),
                y: enemy.diveStart.y + (target.y - enemy.diveStart.y) * CGFloat(eased)
            )
            if eased >= 1 {
                enemy.state = .siphonBeam
                enemy.t = 0
                AudioEngine.shared.play(.capture)
            }

        case .siphonBeam:
            enemy.t += dt
            // Warden tracks the player slowly while the beam is live.
            let drift = (player.position.x - enemy.position.x) * CGFloat(min(1, dt * 0.9))
            enemy.position.x += drift
            enemy.position.x = min(max(enemy.position.x, 40), size.width - 40)
            if enemy.t > 4.2 {
                enemy.state = .siphonRetreat
                enemy.returnFrom = enemy.position
                enemy.t = 0
            }

        case .siphonRetreat:
            enemy.t += dt * 0.7 * speedRamp
            let target = slotPosition(enemy.slot)
            let eased = 1 - pow(1 - min(1, enemy.t), 3)
            enemy.position = CGPoint(
                x: enemy.returnFrom.x + (target.x - enemy.returnFrom.x) * CGFloat(eased),
                y: enemy.returnFrom.y + (target.y - enemy.returnFrom.y) * CGFloat(eased)
            )
            if enemy.t >= 1 {
                enemy.state = .formation
                enemy.t = 1
            }
        }
    }

    private func heading(from previous: CGPoint, to current: CGPoint) -> Double {
        let dx = current.x - previous.x
        let dy = current.y - previous.y
        guard abs(dx) > 0.001 || abs(dy) > 0.001 else { return 0 }
        // Sprites face down by default, so measure the deviation from straight down.
        return atan2(dx, dy) * -1
    }

    // MARK: - Challenge streaming

    private func spawnDueChallengeEnemies() {
        while challengeSpawned < challengeQueue.count,
              challengeQueue[challengeSpawned].time <= waveClock {
            let spawn = challengeQueue[challengeSpawned]
            challengeSpawned += 1

            var enemy = Enemy(
                id: makeID(),
                kind: spawn.kind,
                slot: 0,
                position: spawn.path.position(t: 0, size: size, side: spawn.side),
                health: spawn.kind.health,
                maxHealth: spawn.kind.health,
                state: .streaming,
                entry: EntryPath(p0: .zero, p1: .zero, p2: .zero, p3: .zero)
            )
            enemy.challenge = spawn.path
            enemy.side = spawn.side
            enemy.t = 0
            enemies.append(enemy)
        }
    }

    // MARK: - Dive scheduling

    private func scheduleDives(_ dt: Double) {
        guard player.alive else { return }

        diveTimer -= dt * WaveDesigner.aggression(for: wave) * difficulty.diveScale
        guard diveTimer <= 0 else { return }

        let settled = enemies.indices.filter { enemies[$0].state == .formation && enemies[$0].kind != .warden }
        guard !settled.isEmpty else {
            diveTimer = 1.2
            return
        }

        // Later waves send small squadrons instead of lone attackers.
        let groupSize = min(settled.count, wave >= 7 ? Int.random(in: 1...3) : (wave >= 3 ? Int.random(in: 1...2) : 1))
        let chosen = settled.shuffled().prefix(groupSize)
        let pattern = pickPattern()

        for (offset, index) in chosen.enumerated() {
            var enemy = enemies[index]
            enemy.state = .diving
            enemy.t = -Double(offset) * 0.12
            enemy.diveStart = enemy.position
            enemy.diveTarget = CGPoint(x: player.position.x, y: player.position.y)
            enemy.pattern = enemy.kind == .darter ? .plunge : pattern
            enemy.side = enemy.position.x < size.width / 2 ? 1 : -1
            enemies[index] = enemy
        }

        diveTimer = Double.random(in: 1.5...3.4)
    }

    private func pickPattern() -> DivePattern {
        var pool: [DivePattern] = [.swoop, .zigzag, .plunge]
        if wave >= 3 { pool.append(contentsOf: [.split, .boomerang]) }
        if wave >= 6 { pool.append(contentsOf: [.spiral, .orbit]) }
        return pool.randomElement() ?? .swoop
    }

    // MARK: - Siphon warden

    private func scheduleSiphon(_ dt: Double) {
        guard player.alive, capturedShipEnemyID == nil else { return }

        wardenTimer -= dt
        guard wardenTimer <= 0 else { return }

        guard let index = enemies.firstIndex(where: { $0.kind == .warden && $0.state == .formation }) else {
            wardenTimer = 4
            return
        }

        enemies[index].state = .siphonDescend
        enemies[index].t = 0
        enemies[index].diveStart = enemies[index].position
        enemies[index].diveTarget = CGPoint(x: player.position.x, y: size.height * 0.44)
        wardenTimer = Double.random(in: 13...19)
    }

    /// Advances siphon capture progress for any warden whose beam covers the ship.
    func updateSiphonBeams(_ dt: Double) {
        guard player.alive, player.invulnerable <= 0, capturedShipEnemyID == nil else { return }

        for enemy in enemies where enemy.state == .siphonBeam {
            let halfWidth: CGFloat = 34
            guard abs(player.position.x - enemy.position.x) < halfWidth,
                  player.position.y > enemy.position.y else { continue }

            player.siphonProgress = min(1, player.siphonProgress + dt * 0.62)
            if player.siphonProgress >= 1 {
                capturePlayerShip(by: enemy.id)
            }
            return
        }
    }

    // MARK: - Enemy fire

    private func updateEnemyFire(_ dt: Double, plan: WavePlan) {
        // Challenge waves are strictly a shooting gallery.
        guard !plan.isChallenge, player.alive else { return }

        let aggression = WaveDesigner.aggression(for: wave) * difficulty.fireScale

        for index in enemies.indices {
            let enemy = enemies[index]
            guard enemy.state != .entering, enemy.state != .siphonBeam else { continue }

            enemies[index].fireTimer -= dt * aggression
            guard enemies[index].fireTimer <= 0 else { continue }

            fire(from: enemy)
            enemies[index].fireTimer = reloadInterval(for: enemy)
        }
    }

    private func reloadInterval(for enemy: Enemy) -> Double {
        switch enemy.kind {
        case .swarmer: Double.random(in: 4.5...9.5)
        case .darter: Double.random(in: 3.0...6.5)
        case .bomber: Double.random(in: 2.6...5.0)
        case .guardian: Double.random(in: 3.4...6.0)
        case .hunter: Double.random(in: 3.0...5.5)
        case .warden: Double.random(in: 4.0...7.0)
        }
    }

    private func fire(from enemy: Enemy) {
        let origin = CGPoint(x: enemy.position.x, y: enemy.position.y + enemy.kind.radius)

        switch enemy.kind {
        case .bomber:
            spawnBullet(at: origin, velocity: CGVector(dx: .random(in: -30...30), dy: 210), kind: .enemyBomb)

        case .hunter:
            // Leads the player's current drift instead of aiming where they are.
            let lead = player.position.x + player.velocityX * 0.42
            let dx = lead - origin.x
            let dy = max(120, player.position.y - origin.y)
            let length = max(1, hypot(dx, dy))
            let speed: Double = 330
            spawnBullet(
                at: origin,
                velocity: CGVector(dx: Double(dx / length) * speed, dy: Double(dy / length) * speed),
                kind: .enemyShard
            )

        case .guardian:
            for offset in [-0.22, 0.0, 0.22] {
                spawnBullet(
                    at: origin,
                    velocity: CGVector(dx: sin(offset) * 300, dy: cos(offset) * 300),
                    kind: .enemyBolt
                )
            }

        case .warden:
            for offset in [-0.34, 0.34] {
                spawnBullet(
                    at: origin,
                    velocity: CGVector(dx: sin(offset) * 290, dy: cos(offset) * 290),
                    kind: .enemyShard
                )
            }

        default:
            let dx = player.position.x - origin.x
            let dy = max(100, player.position.y - origin.y)
            let length = max(1, hypot(dx, dy))
            let speed: Double = enemy.isDiving ? 400 : 300
            spawnBullet(
                at: origin,
                velocity: CGVector(dx: Double(dx / length) * speed * 0.55, dy: Double(dy / length) * speed),
                kind: .enemyBolt
            )
        }
    }
}
