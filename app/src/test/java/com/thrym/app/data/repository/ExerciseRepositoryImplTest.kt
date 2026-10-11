package com.thrym.app.data.repository

import com.thrym.app.data.local.dao.ExerciseDao
import com.thrym.app.data.local.entity.ExerciseEntity
import com.thrym.app.domain.error.ExerciseValidationException
import com.thrym.app.domain.model.Exercise
import com.thrym.app.domain.model.ExerciseCategory
import com.thrym.app.domain.model.MuscleGroup
import com.thrym.app.domain.repository.ExerciseRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.test.assertFailsWith

class ExerciseRepositoryImplTest {

    private val exerciseDao: ExerciseDao = mockk(relaxed = true)
    private val repository: ExerciseRepository = ExerciseRepositoryImpl(exerciseDao)

    private fun customExercise(name: String, id: Long = 0L) = Exercise(
        id = id,
        name = name,
        primaryMuscleGroup = MuscleGroup.BRUST,
        secondaryMuscleGroups = emptyList(),
        category = ExerciseCategory.LANGHANTEL,
        isCustom = true
    )

    private fun customEntity(id: Long, name: String) = ExerciseEntity(
        id = id,
        name = name,
        primaryMuscleGroup = "BRUST",
        secondaryMuscleGroups = "",
        category = "LANGHANTEL",
        isCustom = true
    )

    @Test
    fun `addCustomExercise akzeptiert Namen ohne Duplikat und speichert normalisiert`() = runTest {
        coEvery { exerciseDao.getActiveExerciseNames(excludeId = null) } returns emptyList()
        coEvery { exerciseDao.insert(any()) } returns 42L

        val id = repository.addCustomExercise(customExercise(name = "  Bankdrücken (Kurzhantel)  "))

        assertEquals(42L, id)
        coVerify(exactly = 1) {
            exerciseDao.insert(
                match { entity ->
                    entity.name == "Bankdrücken (Kurzhantel)" &&
                        entity.isCustom &&
                        !entity.isArchived &&
                        entity.id == 0L
                }
            )
        }
    }

    @Test
    fun `addCustomExercise lehnt doppelten Namen ab`() = runTest {
        coEvery { exerciseDao.getActiveExerciseNames(excludeId = null) } returns listOf("Bankdrücken (Kurzhantel)")

        assertFailsWith<IllegalArgumentException> {
            repository.addCustomExercise(customExercise(name = "Bankdrücken (Kurzhantel)"))
        }
        coVerify(exactly = 0) { exerciseDao.insert(any()) }
    }

    @Test
    fun `addCustomExercise erkennt Umlaut-Duplikat trotz anderer Grossschreibung`() = runTest {
        // SQLite lower()/NOCASE fold nur ASCII; der Umlaut-Fall muss in Kotlin
        // unicode-aware verglichen werden, sobald der DAO die Kandidaten liefert.
        coEvery { exerciseDao.getActiveExerciseNames(excludeId = null) } returns listOf("Bankdrücken")

        assertFailsWith<IllegalArgumentException> {
            repository.addCustomExercise(customExercise(name = "BANKDRÜCKEN"))
        }
        coVerify(exactly = 0) { exerciseDao.insert(any()) }
    }

    @Test
    fun `updateCustomExercise erkennt Duplikat und schliesst die eigene Id aus`() = runTest {
        coEvery { exerciseDao.getExerciseById(5L) } returns customEntity(id = 5L, name = "Kniebeuge")
        coEvery { exerciseDao.getActiveExerciseNames(excludeId = 5L) } returns listOf("Bankdrücken")

        assertFailsWith<IllegalArgumentException> {
            repository.updateCustomExercise(customExercise(name = "BANKDRÜCKEN", id = 5L))
        }
        coVerify(exactly = 1) { exerciseDao.getActiveExerciseNames(excludeId = 5L) }
    }

    @Test
    fun `deleteCustomExercise archiviert statt zu loeschen wenn via progression_suggestions referenziert`() = runTest {
        coEvery { exerciseDao.getExerciseById(7L) } returns customEntity(id = 7L, name = "Bankdrücken")
        coEvery { exerciseDao.isExerciseReferenced(7L) } returns true

        repository.deleteCustomExercise(7L)

        coVerify(exactly = 1) { exerciseDao.archiveCustomExercise(7L) }
        coVerify(exactly = 0) { exerciseDao.deleteCustomExercise(any()) }
    }

    @Test
    fun `deleteCustomExercise loescht endgueltig wenn nicht referenziert`() = runTest {
        coEvery { exerciseDao.getExerciseById(7L) } returns customEntity(id = 7L, name = "Bankdrücken")
        coEvery { exerciseDao.isExerciseReferenced(7L) } returns false

        repository.deleteCustomExercise(7L)

        coVerify(exactly = 1) { exerciseDao.deleteCustomExercise(7L) }
        coVerify(exactly = 0) { exerciseDao.archiveCustomExercise(any()) }
    }

    @Test
    fun `deleteCustomExercise lehnt nicht eigene Uebungen ab`() = runTest {
        coEvery { exerciseDao.getExerciseById(7L) } returns customEntity(id = 7L, name = "Bankdrücken").copy(isCustom = false)

        assertFailsWith<IllegalArgumentException> {
            repository.deleteCustomExercise(7L)
        }
        coVerify(exactly = 0) { exerciseDao.archiveCustomExercise(any()) }
        coVerify(exactly = 0) { exerciseDao.deleteCustomExercise(any()) }
    }

    @Test
    fun `validation failures carry a reason the UI can localize`() = runTest {
        coEvery { exerciseDao.getActiveExerciseNames(excludeId = null) } returns listOf("Bankdrücken")
        coEvery { exerciseDao.getExerciseById(7L) } returns customEntity(id = 7L, name = "Bankdrücken").copy(isCustom = false)

        suspend fun reasonOf(block: suspend () -> Unit): ExerciseValidationException.Reason =
            assertFailsWith<ExerciseValidationException> { block() }.reason

        assertEquals(ExerciseValidationException.Reason.DUPLICATE_NAME, reasonOf { repository.addCustomExercise(customExercise("bankdrücken")) })
        assertEquals(ExerciseValidationException.Reason.NAME_LENGTH, reasonOf { repository.addCustomExercise(customExercise("ab")) })
        assertEquals(
            ExerciseValidationException.Reason.NOTES_TOO_LONG,
            reasonOf { repository.addCustomExercise(customExercise("Rudern").copy(notes = "x".repeat(241))) }
        )
        assertEquals(
            ExerciseValidationException.Reason.PRIMARY_ALSO_SECONDARY,
            reasonOf { repository.addCustomExercise(customExercise("Rudern").copy(secondaryMuscleGroups = listOf(MuscleGroup.BRUST))) }
        )
        assertEquals(ExerciseValidationException.Reason.NOT_CUSTOM_DELETE, reasonOf { repository.deleteCustomExercise(7L) })
        assertEquals(ExerciseValidationException.Reason.NOT_CUSTOM_EDIT, reasonOf { repository.updateCustomExercise(customExercise("Rudern", id = 7L)) })
    }

    @Test
    fun `addCustomExercise rejects the localized name of a built-in exercise`() = runTest {
        // Built-ins are stored under their German canonical name ("Kreuzheben"); in an English UI
        // the user sees "Deadlift", so a custom "deadlift" would show up twice.
        val localized = ExerciseRepositoryImpl(exerciseDao) { listOf("Deadlift") }
        coEvery { exerciseDao.getActiveExerciseNames(excludeId = null) } returns listOf("Kreuzheben")

        val error = assertFailsWith<ExerciseValidationException> {
            localized.addCustomExercise(customExercise(name = " deadlift "))
        }

        assertEquals(ExerciseValidationException.Reason.DUPLICATE_NAME, error.reason)
        coVerify(exactly = 0) { exerciseDao.insert(any()) }
    }

    @Test
    fun `updateCustomExercise keeps an unchanged name that later became a localized built-in name`() = runTest {
        val localized = ExerciseRepositoryImpl(exerciseDao) { listOf("Deadlift") }
        coEvery { exerciseDao.getExerciseById(5L) } returns customEntity(id = 5L, name = "Deadlift")
        coEvery { exerciseDao.getActiveExerciseNames(excludeId = 5L) } returns listOf("Kreuzheben")

        localized.updateCustomExercise(customExercise(name = "Deadlift", id = 5L).copy(notes = "Pause unten"))

        coVerify(exactly = 1) { exerciseDao.updateExercise(match { it.name == "Deadlift" && it.notes == "Pause unten" }) }
    }
}
