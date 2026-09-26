package com.ironlog.app.presentation.workout

import com.ironlog.app.domain.model.DeloadMode
import com.ironlog.app.domain.model.ProgressionConfig
import com.ironlog.app.domain.model.SetType
import com.ironlog.app.domain.model.WorkoutPlanTarget
import com.ironlog.app.domain.model.WorkoutSet
import com.ironlog.app.domain.util.RpeAutoregulation
import kotlinx.coroutines.flow.map

private val submissionIdSequence = java.util.concurrent.atomic.AtomicLong(0L)

internal fun nextSubmissionId(): Long = submissionIdSequence.incrementAndGet()

/**
 * Normalizes user-typed decimal input (weight/intensity) so that comma decimal
 * separators (common on non-US keyboards) are parsed the same as dots.
 */
fun parseDecimal(text: String): Double? = text.trim().replace(",", ".").toDoubleOrNull()

fun lastWorkSetReachedTarget(
    planTarget: WorkoutPlanTarget?,
    previousSets: List<WorkoutSet>
): Boolean {
    val target = planTarget ?: return false
    if (target.target.reps <= 0 || target.target.weightKg <= 0.0) return false
    val lastWorkSet = previousSets.lastOrNull { !it.isWarmup } ?: return false
    return lastWorkSet.reps >= target.target.reps &&
        lastWorkSet.weightKg >= target.target.weightKg
}

/**
 * Passt ein Planziel an den aktiven Deload-Modus an (nur Anzeigeebene — die
 * gespeicherten Planziele und die Progressions-Auswertung bleiben unverändert).
 * Ohne aktiven Modus wird das Ziel unverändert zurückgegeben.
 */
internal fun applyDeloadToTarget(
    target: WorkoutPlanTarget,
    deloadMode: DeloadMode?
): WorkoutPlanTarget = when (deloadMode) {
    null -> target
    DeloadMode.HALVE_SET_VOLUME -> target.copy(
        target = target.target.copy(
            sets = maxOf(1, (target.target.sets + 1) / 2)
        ),
        setTargets = target.setTargets.let { slots ->
            var work = 0
            slots.filter { it.kind == "WARMUP" || ++work <= maxOf(1, (target.target.sets + 1) / 2) }
        }
    )
    DeloadMode.REDUCE_INTENSITY_BY_15_PERCENT -> target.copy(
        target = target.target.copy(
            weightKg = roundToOneDecimal(target.target.weightKg * 0.85)
        ),
        setTargets = target.setTargets.map { it.copy(weightKg = roundToOneDecimal(it.weightKg * 0.85)) }
    )
}

internal fun roundToOneDecimal(value: Double): Double =
    kotlin.math.round(value * 10.0) / 10.0

/**
 * Returns whether logging [newSetType] completes the planned slots for one exact row.
 * Planned slots are fulfilled by NORMAL sets only; warmups, drop sets and failure sets remain
 * useful workout evidence but must not make a planned exercise appear complete. The active
 * deload mode changes the effective set target used for this decision.
 */
internal fun isPlannedExerciseComplete(
    planTarget: WorkoutPlanTarget?,
    deloadMode: DeloadMode?,
    previousSets: List<WorkoutSet>,
    newSetType: SetType
): Boolean {
    val effectiveTarget = planTarget?.let { applyDeloadToTarget(it, deloadMode) } ?: return false
    val targetSetCount = effectiveTarget.target.sets
    if (targetSetCount <= 0) return false

    val normalSetCount = previousSets.count { it.setType == SetType.NORMAL } +
        if (newSetType == SetType.NORMAL) 1 else 0
    return normalSetCount >= targetSetCount
}

internal fun configuredBackoffPercent(config: ProgressionConfig?): Double = when (config) {
    is ProgressionConfig.Linear -> config.failurePolicy.backoffPercent
    is ProgressionConfig.DoubleProgression -> config.failurePolicy.backoffPercent
    is ProgressionConfig.TotalReps -> config.failurePolicy.backoffPercent
    is ProgressionConfig.RpeRir -> config.failurePolicy.backoffPercent
    null,
    is ProgressionConfig.Manual,
    is ProgressionConfig.Invalid -> RpeAutoregulation.DEFAULT_BACKOFF_PERCENT
}
