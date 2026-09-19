import SwiftUI

/// Briefing sheet: controls, enemy dossier, power-up legend and the siphon mechanic.
struct HowToPlayScreen: View {
    var onDismiss: () -> Void

    var body: some View {
        ZStack {
            MenuBackdrop(dim: 0.52)

            VStack(spacing: 0) {
                header

                ScrollView {
                    VStack(alignment: .leading, spacing: 22) {
                        controlsSection
                        enemySection
                        powerUpSection
                        siphonSection
                        scoringSection
                        Color.clear.frame(height: 12)
                    }
                    .padding(.horizontal, 20)
                    .padding(.top, 8)
                }

                NeonButton(title: "GOT IT", accent: Theme.cyan, style: .filled, showsChevron: false, action: onDismiss)
                    .padding(.horizontal, 24)
                    .padding(.bottom, 14)
                    .padding(.top, 8)
            }
        }
    }

    private var header: some View {
        VStack(spacing: 4) {
            Text("HOW TO PLAY")
                .font(Theme.display(30))
                .tracking(4)
                .foregroundStyle(Theme.textPrimary)
                .neonGlow(Theme.violet, radius: 14)
            Text("SURVIVE THE FORMATIONS")
                .font(Theme.label(10))
                .tracking(2.2)
                .foregroundStyle(Theme.textSecondary)
        }
        .padding(.top, 18)
        .padding(.bottom, 6)
    }

    private func sectionTitle(_ text: String, accent: Color) -> some View {
        Text(text)
            .font(Theme.label(13))
            .tracking(2.4)
            .foregroundStyle(accent)
            .neonGlow(accent, radius: 6, intensity: 0.5)
    }

    private var controlsSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            sectionTitle("CONTROLS", accent: Theme.cyan)

            NeonPanel(accent: Theme.cyan) {
                VStack(alignment: .leading, spacing: 12) {
                    bullet(icon: "hand.draw.fill", title: "DRAG ANYWHERE", detail: "Slide your thumb to fly. Steering is relative, so your finger never covers the ship.", accent: Theme.cyan)
                    bullet(icon: "bolt.fill", title: "AUTO-FIRE", detail: "On by default. Turn it off from the menu or pause screen to tap-fire manually.", accent: Theme.amber)
                    bullet(icon: "burst.fill", title: "BLAST", detail: "Tap the bottom-right charge to vaporise everything near you. Earn more from drops and wave clears.", accent: Theme.ember)
                }
                .padding(16)
            }
        }
    }

    private var enemySection: some View {
        VStack(alignment: .leading, spacing: 10) {
            sectionTitle("HOSTILE DOSSIER", accent: Theme.magenta)

            VStack(spacing: 8) {
                ForEach(EnemyKind.allCases) { kind in
                    HStack(spacing: 12) {
                        Image(kind.sprite)
                            .resizable()
                            .scaledToFit()
                            .frame(width: 38, height: 38)
                            .shadow(color: kind.accent.opacity(0.7), radius: 8)

                        VStack(alignment: .leading, spacing: 2) {
                            HStack(spacing: 6) {
                                Text(kind.displayName)
                                    .font(Theme.label(12))
                                    .tracking(1.4)
                                    .foregroundStyle(kind.accent)
                                Text("\(kind.points) PTS")
                                    .font(Theme.numeral(10))
                                    .foregroundStyle(Theme.textSecondary)
                            }
                            Text(kind.dossier)
                                .font(.system(size: 11, weight: .medium, design: .rounded))
                                .foregroundStyle(Theme.textSecondary)
                                .fixedSize(horizontal: false, vertical: true)
                        }

                        Spacer(minLength: 0)
                    }
                    .padding(12)
                    .background(
                        RoundedRectangle(cornerRadius: 14, style: .continuous)
                            .fill(Theme.panel.opacity(0.55))
                            .overlay(
                                RoundedRectangle(cornerRadius: 14, style: .continuous)
                                    .strokeBorder(kind.accent.opacity(0.28), lineWidth: 1)
                            )
                    )
                }
            }
        }
    }

    private var powerUpSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            sectionTitle("POWER-UPS", accent: Theme.acid)

            NeonPanel(accent: Theme.acid) {
                VStack(alignment: .leading, spacing: 11) {
                    ForEach(PowerUpKind.allCases) { kind in
                        HStack(alignment: .top, spacing: 11) {
                            Image(systemName: kind.symbol)
                                .font(.system(size: 13, weight: .black))
                                .foregroundStyle(kind.accent)
                                .frame(width: 26, height: 26)
                                .background(
                                    RoundedRectangle(cornerRadius: 7, style: .continuous)
                                        .fill(Theme.voidDeep.opacity(0.7))
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 7, style: .continuous)
                                                .strokeBorder(kind.accent.opacity(0.6), lineWidth: 1)
                                        )
                                )

                            VStack(alignment: .leading, spacing: 2) {
                                Text(kind.title)
                                    .font(Theme.label(11))
                                    .tracking(1.2)
                                    .foregroundStyle(Theme.textPrimary)
                                Text(kind.detail)
                                    .font(.system(size: 11, weight: .medium, design: .rounded))
                                    .foregroundStyle(Theme.textSecondary)
                                    .fixedSize(horizontal: false, vertical: true)
                            }

                            Spacer(minLength: 0)
                        }
                    }
                }
                .padding(16)
            }
        }
    }

    private var siphonSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            sectionTitle("THE VOID SIPHON", accent: Theme.violet)

            NeonPanel(accent: Theme.violet) {
                VStack(alignment: .leading, spacing: 12) {
                    bullet(
                        icon: "target",
                        title: "THE BEAM",
                        detail: "A Siphon Warden drops low and opens a prism beam. Linger inside it and a ring closes around your ship.",
                        accent: Theme.magenta
                    )
                    bullet(
                        icon: "arrow.down.circle.fill",
                        title: "IF IT LOCKS",
                        detail: "Your ship is taken and you lose a life. The captured hull stays clamped beneath the warden.",
                        accent: Theme.danger
                    )
                    bullet(
                        icon: "arrow.uturn.up.circle.fill",
                        title: "THE RESCUE",
                        detail: "Destroy that warden to recover your ship. It rejoins as two wing-mates flying escort with extra guns.",
                        accent: Theme.cyan
                    )
                }
                .padding(16)
            }
        }
    }

    private var scoringSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            sectionTitle("SCORING", accent: Theme.amber)

            NeonPanel(accent: Theme.amber) {
                VStack(alignment: .leading, spacing: 12) {
                    bullet(icon: "flame.fill", title: "COMBOS", detail: "Consecutive kills raise your multiplier up to x10. Taking a hit resets it to x1.", accent: Theme.amber)
                    bullet(icon: "sparkles", title: "PERFECT WAVE", detail: "Clear a wave without being hit for a large bonus.", accent: Theme.acid)
                    bullet(icon: "star.circle.fill", title: "CHALLENGE WAVES", detail: "Every fourth wave the aliens fly elaborate routes and never shoot. Clear them all for a huge payout.", accent: Theme.cyan)
                    bullet(icon: "crown.fill", title: "BOSSES", detail: "Every fifth wave. Shoot the glowing cores for triple damage.", accent: Theme.magenta)
                }
                .padding(16)
            }
        }
    }

    private func bullet(icon: String, title: String, detail: String, accent: Color) -> some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 15, weight: .black))
                .foregroundStyle(accent)
                .frame(width: 24)

            VStack(alignment: .leading, spacing: 3) {
                Text(title)
                    .font(Theme.label(12))
                    .tracking(1.4)
                    .foregroundStyle(Theme.textPrimary)
                Text(detail)
                    .font(.system(size: 12, weight: .medium, design: .rounded))
                    .foregroundStyle(Theme.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
    }
}
