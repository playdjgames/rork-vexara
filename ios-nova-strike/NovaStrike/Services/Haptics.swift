import UIKit

/// Thin wrapper over UIFeedbackGenerator with pre-warmed generators for arcade feedback.
enum Haptics {
    private static let light = UIImpactFeedbackGenerator(style: .light)
    private static let medium = UIImpactFeedbackGenerator(style: .medium)
    private static let heavy = UIImpactFeedbackGenerator(style: .heavy)
    private static let rigid = UIImpactFeedbackGenerator(style: .rigid)
    private static let notifier = UINotificationFeedbackGenerator()

    static var enabled: Bool = true

    static func prepare() {
        light.prepare()
        medium.prepare()
        heavy.prepare()
        rigid.prepare()
    }

    static func tap() {
        guard enabled else { return }
        light.impactOccurred(intensity: 0.7)
    }

    static func kill() {
        guard enabled else { return }
        rigid.impactOccurred(intensity: 0.45)
    }

    static func hit() {
        guard enabled else { return }
        medium.impactOccurred(intensity: 0.8)
    }

    static func heavyBlast() {
        guard enabled else { return }
        heavy.impactOccurred(intensity: 1.0)
    }

    static func success() {
        guard enabled else { return }
        notifier.notificationOccurred(.success)
    }

    static func failure() {
        guard enabled else { return }
        notifier.notificationOccurred(.error)
    }
}
