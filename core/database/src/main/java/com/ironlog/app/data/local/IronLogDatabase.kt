package com.ironlog.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ironlog.app.data.local.dao.ExerciseDao
import com.ironlog.app.data.local.dao.PersonalRecordDao
import com.ironlog.app.data.local.dao.TrainingPlanDao
import com.ironlog.app.data.local.dao.WorkoutSessionDao
import com.ironlog.app.data.local.dao.WorkoutSetDao
import com.ironlog.app.data.local.entity.ExerciseEntity
import com.ironlog.app.data.local.entity.PersonalRecordEntity
import com.ironlog.app.data.local.entity.PlanExerciseEntity
import com.ironlog.app.data.local.entity.TrainingPlanEntity
import com.ironlog.app.data.local.entity.WorkoutSessionEntity
import com.ironlog.app.data.local.entity.WorkoutSetEntity
import com.ironlog.app.data.seed.ExerciseSeedData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        ExerciseEntity::class,
        WorkoutSessionEntity::class,
        WorkoutSetEntity::class,
        PersonalRecordEntity::class,
        TrainingPlanEntity::class,
        PlanExerciseEntity::class
    ],
    version = 4,
    exportSchema = true
)
abstract class IronLogDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutSessionDao(): WorkoutSessionDao
    abstract fun workoutSetDao(): WorkoutSetDao
    abstract fun personalRecordDao(): PersonalRecordDao
    abstract fun trainingPlanDao(): TrainingPlanDao

    companion object {
        /** Migration 1 -> 2: Training Plans feature */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `training_plans` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `plan_exercises` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `planId` INTEGER NOT NULL,
                        `exerciseId` INTEGER NOT NULL,
                        `orderIndex` INTEGER NOT NULL,
                        `targetSets` INTEGER NOT NULL DEFAULT 3,
                        `targetReps` INTEGER NOT NULL DEFAULT 10,
                        `targetWeightKg` REAL NOT NULL DEFAULT 0.0,
                        FOREIGN KEY(`planId`) REFERENCES `training_plans`(`id`) ON DELETE CASCADE,
                        FOREIGN KEY(`exerciseId`) REFERENCES `exercises`(`id`) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )

                db.execSQL("CREATE INDEX IF NOT EXISTS `index_plan_exercises_planId` ON `plan_exercises` (`planId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_plan_exercises_exerciseId` ON `plan_exercises` (`exerciseId`)")
            }
        }

        /** Migration 2 -> 3: enforce single active workout session */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                normalizeActiveSessions(db)
                createSingleActiveSessionTriggers(db)
            }
        }

        /** Migration 3 -> 4: Add rpe to workout_sets */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `workout_sets` ADD COLUMN `rpe` REAL DEFAULT NULL")
            }
        }

        private fun normalizeActiveSessions(db: SupportSQLiteDatabase) {
            val cursor = db.query(
                "SELECT id FROM workout_sessions WHERE endTime IS NULL ORDER BY startTime DESC"
            )

            val activeIds = mutableListOf<Long>()
            cursor.use {
                while (it.moveToNext()) {
                    activeIds.add(it.getLong(0))
                }
            }

            if (activeIds.size <= 1) return

            val idsToClose = activeIds.drop(1)
            for (id in idsToClose) {
                db.execSQL(
                    """
                    UPDATE workout_sessions
                    SET endTime = startTime,
                        durationSeconds = 0
                    WHERE id = ? AND endTime IS NULL
                    """.trimIndent(),
                    arrayOf<Any>(id)
                )
            }
        }

        private fun createSingleActiveSessionTriggers(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS `trg_workout_sessions_single_active_insert`
                BEFORE INSERT ON `workout_sessions`
                WHEN NEW.endTime IS NULL
                  AND EXISTS (SELECT 1 FROM `workout_sessions` WHERE endTime IS NULL)
                BEGIN
                    SELECT RAISE(ABORT, 'Only one active workout session is allowed');
                END
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS `trg_workout_sessions_single_active_update`
                BEFORE UPDATE OF `endTime` ON `workout_sessions`
                WHEN NEW.endTime IS NULL
                  AND OLD.endTime IS NOT NULL
                  AND EXISTS (
                      SELECT 1
                      FROM `workout_sessions`
                      WHERE endTime IS NULL AND id != OLD.id
                  )
                BEGIN
                    SELECT RAISE(ABORT, 'Only one active workout session is allowed');
                END
                """.trimIndent()
            )
        }

        fun create(context: Context): IronLogDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                IronLogDatabase::class.java,
                "ironlog.db"
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .addCallback(SeedCallback())
                .build()
        }
    }

    /**
     * Seeds exercise data and installs DB safety triggers on first create.
     */
    private class SeedCallback : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            createSingleActiveSessionTriggers(db)

            CoroutineScope(Dispatchers.IO).launch {
                val exercises = ExerciseSeedData.getAll()
                for (exercise in exercises) {
                    db.execSQL(
                        "INSERT OR IGNORE INTO exercises (name, primaryMuscleGroup, secondaryMuscleGroups, category, isCustom) VALUES (?, ?, ?, ?, ?)",
                        arrayOf<Any>(
                            exercise.name,
                            exercise.primaryMuscleGroup,
                            exercise.secondaryMuscleGroups,
                            exercise.category,
                            if (exercise.isCustom) 1 else 0
                        )
                    )
                }
            }
        }
    }
}
