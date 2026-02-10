package com.ironlog.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ironlog.app.data.local.dao.ExerciseDao
import com.ironlog.app.data.local.dao.PersonalRecordDao
import com.ironlog.app.data.local.dao.WorkoutSessionDao
import com.ironlog.app.data.local.dao.WorkoutSetDao
import com.ironlog.app.data.local.entity.ExerciseEntity
import com.ironlog.app.data.local.entity.PersonalRecordEntity
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
        PersonalRecordEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class IronLogDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutSessionDao(): WorkoutSessionDao
    abstract fun workoutSetDao(): WorkoutSetDao
    abstract fun personalRecordDao(): PersonalRecordDao

    companion object {
        fun create(context: Context): IronLogDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                IronLogDatabase::class.java,
                "ironlog.db"
            )
                .addCallback(SeedCallback())
                .build()
        }
    }

    private class SeedCallback : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
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
