package com.batteryfitness.app.domain

/**
 * The 10 Battery Fitness Assessment Tests.
 *
 * Every field here is taken directly from a source document — the per-test
 * duration and size limits from Concept Note §6.3.1, the attempt counts and
 * scoring units from the SAI Technical Handbook. Encoding them as data rather
 * than scattering them through UI code means the capture gate, the report card
 * and the validation harness all enforce one definition.
 *
 * `measurement` records whether this build measures the test with on-device AI
 * or falls back to manual entry. Concept Note §9.1.1 explicitly sanctions
 * manual entry for the remainder, and §9.1.3 sets the floor at 3-4 AI tests.
 */
enum class Measurement { AI, MANUAL }

enum class BatteryTest(
    val number: Int,
    val key: String,
    /** Concept Note §6.3.1. Duration is a hard limit; size has 50% tolerance. */
    val minDurationSec: Int,
    val maxDurationSec: Int,
    val minSizeBytes: Long,
    val maxSizeBytes: Long,
    /** Handbook: number of attempts permitted. */
    val attempts: Int,
    val unit: String,
    val measurement: Measurement,
    /** True when the capture is a still photo rather than a video (Test 2). */
    val isPhoto: Boolean = false,
) {
    HEIGHT(
        1, "height", 10, 30, 2 * MB, 15 * MB, 1, "cm", Measurement.AI,
    ),
    // Seven-segment OCR of the scale display. This is the ONE test where
    // legitimate training data can be created: synthetic seven-segment digits
    // rendered across fonts, glare, angles and blur, generated on our own
    // hardware and fully disclosable under §7.2. Every other data gap in this
    // battery is real; this one is not. OCR pre-fills an always-editable field
    // — §5.2 asks for "correct entry with OCR assist", not a locked read.
    WEIGHT(
        2, "weight", 0, 0, 100 * KB, 1 * MB, 1, "kg", Measurement.AI,
        isPhoto = true,
    ),
    // Shares the calibration module with HEIGHT. Needs Hand Landmarker on a
    // hand crop: MediaPipe Pose has no fingertip landmark, and measuring to the
    // wrist plus a guess is not a measurement.
    SIT_AND_REACH(
        3, "sit_and_reach", 10, 30, 2 * MB, 15 * MB, 3, "cm", Measurement.AI,
    ),
    VERTICAL_JUMP(
        4, "vertical_jump", 15, 30, 3 * MB, 15 * MB, 3, "cm", Measurement.AI,
    ),
    // Stretch: needs GROUND-plane homography, which is harder than the vertical
    // jump's wall because the ground is foreshortened along the jump axis, so a
    // single px/cm ratio fails. Also needs ~4 m of clear space plus tape, so it
    // is not demonstrable in a room.
    BROAD_JUMP(
        5, "broad_jump", 15, 30, 3 * MB, 15 * MB, 3, "cm", Measurement.MANUAL,
    ),
    MEDICINE_BALL_THROW(
        6, "medicine_ball_throw", 15, 30, 3 * MB, 15 * MB, 2, "cm", Measurement.MANUAL,
    ),
    // Stretch, and cheaper than its "High" rating suggests: the ±1 s tolerance
    // is enormous next to video line-crossing, which resolves to tens of ms.
    // Blocked only by space — framing all 30 m perpendicular needs ~23 m of
    // lateral clearance, so the viable rig is a finish-line camera with the
    // start taken from the starter's command in the audio track.
    SPRINT_30M(
        7, "sprint_30m", 10, 45, 2 * MB, 25 * MB, 2, "s", Measurement.MANUAL,
    ),
    // Stretch. Turn detection is a velocity sign change and the ±5 s tolerance
    // is vast, so TIMING is easy. Touch verification at the turn line is the
    // hard part — §3 states it is "hard to verify from video" — so it should be
    // flagged amber rather than silently accepted.
    SHUTTLE_RUN(
        8, "shuttle_run", 15, 60, 3 * MB, 30 * MB, 2, "s", Measurement.MANUAL,
    ),
    SIT_UPS(
        9, "sit_ups", 30, 60, 5 * MB, 30 * MB, 1, "reps", Measurement.AI,
    ),
    ENDURANCE_RUN(
        10, "endurance_run", 120, 900, 20 * MB, 200 * MB, 1, "s", Measurement.MANUAL,
    );

    /**
     * Concept Note §6.3.1: "Apply the size limit with a 50% tolerance so that
     * teams capturing at higher quality are not penalised for larger files."
     */
    val maxSizeWithTolerance: Long get() = (maxSizeBytes * 3) / 2

    /**
     * Handbook age-group variations. Sit-ups run 30 s under 12 and 45 s at 12+;
     * the endurance run is 800 m under 12 and 1.6 km at 12+.
     */
    fun windowSecondsFor(age: Int): Int? = when (this) {
        SIT_UPS -> if (age < 12) 30 else 45
        else -> null
    }

    fun enduranceDistanceMetresFor(age: Int): Int? =
        if (this == ENDURANCE_RUN) (if (age < 12) 800 else 1600) else null

    companion object {
        fun fromKey(key: String): BatteryTest? = entries.firstOrNull { it.key == key }

        /** Tests this build measures with on-device AI. */
        val aiMeasured: List<BatteryTest> get() = entries.filter { it.measurement == Measurement.AI }
    }
}

private const val KB = 1024L
private const val MB = 1024L * 1024L
