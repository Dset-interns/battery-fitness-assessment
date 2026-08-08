package com.batteryfitness.app.capture

import com.batteryfitness.app.domain.BatteryTest

/**
 * Enforcement of Concept Note §6.3.1.
 *
 * Two rules, and they are asymmetric — which is the part teams get wrong:
 *
 *   "Reject below the minimum, AT THE POINT OF CAPTURE. The app must block any
 *    video shorter than the per-test minimum as soon as it is recorded, with a
 *    clear message explaining why."
 *
 *   "Warn, then auto-stop at the maximum. The app should warn as recording
 *    approaches the maximum duration and automatically stop at the limit with a
 *    notification, rather than silently truncating the attempt."
 *
 * And on tolerance:
 *
 *   "Duration is the hard limit; size has tolerance. Enforce the duration limit
 *    strictly. Apply the size limit with a 50% tolerance so that teams capturing
 *    at higher quality (1080p or 120 fps) for better AI accuracy are not
 *    penalised for larger files."
 *
 * That last clause matters for us specifically: the vertical jump needs 120 fps
 * to reach ±2 cm, and 120 fps roughly quadruples file size. Enforcing size
 * strictly would penalise the very capture setting that makes the measurement
 * accurate.
 */
object CaptureQualityGate {

    /** Minimum acceptable resolution — §6.3 requires 720p. */
    const val MIN_SHORT_EDGE_PX = 720

    /** Minimum acceptable frame rate — §6.3 requires 30 fps. */
    const val MIN_FPS = 29.0

    /** Warn the operator this many seconds before the hard stop. */
    const val WARN_BEFORE_MAX_SEC = 5

    sealed interface Verdict {
        data object Accepted : Verdict
        data class Rejected(val reason: String, val detail: String) : Verdict
        data class AcceptedWithWarning(val warning: String) : Verdict
    }

    /**
     * Should recording be allowed to stop yet?
     *
     * Used to keep the stop control disabled until the minimum is reached, so
     * the operator cannot produce a clip that will only be rejected afterwards.
     */
    fun canStop(test: BatteryTest, elapsedSec: Int): Boolean =
        elapsedSec >= test.minDurationSec

    /** True once the warning banner should appear. */
    fun shouldWarn(test: BatteryTest, elapsedSec: Int): Boolean =
        test.maxDurationSec > 0 &&
            elapsedSec >= test.maxDurationSec - WARN_BEFORE_MAX_SEC

    /** True when recording must be stopped automatically. */
    fun mustAutoStop(test: BatteryTest, elapsedSec: Int): Boolean =
        test.maxDurationSec > 0 && elapsedSec >= test.maxDurationSec

    /**
     * Post-capture verdict.
     *
     * The rejection message names the test and the requirement, following the
     * example §6.3.1 gives verbatim: "This video is too short to contain a valid
     * sit-up test; the test requires at least 30 seconds of recording."
     */
    fun evaluate(
        test: BatteryTest,
        durationSec: Double,
        sizeBytes: Long,
        widthPx: Int,
        heightPx: Int,
        fps: Double,
        testDisplayName: String,
    ): Verdict {
        // --- Duration: hard limit, checked first -------------------------
        if (!test.isPhoto && durationSec < test.minDurationSec) {
            return Verdict.Rejected(
                reason = "Recording too short",
                detail = "This video is too short to contain a valid " +
                    "$testDisplayName test; the test requires at least " +
                    "${test.minDurationSec} seconds of recording.",
            )
        }
        if (!test.isPhoto && test.maxDurationSec > 0 &&
            durationSec > test.maxDurationSec + 1
        ) {
            return Verdict.Rejected(
                reason = "Recording too long",
                detail = "$testDisplayName allows at most " +
                    "${test.maxDurationSec} seconds.",
            )
        }

        // --- Resolution and frame rate -----------------------------------
        val shortEdge = minOf(widthPx, heightPx)
        if (shortEdge in 1 until MIN_SHORT_EDGE_PX) {
            return Verdict.Rejected(
                reason = "Resolution too low",
                detail = "Recorded at ${widthPx}×${heightPx}. " +
                    "At least 720p is required.",
            )
        }
        if (fps > 0.0 && fps < MIN_FPS) {
            return Verdict.Rejected(
                reason = "Frame rate too low",
                detail = "Recorded at ${"%.0f".format(fps)} fps. " +
                    "At least 30 fps is required.",
            )
        }

        // --- Size: soft limit, 50% tolerance -----------------------------
        if (sizeBytes > test.maxSizeWithTolerance) {
            return Verdict.Rejected(
                reason = "File too large",
                detail = "${sizeBytes / (1024 * 1024)} MB exceeds the " +
                    "${test.maxSizeWithTolerance / (1024 * 1024)} MB ceiling " +
                    "(limit plus the permitted 50% tolerance).",
            )
        }
        if (sizeBytes > test.maxSizeBytes) {
            return Verdict.AcceptedWithWarning(
                "Larger than the ${test.maxSizeBytes / (1024 * 1024)} MB " +
                    "guideline, within the 50% tolerance for higher-quality capture.",
            )
        }
        if (sizeBytes in 1 until test.minSizeBytes) {
            return Verdict.AcceptedWithWarning(
                "Smaller than expected for this test — check the recording " +
                    "actually captured the attempt.",
            )
        }

        return Verdict.Accepted
    }
}
