import Foundation

/// How a test's result is produced (Concept Note §8.5 & §9.1.3).
/// Mirrors `com.batteryfitness.app.domain.Measurement` in the Android codebase.
public enum Measurement: String, Codable, CaseIterable, Sendable {
    /// Built, working, verified on device (4 tests: Height, Sit & Reach, Vertical Jump, Sit-Ups).
    case ai = "AI"
    /// Method designed and documented; planned for next phase (3 tests: Broad Jump, 30m Sprint, 6x10m Shuttle).
    case aiPlanned = "AI_PLANNED"
    /// Reads a device display rather than measuring the quantity.
    case ocrAssist = "OCR_ASSIST"
    /// Assessor enters the value, with protocol guidance on screen (3 tests: Weight, Medicine Ball Throw, Endurance Run).
    case manual = "MANUAL"
}

/// Confidence band per Concept Note §8.2 — surfaces reduced accuracy instead of silently giving wrong results.
public enum ConfidenceBand: String, Codable, CaseIterable, Sendable {
    case green = "GREEN"
    case amber = "AMBER"
    case red = "RED"
}

public enum Sequencing: String, Codable, CaseIterable, Sendable {
    case athleteWise = "ATHLETE_WISE"
    case testWise = "TEST_WISE"
}

/// The 10 SAI Battery Fitness Assessment Tests.
/// Matches `com.batteryfitness.app.domain.BatteryTest` and Concept Note §6.3.1 / SAI Technical Handbook.
public enum BatteryTest: String, Codable, CaseIterable, Identifiable, Sendable {
    case height = "height"
    case weight = "weight"
    case sitAndReach = "sit_and_reach"
    case verticalJump = "vertical_jump"
    case broadJump = "broad_jump"
    case medicineBallThrow = "medicine_ball_throw"
    case sprint30m = "sprint_30m"
    case shuttleRun = "shuttle_run"
    case sitUps = "sit_ups"
    case enduranceRun = "endurance_run"

    public var id: String { rawValue }

    private static let kb: Int64 = 1024
    private static let mb: Int64 = 1024 * 1024

    public var number: Int {
        switch self {
        case .height: return 1
        case .weight: return 2
        case .sitAndReach: return 3
        case .verticalJump: return 4
        case .broadJump: return 5
        case .medicineBallThrow: return 6
        case .sprint30m: return 7
        case .shuttleRun: return 8
        case .sitUps: return 9
        case .enduranceRun: return 10
        }
    }

    public var displayNameEn: String {
        switch self {
        case .height: return "Height"
        case .weight: return "Weight"
        case .sitAndReach: return "Sit and Reach"
        case .verticalJump: return "Standing Vertical Jump"
        case .broadJump: return "Standing Broad Jump"
        case .medicineBallThrow: return "Medicine Ball Throw"
        case .sprint30m: return "30m Standing Start"
        case .shuttleRun: return "6 × 10m Shuttle Run"
        case .sitUps: return "Sit-Ups"
        case .enduranceRun: return "800m / 1.6km Run"
        }
    }

    public var displayNameHi: String {
        switch self {
        case .height: return "ऊँचाई (Height)"
        case .weight: return "वज़न (Weight)"
        case .sitAndReach: return "सिट एंड रीच (Sit & Reach)"
        case .verticalJump: return "स्टैंडिंग वर्टिकल जंप"
        case .broadJump: return "स्टैंडिंग ब्रॉड जंप"
        case .medicineBallThrow: return "मेडिसिन बॉल थ्रो"
        case .sprint30m: return "30 मीटर स्प्रिंट"
        case .shuttleRun: return "6 × 10 मीटर शटल रन"
        case .sitUps: return "सिट-अप्स (Sit-Ups)"
        case .enduranceRun: return "800 मी / 1.6 किमी दौड़"
        }
    }

    public var minDurationSec: Int {
        switch self {
        case .height: return 10
        case .weight: return 0
        case .sitAndReach: return 10
        case .verticalJump: return 15
        case .broadJump: return 15
        case .medicineBallThrow: return 15
        case .sprint30m: return 10
        case .shuttleRun: return 15
        case .sitUps: return 30
        case .enduranceRun: return 120
        }
    }

    public var maxDurationSec: Int {
        switch self {
        case .height: return 30
        case .weight: return 0
        case .sitAndReach: return 30
        case .verticalJump: return 30
        case .broadJump: return 30
        case .medicineBallThrow: return 30
        case .sprint30m: return 45
        case .shuttleRun: return 60
        case .sitUps: return 60
        case .enduranceRun: return 900
        }
    }

    public var minSizeBytes: Int64 {
        switch self {
        case .height: return 2 * Self.mb
        case .weight: return 100 * Self.kb
        case .sitAndReach: return 2 * Self.mb
        case .verticalJump: return 3 * Self.mb
        case .broadJump: return 3 * Self.mb
        case .medicineBallThrow: return 3 * Self.mb
        case .sprint30m: return 2 * Self.mb
        case .shuttleRun: return 3 * Self.mb
        case .sitUps: return 5 * Self.mb
        case .enduranceRun: return 20 * Self.mb
        }
    }

    public var maxSizeBytes: Int64 {
        switch self {
        case .height: return 15 * Self.mb
        case .weight: return 1 * Self.mb
        case .sitAndReach: return 15 * Self.mb
        case .verticalJump: return 15 * Self.mb
        case .broadJump: return 15 * Self.mb
        case .medicineBallThrow: return 15 * Self.mb
        case .sprint30m: return 25 * Self.mb
        case .shuttleRun: return 30 * Self.mb
        case .sitUps: return 30 * Self.mb
        case .enduranceRun: return 200 * Self.mb
        }
    }

    /// Concept Note §6.3.1: 50% size tolerance for 1080p/120fps capture.
    public var maxSizeWithTolerance: Int64 {
        (maxSizeBytes * 3) / 2
    }

    public var attempts: Int {
        switch self {
        case .height, .weight, .sitUps, .enduranceRun: return 1
        case .medicineBallThrow, .sprint30m, .shuttleRun: return 2
        case .sitAndReach, .verticalJump, .broadJump: return 3
        }
    }

    public var unit: String {
        switch self {
        case .height, .sitAndReach, .verticalJump, .broadJump, .medicineBallThrow: return "cm"
        case .weight: return "kg"
        case .sprint30m, .shuttleRun, .enduranceRun: return "s"
        case .sitUps: return "reps"
        }
    }

    public var measurement: Measurement {
        switch self {
        case .height, .sitAndReach, .verticalJump, .sitUps:
            return .ai
        case .broadJump, .sprint30m, .shuttleRun:
            return .aiPlanned
        case .weight, .medicineBallThrow, .enduranceRun:
            return .manual
        }
    }

    public var isPhoto: Bool {
        self == .weight
    }

    /// Handbook age-group window for Sit-Ups: <12 -> 30s, 12+ -> 45s.
    public func windowSeconds(forAge age: Int) -> Int? {
        guard self == .sitUps else { return nil }
        return age < 12 ? 30 : 45
    }

    /// Handbook age-group distance for Endurance Run: <12 -> 800m, 12+ -> 1600m.
    public func enduranceDistanceMetres(forAge age: Int) -> Int? {
        guard self == .enduranceRun else { return nil }
        return age < 12 ? 800 : 1600
    }

    /// Tests measured with on-device AI in this build (4 tests).
    public static var aiMeasured: [BatteryTest] {
        allCases.filter { $0.measurement == .ai }
    }

    /// Tests whose AI method is designed and planned (3 tests).
    public static var aiPlanned: [BatteryTest] {
        allCases.filter { $0.measurement == .aiPlanned }
    }
}
