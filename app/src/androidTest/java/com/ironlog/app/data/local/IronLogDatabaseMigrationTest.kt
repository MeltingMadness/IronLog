package com.ironlog.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IronLogDatabaseMigrationTest {

    @Test
    fun migration5To6_addsMetaTablesAndSessionReferenceColumns() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dbName = "ironlog-migration-5-6-test.db"
        context.deleteDatabase(dbName)

        val legacyHelper = createLegacyV5Helper(context, dbName)
        legacyHelper.writableDatabase.use { db ->
            db.execSQL("INSERT INTO training_plans (id, name, createdAt) VALUES (1, 'Push', 1000)")
            db.execSQL(
                """
                INSERT INTO workout_sessions (id, startTime, endTime, durationSeconds, name, notes)
                VALUES (1, 1000, 2000, 1, 'Session', '')
                """.trimIndent()
            )
        }
        legacyHelper.close()

        val migratedHelper = createMigratingV6Helper(context, dbName)
        migratedHelper.writableDatabase.use { db ->
            assertTrue(hasColumn(db, "workout_sessions", "planId"))
            assertTrue(hasColumn(db, "workout_sessions", "metaPlanId"))
            assertTrue(tableExists(db, "meta_training_plans"))
            assertTrue(tableExists(db, "meta_plan_items"))

            db.execSQL("UPDATE workout_sessions SET planId = 1 WHERE id = 1")
            db.execSQL("INSERT INTO meta_training_plans (id, name, createdAt) VALUES (1, 'Meta', 1000)")
            db.execSQL(
                """
                INSERT INTO meta_plan_items (id, metaPlanId, trainingPlanId, orderIndex)
                VALUES (1, 1, 1, 0)
                """.trimIndent()
            )

            db.query("SELECT COUNT(*) FROM meta_plan_items").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
        }
        migratedHelper.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun migration6To7_addsExerciseNotesAndArchiveColumns() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dbName = "ironlog-migration-6-7-test.db"
        context.deleteDatabase(dbName)

        val legacyHelper = createLegacyV6Helper(context, dbName)
        legacyHelper.writableDatabase.use { db ->
            db.execSQL(
                """
                INSERT INTO exercises (id, name, primaryMuscleGroup, secondaryMuscleGroups, category, isCustom)
                VALUES (1, 'Custom Pushup', 'BRUST', '', 'EIGENGEWICHT', 1)
                """.trimIndent()
            )
        }
        legacyHelper.close()

        val migratedHelper = createMigratingV7Helper(context, dbName)
        migratedHelper.writableDatabase.use { db ->
            assertTrue(hasColumn(db, "exercises", "notes"))
            assertTrue(hasColumn(db, "exercises", "isArchived"))

            db.query("SELECT notes, isArchived FROM exercises WHERE id = 1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("", cursor.getString(0))
                assertEquals(0, cursor.getInt(1))
            }
        }

        migratedHelper.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun migration7To8_addsArchivedIndexOnExercises() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dbName = "ironlog-migration-7-8-test.db"
        context.deleteDatabase(dbName)

        val legacyHelper = createLegacyV7Helper(context, dbName)
        legacyHelper.writableDatabase.use { db ->
            db.execSQL(
                """
                INSERT INTO exercises (id, name, primaryMuscleGroup, secondaryMuscleGroups, category, isCustom, notes, isArchived)
                VALUES (1, 'Index Test', 'BRUST', '', 'EIGENGEWICHT', 1, '', 0)
                """.trimIndent()
            )
            assertFalse(hasIndex(db, "exercises", "index_exercises_isArchived"))
        }
        legacyHelper.close()

        val migratedHelper = createMigratingV8Helper(context, dbName)
        migratedHelper.writableDatabase.use { db ->
            assertTrue(hasIndex(db, "exercises", "index_exercises_isArchived"))
        }

        migratedHelper.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun migration8To9_acceptsLegacyV8WithoutDefaults() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dbName = "ironlog-migration-8-9-legacy.db"
        context.deleteDatabase(dbName)

        val legacyHelper = createV8Helper(
            context = context,
            dbName = dbName,
            withDefaults = false,
            identityHash = "097e7e46688af90b3da0301fb81fbfab"
        )
        insertV8Data(legacyHelper)
        legacyHelper.close()

        // The real Room open runs the full 8 -> 9 -> 10 -> 11 chain,
        // validates the current schema and rewrites the recorded identity
        // hash. Any schema mismatch throws here.
        val database = openMigratedV8ToCurrentDatabase(context, dbName)
        runBlocking {
            // Triggers the Room open on a database whose recorded identity is
            // the pre-push v8 hash and whose physical schema has no DEFAULTs.
            database.exerciseDao().getCount()
        }
        database.close()

        val rawHelper = openRawV11Connection(context, dbName)
        rawHelper.writableDatabase.use { db ->
            db.query("PRAGMA user_version").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(11, cursor.getInt(0))
            }
            assertRecordedIdentityHash(
                db,
                "e306d69198bb1578052d10f97dd36b72"
            )
            assertExerciseDataPreserved(db)
            assertChildDataPreserved(db)
            db.execSQL("PRAGMA foreign_keys = ON")
            db.query("PRAGMA foreign_key_check").use { cursor ->
                assertFalse("foreign_key_check must be empty", cursor.moveToFirst())
            }
        }
        rawHelper.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun migration8To9_acceptsPushV8WithDefaults() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dbName = "ironlog-migration-8-9-push.db"
        context.deleteDatabase(dbName)

        val pushHelper = createV8Helper(
            context = context,
            dbName = dbName,
            withDefaults = true,
            identityHash = "295b56d3fe195cf19e2a0cd0df90eb50"
        )
        insertV8Data(pushHelper)
        pushHelper.close()

        val database = openMigratedV8ToCurrentDatabase(context, dbName)
        runBlocking {
            // Triggers the Room open on a database whose recorded identity is
            // the broken v8 hash and whose physical schema carries DEFAULTs.
            database.exerciseDao().getCount()
        }
        database.close()

        val rawHelper = openRawV11Connection(context, dbName)
        rawHelper.writableDatabase.use { db ->
            db.query("PRAGMA user_version").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(11, cursor.getInt(0))
            }
            assertRecordedIdentityHash(
                db,
                "e306d69198bb1578052d10f97dd36b72"
            )
            assertExerciseDataPreserved(db)
            assertChildDataPreserved(db)
            db.execSQL("PRAGMA foreign_keys = ON")
            db.query("PRAGMA foreign_key_check").use { cursor ->
                assertFalse("foreign_key_check must be empty", cursor.moveToFirst())
            }

            // The physical DEFAULT clauses from the broken v8 build stay in
            // place (no-op migration), so they keep working for raw inserts.
            assertColumnDefault(db, "exercises", "notes", "''")
            assertColumnDefault(db, "exercises", "isArchived", "0")
            assertColumnDefault(db, "plan_exercises", "targetSets", "3")
            assertColumnDefault(db, "plan_exercises", "targetReps", "10")
            assertColumnDefault(db, "plan_exercises", "targetWeightKg", "0.0")

            db.execSQL(
                """
                INSERT INTO exercises (name, primaryMuscleGroup, secondaryMuscleGroups, category, isCustom)
                VALUES ('Defaulted Pushup', 'BRUST', '', 'EIGENGEWICHT', 0)
                """.trimIndent()
            )
            db.execSQL("INSERT INTO plan_exercises (planId, exerciseId, orderIndex) VALUES (1, 1, 1)")
            db.query("SELECT notes, isArchived FROM exercises WHERE name = 'Defaulted Pushup'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("", cursor.getString(0))
                assertEquals(0, cursor.getInt(1))
            }
            db.query(
                "SELECT targetSets, targetReps, targetWeightKg FROM plan_exercises ORDER BY id DESC LIMIT 1"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(3, cursor.getInt(0))
                assertEquals(10, cursor.getInt(1))
                assertEquals(0.0, cursor.getDouble(2), 0.0)
            }
        }
        rawHelper.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun migration9To10_addsMetaPlanSkipsTableAndPreservesData() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dbName = "ironlog-migration-9-10-test.db"
        context.deleteDatabase(dbName)

        val legacyHelper = createLegacyV9Helper(context, dbName)
        legacyHelper.writableDatabase.use { db ->
            db.execSQL("INSERT INTO training_plans (id, name, createdAt) VALUES (1, 'Push', 1000)")
            db.execSQL("INSERT INTO meta_training_plans (id, name, createdAt) VALUES (1, 'Meta', 1000)")
            db.execSQL(
                """
                INSERT INTO meta_plan_items (id, metaPlanId, trainingPlanId, orderIndex)
                VALUES (1, 1, 1, 0)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO workout_sessions (id, startTime, endTime, durationSeconds, name, notes, planId, metaPlanId)
                VALUES (1, 1000, 2000, 1, 'Session', '', 1, 1)
                """.trimIndent()
            )
        }
        legacyHelper.close()

        // The real Room open applies 9 -> 10 -> 11, validates the legacy v9
        // schema against the recorded identity and rewrites the identity hash
        // to the current schema. Any mismatch throws here.
        val database = openMigratedV9ToCurrentDatabase(context, dbName)
        runBlocking {
            database.trainingPlanDao().getAllPlansList()
        }
        database.close()

        val rawHelper = openRawV11Connection(context, dbName)
        rawHelper.writableDatabase.use { db ->
            db.query("PRAGMA user_version").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(11, cursor.getInt(0))
            }
            assertRecordedIdentityHash(
                db,
                "e306d69198bb1578052d10f97dd36b72"
            )
            assertTrue(tableExists(db, "meta_plan_skips"))
            assertTrue(hasIndex(db, "meta_plan_skips", "index_meta_plan_skips_metaPlanId"))
            assertTrue(hasIndex(db, "meta_plan_skips", "index_meta_plan_skips_trainingPlanId"))
            assertTrue(
                hasIndex(db, "meta_plan_skips", "index_meta_plan_skips_metaPlanId_trainingPlanId")
            )

            db.query("SELECT COUNT(*) FROM training_plans").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
            db.query("SELECT COUNT(*) FROM meta_training_plans").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
            db.query("SELECT COUNT(*) FROM meta_plan_items").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
            db.query(
                "SELECT COUNT(*) FROM workout_sessions WHERE id = 1 AND planId = 1 AND metaPlanId = 1"
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }

            db.execSQL(
                """
                INSERT INTO meta_plan_skips (metaPlanId, trainingPlanId, skippedAt)
                VALUES (1, 1, 1000)
                """.trimIndent()
            )
            db.query("SELECT COUNT(*) FROM meta_plan_skips").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
            db.execSQL("PRAGMA foreign_keys = ON")
            db.query("PRAGMA foreign_key_check").use { cursor ->
                assertFalse("foreign_key_check must be empty", cursor.moveToFirst())
            }
        }
        rawHelper.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun migration10To11_preservesRowsAndCreatesManualProgressionSchema() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dbName = "ironlog-migration-10-11-test.db"
        context.deleteDatabase(dbName)
        createLegacyV10Helper(context, dbName).use { helper ->
            helper.writableDatabase.execSQL("INSERT INTO exercises (id, name, primaryMuscleGroup, secondaryMuscleGroups, category, isCustom, notes, isArchived) VALUES (1, 'Squat', 'BEINE', '', 'LANGHANTEL', 0, '', 0)")
            helper.writableDatabase.execSQL("INSERT INTO training_plans (id, name, createdAt) VALUES (1, 'Lower', 1000)")
            helper.writableDatabase.execSQL("INSERT INTO plan_exercises (id, planId, exerciseId, orderIndex, targetSets, targetReps, targetWeightKg, supersetGroupId) VALUES (1, 1, 1, 0, 3, 8, 100.0, NULL)")
            helper.writableDatabase.execSQL("INSERT INTO plan_exercises (id, planId, exerciseId, orderIndex, targetSets, targetReps, targetWeightKg, supersetGroupId) VALUES (2, 1, 1, 1, 2, 12, 80.0, NULL)")
            helper.writableDatabase.execSQL("INSERT INTO workout_sessions (id, startTime, endTime, durationSeconds, name, notes, planId, metaPlanId) VALUES (1, 1000, 2000, 1, 'Lower', '', 1, NULL)")
            helper.writableDatabase.execSQL("INSERT INTO workout_sets (id, sessionId, exerciseId, setNumber, reps, weightKg, isWarmup, completedAt, rpe) VALUES (1, 1, 1, 1, 8, 100.0, 0, 1500, 8.0)")
        }

        val database = Room.databaseBuilder(context, IronLogDatabase::class.java, dbName)
            .addMigrations(IronLogDatabase.migration10To11ForTests())
            .build()
        runBlocking { database.exerciseDao().getCount() }
        database.close()

        openRawV11Connection(context, dbName).use { helper ->
            val db = helper.writableDatabase
            assertEquals("MANUAL", queryString(db, "SELECT progressionScheme FROM plan_exercises WHERE id = 1"))
            assertEquals("MANUAL", queryString(db, "SELECT progressionScheme FROM plan_exercises WHERE id = 2"))
            assertEquals(3, queryInt(db, "SELECT targetSets FROM plan_exercises WHERE id = 1"))
            assertEquals(8, queryInt(db, "SELECT targetReps FROM plan_exercises WHERE id = 1"))
            assertEquals(100.0, queryDouble(db, "SELECT targetWeightKg FROM plan_exercises WHERE id = 1"), 0.0)
            assertEquals(80.0, queryDouble(db, "SELECT targetWeightKg FROM plan_exercises WHERE id = 2"), 0.0)
            assertTrue(hasColumn(db, "workout_sets", "planTargetSnapshotId"))
            assertTrue(tableExists(db, "workout_plan_targets"))
            assertTrue(tableExists(db, "progression_suggestions"))
            assertTrue(hasIndex(db, "workout_plan_targets", "index_workout_plan_targets_sessionId_orderIndex"))
            assertTrue(hasIndex(db, "workout_plan_targets", "index_workout_plan_targets_planId_exerciseId_orderIndex"))
            assertTrue(hasIndex(db, "progression_suggestions", "index_progression_suggestions_sourceTargetSnapshotId_sourceProgressionRuleRevision"))
            assertEquals(0, queryInt(db, "SELECT COUNT(*) FROM workout_plan_targets"))
            assertEquals(0, queryInt(db, "SELECT COUNT(*) FROM progression_suggestions"))
            assertEquals(0, queryInt(db, "SELECT COUNT(*) FROM workout_sets WHERE planTargetSnapshotId IS NOT NULL"))
            db.execSQL("PRAGMA foreign_keys = ON")
            db.query("PRAGMA foreign_key_check").use { cursor -> assertFalse(cursor.moveToFirst()) }
        }
        context.deleteDatabase(dbName)
    }

    private fun insertV8Data(helper: SupportSQLiteOpenHelper) {
        helper.writableDatabase.use { db ->
            db.execSQL("INSERT INTO training_plans (id, name, createdAt) VALUES (1, 'Push', 1000)")
            db.execSQL(
                """
                INSERT INTO exercises (id, name, primaryMuscleGroup, secondaryMuscleGroups, category, isCustom, notes, isArchived)
                VALUES (1, 'Bench Press', 'BRUST', '', 'KRAFT', 1, 'keep notes', 1)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO workout_sessions (id, startTime, endTime, durationSeconds, name, notes)
                VALUES (1, 1000, 2000, 1, 'Session', '')
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO workout_sets (id, sessionId, exerciseId, setNumber, reps, weightKg, isWarmup, completedAt)
                VALUES (1, 1, 1, 1, 10, 60.0, 0, 1500)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO personal_records (id, exerciseId, type, value, achievedAt)
                VALUES (1, 1, 'BEST', 60.0, 1500)
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO plan_exercises (id, planId, exerciseId, orderIndex, supersetGroupId, targetSets, targetReps, targetWeightKg)
                VALUES (1, 1, 1, 0, NULL, 5, 8, 60.0)
                """.trimIndent()
            )
        }
    }

    private fun openMigratedV8ToCurrentDatabase(
        context: Context,
        dbName: String
    ): IronLogDatabase {
        return Room.databaseBuilder(context, IronLogDatabase::class.java, dbName)
            .addMigrations(IronLogDatabase.migration8To9ForTests())
            .addMigrations(IronLogDatabase.migration9To10ForTests())
            .addMigrations(IronLogDatabase.migration10To11ForTests())
            .allowMainThreadQueries()
            .build()
    }

    private fun openMigratedV9ToCurrentDatabase(
        context: Context,
        dbName: String
    ): IronLogDatabase {
        return Room.databaseBuilder(context, IronLogDatabase::class.java, dbName)
            .addMigrations(IronLogDatabase.migration9To10ForTests())
            .addMigrations(IronLogDatabase.migration10To11ForTests())
            .allowMainThreadQueries()
            .build()
    }

    private fun openRawV11Connection(
        context: Context,
        dbName: String
    ): SupportSQLiteOpenHelper {
        val callback = object : SupportSQLiteOpenHelper.Callback(11) {
            override fun onCreate(db: SupportSQLiteDatabase) = Unit

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }

        return FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(callback)
                .build()
        )
    }

    private fun assertExerciseDataPreserved(db: SupportSQLiteDatabase) {
        db.query("SELECT notes, isArchived FROM exercises WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("keep notes", cursor.getString(0))
            assertEquals(1, cursor.getInt(1))
        }
        db.query("SELECT targetSets, targetReps, targetWeightKg FROM plan_exercises WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(5, cursor.getInt(0))
            assertEquals(8, cursor.getInt(1))
            assertEquals(60.0, cursor.getDouble(2), 0.0)
        }
    }

    private fun assertChildDataPreserved(db: SupportSQLiteDatabase) {
        db.query("SELECT COUNT(*) FROM workout_sets WHERE id = 1 AND exerciseId = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM personal_records WHERE id = 1 AND exerciseId = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM plan_exercises WHERE id = 1 AND exerciseId = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
    }

    private fun createLegacyV5Helper(
        context: Context,
        dbName: String
    ): SupportSQLiteOpenHelper {
        val callback = object : SupportSQLiteOpenHelper.Callback(5) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS training_plans (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS workout_sessions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        startTime INTEGER NOT NULL,
                        endTime INTEGER,
                        durationSeconds INTEGER NOT NULL,
                        name TEXT NOT NULL,
                        notes TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }

        return FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(callback)
                .build()
        )
    }

    private fun createMigratingV6Helper(
        context: Context,
        dbName: String
    ): SupportSQLiteOpenHelper {
        val callback = object : SupportSQLiteOpenHelper.Callback(6) {
            override fun onCreate(db: SupportSQLiteDatabase) = Unit

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                assertEquals(5, oldVersion)
                assertEquals(6, newVersion)
                IronLogDatabase.migration5To6ForTests().migrate(db)
            }
        }

        return FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(callback)
                .build()
        )
    }

    private fun createLegacyV6Helper(
        context: Context,
        dbName: String
    ): SupportSQLiteOpenHelper {
        val callback = object : SupportSQLiteOpenHelper.Callback(6) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS exercises (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        primaryMuscleGroup TEXT NOT NULL,
                        secondaryMuscleGroups TEXT NOT NULL,
                        category TEXT NOT NULL,
                        isCustom INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }

        return FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(callback)
                .build()
        )
    }

    private fun createMigratingV7Helper(
        context: Context,
        dbName: String
    ): SupportSQLiteOpenHelper {
        val callback = object : SupportSQLiteOpenHelper.Callback(7) {
            override fun onCreate(db: SupportSQLiteDatabase) = Unit

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                assertEquals(6, oldVersion)
                assertEquals(7, newVersion)
                IronLogDatabase.migration6To7ForTests().migrate(db)
            }
        }

        return FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(callback)
                .build()
        )
    }

    private fun createLegacyV7Helper(
        context: Context,
        dbName: String
    ): SupportSQLiteOpenHelper {
        val callback = object : SupportSQLiteOpenHelper.Callback(7) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS exercises (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        primaryMuscleGroup TEXT NOT NULL,
                        secondaryMuscleGroups TEXT NOT NULL,
                        category TEXT NOT NULL,
                        isCustom INTEGER NOT NULL,
                        notes TEXT NOT NULL,
                        isArchived INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }

        return FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(callback)
                .build()
        )
    }

    private fun createMigratingV8Helper(
        context: Context,
        dbName: String
    ): SupportSQLiteOpenHelper {
        val callback = object : SupportSQLiteOpenHelper.Callback(8) {
            override fun onCreate(db: SupportSQLiteDatabase) = Unit

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                assertEquals(7, oldVersion)
                assertEquals(8, newVersion)
                IronLogDatabase.migration7To8ForTests().migrate(db)
            }
        }

        return FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(callback)
                .build()
        )
    }

    private fun createV8Helper(
        context: Context,
        dbName: String,
        withDefaults: Boolean,
        identityHash: String
    ): SupportSQLiteOpenHelper {
        val callback = object : SupportSQLiteOpenHelper.Callback(8) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS exercises (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        primaryMuscleGroup TEXT NOT NULL,
                        secondaryMuscleGroups TEXT NOT NULL,
                        category TEXT NOT NULL,
                        isCustom INTEGER NOT NULL,
                        notes TEXT NOT NULL${if (withDefaults) " DEFAULT ''" else ""},
                        isArchived INTEGER NOT NULL${if (withDefaults) " DEFAULT 0" else ""}
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS workout_sessions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        startTime INTEGER NOT NULL,
                        endTime INTEGER,
                        durationSeconds INTEGER NOT NULL,
                        name TEXT NOT NULL,
                        notes TEXT NOT NULL,
                        planId INTEGER,
                        metaPlanId INTEGER
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS workout_sets (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        sessionId INTEGER NOT NULL,
                        exerciseId INTEGER NOT NULL,
                        setNumber INTEGER NOT NULL,
                        reps INTEGER NOT NULL,
                        weightKg REAL NOT NULL,
                        isWarmup INTEGER NOT NULL,
                        completedAt INTEGER NOT NULL,
                        rpe REAL,
                        FOREIGN KEY(sessionId) REFERENCES workout_sessions(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(exerciseId) REFERENCES exercises(id) ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS personal_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        exerciseId INTEGER NOT NULL,
                        type TEXT NOT NULL,
                        value REAL NOT NULL,
                        achievedAt INTEGER NOT NULL,
                        FOREIGN KEY(exerciseId) REFERENCES exercises(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS training_plans (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS plan_exercises (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        planId INTEGER NOT NULL,
                        exerciseId INTEGER NOT NULL,
                        orderIndex INTEGER NOT NULL,
                        supersetGroupId INTEGER,
                        targetSets INTEGER NOT NULL${if (withDefaults) " DEFAULT 3" else ""},
                        targetReps INTEGER NOT NULL${if (withDefaults) " DEFAULT 10" else ""},
                        targetWeightKg REAL NOT NULL${if (withDefaults) " DEFAULT 0.0" else ""},
                        FOREIGN KEY(planId) REFERENCES training_plans(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(exerciseId) REFERENCES exercises(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS meta_training_plans (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS meta_plan_items (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        metaPlanId INTEGER NOT NULL,
                        trainingPlanId INTEGER NOT NULL,
                        orderIndex INTEGER NOT NULL,
                        FOREIGN KEY(metaPlanId) REFERENCES meta_training_plans(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(trainingPlanId) REFERENCES training_plans(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_exercises_isArchived ON exercises (isArchived)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_workout_sessions_planId ON workout_sessions (planId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_workout_sessions_metaPlanId ON workout_sessions (metaPlanId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_workout_sets_sessionId ON workout_sets (sessionId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_workout_sets_exerciseId ON workout_sets (exerciseId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_personal_records_exerciseId ON personal_records (exerciseId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_plan_exercises_planId ON plan_exercises (planId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_plan_exercises_exerciseId ON plan_exercises (exerciseId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_meta_plan_items_metaPlanId ON meta_plan_items (metaPlanId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_meta_plan_items_trainingPlanId ON meta_plan_items (trainingPlanId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
                db.execSQL(
                    "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '$identityHash')"
                )
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }

        return FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(callback)
                .build()
        )
    }

    private fun createLegacyV9Helper(
        context: Context,
        dbName: String
    ): SupportSQLiteOpenHelper = createLegacyV9OrV10Helper(
        context = context,
        dbName = dbName,
        version = 9,
        includeMetaPlanSkips = false,
        identityHash = "097e7e46688af90b3da0301fb81fbfab"
    )

    private fun createLegacyV10Helper(
        context: Context,
        dbName: String
    ): SupportSQLiteOpenHelper = createLegacyV9OrV10Helper(
        context = context,
        dbName = dbName,
        version = 10,
        includeMetaPlanSkips = true,
        identityHash = "c6950cf049fba43c8663d55261bd94a1"
    )

    private fun createLegacyV9OrV10Helper(
        context: Context,
        dbName: String,
        version: Int,
        includeMetaPlanSkips: Boolean,
        identityHash: String
    ): SupportSQLiteOpenHelper {
        val callback = object : SupportSQLiteOpenHelper.Callback(version) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS exercises (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        primaryMuscleGroup TEXT NOT NULL,
                        secondaryMuscleGroups TEXT NOT NULL,
                        category TEXT NOT NULL,
                        isCustom INTEGER NOT NULL,
                        notes TEXT NOT NULL,
                        isArchived INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS workout_sessions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        startTime INTEGER NOT NULL,
                        endTime INTEGER,
                        durationSeconds INTEGER NOT NULL,
                        name TEXT NOT NULL,
                        notes TEXT NOT NULL,
                        planId INTEGER,
                        metaPlanId INTEGER
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS workout_sets (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        sessionId INTEGER NOT NULL,
                        exerciseId INTEGER NOT NULL,
                        setNumber INTEGER NOT NULL,
                        reps INTEGER NOT NULL,
                        weightKg REAL NOT NULL,
                        isWarmup INTEGER NOT NULL,
                        completedAt INTEGER NOT NULL,
                        rpe REAL,
                        FOREIGN KEY(sessionId) REFERENCES workout_sessions(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(exerciseId) REFERENCES exercises(id) ON UPDATE NO ACTION ON DELETE RESTRICT
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS personal_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        exerciseId INTEGER NOT NULL,
                        type TEXT NOT NULL,
                        value REAL NOT NULL,
                        achievedAt INTEGER NOT NULL,
                        FOREIGN KEY(exerciseId) REFERENCES exercises(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS training_plans (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS plan_exercises (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        planId INTEGER NOT NULL,
                        exerciseId INTEGER NOT NULL,
                        orderIndex INTEGER NOT NULL,
                        supersetGroupId INTEGER,
                        targetSets INTEGER NOT NULL,
                        targetReps INTEGER NOT NULL,
                        targetWeightKg REAL NOT NULL,
                        FOREIGN KEY(planId) REFERENCES training_plans(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(exerciseId) REFERENCES exercises(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS meta_training_plans (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS meta_plan_items (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        metaPlanId INTEGER NOT NULL,
                        trainingPlanId INTEGER NOT NULL,
                        orderIndex INTEGER NOT NULL,
                        FOREIGN KEY(metaPlanId) REFERENCES meta_training_plans(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(trainingPlanId) REFERENCES training_plans(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                if (includeMetaPlanSkips) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS meta_plan_skips (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            metaPlanId INTEGER NOT NULL,
                            trainingPlanId INTEGER NOT NULL,
                            skippedAt INTEGER NOT NULL,
                            FOREIGN KEY(metaPlanId) REFERENCES meta_training_plans(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                            FOREIGN KEY(trainingPlanId) REFERENCES training_plans(id) ON UPDATE NO ACTION ON DELETE CASCADE
                        )
                        """.trimIndent()
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_meta_plan_skips_metaPlanId ON meta_plan_skips (metaPlanId)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_meta_plan_skips_trainingPlanId ON meta_plan_skips (trainingPlanId)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_meta_plan_skips_metaPlanId_trainingPlanId ON meta_plan_skips (metaPlanId, trainingPlanId)")
                }
                db.execSQL("CREATE INDEX IF NOT EXISTS index_exercises_isArchived ON exercises (isArchived)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_workout_sessions_planId ON workout_sessions (planId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_workout_sessions_metaPlanId ON workout_sessions (metaPlanId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_workout_sets_sessionId ON workout_sets (sessionId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_workout_sets_exerciseId ON workout_sets (exerciseId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_personal_records_exerciseId ON personal_records (exerciseId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_plan_exercises_planId ON plan_exercises (planId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_plan_exercises_exerciseId ON plan_exercises (exerciseId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_meta_plan_items_metaPlanId ON meta_plan_items (metaPlanId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_meta_plan_items_trainingPlanId ON meta_plan_items (trainingPlanId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
                db.execSQL(
                    "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '$identityHash')"
                )
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }

        return FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(callback)
                .build()
        )
    }

    private fun assertColumnDefault(
        db: SupportSQLiteDatabase,
        table: String,
        columnName: String,
        expectedDefault: String
    ) {
        db.query("PRAGMA table_info($table)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            val defaultValueIndex = cursor.getColumnIndex("dflt_value")
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == columnName) {
                    assertEquals(expectedDefault, cursor.getString(defaultValueIndex))
                    return
                }
            }
        }
        fail("Column $columnName not found in $table")
    }

    private fun assertRecordedIdentityHash(
        db: SupportSQLiteDatabase,
        expectedHash: String
    ) {
        db.query("SELECT identity_hash FROM room_master_table WHERE id = 42").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(expectedHash, cursor.getString(0))
        }
    }

    private fun hasColumn(db: SupportSQLiteDatabase, table: String, columnName: String): Boolean {
        db.query("PRAGMA table_info($table)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == columnName) return true
            }
        }
        return false
    }

    private fun tableExists(db: SupportSQLiteDatabase, table: String): Boolean {
        db.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
            arrayOf(table)
        ).use { cursor ->
            return cursor.moveToFirst()
        }
    }

    private fun hasIndex(db: SupportSQLiteDatabase, table: String, indexName: String): Boolean {
        db.query("PRAGMA index_list($table)").use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == indexName) return true
            }
        }
        return false
    }

    private fun queryString(db: SupportSQLiteDatabase, sql: String): String =
        db.query(sql).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getString(0)
        }

    private fun queryInt(db: SupportSQLiteDatabase, sql: String): Int =
        db.query(sql).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private fun queryDouble(db: SupportSQLiteDatabase, sql: String): Double =
        db.query(sql).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getDouble(0)
        }
}
