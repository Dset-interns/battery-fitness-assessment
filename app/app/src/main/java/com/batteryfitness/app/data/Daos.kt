package com.batteryfitness.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AthleteDao {
    @Insert suspend fun insert(athlete: Athlete): Long
    @Update suspend fun update(athlete: Athlete)

    @Query("SELECT * FROM athletes ORDER BY name")
    fun observeAll(): Flow<List<Athlete>>

    @Query("SELECT * FROM athletes WHERE id = :id")
    suspend fun byId(id: Long): Athlete?

    @Query("SELECT * FROM athletes WHERE externalId = :externalId LIMIT 1")
    suspend fun byExternalId(externalId: String): Athlete?

    @Query(
        """
        SELECT a.* FROM athletes a
        INNER JOIN session_athletes sa ON sa.athleteId = a.id
        WHERE sa.sessionId = :sessionId
        ORDER BY sa.orderIndex
        """
    )
    suspend fun rosterFor(sessionId: Long): List<Athlete>
}

@Dao
interface SessionDao {
    @Insert suspend fun insert(session: Session): Long
    @Update suspend fun update(session: Session)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addToRoster(entry: SessionAthlete)

    @Query("SELECT * FROM sessions ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Session>>

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun byId(id: Long): Session?

    /** §6.5 — partly-completed sessions must be resumable, so these persist. */
    @Query("SELECT * FROM sessions WHERE completedAt IS NULL ORDER BY createdAt DESC")
    suspend fun openSessions(): List<Session>

    @Transaction
    suspend fun createWithRoster(session: Session, athleteIds: List<Long>): Long {
        val id = insert(session)
        athleteIds.forEachIndexed { index, athleteId ->
            addToRoster(SessionAthlete(sessionId = id, athleteId = athleteId, orderIndex = index))
        }
        return id
    }
}

@Dao
interface CaptureDao {
    @Insert suspend fun insert(capture: SourceCapture): Long

    @Query("SELECT * FROM captures WHERE sessionId = :sessionId")
    suspend fun forSession(sessionId: Long): List<SourceCapture>

    @Query("SELECT * FROM captures WHERE sha256 = :sha LIMIT 1")
    suspend fun byHash(sha: String): SourceCapture?

    /**
     * §6.3 — once synced the user may delete local video to save space. The
     * metadata row is retained so the result keeps its provenance.
     */
    @Query("UPDATE captures SET deletedAfterSync = 1 WHERE id = :id")
    suspend fun markVideoDeleted(id: Long)
}

@Dao
interface ResultDao {
    @Insert suspend fun insert(result: Result): Long

    @Query("SELECT * FROM results WHERE sessionId = :sessionId")
    fun observeSession(sessionId: Long): Flow<List<Result>>

    @Query("SELECT * FROM results WHERE sessionId = :sessionId")
    suspend fun forSession(sessionId: Long): List<Result>

    @Query(
        """
        SELECT * FROM results
        WHERE sessionId = :sessionId AND athleteId = :athleteId AND testKey = :testKey
        ORDER BY attemptNo, revision
        """
    )
    suspend fun attemptsFor(sessionId: Long, athleteId: Long, testKey: String): List<Result>

    /**
     * Best valid attempt. The Handbook scores "best of N" for jumps, throws,
     * sprint and sit & reach — and a foul nullifies an attempt, so invalid rows
     * are excluded here rather than deleted.
     *
     * `highIsBetter` distinguishes distance/count tests from timed tests.
     */
    @Query(
        """
        SELECT * FROM results
        WHERE sessionId = :sessionId AND athleteId = :athleteId
          AND testKey = :testKey AND valid = 1 AND value IS NOT NULL
        ORDER BY CASE WHEN :highIsBetter THEN -value ELSE value END
        LIMIT 1
        """
    )
    suspend fun bestAttempt(
        sessionId: Long,
        athleteId: Long,
        testKey: String,
        highIsBetter: Boolean,
    ): Result?

    @Query("SELECT * FROM results WHERE syncedAt IS NULL")
    suspend fun pendingSync(): List<Result>

    @Query("UPDATE results SET syncedAt = :at WHERE id = :id")
    suspend fun markSynced(id: Long, at: Long)

    /**
     * Highest revision recorded for a cell, so a correction appends rather than
     * overwrites (Submission §4.2.5 — a synced result is never clobbered).
     */
    @Query(
        """
        SELECT COALESCE(MAX(revision), 0) FROM results
        WHERE sessionId = :sessionId AND athleteId = :athleteId
          AND testKey = :testKey AND attemptNo = :attemptNo
        """
    )
    suspend fun maxRevision(
        sessionId: Long,
        athleteId: Long,
        testKey: String,
        attemptNo: Int,
    ): Int
}
