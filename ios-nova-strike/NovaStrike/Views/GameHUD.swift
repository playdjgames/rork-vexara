import SwiftUI

/// Arcade HUD drawn over the battlefield: score row, boss bar, combo callout,
/// power-up badges and the bottom status pills.
struct GameHUD: View {
    let engine: GameEngine
    var onPause: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            topRow
            if let boss = engine.bossHUD {
                bossBar(boss)
                    .padding(.top, 10)
                    .transition(.move(edge: .top).combined(with: .opacity))
            }
            Spacer(minLength: 0)
            centerCallouts
            Spacer(minLength: 0)
            badgeRow
            bottomRow
        }
        .padding(.horizontal, 14)
        .animation(.spring(response: 0.36, dampingFraction: 0.8), value: engine.bossHUD)
        .animation(.spring(response: 0.3, dampingFraction: 0.75), value: engine.activeBadges)
    }

    // MARK: - Top

    private var topRow: some View {
        HStack(alignment: .top, spacing: 8) {
            HUDPill(accent: Theme.cyan) {
                VStack(alignment: .leading, spacing: 1) {
                    Text("SCORE")
                        .font(Theme.label(9))
                        .tracking(1.6)
                        .foregroundStyle(Theme.cyan.opacity(0.85))
                    Text(arcadeScore(engine.score))
                        .font(Theme.numeral(18))
                        .foregroundStyle(Theme.textPrimary)
                        .contentTransition(.numericText())
                        .neonGlow(Theme.cyan, radius: 6, intensity: 0.5)
                }
            }
            .accessibilityLabel("Score \(engine.score)")

            Spacer(minLength: 0)

            if engine.bossHUD == nil {
                HUDPill(accent: Theme.magenta) {
                    Text("WAVE \(engine.wave)")
                        .font(Theme.label(13))
                        .tracking(1.4)
                        .foregroundStyle(Theme.textPrimary)
                        .neonGlow(Theme.magenta, radius: 6, intensity: 0.5)
                }
                .accessibilityLabel("Wave \(engine.wave)")
            }

            Spacer(minLength: 0)

            VStack(alignment: .trailing, spacing: 8) {
                HUDPill(accent: Theme.violet) {
                    VStack(alignment: .trailing, spacing: 1) {
                        Text("HIGH SCORE")
                            .font(Theme.label(9))
                            .tracking(1.4)
                            .foregroundStyle(Theme.violet.opacity(0.9))
                        Text(arcadeScore(max(engine.store.highScore, engine.score)))
                            .font(Theme.numeral(15))
                            .foregroundStyle(Theme.textPrimary)
                    }
                }
                .accessibilityLabel("High score \(max(engine.store.highScore, engine.score))")

                CircleIconButton(systemName: "pause.fill", accent: Theme.textPrimary, action: onPause)
                    .accessibilityLabel("Pause")
            }
        }
    }

    private func bossBar(_ boss: BossHUDState) -> some View {
        VStack(spacing: 5) {
            Text(boss.name)
                .font(Theme.label(15))
                .tracking(3)
                .foregroundStyle(Theme.textPrimary)
                .neonGlow(boss.accent, radius: 10)

            GeometryReader { proxy in
                ZStack(alignment: .leading) {
                    Capsule()
                        .fill(Theme.voidDeep.opacity(0.8))
                        .overlay(Capsule().strokeBorder(boss.accent.opacity(0.6), lineWidth: 1))

                    Capsule()
                        .fill(
                            LinearGradient(
                                colors: [Theme.danger, boss.accent],
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        )
                        .frame(width: max(0, proxy.size.width * boss.fraction))
                        .shadow(color: boss.accent.opacity(0.8), radius: 8)

                    // Phase threshold ticks at two thirds and one third.
                    ForEach([0.33, 0.66], id: \.self) { mark in
                        Rectangle()
                            .fill(Theme.voidDeep.opacity(0.75))
                            .frame(width: 2)
                            .offset(x: proxy.size.width * mark)
                    }
                }
            }
            .frame(height: 14)

            Text("\(Int(boss.fraction * 100))%")
                .font(Theme.numeral(11))
                .foregroundStyle(boss.accent)
                .frame(maxWidth: .infinity, alignment: .trailing)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(boss.name), \(Int(boss.fraction * 100)) percent health")
    }

    // MARK: - Center callouts

    private var centerCallouts: some View {
        VStack(spacing: 14) {
            if let summary = engine.waveSummary {
                VStack(spacing: 4) {
                    Text(summary.isChallenge ? "CHALLENGE CLEAR" : "WAVE \(summary.wave) CLEAR")
                        .font(Theme.display(26))
                        .tracking(4)
                        .foregroundStyle(Theme.textPrimary)
                        .neonGlow(Theme.cyan, radius: 14)
                    Text("+\(summary.total) BONUS")
                        .font(Theme.numeral(16))
                        .foregroundStyle(Theme.cyan)
                        .neonGlow(Theme.cyan, radius: 8, intensity: 0.6)
                    if summary.perfect || summary.challengeFullClear {
                        Text(summary.challengeFullClear ? "FULL CLEAR" : "PERFECT WAVE")
                            .font(Theme.label(10))
                            .tracking(2.2)
                            .foregroundStyle(Theme.amber)
                    }
                    if summary.nextIsBoss {
                        Text("BOSS INCOMING")
                            .font(Theme.label(10))
                            .tracking(2.4)
                            .foregroundStyle(Theme.danger)
                    }
                }
                .multilineTextAlignment(.center)
                .allowsHitTesting(false)
                .transition(.scale(scale: 0.85).combined(with: .opacity))
            }

            if engine.bannerTimer > 0 {
                VStack(spacing: 4) {
                    Text(engine.bannerTitle)
                        .font(Theme.display(engine.isBossWave ? 34 : 30))
                        .tracking(4)
                        .foregroundStyle(Theme.textPrimary)
                        .neonGlow(engine.isBossWave ? Theme.danger : Theme.cyan, radius: 16)
                    Text(engine.bannerSubtitle)
                        .font(Theme.label(12))
                        .tracking(2.4)
                        .foregroundStyle(Theme.textSecondary)
                }
                .multilineTextAlignment(.center)
                .transition(.scale(scale: 0.8).combined(with: .opacity))
            }

            if engine.combo >= 2, engine.comboFlashTimer > 0 {
                Text("COMBO x\(engine.combo)")
                    .font(Theme.display(30))
                    .tracking(3)
                    .foregroundStyle(
                        LinearGradient(
                            colors: [Theme.textPrimary, Theme.cyan],
                            startPoint: .top,
                            endPoint: .bottom
                        )
                    )
                    .neonGlow(Theme.cyan, radius: 14)
                    .scaleEffect(1 + min(0.25, engine.comboFlashTimer * 0.3))
                    .transition(.scale.combined(with: .opacity))
            }

            if engine.captureNoticeTimer > 0 {
                calloutBanner("SHIP CAPTURED", detail: "DESTROY THE WARDEN TO RECOVER IT", accent: Theme.magenta)
            }

            if engine.rescueNoticeTimer > 0 {
                calloutBanner("SHIP RECOVERED", detail: "WING-MATES ONLINE", accent: Theme.cyan)
            }
        }
        .animation(.spring(response: 0.34, dampingFraction: 0.7), value: engine.bannerTimer > 0)
        .animation(.spring(response: 0.34, dampingFraction: 0.7), value: engine.waveSummary == nil)
        .animation(.spring(response: 0.3, dampingFraction: 0.6), value: engine.comboFlashTimer > 0)
    }

    private func calloutBanner(_ title: String, detail: String, accent: Color) -> some View {
        VStack(spacing: 3) {
            Text(title)
                .font(Theme.display(24))
                .tracking(3)
                .foregroundStyle(Theme.textPrimary)
                .neonGlow(accent, radius: 12)
            Text(detail)
                .font(Theme.label(10))
                .tracking(1.8)
                .foregroundStyle(Theme.textSecondary)
        }
        .multilineTextAlignment(.center)
        .transition(.scale(scale: 0.85).combined(with: .opacity))
    }

    // MARK: - Bottom

    @ViewBuilder
    private var badgeRow: some View {
        if !engine.activeBadges.isEmpty {
            HStack(spacing: 7) {
                ForEach(engine.activeBadges) { badge in
                    HStack(spacing: 4) {
                        Image(systemName: badge.kind.symbol)
                            .font(.system(size: 10, weight: .black))
                        if badge.secondsLeft > 0 {
                            Text("\(badge.secondsLeft)")
                                .font(Theme.numeral(10))
                        }
                    }
                    .foregroundStyle(badge.kind.accent)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 5)
                    .background(
                        Capsule()
                            .fill(Theme.voidDeep.opacity(0.7))
                            .overlay(Capsule().strokeBorder(badge.kind.accent.opacity(0.6), lineWidth: 1))
                    )
                    .transition(.scale.combined(with: .opacity))
                }
            }
            .padding(.bottom, 8)
            .accessibilityLabel("Active power-ups")
        }
    }

    private var bottomRow: some View {
        HStack {
            HUDPill(accent: Theme.cyan) {
                HStack(spacing: 6) {
                    Image(systemName: "shield.lefthalf.filled")
                        .font(.system(size: 13, weight: .black))
                        .foregroundStyle(Theme.cyan)
                    Text("\(max(0, engine.lives))")
                        .font(Theme.numeral(17))
                        .foregroundStyle(Theme.textPrimary)
                        .contentTransition(.numericText())
                }
            }
            .accessibilityLabel("\(max(0, engine.lives)) lives remaining")

            Spacer()

            Button {
                engine.triggerBlast()
            } label: {
                HStack(spacing: 6) {
                    Image(systemName: "burst.fill")
                        .font(.system(size: 13, weight: .black))
                    Text("\(engine.blastCharges)")
                        .font(Theme.numeral(17))
                        .contentTransition(.numericText())
                }
                .foregroundStyle(engine.blastCharges > 0 ? Theme.ember : Theme.textSecondary.opacity(0.5))
                .padding(.horizontal, 14)
                .padding(.vertical, 7)
                .background(
                    Capsule()
                        .fill(Theme.voidDeep.opacity(0.66))
                        .overlay(
                            Capsule().strokeBorder(
                                (engine.blastCharges > 0 ? Theme.ember : Theme.textSecondary).opacity(0.55),
                                lineWidth: 1
                            )
                        )
                )
                .shadow(color: Theme.ember.opacity(engine.blastCharges > 0 ? 0.45 : 0), radius: 10)
            }
            .buttonStyle(.plain)
            .disabled(engine.blastCharges == 0)
            .accessibilityLabel("Fire blast, \(engine.blastCharges) charges")
        }
    }
}

/// Transient achievement unlock toast.
struct AchievementToastView: View {
    let toast: AchievementToast

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: toast.achievement.symbol)
                .font(.system(size: 18, weight: .black))
                .foregroundStyle(toast.achievement.accent)
                .frame(width: 40, height: 40)
                .background(
                    Circle()
                        .fill(Theme.voidDeep.opacity(0.8))
                        .overlay(Circle().strokeBorder(toast.achievement.accent.opacity(0.6), lineWidth: 1))
                )

            VStack(alignment: .leading, spacing: 2) {
                Text("ACHIEVEMENT UNLOCKED")
                    .font(Theme.label(9))
                    .tracking(1.6)
                    .foregroundStyle(toast.achievement.accent)
                Text(toast.achievement.title)
                    .font(Theme.label(15))
                    .tracking(1.4)
                    .foregroundStyle(Theme.textPrimary)
            }

            Spacer(minLength: 0)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(Theme.panel.opacity(0.9))
                .overlay(
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .strokeBorder(toast.achievement.accent.opacity(0.5), lineWidth: 1)
                )
        )
        .shadow(color: toast.achievement.accent.opacity(0.35), radius: 16)
        .accessibilityLabel("Achievement unlocked: \(toast.achievement.title)")
    }
}
