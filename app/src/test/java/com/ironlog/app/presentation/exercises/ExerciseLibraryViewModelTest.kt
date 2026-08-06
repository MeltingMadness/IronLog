package com.ironlog.app.presentation.exercises

import com.ironlog.app.domain.model.Exercise
import com.ironlog.app.domain.model.ExerciseCategory
import com.ironlog.app.domain.model.MuscleGroup
import com.ironlog.app.fakes.FakeExerciseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExerciseLibraryViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var exerciseRepository: FakeExerciseRepository

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        exerciseRepository = FakeExerciseRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `saveCustomExercise adds custom exercise with optional fields`() = runTest {
        val viewModel = ExerciseLibraryViewModel(exerciseRepository)
        backgroundScope.launch { viewModel.uiState.collect { } }
        viewModel.onShowAddDialog()

        viewModel.saveCustomExercise(
            id = null,
            name = "Cable Fly hoch",
            primaryMuscleGroup = MuscleGroup.BRUST,
            secondaryMuscleGroups = listOf(MuscleGroup.SCHULTERN),
            category = ExerciseCategory.KABEL,
            notes = "Langsam exzentrisch"
        )
        advanceUntilIdle()

        val created = exerciseRepository.searchExercises("Cable Fly").first().first()
        assertTrue(created.isCustom)
        assertEquals(listOf(MuscleGroup.SCHULTERN), created.secondaryMuscleGroups)
        assertEquals("Langsam exzentrisch", created.notes)
        assertFalse(viewModel.uiState.value.showExerciseDialog)
    }

    @Test
    fun `saveCustomExercise in edit mode updates custom exercise`() = runTest {
        val existing = Exercise(
            id = 10L,
            name = "Pushup",
            primaryMuscleGroup = MuscleGroup.BRUST,
            category = ExerciseCategory.EIGENGEWICHT,
            isCustom = true
        )
        exerciseRepository.addExercise(existing)
        val viewModel = ExerciseLibraryViewModel(exerciseRepository)
        backgroundScope.launch { viewModel.uiState.collect { } }

        viewModel.onShowEditDialog(existing)
        viewModel.saveCustomExercise(
            id = existing.id,
            name = "Pushup langsam",
            primaryMuscleGroup = MuscleGroup.BRUST,
            secondaryMuscleGroups = listOf(MuscleGroup.TRIZEPS),
            category = ExerciseCategory.EIGENGEWICHT,
            notes = "2s Pause unten"
        )
        advanceUntilIdle()

        val updated = exerciseRepository.getExerciseById(existing.id)
        assertNotNull(updated)
        assertEquals("Pushup langsam", updated?.name)
        assertEquals(listOf(MuscleGroup.TRIZEPS), updated?.secondaryMuscleGroups)
        assertEquals("2s Pause unten", updated?.notes)
    }

    @Test
    fun `archived exercises are hidden from ui state`() = runTest {
        exerciseRepository.addExercise(
            Exercise(
                id = 1L,
                name = "Archiviert",
                primaryMuscleGroup = MuscleGroup.BRUST,
                category = ExerciseCategory.KABEL,
                isCustom = true,
                isArchived = true
            )
        )
        exerciseRepository.addExercise(
            Exercise(
                id = 2L,
                name = "Aktiv",
                primaryMuscleGroup = MuscleGroup.BRUST,
                category = ExerciseCategory.KABEL,
                isCustom = true
            )
        )

        val viewModel = ExerciseLibraryViewModel(exerciseRepository)
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()

        val names = viewModel.uiState.value.exercises.map { it.name }
        assertEquals(listOf("Aktiv"), names)
    }

    @Test
    fun `error while loading exercises clears isLoading and surfaces an error message`() = runTest {
        exerciseRepository.errorToThrow = RuntimeException("DB kaputt")

        val viewModel = ExerciseLibraryViewModel(exerciseRepository)
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()

        assertFalse("isLoading must not stay stuck at true after a load error", viewModel.uiState.value.isLoading)
        assertNotNull(viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.exercises.isEmpty())
    }
}
