package com.ironlog.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ironlog.app.data.local.entity.*
import com.ironlog.shared.plans.PlannedSet
import com.ironlog.shared.plans.PlannedSets
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IndividualSetTargetsTest {
    @Test fun migration13To14PreservesPlanAndSnapshot() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "individual-targets-migration-test.db"
        context.deleteDatabase(name)
        val schema = JSONObject(instrumentation.context.assets.open("com.ironlog.app.data.local.IronLogDatabase/13.json").bufferedReader().use { it.readText() }).getJSONObject("database")
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object : SupportSQLiteOpenHelper.Callback(13) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                val entities = schema.getJSONArray("entities")
                for (i in 0 until entities.length()) {
                    val entity = entities.getJSONObject(i)
                    val table = entity.getString("tableName")
                    db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                    val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                    for (j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
                val setup = schema.getJSONArray("setupQueries")
                for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
                db.execSQL("INSERT INTO exercises (id,name,primaryMuscleGroup,secondaryMuscleGroups,category,isCustom,notes,isArchived) VALUES (1,'Bench','BRUST','','LANGHANTEL',0,'',0)")
                db.execSQL("INSERT INTO training_plans (id,name,createdAt) VALUES (1,'Push',1000)")
                db.execSQL("INSERT INTO plan_exercises (id,planId,exerciseId,orderIndex,targetSets,targetReps,targetWeightKg) VALUES (1,1,1,0,3,10,60)")
                db.execSQL("INSERT INTO workout_sessions (id,startTime,durationSeconds,name,notes,planId) VALUES (1,1000,0,'Push','',1)")
                db.execSQL("INSERT INTO workout_plan_targets (id,sessionId,planId,exerciseId,orderIndex,targetSets,targetReps,targetWeightKg) VALUES (1,1,1,1,0,3,10,60)")
            }
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }).build())
        helper.writableDatabase
        helper.close()
        val db = Room.databaseBuilder(context, IronLogDatabase::class.java, name).addMigrations(IronLogDatabase.migration13To14ForTests()).build()
        try {
            val plan = db.trainingPlanDao().getExercisesForPlan(1).single()
            assertEquals(3, plan.targetSets)
            assertEquals(60.0, plan.targetWeightKg, 0.0)
            assertEquals("[]", plan.setTargetsJson)
            assertEquals("[]", db.progressionDao().getTargetsForSession(1).single().setTargetsJson)
            assertEquals(14, db.openHelper.writableDatabase.version)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun explicitApplyKeepsOpenSlotsAndRejectsStalePlanAtomically() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, IronLogDatabase::class.java).build()
        try {
            val exerciseId = db.exerciseDao().insert(ExerciseEntity(name="Bench", primaryMuscleGroup="BRUST", secondaryMuscleGroups="", category="LANGHANTEL"))
            val planId = db.trainingPlanDao().insertPlan(TrainingPlanEntity(name="Push", createdAt=1000))
            val slots = listOf(PlannedSet("WARMUP",10,20.0), PlannedSet("NORMAL",8,60.0), PlannedSet("BACKOFF",10,55.0))
            val json = PlannedSets.encode(slots)
            db.trainingPlanDao().insertExercise(PlanExerciseEntity(planId=planId, exerciseId=exerciseId, orderIndex=0, targetSets=2, targetReps=8, targetWeightKg=60.0, setTargetsJson=json))
            val session = db.workoutSessionDao().insert(WorkoutSessionEntity(startTime=1000, endTime=2000, planId=planId))
            val snapshotId = db.progressionDao().insertTargets(listOf(WorkoutPlanTargetEntity(sessionId=session, planId=planId, exerciseId=exerciseId, orderIndex=0, supersetGroupId=null, setTargetsJson=json, target=ProgressionTargetColumns(2,8,60.0), progression=ProgressionConfigColumns()))).single()
            db.workoutSetDao().insert(WorkoutSetEntity(sessionId=session, exerciseId=exerciseId, setNumber=1, reps=7, weightKg=62.5, setType="NORMAL", completedAt=1500, planTargetSnapshotId=snapshotId))
            val original = db.trainingPlanDao().getExercisesForPlan(planId).single()
            // No automatic plan mutation at finish.
            assertEquals(json, original.setTargetsJson)
            db.trainingPlanDao().updateExercise(original.copy(targetWeightKg=70.0))
            try { db.trainingPlanDao().applyPerformedSetTargets(session); fail("Must reject a changed plan") } catch (_: IllegalStateException) { }
            assertEquals(70.0, db.trainingPlanDao().getExercisesForPlan(planId).single().targetWeightKg, 0.0)
            db.trainingPlanDao().updateExercise(original)
            db.trainingPlanDao().applyPerformedSetTargets(session)
            assertEquals(listOf(slots[0], PlannedSet("NORMAL",7,62.5), slots[2]), PlannedSets.decode(db.trainingPlanDao().getExercisesForPlan(planId).single().setTargetsJson))
            assertEquals(json, db.progressionDao().getTargetsForSession(session).single().setTargetsJson)
        } finally { db.close() }
    }
}
