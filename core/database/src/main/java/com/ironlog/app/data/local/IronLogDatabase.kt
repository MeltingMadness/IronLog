package com.ironlog.app.data.local

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ironlog.app.data.local.dao.ExerciseDao
import com.ironlog.app.data.local.dao.MetaTrainingPlanDao
import com.ironlog.app.data.local.dao.PersonalRecordDao
import com.ironlog.app.data.local.dao.TrainingPlanDao
import com.ironlog.app.data.local.dao.WorkoutSessionDao
import com.ironlog.app.data.local.dao.WorkoutSetDao
import com.ironlog.app.data.local.entity.ExerciseEntity
import com.ironlog.app.data.local.entity.MetaPlanItemEntity
import com.ironlog.app.data.local.entity.MetaTrainingPlanEntity
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
        PlanExerciseEntity::class,
        MetaTrainingPlanEntity::class,
        MetaPlanItemEntity::class
    ],
    version = 8,
    exportSchema = true
)
abstract class IronLogDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutSessionDao(): WorkoutSessionDao
    abstract fun workoutSetDao(): WorkoutSetDao
    abstract fun personalRecordDao(): PersonalRecordDao
    abstract fun trainingPlanDao(): TrainingPlanDao
    abstract fun metaTrainingPlanDao(): MetaTrainingPlanDao

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

        /** Migration 4 -> 5: Add supersetGroupId to plan_exercises */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `plan_exercises` ADD COLUMN `supersetGroupId` INTEGER")
            }
        }

        /** Migration 5 -> 6: Meta plans and workout session plan references */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `workout_sessions` ADD COLUMN `planId` INTEGER")
                db.execSQL("ALTER TABLE `workout_sessions` ADD COLUMN `metaPlanId` INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_sessions_planId` ON `workout_sessions` (`planId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_sessions_metaPlanId` ON `workout_sessions` (`metaPlanId`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `meta_training_plans` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `meta_plan_items` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `metaPlanId` INTEGER NOT NULL,
                        `trainingPlanId` INTEGER NOT NULL,
                        `orderIndex` INTEGER NOT NULL,
                        FOREIGN KEY(`metaPlanId`) REFERENCES `meta_training_plans`(`id`) ON DELETE CASCADE,
                        FOREIGN KEY(`trainingPlanId`) REFERENCES `training_plans`(`id`) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_meta_plan_items_metaPlanId` ON `meta_plan_items` (`metaPlanId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_meta_plan_items_trainingPlanId` ON `meta_plan_items` (`trainingPlanId`)")
            }
        }

        /** Migration 6 -> 7: Exercise notes + archive state */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `exercises` ADD COLUMN `notes` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `exercises` ADD COLUMN `isArchived` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_exercises_isArchived` ON `exercises` (`isArchived`)")
            }
        }

        /** Migration 7 -> 8: Normalize archive index for exercises */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_exercises_isArchived` ON `exercises` (`isArchived`)")
            }
        }

        @VisibleForTesting
        fun migration5To6ForTests(): Migration = MIGRATION_5_6

        @VisibleForTesting
        fun migration6To7ForTests(): Migration = MIGRATION_6_7

        @VisibleForTesting
        fun migration7To8ForTests(): Migration = MIGRATION_7_8

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

        /**
         * Defensively repairs legacy schemas from early pre-release builds that were
         * version-bumped without all expected tables/columns.
         */
        private fun ensureRuntimeSchemaCompatibility(db: SupportSQLiteDatabase) {
            addColumnIfMissing(
                db = db,
                table = "plan_exercises",
                column = "supersetGroupId",
                alterSql = "ALTER TABLE `plan_exercises` ADD COLUMN `supersetGroupId` INTEGER"
            )
            addColumnIfMissing(
                db = db,
                table = "workout_sessions",
                column = "planId",
                alterSql = "ALTER TABLE `workout_sessions` ADD COLUMN `planId` INTEGER"
            )
            addColumnIfMissing(
                db = db,
                table = "workout_sessions",
                column = "metaPlanId",
                alterSql = "ALTER TABLE `workout_sessions` ADD COLUMN `metaPlanId` INTEGER"
            )
            addColumnIfMissing(
                db = db,
                table = "exercises",
                column = "notes",
                alterSql = "ALTER TABLE `exercises` ADD COLUMN `notes` TEXT NOT NULL DEFAULT ''"
            )
            addColumnIfMissing(
                db = db,
                table = "exercises",
                column = "isArchived",
                alterSql = "ALTER TABLE `exercises` ADD COLUMN `isArchived` INTEGER NOT NULL DEFAULT 0"
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `meta_training_plans` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `meta_plan_items` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `metaPlanId` INTEGER NOT NULL,
                    `trainingPlanId` INTEGER NOT NULL,
                    `orderIndex` INTEGER NOT NULL,
                    FOREIGN KEY(`metaPlanId`) REFERENCES `meta_training_plans`(`id`) ON DELETE CASCADE,
                    FOREIGN KEY(`trainingPlanId`) REFERENCES `training_plans`(`id`) ON DELETE CASCADE
                )
                """.trimIndent()
            )

            if (hasColumn(db, "workout_sessions", "planId")) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_sessions_planId` ON `workout_sessions` (`planId`)")
            }
            if (hasColumn(db, "workout_sessions", "metaPlanId")) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_workout_sessions_metaPlanId` ON `workout_sessions` (`metaPlanId`)")
            }
            if (hasColumn(db, "exercises", "isArchived")) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_exercises_isArchived` ON `exercises` (`isArchived`)")
            }
            if (hasTable(db, "meta_plan_items")) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_meta_plan_items_metaPlanId` ON `meta_plan_items` (`metaPlanId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_meta_plan_items_trainingPlanId` ON `meta_plan_items` (`trainingPlanId`)")
            }
        }

        private fun addColumnIfMissing(
            db: SupportSQLiteDatabase,
            table: String,
            column: String,
            alterSql: String
        ) {
            if (!hasTable(db, table)) return
            if (hasColumn(db, table, column)) return
            db.execSQL(alterSql)
        }

        private fun hasTable(db: SupportSQLiteDatabase, table: String): Boolean {
            db.query(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ? LIMIT 1",
                arrayOf(table)
            ).use { cursor ->
                return cursor.moveToFirst()
            }
        }

        private fun hasColumn(db: SupportSQLiteDatabase, table: String, column: String): Boolean {
            if (!hasTable(db, table)) return false
            db.query("PRAGMA table_info(`$table`)").use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                if (nameIndex < 0) return false
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameIndex) == column) return true
                }
            }
            return false
        }

        fun create(context: Context): IronLogDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                IronLogDatabase::class.java,
                "ironlog.db"
            )
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8
                )
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
                        "INSERT OR IGNORE INTO exercises (name, primaryMuscleGroup, secondaryMuscleGroups, category, isCustom, notes, isArchived) VALUES (?, ?, ?, ?, ?, ?, ?)",
                        arrayOf<Any>(
                            exercise.name,
                            exercise.primaryMuscleGroup,
                            exercise.secondaryMuscleGroups,
                            exercise.category,
                            if (exercise.isCustom) 1 else 0,
                            exercise.notes,
                            if (exercise.isArchived) 1 else 0
                        )
                    )
                }
            }
        }

        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            ensureRuntimeSchemaCompatibility(db)
            createSingleActiveSessionTriggers(db)
        }
    }
}
