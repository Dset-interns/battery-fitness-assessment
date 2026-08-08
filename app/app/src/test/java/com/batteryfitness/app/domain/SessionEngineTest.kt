package com.batteryfitness.app.domain

import com.batteryfitness.app.data.Sequencing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The central correctness requirement of the session engine, from Concept
 * Note §6.1:
 *
 *   "The app must produce identical, correctly indexed results regardless of
 *    which sequence is used."
 *
 * These tests exist because that sentence is easy to agree with and easy to
 * violate — the usual failure is a data model that keys results by position in
 * the running order, which silently misattributes every result the moment an
 * assessor switches modes or resumes a part-finished session.
 */
class SessionEngineTest {

    private val roster = listOf(101L, 102L, 103L)
    private val tests = applicableTests()

    @Test
    fun `both sequencings visit exactly the same cells`() {
        val athleteWise = SessionEngine.traversal(roster, tests, Sequencing.ATHLETE_WISE)
        val testWise = SessionEngine.traversal(roster, tests, Sequencing.TEST_WISE)

        assertEquals("both must cover 3 athletes x 10 tests", 30, athleteWise.size)
        assertEquals(30, testWise.size)
        assertEquals("same set of cells", athleteWise.toSet(), testWise.toSet())
        assertNotEquals("but a different order", athleteWise, testWise)
        assertTrue(SessionEngine.orderEquivalence(roster, tests))
    }

    @Test
    fun `athlete-wise finishes one athlete before starting the next`() {
        val order = SessionEngine.traversal(roster, tests, Sequencing.ATHLETE_WISE)
        val firstTen = order.take(tests.size)
        assertTrue(
            "the first block must all belong to one athlete",
            firstTen.all { it.athleteId == roster.first() },
        )
    }

    @Test
    fun `test-wise runs the whole roster through one test before moving on`() {
        val order = SessionEngine.traversal(roster, tests, Sequencing.TEST_WISE)
        val firstBlock = order.take(roster.size)
        assertTrue(
            "the first block must all be the same test",
            firstBlock.all { it.test == tests.first() },
        )
        assertEquals(
            "and cover every athlete once",
            roster.toSet(),
            firstBlock.map { it.athleteId }.toSet(),
        )
    }

    @Test
    fun `no cell is ever visited twice`() {
        for (seq in Sequencing.entries) {
            val order = SessionEngine.traversal(roster, tests, seq)
            assertEquals("$seq produced duplicates", order.size, order.toSet().size)
        }
    }

    @Test
    fun `resume cursor skips completed cells`() {
        val completed = setOf(
            Cell(101L, BatteryTest.HEIGHT),
            Cell(101L, BatteryTest.WEIGHT),
        )
        val next = SessionEngine.nextIncomplete(
            roster, tests, Sequencing.ATHLETE_WISE, completed,
        )
        assertEquals(Cell(101L, BatteryTest.SIT_AND_REACH), next)
    }

    @Test
    fun `resume cursor is null only when everything is done`() {
        val all = SessionEngine.traversal(roster, tests, Sequencing.TEST_WISE).toSet()
        assertNull(
            SessionEngine.nextIncomplete(roster, tests, Sequencing.TEST_WISE, all),
        )
    }

    @Test
    fun `switching sequencing mid-session preserves completed work`() {
        // Assessor starts athlete-wise, completes 12 cells, then switches to
        // station mode. Nothing already recorded may be re-requested.
        val started = SessionEngine.traversal(roster, tests, Sequencing.ATHLETE_WISE)
        val completed = started.take(12).toSet()

        val next = SessionEngine.nextIncomplete(
            roster, tests, Sequencing.TEST_WISE, completed,
        )
        assertTrue("must not re-request completed work", next !in completed)

        val remainingAfterSwitch = SessionEngine
            .traversal(roster, tests, Sequencing.TEST_WISE)
            .filter { it !in completed }
        assertEquals("18 cells outstanding either way", 18, remainingAfterSwitch.size)
    }

    @Test
    fun `progress is identical under both sequencings`() {
        val completed = SessionEngine
            .traversal(roster, tests, Sequencing.ATHLETE_WISE)
            .take(17).toSet()

        val p = SessionEngine.progress(roster, tests, completed)
        assertEquals(17, p.done)
        assertEquals(30, p.total)

        // per-athlete and per-test breakdowns must sum back to the same total
        assertEquals(17, SessionEngine.perAthlete(roster, tests, completed).values.sumOf { it.done })
        assertEquals(17, SessionEngine.perTest(roster, tests, completed).values.sumOf { it.done })
    }

    @Test
    fun `empty roster does not crash or divide by zero`() {
        val p = SessionEngine.progress(emptyList(), tests, emptySet())
        assertEquals(0, p.total)
        assertEquals(0f, p.fraction, 0.0001f)
        assertNull(
            SessionEngine.nextIncomplete(emptyList(), tests, Sequencing.TEST_WISE, emptySet()),
        )
    }

    // ---- Handbook protocol rules -----------------------------------------

    @Test
    fun `medicine ball mass follows Handbook Test VII`() {
        // "1 kg medicine ball shall be used for girls and under 12 boys
        //  category, whereas for boys above 12 years the weight shall be 2 kg."
        assertEquals(2, medicineBallKgFor(ageYears = 14, isMale = true))
        assertEquals(1, medicineBallKgFor(ageYears = 11, isMale = true))
        assertEquals(1, medicineBallKgFor(ageYears = 14, isMale = false))
        assertEquals(1, medicineBallKgFor(ageYears = 17, isMale = false))
        // boundary: "above 12" means 12 itself still uses 1 kg
        assertEquals(1, medicineBallKgFor(ageYears = 12, isMale = true))
    }

    @Test
    fun `sit-up window and endurance distance follow the age-group protocol`() {
        assertEquals(30, BatteryTest.SIT_UPS.windowSecondsFor(9))
        assertEquals(45, BatteryTest.SIT_UPS.windowSecondsFor(12))
        assertEquals(800, BatteryTest.ENDURANCE_RUN.enduranceDistanceMetresFor(11))
        assertEquals(1600, BatteryTest.ENDURANCE_RUN.enduranceDistanceMetresFor(12))
        assertNull("only sit-ups carry a timed window", BatteryTest.HEIGHT.windowSecondsFor(10))
    }

    @Test
    fun `size tolerance is 50 percent per section 6-3-1`() {
        // "Apply the size limit with a 50% tolerance so that teams capturing at
        //  higher quality are not penalised for larger files."
        val t = BatteryTest.SIT_UPS
        assertEquals(t.maxSizeBytes * 3 / 2, t.maxSizeWithTolerance)
    }

    @Test
    fun `at least the mandatory minimum of tests are AI-measured`() {
        // Submission §9.1.3 sets the floor at 3-4 AI-measured tests.
        assertTrue(
            "AI coverage below the mandatory floor",
            BatteryTest.aiMeasured.size >= 3,
        )
    }
}
