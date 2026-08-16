package com.ironlog.app.presentation.progression

import androidx.lifecycle.SavedStateHandle
import com.ironlog.app.domain.model.AppPreferences
import com.ironlog.app.domain.model.IntensitySystem
import com.ironlog.app.domain.model.ProgressionConfig
import com.ironlog.app.domain.model.ProgressionDecisionResult
import com.ironlog.app.domain.model.ProgressionGenerationResult
import com.ironlog.app.domain.model.ProgressionOutcome
import com.ironlog.app.domain.model.ProgressionReasonCode
import com.ironlog.app.domain.model.ProgressionStreakEffect
import com.ironlog.app.domain.model.ProgressionSuggestion
import com.ironlog.app.domain.model.ProgressionSuggestionStatus
import com.ironlog.app.domain.model.ProgressionTarget
import com.ironlog.app.domain.model.ReminderConfig
import com.ironlog.app.domain.model.ThemeMode
import com.ironlog.app.domain.model.ThemeScheme
import com.ironlog.app.domain.model.UnitSystem
import com.ironlog.app.domain.model.WeekStart
import com.ironlog.app.domain.model.WeightStep
import com.ironlog.app.domain.model.WorkoutPlanTarget
import com.ironlog.app.domain.model.WorkoutSet
import com.ironlog.app.domain.repository.AppPreferencesRepository
import com.ironlog.app.domain.repository.ProgressionRepository
import com.ironlog.app.domain.util.WeightFormatting
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressionReviewViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeProgressionRepository
    private lateinit var preferences: FakePreferencesRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeProgressionRepository()
        preferences = FakePreferencesRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `session scope is observed and init reconciles exactly once`() = runTest(dispatcher) {
        val viewModel = createViewModel(sessionId = 42L)
        advanceUntilIdle()

        assertEquals(listOf(42L), repository.observedSessionIds)
        assertEquals(1, repository.reconcileCalls)

        repository.reviewItems.value = listOf(pendingChange(id = 2L))
        preferences.setUnitSystem(UnitSystem.IMPERIAL)
        advanceUntilIdle()

        assertEquals(1, repository.reconcileCalls)
        assertEquals(UnitSystem.IMPERIAL, viewModel.uiState.value.unitSystem)
    }

    @Test
    fun `init reconciliation failure exposes only a code and preserves review items`() = runTest(dispatcher) {
        val leakPayload = "INIT_RECONCILE_SECRET_PAYLOAD"
        val originalItems = listOf(pendingChange(id = 2L))
        repository.reviewItems.value = originalItems
        repository.reconcileError = IllegalStateException(leakPayload)

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ProgressionReviewMessage.ACTION_FAILED, state.message)
        assertFalse(state.toString().contains(leakPayload))
        assertEquals(listOf(2L), state.items.map(ProgressionReviewItemUi::id))
        assertEquals(ProgressionSuggestionStatus.PENDING, state.items.single().status)
        assertEquals(originalItems, repository.reviewItems.value)
        assertEquals(ProgressionSuggestionStatus.PENDING, repository.reviewItems.value.single().status)
    }

    @Test
    fun `zero session id observes the all pending scope`() = runTest(dispatcher) {
        createViewModel(sessionId = 0L)
        advanceUntilIdle()

        assertEquals(listOf<Long?>(null), repository.observedSessionIds)
    }

    @Test
    fun `negative session id observes the all pending scope`() = runTest(dispatcher) {
        createViewModel(sessionId = -9L)
        advanceUntilIdle()

        assertEquals(listOf<Long?>(null), repository.observedSessionIds)
    }

    @Test
    fun `missing session id observes the all pending scope`() = runTest(dispatcher) {
        createViewModel(sessionId = null)
        advanceUntilIdle()

        assertEquals(listOf<Long?>(null), repository.observedSessionIds)
    }

    @Test
    fun `informational and non proposal rows never expose decision actions`() = runTest(dispatcher) {
        repository.reviewItems.value = listOf(
            information(id = 1L),
            pendingInformation(id = 2L),
            pendingChange(id = 3L)
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.items.first { it.id == 1L }.canDecide)
        assertFalse(state.items.first { it.id == 2L }.canDecide)
        assertTrue(state.items.first { it.id == 3L }.canDecide)
    }

    @Test
    fun `accept one with no edit sends the exact stored proposal`() = runTest(dispatcher) {
        val exactProposal = ProgressionTarget(sets = 3, reps = 8, weightKg = 45.359237)
        repository.reviewItems.value = listOf(pendingChange(id = 2L, proposed = exactProposal))
        val viewModel = createViewModel()
        advanceUntilIdle()
        repository.reconcileCalls = 0

        viewModel.acceptOne(2L)
        advanceUntilIdle()

        assertEquals(exactProposal, repository.lastAccepted.getValue(2L))
        assertEquals(1, repository.reconcileCalls)
    }

    @Test
    fun `accept one overrides only dirty fields and preserves canonical values`() = runTest(dispatcher) {
        val exactProposal = ProgressionTarget(sets = 3, reps = 8, weightKg = 45.359237)
        repository.reviewItems.value = listOf(pendingChange(id = 2L, proposed = exactProposal))
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.beginEdit(2L)
        val originalDraft = viewModel.uiState.value.edits.getValue(2L)
        viewModel.updateEdit(
            suggestionId = 2L,
            sets = "4",
            reps = originalDraft.reps,
            weight = originalDraft.weight
        )
        viewModel.acceptOne(2L)
        advanceUntilIdle()

        assertEquals(
            ProgressionTarget(sets = 4, reps = 8, weightKg = 45.359237),
            repository.lastAccepted.getValue(2L)
        )
    }

    @Test
    fun `dirty display weight uses the unit captured when editing started`() = runTest(dispatcher) {
        preferences.setUnitSystem(UnitSystem.IMPERIAL)
        repository.reviewItems.value = listOf(
            pendingChange(
                id = 2L,
                proposed = ProgressionTarget(sets = 3, reps = 8, weightKg = 45.359237)
            )
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.beginEdit(2L)
        val originalDraft = viewModel.uiState.value.edits.getValue(2L)
        viewModel.updateEdit(
            suggestionId = 2L,
            sets = originalDraft.sets,
            reps = originalDraft.reps,
            weight = "105"
        )
        preferences.setUnitSystem(UnitSystem.METRIC)
        advanceUntilIdle()
        viewModel.acceptOne(2L)
        advanceUntilIdle()

        assertEquals(
            UnitSystem.IMPERIAL,
            originalDraft.unitSystem
        )
        assertEquals(
            WeightFormatting.convertToKg(105.0, UnitSystem.IMPERIAL),
            repository.lastAccepted.getValue(2L).weightKg,
            0.000001
        )
    }

    @Test
    fun `invalid dirty fields stay inline and never call the repository`() = runTest(dispatcher) {
        repository.reviewItems.value = listOf(pendingChange(id = 2L))
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.updateEdit(
            suggestionId = 2L,
            sets = "0",
            reps = "eight",
            weight = "-1"
        )
        viewModel.acceptOne(2L)
        advanceUntilIdle()

        assertEquals(
            mapOf(
                ProgressionEditField.SETS to ProgressionEditError.INVALID_SETS,
                ProgressionEditField.REPS to ProgressionEditError.INVALID_REPS,
                ProgressionEditField.WEIGHT to ProgressionEditError.INVALID_WEIGHT
            ),
            viewModel.uiState.value.edits.getValue(2L).errors
        )
        assertTrue(repository.lastAccepted.isEmpty())
        assertFalse(viewModel.uiState.value.isWorking)
    }

    @Test
    fun `dismiss edit discards an unconfirmed draft`() = runTest(dispatcher) {
        repository.reviewItems.value = listOf(pendingChange(id = 2L))
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.beginEdit(2L)
        assertTrue(2L in viewModel.uiState.value.edits)

        viewModel.dismissEdit(2L)

        assertFalse(2L in viewModel.uiState.value.edits)
        assertTrue(repository.lastAccepted.isEmpty())
    }

    @Test
    fun `accept all sends only pending propose change rows`() = runTest(dispatcher) {
        repository.reviewItems.value = listOf(
            information(id = 1L),
            pendingChange(id = 2L),
            staleChange(id = 3L),
            acceptedChange(id = 4L),
            pendingInformation(id = 5L)
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.acceptAllSafe()
        advanceUntilIdle()

        assertEquals(setOf(2L), repository.lastAccepted.keys)
    }

    @Test
    fun `accept all selects newest source session once per plan position`() = runTest(dispatcher) {
        repository.reviewItems.value = listOf(
            pendingChange(id = 1L, sourceSessionId = 9L, planId = 3L, exerciseId = 7L, orderIndex = 0),
            pendingChange(id = 2L, sourceSessionId = 12L, planId = 3L, exerciseId = 7L, orderIndex = 0),
            pendingChange(id = 3L, sourceSessionId = 10L, planId = 3L, exerciseId = 8L, orderIndex = 0)
        )
        val viewModel = createViewModel()
        advanceUntilIdle()
        repository.reconcileCalls = 0

        viewModel.acceptAllSafe()
        advanceUntilIdle()

        assertEquals(setOf(2L, 3L), repository.lastAccepted.keys)
        assertEquals(1, repository.reconcileCalls)
    }

    @Test
    fun `accept all ignores drafts and sends exact stored proposals`() = runTest(dispatcher) {
        preferences.setUnitSystem(UnitSystem.IMPERIAL)
        val firstExact = ProgressionTarget(sets = 3, reps = 8, weightKg = 45.359237)
        val secondExact = ProgressionTarget(sets = 4, reps = 6, weightKg = 72.574779)
        repository.reviewItems.value = listOf(
            pendingChange(id = 2L, exerciseId = 7L, proposed = firstExact),
            pendingChange(id = 3L, exerciseId = 8L, proposed = secondExact)
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.beginEdit(2L)
        val draft = viewModel.uiState.value.edits.getValue(2L)
        viewModel.updateEdit(2L, draft.sets, draft.reps, "105")
        viewModel.acceptAllSafe()
        advanceUntilIdle()

        assertEquals(firstExact, repository.lastAccepted.getValue(2L))
        assertEquals(secondExact, repository.lastAccepted.getValue(3L))
    }

    @Test
    fun `accept all with no safe rows does not call the repository`() = runTest(dispatcher) {
        repository.reviewItems.value = listOf(information(id = 1L), staleChange(id = 3L))
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.acceptAllSafe()
        advanceUntilIdle()

        assertEquals(0, repository.acceptCalls)
        assertTrue(repository.lastAccepted.isEmpty())
    }

    @Test
    fun `accepted result refreshes once and leaves status to repository flow`() = runTest(dispatcher) {
        repository.reviewItems.value = listOf(pendingChange(id = 2L))
        val viewModel = createViewModel()
        advanceUntilIdle()
        repository.reconcileCalls = 0

        viewModel.acceptOne(2L)
        advanceUntilIdle()

        assertEquals(1, repository.reconcileCalls)
        assertEquals(
            ProgressionSuggestionStatus.PENDING,
            viewModel.uiState.value.items.single().status
        )
    }

    @Test
    fun `stale acceptance remains visible refreshes once and does not mutate status`() = runTest(dispatcher) {
        repository.reviewItems.value = listOf(pendingChange(id = 2L))
        repository.acceptResult = ProgressionDecisionResult.Stale(setOf(2L))
        val viewModel = createViewModel()
        advanceUntilIdle()
        repository.reconcileCalls = 0

        viewModel.acceptOne(2L)
        advanceUntilIdle()

        assertEquals(ProgressionReviewMessage.STALE, viewModel.uiState.value.message)
        assertEquals(1, repository.reconcileCalls)
        assertEquals(
            ProgressionSuggestionStatus.PENDING,
            viewModel.uiState.value.items.single().status
        )
    }

    @Test
    fun `invalid acceptance message remains visible without optimistic status`() = runTest(dispatcher) {
        repository.reviewItems.value = listOf(pendingChange(id = 2L))
        repository.acceptResult = ProgressionDecisionResult.Invalid("must not reach the UI")
        val viewModel = createViewModel()
        advanceUntilIdle()
        repository.reconcileCalls = 0

        viewModel.acceptOne(2L)
        advanceUntilIdle()

        assertEquals(ProgressionReviewMessage.INVALID, viewModel.uiState.value.message)
        assertEquals(0, repository.reconcileCalls)
        assertEquals(
            ProgressionSuggestionStatus.PENDING,
            viewModel.uiState.value.items.single().status
        )
    }

    @Test
    fun `accept failure exposes only a code clears working and preserves review status`() = runTest(dispatcher) {
        val leakPayload = "ACCEPT_SECRET_PAYLOAD"
        val originalItems = listOf(pendingChange(id = 2L))
        repository.reviewItems.value = originalItems
        repository.acceptError = IllegalStateException(leakPayload)
        val viewModel = createViewModel()
        advanceUntilIdle()
        repository.reconcileCalls = 0

        viewModel.acceptOne(2L)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ProgressionReviewMessage.ACTION_FAILED, state.message)
        assertFalse(state.isWorking)
        assertFalse(state.toString().contains(leakPayload))
        assertEquals(ProgressionSuggestionStatus.PENDING, state.items.single().status)
        assertEquals(originalItems, repository.reviewItems.value)
        assertEquals(ProgressionSuggestionStatus.PENDING, repository.reviewItems.value.single().status)
        assertEquals(0, repository.reconcileCalls)
    }

    @Test
    fun `double tap accepts once while the first acceptance is in flight`() = runTest(dispatcher) {
        repository.reviewItems.value = listOf(pendingChange(id = 2L))
        repository.acceptGate = CompletableDeferred()
        val viewModel = createViewModel()
        advanceUntilIdle()
        repository.reconcileCalls = 0

        viewModel.acceptOne(2L)
        viewModel.acceptOne(2L)
        runCurrent()

        assertEquals(1, repository.acceptCalls)
        assertTrue(viewModel.uiState.value.isWorking)

        repository.acceptGate?.complete(Unit)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isWorking)
        assertEquals(setOf(2L), repository.lastAccepted.keys)
        assertEquals(1, repository.reconcileCalls)
    }

    @Test
    fun `reject exposes working state and waits for repository flow to change status`() = runTest(dispatcher) {
        repository.reviewItems.value = listOf(pendingChange(id = 2L))
        repository.rejectGate = CompletableDeferred()
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.reject(2L)
        runCurrent()

        assertTrue(viewModel.uiState.value.isWorking)
        assertEquals(
            ProgressionSuggestionStatus.PENDING,
            viewModel.uiState.value.items.single().status
        )

        repository.rejectGate?.complete(Unit)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isWorking)
        assertEquals(listOf(2L), repository.rejectedIds)
        assertEquals(
            ProgressionSuggestionStatus.PENDING,
            viewModel.uiState.value.items.single().status
        )
    }

    @Test
    fun `reject failure clears working and reports error without optimistic mutation`() = runTest(dispatcher) {
        repository.reviewItems.value = listOf(pendingChange(id = 2L))
        repository.rejectError = IllegalStateException("must not reach the UI")
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.reject(2L)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isWorking)
        assertEquals(ProgressionReviewMessage.ACTION_FAILED, viewModel.uiState.value.message)
        assertEquals(
            ProgressionSuggestionStatus.PENDING,
            viewModel.uiState.value.items.single().status
        )
    }

    @Test
    fun `reject ignores rows without decision actions`() = runTest(dispatcher) {
        repository.reviewItems.value = listOf(information(id = 1L))
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.reject(1L)
        advanceUntilIdle()

        assertTrue(repository.rejectedIds.isEmpty())
        assertNull(viewModel.uiState.value.message)
    }

    private fun createViewModel(sessionId: Long? = 42L): ProgressionReviewViewModel {
        val handle = if (sessionId == null) {
            SavedStateHandle()
        } else {
            SavedStateHandle(mapOf("sessionId" to sessionId))
        }
        return ProgressionReviewViewModel(handle, repository, preferences)
    }

    private fun pendingChange(
        id: Long,
        sourceSessionId: Long = 12L,
        planId: Long = 3L,
        exerciseId: Long = 7L,
        orderIndex: Int = 0,
        proposed: ProgressionTarget = ProgressionTarget(sets = 3, reps = 9, weightKg = 102.5)
    ): ProgressionSuggestion = suggestion(
        id = id,
        sourceSessionId = sourceSessionId,
        planId = planId,
        exerciseId = exerciseId,
        orderIndex = orderIndex,
        outcome = ProgressionOutcome.ProposeChange(
            sourceTarget = SOURCE_TARGET,
            proposedTarget = proposed,
            reasonCode = ProgressionReasonCode.LOAD_ADVANCED,
            reasonArguments = mapOf("stepOriginalValue" to 2.5),
            streakEffect = ProgressionStreakEffect.RESET
        ),
        status = ProgressionSuggestionStatus.PENDING
    )

    private fun information(id: Long): ProgressionSuggestion = suggestion(
        id = id,
        outcome = ProgressionOutcome.KeepTarget(
            sourceTarget = SOURCE_TARGET,
            reasonCode = ProgressionReasonCode.REPEAT_TARGET,
            streakEffect = ProgressionStreakEffect.INCREMENT
        ),
        status = ProgressionSuggestionStatus.INFORMATIONAL
    )

    private fun pendingInformation(id: Long): ProgressionSuggestion = suggestion(
        id = id,
        outcome = ProgressionOutcome.KeepTarget(
            sourceTarget = SOURCE_TARGET,
            reasonCode = ProgressionReasonCode.REPEAT_TARGET,
            streakEffect = ProgressionStreakEffect.INCREMENT
        ),
        status = ProgressionSuggestionStatus.PENDING
    )

    private fun staleChange(id: Long): ProgressionSuggestion = pendingChange(id).copy(
        status = ProgressionSuggestionStatus.STALE
    )

    private fun acceptedChange(id: Long): ProgressionSuggestion = pendingChange(id).copy(
        status = ProgressionSuggestionStatus.ACCEPTED,
        finalTarget = (pendingChange(id).outcome as ProgressionOutcome.ProposeChange).proposedTarget
    )

    private fun suggestion(
        id: Long,
        sourceSessionId: Long = 12L,
        planId: Long = 3L,
        exerciseId: Long = 7L,
        orderIndex: Int = 0,
        outcome: ProgressionOutcome,
        status: ProgressionSuggestionStatus
    ): ProgressionSuggestion = ProgressionSuggestion(
        id = id,
        sourceTarget = WorkoutPlanTarget(
            id = 1000L + id,
            sessionId = sourceSessionId,
            planId = planId,
            exerciseId = exerciseId,
            orderIndex = orderIndex,
            supersetGroupId = null,
            target = SOURCE_TARGET,
            config = ProgressionConfig.Linear(
                step = WeightStep(
                    originalValue = 2.5,
                    originalUnit = UnitSystem.METRIC,
                    kilograms = 2.5
                )
            )
        ),
        outcome = outcome,
        countedSets = listOf(
            WorkoutSet(
                id = 2000L + id,
                sessionId = sourceSessionId,
                exerciseId = exerciseId,
                setNumber = 1,
                reps = 8,
                weightKg = 100.0,
                rpe = 8.0,
                planTargetSnapshotId = 1000L + id
            )
        ),
        status = status,
        wasEdited = false,
        finalTarget = null,
        createdAtEpochMillis = id,
        decidedAtEpochMillis = null
    )

    private companion object {
        val SOURCE_TARGET = ProgressionTarget(sets = 3, reps = 8, weightKg = 100.0)
    }
}

private class FakeProgressionRepository : ProgressionRepository {
    val reviewItems = MutableStateFlow<List<ProgressionSuggestion>>(emptyList())
    val observedSessionIds = mutableListOf<Long?>()
    var reconcileCalls = 0
    var reconcileError: Throwable? = null
    var acceptCalls = 0
    var lastAccepted: Map<Long, ProgressionTarget> = emptyMap()
    var acceptResult: ProgressionDecisionResult? = null
    var acceptError: Throwable? = null
    var acceptGate: CompletableDeferred<Unit>? = null
    val rejectedIds = mutableListOf<Long>()
    var rejectGate: CompletableDeferred<Unit>? = null
    var rejectError: Throwable? = null

    override fun observeTargetsForSession(sessionId: Long): Flow<List<WorkoutPlanTarget>> =
        MutableStateFlow(emptyList())

    override fun observeReviewItems(sessionId: Long?): Flow<List<ProgressionSuggestion>> {
        observedSessionIds += sessionId
        return reviewItems
    }

    override fun observePendingCount(): Flow<Int> = MutableStateFlow(0)

    override suspend fun generateOutcomesForSession(sessionId: Long): ProgressionGenerationResult =
        ProgressionGenerationResult(insertedCount = 0, reviewItemCount = 0, pendingCount = 0)

    override suspend fun generateMissingOutcomes(): Int = 0

    override suspend fun reconcileOutstandingSuggestions(): Set<Long> {
        reconcileCalls += 1
        reconcileError?.let { throw it }
        return emptySet()
    }

    override suspend fun acceptSuggestions(
        finalTargetsBySuggestionId: Map<Long, ProgressionTarget>
    ): ProgressionDecisionResult {
        acceptCalls += 1
        lastAccepted = finalTargetsBySuggestionId.toMap()
        acceptGate?.await()
        acceptError?.let { throw it }
        return acceptResult ?: ProgressionDecisionResult.Accepted(finalTargetsBySuggestionId.keys)
    }

    override suspend fun rejectSuggestion(suggestionId: Long) {
        rejectedIds += suggestionId
        rejectGate?.await()
        rejectError?.let { throw it }
    }
}

private class FakePreferencesRepository : AppPreferencesRepository {
    private val state = MutableStateFlow(AppPreferences())
    override val preferences: Flow<AppPreferences> = state

    fun setUnitSystem(unitSystem: UnitSystem) {
        state.value = state.value.copy(unitSystem = unitSystem)
    }

    override suspend fun updateUnitSystem(unitSystem: UnitSystem) = setUnitSystem(unitSystem)

    override suspend fun updateWeekStart(weekStart: WeekStart) {
        state.value = state.value.copy(weekStart = weekStart)
    }

    override suspend fun updateThemeMode(themeMode: ThemeMode) {
        state.value = state.value.copy(themeMode = themeMode)
    }

    override suspend fun updateThemeScheme(themeScheme: ThemeScheme) {
        state.value = state.value.copy(themeScheme = themeScheme)
    }

    override suspend fun updateUseDynamicColor(enabled: Boolean) {
        state.value = state.value.copy(useDynamicColor = enabled)
    }

    override suspend fun updateReducedMotion(enabled: Boolean) {
        state.value = state.value.copy(reducedMotion = enabled)
    }

    override suspend fun updateDefaultWarmupFlag(enabled: Boolean) {
        state.value = state.value.copy(defaultWarmupFlag = enabled)
    }

    override suspend fun updateTimerKeepScreenOn(enabled: Boolean) {
        state.value = state.value.copy(timerKeepScreenOn = enabled)
    }

    override suspend fun updateBetaDiagnosticsOptIn(enabled: Boolean) {
        state.value = state.value.copy(betaDiagnosticsOptIn = enabled)
    }

    override suspend fun updateReminderConfig(config: ReminderConfig) {
        state.value = state.value.copy(reminderConfig = config)
    }

    override suspend fun updateIntensitySystem(intensitySystem: IntensitySystem) {
        state.value = state.value.copy(intensitySystem = intensitySystem)
    }

    override suspend fun updateShareWeightHistoryAcrossContexts(enabled: Boolean) {
        state.value = state.value.copy(shareWeightHistoryAcrossContexts = enabled)
    }
}
