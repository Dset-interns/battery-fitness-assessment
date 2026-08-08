package com.batteryfitness.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Athlete::class, Session::class, SessionAthlete::class,
        SourceCapture::class, Result::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class BatteryDatabase : RoomDatabase() {
    abstract fun athletes(): AthleteDao
    abstract fun sessions(): SessionDao
    abstract fun captures(): CaptureDao
    abstract fun results(): ResultDao

    companion object {
        @Volatile private var instance: BatteryDatabase? = null

        fun get(context: Context): BatteryDatabase = instance ?: synchronized(this) {
            instance ?: build(context).also { instance = it }
        }

        private fun build(context: Context): BatteryDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                BatteryDatabase::class.java,
                "battery_fitness.db",
            )
                // Write-ahead logging is the crash-safety requirement from
                // Concept Note §6.3 ("no data loss on app crash") and
                // Submission §4.1.4. A mid-test crash leaves a recoverable
                // record rather than a truncated database.
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .addCallback(object : Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        db.execSQL("PRAGMA foreign_keys = ON")
                        // Durability over speed: this app records data that
                        // cannot be re-collected once the athletes have gone home.
                        db.execSQL("PRAGMA synchronous = FULL")
                    }
                })
                .build()

        // NOTE: SQLCipher passphrase-backed encryption (Concept Note §6.3
        // "encrypted local storage") is wired in Phase 3 alongside encrypted
        // video-at-rest, with the key held in the Android Keystore. Kept as a
        // separate step so the schema can be verified independently.
    }
}
