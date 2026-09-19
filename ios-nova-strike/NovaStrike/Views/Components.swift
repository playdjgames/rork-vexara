import SwiftUI

/// Neon-outlined capsule used for every HUD readout.
struct HUDPill<Content: View>: View {
    var accent: Color
    var content: Content

    init(accent: Color, @ViewBuilder content: () -> Content) {
        self.accent = accent
        self.content = content()
    }

    var body: some View {
        content
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            .background(
                Capsule(style: .continuous)
                    .fill(Theme.voidDeep.opacity(0.62))
                    .overlay(
                        Capsule(style: .continuous)
                            .strokeBorder(accent.opacity(0.55), lineWidth: 1)
                    )
            )
            .shadow(color: accent.opacity(0.35), radius: 8)
    }
}

/// The primary arcade action button: a glowing capsule with press feedback.
struct NeonButton: View {
    enum Style {
        case filled
        case outline
    }

    var title: String
    var accent: Color = Theme.cyan
    var style: Style = .outline
    var showsChevron: Bool = true
    var subtitle: String?
    var action: () -> Void

    @State private var isPressed: Bool = false

    var body: some View {
        Button {
            Haptics.tap()
            AudioEngine.shared.play(.uiTap)
            action()
        } label: {
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(Theme.label(17))
                        .tracking(2)
                        .foregroundStyle(style == .filled ? Theme.voidDeep : Theme.textPrimary)
                    if let subtitle {
                        Text(subtitle)
                            .font(.system(size: 11, weight: .semibold, design: .rounded))
                            .tracking(0.8)
                            .foregroundStyle(style == .filled ? Theme.voidDeep.opacity(0.72) : Theme.textSecondary)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .center)
                .multilineTextAlignment(.center)

                if showsChevron {
                    Image(systemName: "chevron.right")
                        .font(.system(size: 14, weight: .black))
                        .foregroundStyle(style == .filled ? Theme.voidDeep.opacity(0.8) : accent)
                }
            }
            .padding(.horizontal, 22)
            .frame(height: subtitle == nil ? 56 : 64)
            .frame(maxWidth: .infinity)
            .background(background)
            .clipShape(Capsule(style: .continuous))
            .overlay(
                Capsule(style: .continuous)
                    .strokeBorder(accent.opacity(style == .filled ? 0.9 : 0.55), lineWidth: 1.5)
            )
            .shadow(color: accent.opacity(style == .filled ? 0.6 : 0.3), radius: isPressed ? 6 : 16, y: 2)
            .scaleEffect(isPressed ? 0.96 : 1)
        }
        .buttonStyle(.plain)
        .simultaneousGesture(
            DragGesture(minimumDistance: 0)
                .onChanged { _ in
                    if !isPressed {
                        withAnimation(.easeOut(duration: 0.12)) { isPressed = true }
                    }
                }
                .onEnded { _ in
                    withAnimation(.spring(response: 0.3, dampingFraction: 0.6)) { isPressed = false }
                }
        )
        .accessibilityLabel(subtitle == nil ? title : "\(title). \(subtitle ?? "")")
    }

    @ViewBuilder
    private var background: some View {
        switch style {
        case .filled:
            LinearGradient(
                colors: [accent, accent.opacity(0.72)],
                startPoint: .top,
                endPoint: .bottom
            )
        case .outline:
            LinearGradient(
                colors: [Theme.panel.opacity(0.85), Theme.voidDeep.opacity(0.75)],
                startPoint: .top,
                endPoint: .bottom
            )
        }
    }
}

/// The VEXARA marquee. One word, so the brand's cyan-to-magenta split runs vertically
/// through the letterforms instead of across two stacked tiers, with a ruled kicker below.
struct VexaraWordmark: View {
    var scale: CGFloat = 1

    private let tracking: CGFloat = 10

    var body: some View {
        VStack(spacing: 7 * scale) {
            Text("VEXARA")
                .font(Theme.display(60 * scale))
                .tracking(tracking * scale)
                .lineLimit(1)
                .minimumScaleFactor(0.6)
                .foregroundStyle(
                    LinearGradient(
                        colors: [
                            Color(hex: 0xEAFBFF),
                            Theme.cyan,
                            Theme.magenta,
                            Color(hex: 0x9B1E86),
                        ],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                )
                // Tracking appends a trailing gap; nudge back to true optical centre.
                .padding(.leading, tracking * scale)
                .neonGlow(Theme.cyan, radius: 15 * scale, intensity: 0.75)
                .neonGlow(Theme.magenta, radius: 22 * scale, intensity: 0.65)

            kicker
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Vexara")
    }

    private var kicker: some View {
        HStack(spacing: 9 * scale) {
            rule(flipped: false)

            Text("NEON VOID ASSAULT")
                .font(Theme.label(11 * scale))
                .tracking(4 * scale)
                .lineLimit(1)
                .foregroundStyle(Theme.textSecondary)

            rule(flipped: true)
        }
        .padding(.leading, 4 * scale)
    }

    private func rule(flipped: Bool) -> some View {
        LinearGradient(
            colors: flipped
                ? [Theme.magenta.opacity(0.75), Theme.magenta.opacity(0)]
                : [Theme.cyan.opacity(0), Theme.cyan.opacity(0.75)],
            startPoint: .leading,
            endPoint: .trailing
        )
        .frame(width: 34 * scale, height: 1)
    }
}

/// Elevated glass-ish panel used by summary and menu surfaces.
struct NeonPanel<Content: View>: View {
    var accent: Color = Theme.cyan
    var content: Content

    init(accent: Color = Theme.cyan, @ViewBuilder content: () -> Content) {
        self.accent = accent
        self.content = content()
    }

    var body: some View {
        content
            .background(
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .fill(Theme.panel.opacity(0.72))
                    .overlay(
                        RoundedRectangle(cornerRadius: 18, style: .continuous)
                            .strokeBorder(accent.opacity(0.4), lineWidth: 1)
                    )
            )
            .shadow(color: accent.opacity(0.22), radius: 18, y: 6)
    }
}

/// Small circular control used for pause and dismiss affordances.
struct CircleIconButton: View {
    var systemName: String
    var accent: Color = Theme.textPrimary
    var action: () -> Void

    var body: some View {
        Button {
            Haptics.tap()
            AudioEngine.shared.play(.uiTap)
            action()
        } label: {
            Image(systemName: systemName)
                .font(.system(size: 16, weight: .black))
                .foregroundStyle(accent)
                .frame(width: 44, height: 44)
                .background(
                    Circle()
                        .fill(Theme.voidDeep.opacity(0.66))
                        .overlay(Circle().strokeBorder(accent.opacity(0.35), lineWidth: 1))
                )
        }
        .buttonStyle(.plain)
    }
}

/// Animated starfield used behind every non-gameplay surface.
struct MenuBackdrop: View {
    var imageName: String = "space_nebula_bg"
    var dim: Double = 0.34

    @State private var drift: CGFloat = 0

    var body: some View {
        GeometryReader { proxy in
            ZStack {
                Theme.voidBase

                Image(imageName)
                    .resizable()
                    .scaledToFill()
                    .frame(width: proxy.size.width, height: proxy.size.height)
                    .offset(y: drift)
                    .clipped()
                    .allowsHitTesting(false)

                LinearGradient(
                    colors: [
                        Theme.voidDeep.opacity(0.2),
                        Theme.voidDeep.opacity(dim + 0.2),
                        Theme.voidDeep.opacity(dim + 0.5)
                    ],
                    startPoint: .top,
                    endPoint: .bottom
                )
            }
            .ignoresSafeArea()
            .onAppear {
                withAnimation(.linear(duration: 26).repeatForever(autoreverses: true)) {
                    drift = -26
                }
            }
        }
        .ignoresSafeArea()
    }
}

/// Formats a score with fixed-width arcade zero padding.
func arcadeScore(_ value: Int, digits: Int = 6) -> String {
    String(format: "%0\(digits)d", min(value, 9_999_999))
}
