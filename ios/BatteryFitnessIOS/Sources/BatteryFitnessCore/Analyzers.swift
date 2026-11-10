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

// MARK: - Test 5: Standing Broad Jump Analyzer (SAI Handbook Test V)
//
// Handbook: stand behind a line, feet slightly apart, two-foot take-off and landing, do not
// fall backward. Distance is taken from the take-off line to the NEAREST point of contact on
// landing (the back of the heels). The camera sees the jump from the side, so "forward" is
// whichever way the hips travel across the frame.

/// One frame of side-view pose features. All x/y values are image pixels.
public struct BroadJumpSignal: Sendable {
    public let t: Double
    public let hipX: Double
    public let ankleX: Double
    public let toeLeftX: Double
    public let toeRightX: Double
    public let heelLeftX: Double
    public let heelRightX: Double
    public let ankleSpreadX: Double
    public let footY: Double
    public let kneeDeg: Double

    public init(
        t: Double,
        hipX: Double,
        ankleX: Double,
        toeLeftX: Double,
        toeRightX: Double,
        heelLeftX: Double,
        heelRightX: Double,
        ankleSpreadX: Double,
        footY: Double,
        kneeDeg: Double
    ) {
        self.t = t
        self.hipX = hipX
        self.ankleX = ankleX
        self.toeLeftX = toeLeftX
        self.toeRightX = toeRightX
        self.heelLeftX = heelLeftX
        self.heelRightX = heelRightX
        self.ankleSpreadX = ankleSpreadX
        self.footY = footY
        self.kneeDeg = kneeDeg
    }
}

public struct BroadJumpMeasurementResult: Equatable, Sendable {
    public let valid: Bool
    public let distanceCm: Double
    public let foulReason: String
    public let centreTravelCm: Double
    public let liftCm: Double
    public let minKneeDeg: Double
    public let usedTakeoffLine: Bool
    public let confidence: ConfidenceBand
    public let confidenceReasons: [String]
}

public enum BroadJumpAnalyzer {
    public static let minHorizontalTravelCm: Double = 25.0
    public static let minHipTravelCm: Double = 20.0
    public static let maxPreWalkCm: Double = 25.0
    public static let maxStartSpreadCm: Double = 26.0
    public static let maxEndSpreadCm: Double = 28.0
    public static let maxAirSpreadCm: Double = 38.0
    public static let minTwoFootLiftCm: Double = 2.2
    public static let minPeakSpeedCmPerSec: Double = 55.0
    public static let maxStepBackCm: Double = 15.0
    public static let toeOverLineToleranceCm: Double = 2.0
    /// Toe-to-heel should roughly match how far the body travelled; outside this the toe/heel
    /// landmarks are probably jittering, so the result is flagged AMBER (never silently replaced).
    public static let toeHeelVsTravelRange: ClosedRange<Double> = 0.65...1.05

    // MARK: Per-frame gates (used by the pose layer before frames are recorded)

    /// Rejects a front-facing athlete: a side-on camera must see narrow shoulders and hips.
    public static func isSideProfile(shoulderWidthPx: Double, hipWidthPx: Double, torsoPx: Double) -> Bool {
        !(shoulderWidthPx > 0.46 * torsoPx && hipWidthPx > 0.28 * torsoPx)
    }

    /// Rejects sitting/lying: the ankles must be well below the hips.
    public static func isUpright(ankleY: Double, hipY: Double, torsoPx: Double) -> Bool {
        (ankleY - hipY) >= 0.55 * torsoPx
    }

    // MARK: Measurement

    /// - Parameters:
    ///   - cmPerPx: image scale at the athlete's plane.
    ///   - takeoffLineX: x of the take-off line in pixels, if known. When nil the distance is
    ///     measured from the athlete's own lead toe (an approximation, reported as such).
    ///   - scaleRung: calibration rung (1-2 reference in frame, 3+ estimated from body size).
    public static func measure(
        signals: [BroadJumpSignal],
        cmPerPx: Double,
        takeoffLineX: Double? = nil,
        scaleRung: Int = 3,
        fps: Double = 30.0,
        detectionRate: Double = 1.0
    ) -> BroadJumpMeasurementResult {
        func foul(_ reason: String, travel: Double = 0.0, lift: Double = 0.0, knee: Double = 180.0) -> BroadJumpMeasurementResult {
            BroadJumpMeasurementResult(
                valid: false, distanceCm: 0.0, foulReason: reason,
                centreTravelCm: travel, liftCm: lift, minKneeDeg: knee,
                usedTakeoffLine: takeoffLineX != nil,
                confidence: .red, confidenceReasons: [reason]
            )
        }

        let n = signals.count
        guard n >= 15, cmPerPx > 0 else {
            return foul("Fewer than 15 usable pose frames or no image scale")
        }

        let hip = medianFilter3(signals.map(\.hipX))
        let ankle = medianFilter3(signals.map(\.ankleX))
        let foot = medianFilter3(signals.map(\.footY))
        let spread = medianFilter3(signals.map(\.ankleSpreadX))
        let knee = medianFilter3(signals.map(\.kneeDeg))
        let minKnee = knee.min() ?? 180.0

        // Smoothed horizontal hip speed; its peak marks the push-off/flight phase.
        var speed = [Double](repeating: 0.0, count: n)
        for i in 1..<n {
            let dt = max(0.016, signals[i].t - signals[i - 1].t)
            speed[i] = abs(hip[i] - hip[i - 1]) * cmPerPx / dt
        }
        let speedSm: [Double] = (0..<n).map { i in
            let lo = max(0, i - 1), hi = min(n - 1, i + 1)
            return speed[lo...hi].reduce(0.0, +) / Double(hi - lo + 1)
        }
        var peakIdx = 1
        var peakSpeed = 0.0
        for i in 1..<n where speedSm[i] > peakSpeed {
            peakSpeed = speedSm[i]
            peakIdx = i
        }
        let speedThr = max(35.0, peakSpeed * 0.25)
        var take = peakIdx
        while take > 1 && speedSm[take - 1] >= speedThr { take -= 1 }
        var land = peakIdx
        while land < n - 2 && speedSm[land + 1] >= speedThr { land += 1 }

        let preStart = max(0, take - 12)
        let preEnd = max(preStart + 2, take)
        let postStart = min(n - 2, land + 1)
        let postEnd = min(n, land + 13)
        guard preEnd <= n, postEnd - postStart >= 2 else {
            return foul("Not enough frames before take-off or after landing")
        }

        let dir: Double = (median(Array(hip[postStart..<postEnd])) - median(Array(hip[preStart..<preEnd]))) >= 0 ? 1.0 : -1.0

        let startAnkle = median(Array(ankle[preStart..<preEnd]))
        let endAnkle = median(Array(ankle[postStart..<postEnd]))
        let ankleTravelCm = abs(endAnkle - startAnkle) * cmPerPx
        let hipTravelCm = abs(median(Array(hip[postStart..<postEnd])) - median(Array(hip[preStart..<preEnd]))) * cmPerPx
        let centreTravelCm = 0.60 * ankleTravelCm + 0.40 * hipTravelCm

        if max(ankleTravelCm, hipTravelCm) < minHorizontalTravelCm || hipTravelCm < minHipTravelCm {
            return foul(String(format: "No horizontal jump (%.1f cm < %.0f cm)", max(ankleTravelCm, hipTravelCm), minHorizontalTravelCm),
                        travel: centreTravelCm, knee: minKnee)
        }

        // Must start stationary behind the line: no walk-up or run-up.
        if preStart >= 4 {
            let initialAnkle = median(Array(ankle[0..<min(5, preStart)]))
            let preWalkCm = (startAnkle - initialAnkle) * dir * cmPerPx
            if preWalkCm > maxPreWalkCm {
                return foul(String(format: "Preliminary walk-up before take-off (%.0f cm)", preWalkCm),
                            travel: centreTravelCm, knee: minKnee)
            }
        }

        // Two-foot take-off and landing.
        let startSpreadCm = median(Array(spread[preStart..<preEnd])) * cmPerPx
        let endSpreadCm = median(Array(spread[postStart..<postEnd])) * cmPerPx
        let airSlice = Array(spread[take...max(take, land)])
        let maxSpreadCm = percentile(airSlice, 0.90) * cmPerPx
        let baselineFootY = max(median(Array(foot[preStart..<preEnd])), median(Array(foot[postStart..<postEnd])))
        let minFootY = foot[max(0, take - 2)..<min(n, land + 3)].min() ?? baselineFootY
        let liftCm = max(0.0, (baselineFootY - minFootY) * cmPerPx)
        if (maxSpreadCm > maxAirSpreadCm && liftCm < minTwoFootLiftCm)
            || startSpreadCm > maxStartSpreadCm
            || endSpreadCm > maxEndSpreadCm
            || peakSpeed < minPeakSpeedCmPerSec {
            return foul(String(format: "Not a two-foot jump (foot separation %.0f cm, lift %.1f cm)",
                               max(startSpreadCm, endSpreadCm, maxSpreadCm), liftCm),
                        travel: centreTravelCm, lift: liftCm, knee: minKnee)
        }

        // Handbook: landing "without falling backward".
        if postEnd < n - 2 {
            let rearAfterLanding = dir > 0
                ? (ankle[postStart...].min() ?? endAnkle)
                : (ankle[postStart...].max() ?? endAnkle)
            let stepBackCm = (endAnkle - rearAfterLanding) * dir * cmPerPx
            if stepBackCm > maxStepBackCm {
                return foul(String(format: "Stepped or fell backward after landing (%.0f cm)", stepBackCm),
                            travel: centreTravelCm, lift: liftCm, knee: minKnee)
            }
        }

        // Lead toe at take-off, and the rear-most heel on landing ("back of the heels").
        let toes = (preStart..<preEnd).map { i -> Double in
            dir > 0 ? max(signals[i].toeLeftX, signals[i].toeRightX) : min(signals[i].toeLeftX, signals[i].toeRightX)
        }
        let heels = (postStart..<postEnd).map { i -> Double in
            dir > 0 ? min(signals[i].heelLeftX, signals[i].heelRightX) : max(signals[i].heelLeftX, signals[i].heelRightX)
        }
        let startLeadToe = median(toes)
        let landRearHeel = median(heels)

        var reasons: [String] = []
        let origin: Double
        if let line = takeoffLineX {
            let toesOverCm = (startLeadToe - line) * dir * cmPerPx
            if toesOverCm > toeOverLineToleranceCm {
                return foul(String(format: "Toes over the take-off line by %.1f cm", toesOverCm),
                            travel: centreTravelCm, lift: liftCm, knee: minKnee)
            }
            origin = line
        } else {
            origin = startLeadToe
            reasons.append("No take-off line marked: measured from the athlete's own lead toe")
        }
        let distanceCm = max(0.0, (landRearHeel - origin) * dir * cmPerPx)

        let baseBand = ConfidenceEvaluators.bandFromSignals(detectionRate: detectionRate, fps: fps)
        var band = baseBand.0
        reasons.append(contentsOf: baseBand.1)
        if scaleRung >= 3 && band == .green {
            band = .amber
            reasons.append("Scale estimated from body size (Rung \(scaleRung)), not a reference in frame")
        }
        if takeoffLineX == nil {
            if band == .green { band = .amber }
            if centreTravelCm > 0 && !toeHeelVsTravelRange.contains(distanceCm / centreTravelCm) {
                reasons.append("Toe/heel landmarks disagree with body travel (landmark jitter likely)")
            }
        }

        return BroadJumpMeasurementResult(
            valid: true,
            distanceCm: (distanceCm * 10.0).rounded() / 10.0,
            foulReason: "",
            centreTravelCm: (centreTravelCm * 10.0).rounded() / 10.0,
            liftCm: (liftCm * 10.0).rounded() / 10.0,
            minKneeDeg: (minKnee * 10.0).rounded() / 10.0,
            usedTakeoffLine: takeoffLineX != nil,
            confidence: band,
            confidenceReasons: reasons
        )
    }

    // MARK: Small numeric helpers

    private static func median(_ a: [Double]) -> Double {
        guard !a.isEmpty else { return .nan }
        let s = a.sorted()
        let m = s.count / 2
        return s.count % 2 == 1 ? s[m] : (s[m - 1] + s[m]) / 2.0
    }

    private static func percentile(_ a: [Double], _ p: Double) -> Double {
        guard !a.isEmpty else { return 0.0 }
        let s = a.sorted()
        let idx = min(s.count - 1, max(0, Int((p * Double(s.count - 1)).rounded())))
        return s[idx]
    }

    private static func medianFilter3(_ a: [Double]) -> [Double] {
        guard a.count > 2 else { return a }
        return a.indices.map { i in
            median(Array(a[max(0, i - 1)...min(a.count - 1, i + 1)]))
        }
    }
}
