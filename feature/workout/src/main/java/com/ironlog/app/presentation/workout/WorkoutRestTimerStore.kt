package com.ironlog.app.presentation.workout

import com.ironlog.app.domain.repository.AppPreferencesRepository
import com.ironlog.app.domain.repository.WorkoutRepository
import com.ironlog.app.domain.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZoneId

/**
 * Rest timers of one active session: in-memory state plus its durable copy in the app
 * preferences, so running timers survive ViewModel recreation and process death.
 */
internal class WorkoutRestTimerStore(
    private val sessionId: Long,
    private val workoutRepository: WorkoutRepository,
    private val appPreferencesRepository: AppPreferencesRepository
) {
    private val _restTimers = MutableStateFlow<Map<WorkoutExerciseKey, RestTimerUi>>(emptyMap())
    val timers: StateFlow<Map<WorkoutExerciseKey, RestTimerUi>> = _restTimers.asStateFlow()
    private var restTimerSessionStartEpochMillis: Long? = null
    /**
     * Serializes restore, in-memory timer updates and their durable writes. The restore read can
     * suspend on DataStore; keeping it under this mutex prevents a later cleanup write from
     * racing a timer mutation that started while the read was in flight.
     */
    private val restTimerMutationMutex = Mutex()
    private var restTimerMutationGeneration = 0L

    suspend fun restore() {
        restTimerMutationMutex.withLock {
            val hasLiveMutation = restTimerMutationGeneration > 0L
            val sessionResult = runCatching { workoutRepository.getSessionById(sessionId) }
                .onFailure { e ->
                    AppLogger.w(
                        "ActiveWorkoutVM",
                        "Aktive Session fuer Rest-Timer konnte nicht geladen werden: ${e.message}",
                        e
                    )
                }
            val session = sessionResult.getOrNull()
            val sessionStartEpochMillis = session?.startTime
                ?.atZone(ZoneId.systemDefault())
                ?.toInstant()
                ?.toEpochMilli()
            restTimerSessionStartEpochMillis = sessionStartEpochMillis

            val encodedStateResult = runCatching {
                appPreferencesRepository.readRestTimerState(sessionId)
            }
            val encodedState = encodedStateResult.getOrNull()

            if (sessionResult.isFailure || encodedStateResult.isFailure) {
                // Keep any live state intact when either read fails. A transient DataStore or
                // database error must not turn a visible timer into an empty map. Subsequent
                // timer mutations still persist through [replace]. If state is
                // already present, refresh its durable copy while the mutex is held.
                if (_restTimers.value.isNotEmpty() && sessionStartEpochMillis != null) {
                    persistRestTimerState(
                        encodeRestTimerState(_restTimers.value, sessionStartEpochMillis)
                    )
                }
                if (encodedStateResult.isFailure) {
                    AppLogger.w(
                        "ActiveWorkoutVM",
                        "Rest-Timer konnten nicht gelesen werden: ${encodedStateResult.exceptionOrNull()?.message}",
                        encodedStateResult.exceptionOrNull()
                    )
                }
                return@withLock
            }

            // Restore only a still-active session. A completed or missing session must clear
            // any stale payload left behind by navigation, import, or a reused database id.
            // If a timer mutation won the mutex before this restore started, preserve that
            // live state and make it the durable source of truth instead of overwriting it
            // with an older payload (or an empty read after a failed write).
            if (session?.endTime != null || session == null || sessionStartEpochMillis == null) {
                _restTimers.value = emptyMap()
            } else if (!hasLiveMutation) {
                if (session?.endTime == null && sessionStartEpochMillis != null && encodedState != null) {
                    _restTimers.value = decodeRestTimerState(
                        encodedState = encodedState,
                        expectedSessionStartEpochMillis = sessionStartEpochMillis
                    )
                } else {
                    _restTimers.value = emptyMap()
                }
            }

            if (session?.endTime != null || session == null || sessionStartEpochMillis == null) {
                persistRestTimerState(null)
            } else if (hasLiveMutation) {
                persistRestTimerState(
                    if (_restTimers.value.isEmpty()) {
                        null
                    } else {
                        encodeRestTimerState(_restTimers.value, sessionStartEpochMillis)
                    }
                )
            } else if (encodedState != null && _restTimers.value.isEmpty()) {
                // Invalid or stale payload (including a different session start) is removed
                // so it cannot be reconsidered on every subsequent ViewModel recreation.
                persistRestTimerState(null)
            }
        }
    }

    private suspend fun persistRestTimerState(encodedState: String?) {
        runCatching {
            appPreferencesRepository.writeRestTimerState(
                sessionId = sessionId,
                encodedState = encodedState
            )
        }.onFailure { e ->
            AppLogger.w(
                "ActiveWorkoutVM",
                "Rest-Timer konnten nicht gespeichert werden: ${e.message}",
                e
            )
        }
    }

    suspend fun replace(
        transform: (Map<WorkoutExerciseKey, RestTimerUi>) -> Map<WorkoutExerciseKey, RestTimerUi>
    ) {
        restTimerMutationMutex.withLock {
            restTimerMutationGeneration += 1L
            val updatedTimers = transform(_restTimers.value)
            _restTimers.value = updatedTimers
            val sessionStartEpochMillis = restTimerSessionStartEpochMillis
            val encodedState = if (updatedTimers.isEmpty() || sessionStartEpochMillis == null) {
                null
            } else {
                encodeRestTimerState(updatedTimers, sessionStartEpochMillis)
            }
            persistRestTimerState(encodedState)
        }
    }

    suspend fun clear() {
        replace { emptyMap() }
    }
}
