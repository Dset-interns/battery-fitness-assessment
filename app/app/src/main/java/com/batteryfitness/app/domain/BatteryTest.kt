package com.batteryfitness.app.domain

/**
 * How a test's result is produced.
 *
 * Four states rather than two, because one label was previously doing two jobs
 * — what we have BUILT and what we PLAN. Conflating them overstates coverage,
 * and Round 2 evaluation would expose it. Concept Note §8.5 rewards being
 * explicit about what does not exist yet.
 */
enum class Measurement {
    /** Built, working, verified on device. */
    AI,

    /** Method designed and documented; not implemented in this build. */
    AI_PLANNED,

    /**
     * Reads a device display rather than measuring the quantity.
     *
     * Weight sits here, not under AI: the scale measures the weight, OCR only
     * transcribes the digits. §2's target is "correct entry with OCR assist" —
     * not a tolerance — and §5.2 asks for manual entry as the baseline with
     * OCR optional. Counting it as an AI-measured test would inflate coverage.
     */
    OCR_ASSIST,

    /** Assessor enters the value, with protocol guidance on screen. */
    MANUAL,
}

/**
 * The 10 Battery Fitness Assessment Tests.
 *
 * Every field here is taken directly from a source document — the per-test
 * duration and size limits from Concept Note §6.3.1, the attempt counts and
 * scoring units from the SAI Technical Handbook. Encoding them as data rather
 * than scattering them through UI code means the capture gate, the report card
 * and the validation harness all enforce one definition.
 *
 * §9.1.1 explicitly sanctions manual entry for the tests not measured by AI,
 * and §9.1.3 sets the floor at 3-4 AI-measured tests.
 */
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
    // Manual entry. The SCALE measures the weight; a camera can at best read
    // the digits off its display, which is transcription rather than
    // measurement. §2's target is "correct entry with OCR assist" — not a
    // tolerance — and §5.2 makes manual entry the baseline with OCR optional.
    // OCR of the display remains a possible convenience later; it would not
    // make this an AI-measured test.
    WEIGHT(
        2, "weight", 0, 0, 100 * KB, 1 * MB, 1, "kg", Measurement.MANUAL,
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
        5, "broad_jump", 15, 30, 3 * MB, 15 * MB, 3, "cm", Measurement.AI_PLANNED,
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
        7, "sprint_30m", 10, 45, 2 * MB, 25 * MB, 2, "s", Measurement.AI_PLANNED,
    ),
    // Stretch. Turn detection is a velocity sign change and the ±5 s tolerance
    // is vast, so TIMING is easy. Touch verification at the turn line is the
    // hard part — §3 states it is "hard to verify from video" — so it should be
    // flagged amber rather than silently accepted.
    SHUTTLE_RUN(
        8, "shuttle_run", 15, 60, 3 * MB, 30 * MB, 2, "s", Measurement.AI_PLANNED,
    ),
    SIT_UPS(
        9, "sit_ups", 30, 60, 5 * MB, 30 * MB, 1, "reps", Measurement.AI,
    ),
    // Manual. The ALGORITHM is easy — lap crossings at the start/finish line
    // are the same detection as the sprint, against a ±10 s tolerance. What
    // defeats it is the CAPTURE: 6-12 minutes of video per heat, a 400 m track
    // so that start and finish coincide, and identity attribution for every
    // crossing of every athlete. Operationally impractical here, so the
    // assessor times it and enters the result.
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

        /** Tests this build actually measures with on-device AI. */
        val aiMeasured: List<BatteryTest>
            get() = entries.filter { it.measurement == Measurement.AI }

        /** Tests whose AI method is designed but not yet implemented. */
        val aiPlanned: List<BatteryTest>
            get() = entries.filter { it.measurement == Measurement.AI_PLANNED }
    }
}

private const val KB = 1024L
private const val MB = 1024L * 1024L
