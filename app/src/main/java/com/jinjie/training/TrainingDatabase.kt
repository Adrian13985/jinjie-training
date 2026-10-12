package com.jinjie.training

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.ColumnInfo
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName = "sessions", indices = [Index(value = ["date"])])
data class TrainingSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val workoutCode: String,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val status: String = "ACTIVE"
)

@Entity(tableName = "set_logs", indices = [Index(value = ["sessionId"])])
data class SetLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val exerciseId: String,
    val exerciseName: String,
    val setNumber: Int,
    val targetReps: String,
    val restSeconds: Int,
    val weightKg: Double? = null,
    val actualReps: Int? = null,
    val rir: Int? = null,
    val distanceCm: Double? = null,
    val completed: Boolean = false,
    val note: String = ""
)

@Entity(tableName = "daily_checkins")
data class DailyCheckin(
    @PrimaryKey val date: String,
    val status: String,
    val weightKg: Double? = null,
    val energy: Int? = null
)

@Entity(tableName = "day_schedule")
data class DaySchedule(@PrimaryKey val date: String, val workoutCode: String, @ColumnInfo(defaultValue = "''") val note: String = "")

@Entity(tableName = "set_suggestions", primaryKeys = ["date", "exerciseId", "setNumber"], indices = [Index(value = ["date"])])
data class SetSuggestion(
    val date: String,
    val exerciseId: String,
    val setNumber: Int,
    val weightKg: Double? = null,
    val guidance: String = ""
)

@Entity(tableName = "plan_adjustments", primaryKeys = ["workoutCode", "exerciseId"])
data class PlanAdjustment(
    val workoutCode: String,
    val exerciseId: String,
    val replacementName: String? = null,
    val setCount: Int? = null
)

@Dao
interface TrainingDao {
    @Query("SELECT * FROM sessions WHERE date = :date ORDER BY id DESC LIMIT 1")
    suspend fun sessionOn(date: String): TrainingSession?

    @Query("SELECT * FROM sessions ORDER BY date DESC, id DESC")
    suspend fun allSessions(): List<TrainingSession>

    @Query("SELECT * FROM set_logs WHERE sessionId = :sessionId ORDER BY id")
    suspend fun setsFor(sessionId: Long): List<SetLog>

    @Query("SELECT * FROM set_logs ORDER BY id")
    suspend fun allSets(): List<SetLog>

    @Query("SELECT * FROM set_logs WHERE exerciseId = :exerciseId AND completed = 1 ORDER BY id DESC LIMIT 1")
    suspend fun lastCompletedSet(exerciseId: String): SetLog?

    @Query("SELECT * FROM daily_checkins WHERE date = :date LIMIT 1")
    suspend fun checkinOn(date: String): DailyCheckin?

    @Query("SELECT * FROM daily_checkins ORDER BY date DESC")
    suspend fun allCheckins(): List<DailyCheckin>

    @Query("SELECT * FROM day_schedule WHERE date = :date LIMIT 1")
    suspend fun scheduleOn(date: String): DaySchedule?

    @Query("SELECT * FROM day_schedule ORDER BY date")
    suspend fun allSchedules(): List<DaySchedule>

    @Query("SELECT * FROM set_suggestions WHERE date = :date ORDER BY exerciseId, setNumber")
    suspend fun suggestionsOn(date: String): List<SetSuggestion>

    @Query("SELECT * FROM set_suggestions ORDER BY date, exerciseId, setNumber")
    suspend fun allSuggestions(): List<SetSuggestion>

    @Query("SELECT * FROM plan_adjustments")
    suspend fun allAdjustments(): List<PlanAdjustment>

    @Upsert
    suspend fun saveAdjustment(adjustment: PlanAdjustment)

    @Insert
    suspend fun insertSession(session: TrainingSession): Long

    @Insert
    suspend fun insertSets(sets: List<SetLog>)

    @Upsert
    suspend fun saveSession(session: TrainingSession)

    @Upsert
    suspend fun saveSet(set: SetLog)

    @Upsert
    suspend fun saveCheckin(checkin: DailyCheckin)

    @Upsert
    suspend fun saveSchedule(schedule: DaySchedule)

    @Upsert
    suspend fun saveSuggestions(rows: List<SetSuggestion>)

    @Query("DELETE FROM set_logs WHERE id = :id")
    suspend fun deleteSet(id: Long)

    @Query("DELETE FROM set_logs")
    suspend fun clearSets()

    @Query("DELETE FROM sessions")
    suspend fun clearSessions()

    @Query("DELETE FROM daily_checkins")
    suspend fun clearCheckins()

    @Query("DELETE FROM day_schedule")
    suspend fun clearSchedules()

    @Query("DELETE FROM set_suggestions WHERE date = :date")
    suspend fun deleteSuggestionsOn(date: String)

    @Query("DELETE FROM set_suggestions")
    suspend fun clearSuggestions()

    @Query("DELETE FROM plan_adjustments")
    suspend fun clearAdjustments()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreSessions(rows: List<TrainingSession>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreSets(rows: List<SetLog>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreCheckins(rows: List<DailyCheckin>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreSchedules(rows: List<DaySchedule>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreSuggestions(rows: List<SetSuggestion>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreAdjustments(rows: List<PlanAdjustment>)
}

@Database(
    entities = [TrainingSession::class, SetLog::class, DailyCheckin::class, DaySchedule::class, PlanAdjustment::class, SetSuggestion::class],
    version = 2,
    exportSchema = true
)
abstract class TrainingDatabase : RoomDatabase() {
    abstract fun dao(): TrainingDao

    companion object {
        @Volatile private var instance: TrainingDatabase? = null
        private val migration1To2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE day_schedule ADD COLUMN note TEXT NOT NULL DEFAULT ''")
                db.execSQL("CREATE TABLE IF NOT EXISTS set_suggestions (date TEXT NOT NULL, exerciseId TEXT NOT NULL, setNumber INTEGER NOT NULL, weightKg REAL, guidance TEXT NOT NULL, PRIMARY KEY(date, exerciseId, setNumber))")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_set_suggestions_date ON set_suggestions (date)")
            }
        }

        fun get(context: Context): TrainingDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                TrainingDatabase::class.java,
                "training.db"
            ).addMigrations(migration1To2).build().also { instance = it }
        }
    }
}
