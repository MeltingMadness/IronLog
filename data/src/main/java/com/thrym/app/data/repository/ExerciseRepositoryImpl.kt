package com.thrym.app.data.repository

import com.thrym.app.data.local.dao.ExerciseDao
import com.thrym.app.data.local.entity.ExerciseEntity
import com.thrym.app.domain.error.ExerciseValidationException
import com.thrym.app.domain.error.ExerciseValidationException.Reason
import com.thrym.app.domain.model.Exercise
import com.thrym.app.domain.model.MuscleGroup
import com.thrym.app.domain.repository.ExerciseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ExerciseRepositoryImpl(
    private val exerciseDao: ExerciseDao,
    /**
     * Built-in exercise names in every shipped UI language. Built-ins are stored under their
     * German canonical name, so the DAO check alone would let a custom exercise reuse a
     * translated built-in name (e.g. "Deadlift") and show up twice in the list, either right
     * away or after a language switch.
     */
    private val localizedBuiltInNames: () -> Collection<String> = { emptyList() }
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
        if (exercise.id <= 0L) fail(Reason.NOT_SAVED, "Only saved exercises can be edited")
        val existing = exerciseDao.getExerciseById(exercise.id)
            ?: fail(Reason.NOT_FOUND, "Exercise ${exercise.id} not found")
        if (!existing.isCustom) fail(Reason.NOT_CUSTOM_EDIT, "Only custom exercises can be edited")

        val sanitized = sanitizeAndValidate(
            exercise.copy(
                isCustom = true,
                isArchived = existing.isArchived
            )
        )
        validateUniqueName(name = sanitized.name, excludeId = sanitized.id, previousName = existing.name)
        exerciseDao.updateExercise(ExerciseEntity.fromDomain(sanitized))
    }

    override suspend fun deleteCustomExercise(id: Long) {
        val existing = exerciseDao.getExerciseById(id)
            ?: fail(Reason.NOT_FOUND, "Exercise $id not found")
        if (!existing.isCustom) fail(Reason.NOT_CUSTOM_DELETE, "Only custom exercises can be deleted")

        if (exerciseDao.isExerciseReferenced(id)) {
            exerciseDao.archiveCustomExercise(id)
        } else {
            exerciseDao.deleteCustomExercise(id)
        }
    }

    private suspend fun validateUniqueName(name: String, excludeId: Long?, previousName: String? = null) {
        // SQLite lower()/NOCASE fold only ASCII, so umlaut variants like
        // "BANKDRÜCKEN" vs "Bankdrücken" are compared unicode-aware in Kotlin.
        // The DAO therefore returns all active candidate names for comparison.
        val normalized = name.trim().lowercase()
        // A custom exercise created before its name became a translated built-in name
        // (or in another language) keeps working as long as it is not renamed.
        val reservedNames = if (previousName?.trim()?.lowercase() == normalized) emptyList() else localizedBuiltInNames()
        val hasDuplicate = (exerciseDao.getActiveExerciseNames(excludeId = excludeId) + reservedNames)
            .any { it.trim().lowercase() == normalized }
        if (hasDuplicate) fail(Reason.DUPLICATE_NAME, "An active exercise with this name already exists")
    }

    private fun sanitizeAndValidate(exercise: Exercise): Exercise {
        val name = exercise.name.trim()
        if (name.length !in ExerciseValidationException.NAME_MIN_LENGTH..ExerciseValidationException.NAME_MAX_LENGTH) {
            fail(Reason.NAME_LENGTH, "Name must have 3 to 60 characters")
        }

        val notes = exercise.notes.trim()
        if (notes.length > ExerciseValidationException.NOTES_MAX_LENGTH) {
            fail(Reason.NOTES_TOO_LONG, "Notes must not exceed 240 characters")
        }

        val secondary = exercise.secondaryMuscleGroups.distinct()
        if (secondary.size > ExerciseValidationException.MAX_SECONDARY_GROUPS) {
            fail(Reason.TOO_MANY_SECONDARY_GROUPS, "At most three secondary muscle groups are allowed")
        }
        if (exercise.primaryMuscleGroup in secondary) {
            fail(Reason.PRIMARY_ALSO_SECONDARY, "Primary muscle group must not also be secondary")
        }

        return exercise.copy(
            name = name,
            notes = notes,
            secondaryMuscleGroups = secondary
        )
    }

    private fun fail(reason: Reason, message: String): Nothing =
        throw ExerciseValidationException(reason, message)
}
