import SwiftUI

/// Root shell. Vexara is a full-bleed arcade product: no tab bar, no navigation
/// chrome. Each phase owns the whole screen and transitions are crossfades.
struct ContentView: View {
    @State private var store = ProgressStore()
    @State private var engine: GameEngine?
    @State private var showHowToPlay: Bool = false
    @State private var showLeaderboard: Bool = false

    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        ZStack {
            Theme.voidDeep.ignoresSafeArea()

            if let engine {
                content(for: engine)
            }
        }
        .preferredColorScheme(.dark)
        .statusBarHidden(engine?.phase != .title)
        .persistentSystemOverlays(engine?.phase == .playing ? .hidden : .automatic)
        .task {
            if engine == nil {
                AudioEngine.shared.preload()
                Haptics.prepare()
                let created = GameEngine(store: store)
                engine = created
                AudioEngine.shared.playMusic(.menu)
            }
        }
        .onChange(of: scenePhase) { _, newPhase in
            guard let engine else { return }
            // Backgrounding mid-run should never cost the player a life.
            if newPhase != .active, engine.phase == .playing {
                engine.pause()
            }
        }
    }

    @ViewBuilder
    private func content(for engine: GameEngine) -> some View {
        ZStack {
            switch engine.phase {
            case .title:
                TitleScreen(
                    engine: engine,
                    onStart: {
                        engine.startRun()
                    },
                    onHowToPlay: { showHowToPlay = true },
                    onLeaderboard: { showLeaderboard = true }
                )
                .transition(.opacity)

            case .playing, .paused:
                GameScreen(engine: engine)
                    .transition(.opacity)

            case .gameOver:
                if let summary = engine.gameOverSummary {
                    GameOverScreen(
                        summary: summary,
                        engine: engine,
                        onRetry: { engine.startRun() },
                        onMenu: { engine.returnToTitle() }
                    )
                    .transition(.opacity)
                }
            }

            if engine.phase == .paused {
                PauseOverlay(
                    engine: engine,
                    onResume: { engine.resume() },
                    onRestart: { engine.startRun() },
                    onQuit: { engine.returnToTitle() }
                )
                .transition(.opacity)
            }
        }
        .animation(.easeInOut(duration: 0.3), value: engine.phase)
        .fullScreenCover(isPresented: $showHowToPlay) {
            HowToPlayScreen { showHowToPlay = false }
        }
        .fullScreenCover(isPresented: $showLeaderboard) {
            LeaderboardScreen(store: store) { showLeaderboard = false }
        }
    }
}

#Preview {
    ContentView()
}
