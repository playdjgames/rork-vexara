import AVFoundation
import Foundation

/// Named sound effects mapped to their bundled resource files.
enum SFX: String, CaseIterable {
    case shoot = "arcade_laser_pew"
    case enemyExplode = "arcade_enemy_explosion"
    case playerExplode = "arcade_ship_explosion"
    case powerUp = "arcade_power_up_sparkle"
    case bossAppear = "boss_arrival_alert"
    case waveComplete = "arcade_wave_clear_fanfare"
    case combo = "combo_blip_ascend"
    case gameOver = "arcade_game_over_sting"
    case highScore = "arcade_high_score_jingle"
    case hit = "shield_impact_tick"
    case capture = "alien_tractor_beam_capture"
    case rescue = "rescue_reunion_triumph"
    case uiTap = "arcade_menu_tap"
    case blast = "energy_bomb_explosion"

    /// How many simultaneous voices this effect needs.
    var voices: Int {
        switch self {
        case .shoot: 6
        case .enemyExplode, .hit: 5
        default: 2
        }
    }

    var gain: Float {
        switch self {
        case .shoot: 0.3
        case .hit: 0.35
        case .enemyExplode: 0.55
        case .combo: 0.45
        case .uiTap: 0.5
        case .playerExplode, .blast: 0.85
        default: 0.75
        }
    }
}

enum MusicTrack: String {
    case menu = "retro_arcade_synthwave"
    case battle = "arcade_shooter_battle"
    case boss = "dark_boss_battle_synth"
}

/// Pooled AVAudioPlayer playback for low-latency arcade sound plus crossfaded music beds.
final class AudioEngine {
    static let shared = AudioEngine()

    private var pools: [SFX: [AVAudioPlayer]] = [:]
    private var cursors: [SFX: Int] = [:]
    private var musicPlayer: AVAudioPlayer?
    private var currentTrack: MusicTrack?
    private var fadeTimer: Timer?

    var isMuted: Bool = false {
        didSet {
            musicPlayer?.volume = isMuted ? 0 : musicVolume
            if isMuted { stopAllEffects() }
        }
    }

    private let musicVolume: Float = 0.42

    private init() {
        configureSession()
    }

    private func configureSession() {
        do {
            try AVAudioSession.sharedInstance().setCategory(.ambient, mode: .default, options: [.mixWithOthers])
            try AVAudioSession.sharedInstance().setActive(true)
        } catch {
            print("[audio] session setup failed: \(error.localizedDescription)")
        }
    }

    /// Loads every effect into memory so the first shot never stutters.
    func preload() {
        guard pools.isEmpty else { return }
        for effect in SFX.allCases {
            guard let url = Bundle.main.url(forResource: effect.rawValue, withExtension: "mp3") else {
                continue
            }
            var players: [AVAudioPlayer] = []
            for _ in 0..<effect.voices {
                if let player = try? AVAudioPlayer(contentsOf: url) {
                    player.volume = effect.gain
                    player.prepareToPlay()
                    players.append(player)
                }
            }
            if !players.isEmpty {
                pools[effect] = players
                cursors[effect] = 0
            }
        }
    }

    func play(_ effect: SFX, rateVariation: Bool = false) {
        guard !isMuted, let players = pools[effect], !players.isEmpty else { return }
        let index = (cursors[effect] ?? 0) % players.count
        cursors[effect] = index + 1
        let player = players[index]
        player.currentTime = 0
        if rateVariation {
            player.enableRate = true
            player.rate = Float.random(in: 0.94...1.08)
        }
        player.play()
    }

    private func stopAllEffects() {
        for players in pools.values {
            for player in players where player.isPlaying {
                player.stop()
            }
        }
    }

    func playMusic(_ track: MusicTrack) {
        guard currentTrack != track else { return }
        currentTrack = track
        fadeTimer?.invalidate()

        guard let url = Bundle.main.url(forResource: track.rawValue, withExtension: "mp3") else { return }
        let outgoing = musicPlayer

        guard let player = try? AVAudioPlayer(contentsOf: url) else { return }
        player.numberOfLoops = -1
        player.volume = 0
        player.prepareToPlay()
        player.play()
        musicPlayer = player

        let target = isMuted ? 0 : musicVolume
        var progress: Float = 0
        fadeTimer = Timer.scheduledTimer(withTimeInterval: 0.05, repeats: true) { [weak player, weak outgoing] timer in
            progress += 0.05 / 0.9
            let clamped = min(1, progress)
            player?.volume = target * clamped
            outgoing?.volume = target * (1 - clamped)
            if clamped >= 1 {
                outgoing?.stop()
                timer.invalidate()
            }
        }
    }

    func stopMusic() {
        fadeTimer?.invalidate()
        musicPlayer?.stop()
        musicPlayer = nil
        currentTrack = nil
    }
}
