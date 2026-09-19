import SwiftUI

/// Top-level flow state of a run. Menus outside a run are presented from the title screen.
enum GamePhase: Equatable {
    case title
    case playing
    case paused
    case gameOver
}

nonisolated enum Difficulty: String, CaseIterable, Codable, Identifiable, Sendable {
    case easy, normal, hard, arcade

    var id: String { rawValue }

    var title: String {
        switch self {
        case .easy: "EASY"
        case .normal: "NORMAL"
        case .hard: "HARD"
        case .arcade: "ARCADE"
        }
    }

    var blurb: String {
        switch self {
        case .easy: "Slower aliens, forgiving fire. Learn the patterns."
        case .normal: "The intended arcade balance."
        case .hard: "Faster dives, denser fire, tougher bosses."
        case .arcade: "Full cabinet cruelty. Two lives, relentless waves."
        }
    }

    var accent: Color {
        switch self {
        case .easy: Color(hex: 0x4DFFB0)
        case .normal: Color(hex: 0x2FE8FF)
        case .hard: Color(hex: 0xFFB020)
        case .arcade: Color(hex: 0xFF2FD4)
        }
    }

    var startingLives: Int {
        switch self {
        case .easy: 5
        case .normal: 3
        case .hard: 3
        case .arcade: 2
        }
    }

    /// Multiplier on enemy travel speeds.
    var speedScale: Double {
        switch self {
        case .easy: 0.78
        case .normal: 1.0
        case .hard: 1.18
        case .arcade: 1.34
        }
    }

    /// Multiplier on how often enemies shoot (higher = more shots).
    var fireScale: Double {
        switch self {
        case .easy: 0.55
        case .normal: 1.0
        case .hard: 1.45
        case .arcade: 1.85
        }
    }

    /// Multiplier on dive scheduling frequency.
    var diveScale: Double {
        switch self {
        case .easy: 0.6
        case .normal: 1.0
        case .hard: 1.4
        case .arcade: 1.8
        }
    }

    /// Extra formation rows beyond the standard progression.
    var extraRows: Int {
        switch self {
        case .easy: -1
        case .normal: 0
        case .hard: 0
        case .arcade: 1
        }
    }

    var bossHealthScale: Double {
        switch self {
        case .easy: 0.7
        case .normal: 1.0
        case .hard: 1.3
        case .arcade: 1.55
        }
    }

    var scoreScale: Double {
        switch self {
        case .easy: 0.7
        case .normal: 1.0
        case .hard: 1.35
        case .arcade: 1.7
        }
    }
}

enum EnemyKind: String, CaseIterable, Identifiable, Sendable {
    case swarmer, darter, bomber, guardian, hunter, warden

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .swarmer: "SWARMER"
        case .darter: "DARTER"
        case .bomber: "BOMBER"
        case .guardian: "GUARDIAN"
        case .hunter: "HUNTER"
        case .warden: "SIPHON WARDEN"
        }
    }

    var sprite: String {
        switch self {
        case .swarmer: "insectoid_fighter_craft"
        case .darter: "alien_interceptor_sprite"
        case .bomber: "alien_bomber_sprite"
        case .guardian: "alien_warship_sprite"
        case .hunter: "alien_manta_craft"
        case .warden: "alien_station_boss"
        }
    }

    var health: Int {
        switch self {
        case .swarmer: 1
        case .darter: 1
        case .bomber: 2
        case .guardian: 4
        case .hunter: 2
        case .warden: 6
        }
    }

    var points: Int {
        switch self {
        case .swarmer: 100
        case .darter: 200
        case .bomber: 300
        case .guardian: 500
        case .hunter: 400
        case .warden: 800
        }
    }

    var radius: CGFloat {
        switch self {
        case .swarmer: 16
        case .darter: 15
        case .bomber: 19
        case .guardian: 20
        case .hunter: 18
        case .warden: 26
        }
    }

    var accent: Color {
        switch self {
        case .swarmer: Theme.magenta
        case .darter: Theme.ember
        case .bomber: Theme.amber
        case .guardian: Theme.violet
        case .hunter: Theme.acid
        case .warden: Color(hex: 0xFF5CE1)
        }
    }

    var dossier: String {
        switch self {
        case .swarmer: "Cheap chitin fighters. They hold the grid and dive in packs."
        case .darter: "Breaks formation instantly and knifes straight at your lane."
        case .bomber: "Lobs slow arcing ordnance that tracks where you were."
        case .guardian: "Layered plating. Needs four clean hits before it cracks."
        case .hunter: "Reads your drift and intercepts ahead of your movement."
        case .warden: "Deploys a prism siphon. Linger in the beam and it takes your ship."
        }
    }
}

enum BossKind: String, CaseIterable, Identifiable, Sendable {
    case voidQueen, hiveCore, starDevourer

    var id: String { rawValue }

    var displayName: String {
        switch self {
        case .voidQueen: "THE VOID QUEEN"
        case .hiveCore: "THE HIVE CORE"
        case .starDevourer: "THE STAR DEVOURER"
        }
    }

    var sprite: String {
        switch self {
        case .voidQueen: "alien_mothership_boss"
        case .hiveCore: "alien_station_boss"
        case .starDevourer: "alien_leviathan_boss"
        }
    }

    var baseHealth: Int {
        switch self {
        case .voidQueen: 220
        case .hiveCore: 280
        case .starDevourer: 340
        }
    }

    var accent: Color {
        switch self {
        case .voidQueen: Theme.magenta
        case .hiveCore: Theme.amber
        case .starDevourer: Color(hex: 0x6FD9FF)
        }
    }

    var widthFraction: CGFloat {
        switch self {
        case .voidQueen: 0.78
        case .hiveCore: 0.66
        case .starDevourer: 0.86
        }
    }

    var points: Int {
        switch self {
        case .voidQueen: 2000
        case .hiveCore: 3000
        case .starDevourer: 4000
        }
    }
}

enum PowerUpKind: String, CaseIterable, Identifiable, Sendable {
    case rapidFire, doubleShot, tripleShot, plasma, shield, blast, slowTime

    var id: String { rawValue }

    var title: String {
        switch self {
        case .rapidFire: "RAPID FIRE"
        case .doubleShot: "DOUBLE SHOT"
        case .tripleShot: "TRIPLE SHOT"
        case .plasma: "PLASMA"
        case .shield: "SHIELD"
        case .blast: "BLAST"
        case .slowTime: "SLOW TIME"
        }
    }

    var detail: String {
        switch self {
        case .rapidFire: "Cuts your reload to a stutter for 12 seconds."
        case .doubleShot: "Two parallel bolts for 15 seconds."
        case .tripleShot: "A three-way spread for 15 seconds."
        case .plasma: "Heavier bolts that punch through armour for 14 seconds."
        case .shield: "A prism barrier that eats exactly one hit."
        case .blast: "Stores a charge. Fire it to vaporise everything nearby."
        case .slowTime: "Drags enemy movement to half speed for 8 seconds."
        }
    }

    var symbol: String {
        switch self {
        case .rapidFire: "bolt.fill"
        case .doubleShot: "equal"
        case .tripleShot: "chevron.up.2"
        case .plasma: "flame.fill"
        case .shield: "shield.lefthalf.filled"
        case .blast: "burst.fill"
        case .slowTime: "hourglass"
        }
    }

    var accent: Color {
        switch self {
        case .rapidFire: Theme.amber
        case .doubleShot: Theme.cyan
        case .tripleShot: Theme.cyan
        case .plasma: Theme.magenta
        case .shield: Theme.acid
        case .blast: Theme.ember
        case .slowTime: Theme.violet
        }
    }

    var duration: Double {
        switch self {
        case .rapidFire: 12
        case .doubleShot: 15
        case .tripleShot: 15
        case .plasma: 14
        case .shield: 0
        case .blast: 0
        case .slowTime: 8
        }
    }
}
