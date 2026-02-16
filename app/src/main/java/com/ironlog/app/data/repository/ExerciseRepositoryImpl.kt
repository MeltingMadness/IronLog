package com.ironlog.app.data.repository

import com.ironlog.app.data.local.dao.ExerciseDao
import com.ironlog.app.data.local.entity.ExerciseEntity
import com.ironlog.app.domain.model.Exercise
import com.ironlog.app.domain.model.MuscleGroup
import com.ironlog.app.domain.repository.ExerciseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ExerciseRepositoryImpl(
    private val exerciseDao: ExerciseDao
) : ExerciseRepository {

    override fun getAllExercises(): Flow<List<Exercise>> =
        exerciseDao.getAllExercises().map { list -> list.map { it.toDomain() } }

    override fun getExercisesByMuscleGroup(muscleGroup: MuscleGroup): Flow<List<Exercise>> =
        exerciseDao.getExercisesByMuscleGroup(muscleGroup.name).map { list -> list.map { it.toDomain() } }

    override fun searchExercises(query: String): Flow<List<Exercise>> =
        exerciseDao.searchExercises(query).map { list -> list.map { it.toDomain() } }

    override suspend fun getExerciseById(id: Long): Exercise? =
        exerciseDao.getExerciseById(id)?.toDomain()

    override suspend fun getExercisesByIds(ids: List<Long>): List<Exercise> {
        if (ids.isEmpty()) return emptyList()
        return exerciseDao.getExercisesByIds(ids).map { it.toDomain() }
    }

    override suspend fun addCustomExercise(exercise: Exercise): Long =
        exerciseDao.insert(ExerciseEntity.fromDomain(exercise.copy(isCustom = true)))

    override suspend fun deleteCustomExercise(id: Long) =
        exerciseDao.deleteCustomExercise(id)
}
