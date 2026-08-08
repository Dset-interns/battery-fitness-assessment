package com.batteryfitness.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

// ---------------------------------------------------------------------------
// Enumerations
// ---------------------------------------------------------------------------

/** Concept Note §6.1 — the two app modes. */
enum class SessionMode { INDIVIDUAL, COACH }

/**
 * Concept Note §6.1/§6.5 — the assessor chooses at session start.
 *
 * ATHLETE_WISE: one athlete completes all applicable tests, then the next.
 * TEST_WISE:    one test is run across the whole roster, then the next test.
 *               This mirrors real ground practice and is the faster mode.
 */
enum class Sequencing { ATHLETE_WISE, TEST_WISE }

/** Which identity scheme the athlete registered under (§6.5 step 1). */
enum class IdType { APAAR, AADHAAR, NSRS, LOCAL }

enum class Gender { MALE, FEMALE, OTHER }

/** Confidence banding — §6.2(c) and §8.2. Mirrors the Python lab exactly. */
enum class Band { GREEN, AMBER, RED }

class Converters {
    @TypeConverter fun modeTo(v: SessionMode) = v.name
    @TypeConverter fun modeFrom(v: String) = SessionMode.valueOf(v)
    @TypeConverter fun seqTo(v: Sequencing) = v.name
    @TypeConverter fun seqFrom(v: String) = Sequencing.valueOf(v)
    @TypeConverter fun idTypeTo(v: IdType) = v.name
    @TypeConverter fun idTypeFrom(v: String) = IdType.valueOf(v)
    @TypeConverter fun genderTo(v: Gender) = v.name
    @TypeConverter fun genderFrom(v: String) = Gender.valueOf(v)
    @TypeConverter fun bandTo(v: Band) = v.name
    @TypeConverter fun bandFrom(v: String) = Band.valueOf(v)
}

// ---------------------------------------------------------------------------
// Entities
// ---------------------------------------------------------------------------

@Entity(tableName = "athletes", indices = [Index(value = ["externalId"], unique = false)])
data class Athlete(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /**
     * APAAR / Aadhaar / NSRS identifier as entered.
     *
     * §6.5: an NSRS ID is NOT created speculatively — it is generated only for
     * athletes who complete the full battery, to keep preliminary and
     * non-qualifying participants off the central database. So this is nullable
     * and stays null for a walk-in athlete until completion.
     */
    val externalId: String? = null,
    val idType: IdType = IdType.LOCAL,
    val ageYears: Int,
    val gender: Gender,
    val school: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "sessions")
data class Session(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mode: SessionMode,
    val sequencing: Sequencing,
    val assessorName: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    /**
     * §6.5: "Athletes don't have to finish all their tests in one sitting: the
     * app keeps partly-completed assessments so the assessor can come back and
     * finish them later." A session is therefore never implicitly closed.
     */
    val completedAt: Long? = null,
)

/** Roster membership. `orderIndex` fixes the athlete order within the session. */
@Entity(
    tableName = "session_athletes",
    primaryKeys = ["sessionId", "athleteId"],
    foreignKeys = [
        ForeignKey(Session::class, ["id"], ["sessionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(Athlete::class, ["id"], ["athleteId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("athleteId")],
)
data class SessionAthlete(
    val sessionId: Long,
    val athleteId: Long,
    val orderIndex: Int,
)

/**
 * One recorded media file.
 *
 * Deliberately NOT keyed to a single athlete. Handbook Test VIII runs
 * "athletes (2 together)" and Test X is a mass start, so one capture
 * legitimately serves several athletes. A naive one-video-per-athlete-per-test
 * model cannot represent that; this one can, via `Result.sourceCaptureId`.
 */
@Entity(
    tableName = "captures",
    foreignKeys = [
        ForeignKey(Session::class, ["id"], ["sessionId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("sessionId")],
)
data class SourceCapture(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val testKey: String,
    val filePath: String,
    /** SHA-256 at capture time — the basis of the §6.3 authenticity chain. */
    val sha256: String,
    val fps: Double,
    val widthPx: Int,
    val heightPx: Int,
    val durationMs: Long,
    val sizeBytes: Long,
    // §6.3: every video is auto-tagged with timestamp and GPS location.
    val capturedAt: Long,
    val gpsLat: Double? = null,
    val gpsLon: Double? = null,
    /** True when the capture covers more than one athlete (shuttle, endurance). */
    val isShared: Boolean = false,
    /** Setup parameters logged at capture, so confidence can cite them later. */
    val setupLog: String? = null,
    val deletedAfterSync: Boolean = false,
)

/**
 * One cell of the sparse (athlete × test × attempt) matrix.
 *
 * This shape is what makes Concept Note §6.1's guarantee — "identical,
 * correctly indexed results regardless of which sequence is used" — true by
 * construction rather than by testing. Sequencing is purely a traversal order
 * over this matrix and never changes how a result is stored.
 */
@Entity(
    tableName = "results",
    foreignKeys = [
        ForeignKey(Session::class, ["id"], ["sessionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(Athlete::class, ["id"], ["athleteId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [
        Index(value = ["sessionId", "athleteId", "testKey", "attemptNo"], unique = true),
        Index("athleteId"),
        Index("sourceCaptureId"),
    ],
)
data class Result(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val athleteId: Long,
    val testKey: String,
    val attemptNo: Int,

    val value: Double?,
    val unit: String,

    // §6.2(c): the measurement must arrive with a confidence indicator, and
    // §8.2 requires the reason to be surfaced rather than the number silently
    // being wrong.
    val confidence: Band,
    val confidenceReasons: String? = null,
    /** Which rung of the measurement ladder produced this number (1..5). */
    val methodRung: Int,
    val measuredByAi: Boolean,

    /** Handbook foul handling — invalid attempts are recorded, not discarded. */
    val valid: Boolean = true,
    val foulRule: String? = null,
    val foulDetail: String? = null,

    /** Per-rep audit detail for sit-ups, serialised as JSON. */
    val detailJson: String? = null,

    val sourceCaptureId: Long? = null,

    /**
     * Sync conflict resolution (Submission §4.2.5): a synced result is never
     * overwritten. A correction appends a new revision and the audit trail
     * survives.
     */
    val revision: Int = 1,
    val deviceId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val syncedAt: Long? = null,
)
