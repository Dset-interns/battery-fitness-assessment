// Standalone checks for BatteryFitnessCore.
//
// XCTest is only shipped with full Xcode, so on a Mac that has just the Command Line Tools
// `swift test` cannot run. This file is compiled together with Sources/BatteryFitnessCore by
// Scripts/run_core_checks.sh and mirrors Tests/BatteryFitnessCoreTests plus the newer analyzers.

import Foundation

var failures = 0
var total = 0

func check(_ ok: Bool, _ name: String, _ detail: @autoclosure () -> String = "") {
    total += 1
    if ok {
        print("  PASS  \(name)")
    } else {
        failures += 1
        print("  FAIL  \(name)  \(detail())")
    }
}

func near(_ a: Double, _ b: Double, _ tol: Double) -> Bool { abs(a - b) <= tol }

// MARK: - Session, quality gate (existing behaviour)

print("Session engine + capture gate")
do {
    check(BatteryTest.allCases.count == 10, "ten tests")
    check(BatteryTest.aiMeasured.count == 5, "five AI-measured tests", "\(BatteryTest.aiMeasured.count)")
    check(Set(BatteryTest.aiMeasured) == Set([.height, .sitAndReach, .verticalJump, .broadJump, .sitUps]), "AI set")
    check(BatteryTest.aiPlanned.count == 2, "two planned tests")
    check(SessionEngine.orderEquivalence(roster: [101, 102, 103]), "athlete-wise and test-wise order equivalent")

    let shortVerdict = CaptureQualityGate.evaluate(
        test: .sitUps, durationSec: 18.0, sizeBytes: 8 * 1024 * 1024, widthPx: 1280, heightPx: 720, fps: 30.0)
    if case .rejected(let reason, _) = shortVerdict {
        check(reason == "Recording too short", "short sit-up clip rejected")
    } else {
        check(false, "short sit-up clip rejected")
    }
}

// MARK: - Height

print("Height")
do {
    let frames = (0..<20).map { i in
        HeightSignalFrame(t: Double(i) / 30.0, heightCm: 168.4 + Double(i % 3) * 0.1, headTiltDeg: 2.1)
    }
    let res = HeightAnalyzer.measure(frames: frames, rung: 1, fps: 30.0, detectionRate: 0.96)
    check(res.confidence == .green, "clean height is GREEN")
    check(near(res.heightCm, 168.5, 0.3), "clean height value", "\(res.heightCm)")
}

// MARK: - Sit & Reach

print("Sit & Reach")
do {
    var sig: [SitAndReachSignal] = []
    var t = 0.0
    func add(_ n: Int, reach: Double, knee: Double) {
        for _ in 0..<n { sig.append(SitAndReachSignal(t: t, reachCm: reach, kneeAngleDeg: knee)); t += 1.0 / 30.0 }
    }
    add(15, reach: -4.0, knee: 175.0)
    add(30, reach: 14.4, knee: 174.0)
    add(15, reach: -4.0, knee: 175.0)
    add(30, reach: 21.0, knee: 155.0)
    let res = SitAndReachAnalyzer.measure(signals: sig, rung: 2, fps: 30.0, detectionRate: 1.0)
    check(near(res.bestReachCm, 14.4, 0.5), "best valid reach", "\(res.bestReachCm)")
    check(res.attempts.contains { !$0.valid && $0.foulReason.contains("Knee bent") }, "bent-knee attempt is a foul")
}

// MARK: - Vertical jump

print("Vertical jump")
do {
    var sig: [VerticalJumpSignal] = []
    var t = 0.0
    let fps = 60.0
    func add(_ n: Int, hip: Double, ankle: Double) {
        for _ in 0..<n { sig.append(VerticalJumpSignal(t: t, hipY: hip, ankleY: ankle)); t += 1.0 / fps }
    }
    add(30, hip: 0.50, ankle: 0.85)
    add(30, hip: 0.32, ankle: 0.65)
    add(30, hip: 0.50, ankle: 0.85)
    let res = VerticalJumpAnalyzer.measure(signals: sig, fps: fps, detectionRate: 0.98)
    check(near(res.bestHeightCm, 30.6, 1.5), "0.5 s flight is about 30.6 cm", "\(res.bestHeightCm)")
}

// MARK: - Sit-ups

print("Sit-ups")
do {
    var sig: [SitUpSignal] = []
    var t = 0.0
    let fps = 30.0
    func appendRep(wristEarPeak: Double) {
        for i in 1...12 {
            let u = Double(i) / 12.0
            sig.append(SitUpSignal(t: t, hipAngle: 130.0 + (60.0 - 130.0) * u, elbowKnee: 1.50 + (0.40 - 1.50) * u,
                                   wristEar: 0.60 + (wristEarPeak - 0.60) * u, elbowY: 0.80 + (0.50 - 0.80) * u,
                                   shoulderY: 0.70, shoulderW: 0.15))
            t += 1.0 / fps
        }
        for _ in 0..<2 {
            sig.append(SitUpSignal(t: t, hipAngle: 60.0, elbowKnee: 0.40, wristEar: wristEarPeak, elbowY: 0.50,
                                   shoulderY: 0.70, shoulderW: 0.15))
            t += 1.0 / fps
        }
        for i in 1...12 {
            let u = Double(i) / 12.0
            sig.append(SitUpSignal(t: t, hipAngle: 60.0 + (130.0 - 60.0) * u, elbowKnee: 0.40 + (1.50 - 0.40) * u,
                                   wristEar: wristEarPeak + (0.60 - wristEarPeak) * u, elbowY: 0.50 + (0.80 - 0.50) * u,
                                   shoulderY: 0.70, shoulderW: 0.15))
            t += 1.0 / fps
        }
        for _ in 0..<3 {
            sig.append(SitUpSignal(t: t, hipAngle: 130.0, elbowKnee: 1.50, wristEar: 0.60, elbowY: 0.80,
                                   shoulderY: 0.70, shoulderW: 0.15))
            t += 1.0 / fps
        }
    }
    for _ in 0..<5 { appendRep(wristEarPeak: 0.60) }
    appendRep(wristEarPeak: 1.65)
    let res = SitUpAnalyzer.countFromSignals(signals: sig, windowSec: 45, fps: fps, detectionRate: 1.0)
    check(res.totalAttempts == 6, "six reps seen", "\(res.totalAttempts)")
    check(res.validCount == 5, "five reps valid", "\(res.validCount)")
    check(res.reps.last?.foulRule == "3a", "unclasped hands is Rule 3a")
}

// MARK: - Standing broad jump (Handbook Test V)

print("Standing broad jump")

/// Side-view jump at 30 fps. 1 px = 0.5 cm in these checks. The lead toe sits 22 px ahead of
/// the ankle and the heel 12 px behind it, so toe-to-rear-heel = D - 34 px.
func makeJump(
    dir: Double = 1.0,
    travelPx: Double = 420.0,
    spreadPx: Double = 6.0,
    stepBackPx: Double = 0.0,
    preStill: Int = 20,
    postStill: Int = 20
) -> [BroadJumpSignal] {
    var out: [BroadJumpSignal] = []
    var t = 0.0
    let origin = 300.0
    func frame(ankleOffset: Double, footY: Double) {
        let ankle = origin + dir * ankleOffset
        out.append(BroadJumpSignal(
            t: t, hipX: ankle, ankleX: ankle,
            toeLeftX: ankle + dir * 22.0, toeRightX: ankle + dir * 22.0,
            heelLeftX: ankle - dir * 12.0, heelRightX: ankle - dir * 12.0,
            ankleSpreadX: spreadPx, footY: footY, kneeDeg: 170.0))
        t += 1.0 / 30.0
    }
    for _ in 0..<preStill { frame(ankleOffset: 0.0, footY: 600.0) }
    for i in 1...10 {
        let u = Double(i) / 10.0
        frame(ankleOffset: travelPx * u, footY: 600.0 - 40.0 * sin(Double.pi * u))
    }
    for _ in 0..<postStill { frame(ankleOffset: travelPx, footY: 600.0) }
    if stepBackPx > 0 {
        for i in 1...10 { frame(ankleOffset: travelPx - stepBackPx * Double(i) / 10.0, footY: 600.0) }
        for _ in 0..<5 { frame(ankleOffset: travelPx - stepBackPx, footY: 600.0) }
    }
    return out
}

do {
    let ok = BroadJumpAnalyzer.measure(signals: makeJump(), cmPerPx: 0.5, scaleRung: 1)
    check(ok.valid, "clean jump is valid", ok.foulReason)
    check(near(ok.distanceCm, 193.0, 1.0), "toe-to-rear-heel distance (193 cm)", "\(ok.distanceCm)")
    check(!ok.usedTakeoffLine, "no line used")

    let mirrored = BroadJumpAnalyzer.measure(signals: makeJump(dir: -1.0), cmPerPx: 0.5, scaleRung: 1)
    check(mirrored.valid && near(mirrored.distanceCm, 193.0, 1.0), "right-to-left jump measures the same", "\(mirrored.distanceCm) \(mirrored.foulReason)")

    let noLine = BroadJumpAnalyzer.measure(signals: makeJump(), cmPerPx: 0.5, scaleRung: 3)
    check(noLine.confidence != .green, "no take-off line / estimated scale is never GREEN")

    // Take-off line 8 px ahead of... behind the lead toe (toe x = 322): line at 330 means toes 8 px behind it.
    let withLine = BroadJumpAnalyzer.measure(signals: makeJump(), cmPerPx: 0.5, takeoffLineX: 330.0, scaleRung: 1)
    check(withLine.valid && near(withLine.distanceCm, (300.0 + 420.0 - 12.0 - 330.0) * 0.5, 1.0),
          "distance measured from the take-off line", "\(withLine.distanceCm) \(withLine.foulReason)")
    check(withLine.usedTakeoffLine, "line flagged as used")

    let toesOver = BroadJumpAnalyzer.measure(signals: makeJump(), cmPerPx: 0.5, takeoffLineX: 310.0, scaleRung: 1)
    check(!toesOver.valid && toesOver.foulReason.contains("take-off line"), "toes over the line is a foul", toesOver.foulReason)

    let noJump = BroadJumpAnalyzer.measure(signals: makeJump(travelPx: 20.0), cmPerPx: 0.5, scaleRung: 1)
    check(!noJump.valid && noJump.foulReason.contains("No horizontal jump"), "standing in place is rejected", noJump.foulReason)

    let oneFoot = BroadJumpAnalyzer.measure(signals: makeJump(spreadPx: 100.0), cmPerPx: 0.5, scaleRung: 1)
    check(!oneFoot.valid && oneFoot.foulReason.contains("two-foot"), "wide foot separation is rejected", oneFoot.foulReason)

    let fellBack = BroadJumpAnalyzer.measure(signals: makeJump(stepBackPx: 60.0), cmPerPx: 0.5, scaleRung: 1)
    check(!fellBack.valid && fellBack.foulReason.contains("backward"), "stepping back after landing is a foul", fellBack.foulReason)

    check(!BroadJumpAnalyzer.isSideProfile(shoulderWidthPx: 60, hipWidthPx: 40, torsoPx: 100), "front view rejected")
    check(BroadJumpAnalyzer.isSideProfile(shoulderWidthPx: 15, hipWidthPx: 10, torsoPx: 100), "side view accepted")
    check(!BroadJumpAnalyzer.isUpright(ankleY: 150, hipY: 100, torsoPx: 100), "seated/lying rejected")
    check(BroadJumpAnalyzer.isUpright(ankleY: 250, hipY: 100, torsoPx: 100), "upright accepted")
}

print("")
print(failures == 0 ? "ALL \(total) CHECKS PASSED" : "\(failures) of \(total) CHECKS FAILED")
exit(failures == 0 ? 0 : 1)
