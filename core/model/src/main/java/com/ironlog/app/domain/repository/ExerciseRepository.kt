package com.ironlog.app.domain.repository

import com.ironlog.app.domain.model.Exercise
import com.ironlog.app.domain.model.MuscleGroup
import kotlinx.coroutines.flow.Flow

interface ExerciseRepository {
    fun getAllExercises(): Flow<List<Exercise>>
    fun getExercisesByMuscleGroup(muscleGroup: MuscleGroup): Flow<List<Exercise>>
    fun searchExercises(query: String): Flow<List<Exercise>>
    suspend fun getExerciseById(id: Long): Exercise?
    suspend fun getExercisesByIds(ids: List<Long>): List<Exercise>
    suspend fun addCustomExercise(exercise: Exercise): Long
    suspend fun deleteCustomExercise(id: Long)
}
