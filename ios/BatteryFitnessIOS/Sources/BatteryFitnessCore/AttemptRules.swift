import Foundation

// SAI Handbook attempt rules, shared by every test. Mirrors the browser app (ATTEMPT_RULES in index.html):
//   - Height 1, Weight 1, Sit-Ups 1, Endurance Run 1; Medicine Ball 2, 30 m 2, Shuttle 2; Sit & Reach, Jumps 3.
//   - A rule foul uses up an attempt. A capture failure ("not in frame", "no jump seen") is a free retake.
//   - The score of record is the best attempt (highest for cm / reps, lowest for seconds); Height keeps the last valid one.
//   - The assessor may grant ONE extra attempt where the Handbook allows it (Medicine Ball, Sit-Ups).
//   - A typing mistake in a manual entry can be corrected without using an attempt; the original value is kept.

/// Why an attempt was rejected.
public enum RejectKind: String, Codable, Sendable {
    /// The athlete performed the test and broke a Handbook rule: counts as an attempt.
    case foul
    /// The app could not judge it, or nothing happened: free retake.
    case nothing

    /// Classifies a rejection message the way the browser app does (English and Hindi wording).
    public static func infer(fromMessage message: String) -> RejectKind {
        let m = message.lowercased()
        if m.contains("foul") || message.contains("फाउल") || message.contains("नियम उल्लंघन") { return .foul }
        return .nothing
    }
}

public enum ScorePick: Sendable {
    case highest, lowest, last
}

/// The one extra attempt the Handbook allows, granted only by the assessor.
public enum ExtraAttempt: Sendable {
    case validReason        // Medicine Ball: "a fault or interruption"
    case fairOpportunity    // Sit-Ups: "has not had a fair opportunity"

    public var english: String {
        switch self {
        case .validReason: return "Grant a 3rd attempt (valid reason: fault or interruption)"
        case .fairOpportunity: return "Grant a retry (athlete did not get a fair opportunity)"
        }
    }
}

public struct AttemptRule: Sendable {
    public let pick: ScorePick
    /// Height and Weight may be re-measured (Concept Note 5.1); the count is not enforced.
    public let unlimited: Bool
    public let extra: ExtraAttempt?
}

public struct AttemptCorrection: Codable, Equatable, Sendable {
    public let from: Double
    public let to: Double
    public let at: Date
}

/// One recorded attempt (a measurement, a manual entry, or a rejected try).
public struct AttemptRecord: Codable, Equatable, Sendable {
    public var valid: Bool
    public var value: Double?
    public var unit: String
    public var rejectKind: RejectKind?
    public var isManual: Bool
    public var detail: String
    public var corrections: [AttemptCorrection]

    public init(valid: Bool, value: Double?, unit: String, rejectKind: RejectKind? = nil,
                isManual: Bool = false, detail: String = "", corrections: [AttemptCorrection] = []) {
        self.valid = valid
        self.value = value
        self.unit = unit
        self.rejectKind = rejectKind
        self.isManual = isManual
        self.detail = detail
        self.corrections = corrections
    }

    public static func manual(_ value: Double, unit: String) -> AttemptRecord {
        AttemptRecord(valid: true, value: value, unit: unit, isManual: true,
                      detail: "Assessor entered \(Self.format(value)) \(unit)")
    }

    static func format(_ v: Double) -> String {
        v == v.rounded() ? String(Int(v)) : String(v)
    }
}

public enum AttemptRules {
    public static func rule(for test: BatteryTest) -> AttemptRule {
        switch test {
        case .height, .weight:
            return AttemptRule(pick: .last, unlimited: true, extra: nil)
        case .sitAndReach, .verticalJump, .broadJump:
            return AttemptRule(pick: .highest, unlimited: false, extra: nil)
        case .medicineBallThrow:
            return AttemptRule(pick: .highest, unlimited: false, extra: .validReason)
        case .sitUps:
            return AttemptRule(pick: .highest, unlimited: false, extra: .fairOpportunity)
        case .sprint30m, .shuttleRun, .enduranceRun:
            return AttemptRule(pick: .lowest, unlimited: false, extra: nil)
        }
    }

    /// A valid result and a rule foul use an attempt; a capture failure or "nothing seen" does not.
    public static func counts(_ a: AttemptRecord) -> Bool {
        a.valid || a.rejectKind == .foul
    }

    public static func counted(_ history: [AttemptRecord]) -> Int {
        history.filter(counts).count
    }

    public static func allowed(_ test: BatteryTest, extraGranted: Bool) -> Int {
        test.attempts + (extraGranted ? 1 : 0)
    }

    public static func isExhausted(_ test: BatteryTest, history: [AttemptRecord], extraGranted: Bool) -> Bool {
        if rule(for: test).unlimited { return false }
        return counted(history) >= allowed(test, extraGranted: extraGranted)
    }

    /// The score of record, chosen from the valid attempts per the test's rule.
    public static func pickBest(_ test: BatteryTest, history: [AttemptRecord]) -> AttemptRecord? {
        let valid = history.filter { $0.valid && $0.value != nil }
        guard let first = valid.first else { return nil }
        switch rule(for: test).pick {
        case .last: return valid.last
        case .lowest: return valid.reduce(first) { ($1.value ?? .infinity) < ($0.value ?? .infinity) ? $1 : $0 }
        case .highest: return valid.reduce(first) { ($1.value ?? -.infinity) > ($0.value ?? -.infinity) ? $1 : $0 }
        }
    }

    /// Fixes a typing mistake in the LAST manually entered value. It is not a new attempt, and the original value is
    /// kept so the record stays auditable. Returns nil when there is nothing to correct.
    public static func correctLastEntry(_ history: [AttemptRecord], newValue: Double, at date: Date = Date()) -> [AttemptRecord]? {
        guard let last = history.last, last.valid, last.isManual, let old = last.value,
              newValue.isFinite, newValue != old else { return nil }
        var corrected = last
        corrected.value = newValue
        corrected.corrections.append(AttemptCorrection(from: old, to: newValue, at: date))
        corrected.detail = "Assessor entered \(AttemptRecord.format(newValue)) \(last.unit) (corrected from \(AttemptRecord.format(old)))"
        return Array(history.dropLast()) + [corrected]
    }
}
