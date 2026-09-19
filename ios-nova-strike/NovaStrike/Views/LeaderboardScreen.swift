import SwiftUI

/// Local top-five plus the achievements roster, toggled with a segmented control.
struct LeaderboardScreen: View {
    let store: ProgressStore
    var onDismiss: () -> Void

    private enum Tab: String, CaseIterable, Identifiable {
        case scores = "SCORES"
        case achievements = "AWARDS"
        var id: String { rawValue }
    }

    @State private var tab: Tab = .scores

    var body: some View {
        ZStack {
            MenuBackdrop(dim: 0.52)

            VStack(spacing: 0) {
                header
                picker
                    .padding(.horizontal, 24)
                    .padding(.top, 12)

                ScrollView {
                    VStack(spacing: 10) {
                        switch tab {
                        case .scores:
                            scoresContent
                        case .achievements:
                            achievementsContent
                        }
                        Color.clear.frame(height: 10)
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 14)
                }

                NeonButton(title: "BACK", accent: Theme.cyan, style: .filled, showsChevron: false, action: onDismiss)
                    .padding(.horizontal, 24)
                    .padding(.bottom, 14)
                    .padding(.top, 4)
            }
        }
    }

    private var header: some View {
        VStack(spacing: 4) {
            Text("LEADERBOARD")
                .font(Theme.display(30))
                .tracking(4)
                .foregroundStyle(Theme.textPrimary)
                .neonGlow(Theme.magenta, radius: 14)

            Text("HIGH SCORE \(arcadeScore(store.highScore))")
                .font(Theme.numeral(12))
                .foregroundStyle(Theme.textSecondary)
        }
        .padding(.top, 18)
    }

    private var picker: some View {
        HStack(spacing: 8) {
            ForEach(Tab.allCases) { item in
                Button {
                    Haptics.tap()
                    AudioEngine.shared.play(.uiTap)
                    withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) {
                        tab = item
                    }
                } label: {
                    Text(item.rawValue)
                        .font(Theme.label(12))
                        .tracking(1.8)
                        .foregroundStyle(tab == item ? Theme.voidDeep : Theme.textSecondary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 11)
                        .background(
                            Capsule()
                                .fill(tab == item ? Theme.cyan : Theme.panel.opacity(0.5))
                                .overlay(
                                    Capsule().strokeBorder(
                                        tab == item ? Color.clear : Theme.textSecondary.opacity(0.25),
                                        lineWidth: 1
                                    )
                                )
                        )
                }
                .buttonStyle(.plain)
            }
        }
    }

    // MARK: - Scores

    @ViewBuilder
    private var scoresContent: some View {
        if store.leaderboard.isEmpty {
            emptyState(
                icon: "trophy",
                title: "NO RUNS LOGGED",
                detail: "Finish a run to claim the first slot on the cabinet."
            )
        } else {
            ForEach(Array(store.leaderboard.enumerated()), id: \.element.id) { index, entry in
                scoreRow(rank: index + 1, entry: entry)
            }
        }
    }

    private func scoreRow(rank: Int, entry: LeaderboardEntry) -> some View {
        let accent: Color = switch rank {
        case 1: Theme.amber
        case 2: Theme.cyan
        case 3: Theme.magenta
        default: Theme.textSecondary
        }

        return HStack(spacing: 12) {
            Text("\(rank)")
                .font(Theme.numeral(16))
                .foregroundStyle(accent)
                .frame(width: 26, alignment: .center)

            VStack(alignment: .leading, spacing: 2) {
                Text(entry.name)
                    .font(Theme.label(14))
                    .tracking(1.6)
                    .foregroundStyle(Theme.textPrimary)
                HStack(spacing: 6) {
                    Text("WAVE \(entry.wave)")
                    Text("·")
                    Text(entry.difficulty.title)
                    Text("·")
                    Text(entry.date, format: .dateTime.month(.abbreviated).day())
                }
                .font(.system(size: 10, weight: .semibold, design: .rounded))
                .foregroundStyle(Theme.textSecondary)
            }

            Spacer(minLength: 0)

            Text(arcadeScore(entry.score))
                .font(Theme.numeral(17))
                .foregroundStyle(accent)
                .neonGlow(accent, radius: rank <= 3 ? 7 : 0, intensity: rank <= 3 ? 0.6 : 0)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(Theme.panel.opacity(rank <= 3 ? 0.7 : 0.45))
                .overlay(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .strokeBorder(accent.opacity(rank <= 3 ? 0.45 : 0.16), lineWidth: 1)
                )
        )
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Rank \(rank), \(entry.name), \(entry.score) points, wave \(entry.wave), \(entry.difficulty.title)")
    }

    // MARK: - Achievements

    private var achievementsContent: some View {
        VStack(spacing: 10) {
            statsSummary

            ForEach(Achievement.allCases) { achievement in
                let unlocked = store.isUnlocked(achievement)

                HStack(spacing: 12) {
                    Image(systemName: unlocked ? achievement.symbol : "lock.fill")
                        .font(.system(size: 16, weight: .black))
                        .foregroundStyle(unlocked ? achievement.accent : Theme.textSecondary.opacity(0.5))
                        .frame(width: 40, height: 40)
                        .background(
                            Circle()
                                .fill(Theme.voidDeep.opacity(0.65))
                                .overlay(
                                    Circle().strokeBorder(
                                        (unlocked ? achievement.accent : Theme.textSecondary).opacity(unlocked ? 0.6 : 0.2),
                                        lineWidth: 1
                                    )
                                )
                        )

                    VStack(alignment: .leading, spacing: 2) {
                        Text(achievement.title)
                            .font(Theme.label(12))
                            .tracking(1.4)
                            .foregroundStyle(unlocked ? Theme.textPrimary : Theme.textSecondary.opacity(0.7))
                        Text(achievement.detail)
                            .font(.system(size: 11, weight: .medium, design: .rounded))
                            .foregroundStyle(Theme.textSecondary.opacity(unlocked ? 1 : 0.6))
                            .fixedSize(horizontal: false, vertical: true)
                    }

                    Spacer(minLength: 0)

                    if unlocked {
                        Image(systemName: "checkmark.seal.fill")
                            .font(.system(size: 14, weight: .black))
                            .foregroundStyle(achievement.accent)
                    }
                }
                .padding(12)
                .background(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .fill(Theme.panel.opacity(unlocked ? 0.6 : 0.35))
                        .overlay(
                            RoundedRectangle(cornerRadius: 14, style: .continuous)
                                .strokeBorder(
                                    (unlocked ? achievement.accent : Theme.textSecondary).opacity(unlocked ? 0.35 : 0.12),
                                    lineWidth: 1
                                )
                        )
                )
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("\(achievement.title), \(unlocked ? "unlocked" : "locked"). \(achievement.detail)")
            }
        }
    }

    private var statsSummary: some View {
        NeonPanel(accent: Theme.violet) {
            VStack(spacing: 12) {
                HStack(spacing: 10) {
                    statTile("RUNS", "\(store.stats.runsPlayed)", Theme.cyan)
                    statTile("KILLS", "\(store.stats.totalKills)", Theme.magenta)
                    statTile("BEST WAVE", "\(store.stats.bestWave)", Theme.amber)
                }
                HStack(spacing: 10) {
                    statTile("BOSSES", "\(store.stats.bossKills)", Theme.danger)
                    statTile("RESCUES", "\(store.stats.rescues)", Theme.acid)
                    statTile("PERFECT", "\(store.stats.perfectWaves)", Theme.violet)
                }
            }
            .padding(16)
        }
    }

    private func statTile(_ label: String, _ value: String, _ accent: Color) -> some View {
        VStack(spacing: 3) {
            Text(value)
                .font(Theme.numeral(17))
                .foregroundStyle(accent)
            Text(label)
                .font(Theme.label(8))
                .tracking(1)
                .foregroundStyle(Theme.textSecondary)
        }
        .frame(maxWidth: .infinity)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(label): \(value)")
    }

    private func emptyState(icon: String, title: String, detail: String) -> some View {
        VStack(spacing: 10) {
            Image(systemName: icon)
                .font(.system(size: 34, weight: .light))
                .foregroundStyle(Theme.textSecondary.opacity(0.5))
            Text(title)
                .font(Theme.label(14))
                .tracking(1.8)
                .foregroundStyle(Theme.textSecondary)
            Text(detail)
                .font(.system(size: 12, weight: .medium, design: .rounded))
                .foregroundStyle(Theme.textSecondary.opacity(0.7))
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 50)
    }
}
