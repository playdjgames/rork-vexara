import SwiftUI

/// Modal pause surface layered over the frozen battlefield.
struct PauseOverlay: View {
    let engine: GameEngine
    var onResume: () -> Void
    var onRestart: () -> Void
    var onQuit: () -> Void

    var body: some View {
        ZStack {
            Rectangle()
                .fill(Theme.voidDeep.opacity(0.82))
                .ignoresSafeArea()

            VStack(spacing: 20) {
                Text("PAUSED")
                    .font(Theme.display(40))
                    .tracking(6)
                    .foregroundStyle(Theme.textPrimary)
                    .neonGlow(Theme.cyan, radius: 16)

                NeonPanel(accent: Theme.cyan) {
                    VStack(spacing: 0) {
                        statLine("SCORE", value: arcadeScore(engine.score), accent: Theme.cyan)
                        separator
                        statLine("WAVE", value: "\(engine.wave)", accent: Theme.magenta)
                        separator
                        statLine("LIVES", value: "\(max(0, engine.lives))", accent: Theme.acid)
                    }
                    .padding(.vertical, 4)
                }

                toggles

                VStack(spacing: 12) {
                    NeonButton(title: "RESUME", accent: Theme.cyan, style: .filled, showsChevron: false, action: onResume)
                    NeonButton(title: "RESTART RUN", accent: Theme.amber, showsChevron: false, action: onRestart)
                    NeonButton(title: "QUIT TO MENU", accent: Theme.danger, showsChevron: false, action: onQuit)
                }
            }
            .padding(.horizontal, 30)
        }
    }

    private var separator: some View {
        Rectangle()
            .fill(Theme.textSecondary.opacity(0.14))
            .frame(height: 1)
            .padding(.horizontal, 16)
    }

    private func statLine(_ label: String, value: String, accent: Color) -> some View {
        HStack {
            Text(label)
                .font(Theme.label(12))
                .tracking(1.6)
                .foregroundStyle(Theme.textSecondary)
            Spacer()
            Text(value)
                .font(Theme.numeral(17))
                .foregroundStyle(accent)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 11)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(label): \(value)")
    }

    private var toggles: some View {
        HStack(spacing: 10) {
            toggleChip(
                title: "SOUND",
                isOn: !engine.store.isMuted,
                symbol: engine.store.isMuted ? "speaker.slash.fill" : "speaker.wave.2.fill",
                accent: Theme.cyan
            ) {
                engine.store.isMuted.toggle()
            }

            toggleChip(
                title: "AUTO-FIRE",
                isOn: engine.store.autoFire,
                symbol: engine.store.autoFire ? "bolt.fill" : "bolt.slash.fill",
                accent: Theme.amber
            ) {
                engine.store.autoFire.toggle()
                engine.autoFire = engine.store.autoFire
            }

            toggleChip(
                title: "HAPTICS",
                isOn: engine.store.hapticsEnabled,
                symbol: "waveform",
                accent: Theme.violet
            ) {
                engine.store.hapticsEnabled.toggle()
            }
        }
    }

    private func toggleChip(title: String, isOn: Bool, symbol: String, accent: Color, action: @escaping () -> Void) -> some View {
        Button {
            Haptics.tap()
            AudioEngine.shared.play(.uiTap)
            action()
        } label: {
            VStack(spacing: 5) {
                Image(systemName: symbol)
                    .font(.system(size: 15, weight: .black))
                Text(title)
                    .font(Theme.label(9))
                    .tracking(1)
            }
            .foregroundStyle(isOn ? accent : Theme.textSecondary.opacity(0.55))
            .frame(maxWidth: .infinity)
            .padding(.vertical, 12)
            .background(
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .fill(Theme.panel.opacity(isOn ? 0.75 : 0.4))
                    .overlay(
                        RoundedRectangle(cornerRadius: 14, style: .continuous)
                            .strokeBorder((isOn ? accent : Theme.textSecondary).opacity(isOn ? 0.55 : 0.2), lineWidth: 1)
                    )
            )
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(title) \(isOn ? "on" : "off")")
    }
}
