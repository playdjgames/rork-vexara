import SwiftUI

/// End-of-run surface with stats, optional high-score celebration and name entry.
struct GameOverScreen: View {
    let summary: GameOverSummary
    let engine: GameEngine
    var onRetry: () -> Void
    var onMenu: () -> Void

    @State private var name: String = ""
    @State private var submitted: Bool = false
    @State private var revealed: Bool = false
    @FocusState private var nameFocused: Bool

    private var needsEntry: Bool {
        summary.qualifiesForLeaderboard && !submitted
    }

    var body: some View {
        ZStack {
            MenuBackdrop(imageName: "nebula_storm_bg", dim: 0.6)

            ScrollView {
                VStack(spacing: 18) {
                    Spacer(minLength: 28)

                    if summary.isNewHighScore {
                        highScoreBanner
                    } else {
                        Text("GAME OVER")
                            .font(Theme.display(44))
                            .tracking(5)
                            .foregroundStyle(Theme.textPrimary)
                            .neonGlow(Theme.danger, radius: 18)
                    }

                    scorePanel

                    if needsEntry {
                        nameEntry
                    }

                    statRow

                    VStack(spacing: 12) {
                        NeonButton(title: "RETRY", accent: Theme.cyan, style: .filled, showsChevron: false) {
                            commitIfNeeded()
                            onRetry()
                        }
                        NeonButton(title: "MAIN MENU", accent: Theme.violet, showsChevron: false) {
                            commitIfNeeded()
                            onMenu()
                        }
                    }
                    .padding(.top, 4)

                    Spacer(minLength: 24)
                }
                .padding(.horizontal, 24)
                .scaleEffect(revealed ? 1 : 0.94)
                .opacity(revealed ? 1 : 0)
            }
            .scrollDismissesKeyboard(.interactively)
        }
        .onAppear {
            name = engine.store.playerName
            withAnimation(.spring(response: 0.5, dampingFraction: 0.8)) {
                revealed = true
            }
        }
    }

    private var highScoreBanner: some View {
        VStack(spacing: 2) {
            Text("NEW HIGH SCORE!")
                .font(Theme.display(34))
                .tracking(3)
                .foregroundStyle(
                    LinearGradient(colors: [Color(hex: 0xFFF3C4), Theme.amber], startPoint: .top, endPoint: .bottom)
                )
                .neonGlow(Theme.amber, radius: 20)
            Text("YOU TOPPED THE CABINET")
                .font(Theme.label(11))
                .tracking(2.2)
                .foregroundStyle(Theme.textSecondary)
        }
        .multilineTextAlignment(.center)
    }

    private var scorePanel: some View {
        NeonPanel(accent: summary.isNewHighScore ? Theme.amber : Theme.cyan) {
            VStack(spacing: 6) {
                Text("FINAL SCORE")
                    .font(Theme.label(11))
                    .tracking(2)
                    .foregroundStyle(Theme.textSecondary)
                Text(arcadeScore(summary.score))
                    .font(Theme.numeral(40))
                    .foregroundStyle(Theme.textPrimary)
                    .neonGlow(summary.isNewHighScore ? Theme.amber : Theme.cyan, radius: 14)
                Text("HIGH SCORE \(arcadeScore(engine.store.highScore))")
                    .font(Theme.numeral(12))
                    .foregroundStyle(Theme.textSecondary)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 20)
        }
        .accessibilityLabel("Final score \(summary.score)")
    }

    private var nameEntry: some View {
        NeonPanel(accent: Theme.magenta) {
            VStack(spacing: 12) {
                Text("YOU MADE THE TOP FIVE")
                    .font(Theme.label(12))
                    .tracking(1.8)
                    .foregroundStyle(Theme.magenta)

                TextField("", text: $name, prompt: Text("ACE").foregroundStyle(Theme.textSecondary.opacity(0.5)))
                    .font(Theme.numeral(24))
                    .foregroundStyle(Theme.textPrimary)
                    .multilineTextAlignment(.center)
                    .textInputAutocapitalization(.characters)
                    .autocorrectionDisabled()
                    .focused($nameFocused)
                    .submitLabel(.done)
                    .onSubmit(commitIfNeeded)
                    .onChange(of: name) { _, newValue in
                        if newValue.count > 8 {
                            name = String(newValue.prefix(8))
                        }
                    }
                    .padding(.vertical, 10)
                    .background(
                        RoundedRectangle(cornerRadius: 10, style: .continuous)
                            .fill(Theme.voidDeep.opacity(0.7))
                            .overlay(
                                RoundedRectangle(cornerRadius: 10, style: .continuous)
                                    .strokeBorder(Theme.magenta.opacity(0.5), lineWidth: 1)
                            )
                    )

                Button {
                    Haptics.tap()
                    AudioEngine.shared.play(.uiTap)
                    commitIfNeeded()
                } label: {
                    Text("SAVE SCORE")
                        .font(Theme.label(14))
                        .tracking(2)
                        .foregroundStyle(Theme.voidDeep)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(Capsule().fill(Theme.magenta))
                }
                .buttonStyle(.plain)
            }
            .padding(16)
        }
    }

    private var statRow: some View {
        HStack(spacing: 10) {
            statTile("WAVE", value: "\(summary.wave)", accent: Theme.magenta)
            statTile("KILLS", value: "\(summary.kills)", accent: Theme.cyan)
            statTile("BEST COMBO", value: "x\(summary.bestCombo)", accent: Theme.amber)
        }
    }

    private func statTile(_ label: String, value: String, accent: Color) -> some View {
        VStack(spacing: 4) {
            Text(value)
                .font(Theme.numeral(20))
                .foregroundStyle(accent)
            Text(label)
                .font(Theme.label(9))
                .tracking(1.2)
                .foregroundStyle(Theme.textSecondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 14)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(Theme.panel.opacity(0.6))
                .overlay(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .strokeBorder(accent.opacity(0.32), lineWidth: 1)
                )
        )
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(label): \(value)")
    }

    private func commitIfNeeded() {
        guard needsEntry else { return }
        nameFocused = false
        engine.store.submit(
            name: name,
            score: summary.score,
            wave: summary.wave,
            difficulty: engine.difficulty
        )
        submitted = true
        Haptics.success()
    }
}
