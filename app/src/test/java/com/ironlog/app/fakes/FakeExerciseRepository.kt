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
    var getExerciseByIdCallCount = 0

    fun addExercise(exercise: Exercise) {
        val e = if (exercise.id == 0L) exercise.copy(id = nextId++) else exercise
        exercises.value = exercises.value + e
    }

    override fun getAllExercises(): Flow<List<Exercise>> = exercises

    override fun getExercisesByMuscleGroup(muscleGroup: MuscleGroup): Flow<List<Exercise>> =
        exercises.map { list -> list.filter { it.primaryMuscleGroup == muscleGroup } }

    override fun searchExercises(query: String): Flow<List<Exercise>> =
        exercises.map { list -> list.filter { it.name.contains(query, ignoreCase = true) } }

    override suspend fun getExerciseById(id: Long): Exercise? {
        getExerciseByIdCallCount++
        return exercises.value.find { it.id == id }
    }

    override suspend fun getExercisesByIds(ids: List<Long>): List<Exercise> {
        val idSet = ids.toSet()
        return exercises.value.filter { it.id in idSet }
    }

    override suspend fun addCustomExercise(exercise: Exercise): Long {
        val id = nextId++
        exercises.value = exercises.value + exercise.copy(id = id, isCustom = true, isArchived = false)
        return id
    }

    override suspend fun updateCustomExercise(exercise: Exercise) {
        exercises.value = exercises.value.map { current ->
            if (current.id == exercise.id) {
                exercise.copy(isCustom = true, isArchived = current.isArchived)
            } else {
                current
            }
        }
    }

    override suspend fun deleteCustomExercise(id: Long) {
        val isReferenced = false
        exercises.value = exercises.value.mapNotNull { current ->
            if (current.id != id) return@mapNotNull current
            if (!current.isCustom) return@mapNotNull current
            if (isReferenced) current.copy(isArchived = true) else null
        }
    }
}
