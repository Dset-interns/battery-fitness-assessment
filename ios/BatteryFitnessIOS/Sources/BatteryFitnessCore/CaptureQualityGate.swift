import Foundation

/// Enforcement of Concept Note §6.3.1 on iOS.
/// - Reject below the minimum duration at the point of capture.
/// - Warn 5 seconds before the maximum duration, then auto-stop at the maximum.
/// - Enforce 720p minimum resolution and 30 fps minimum frame rate.
/// - Apply file size limits with a 50% tolerance so 1080p/120fps capture is not penalised.
public enum CaptureQualityGate {
    public static let minShortEdgePx: Int = 720
    public static let minFps: Double = 29.0
    public static let warnBeforeMaxSec: Int = 5

    public enum Verdict: Equatable, Sendable {
        case accepted
        case rejected(reason: String, detail: String)
        case acceptedWithWarning(warning: String)
    }

    public static func canStop(test: BatteryTest, elapsedSec: Int) -> Bool {
        elapsedSec >= test.minDurationSec
    }

    public static func shouldWarn(test: BatteryTest, elapsedSec: Int) -> Bool {
        test.maxDurationSec > 0 && elapsedSec >= (test.maxDurationSec - warnBeforeMaxSec)
    }

    public static func mustAutoStop(test: BatteryTest, elapsedSec: Int) -> Bool {
        test.maxDurationSec > 0 && elapsedSec >= test.maxDurationSec
    }

    public static func evaluate(
        test: BatteryTest,
        durationSec: Double,
        sizeBytes: Int64,
        widthPx: Int,
        heightPx: Int,
        fps: Double,
        testDisplayName: String? = nil
    ) -> Verdict {
        let label = testDisplayName ?? test.displayNameEn

        if !test.isPhoto && durationSec < Double(test.minDurationSec) {
            return .rejected(
                reason: "Recording too short",
                detail: "This video is too short to contain a valid \(label) test; the test requires at least \(test.minDurationSec) seconds of recording."
            )
        }

        if !test.isPhoto && test.maxDurationSec > 0 && durationSec > Double(test.maxDurationSec + 1) {
            return .rejected(
                reason: "Recording too long",
                detail: "\(label) allows at most \(test.maxDurationSec) seconds."
            )
        }

        let shortEdge = min(widthPx, heightPx)
        if shortEdge >= 1 && shortEdge < minShortEdgePx {
            return .rejected(
                reason: "Resolution too low",
                detail: "Recorded at \(widthPx)×\(heightPx). At least 720p is required."
            )
        }

        if fps > 0.0 && fps < minFps {
            return .rejected(
                reason: "Frame rate too low",
                detail: "Recorded at \(Int(fps.rounded())) fps. At least 30 fps is required."
            )
        }

        if sizeBytes > test.maxSizeWithTolerance {
            let mbVal = sizeBytes / (1024 * 1024)
            let capMb = test.maxSizeWithTolerance / (1024 * 1024)
            return .rejected(
                reason: "File too large",
                detail: "\(mbVal) MB exceeds the \(capMb) MB ceiling (limit plus the permitted 50% tolerance)."
            )
        }

        if sizeBytes > test.maxSizeBytes {
            let limitMb = test.maxSizeBytes / (1024 * 1024)
            return .acceptedWithWarning(
                warning: "Larger than the \(limitMb) MB guideline, within the 50% tolerance for higher-quality capture."
            )
        }

        if sizeBytes >= 1 && sizeBytes < test.minSizeBytes {
            return .acceptedWithWarning(
                warning: "Smaller than expected for this test — check the recording actually captured the attempt."
            )
        }

        return .accepted
    }
}
