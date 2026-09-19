import Foundation
import Observation

/// Durable player progress: high scores, the local leaderboard, lifetime stats,
/// achievements and settings. Structured so a remote leaderboard can sync later.
@Observable
final class ProgressStore {
    private enum Key {
        static let highScore = "ns.highScore"
        static let leaderboard = "ns.leaderboard"
        static let stats = "ns.stats"
        static let achievements = "ns.achievements"
        static let difficulty = "ns.difficulty"
        static let autoFire = "ns.autoFire"
        static let muted = "ns.muted"
        static let haptics = "ns.haptics"
        static let lastName = "ns.lastName"
    }

    static let leaderboardCapacity = 5

    private let defaults: UserDefaults

    private(set) var highScore: Int
    private(set) var leaderboard: [LeaderboardEntry]
    private(set) var stats: PlayerStats
    private(set) var unlocked: Set<String>

    var difficulty: Difficulty {
        didSet { defaults.set(difficulty.rawValue, forKey: Key.difficulty) }
    }

    var autoFire: Bool {
        didSet { defaults.set(autoFire, forKey: Key.autoFire) }
    }

    var isMuted: Bool {
        didSet {
            defaults.set(isMuted, forKey: Key.muted)
            AudioEngine.shared.isMuted = isMuted
        }
    }

    var hapticsEnabled: Bool {
        didSet {
            defaults.set(hapticsEnabled, forKey: Key.haptics)
            Haptics.enabled = hapticsEnabled
        }
    }

    var playerName: String {
        didSet { defaults.set(playerName, forKey: Key.lastName) }
    }

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        highScore = defaults.integer(forKey: Key.highScore)

        if let data = defaults.data(forKey: Key.leaderboard),
           let decoded = try? JSONDecoder().decode([LeaderboardEntry].self, from: data) {
            // Truncates boards persisted before the capacity change.
            leaderboard = Array(decoded.prefix(Self.leaderboardCapacity))
        } else {
            leaderboard = []
        }

        if let data = defaults.data(forKey: Key.stats),
           let decoded = try? JSONDecoder().decode(PlayerStats.self, from: data) {
            stats = decoded
        } else {
            stats = PlayerStats()
        }

        unlocked = Set(defaults.stringArray(forKey: Key.achievements) ?? [])

        if let raw = defaults.string(forKey: Key.difficulty), let value = Difficulty(rawValue: raw) {
            difficulty = value
        } else {
            difficulty = .normal
        }

        autoFire = defaults.object(forKey: Key.autoFire) as? Bool ?? true
        isMuted = defaults.bool(forKey: Key.muted)
        hapticsEnabled = defaults.object(forKey: Key.haptics) as? Bool ?? true
        playerName = defaults.string(forKey: Key.lastName) ?? "ACE"

        AudioEngine.shared.isMuted = isMuted
        Haptics.enabled = hapticsEnabled
    }

    // MARK: - Scores

    func isHighScore(_ score: Int) -> Bool {
        score > highScore && score > 0
    }

    func qualifiesForLeaderboard(_ score: Int) -> Bool {
        guard score > 0 else { return false }
        if leaderboard.count < Self.leaderboardCapacity { return true }
        return leaderboard.contains { score > $0.score }
    }

    func submit(name: String, score: Int, wave: Int, difficulty: Difficulty) {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        let clean = trimmed.isEmpty ? "ACE" : String(trimmed.prefix(8)).uppercased()
        playerName = clean

        let entry = LeaderboardEntry(name: clean, score: score, wave: wave, difficulty: difficulty, date: Date())
        leaderboard.append(entry)
        leaderboard.sort { lhs, rhs in
            lhs.score == rhs.score ? lhs.date < rhs.date : lhs.score > rhs.score
        }
        if leaderboard.count > Self.leaderboardCapacity {
            leaderboard = Array(leaderboard.prefix(Self.leaderboardCapacity))
        }
        persistLeaderboard()
    }

    func recordHighScore(_ score: Int) {
        guard score > highScore else { return }
        highScore = score
        defaults.set(score, forKey: Key.highScore)
    }

    // MARK: - Stats

    /// Folds one finished run into lifetime statistics.
    func recordRun(score: Int, wave: Int, kills: Int, bossKills: Int, rescues: Int, perfectWaves: Int) {
        stats.runsPlayed += 1
        stats.totalKills += kills
        stats.bossKills += bossKills
        stats.rescues += rescues
        stats.perfectWaves += perfectWaves
        stats.bestWave = max(stats.bestWave, wave)
        stats.bestScore = max(stats.bestScore, score)
        persistStats()
    }

    func recordFormationClear(seconds: Double) {
        guard seconds < stats.fastestFormationClear else { return }
        stats.fastestFormationClear = seconds
        persistStats()
    }

    // MARK: - Achievements

    func isUnlocked(_ achievement: Achievement) -> Bool {
        unlocked.contains(achievement.rawValue)
    }

    /// Unlocks an achievement, returning true only the first time it fires.
    @discardableResult
    func unlock(_ achievement: Achievement) -> Bool {
        guard !unlocked.contains(achievement.rawValue) else { return false }
        unlocked.insert(achievement.rawValue)
        defaults.set(Array(unlocked), forKey: Key.achievements)
        return true
    }

    // MARK: - Persistence

    private func persistLeaderboard() {
        guard let data = try? JSONEncoder().encode(leaderboard) else { return }
        defaults.set(data, forKey: Key.leaderboard)
    }

    private func persistStats() {
        guard let data = try? JSONEncoder().encode(stats) else { return }
        defaults.set(data, forKey: Key.stats)
    }
}
