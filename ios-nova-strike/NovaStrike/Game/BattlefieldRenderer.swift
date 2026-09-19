import SwiftUI

/// Immediate-mode renderer for the whole playfield. Everything is drawn into a single
/// `Canvas` so hundreds of sprites, bolts and particles cost one view update per frame.
struct BattlefieldRenderer: View {
    let engine: GameEngine
    let size: CGSize

    var body: some View {
        // Reading frameToken here is what ties the canvas to the simulation clock.
        let token = engine.frameToken

        Canvas(opaque: false, rendersAsynchronously: false) { context, canvasSize in
            _ = token
            draw(context: &context, size: canvasSize)
        } symbols: {
            spriteSymbols
        }
        .allowsHitTesting(false)
    }

    /// Sprite atlas resolved once per frame by the canvas.
    @ViewBuilder
    private var spriteSymbols: some View {
        Image("starfighter_top_view").resizable().scaledToFit().frame(width: 220, height: 220).tag("player")
        ForEach(EnemyKind.allCases) { kind in
            Image(kind.sprite).resizable().scaledToFit().frame(width: 180, height: 180).tag(kind.rawValue)
        }
        ForEach(BossKind.allCases) { kind in
            Image(kind.sprite).resizable().scaledToFit().frame(width: 520, height: 520).tag("boss_\(kind.rawValue)")
        }
    }

    private func draw(context: inout GraphicsContext, size canvasSize: CGSize) {
        let shakeOffset = currentShakeOffset()
        context.translateBy(x: shakeOffset.x, y: shakeOffset.y)

        drawBackdrop(&context, size: canvasSize)
        drawSiphonBeams(&context)
        drawBossBeam(&context)
        drawBoss(&context)
        drawEnemies(&context)
        drawDrops(&context)
        drawBullets(&context)
        drawPlayer(&context)
        drawParticles(&context)
        drawFloatingTexts(&context)
    }

    private func currentShakeOffset() -> CGPoint {
        guard engine.shake > 0.2 else { return .zero }
        let magnitude = min(engine.shake, 30)
        return CGPoint(
            x: .random(in: -magnitude...magnitude) * 0.5,
            y: .random(in: -magnitude...magnitude) * 0.5
        )
    }

    // MARK: - Backdrop

    private func drawBackdrop(_ context: inout GraphicsContext, size canvasSize: CGSize) {
        for debris in engine.debris {
            let rect = CGRect(
                x: debris.position.x - debris.size / 2,
                y: debris.position.y - debris.size / 2,
                width: debris.size,
                height: debris.size * 0.78
            )
            var shape = context
            shape.translateBy(x: rect.midX, y: rect.midY)
            shape.rotate(by: .radians(debris.rotation))
            shape.translateBy(x: -rect.midX, y: -rect.midY)
            shape.fill(
                Path(roundedRect: rect, cornerRadius: debris.size * 0.3),
                with: .color(Color(hex: 0x1A0F33, opacity: 0.55 * debris.depth))
            )
        }

        for star in engine.stars {
            let pulse = 0.55 + 0.45 * sin(star.twinkle)
            let alpha = star.depth * pulse
            let rect = CGRect(
                x: star.position.x - star.size / 2,
                y: star.position.y - star.size / 2,
                width: star.size,
                height: star.size
            )
            context.fill(Path(ellipseIn: rect), with: .color(star.color.opacity(alpha)))
        }
    }

    // MARK: - Player

    private func drawPlayer(_ context: inout GraphicsContext) {
        let player = engine.player

        // Rescued wing-mates fly escort.
        if player.wingmateTimer > 0, player.alive {
            for side in [-1.0, 1.0] {
                let position = CGPoint(x: player.position.x + CGFloat(side) * 40, y: player.position.y + 16)
                drawShipSprite(&context, at: position, scale: 0.6, tilt: player.tilt * 0.6, opacity: 0.9)
                drawEngineFlare(&context, at: CGPoint(x: position.x, y: position.y + 16), scale: 0.5)
            }
        }

        guard player.alive else { return }

        // Blink while respawn-invulnerable.
        if player.invulnerable > 0, Int(engine.elapsed * 14) % 2 == 0 {
            return
        }

        drawEngineFlare(&context, at: CGPoint(x: player.position.x, y: player.position.y + 24), scale: 1)
        drawShipSprite(&context, at: player.position, scale: 1, tilt: player.tilt, opacity: 1)

        if player.shielded {
            let radius: CGFloat = 32
            let rect = CGRect(
                x: player.position.x - radius,
                y: player.position.y - radius,
                width: radius * 2,
                height: radius * 2
            )
            let pulse = 0.55 + 0.25 * sin(engine.elapsed * 6)
            context.stroke(Path(ellipseIn: rect), with: .color(Theme.acid.opacity(pulse)), lineWidth: 2)
            context.fill(Path(ellipseIn: rect), with: .color(Theme.acid.opacity(0.1)))
        }

        // Siphon lock indicator.
        if player.siphonProgress > 0.02 {
            let radius: CGFloat = 38
            let rect = CGRect(
                x: player.position.x - radius,
                y: player.position.y - radius,
                width: radius * 2,
                height: radius * 2
            )
            var arc = Path()
            arc.addArc(
                center: player.position,
                radius: radius,
                startAngle: .degrees(-90),
                endAngle: .degrees(-90 + 360 * player.siphonProgress),
                clockwise: false
            )
            _ = rect
            context.stroke(arc, with: .color(Theme.magenta), style: StrokeStyle(lineWidth: 3, lineCap: .round))
        }
    }

    private func drawShipSprite(_ context: inout GraphicsContext, at position: CGPoint, scale: CGFloat, tilt: Double, opacity: Double) {
        guard let symbol = context.resolveSymbol(id: "player") else { return }
        let width: CGFloat = 54 * scale
        let height: CGFloat = 54 * scale

        var layer = context
        layer.opacity = opacity
        layer.translateBy(x: position.x, y: position.y)
        layer.rotate(by: .radians(tilt * 0.34))
        layer.addFilter(.shadow(color: Theme.cyan.opacity(0.7), radius: 9))
        layer.draw(symbol, in: CGRect(x: -width / 2, y: -height / 2, width: width, height: height))
    }

    private func drawEngineFlare(_ context: inout GraphicsContext, at position: CGPoint, scale: CGFloat) {
        let flicker = 0.7 + 0.3 * sin(engine.elapsed * 34)
        let length = 26 * scale * flicker
        let width = 11 * scale

        for offset in [-7.5 * scale, 7.5 * scale] {
            let rect = CGRect(
                x: position.x + offset - width / 2,
                y: position.y - 4,
                width: width,
                height: length
            )
            context.fill(
                Path(ellipseIn: rect),
                with: .linearGradient(
                    Gradient(colors: [Theme.textPrimary.opacity(0.95), Theme.cyan.opacity(0.75), Theme.cyan.opacity(0)]),
                    startPoint: CGPoint(x: rect.midX, y: rect.minY),
                    endPoint: CGPoint(x: rect.midX, y: rect.maxY)
                )
            )
        }
    }

    // MARK: - Enemies

    private func drawEnemies(_ context: inout GraphicsContext) {
        for enemy in engine.enemies {
            guard enemy.position.y > -90, enemy.position.y < engine.size.height + 90 else { continue }

            // Materialize-in: eased fade with a scale-up and a brief accent shimmer.
            let fade = min(1, enemy.spawnFade)
            let eased = 1 - (1 - fade) * (1 - fade)
            let size = enemy.kind.radius * 2.35 * CGFloat(0.72 + 0.28 * eased)
            guard let symbol = context.resolveSymbol(id: enemy.kind.rawValue) else { continue }

            if fade < 1 {
                let shimmerRadius = size * (1.15 + (1 - eased) * 0.9)
                let shimmerRect = CGRect(
                    x: enemy.position.x - shimmerRadius / 2,
                    y: enemy.position.y - shimmerRadius / 2,
                    width: shimmerRadius,
                    height: shimmerRadius
                )
                context.fill(
                    Path(ellipseIn: shimmerRect),
                    with: .color(enemy.kind.accent.opacity((1 - eased) * 0.3))
                )
            }

            var layer = context
            layer.opacity = eased
            layer.translateBy(x: enemy.position.x, y: enemy.position.y)
            layer.rotate(by: .radians(enemy.rotation * 0.55))

            let glow = enemy.hitFlash > 0 ? Theme.textPrimary : enemy.kind.accent
            layer.addFilter(.shadow(color: glow.opacity(enemy.hitFlash > 0 ? 1 : 0.6), radius: enemy.hitFlash > 0 ? 14 : 7))
            layer.draw(symbol, in: CGRect(x: -size / 2, y: -size / 2, width: size, height: size))

            // Captured ship rides under its captor.
            if enemy.holdsCapturedShip, let playerSymbol = context.resolveSymbol(id: "player") {
                var captured = context
                captured.opacity = 0.9
                captured.translateBy(x: enemy.position.x, y: enemy.position.y + enemy.kind.radius * 1.9)
                captured.rotate(by: .radians(.pi))
                captured.addFilter(.shadow(color: Theme.cyan.opacity(0.8), radius: 10))
                captured.draw(playerSymbol, in: CGRect(x: -20, y: -20, width: 40, height: 40))

                var tether = Path()
                tether.move(to: CGPoint(x: enemy.position.x, y: enemy.position.y))
                tether.addLine(to: CGPoint(x: enemy.position.x, y: enemy.position.y + enemy.kind.radius * 1.9))
                context.stroke(
                    tether,
                    with: .color(Theme.magenta.opacity(0.6)),
                    style: StrokeStyle(lineWidth: 2, dash: [4, 4])
                )
            }

            // Armour pips for multi-hit enemies still holding damage.
            if enemy.maxHealth > 2, enemy.health < enemy.maxHealth {
                let barWidth = enemy.kind.radius * 1.8
                let fraction = CGFloat(enemy.health) / CGFloat(enemy.maxHealth)
                let origin = CGPoint(x: enemy.position.x - barWidth / 2, y: enemy.position.y - enemy.kind.radius - 8)
                context.fill(
                    Path(roundedRect: CGRect(x: origin.x, y: origin.y, width: barWidth, height: 3), cornerRadius: 1.5),
                    with: .color(Color.black.opacity(0.5))
                )
                context.fill(
                    Path(roundedRect: CGRect(x: origin.x, y: origin.y, width: barWidth * fraction, height: 3), cornerRadius: 1.5),
                    with: .color(enemy.kind.accent)
                )
            }
        }
    }

    private func drawSiphonBeams(_ context: inout GraphicsContext) {
        for enemy in engine.enemies where enemy.state == .siphonBeam {
            let halfWidth: CGFloat = 34
            let top = enemy.position.y + enemy.kind.radius
            let bottom = engine.size.height
            guard bottom > top else { continue }

            let pulse = 0.55 + 0.25 * sin(engine.elapsed * 11)
            let rect = CGRect(x: enemy.position.x - halfWidth, y: top, width: halfWidth * 2, height: bottom - top)

            context.fill(
                Path(rect),
                with: .linearGradient(
                    Gradient(colors: [
                        Theme.magenta.opacity(0.55 * pulse),
                        Theme.magenta.opacity(0.22 * pulse),
                        Theme.magenta.opacity(0.05)
                    ]),
                    startPoint: CGPoint(x: rect.midX, y: rect.minY),
                    endPoint: CGPoint(x: rect.midX, y: rect.maxY)
                )
            )

            // Prism edges.
            for edge in [rect.minX, rect.maxX] {
                var line = Path()
                line.move(to: CGPoint(x: edge, y: rect.minY))
                line.addLine(to: CGPoint(x: edge, y: rect.maxY))
                context.stroke(line, with: .color(Theme.magenta.opacity(0.8 * pulse)), lineWidth: 1.5)
            }

            // Travelling scan bands.
            let bands = 5
            for index in 0..<bands {
                let phase = (engine.elapsed * 0.9 + Double(index) / Double(bands)).truncatingRemainder(dividingBy: 1)
                let y = rect.minY + rect.height * CGFloat(phase)
                let band = CGRect(x: rect.minX, y: y, width: rect.width, height: 3)
                context.fill(Path(band), with: .color(Theme.textPrimary.opacity(0.35 * (1 - phase))))
            }
        }
    }

    // MARK: - Boss

    private func drawBoss(_ context: inout GraphicsContext) {
        guard let boss = engine.boss else { return }
        guard let symbol = context.resolveSymbol(id: "boss_\(boss.kind.rawValue)") else { return }

        let width = boss.width
        let height = boss.width

        var layer = context
        if boss.dying {
            // Violent flicker during the death throes.
            layer.opacity = Int(engine.elapsed * 22) % 2 == 0 ? 1 : 0.35
        }
        layer.translateBy(x: boss.position.x, y: boss.position.y)
        let sway = sin(boss.driftPhase * 0.8) * 0.045
        layer.rotate(by: .radians(sway))

        let glowColor = boss.hitFlash > 0 ? Theme.textPrimary : boss.kind.accent
        layer.addFilter(.shadow(color: glowColor.opacity(boss.hitFlash > 0 ? 0.95 : 0.6), radius: boss.hitFlash > 0 ? 26 : 18))
        layer.draw(symbol, in: CGRect(x: -width / 2, y: -height / 2, width: width, height: height))

        drawBossCores(&context, boss: boss)
        drawHivePods(&context, boss: boss)
    }

    private func drawBossCores(_ context: inout GraphicsContext, boss: Boss) {
        for core in boss.cores {
            let center = CGPoint(x: boss.position.x + core.offset.x, y: boss.position.y + core.offset.y)
            let pulse = 0.6 + 0.4 * sin(engine.elapsed * 4 + Double(core.radius))
            let radius = core.radius * (core.flash > 0 ? 1.25 : 1)
            let rect = CGRect(x: center.x - radius, y: center.y - radius, width: radius * 2, height: radius * 2)

            context.fill(
                Path(ellipseIn: rect),
                with: .radialGradient(
                    Gradient(colors: [
                        Theme.textPrimary.opacity(0.95 * pulse),
                        boss.kind.accent.opacity(0.7 * pulse),
                        boss.kind.accent.opacity(0)
                    ]),
                    center: center,
                    startRadius: 0,
                    endRadius: radius
                )
            )
            context.stroke(Path(ellipseIn: rect), with: .color(boss.kind.accent.opacity(0.9)), lineWidth: 1.5)
        }
    }

    private func drawHivePods(_ context: inout GraphicsContext, boss: Boss) {
        guard boss.kind == .hiveCore else { return }

        for pod in boss.pods {
            let center = engine.podPosition(boss: boss, pod: pod)
            let radius = boss.width * 0.11

            if pod.health <= 0 {
                // Burnt-out mount left behind.
                let rect = CGRect(x: center.x - radius * 0.5, y: center.y - radius * 0.5, width: radius, height: radius)
                context.stroke(Path(ellipseIn: rect), with: .color(Color(hex: 0x3A2A1A, opacity: 0.6)), lineWidth: 1.5)
                continue
            }

            var tether = Path()
            tether.move(to: boss.position)
            tether.addLine(to: center)
            context.stroke(tether, with: .color(boss.kind.accent.opacity(0.3)), lineWidth: 1.5)

            let rect = CGRect(x: center.x - radius, y: center.y - radius, width: radius * 2, height: radius * 2)
            let fill = pod.hitFlash > 0 ? Theme.textPrimary : Color(hex: 0x2A1E12)
            context.fill(Path(ellipseIn: rect), with: .color(fill))
            context.stroke(Path(ellipseIn: rect), with: .color(boss.kind.accent.opacity(0.9)), lineWidth: 2)

            let coreRect = rect.insetBy(dx: radius * 0.55, dy: radius * 0.55)
            context.fill(Path(ellipseIn: coreRect), with: .color(boss.kind.accent))

            let fraction = CGFloat(pod.health) / CGFloat(pod.maxHealth)
            var arc = Path()
            arc.addArc(
                center: center,
                radius: radius + 4,
                startAngle: .degrees(-90),
                endAngle: .degrees(-90 + 360 * Double(fraction)),
                clockwise: false
            )
            context.stroke(arc, with: .color(boss.kind.accent.opacity(0.8)), style: StrokeStyle(lineWidth: 2, lineCap: .round))
        }
    }

    private func drawBossBeam(_ context: inout GraphicsContext) {
        guard let boss = engine.boss, let beam = boss.beam else { return }

        let origin = CGPoint(x: boss.position.x, y: boss.position.y + boss.width * 0.18)
        let length = engine.size.height * 1.6
        let end = CGPoint(
            x: origin.x + CGFloat(sin(beam.angle)) * length,
            y: origin.y + CGFloat(cos(beam.angle)) * length
        )

        if beam.charge > 0 {
            // Telegraph line so the sweep is always dodgeable.
            let progress = 1 - beam.charge / 1.1
            var warning = Path()
            warning.move(to: origin)
            warning.addLine(to: end)
            context.stroke(
                warning,
                with: .color(boss.kind.accent.opacity(0.25 + 0.4 * progress)),
                style: StrokeStyle(lineWidth: 2 + 3 * progress, dash: [10, 8])
            )

            let chargeRadius = boss.width * 0.1 * CGFloat(0.4 + progress)
            let rect = CGRect(
                x: origin.x - chargeRadius,
                y: origin.y - chargeRadius,
                width: chargeRadius * 2,
                height: chargeRadius * 2
            )
            context.fill(
                Path(ellipseIn: rect),
                with: .radialGradient(
                    Gradient(colors: [Theme.textPrimary, boss.kind.accent, boss.kind.accent.opacity(0)]),
                    center: origin,
                    startRadius: 0,
                    endRadius: chargeRadius
                )
            )
            return
        }

        guard beam.active > 0 else { return }

        let flicker = 0.85 + 0.15 * sin(engine.elapsed * 40)
        let halfWidth = beam.width * 0.5 * CGFloat(flicker)
        let normalX = CGFloat(cos(beam.angle)) * halfWidth
        let normalY = -CGFloat(sin(beam.angle)) * halfWidth

        var body = Path()
        body.move(to: CGPoint(x: origin.x + normalX, y: origin.y + normalY))
        body.addLine(to: CGPoint(x: end.x + normalX, y: end.y + normalY))
        body.addLine(to: CGPoint(x: end.x - normalX, y: end.y - normalY))
        body.addLine(to: CGPoint(x: origin.x - normalX, y: origin.y - normalY))
        body.closeSubpath()

        context.fill(
            body,
            with: .linearGradient(
                Gradient(colors: [
                    boss.kind.accent.opacity(0.85),
                    boss.kind.accent.opacity(0.45),
                    boss.kind.accent.opacity(0.12)
                ]),
                startPoint: origin,
                endPoint: end
            )
        )

        var core = Path()
        core.move(to: origin)
        core.addLine(to: end)
        context.stroke(core, with: .color(Theme.textPrimary.opacity(0.95)), style: StrokeStyle(lineWidth: halfWidth * 0.5, lineCap: .round))
    }

    // MARK: - Bullets

    private func drawBullets(_ context: inout GraphicsContext) {
        for bullet in engine.bullets {
            let color = bullet.kind.color
            let length = bullet.kind.length
            let width = bullet.kind.width

            switch bullet.kind {
            case .enemyBomb:
                let radius = width
                let rect = CGRect(
                    x: bullet.position.x - radius,
                    y: bullet.position.y - radius,
                    width: radius * 2,
                    height: radius * 2
                )
                let pulse = 0.7 + 0.3 * sin(engine.elapsed * 14)
                context.fill(
                    Path(ellipseIn: rect),
                    with: .radialGradient(
                        Gradient(colors: [Theme.textPrimary, color, color.opacity(0.1)]),
                        center: bullet.position,
                        startRadius: 0,
                        endRadius: radius * CGFloat(pulse + 0.4)
                    )
                )
                context.stroke(Path(ellipseIn: rect), with: .color(color.opacity(0.9)), lineWidth: 1.5)

            default:
                // Orient the bolt along its velocity so angled shots read correctly.
                let angle = atan2(bullet.velocity.dy, bullet.velocity.dx)
                var layer = context
                layer.translateBy(x: bullet.position.x, y: bullet.position.y)
                layer.rotate(by: .radians(angle - .pi / 2))
                layer.addFilter(.shadow(color: color.opacity(0.9), radius: 6))

                let rect = CGRect(x: -width / 2, y: -length / 2, width: width, height: length)
                layer.fill(
                    Path(roundedRect: rect, cornerRadius: width / 2),
                    with: .linearGradient(
                        Gradient(colors: [Theme.textPrimary, color, color.opacity(0.35)]),
                        startPoint: CGPoint(x: 0, y: -length / 2),
                        endPoint: CGPoint(x: 0, y: length / 2)
                    )
                )
            }
        }
    }

    // MARK: - Drops

    private func drawDrops(_ context: inout GraphicsContext) {
        for drop in engine.drops {
            let radius: CGFloat = 15
            let bob = CGFloat(sin(engine.elapsed * 5 + drop.spin)) * 2
            let center = CGPoint(x: drop.position.x, y: drop.position.y + bob)
            let pulse = 0.6 + 0.4 * sin(engine.elapsed * 6 + drop.spin)

            let halo = CGRect(
                x: center.x - radius * 1.8,
                y: center.y - radius * 1.8,
                width: radius * 3.6,
                height: radius * 3.6
            )
            context.fill(
                Path(ellipseIn: halo),
                with: .radialGradient(
                    Gradient(colors: [drop.kind.accent.opacity(0.35 * pulse), drop.kind.accent.opacity(0)]),
                    center: center,
                    startRadius: 0,
                    endRadius: radius * 1.8
                )
            )

            let rect = CGRect(x: center.x - radius, y: center.y - radius, width: radius * 2, height: radius * 2)
            context.fill(
                Path(roundedRect: rect, cornerRadius: 5),
                with: .color(Theme.voidDeep.opacity(0.88))
            )
            context.stroke(
                Path(roundedRect: rect, cornerRadius: 5),
                with: .color(drop.kind.accent),
                lineWidth: 2
            )

            let glyph = Text(Image(systemName: drop.kind.symbol))
                .font(.system(size: 13, weight: .black))
                .foregroundStyle(drop.kind.accent)
            context.draw(glyph, at: center)
        }
    }

    // MARK: - Particles

    private func drawParticles(_ context: inout GraphicsContext) {
        for particle in engine.particles {
            let progress = max(0, particle.life / particle.maxLife)

            switch particle.kind {
            case .ring:
                let radius = particle.size * CGFloat(1 - progress) + 6
                let rect = CGRect(
                    x: particle.position.x - radius,
                    y: particle.position.y - radius,
                    width: radius * 2,
                    height: radius * 2
                )
                context.stroke(
                    Path(ellipseIn: rect),
                    with: .color(particle.color.opacity(progress * 0.8)),
                    lineWidth: 2.5 * CGFloat(progress) + 0.5
                )

            case .spark, .ember, .shard:
                let size = particle.size * CGFloat(0.4 + progress * 0.6)
                let rect = CGRect(
                    x: particle.position.x - size / 2,
                    y: particle.position.y - size / 2,
                    width: size,
                    height: size
                )
                context.fill(Path(ellipseIn: rect), with: .color(particle.color.opacity(progress)))
            }
        }
    }

    private func drawFloatingTexts(_ context: inout GraphicsContext) {
        for item in engine.floatingTexts {
            let progress = max(0, item.life / item.maxLife)
            let text = Text(item.text)
                .font(.system(size: item.fontSize, weight: .black, design: .rounded))
                .foregroundStyle(item.color)
            var resolved = context.resolve(text)
            resolved.shading = .color(item.color.opacity(progress))
            context.draw(resolved, at: item.position, anchor: .center)
        }
    }
}
