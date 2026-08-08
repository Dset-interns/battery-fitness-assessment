package com.batteryfitness.app.capture

import com.batteryfitness.app.domain.BatteryTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Concept Note §6.3.1 is unusually specific about capture gating, including a
 * verbatim example message. These tests hold us to it.
 */
class CaptureQualityGateTest {

    private val MB = 1024L * 1024L

    private fun evaluate(
        test: BatteryTest,
        durationSec: Double,
        sizeMb: Long = 10,
        w: Int = 1280,
        h: Int = 720,
        fps: Double = 30.0,
    ) = CaptureQualityGate.evaluate(
        test = test,
        durationSec = durationSec,
        sizeBytes = sizeMb * MB,
        widthPx = w,
        heightPx = h,
        fps = fps,
        testDisplayName = "sit-up",
    )

    @Test
    fun `too-short sit-up clip is rejected with the reason stated`() {
        // §6.3.1's own example: sit-ups require at least 30 seconds.
        val v = evaluate(BatteryTest.SIT_UPS, durationSec = 12.0)
        assertTrue(v is CaptureQualityGate.Verdict.Rejected)
        val r = v as CaptureQualityGate.Verdict.Rejected
        assertTrue(
            "the message must explain WHY, not just fail: ${r.detail}",
            r.detail.contains("30 seconds"),
        )
    }

    @Test
    fun `clip at exactly the minimum is accepted`() {
        val v = evaluate(BatteryTest.SIT_UPS, durationSec = 30.0)
        assertTrue(v is CaptureQualityGate.Verdict.Accepted)
    }

    @Test
    fun `stop is blocked until the minimum duration is reached`() {
        assertFalse(CaptureQualityGate.canStop(BatteryTest.SIT_UPS, 29))
        assertTrue(CaptureQualityGate.canStop(BatteryTest.SIT_UPS, 30))
    }

    @Test
    fun `warning fires before the hard stop, not at it`() {
        // §6.3.1: "warn as recording approaches the maximum ... rather than
        // silently truncating the attempt."
        val max = BatteryTest.SIT_UPS.maxDurationSec        // 60
        assertFalse(CaptureQualityGate.shouldWarn(BatteryTest.SIT_UPS, max - 10))
        assertTrue(CaptureQualityGate.shouldWarn(BatteryTest.SIT_UPS, max - 2))
        assertFalse(CaptureQualityGate.mustAutoStop(BatteryTest.SIT_UPS, max - 1))
        assertTrue(CaptureQualityGate.mustAutoStop(BatteryTest.SIT_UPS, max))
    }

    @Test
    fun `size limit carries the 50 percent tolerance, duration does not`() {
        val t = BatteryTest.SIT_UPS                          // 30 MB guideline
        // Within the guideline: clean accept.
        assertTrue(evaluate(t, 40.0, sizeMb = 25) is CaptureQualityGate.Verdict.Accepted)
        // Over guideline, inside tolerance: accepted, but says so.
        val warned = evaluate(t, 40.0, sizeMb = 40)
        assertTrue(warned is CaptureQualityGate.Verdict.AcceptedWithWarning)
        // Beyond the tolerance: rejected.
        assertTrue(evaluate(t, 40.0, sizeMb = 60) is CaptureQualityGate.Verdict.Rejected)
    }

    @Test
    fun `high-quality capture is not penalised — the accuracy path stays open`() {
        // The vertical jump needs 120 fps to reach ±2 cm, and 120 fps roughly
        // quadruples file size. §6.3.1 grants the tolerance precisely so that
        // the setting which makes the measurement accurate is not blocked.
        val t = BatteryTest.VERTICAL_JUMP                    // 15 MB guideline
        val v = evaluate(t, 20.0, sizeMb = 20, w = 1920, h = 1080, fps = 120.0)
        assertTrue(
            "120 fps capture must not be rejected on size",
            v !is CaptureQualityGate.Verdict.Rejected,
        )
    }

    @Test
    fun `sub-720p is rejected`() {
        val v = evaluate(BatteryTest.SIT_UPS, 40.0, w = 854, h = 480)
        assertTrue(v is CaptureQualityGate.Verdict.Rejected)
        assertEquals("Resolution too low",
            (v as CaptureQualityGate.Verdict.Rejected).reason)
    }

    @Test
    fun `sub-30fps is rejected`() {
        val v = evaluate(BatteryTest.SIT_UPS, 40.0, fps = 24.0)
        assertTrue(v is CaptureQualityGate.Verdict.Rejected)
        assertEquals("Frame rate too low",
            (v as CaptureQualityGate.Verdict.Rejected).reason)
    }

    @Test
    fun `photo tests are exempt from duration rules`() {
        // Weight is a single photo, not a video (§6.3.1).
        assertTrue(BatteryTest.WEIGHT.isPhoto)
        val v = CaptureQualityGate.evaluate(
            BatteryTest.WEIGHT, durationSec = 0.0, sizeBytes = 400 * 1024,
            widthPx = 1280, heightPx = 720, fps = 0.0, testDisplayName = "weight",
        )
        assertTrue(v is CaptureQualityGate.Verdict.Accepted)
    }

    @Test
    fun `every test has a coherent duration and size window`() {
        for (t in BatteryTest.entries) {
            if (t.isPhoto) continue
            assertTrue("${t.key}: min >= max", t.minDurationSec < t.maxDurationSec)
            assertTrue("${t.key}: size window inverted", t.minSizeBytes < t.maxSizeBytes)
            assertEquals(
                "${t.key}: tolerance must be exactly 50%",
                t.maxSizeBytes * 3 / 2, t.maxSizeWithTolerance,
            )
        }
    }
}
