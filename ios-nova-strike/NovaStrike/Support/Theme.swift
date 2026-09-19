import SwiftUI

extension Color {
    /// Creates a color from a packed 24-bit RGB value, e.g. `Color(hex: 0xFF2FD4)`.
    nonisolated init(hex: UInt32, opacity: Double = 1) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: opacity
        )
    }
}

/// Central visual tokens for the neon synthwave identity.
enum Theme {
    static let voidDeep = Color(hex: 0x0B0518)
    static let voidBase = Color(hex: 0x160A2E)
    static let panel = Color(hex: 0x1F0F3D)

    static let cyan = Color(hex: 0x2FE8FF)
    static let magenta = Color(hex: 0xFF2FD4)
    static let ember = Color(hex: 0xFF7A1A)
    static let amber = Color(hex: 0xFFB020)
    static let violet = Color(hex: 0xB983FF)
    static let acid = Color(hex: 0x4DFFB0)
    static let danger = Color(hex: 0xFF3B5C)

    static let textPrimary = Color(hex: 0xF5F0FF)
    static let textSecondary = Color(hex: 0xB9A6D9)

    static func display(_ size: CGFloat) -> Font {
        .system(size: size, weight: .black, design: .rounded)
    }

    static func label(_ size: CGFloat) -> Font {
        .system(size: size, weight: .heavy, design: .rounded)
    }

    static func numeral(_ size: CGFloat) -> Font {
        .system(size: size, weight: .bold, design: .monospaced)
    }

    /// Standard playfield aspect. Wide screens letterbox to keep an arcade-shaped battlefield.
    static let playfieldAspect: CGFloat = 0.58
}

extension View {
    /// Layered neon glow used across headings and HUD numerals.
    func neonGlow(_ color: Color, radius: CGFloat = 10, intensity: Double = 1) -> some View {
        self
            .shadow(color: color.opacity(0.85 * intensity), radius: radius * 0.35)
            .shadow(color: color.opacity(0.55 * intensity), radius: radius)
            .shadow(color: color.opacity(0.3 * intensity), radius: radius * 2.2)
    }
}
