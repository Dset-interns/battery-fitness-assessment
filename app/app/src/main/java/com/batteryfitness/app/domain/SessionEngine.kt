package com.batteryfitness.app.domain

import com.batteryfitness.app.data.Sequencing

/**
 * One addressable slot in the assessment: a specific athlete, a specific test.
 *
 * Attempts hang off a cell rather than being part of its identity, because the
 * Handbook's attempt counts differ per test (three for the jumps and sit &
 * reach, two for the throw and sprint, one for sit-ups and the endurance run).
 */
data class Cell(val athleteId: Long, val test: BatteryTest)

/**
 * Session traversal.
 *
 * The entire difference between athlete-wise and test-wise sequencing lives in
 * this file — it is the ORDER in which cells are visited, and nothing else.
 * Storage, indexing and scoring are identical either way, which is how Concept
 * Note §6.1's requirement is satisfied:
 *
 *   "The app must produce identical, correctly indexed results regardless of
 *    which sequence is used."
 *
 * Making that a property of the data model rather than something to be verified
 * by testing is the point. `orderEquivalence` below proves it.
 */
object SessionEngine {

    /**
     * The visiting order for a session.
     *
     * ATHLETE_WISE — athlete A does every test, then athlete B.
     * TEST_WISE    — every athlete does test 1, then every athlete does test 2.
     *
     * §6.5 notes test-wise "mirrors real ground practice — a single test is
     * administered to a lined-up group before moving on — and significantly
     * reduces total assessment time", because the station is set up once per
     * test rather than once per athlete.
     */
    fun traversal(
        roster: List<Long>,
        tests: List<BatteryTest>,
        sequencing: Sequencing,
    ): List<Cell> = when (sequencing) {
        Sequencing.ATHLETE_WISE ->
            roster.flatMap { athlete -> tests.map { Cell(athlete, it) } }
        Sequencing.TEST_WISE ->
            tests.flatMap { test -> roster.map { Cell(it, test) } }
    }

    /**
     * The resume cursor: the first cell in traversal order that has no result.
     *
     * §6.5: "Athletes don't have to finish all their tests in one sitting: the
     * app keeps partly-completed assessments so the assessor can come back and
     * finish them later."
     */
    fun nextIncomplete(
        roster: List<Long>,
        tests: List<BatteryTest>,
        sequencing: Sequencing,
        completed: Set<Cell>,
    ): Cell? = traversal(roster, tests, sequencing).firstOrNull { it !in completed }

    /** Progress across the whole session, for the roster screen. */
    fun progress(
        roster: List<Long>,
        tests: List<BatteryTest>,
        completed: Set<Cell>,
    ): Progress {
        val total = roster.size * tests.size
        val done = completed.count { it.athleteId in roster && it.test in tests }
        return Progress(done = done, total = total)
    }

    /** Per-athlete completion, so the roster can show "6/10" against each name. */
    fun perAthlete(
        roster: List<Long>,
        tests: List<BatteryTest>,
        completed: Set<Cell>,
    ): Map<Long, Progress> = roster.associateWith { athlete ->
        Progress(
            done = tests.count { Cell(athlete, it) in completed },
            total = tests.size,
        )
    }

    /** Per-test completion across the roster, for station (test-wise) mode. */
    fun perTest(
        roster: List<Long>,
        tests: List<BatteryTest>,
        completed: Set<Cell>,
    ): Map<BatteryTest, Progress> = tests.associateWith { test ->
        Progress(
            done = roster.count { Cell(it, test) in completed },
            total = roster.size,
        )
    }

    /**
     * The invariant, expressed as code rather than prose: both sequencings
     * address exactly the same set of cells, differing only in order.
     */
    fun orderEquivalence(roster: List<Long>, tests: List<BatteryTest>): Boolean {
        val a = traversal(roster, tests, Sequencing.ATHLETE_WISE)
        val t = traversal(roster, tests, Sequencing.TEST_WISE)
        return a.toSet() == t.toSet() && a.size == t.size
    }
}

data class Progress(val done: Int, val total: Int) {
    val complete: Boolean get() = total > 0 && done == total
    val fraction: Float get() = if (total == 0) 0f else done.toFloat() / total
    override fun toString(): String = "$done/$total"
}

/**
 * Which tests apply to an athlete.
 *
 * All ten apply to everyone; the age-group variation is in the protocol, not in
 * the selection (sit-ups 30 s vs 45 s, endurance 800 m vs 1.6 km, medicine ball
 * 1 kg vs 2 kg). Kept as a function so future per-cohort subsets have a home.
 */
fun applicableTests(): List<BatteryTest> = BatteryTest.entries.toList()

/**
 * Medicine ball mass, from Handbook Test VII: 1 kg for girls and for boys under
 * 12; 2 kg for boys above 12.
 *
 * The app knows age and gender at capture time, so it can enforce a rule a busy
 * assessor may get wrong — a data-integrity win independent of any measurement.
 */
fun medicineBallKgFor(ageYears: Int, isMale: Boolean): Int =
    if (isMale && ageYears > 12) 2 else 1
