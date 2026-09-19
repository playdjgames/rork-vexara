import SwiftUI

/// The live battlefield: backdrop, canvas renderer, HUD, controls and overlays.
struct GameScreen: View {
    @Bindable var engine: GameEngine

    /// Position the drag began at, used for relative steering so the finger never
    /// has to sit on top of the ship.
    @State private var dragAnchor: CGFloat?
    @State private var shipAnchor: CGFloat = 0

    var body: some View {
        GeometryReader { proxy in
            let playfield = playfieldSize(in: proxy.size)

            ZStack {
                Theme.voidDeep.ignoresSafeArea()

                ZStack {
                    backdrop
                    BattlefieldRenderer(engine: engine, size: playfield)
                    flashOverlay
                    controlSurface
                    GameHUD(engine: engine) {
                        engine.pause()
                    }
                    .padding(.top, 4)
                }
                .frame(width: playfield.width, height: playfield.height)
                .clipped()
                .overlay(
                    Rectangle()
                        .strokeBorder(Theme.magenta.opacity(0.18), lineWidth: 1)
                        .opacity(playfield.width < proxy.size.width - 1 ? 1 : 0)
                )
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .onAppear { engine.updateSize(playfield) }
            .onChange(of: playfield) { _, newValue in
                engine.updateSize(newValue)
            }
            .overlay(alignment: .top) {
                if let toast = engine.achievementToast {
                    AchievementToastView(toast: toast)
                        .padding(.horizontal, 20)
                        .padding(.top, 80)
                        .transition(.move(edge: .top).combined(with: .opacity))
                }
            }
            .animation(.spring(response: 0.4, dampingFraction: 0.75), value: engine.achievementToast)
        }
    }

    /// Letterboxes wide screens so the battlefield stays a vertical arcade screen.
    private func playfieldSize(in available: CGSize) -> CGSize {
        let maxWidth = available.height * Theme.playfieldAspect
        let width = min(available.width, maxWidth)
        return CGSize(width: width, height: available.height)
    }

    private var backdrop: some View {
        MenuBackdrop(
            imageName: engine.isBossWave ? "nebula_storm_bg" : "space_nebula_bg",
            dim: engine.isBossWave ? 0.46 : 0.38
        )
    }

    private var flashOverlay: some View {
        Rectangle()
            .fill(Theme.textPrimary)
            .opacity(engine.flashAlpha * 0.45)
            .blendMode(.plusLighter)
            .allowsHitTesting(false)
            .ignoresSafeArea()
    }

    /// Full-screen drag surface. Relative dragging plus tap-to-fire when auto-fire is off.
    private var controlSurface: some View {
        Color.clear
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { value in
                        if dragAnchor == nil {
                            dragAnchor = value.startLocation.x
                            shipAnchor = engine.player.position.x
                            if !engine.autoFire { engine.firePressed = true }
                        }
                        guard let anchor = dragAnchor else { return }
                        let delta = value.location.x - anchor
                        let target = shipAnchor + delta * 1.35
                        // Convert the positional target into a velocity the sim can smooth.
                        let difference = target - engine.player.position.x
                        engine.inputVelocity = Double(difference) * 12
                    }
                    .onEnded { _ in
                        dragAnchor = nil
                        engine.inputVelocity = 0
                        engine.firePressed = false
                    }
            )
            .simultaneousGesture(
                TapGesture().onEnded {
                    guard !engine.autoFire else { return }
                    engine.firePressed = true
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.12) {
                        engine.firePressed = false
                    }
                }
            )
            .accessibilityHidden(true)
    }
}
