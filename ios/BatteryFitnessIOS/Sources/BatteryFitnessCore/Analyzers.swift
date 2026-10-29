import Foundation

// MARK: - Shared Pose & Confidence Primitives

public struct PosePoint: Equatable, Sendable {
    public let x: Double
    public let y: Double
    public let visibility: Double

    public init(x: Double, y: Double, visibility: Double = 1.0) {
        self.x = x
        self.y = y
        self.visibility = visibility
    }

    public func distance(to other: PosePoint) -> Double {
        hypot(x - other.x, y - other.y)
    }

    public static func angleDeg(_ a: PosePoint, _ b: PosePoint, _ c: PosePoint) -> Double {
        let v1x = a.x - b.x
        let v1y = a.y - b.y
        let v2x = c.x - b.x
        let v2y = c.y - b.y
        let n1 = hypot(v1x, v1y)
        let n2 = hypot(v2x, v2y)
        guard n1 > 1e-9 && n2 > 1e-9 else { return .nan }
        let cosVal = max(-1.0, min(1.0, (v1x * v2x + v1y * v2y) / (n1 * n2)))
        return acos(cosVal) * 180.0 / .pi
    }
}

public enum ConfidenceEvaluators {
    public static func bandFromSignals(detectionRate: Double, fps: Double, minFpsForGreen: Double = 29.0) -> (ConfidenceBand, [String]) {
        var reasons: [String] = []
        if detectionRate < 0.50 {
            reasons.append("Pose detected in only \(Int(detectionRate * 100))% of frames")
            return (.red, reasons)
        }
        var band: ConfidenceBand = .green
        if detectionRate < 0.85 {
            band = .amber
            reasons.append("Pose visibility \(Int(detectionRate * 100))% (< 85%)")
        }
        if fps < minFpsForGreen {
            band = .amber
            reasons.append("Frame rate \(Int(fps.rounded())) fps below target \(Int(minFpsForGreen)) fps")
        }
        return (band, reasons)
    }
}

// MARK: - Test 1: Standing Height Analyzer (±1 cm target)

public struct HeightSignalFrame: Sendable {
    public let t: Double
    public let heightCm: Double
    public let headTiltDeg: Double

    public init(t: Double, heightCm: Double, headTiltDeg: Double) {
        self.t = t
        self.heightCm = heightCm
        self.headTiltDeg = headTiltDeg
    }
}

public struct HeightMeasurementResult: Equatable, Sendable {
    public let heightCm: Double
    public let rung: Int
    public let confidence: ConfidenceBand
    public let confidenceReasons: [String]
    public let spreadCm: Double
    public let headTiltDeg: Double
    public let framesUsed: Int
}

public enum HeightAnalyzer {
    public static let maxSpreadCm: Double = 1.5
    public static let maxHeadTiltDeg: Double = 8.0

    public static func measure(
        frames: [HeightSignalFrame],
        rung: Int = 1,
        fps: Double = 30.0,
        detectionRate: Double = 1.0
    ) -> HeightMeasurementResult {
        guard frames.count >= 10 else {
            return HeightMeasurementResult(
                heightCm: 0.0, rung: 5, confidence: .red,
                confidenceReasons: ["Fewer than 10 stable pose frames"],
                spreadCm: 0.0, headTiltDeg: 0.0, framesUsed: frames.count
            )
        }
        let sortedHeights = frames.map(\.heightCm).sorted()
        let trim = max(1, sortedHeights.count / 5)
        let core = Array(sortedHeights[trim..<(sortedHeights.count - trim)])
        let median = core[core.count / 2]
        let spread = (core.last ?? median) - (core.first ?? median)
        let avgTilt = frames.map { abs($0.headTiltDeg) }.reduce(0.0, +) / Double(frames.count)

        var (band, reasons) = ConfidenceEvaluators.bandFromSignals(detectionRate: detectionRate, fps: fps)
        if rung >= 3 && band == .green {
            band = .amber
            reasons.append("Calibrated via Rung \(rung) rather than printed reference marker")
        }
        if spread > maxSpreadCm {
            band = .amber
            reasons.append(String(format: "Subject moved during capture (spread %.2f cm > %.1f cm)", spread, maxSpreadCm))
        }
        if avgTilt > maxHeadTiltDeg {
            band = .amber
            reasons.append(String(format: "Frankfurt plane tilt %.1f° > %.1f°", avgTilt, maxHeadTiltDeg))
        }

        return HeightMeasurementResult(
            heightCm: (median * 10.0).rounded() / 10.0,
            rung: rung,
            confidence: band,
            confidenceReasons: reasons,
            spreadCm: spread,
            headTiltDeg: avgTilt,
            framesUsed: core.count
        )
    }
}

// MARK: - Test 3: Sit and Reach Analyzer (±1 cm target, knee straightness & hold check)

public struct SitAndReachSignal: Sendable {
    public let t: Double
    public let reachCm: Double
    public let kneeAngleDeg: Double

    public init(t: Double, reachCm: Double, kneeAngleDeg: Double) {
        self.t = t
        self.reachCm = reachCm
        self.kneeAngleDeg = kneeAngleDeg
    }
}

public struct ReachAttemptResult: Equatable, Sendable {
    public let number: Int
    public let tStart: Double
    public let tEnd: Double
    public let reachCm: Double
    public let holdSec: Double
    public let minKneeAngleDeg: Double
    public let valid: Bool
    public let foulReason: String
}

public struct SitAndReachMeasurementResult: Equatable, Sendable {
    public let bestReachCm: Double
    public let attempts: [ReachAttemptResult]
    public let rung: Int
    public let confidence: ConfidenceBand
    public let confidenceReasons: [String]
}

public enum SitAndReachAnalyzer {
    public static let minHoldSec: Double = 0.8
    public static let minKneeStraightDeg: Double = 168.0
    public static let maxHoldSpreadCm: Double = 1.5

    public static func measure(
        signals: [SitAndReachSignal],
        rung: Int = 2,
        fps: Double = 30.0,
        detectionRate: Double = 1.0
    ) -> SitAndReachMeasurementResult {
        guard signals.count >= 10 else {
            return SitAndReachMeasurementResult(
                bestReachCm: 0.0, attempts: [], rung: 5,
                confidence: .red, confidenceReasons: ["Fewer than 10 usable pose frames"]
            )
        }
        let sortedReaches = signals.map(\.reachCm).sorted()
        let baseline = sortedReaches[Int(Double(sortedReaches.count) * 0.20)]
        let peak = sortedReaches[Int(Double(sortedReaches.count) * 0.90)]
        let span = max(2.0, peak - baseline)
        let threshold = baseline + 0.55 * span

        var attempts: [ReachAttemptResult] = []
        var window: [SitAndReachSignal] = []
        var inReach = false

        func finalizeWindow(_ w: [SitAndReachSignal]) {
            guard let first = w.first, let last = w.last else { return }
            let dur = last.t - first.t
            guard dur >= 0.25 else { return }
            let wSorted = w.map(\.reachCm).sorted()
            let topHalf = Array(wSorted[(wSorted.count / 2)...])
            let heldReach = topHalf[topHalf.count / 2]
            let spread = (topHalf.last ?? heldReach) - (topHalf.first ?? heldReach)
            let minKnee = w.map(\.kneeAngleDeg).min() ?? 180.0

            var valid = true
            var foulReason = ""
            if minKnee < minKneeStraightDeg {
                valid = false
                foulReason = String(format: "Knee bent (%.0f° < %.0f°)", minKnee, minKneeStraightDeg)
            } else if dur < minHoldSec {
                valid = false
                foulReason = String(format: "Bounced / not held (%.2fs < %.1fs)", dur, minHoldSec)
            } else if spread > maxHoldSpreadCm * 2.0 {
                valid = false
                foulReason = String(format: "Unsteady hold (spread %.1f cm)", spread)
            }

            attempts.append(ReachAttemptResult(
                number: attempts.count + 1,
                tStart: first.t,
                tEnd: last.t,
                reachCm: (heldReach * 10.0).rounded() / 10.0,
                holdSec: (dur * 100.0).rounded() / 100.0,
                minKneeAngleDeg: (minKnee * 10.0).rounded() / 10.0,
                valid: valid,
                foulReason: foulReason
            ))
        }

        for s in signals {
            if s.reachCm >= threshold {
                inReach = true
                window.append(s)
            } else if inReach {
                finalizeWindow(window)
                window.removeAll()
                inReach = false
                if attempts.count >= 3 { break }
            }
        }
        if inReach && attempts.count < 3 {
            finalizeWindow(window)
        }

        let validAttempts = attempts.filter(\.valid)
        guard let best = validAttempts.max(by: { $0.reachCm < $1.reachCm }) else {
            return SitAndReachMeasurementResult(
                bestReachCm: 0.0, attempts: attempts, rung: rung,
                confidence: .red,
                confidenceReasons: ["No valid held reach attempt detected"]
            )
        }

        var (band, reasons) = ConfidenceEvaluators.bandFromSignals(detectionRate: detectionRate, fps: fps)
        if rung >= 3 && band == .green {
            band = .amber
            reasons.append("Calibrated from body proportions (Rung 3) rather than box scale")
        }

        return SitAndReachMeasurementResult(
            bestReachCm: best.reachCm,
            attempts: attempts,
            rung: rung,
            confidence: band,
            confidenceReasons: reasons
        )
    }
}

// MARK: - Test 4: Standing Vertical Jump Analyzer (Flight-time physics h = g * t^2 / 8)

public struct VerticalJumpSignal: Sendable {
    public let t: Double
    public let hipY: Double
    public let ankleY: Double

    public init(t: Double, hipY: Double, ankleY: Double) {
        self.t = t
        self.hipY = hipY
        self.ankleY = ankleY
    }
}

public struct JumpAttemptResult: Equatable, Sendable {
    public let number: Int
    public let tTakeoff: Double
    public let tLanding: Double
    public let flightSec: Double
    public let heightCm: Double
    public let valid: Bool
    public let note: String
}

public struct VerticalJumpMeasurementResult: Equatable, Sendable {
    public let bestHeightCm: Double
    public let attempts: [JumpAttemptResult]
    public let confidence: ConfidenceBand
    public let confidenceReasons: [String]
}

public enum VerticalJumpAnalyzer {
    public static let gravityMps2: Double = 9.80665
    public static let minFlightSec: Double = 0.22
    public static let maxFlightSec: Double = 0.85

    public static func heightCm(fromFlightSec t: Double) -> Double {
        0.125 * gravityMps2 * t * t * 100.0
    }

    public static func measure(
        signals: [VerticalJumpSignal],
        fps: Double = 60.0,
        detectionRate: Double = 1.0
    ) -> VerticalJumpMeasurementResult {
        guard signals.count >= 15 else {
            return VerticalJumpMeasurementResult(
                bestHeightCm: 0.0, attempts: [], confidence: .red,
                confidenceReasons: ["Fewer than 15 usable pose frames"]
            )
        }
        let sortedAnkles = signals.map(\.ankleY).sorted()
        let groundAnkleY = sortedAnkles[Int(Double(sortedAnkles.count) * 0.80)]
        let sortedHips = signals.map(\.hipY).sorted()
        let standingHipY = sortedHips[Int(Double(sortedHips.count) * 0.80)]
        let legSpan = max(0.08, groundAnkleY - standingHipY)
        let airborneThresholdY = groundAnkleY - 0.14 * legSpan

        var attempts: [JumpAttemptResult] = []
        var airborneStart: Double? = nil

        for s in signals {
            let isAirborne = s.ankleY < airborneThresholdY
            if isAirborne && airborneStart == nil {
                airborneStart = s.t
            } else if !isAirborne, let t0 = airborneStart {
                let dt = s.t - t0
                airborneStart = nil
                if dt >= 0.12 {
                    let valid = dt >= minFlightSec && dt <= maxFlightSec
                    let h = heightCm(fromFlightSec: dt)
                    let note = valid ? "Clean takeoff & landing" :
                        (dt < minFlightSec ? "Hop below minimum flight time" : "Implausible airtime / tuck")
                    attempts.append(JumpAttemptResult(
                        number: attempts.count + 1,
                        tTakeoff: t0,
                        tLanding: s.t,
                        flightSec: (dt * 1000.0).rounded() / 1000.0,
                        heightCm: (h * 10.0).rounded() / 10.0,
                        valid: valid,
                        note: note
                    ))
                    if attempts.count >= 3 { break }
                }
            }
        }

        let validAttempts = attempts.filter(\.valid)
        guard let best = validAttempts.max(by: { $0.heightCm < $1.heightCm }) else {
            return VerticalJumpMeasurementResult(
                bestHeightCm: 0.0, attempts: attempts, confidence: .red,
                confidenceReasons: ["No valid vertical jump flight phase detected"]
            )
        }

        let (band, reasons) = ConfidenceEvaluators.bandFromSignals(
            detectionRate: detectionRate, fps: fps, minFpsForGreen: 55.0
        )
        return VerticalJumpMeasurementResult(
            bestHeightCm: best.heightCm,
            attempts: attempts,
            confidence: band,
            confidenceReasons: reasons
        )
    }
}

// MARK: - Test 9: Sit-Ups Counter & Handbook Foul Auditor (Rules 2, 3a, 3b, 3c)

public struct SitUpSignal: Sendable {
    public let t: Double
    public let hipAngle: Double
    public let elbowKnee: Double
    public let wristEar: Double
    public let elbowY: Double
    public let shoulderY: Double
    public let shoulderW: Double

    public init(
        t: Double,
        hipAngle: Double,
        elbowKnee: Double,
        wristEar: Double,
        elbowY: Double,
        shoulderY: Double,
        shoulderW: Double
    ) {
        self.t = t
        self.hipAngle = hipAngle
        self.elbowKnee = elbowKnee
        self.wristEar = wristEar
        self.elbowY = elbowY
        self.shoulderY = shoulderY
        self.shoulderW = shoulderW
    }
}

public struct SitUpRep: Equatable, Sendable {
    public let number: Int
    public let tStart: Double
    public let tEnd: Double
    public let valid: Bool
    public let foulRule: String?
    public let foulDetail: String
}

public struct SitUpMeasurementResult: Equatable, Sendable {
    public let validCount: Int
    public let totalAttempts: Int
    public let windowSec: Int
    public let reps: [SitUpRep]
    public let confidence: ConfidenceBand
    public let confidenceReasons: [String]
}

public enum SitUpAnalyzer {
    public static let hysteresis: Double = 0.30
    public static let elbowKneeTouch: Double = 0.60
    public static let wristEarMax: Double = 1.10
    public static let elbowFloorTol: Double = 0.55
    public static let elbowPushoff: Double = 0.22
    public static let restGapSec: Double = 2.5

    private enum State {
        case down, rising, up, returning
    }

    public static func countFromSignals(
        signals: [SitUpSignal],
        windowSec: Int = 45,
        fps: Double = 30.0,
        detectionRate: Double = 1.0
    ) -> SitUpMeasurementResult {
        let windowed = signals.filter { $0.t <= Double(windowSec) }
        guard windowed.count >= 15 else {
            return SitUpMeasurementResult(
                validCount: 0, totalAttempts: 0, windowSec: windowSec, reps: [],
                confidence: .red,
                confidenceReasons: ["Insufficient pose signal (< 15 frames in window)"]
            )
        }

        let sortedHips = windowed.map(\.hipAngle).sorted()
        let lowHip = sortedHips[Int(Double(sortedHips.count) * 0.10)]
        let highHip = sortedHips[Int(Double(sortedHips.count) * 0.90)]
        let hipSpan = max(25.0, highHip - lowHip)
        let upThresh = lowHip + hysteresis * hipSpan
        let downThresh = highHip - hysteresis * hipSpan

        let sortedElbows = windowed.map(\.elbowY).sorted()
        let floorElbowY = sortedElbows[Int(Double(sortedElbows.count) * 0.85)]

        var state: State = .down
        var reps: [SitUpRep] = []
        var repStartT: Double = 0.0
        var lastRepEndT: Double? = nil
        var minElbowKnee: Double = 999.0
        var maxWristEar: Double = 0.0
        var prevRiseElbowY: Double = floorElbowY
        var minRiseElbowY: Double = floorElbowY
        var pushoffDrop: Double = 0.0
        var prevReturnElbowValid: Bool = true

        for s in windowed {
            switch state {
            case .down:
                if s.hipAngle < downThresh {
                    state = .rising
                    repStartT = s.t
                    minElbowKnee = s.elbowKnee
                    maxWristEar = s.wristEar
                    prevRiseElbowY = s.elbowY
                    minRiseElbowY = s.elbowY
                    pushoffDrop = 0.0
                }
            case .rising:
                minElbowKnee = min(minElbowKnee, s.elbowKnee)
                maxWristEar = max(maxWristEar, s.wristEar)
                if s.elbowY < minRiseElbowY {
                    minRiseElbowY = s.elbowY
                } else {
                    let drop = (s.elbowY - prevRiseElbowY) / max(0.05, s.shoulderW)
                    pushoffDrop = max(pushoffDrop, drop)
                }
                prevRiseElbowY = s.elbowY

                if s.hipAngle <= upThresh && minElbowKnee <= elbowKneeTouch {
                    state = .up
                } else if s.hipAngle >= downThresh {
                    state = .down
                }
            case .up:
                minElbowKnee = min(minElbowKnee, s.elbowKnee)
                maxWristEar = max(maxWristEar, s.wristEar)
                if s.hipAngle > upThresh {
                    state = .returning
                }
            case .returning:
                maxWristEar = max(maxWristEar, s.wristEar)
                if s.hipAngle >= downThresh {
                    let elbowGap = abs(floorElbowY - s.elbowY) / max(0.05, s.shoulderW)
                    let returnedFlat = elbowGap <= elbowFloorTol

                    var foulRule: String? = nil
                    var foulDetail = ""

                    if !prevReturnElbowValid {
                        foulRule = "3c"
                        foulDetail = "Did not return elbows flat on surface before starting rep"
                    } else if maxWristEar > wristEarMax {
                        foulRule = "3a"
                        foulDetail = "Fingers unclasped from behind neck"
                    } else if pushoffDrop > elbowPushoff {
                        foulRule = "3b"
                        foulDetail = "Pushed off floor with elbow during rise"
                    } else if !returnedFlat {
                        foulRule = "3c"
                        foulDetail = "Elbows did not return flat on surface at end of rep"
                    } else if let prevEnd = lastRepEndT, (repStartT - prevEnd) > restGapSec {
                        foulRule = "2"
                        foulDetail = String(format: "Rested %.1fs between sit-ups (> %.1fs)", repStartT - prevEnd, restGapSec)
                    }

                    reps.append(SitUpRep(
                        number: reps.count + 1,
                        tStart: repStartT,
                        tEnd: s.t,
                        valid: foulRule == nil,
                        foulRule: foulRule,
                        foulDetail: foulDetail
                    ))
                    lastRepEndT = s.t
                    prevReturnElbowValid = returnedFlat
                    state = .down
                }
            }
        }

        let (band, reasons) = ConfidenceEvaluators.bandFromSignals(detectionRate: detectionRate, fps: fps)
        let validCount = reps.filter(\.valid).count
        return SitUpMeasurementResult(
            validCount: validCount,
            totalAttempts: reps.count,
            windowSec: windowSec,
            reps: reps,
            confidence: band,
            confidenceReasons: reasons
        )
    }
}
