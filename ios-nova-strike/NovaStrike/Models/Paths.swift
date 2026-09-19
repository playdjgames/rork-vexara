import SwiftUI

private func easeIn(_ t: Double) -> Double { t * t }
private func easeOut(_ t: Double) -> Double { 1 - (1 - t) * (1 - t) }
private func easeInOut(_ t: Double) -> Double {
    t < 0.5 ? 2 * t * t : 1 - pow(-2 * t + 2, 2) / 2
}

/// Cubic bezier flight path used when enemies swoop in from off-screen to their formation slot.
struct EntryPath {
    var p0: CGPoint
    var p1: CGPoint
    var p2: CGPoint
    var p3: CGPoint

    func point(_ t: Double) -> CGPoint {
        let u = 1 - t
        let a = u * u * u
        let b = 3 * u * u * t
        let c = 3 * u * t * t
        let d = t * t * t
        return CGPoint(
            x: a * p0.x + b * p1.x + c * p2.x + d * p3.x,
            y: a * p0.y + b * p1.y + c * p2.y + d * p3.y
        )
    }

    enum Style: CaseIterable {
        case sweepLeft
        case sweepRight
        case riseLeft
        case riseRight
        case plungeCenter
    }

    static func make(style: Style, target: CGPoint, size: CGSize) -> EntryPath {
        let w = size.width
        let h = size.height
        switch style {
        case .sweepLeft:
            return EntryPath(
                p0: CGPoint(x: -70, y: h * 0.12),
                p1: CGPoint(x: w * 0.34, y: -40),
                p2: CGPoint(x: w * 0.92, y: h * 0.42),
                p3: target
            )
        case .sweepRight:
            return EntryPath(
                p0: CGPoint(x: w + 70, y: h * 0.12),
                p1: CGPoint(x: w * 0.66, y: -40),
                p2: CGPoint(x: w * 0.08, y: h * 0.42),
                p3: target
            )
        case .riseLeft:
            return EntryPath(
                p0: CGPoint(x: w * 0.18, y: h + 80),
                p1: CGPoint(x: -60, y: h * 0.52),
                p2: CGPoint(x: w * 0.2, y: -60),
                p3: target
            )
        case .riseRight:
            return EntryPath(
                p0: CGPoint(x: w * 0.82, y: h + 80),
                p1: CGPoint(x: w + 60, y: h * 0.52),
                p2: CGPoint(x: w * 0.8, y: -60),
                p3: target
            )
        case .plungeCenter:
            return EntryPath(
                p0: CGPoint(x: w * 0.5, y: -90),
                p1: CGPoint(x: w * 0.5, y: h * 0.46),
                p2: CGPoint(x: target.x < w * 0.5 ? w * 0.06 : w * 0.94, y: h * 0.3),
                p3: target
            )
        }
    }
}

/// Attack trajectories enemies take when they break formation.
enum DivePattern: CaseIterable {
    case swoop
    case zigzag
    case spiral
    case split
    case orbit
    case plunge
    case boomerang

    /// Absolute playfield position for a dive in progress.
    func position(t: Double, start: CGPoint, target: CGPoint, size: CGSize, side: CGFloat) -> CGPoint {
        let exitY = size.height + 110
        let travel = exitY - start.y
        let s = Double(side)

        switch self {
        case .swoop:
            let x = start.x + sin(t * .pi * 1.7) * 165 * s + (target.x - start.x) * t * 0.5
            return CGPoint(x: x, y: start.y + travel * easeIn(t))

        case .zigzag:
            let x = start.x + sin(t * .pi * 7) * 58 * s + (target.x - start.x) * t * 0.55
            return CGPoint(x: x, y: start.y + travel * t)

        case .spiral:
            let r = 26 + t * 138
            let a = t * .pi * 4.2 * s
            return CGPoint(x: start.x + cos(a) * r, y: start.y + travel * easeIn(t) + sin(a) * 24)

        case .split:
            let out = min(1, t / 0.38)
            let outX = start.x + 155 * s * sin(out * .pi / 2)
            let cut = max(0, (t - 0.38) / 0.62)
            let x = outX + (target.x - outX) * easeOut(cut)
            return CGPoint(x: x, y: start.y + travel * easeIn(t))

        case .orbit:
            let a = t * .pi * 2 * s
            let r = 128.0 * (1 - t * 0.35)
            return CGPoint(x: start.x + sin(a) * r, y: start.y + travel * t + (1 - cos(a)) * 34)

        case .plunge:
            let x = start.x + (target.x - start.x) * easeOut(min(1, t * 1.6))
            return CGPoint(x: x, y: start.y + travel * easeIn(t))

        case .boomerang:
            let low = size.height * 0.78
            let y: CGFloat
            if t <= 0.55 {
                y = start.y + (low - start.y) * easeOut(t / 0.55)
            } else {
                let u = (t - 0.55) / 0.45
                y = low + (-120 - low) * easeIn(u)
            }
            let x = start.x + sin(t * .pi * 2) * 132 * s
            return CGPoint(x: x, y: y)
        }
    }

    /// Whether the dive leaves through the top of the screen instead of the bottom.
    var exitsUpward: Bool { self == .boomerang }

    var duration: Double {
        switch self {
        case .swoop: 2.5
        case .zigzag: 2.9
        case .spiral: 3.0
        case .split: 2.7
        case .orbit: 3.2
        case .plunge: 1.9
        case .boomerang: 3.1
        }
    }
}

/// Elaborate non-stop routes used by bonus challenge waves.
enum ChallengePath: CaseIterable {
    case figureEight
    case doubleLoop
    case serpentine
    case crossDown
    case bloom

    func position(t: Double, size: CGSize, side: CGFloat) -> CGPoint {
        let w = size.width
        let h = size.height
        let s = Double(side)

        switch self {
        case .figureEight:
            let a = t * .pi * 2
            let x = w * 0.5 + sin(a * 2) * w * 0.36 * s
            let y = -80 + (h + 160) * t + sin(a) * h * 0.1
            return CGPoint(x: x, y: y)

        case .doubleLoop:
            let a = t * .pi * 4 * s
            let x = w * 0.5 + cos(a) * w * 0.34
            let y = -80 + (h + 160) * easeInOut(t) + sin(a) * 40
            return CGPoint(x: x, y: y)

        case .serpentine:
            let x = w * 0.5 + sin(t * .pi * 3.2) * w * 0.42 * s
            let y = -80 + (h + 160) * t
            return CGPoint(x: x, y: y)

        case .crossDown:
            let startX = side > 0 ? -60.0 : w + 60
            let endX = side > 0 ? w + 60 : -60.0
            let x = startX + (endX - startX) * t
            let y = -60 + (h + 130) * easeInOut(t) + sin(t * .pi * 2) * 60
            return CGPoint(x: x, y: y)

        case .bloom:
            let a = t * .pi * 2.4 * s
            let r = w * 0.12 + t * w * 0.38
            let x = w * 0.5 + cos(a) * r
            let y = h * 0.26 + sin(a) * r * 0.62 + t * h * 0.5 - 60
            return CGPoint(x: x, y: y)
        }
    }

    var duration: Double {
        switch self {
        case .figureEight: 7.6
        case .doubleLoop: 8.2
        case .serpentine: 6.4
        case .crossDown: 5.8
        case .bloom: 8.6
        }
    }
}
