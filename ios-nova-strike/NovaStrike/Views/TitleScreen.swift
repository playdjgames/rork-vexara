import SwiftUI

/// Root menu surface: the player's ship front and centre facing a looming formation,
/// with the wordmark between them and the four primary actions below.
struct TitleScreen: View {
    @Bindable var engine: GameEngine
    var onStart: () -> Void
    var onHowToPlay: () -> Void
    var onLeaderboard: () -> Void

    @State private var formationDrift: CGFloat = 0
    @State private var shipBob: CGFloat = 0
    @State private var flarePulse: CGFloat = 1

    var body: some View {
        GeometryReader { proxy in
            let width = proxy.size.width

            ZStack {
                MenuBackdrop(dim: 0.3)

                VStack(spacing: 0) {
                    alienFormation(width: width)
                        .padding(.top, 18)

                    VexaraWordmark(scale: min(1.05, width / 390))
                        .padding(.top, 6)

                    Spacer(minLength: 8)

                    playerShip(width: width)

                    Spacer(minLength: 8)

                    menuButtons
                        .padding(.horizontal, 24)
                        .padding(.bottom, 8)
                }
                .padding(.vertical, 8)
            }
            .overlay(alignment: .topTrailing) {
                soundToggle
                    .padding(.trailing, 16)
                    .padding(.top, 8)
            }
        }
        .onAppear(perform: startAnimations)
    }

    private func startAnimations() {
        withAnimation(.easeInOut(duration: 3.4).repeatForever(autoreverses: true)) {
            formationDrift = 16
        }
        withAnimation(.easeInOut(duration: 2.6).repeatForever(autoreverses: true)) {
            shipBob = -10
        }
        withAnimation(.easeInOut(duration: 0.5).repeatForever(autoreverses: true)) {
            flarePulse = 1.35
        }
    }

    // MARK: - Formation

    private func alienFormation(width: CGFloat) -> some View {
        let unit = min(46, width / 9)

        return VStack(spacing: unit * 0.16) {
            // Lead warden, flanked by a widening V of fighters.
            Image("alien_mothership_boss")
                .resizable()
                .scaledToFit()
                .frame(width: unit * 2.5, height: unit * 2.5)
                .shadow(color: Theme.magenta.opacity(0.7), radius: 18)

            ForEach(0..<3, id: \.self) { row in
                HStack(spacing: unit * 0.28) {
                    ForEach(0..<(row + 3), id: \.self) { column in
                        Image(row == 0 ? "insectoid_fighter_craft" : (row == 1 ? "alien_interceptor_sprite" : "insectoid_fighter_craft"))
                            .resizable()
                            .scaledToFit()
                            .frame(width: unit * 0.82, height: unit * 0.82)
                            .shadow(color: (row == 1 ? Theme.ember : Theme.magenta).opacity(0.6), radius: 8)
                            .offset(y: CGFloat(column % 2) * unit * 0.12)
                    }
                }
            }
        }
        .offset(x: formationDrift - 8)
        .accessibilityHidden(true)
    }

    // MARK: - Player ship

    private func playerShip(width: CGFloat) -> some View {
        let shipWidth = min(150, width * 0.4)

        return ZStack {
            // Twin engine flares beneath the hull.
            HStack(spacing: shipWidth * 0.14) {
                ForEach(0..<2, id: \.self) { _ in
                    Capsule()
                        .fill(
                            LinearGradient(
                                colors: [Theme.textPrimary, Theme.cyan, Theme.cyan.opacity(0)],
                                startPoint: .top,
                                endPoint: .bottom
                            )
                        )
                        .frame(width: shipWidth * 0.1, height: shipWidth * 0.55 * flarePulse)
                        .blur(radius: 2)
                }
            }
            .offset(y: shipWidth * 0.52)

            Image("starfighter_top_view")
                .resizable()
                .scaledToFit()
                .frame(width: shipWidth, height: shipWidth)
                .shadow(color: Theme.cyan.opacity(0.75), radius: 22)
        }
        .offset(y: shipBob)
        .accessibilityHidden(true)
    }

    // MARK: - Menu

    private var menuButtons: some View {
        VStack(spacing: 12) {
            NeonButton(title: "START GAME", accent: Theme.cyan, style: .filled, action: onStart)
            NeonButton(title: "HOW TO PLAY", accent: Theme.violet, action: onHowToPlay)
            NeonButton(title: "LEADERBOARD", accent: Theme.magenta, action: onLeaderboard)
            NeonButton(
                title: "DIFFICULTY: \(engine.store.difficulty.title)",
                accent: engine.store.difficulty.accent,
                showsChevron: true
            ) {
                cycleDifficulty()
            }
        }
    }

    private func cycleDifficulty() {
        let all = Difficulty.allCases
        guard let index = all.firstIndex(of: engine.store.difficulty) else { return }
        let next = all[(index + 1) % all.count]
        withAnimation(.spring(response: 0.3, dampingFraction: 0.7)) {
            engine.store.difficulty = next
            engine.difficulty = next
        }
    }

    private var soundToggle: some View {
        HStack(spacing: 8) {
            CircleIconButton(
                systemName: engine.store.isMuted ? "speaker.slash.fill" : "speaker.wave.2.fill",
                accent: engine.store.isMuted ? Theme.textSecondary : Theme.cyan
            ) {
                engine.store.isMuted.toggle()
            }
            .accessibilityLabel(engine.store.isMuted ? "Unmute sound" : "Mute sound")

            CircleIconButton(
                systemName: engine.store.autoFire ? "bolt.fill" : "bolt.slash.fill",
                accent: engine.store.autoFire ? Theme.amber : Theme.textSecondary
            ) {
                engine.store.autoFire.toggle()
                engine.autoFire = engine.store.autoFire
            }
            .accessibilityLabel(engine.store.autoFire ? "Auto-fire on" : "Auto-fire off")
        }
    }
}
