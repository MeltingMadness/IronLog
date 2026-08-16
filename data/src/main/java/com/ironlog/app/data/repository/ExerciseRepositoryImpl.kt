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

    override suspend fun addCustomExercise(exercise: Exercise): Long {
        val sanitized = sanitizeAndValidate(exercise.copy(id = 0L, isCustom = true, isArchived = false))
        validateUniqueName(name = sanitized.name, excludeId = null)
        return exerciseDao.insert(ExerciseEntity.fromDomain(sanitized))
    }

    override suspend fun updateCustomExercise(exercise: Exercise) {
        require(exercise.id > 0L) { "Nur gespeicherte Uebungen koennen bearbeitet werden" }
        val existing = exerciseDao.getExerciseById(exercise.id)
            ?: throw IllegalArgumentException("Uebung wurde nicht gefunden")
        require(existing.isCustom) { "Nur eigene Uebungen koennen bearbeitet werden" }

        val sanitized = sanitizeAndValidate(
            exercise.copy(
                isCustom = true,
                isArchived = existing.isArchived
            )
        )
        validateUniqueName(name = sanitized.name, excludeId = sanitized.id)
        exerciseDao.updateExercise(ExerciseEntity.fromDomain(sanitized))
    }

    override suspend fun deleteCustomExercise(id: Long) {
        val existing = exerciseDao.getExerciseById(id)
            ?: throw IllegalArgumentException("Uebung wurde nicht gefunden")
        require(existing.isCustom) { "Nur eigene Uebungen koennen geloescht werden" }

        if (exerciseDao.isExerciseReferenced(id)) {
            exerciseDao.archiveCustomExercise(id)
        } else {
            exerciseDao.deleteCustomExercise(id)
        }
    }

    private suspend fun validateUniqueName(name: String, excludeId: Long?) {
        // SQLite lower()/NOCASE fold only ASCII, so umlaut variants like
        // "BANKDRÜCKEN" vs "Bankdrücken" are compared unicode-aware in Kotlin.
        // The DAO therefore returns all active candidate names for comparison.
        val normalized = name.trim().lowercase()
        val hasDuplicate = exerciseDao.getActiveExerciseNames(excludeId = excludeId)
            .any { it.trim().lowercase() == normalized }
        require(!hasDuplicate) { "Eine aktive Uebung mit diesem Namen existiert bereits" }
    }

    private fun sanitizeAndValidate(exercise: Exercise): Exercise {
        val name = exercise.name.trim()
        require(name.length in 3..60) { "Name muss zwischen 3 und 60 Zeichen haben" }

        val notes = exercise.notes.trim()
        require(notes.length <= 240) { "Notiz darf maximal 240 Zeichen enthalten" }

        val secondary = exercise.secondaryMuscleGroups.distinct()
        require(secondary.size <= 3) { "Maximal drei sekundaere Muskelgruppen erlaubt" }
        require(exercise.primaryMuscleGroup !in secondary) {
            "Primaere Muskelgruppe darf nicht gleichzeitig sekundaer sein"
        }

        return exercise.copy(
            name = name,
            notes = notes,
            secondaryMuscleGroups = secondary
        )
    }
}
