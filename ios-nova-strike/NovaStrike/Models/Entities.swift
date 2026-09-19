import SwiftUI

struct PlayerShip {
    var position: CGPoint = .zero
    var velocityX: Double = 0
    var alive: Bool = true
    var invulnerable: Double = 0
    var respawnTimer: Double = 0
    var shielded: Bool = false
    var fireCooldown: Double = 0
    var tilt: Double = 0

    var rapidTimer: Double = 0
    var plasmaTimer: Double = 0
    var multiShot: Int = 1
    var multiTimer: Double = 0
    var wingmateTimer: Double = 0

    /// Progress (0-1) of a warden siphon currently locked onto the ship.
    var siphonProgress: Double = 0

    var radius: CGFloat { 15 }
}

enum EnemyState: Equatable {
    case entering
    case formation
    case diving
    case returning
    case streaming
    case siphonDescend
    case siphonBeam
    case siphonRetreat
}

struct Enemy: Identifiable {
    let id: Int
    var kind: EnemyKind
    var slot: Int
    var position: CGPoint
    var health: Int
    var maxHealth: Int
    var state: EnemyState
    var t: Double = 0
    var pathSpeed: Double = 0.45
    var entry: EntryPath
    var diveStart: CGPoint = .zero
    var diveTarget: CGPoint = .zero
    var pattern: DivePattern = .swoop
    var challenge: ChallengePath?
    var side: CGFloat = 1
    var rotation: Double = 0
    var fireTimer: Double = 1.2
    var hitFlash: Double = 0
    var spawnFade: Double = 0
    /// True while this warden is carrying the player's captured ship.
    var holdsCapturedShip: Bool = false
    var returnFrom: CGPoint = .zero

    var isDiving: Bool {
        state == .diving || state == .siphonDescend || state == .siphonBeam
    }
}

enum BulletKind {
    case pulse
    case plasma
    case wing
    case enemyBolt
    case enemyBomb
    case enemyShard

    var isPlayer: Bool {
        switch self {
        case .pulse, .plasma, .wing: true
        case .enemyBolt, .enemyBomb, .enemyShard: false
        }
    }

    var color: Color {
        switch self {
        case .pulse, .wing: Theme.cyan
        case .plasma: Theme.magenta
        case .enemyBolt: Theme.ember
        case .enemyBomb: Theme.amber
        case .enemyShard: Theme.danger
        }
    }

    var length: CGFloat {
        switch self {
        case .pulse, .wing: 16
        case .plasma: 22
        case .enemyBolt: 14
        case .enemyBomb: 11
        case .enemyShard: 10
        }
    }

    var width: CGFloat {
        switch self {
        case .pulse, .wing: 3.5
        case .plasma: 6
        case .enemyBolt: 4
        case .enemyBomb: 9
        case .enemyShard: 4
        }
    }
}

struct Bullet: Identifiable {
    let id: Int
    var position: CGPoint
    var velocity: CGVector
    var kind: BulletKind
    var damage: Int = 1
    var life: Double = 0
    var piercing: Bool = false

    var radius: CGFloat { kind.width * 0.9 }
}

enum ParticleKind {
    case spark
    case ember
    case ring
    case shard
}

struct Particle {
    var position: CGPoint
    var velocity: CGVector
    var life: Double
    var maxLife: Double
    var size: CGFloat
    var color: Color
    var kind: ParticleKind
    var drag: Double = 1.9
}

struct FloatingText: Identifiable {
    let id: Int
    var position: CGPoint
    var text: String
    var color: Color
    var life: Double
    var maxLife: Double
    var fontSize: CGFloat
    var rise: CGFloat
}

struct PowerUpDrop: Identifiable {
    let id: Int
    var position: CGPoint
    var velocity: CGVector
    var kind: PowerUpKind
    var life: Double = 0
    var spin: Double = 0
}

struct BossCore {
    var offset: CGPoint
    var radius: CGFloat
    var flash: Double = 0
}

struct BossBeam {
    /// Angle in radians, measured from straight down.
    var angle: Double
    var charge: Double
    var active: Double
    var width: CGFloat
    var sweep: Double
}

struct HivePod: Identifiable {
    let id: Int
    var angle: Double
    var radius: CGFloat
    var health: Int
    var maxHealth: Int
    var fireTimer: Double
    var hitFlash: Double = 0
    var detached: Bool = false
}

struct Boss {
    var kind: BossKind
    var position: CGPoint
    var health: Int
    var maxHealth: Int
    var width: CGFloat
    var entering: Bool = true
    var entryProgress: Double = 0
    var driftPhase: Double = 0
    var attackTimer: Double = 2.2
    var spawnTimer: Double = 5
    var hitFlash: Double = 0
    var cores: [BossCore] = []
    var beam: BossBeam?
    var pods: [HivePod] = []
    var volleyIndex: Int = 0
    var deathTimer: Double = 0
    var dying: Bool = false

    var phase: Int {
        let ratio = Double(health) / Double(max(1, maxHealth))
        if ratio > 0.66 { return 0 }
        if ratio > 0.33 { return 1 }
        return 2
    }

    var healthFraction: Double {
        Double(max(0, health)) / Double(max(1, maxHealth))
    }

    var radius: CGFloat { width * 0.34 }
}

struct Star {
    var position: CGPoint
    var depth: Double
    var size: CGFloat
    var twinkle: Double
    var color: Color
}

struct Debris {
    var position: CGPoint
    var depth: Double
    var size: CGFloat
    var spin: Double
    var rotation: Double
}
