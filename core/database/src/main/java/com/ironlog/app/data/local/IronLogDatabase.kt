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
import com.ironlog.app.data.local.dao.ProgressionDao
import com.ironlog.app.data.local.dao.TrainingPlanDao
import com.ironlog.app.data.local.dao.WorkoutSessionDao
import com.ironlog.app.data.local.dao.WorkoutSetDao
import com.ironlog.app.data.local.entity.ExerciseEntity
import com.ironlog.app.data.local.entity.MetaPlanItemEntity
import com.ironlog.app.data.local.entity.MetaPlanSkipEntity
import com.ironlog.app.data.local.entity.MetaTrainingPlanEntity
import com.ironlog.app.data.local.entity.PersonalRecordEntity
import com.ironlog.app.data.local.entity.PlanExerciseEntity
import com.ironlog.app.data.local.entity.ProgressionSuggestionEntity
import com.ironlog.app.data.local.entity.TrainingPlanEntity
import com.ironlog.app.data.local.entity.WorkoutSessionEntity
import com.ironlog.app.data.local.entity.WorkoutSetEntity
import com.ironlog.app.data.local.entity.WorkoutPlanTargetEntity
import com.ironlog.app.data.seed.ExerciseSeedData

@Database(
    entities = [
        ExerciseEntity::class,
        WorkoutSessionEntity::class,
        WorkoutSetEntity::class,
        PersonalRecordEntity::class,
        TrainingPlanEntity::class,
        PlanExerciseEntity::class,
        MetaTrainingPlanEntity::class,
        MetaPlanItemEntity::class,
        MetaPlanSkipEntity::class,
        WorkoutPlanTargetEntity::class,
        ProgressionSuggestionEntity::class
    ],
    version = 11,
    exportSchema = true
)
abstract class IronLogDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutSessionDao(): WorkoutSessionDao
    abstract fun workoutSetDao(): WorkoutSetDao
    abstract fun personalRecordDao(): PersonalRecordDao
    abstract fun trainingPlanDao(): TrainingPlanDao
    abstract fun metaTrainingPlanDao(): MetaTrainingPlanDao
    abstract fun progressionDao(): ProgressionDao

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

        /**
         * Migration 8 -> 9: Record column defaults in the Room schema.
         *
         * The bug-hunt batch added `@ColumnInfo(defaultValue = ...)` to
         * `exercises.notes`/`isArchived` and `plan_exercises.targetSets`/
         * `targetReps`/`targetWeightKg`, which changed the schema identity
         * without a version bump. The annotations are removed again so the
         * entity schema is exactly the pre-push v8 schema, while the version
         * bump lets Room re-run identity validation and rewrite the recorded
         * identity hash. Room only validates a column default when the entity
         * declares one (the compiler side carries a non-null default); without
         * the annotations, both databases created by the broken v8 builds
         * (with physical DEFAULT clauses) and databases created before them
         * (without) pass migration validation, so no table rebuild is needed
         * and no rows can be lost.
         */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // No DDL: the schema is already valid for both v8 variants.
            }
        }

        /** Migration 9 -> 10: Meta-plan skip events */
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `meta_plan_skips` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `metaPlanId` INTEGER NOT NULL,
                        `trainingPlanId` INTEGER NOT NULL,
                        `skippedAt` INTEGER NOT NULL,
                        FOREIGN KEY(`metaPlanId`) REFERENCES `meta_training_plans`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`trainingPlanId`) REFERENCES `training_plans`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_meta_plan_skips_metaPlanId` ON `meta_plan_skips` (`metaPlanId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_meta_plan_skips_trainingPlanId` ON `meta_plan_skips` (`trainingPlanId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_meta_plan_skips_metaPlanId_trainingPlanId` ON `meta_plan_skips` (`metaPlanId`, `trainingPlanId`)")
            }
        }

        /** Migration 10 -> 11: progression targets, suggestions, and set attribution */
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `plan_exercises` ADD COLUMN `progressionScheme` TEXT NOT NULL DEFAULT 'MANUAL'")
                db.execSQL("ALTER TABLE `plan_exercises` ADD COLUMN `progressionIncrementValue` REAL")
                db.execSQL("ALTER TABLE `plan_exercises` ADD COLUMN `progressionIncrementUnit` TEXT")
                db.execSQL("ALTER TABLE `plan_exercises` ADD COLUMN `progressionIncrementKg` REAL")
                db.execSQL("ALTER TABLE `plan_exercises` ADD COLUMN `progressionMinReps` INTEGER")
                db.execSQL("ALTER TABLE `plan_exercises` ADD COLUMN `progressionMaxReps` INTEGER")
                db.execSQL("ALTER TABLE `plan_exercises` ADD COLUMN `progressionTargetTotalReps` INTEGER")
                db.execSQL("ALTER TABLE `plan_exercises` ADD COLUMN `progressionTargetRpe` REAL")
                db.execSQL("ALTER TABLE `plan_exercises` ADD COLUMN `progressionRpeTolerance` REAL")
                db.execSQL("ALTER TABLE `plan_exercises` ADD COLUMN `progressionStallThreshold` INTEGER NOT NULL DEFAULT 2")
                db.execSQL("ALTER TABLE `plan_exercises` ADD COLUMN `progressionBackoffPercent` REAL NOT NULL DEFAULT 10.0")
                db.execSQL("ALTER TABLE `plan_exercises` ADD COLUMN `progressionRuleRevision` INTEGER NOT NULL DEFAULT 1")

                db.execSQL(
                    """
                    CREATE TABLE `workout_plan_targets` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `sessionId` INTEGER NOT NULL,
                        `planId` INTEGER NOT NULL,
                        `exerciseId` INTEGER NOT NULL,
                        `orderIndex` INTEGER NOT NULL,
                        `supersetGroupId` INTEGER,
                        `targetSets` INTEGER NOT NULL,
                        `targetReps` INTEGER NOT NULL,
                        `targetWeightKg` REAL NOT NULL,
                        `progressionScheme` TEXT NOT NULL DEFAULT 'MANUAL',
                        `progressionIncrementValue` REAL,
                        `progressionIncrementUnit` TEXT,
                        `progressionIncrementKg` REAL,
                        `progressionMinReps` INTEGER,
                        `progressionMaxReps` INTEGER,
                        `progressionTargetTotalReps` INTEGER,
                        `progressionTargetRpe` REAL,
                        `progressionRpeTolerance` REAL,
                        `progressionStallThreshold` INTEGER NOT NULL DEFAULT 2,
                        `progressionBackoffPercent` REAL NOT NULL DEFAULT 10.0,
                        `progressionRuleRevision` INTEGER NOT NULL DEFAULT 1,
                        FOREIGN KEY(`sessionId`) REFERENCES `workout_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`planId`) REFERENCES `training_plans`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`exerciseId`) REFERENCES `exercises`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX `index_workout_plan_targets_sessionId` ON `workout_plan_targets` (`sessionId`)")
                db.execSQL("CREATE INDEX `index_workout_plan_targets_planId` ON `workout_plan_targets` (`planId`)")
                db.execSQL("CREATE INDEX `index_workout_plan_targets_exerciseId` ON `workout_plan_targets` (`exerciseId`)")
                db.execSQL("CREATE INDEX `index_workout_plan_targets_planId_exerciseId_orderIndex` ON `workout_plan_targets` (`planId`, `exerciseId`, `orderIndex`)")
                db.execSQL("CREATE UNIQUE INDEX `index_workout_plan_targets_sessionId_orderIndex` ON `workout_plan_targets` (`sessionId`, `orderIndex`)")

                db.execSQL(
                    """
                    CREATE TABLE `progression_suggestions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `sourceSessionId` INTEGER NOT NULL,
                        `sourceTargetSnapshotId` INTEGER NOT NULL,
                        `planId` INTEGER NOT NULL,
                        `exerciseId` INTEGER NOT NULL,
                        `orderIndex` INTEGER NOT NULL,
                        `supersetGroupId` INTEGER,
                        `sourceSets` INTEGER NOT NULL,
                        `sourceReps` INTEGER NOT NULL,
                        `sourceWeightKg` REAL NOT NULL,
                        `sourceProgressionScheme` TEXT NOT NULL DEFAULT 'MANUAL',
                        `sourceProgressionIncrementValue` REAL,
                        `sourceProgressionIncrementUnit` TEXT,
                        `sourceProgressionIncrementKg` REAL,
                        `sourceProgressionMinReps` INTEGER,
                        `sourceProgressionMaxReps` INTEGER,
                        `sourceProgressionTargetTotalReps` INTEGER,
                        `sourceProgressionTargetRpe` REAL,
                        `sourceProgressionRpeTolerance` REAL,
                        `sourceProgressionStallThreshold` INTEGER NOT NULL DEFAULT 2,
                        `sourceProgressionBackoffPercent` REAL NOT NULL DEFAULT 10.0,
                        `sourceProgressionRuleRevision` INTEGER NOT NULL DEFAULT 1,
                        `outcomeType` TEXT NOT NULL,
                        `reasonCode` TEXT NOT NULL,
                        `reasonArgumentsJson` TEXT NOT NULL,
                        `countedSetIdsJson` TEXT NOT NULL,
                        `streakEffect` TEXT NOT NULL,
                        `suggestedSets` INTEGER,
                        `suggestedReps` INTEGER,
                        `suggestedWeightKg` REAL,
                        `status` TEXT NOT NULL,
                        `wasEdited` INTEGER NOT NULL,
                        `finalSets` INTEGER,
                        `finalReps` INTEGER,
                        `finalWeightKg` REAL,
                        `createdAtEpochMillis` INTEGER NOT NULL,
                        `decidedAtEpochMillis` INTEGER,
                        FOREIGN KEY(`sourceTargetSnapshotId`) REFERENCES `workout_plan_targets`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`sourceSessionId`) REFERENCES `workout_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`planId`) REFERENCES `training_plans`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`exerciseId`) REFERENCES `exercises`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX `index_progression_suggestions_sourceSessionId` ON `progression_suggestions` (`sourceSessionId`)")
                db.execSQL("CREATE INDEX `index_progression_suggestions_planId` ON `progression_suggestions` (`planId`)")
                db.execSQL("CREATE INDEX `index_progression_suggestions_exerciseId` ON `progression_suggestions` (`exerciseId`)")
                db.execSQL("CREATE UNIQUE INDEX `index_progression_suggestions_sourceTargetSnapshotId_sourceProgressionRuleRevision` ON `progression_suggestions` (`sourceTargetSnapshotId`, `sourceProgressionRuleRevision`)")
                db.execSQL("CREATE INDEX `index_progression_suggestions_status` ON `progression_suggestions` (`status`)")

                db.execSQL(
                    """
                    CREATE TABLE `workout_sets_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `sessionId` INTEGER NOT NULL,
                        `exerciseId` INTEGER NOT NULL,
                        `setNumber` INTEGER NOT NULL,
                        `reps` INTEGER NOT NULL,
                        `weightKg` REAL NOT NULL,
                        `isWarmup` INTEGER NOT NULL,
                        `completedAt` INTEGER NOT NULL,
                        `rpe` REAL,
                        `planTargetSnapshotId` INTEGER,
                        FOREIGN KEY(`sessionId`) REFERENCES `workout_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`exerciseId`) REFERENCES `exercises`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                        FOREIGN KEY(`planTargetSnapshotId`) REFERENCES `workout_plan_targets`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("INSERT INTO `workout_sets_new` (`id`,`sessionId`,`exerciseId`,`setNumber`,`reps`,`weightKg`,`isWarmup`,`completedAt`,`rpe`,`planTargetSnapshotId`) SELECT `id`,`sessionId`,`exerciseId`,`setNumber`,`reps`,`weightKg`,`isWarmup`,`completedAt`,`rpe`,NULL FROM `workout_sets`")
                db.execSQL("DROP TABLE `workout_sets`")
                db.execSQL("ALTER TABLE `workout_sets_new` RENAME TO `workout_sets`")
                db.execSQL("CREATE INDEX `index_workout_sets_sessionId` ON `workout_sets` (`sessionId`)")
                db.execSQL("CREATE INDEX `index_workout_sets_exerciseId` ON `workout_sets` (`exerciseId`)")
                db.execSQL("CREATE INDEX `index_workout_sets_planTargetSnapshotId` ON `workout_sets` (`planTargetSnapshotId`)")
            }
        }

        @VisibleForTesting
        fun migration5To6ForTests(): Migration = MIGRATION_5_6

        @VisibleForTesting
        fun migration6To7ForTests(): Migration = MIGRATION_6_7

        @VisibleForTesting
        fun migration7To8ForTests(): Migration = MIGRATION_7_8

        @VisibleForTesting
        fun migration8To9ForTests(): Migration = MIGRATION_8_9

        @VisibleForTesting
        fun migration9To10ForTests(): Migration = MIGRATION_9_10

        @VisibleForTesting
        fun migration10To11ForTests(): Migration = MIGRATION_10_11

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
         *
         * Runs from the Room `onOpen` callback, i.e. AFTER Room has validated the
         * database against `room_master_table` via `checkIdentity`. For every valid
         * database — freshly created or fully migrated — all tables, columns and
         * indexes guarded below already exist, so this method is a no-op in that
         * case. It only has an effect on legacy pre-release databases whose recorded
         * identity hash no longer matches the current schema and that therefore
         * never pass Room's identity validation.
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
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                    MIGRATION_10_11
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
            seedExercisesIfEmpty(db)
        }

        /**
         * Seeds the default exercise catalog. INSERT OR IGNORE only deduplicates
         * via PK/UNIQUE constraints, and `exercises.name` has no UNIQUE index, so
         * the loop alone is a no-op against duplicates. Seeding is therefore only
         * performed while the exercises table is empty, which keeps the callback
         * idempotent even if onCreate is invoked for a database that already
         * carries exercise rows.
         */
        private fun seedExercisesIfEmpty(db: SupportSQLiteDatabase) {
            val isEmpty = db.query("SELECT COUNT(*) FROM exercises").use { cursor ->
                cursor.moveToFirst() && cursor.getInt(0) == 0
            }
            if (!isEmpty) return

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

        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            ensureRuntimeSchemaCompatibility(db)
            createSingleActiveSessionTriggers(db)
        }
    }
}
