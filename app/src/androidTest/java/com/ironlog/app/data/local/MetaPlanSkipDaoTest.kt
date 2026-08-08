package com.ironlog.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ironlog.app.data.local.dao.MetaTrainingPlanDao
import com.ironlog.app.data.local.entity.MetaPlanItemEntity
import com.ironlog.app.data.local.entity.MetaTrainingPlanEntity
import com.ironlog.app.data.local.entity.TrainingPlanEntity
import com.ironlog.app.data.local.entity.WorkoutSessionEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MetaPlanSkipDaoTest {

    private lateinit var database: IronLogDatabase
    private lateinit var dao: MetaTrainingPlanDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            IronLogDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
        dao = database.metaTrainingPlanDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun oneItemMetaPlanRejectsSkip() = runBlocking {
        val planId = insertPlan("Plan A")
        val metaPlanId = seedMetaPlan(planId)

        val skipped = dao.skipCurrentSubPlanIfCurrent(
            metaPlanId = metaPlanId,
            expectedTrainingPlanId = planId,
            skippedAt = 100L
        )

        assertFalse(skipped)
        assertEquals(0, skipCount())
    }

    @Test
    fun missingMembershipRejectsSkip() = runBlocking {
        val planId = insertPlan("Plan A")
        val missingPlanId = insertPlan("Not In Rotation")
        val metaPlanId = seedMetaPlan(planId)

        val skipped = dao.skipCurrentSubPlanIfCurrent(
            metaPlanId = metaPlanId,
            expectedTrainingPlanId = missingPlanId,
            skippedAt = 100L
        )

        assertFalse(skipped)
        assertEquals(0, skipCount())
    }

    @Test
    fun secondCallWithSameStaleExpectedPlanRejectedAfterRotationAdvances() = runBlocking {
        val planAId = insertPlan("Plan A")
        val planBId = insertPlan("Plan B")
        val metaPlanId = seedMetaPlan(planAId, planBId)

        assertTrue(
            dao.skipCurrentSubPlanIfCurrent(
                metaPlanId = metaPlanId,
                expectedTrainingPlanId = planAId,
                skippedAt = 100L
            )
        )
        val secondResult = dao.skipCurrentSubPlanIfCurrent(
            metaPlanId = metaPlanId,
            expectedTrainingPlanId = planAId,
            skippedAt = 101L
        )

        assertFalse(secondResult)
        assertEquals(1, skipCount())
        assertEquals(listOf(planAId), skippedTrainingPlanIds())
    }

    @Test
    fun equalMillisecondTimestampsAreNormalizedToIncreasingAnchors() = runBlocking {
        val planAId = insertPlan("Plan A")
        val planBId = insertPlan("Plan B")
        val metaPlanId = seedMetaPlan(planAId, planBId)

        assertTrue(
            dao.skipCurrentSubPlanIfCurrent(
                metaPlanId = metaPlanId,
                expectedTrainingPlanId = planAId,
                skippedAt = 100L
            )
        )
        assertTrue(
            dao.skipCurrentSubPlanIfCurrent(
                metaPlanId = metaPlanId,
                expectedTrainingPlanId = planBId,
                skippedAt = 100L
            )
        )

        assertEquals(listOf(100L, 101L), skippedAtValues())
    }

    @Test
    fun skipNeverCreatesOrChangesWorkoutSession() = runBlocking {
        val planAId = insertPlan("Plan A")
        val planBId = insertPlan("Plan B")
        val metaPlanId = seedMetaPlan(planAId, planBId)
        val sessionId = database.workoutSessionDao().insert(
            WorkoutSessionEntity(
                startTime = 1000L,
                endTime = 2000L,
                durationSeconds = 1L,
                planId = planAId,
                metaPlanId = metaPlanId
            )
        )

        assertTrue(
            dao.skipCurrentSubPlanIfCurrent(
                metaPlanId = metaPlanId,
                expectedTrainingPlanId = planAId,
                skippedAt = 3000L
            )
        )

        val sessions = database.workoutSessionDao().getAllCompletedSessionsList()
        assertEquals(1, sessions.size)
        assertEquals(sessionId, sessions.single().id)
        assertEquals(null, database.workoutSessionDao().getActiveSession())
    }

    private suspend fun insertPlan(name: String): Long =
        database.trainingPlanDao().insertPlan(
            TrainingPlanEntity(id = 0L, name = name, createdAt = 1L)
        )

    private suspend fun seedMetaPlan(vararg trainingPlanIds: Long): Long {
        val metaPlanId = dao.insertMetaPlan(
            MetaTrainingPlanEntity(id = 0L, name = "Meta", createdAt = 1L)
        )
        dao.insertItems(
            trainingPlanIds.mapIndexed { index, trainingPlanId ->
                MetaPlanItemEntity(
                    id = 0L,
                    metaPlanId = metaPlanId,
                    trainingPlanId = trainingPlanId,
                    orderIndex = index
                )
            }
        )
        return metaPlanId
    }

    private fun skipCount(): Int =
        database.openHelper.writableDatabase.query(
            "SELECT COUNT(*) FROM meta_plan_skips"
        ).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private fun skippedTrainingPlanIds(): List<Long> {
        val result = mutableListOf<Long>()
        database.openHelper.writableDatabase.query(
            "SELECT trainingPlanId FROM meta_plan_skips ORDER BY id ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(cursor.getLong(0))
            }
        }
        return result
    }

    private fun skippedAtValues(): List<Long> {
        val result = mutableListOf<Long>()
        database.openHelper.writableDatabase.query(
            "SELECT skippedAt FROM meta_plan_skips ORDER BY id ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(cursor.getLong(0))
            }
        }
        return result
    }
}
