import XCTest
@testable import BatteryFitnessCore

final class BatteryFitnessCoreTests: XCTestCase {

    func testAllTenBatteryTestsAndFourAIMeasured() {
        XCTAssertEqual(BatteryTest.allCases.count, 10)
        let aiTests = BatteryTest.aiMeasured
        XCTAssertEqual(aiTests.count, 4)
        XCTAssertEqual(Set(aiTests), Set([.height, .sitAndReach, .verticalJump, .sitUps]))
        XCTAssertEqual(BatteryTest.aiPlanned.count, 3)
    }

    func testSessionEngineOrderEquivalence() {
        let roster: [Int64] = [101, 102, 103]
        XCTAssertTrue(SessionEngine.orderEquivalence(roster: roster))

        let athleteOrder = SessionEngine.traversal(roster: roster, sequencing: .athleteWise)
        let testOrder = SessionEngine.traversal(roster: roster, sequencing: .testWise)
        XCTAssertEqual(athleteOrder.count, 30)
        XCTAssertEqual(testOrder.count, 30)
        XCTAssertEqual(athleteOrder.first, Cell(athleteId: 101, test: .height))
        XCTAssertEqual(athleteOrder[1], Cell(athleteId: 101, test: .weight))
        XCTAssertEqual(testOrder.first, Cell(athleteId: 101, test: .height))
        XCTAssertEqual(testOrder[1], Cell(athleteId: 102, test: .height))
    }

    func testCaptureQualityGateEnforcesDurationAndTolerance() {
        // Sit-ups require >= 30s
        let shortVerdict = CaptureQualityGate.evaluate(
            test: .sitUps,
            durationSec: 18.0,
            sizeBytes: 8 * 1024 * 1024,
            widthPx: 1280,
            heightPx: 720,
            fps: 30.0
        )
        if case .rejected(let reason, _) = shortVerdict {
            XCTAssertEqual(reason, "Recording too short")
        } else {
            XCTFail("Expected rejection for short sit-ups clip")
        }

        // Vertical jump at 120fps (20 MB, above 15 MB limit but within 22.5 MB 50% tolerance)
        let highFpsVerdict = CaptureQualityGate.evaluate(
            test: .verticalJump,
            durationSec: 18.0,
            sizeBytes: 20 * 1024 * 1024,
            widthPx: 1920,
            heightPx: 1080,
            fps: 120.0
        )
        if case .acceptedWithWarning = highFpsVerdict {
            // Expected
        } else {
            XCTFail("Expected acceptedWithWarning for 20MB vertical jump clip within 50% tolerance")
        }
    }

    func testHeightAnalyzerMeasuresCleanStandingHeight() {
        let frames = (0..<20).map { i in
            HeightSignalFrame(t: Double(i) / 30.0, heightCm: 168.4 + Double(i % 3) * 0.1, headTiltDeg: 2.1)
        }
        let res = HeightAnalyzer.measure(frames: frames, rung: 1, fps: 30.0, detectionRate: 0.96)
        XCTAssertEqual(res.confidence, .green)
        XCTAssertEqual(res.heightCm, 168.5, accuracy: 0.3)
    }

    func testSitAndReachAnalyzerValidHoldAndKneeFoul() {
        var sig: [SitAndReachSignal] = []
        var t = 0.0
        for _ in 0..<15 {
            sig.append(SitAndReachSignal(t: t, reachCm: -4.0, kneeAngleDeg: 175.0))
            t += 1.0 / 30.0
        }
        for _ in 0..<30 {
            sig.append(SitAndReachSignal(t: t, reachCm: 14.4, kneeAngleDeg: 174.0))
            t += 1.0 / 30.0
        }
        for _ in 0..<15 {
            sig.append(SitAndReachSignal(t: t, reachCm: -4.0, kneeAngleDeg: 175.0))
            t += 1.0 / 30.0
        }
        // Bent-knee attempt (155° < 168°)
        for _ in 0..<30 {
            sig.append(SitAndReachSignal(t: t, reachCm: 21.0, kneeAngleDeg: 155.0))
            t += 1.0 / 30.0
        }
        let res = SitAndReachAnalyzer.measure(signals: sig, rung: 2, fps: 30.0, detectionRate: 1.0)
        XCTAssertEqual(res.confidence, .green)
        XCTAssertEqual(res.bestReachCm, 14.4, accuracy: 0.5)
        XCTAssertTrue(res.attempts.contains { !$0.valid && $0.foulReason.contains("Knee bent") })
    }

    func testVerticalJumpAnalyzerFlightPhysics() {
        var sig: [VerticalJumpSignal] = []
        var t = 0.0
        let fps = 60.0
        for _ in 0..<30 {
            sig.append(VerticalJumpSignal(t: t, hipY: 0.50, ankleY: 0.85))
            t += 1.0 / fps
        }
        // 0.50s flight time -> h = 9.80665 * 0.25 / 8 * 100 = 30.6 cm
        for _ in 0..<30 {
            sig.append(VerticalJumpSignal(t: t, hipY: 0.32, ankleY: 0.65))
            t += 1.0 / fps
        }
        for _ in 0..<30 {
            sig.append(VerticalJumpSignal(t: t, hipY: 0.50, ankleY: 0.85))
            t += 1.0 / fps
        }
        let res = VerticalJumpAnalyzer.measure(signals: sig, fps: fps, detectionRate: 0.98)
        XCTAssertEqual(res.confidence, .green)
        XCTAssertEqual(res.bestHeightCm, 30.6, accuracy: 1.5)
    }

    func testSitUpAnalyzerCountsValidAndFlagsFouls() {
        var sig: [SitUpSignal] = []
        var t = 0.0
        let fps = 30.0

        func appendRep(wristEarPeak: Double = 0.60) {
            for i in 1...12 {
                let u = Double(i) / 12.0
                sig.append(SitUpSignal(
                    t: t,
                    hipAngle: 130.0 + (60.0 - 130.0) * u,
                    elbowKnee: 1.50 + (0.40 - 1.50) * u,
                    wristEar: 0.60 + (wristEarPeak - 0.60) * u,
                    elbowY: 0.80 + (0.50 - 0.80) * u,
                    shoulderY: 0.70,
                    shoulderW: 0.15
                ))
                t += 1.0 / fps
            }
            for _ in 0..<2 {
                sig.append(SitUpSignal(t: t, hipAngle: 60.0, elbowKnee: 0.40, wristEar: wristEarPeak, elbowY: 0.50, shoulderY: 0.70, shoulderW: 0.15))
                t += 1.0 / fps
            }
            for i in 1...12 {
                let u = Double(i) / 12.0
                sig.append(SitUpSignal(
                    t: t,
                    hipAngle: 60.0 + (130.0 - 60.0) * u,
                    elbowKnee: 0.40 + (1.50 - 0.40) * u,
                    wristEar: wristEarPeak + (0.60 - wristEarPeak) * u,
                    elbowY: 0.50 + (0.80 - 0.50) * u,
                    shoulderY: 0.70,
                    shoulderW: 0.15
                ))
                t += 1.0 / fps
            }
            for _ in 0..<3 {
                sig.append(SitUpSignal(t: t, hipAngle: 130.0, elbowKnee: 1.50, wristEar: 0.60, elbowY: 0.80, shoulderY: 0.70, shoulderW: 0.15))
                t += 1.0 / fps
            }
        }

        for _ in 0..<5 { appendRep(wristEarPeak: 0.60) }
        appendRep(wristEarPeak: 1.65) // Rule 3a foul

        let res = SitUpAnalyzer.countFromSignals(signals: sig, windowSec: 45, fps: fps, detectionRate: 1.0)
        XCTAssertEqual(res.totalAttempts, 6)
        XCTAssertEqual(res.validCount, 5)
        XCTAssertEqual(res.reps.last?.foulRule, "3a")
    }
}
