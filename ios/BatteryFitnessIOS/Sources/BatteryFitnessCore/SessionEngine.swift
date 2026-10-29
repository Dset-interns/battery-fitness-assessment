import Foundation

/// One addressable slot in the assessment: a specific athlete, a specific test.
public struct Cell: Hashable, Codable, Sendable {
    public let athleteId: Int64
    public let test: BatteryTest

    public init(athleteId: Int64, test: BatteryTest) {
        self.athleteId = athleteId
        self.test = test
    }
}

public struct Progress: Equatable, Sendable, CustomStringConvertible {
    public let done: Int
    public let total: Int

    public init(done: Int, total: Int) {
        self.done = done
        self.total = total
    }

    public var complete: Bool {
        total > 0 && done == total
    }

    public var fraction: Float {
        total == 0 ? 0.0 : Float(done) / Float(total)
    }

    public var description: String {
        "\(done)/\(total)"
    }
}

/// Session traversal engine (Concept Note §6.1 & §6.5).
/// Guarantees identical, correctly indexed results regardless of whether
/// `ATHLETE_WISE` or `TEST_WISE` sequencing is selected.
public enum SessionEngine {
    public static func traversal(
        roster: [Int64],
        tests: [BatteryTest] = BatteryTest.allCases,
        sequencing: Sequencing
    ) -> [Cell] {
        switch sequencing {
        case .athleteWise:
            return roster.flatMap { athlete in tests.map { Cell(athleteId: athlete, test: $0) } }
        case .testWise:
            return tests.flatMap { test in roster.map { Cell(athleteId: $0, test: test) } }
        }
    }

    public static func nextIncomplete(
        roster: [Int64],
        tests: [BatteryTest] = BatteryTest.allCases,
        sequencing: Sequencing,
        completed: Set<Cell>
    ) -> Cell? {
        traversal(roster: roster, tests: tests, sequencing: sequencing)
            .first { !completed.contains($0) }
    }

    public static func progress(
        roster: [Int64],
        tests: [BatteryTest] = BatteryTest.allCases,
        completed: Set<Cell>
    ) -> Progress {
        let rosterSet = Set(roster)
        let testSet = Set(tests)
        let done = completed.filter { rosterSet.contains($0.athleteId) && testSet.contains($0.test) }.count
        return Progress(done: done, total: roster.count * tests.count)
    }

    public static func perAthlete(
        roster: [Int64],
        tests: [BatteryTest] = BatteryTest.allCases,
        completed: Set<Cell>
    ) -> [Int64: Progress] {
        Dictionary(uniqueKeysWithValues: roster.map { athlete in
            let done = tests.filter { completed.contains(Cell(athleteId: athlete, test: $0)) }.count
            return (athlete, Progress(done: done, total: tests.count))
        })
    }

    public static func perTest(
        roster: [Int64],
        tests: [BatteryTest] = BatteryTest.allCases,
        completed: Set<Cell>
    ) -> [BatteryTest: Progress] {
        Dictionary(uniqueKeysWithValues: tests.map { test in
            let done = roster.filter { completed.contains(Cell(athleteId: $0, test: test)) }.count
            return (test, Progress(done: done, total: roster.count))
        })
    }

    public static func orderEquivalence(
        roster: [Int64],
        tests: [BatteryTest] = BatteryTest.allCases
    ) -> Bool {
        let a = traversal(roster: roster, tests: tests, sequencing: .athleteWise)
        let t = traversal(roster: roster, tests: tests, sequencing: .testWise)
        return Set(a) == Set(t) && a.count == t.count
    }

    /// Handbook Test VII: 1 kg for girls and boys under 12; 2 kg for boys above 12.
    public static func medicineBallKg(ageYears: Int, isMale: Bool) -> Int {
        (isMale && ageYears > 12) ? 2 : 1
    }
}
