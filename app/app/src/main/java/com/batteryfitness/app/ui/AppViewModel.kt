package com.batteryfitness.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.batteryfitness.app.data.Athlete
import com.batteryfitness.app.data.BatteryDatabase
import com.batteryfitness.app.data.Gender
import com.batteryfitness.app.data.IdType
import com.batteryfitness.app.data.Result
import com.batteryfitness.app.data.Sequencing
import com.batteryfitness.app.data.Session
import com.batteryfitness.app.data.SessionMode
import com.batteryfitness.app.domain.BatteryTest
import com.batteryfitness.app.domain.Cell
import com.batteryfitness.app.domain.Progress
import com.batteryfitness.app.domain.SessionEngine
import com.batteryfitness.app.domain.applicableTests
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Screen state for an active assessment session.
 *
 * Note what is NOT here: any notion of "current index into a running order".
 * The cursor is derived from which cells have results (see [SessionEngine]),
 * so switching sequencing mid-session, or resuming days later, cannot
 * misattribute a result. Concept Note §6.1 and §6.5.
 */
data class SessionState(
    val sessionId: Long = 0,
    val mode: SessionMode = SessionMode.COACH,
    val sequencing: Sequencing = Sequencing.TEST_WISE,
    val roster: List<Athlete> = emptyList(),
    val tests: List<BatteryTest> = applicableTests(),
    val completed: Set<Cell> = emptySet(),
    val results: List<Result> = emptyList(),
) {
    val progress: Progress
        get() = SessionEngine.progress(roster.map { it.id }, tests, completed)

    val nextCell: Cell?
        get() = SessionEngine.nextIncomplete(
            roster.map { it.id }, tests, sequencing, completed,
        )

    val perAthlete: Map<Long, Progress>
        get() = SessionEngine.perAthlete(roster.map { it.id }, tests, completed)

    val perTest: Map<BatteryTest, Progress>
        get() = SessionEngine.perTest(roster.map { it.id }, tests, completed)

    fun athlete(id: Long): Athlete? = roster.firstOrNull { it.id == id }
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val db = BatteryDatabase.get(app)

    private val _session = MutableStateFlow(SessionState())
    val session: StateFlow<SessionState> = _session.asStateFlow()

    /** Room emits on every write, so screens observe rather than poll. */
    val athletes: Flow<List<Athlete>> = db.athletes().observeAll()

    // ---- Registration ----------------------------------------------------

    /**
     * Register an athlete.
     *
     * `externalId` may be an APAAR, Aadhaar or NSRS identifier. Per §6.5 an
     * NSRS ID is never minted here — it is issued only after the full battery
     * is completed, so a walk-in athlete simply has none.
     */
    fun registerAthlete(
        name: String,
        ageYears: Int,
        gender: Gender,
        externalId: String?,
        idType: IdType,
        school: String?,
        onDone: (Long) -> Unit = {},
    ) = viewModelScope.launch {
        val id = db.athletes().insert(
            Athlete(
                name = name.trim(),
                ageYears = ageYears,
                gender = gender,
                externalId = externalId?.trim()?.ifBlank { null },
                idType = idType,
                school = school?.trim()?.ifBlank { null },
            )
        )
        onDone(id)
    }

    // ---- Sessions --------------------------------------------------------

    fun startSession(
        mode: SessionMode,
        sequencing: Sequencing,
        athleteIds: List<Long>,
        assessorName: String?,
        onDone: (Long) -> Unit = {},
    ) = viewModelScope.launch {
        val id = db.sessions().createWithRoster(
            Session(mode = mode, sequencing = sequencing, assessorName = assessorName),
            athleteIds,
        )
        loadSession(id)
        onDone(id)
    }

    fun loadSession(sessionId: Long) = viewModelScope.launch {
        val s = db.sessions().byId(sessionId) ?: return@launch
        val roster = db.athletes().rosterFor(sessionId)
        val results = db.results().forSession(sessionId)
        _session.value = SessionState(
            sessionId = sessionId,
            mode = s.mode,
            sequencing = s.sequencing,
            roster = roster,
            completed = results.toCells(),
            results = results,
        )
    }

    /**
     * Change sequencing part-way through.
     *
     * Deliberately supported: the assessor may start athlete-wise and switch to
     * station mode when a queue forms. Because completion is tracked per cell,
     * nothing already recorded is re-requested.
     */
    fun setSequencing(sequencing: Sequencing) = viewModelScope.launch {
        val s = _session.value
        db.sessions().byId(s.sessionId)?.let {
            db.sessions().update(it.copy(sequencing = sequencing))
        }
        _session.update { it.copy(sequencing = sequencing) }
    }

    // ---- Results ---------------------------------------------------------

    /**
     * Record a result for one cell.
     *
     * Appends a revision rather than overwriting, so a correction never
     * destroys the previously synced value (Submission §4.2.5).
     */
    fun recordResult(result: Result, onDone: () -> Unit = {}) = viewModelScope.launch {
        val nextRevision = db.results().maxRevision(
            result.sessionId, result.athleteId, result.testKey, result.attemptNo,
        ) + 1
        db.results().insert(result.copy(revision = nextRevision))
        loadSession(result.sessionId).join()
        onDone()
    }

    private fun List<Result>.toCells(): Set<Cell> =
        mapNotNull { r ->
            BatteryTest.fromKey(r.testKey)?.let { Cell(r.athleteId, it) }
        }.toSet()
}
