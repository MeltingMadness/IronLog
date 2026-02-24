package com.ironlog.app.data.local

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
}
