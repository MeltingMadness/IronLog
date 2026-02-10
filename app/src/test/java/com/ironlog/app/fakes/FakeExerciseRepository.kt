package com.ironlog.app.fakes

import com.ironlog.app.domain.model.Exercise
import com.ironlog.app.domain.model.MuscleGroup
import com.ironlog.app.domain.repository.ExerciseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeExerciseRepository : ExerciseRepository {

    private val exercises = MutableStateFlow<List<Exercise>>(emptyList())
    private var nextId = 1L

    fun addExercise(exercise: Exercise) {
        val e = if (exercise.id == 0L) exercise.copy(id = nextId++) else exercise
        exercises.value = exercises.value + e
    }

    override fun getAllExercises(): Flow<List<Exercise>> = exercises

    override fun getExercisesByMuscleGroup(muscleGroup: MuscleGroup): Flow<List<Exercise>> =
        exercises.map { list -> list.filter { it.primaryMuscleGroup == muscleGroup } }

    override fun searchExercises(query: String): Flow<List<Exercise>> =
        exercises.map { list -> list.filter { it.name.contains(query, ignoreCase = true) } }

    override suspend fun getExerciseById(id: Long): Exercise? =
        exercises.value.find { it.id == id }

    override suspend fun addCustomExercise(exercise: Exercise): Long {
        val id = nextId++
        exercises.value = exercises.value + exercise.copy(id = id, isCustom = true)
        return id
    }

    override suspend fun deleteCustomExercise(id: Long) {
        exercises.value = exercises.value.filter { it.id != id }
    }
}
