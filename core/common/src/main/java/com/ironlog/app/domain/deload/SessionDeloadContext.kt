package com.ironlog.app.domain.deload

import com.ironlog.app.domain.model.WorkoutSession
import com.ironlog.shared.readiness.DeloadState

/**
 * Maps the explicitly stored per-session deload context ([WorkoutSession.isDeload])
 * into the shared readiness-engine vocabulary.
 *
 * Frozen platform contract (11.09.2026):
 * - `true`  -> [DeloadState.PLANNED_DELOAD]
 * - `false` -> [DeloadState.NONE]
 * - `null`  -> [DeloadState.UNKNOWN]
 *
 * `null` is the value of every session recorded before Room schema 13 and must stay
 * [DeloadState.UNKNOWN]. The current `AppPreferences.deloadMode` switch is never read
 * here: a later switch change must not reclassify a historical session.
 */
fun WorkoutSession.deloadState(): DeloadState = when (isDeload) {
    true -> DeloadState.PLANNED_DELOAD
    false -> DeloadState.NONE
    null -> DeloadState.UNKNOWN
}
