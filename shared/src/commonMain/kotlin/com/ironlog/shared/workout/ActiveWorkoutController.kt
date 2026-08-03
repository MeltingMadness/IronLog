package com.ironlog.shared.workout

import com.ironlog.shared.model.AppError
import com.ironlog.shared.model.WorkoutSession
import com.ironlog.shared.model.WorkoutSet
import com.ironlog.shared.model.toAppError
import com.ironlog.shared.repository.SharedWorkoutRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ActiveWorkoutState(
    val activeSession: WorkoutSession? = null,
    val sets: List<WorkoutSet> = emptyList(),
    val isLoading: Boolean = true,
    val error: AppError? = null,
)

class ActiveWorkoutController(
    private val scope: CoroutineScope,
    private val workoutRepository: SharedWorkoutRepository,
) {
    private val mutableState = MutableStateFlow(ActiveWorkoutState())
    private var setsObservationJob: Job? = null

    val state: StateFlow<ActiveWorkoutState> = mutableState.asStateFlow()

    init {
        scope.launch {
            runCatching {
                workoutRepository.observeActiveSession().collect { session ->
                    mutableState.value = mutableState.value.copy(
                        activeSession = session,
                        sets = if (session == null) emptyList() else mutableState.value.sets,
                        isLoading = false,
                        error = null,
                    )
                    observeSets(session?.id)
                }
            }.onFailure { error ->
                mutableState.value = mutableState.value.copy(
                    isLoading = false,
                    error = error.toAppError(),
                )
            }
        }
    }

    suspend fun startWorkout(name: String = "", planId: Long? = null, metaPlanId: Long? = null): Long {
        return runAction {
            workoutRepository.startWorkout(name = name, planId = planId, metaPlanId = metaPlanId)
        }
    }

    suspend fun finishWorkout() {
        val sessionId = state.value.activeSession?.id ?: return
        runAction {
            workoutRepository.finishWorkout(sessionId)
        }
    }

    suspend fun addSet(set: WorkoutSet): Long = runAction {
        workoutRepository.addSet(set)
    }

    suspend fun updateSet(set: WorkoutSet) {
        runAction {
            workoutRepository.updateSet(set)
        }
    }

    suspend fun deleteSet(setId: Long) {
        runAction {
            workoutRepository.deleteSet(setId)
        }
    }

    private fun observeSets(sessionId: Long?) {
        setsObservationJob?.cancel()
        if (sessionId == null) {
            mutableState.value = mutableState.value.copy(sets = emptyList())
            return
        }

        setsObservationJob = scope.launch {
            runCatching {
                workoutRepository.observeSetsForSession(sessionId).collect { sets ->
                    mutableState.value = mutableState.value.copy(
                        sets = sets,
                        isLoading = false,
                        error = null,
                    )
                }
            }.onFailure { error ->
                mutableState.value = mutableState.value.copy(
                    isLoading = false,
                    error = error.toAppError(),
                )
            }
        }
    }

    private suspend fun <T> runAction(action: suspend () -> T): T {
        mutableState.value = mutableState.value.copy(error = null)
        return runCatching { action() }
            .onFailure { error ->
                mutableState.value = mutableState.value.copy(error = error.toAppError())
            }
            .getOrThrow()
    }
}
