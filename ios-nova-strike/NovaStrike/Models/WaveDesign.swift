import SwiftUI

/// A single grid row in a formation blueprint.
struct FormationRow {
    var kind: EnemyKind
    var count: Int
}

enum WaveStyle: Equatable {
    case formation
    case challenge
    case boss(BossKind)
}

/// Everything needed to build one wave.
struct WavePlan {
    var index: Int
    var style: WaveStyle
    var rows: [FormationRow]
    var challengeRuns: [ChallengeRun]
    var title: String
    var subtitle: String

    var isBoss: Bool {
        if case .boss = style { return true }
        return false
    }

    var isChallenge: Bool { style == .challenge }

    var totalEnemies: Int {
        switch style {
        case .challenge: challengeRuns.reduce(0) { $0 + $1.count }
        default: rows.reduce(0) { $0 + $1.count }
        }
    }
}

/// One squadron pass in a challenge wave.
struct ChallengeRun {
    var kind: EnemyKind
    var path: ChallengePath
    var count: Int
    var delay: Double
    var spacing: Double
    var side: CGFloat
}

enum WaveDesigner {
    static let columns = 8
    static let bossEvery = 5
    static let challengeEvery = 4

    /// Builds the plan for a given wave number. Waves loop their structure but keep
    /// scaling density and speed, so the run can continue indefinitely.
    static func plan(for wave: Int, difficulty: Difficulty) -> WavePlan {
        if wave % bossEvery == 0 {
            let bosses = BossKind.allCases
            let boss = bosses[((wave / bossEvery) - 1) % bosses.count]
            return WavePlan(
                index: wave,
                style: .boss(boss),
                rows: [],
                challengeRuns: [],
                title: "WARNING",
                subtitle: boss.displayName
            )
        }

        if wave % challengeEvery == 0 {
            return WavePlan(
                index: wave,
                style: .challenge,
                rows: [],
                challengeRuns: challengeRuns(for: wave),
                title: "CHALLENGE WAVE",
                subtitle: "NO ENEMY FIRE · CLEAR THEM ALL"
            )
        }

        return WavePlan(
            index: wave,
            style: .formation,
            rows: formationRows(for: wave, difficulty: difficulty),
            challengeRuns: [],
            title: "WAVE \(wave)",
            subtitle: formationSubtitle(for: wave)
        )
    }

    private static func formationSubtitle(for wave: Int) -> String {
        switch wave {
        case 1: "INCOMING FORMATION"
        case 2: "THEY DIVE NOW"
        case 3: "MIXED SQUADRONS"
        case 6: "ARMOURED ESCORT"
        case 7: "HUNTERS ON THE GRID"
        default: wave >= 11 ? "DEEP SPACE ASSAULT" : "HOLD THE LINE"
        }
    }

    /// Formation blueprints ramp in complexity, then cycle with added density.
    private static func formationRows(for wave: Int, difficulty: Difficulty) -> [FormationRow] {
        let cycle = ((wave - 1) % 10) + 1
        var rows: [FormationRow]

        switch cycle {
        case 1:
            rows = [
                FormationRow(kind: .swarmer, count: 6),
                FormationRow(kind: .swarmer, count: 8)
            ]
        case 2:
            rows = [
                FormationRow(kind: .darter, count: 6),
                FormationRow(kind: .swarmer, count: 8),
                FormationRow(kind: .swarmer, count: 8)
            ]
        case 3:
            rows = [
                FormationRow(kind: .bomber, count: 4),
                FormationRow(kind: .darter, count: 8),
                FormationRow(kind: .swarmer, count: 8)
            ]
        case 5:
            rows = [
                FormationRow(kind: .guardian, count: 4),
                FormationRow(kind: .bomber, count: 6),
                FormationRow(kind: .swarmer, count: 8)
            ]
        case 6:
            rows = [
                FormationRow(kind: .guardian, count: 4),
                FormationRow(kind: .hunter, count: 6),
                FormationRow(kind: .darter, count: 8),
                FormationRow(kind: .swarmer, count: 8)
            ]
        case 7:
            rows = [
                FormationRow(kind: .warden, count: 2),
                FormationRow(kind: .hunter, count: 6),
                FormationRow(kind: .bomber, count: 6),
                FormationRow(kind: .swarmer, count: 8)
            ]
        case 9:
            rows = [
                FormationRow(kind: .warden, count: 2),
                FormationRow(kind: .guardian, count: 6),
                FormationRow(kind: .hunter, count: 8),
                FormationRow(kind: .darter, count: 8)
            ]
        default:
            rows = [
                FormationRow(kind: .bomber, count: 6),
                FormationRow(kind: .darter, count: 8),
                FormationRow(kind: .swarmer, count: 8)
            ]
        }

        // Later loops thicken the grid and upgrade the leading row.
        let loop = (wave - 1) / 10
        if loop >= 1 {
            rows.insert(FormationRow(kind: .guardian, count: 4), at: 0)
        }
        if loop >= 2 {
            rows.insert(FormationRow(kind: .warden, count: 2), at: 0)
        }

        let extra = difficulty.extraRows
        if extra > 0 {
            rows.append(FormationRow(kind: .swarmer, count: 8))
        } else if extra < 0, rows.count > 2 {
            rows.removeLast()
        }

        return rows
    }

    /// Bonus-wave squadrons flying elaborate routes without firing.
    private static func challengeRuns(for wave: Int) -> [ChallengeRun] {
        let variant = ((wave / challengeEvery) - 1) % 3

        switch variant {
        case 0:
            return [
                ChallengeRun(kind: .swarmer, path: .figureEight, count: 8, delay: 0.0, spacing: 0.26, side: 1),
                ChallengeRun(kind: .swarmer, path: .figureEight, count: 8, delay: 1.6, spacing: 0.26, side: -1),
                ChallengeRun(kind: .darter, path: .crossDown, count: 6, delay: 4.4, spacing: 0.3, side: 1),
                ChallengeRun(kind: .darter, path: .crossDown, count: 6, delay: 5.2, spacing: 0.3, side: -1)
            ]
        case 1:
            return [
                ChallengeRun(kind: .darter, path: .doubleLoop, count: 10, delay: 0.0, spacing: 0.24, side: 1),
                ChallengeRun(kind: .swarmer, path: .serpentine, count: 8, delay: 3.0, spacing: 0.22, side: -1),
                ChallengeRun(kind: .swarmer, path: .serpentine, count: 8, delay: 4.0, spacing: 0.22, side: 1),
                ChallengeRun(kind: .bomber, path: .bloom, count: 6, delay: 7.0, spacing: 0.34, side: 1)
            ]
        default:
            return [
                ChallengeRun(kind: .swarmer, path: .bloom, count: 10, delay: 0.0, spacing: 0.2, side: 1),
                ChallengeRun(kind: .hunter, path: .figureEight, count: 6, delay: 3.2, spacing: 0.3, side: -1),
                ChallengeRun(kind: .darter, path: .doubleLoop, count: 8, delay: 5.6, spacing: 0.24, side: 1),
                ChallengeRun(kind: .darter, path: .crossDown, count: 6, delay: 8.4, spacing: 0.26, side: -1)
            ]
        }
    }

    /// Enemy aggression scales with depth but saturates so it never becomes unfair.
    static func aggression(for wave: Int) -> Double {
        min(2.4, 1.0 + Double(wave - 1) * 0.11)
    }

    static func speedRamp(for wave: Int) -> Double {
        min(1.75, 1.0 + Double(wave - 1) * 0.045)
    }
}
