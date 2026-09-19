import SwiftUI

nonisolated struct LeaderboardEntry: Codable, Identifiable, Equatable, Sendable {
    var id: UUID = UUID()
    var name: String
    var score: Int
    var wave: Int
    var difficulty: Difficulty
    var date: Date
}

nonisolated struct PlayerStats: Codable, Equatable, Sendable {
    var totalKills: Int = 0
    var bossKills: Int = 0
    var rescues: Int = 0
    var perfectWaves: Int = 0
    var bestWave: Int = 0
    var bestScore: Int = 0
    var runsPlayed: Int = 0
    var fastestFormationClear: Double = .greatestFiniteMagnitude
}

enum Achievement: String, CaseIterable, Identifiable, Sendable {
    case firstBlood
    case alienHunter
    case formationBreaker
    case bossSlayer
    case perfectWave
    case highRoller
    case voidRescue
    case deepRun

    var id: String { rawValue }

    var title: String {
        switch self {
        case .firstBlood: "FIRST BLOOD"
        case .alienHunter: "ALIEN HUNTER"
        case .formationBreaker: "FORMATION BREAKER"
        case .bossSlayer: "BOSS SLAYER"
        case .perfectWave: "PERFECT WAVE"
        case .highRoller: "HIGH ROLLER"
        case .voidRescue: "VOID RESCUE"
        case .deepRun: "DEEP RUN"
        }
    }

    var detail: String {
        switch self {
        case .firstBlood: "Destroy your first enemy."
        case .alienHunter: "Destroy 100 enemies across all runs."
        case .formationBreaker: "Clear a full formation in under 25 seconds."
        case .bossSlayer: "Defeat your first boss."
        case .perfectWave: "Complete a wave without being hit."
        case .highRoller: "Reach 100,000 points in a single run."
        case .voidRescue: "Recover a ship captured by a Siphon Warden."
        case .deepRun: "Reach wave 10."
        }
    }

    var symbol: String {
        switch self {
        case .firstBlood: "scope"
        case .alienHunter: "target"
        case .formationBreaker: "square.grid.3x3.fill"
        case .bossSlayer: "crown.fill"
        case .perfectWave: "sparkles"
        case .highRoller: "chart.line.uptrend.xyaxis"
        case .voidRescue: "arrow.uturn.up.circle.fill"
        case .deepRun: "flame.fill"
        }
    }

    var accent: Color {
        switch self {
        case .firstBlood: Theme.cyan
        case .alienHunter: Theme.magenta
        case .formationBreaker: Theme.violet
        case .bossSlayer: Theme.amber
        case .perfectWave: Theme.acid
        case .highRoller: Theme.amber
        case .voidRescue: Theme.cyan
        case .deepRun: Theme.ember
        }
    }
}

struct WaveSummary: Equatable {
    var wave: Int
    var enemiesCleared: Int
    var clearPoints: Int
    var perfect: Bool
    var perfectBonus: Int
    var comboBonus: Int
    var total: Int
    var isChallenge: Bool
    var challengeFullClear: Bool
    var nextIsBoss: Bool
}

struct GameOverSummary: Equatable {
    var score: Int
    var wave: Int
    var kills: Int
    var bestCombo: Int
    var isNewHighScore: Bool
    var qualifiesForLeaderboard: Bool
}

struct PowerUpBadge: Equatable, Identifiable {
    var kind: PowerUpKind
    var secondsLeft: Int
    var id: String { kind.rawValue }
}

struct BossHUDState: Equatable {
    var name: String
    var fraction: Double
    var phase: Int
    var accentHex: UInt32

    var accent: Color { Color(hex: accentHex) }
}

struct AchievementToast: Equatable, Identifiable {
    let id: UUID
    let achievement: Achievement
}
