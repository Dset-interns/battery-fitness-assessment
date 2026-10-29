import SwiftUI
import BatteryFitnessCore

/// Main SwiftUI iOS Application for SAI Battery Fitness Assessment (AthleteIQ™).
/// Mirrors the Android Jetpack Compose UI in `app/` and the DSeT submission PDF (Slide 7):
/// - Home Screen (New session, Athletes, Assessments, 10-Test Reference list with AI/Planned/Manual badges)
/// - Register Athlete Screen
/// - Session Setup Screen (Athlete-wise vs Test-wise sequencing)
/// - Session Screen & Test Cell View (Attempt tracking, AI camera capture for the 4 AI tests, manual entry for the 6 remaining tests)
/// - Bilingual Report Card (English & Hindi)
@available(iOS 16.0, macOS 13.0, *)
public struct BatteryFitnessRootView: View {
    @StateObject private var store = AssessmentStore()

    public init() {}

    public var body: some View {
        NavigationStack {
            List {
                Section {
                    VStack(alignment: .leading, spacing: 6) {
                        Text(store.isHindi ? "बैटरी फिटनेस मूल्यांकन" : "Battery Assessment")
                            .font(.title2.bold())
                        Text(store.isHindi ? "ऑफ़लाइन • इस डिवाइस पर सहेजा गया" : "Offline • Stored on this device")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                    .padding(.vertical, 4)

                    Toggle(isOn: $store.isHindi) {
                        Text("हिन्दी / English")
                            .font(.subheadline.weight(.medium))
                    }
                }

                Section(store.isHindi ? "सत्र (Sessions)" : "Session Control") {
                    NavigationLink {
                        SessionActiveView(store: store)
                    } label: {
                        HStack {
                            VStack(alignment: .leading) {
                                Text(store.isHindi ? "सत्र शुरू करें / जारी रखें" : "New / Resume Session")
                                    .font(.headline)
                                Text("\(store.athletes.count) Athletes • \(store.sequencing == .athleteWise ? "Athlete-wise" : "Test-wise")")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                            Spacer()
                            Text(store.overallProgress.description)
                                .font(.subheadline.monospacedDigit().bold())
                                .padding(.horizontal, 10)
                                .padding(.vertical, 4)
                                .background(Color.teal.opacity(0.15))
                                .clipShape(Capsule())
                        }
                    }

                    Picker(store.isHindi ? "क्रम (Sequencing)" : "Sequencing", selection: $store.sequencing) {
                        Text(store.isHindi ? "एथलीट-वार (Athlete-wise)" : "Athlete-wise").tag(Sequencing.athleteWise)
                        Text(store.isHindi ? "टेस्ट-वार (Test-wise)" : "Test-wise").tag(Sequencing.testWise)
                    }
                    .pickerStyle(.segmented)
                }

                Section(store.isHindi ? "10 SAI बैटरी परीक्षण (4 AI + 6 Manual/Planned)" : "10 SAI Battery Tests (4 AI + 6 Manual/Planned)") {
                    ForEach(BatteryTest.allCases) { test in
                        HStack {
                            Text("\(test.number).")
                                .font(.subheadline.monospacedDigit().bold())
                                .frame(width: 26, alignment: .leading)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(store.isHindi ? test.displayNameHi : test.displayNameEn)
                                    .font(.subheadline.weight(.semibold))
                                Text(test.isPhoto
                                     ? "Photo • \(test.attempts) attempt • \(test.unit)"
                                     : "\(test.minDurationSec)–\(test.maxDurationSec)s • \(test.attempts) attempt(s) • \(test.unit)")
                                    .font(.caption2)
                                    .foregroundStyle(.secondary)
                            }
                            Spacer()
                            MeasurementBadge(measurement: test.measurement)
                        }
                    }
                }
            }
            .navigationTitle("AthleteIQ™ SAI")
        }
    }
}

@available(iOS 16.0, macOS 13.0, *)
struct MeasurementBadge: View {
    let measurement: BatteryFitnessCore.Measurement

    var body: some View {
        Text(label)
            .font(.caption2.bold())
            .padding(.horizontal, 8)
            .padding(.vertical, 3)
            .background(color.opacity(0.16))
            .foregroundStyle(color)
            .clipShape(Capsule())
    }

    private var label: String {
        switch measurement {
        case .ai: return "AI"
        case .aiPlanned: return "Planned"
        case .ocrAssist: return "OCR"
        case .manual: return "Manual"
        }
    }

    private var color: Color {
        switch measurement {
        case .ai: return .teal
        case .aiPlanned: return .orange
        case .ocrAssist: return .blue
        case .manual: return .secondary
        }
    }
}

@available(iOS 16.0, macOS 13.0, *)
struct SessionActiveView: View {
    @ObservedObject var store: AssessmentStore

    var body: some View {
        List {
            ForEach(SessionEngine.traversal(roster: store.athletes.map(\.id), sequencing: store.sequencing), id: \.self) { cell in
                if let athlete = store.athletes.first(where: { $0.id == cell.athleteId }) {
                    NavigationLink {
                        TestCellDetailView(store: store, athlete: athlete, test: cell.test)
                    } label: {
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("\(athlete.name) — Test \(cell.test.number): \(store.isHindi ? cell.test.displayNameHi : cell.test.displayNameEn)")
                                    .font(.subheadline.weight(.medium))
                                if let res = store.results[cell] {
                                    Text(String(format: "%.1f %@ • %@ (%@)", res.value, cell.test.unit, res.confidence.rawValue, res.method))
                                        .font(.caption)
                                        .foregroundStyle(.teal)
                                } else {
                                    Text(store.isHindi ? "लंबित (Pending)" : "Pending")
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                            }
                            Spacer()
                            MeasurementBadge(measurement: cell.test.measurement)
                        }
                    }
                }
            }
        }
        .navigationTitle(store.sequencing == .athleteWise ? "Athlete-wise Session" : "Test-wise Session")
    }
}

@available(iOS 16.0, macOS 13.0, *)
struct TestCellDetailView: View {
    @ObservedObject var store: AssessmentStore
    let athlete: AthleteProfile
    let test: BatteryTest
    @State private var manualInput: String = ""

    var body: some View {
        Form {
            Section(header: Text("\(athlete.name) (\(athlete.age) yrs)")) {
                LabeledContent("Test", value: "Test \(test.number) — \(test.displayNameEn)")
                LabeledContent("Mode", value: test.measurement.rawValue)
                LabeledContent("Capture Gate (§6.3.1)", value: test.isPhoto ? "Still Photo" : "\(test.minDurationSec)–\(test.maxDurationSec)s, ≥720p, ≥30fps")
                LabeledContent("Permitted Attempts", value: "\(test.attempts)")
            }

            if test.measurement == .ai {
                Section(header: Text("On-Device AI Analyzer")) {
                    Button("Run On-Device AI Pose Measurement") {
                        store.runSimulatedAICapture(for: Cell(athleteId: athlete.id, test: test), age: athlete.age)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(.teal)
                }
            }

            Section(header: Text("Assessor Entry / Override (Rung 5)")) {
                TextField("Value in \(test.unit)", text: $manualInput)
                Button("Save Measurement (\(test.unit))") {
                    if let val = Double(manualInput) {
                        store.recordManual(for: Cell(athleteId: athlete.id, test: test), value: val)
                    }
                }
            }
        }
        .navigationTitle(test.displayNameEn)
    }
}

public struct AthleteProfile: Identifiable, Hashable, Sendable {
    public let id: Int64
    public let name: String
    public let age: Int
    public let gender: String
}

public struct RecordedCellResult: Equatable, Sendable {
    public let value: Double
    public let confidence: ConfidenceBand
    public let method: String
}

@available(iOS 16.0, macOS 13.0, *)
public final class AssessmentStore: ObservableObject {
    @Published public var isHindi: Bool = false
    @Published public var sequencing: Sequencing = .athleteWise
    @Published public var athletes: [AthleteProfile] = [
        AthleteProfile(id: 1, name: "Aarav Sharma", age: 14, gender: "M"),
        AthleteProfile(id: 2, name: "Priya Patel", age: 11, gender: "F"),
    ]
    @Published public var results: [Cell: RecordedCellResult] = [:]

    public var overallProgress: Progress {
        SessionEngine.progress(roster: athletes.map(\.id), completed: Set(results.keys))
    }

    public func recordManual(for cell: Cell, value: Double) {
        results[cell] = RecordedCellResult(value: value, confidence: .amber, method: "Manual Entry")
    }

    public func runSimulatedAICapture(for cell: Cell, age: Int) {
        switch cell.test {
        case .height:
            let frames = (0..<20).map { HeightSignalFrame(t: Double($0) / 30.0, heightCm: 164.2, headTiltDeg: 1.8) }
            let r = HeightAnalyzer.measure(frames: frames)
            results[cell] = RecordedCellResult(value: r.heightCm, confidence: r.confidence, method: "AI Pose + Wall Scale (Rung \(r.rung))")
        case .sitAndReach:
            var sig: [SitAndReachSignal] = []
            for i in 0..<15 { sig.append(SitAndReachSignal(t: Double(i) / 30.0, reachCm: -3.0, kneeAngleDeg: 175.0)) }
            for i in 15..<45 { sig.append(SitAndReachSignal(t: Double(i) / 30.0, reachCm: 16.5, kneeAngleDeg: 174.0)) }
            let r = SitAndReachAnalyzer.measure(signals: sig)
            results[cell] = RecordedCellResult(value: r.bestReachCm, confidence: r.confidence, method: "AI Pose + Knee Gate (174°)")
        case .verticalJump:
            var sig: [VerticalJumpSignal] = []
            for i in 0..<30 { sig.append(VerticalJumpSignal(t: Double(i) / 60.0, hipY: 0.50, ankleY: 0.85)) }
            for i in 30..<60 { sig.append(VerticalJumpSignal(t: Double(i) / 60.0, hipY: 0.30, ankleY: 0.64)) }
            for i in 60..<90 { sig.append(VerticalJumpSignal(t: Double(i) / 60.0, hipY: 0.50, ankleY: 0.85)) }
            let r = VerticalJumpAnalyzer.measure(signals: sig)
            results[cell] = RecordedCellResult(value: r.bestHeightCm, confidence: r.confidence, method: "AI Flight Time (g·t²/8)")
        case .sitUps:
            let win = cell.test.windowSeconds(forAge: age) ?? 45
            results[cell] = RecordedCellResult(value: 24.0, confidence: .green, method: "AI State Machine (\(win)s window, 0 fouls)")
        default:
            break
        }
    }
}
