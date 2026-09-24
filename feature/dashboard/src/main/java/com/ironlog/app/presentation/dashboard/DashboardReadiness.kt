package com.ironlog.app.presentation.dashboard

import com.ironlog.app.domain.model.MuscleGroup
import com.ironlog.feature.dashboard.R
import com.ironlog.shared.readiness.EvidenceConfidence
import com.ironlog.shared.readiness.ExerciseTrendStatus
import com.ironlog.shared.readiness.ReadinessReasonCode
import com.ironlog.shared.readiness.TrainingTrendStatus
import java.time.LocalDate

/**
 * Persisted answers for exactly one calendar day.
 *
 * Nothing in here is a draft: this is what the store returned. Every dimension
 * stays `null` when the athlete did not answer it, and an absent soreness key
 * means "not reported", never "reported as low".
 */
data class DashboardStoredCheckIn(
    val date: LocalDate,
    val sleepQuality: Int? = null,
    val energy: Int? = null,
    val stress: Int? = null,
    val sorenessByMuscle: Map<MuscleGroup, Int> = emptyMap(),
    /** First-recorded timestamp, preserved across edits. */
    val recordedAtEpochMillis: Long? = null,
) {
    val hasAnyAnswer: Boolean
        get() = sleepQuality != null || energy != null || stress != null || sorenessByMuscle.isNotEmpty()
}

/**
 * Editable draft for the daily check-in.
 *
 * The draft is never rendered as stored data; it becomes visible state only
 * through an explicit save. Cancelling an edit restores [DashboardCheckInState.stored].
 */
data class DashboardCheckInDraft(
    val sleepQuality: Int? = null,
    val energy: Int? = null,
    val stress: Int? = null,
    val sorenessByMuscle: Map<MuscleGroup, Int> = emptyMap(),
) {
    val hasAnyAnswer: Boolean
        get() = sleepQuality != null || energy != null || stress != null || sorenessByMuscle.isNotEmpty()

    companion object {
        /** Seeds a draft from the stored day so an edit starts from the saved values. */
        fun from(stored: DashboardStoredCheckIn?): DashboardCheckInDraft = DashboardCheckInDraft(
            sleepQuality = stored?.sleepQuality,
            energy = stored?.energy,
            stress = stored?.stress,
            sorenessByMuscle = stored?.sorenessByMuscle.orEmpty()
        )
    }
}

/**
 * What the dashboard shows for the optional daily check-in.
 *
 * Stored answers and the draft are kept apart on purpose: an abandoned edit
 * must never look like saved data. [draftDate] records the day the draft
 * belongs to, so a draft created before midnight is never silently written
 * under the next day's date.
 */
data class DashboardCheckInState(
    /** Calendar day this state describes. */
    val today: LocalDate? = null,
    val stored: DashboardStoredCheckIn? = null,
    val draft: DashboardCheckInDraft = DashboardCheckInDraft(),
    /** Day the draft belongs to; `null` while not editing. */
    val draftDate: LocalDate? = null,
    val isEditing: Boolean = false,
    val isSaving: Boolean = false,
)

/** One-shot confirmation shown after a check-in write; never a data value. */
enum class DashboardCheckInNotice { SAVED, DELETED, NEEDS_ANSWER }

/**
 * Localized text for the trend and daily-form surfaces.
 *
 * The shared core emits machine-readable codes only; the German wording lives
 * here so the reason list stays auditable and no prose leaks into `:shared`.
 */
object DashboardReadinessText {

    fun trendStatusLabel(status: TrainingTrendStatus): Int = when (status) {
        TrainingTrendStatus.INSUFFICIENT_DATA -> R.string.dashboard_trend_status_insufficient
        TrainingTrendStatus.NO_NOTABLE_STRAIN -> R.string.dashboard_trend_status_no_strain
        TrainingTrendStatus.SINGLE_EXERCISE_DECLINE -> R.string.dashboard_trend_status_single
        TrainingTrendStatus.MULTIPLE_EXERCISE_DECLINE -> R.string.dashboard_trend_status_multiple
    }

    fun confidenceLabel(confidence: EvidenceConfidence): Int = when (confidence) {
        EvidenceConfidence.NONE -> R.string.dashboard_trend_confidence_none
        EvidenceConfidence.LOW -> R.string.dashboard_trend_confidence_low
        EvidenceConfidence.MODERATE -> R.string.dashboard_trend_confidence_moderate
        EvidenceConfidence.HIGH -> R.string.dashboard_trend_confidence_high
    }

    fun exerciseStatusLabel(status: ExerciseTrendStatus): Int = when (status) {
        ExerciseTrendStatus.IMPROVING -> R.string.dashboard_trend_ex_status_improving
        ExerciseTrendStatus.STABLE -> R.string.dashboard_trend_ex_status_stable
        ExerciseTrendStatus.DECLINING -> R.string.dashboard_trend_ex_status_declining
        ExerciseTrendStatus.INSUFFICIENT_DATA -> R.string.dashboard_trend_ex_status_insufficient
        ExerciseTrendStatus.EXCLUDED -> R.string.dashboard_trend_ex_status_excluded
    }

    /**
     * German sentence for a reason code.
     *
     * Per-exercise reset codes stay on the generic fallback on purpose: they
     * explain a neutral series break, not a finding about today's training, and
     * the aggregate list would become noise if every one of them were listed.
     */
    fun reasonLabel(code: ReadinessReasonCode): Int = when (code) {
        ReadinessReasonCode.INSUFFICIENT_HISTORY -> R.string.dashboard_reason_insufficient_history
        ReadinessReasonCode.NO_NOTABLE_STRAIN -> R.string.dashboard_reason_no_notable_strain
        ReadinessReasonCode.SINGLE_EXERCISE_DECLINE -> R.string.dashboard_reason_single_decline
        ReadinessReasonCode.MULTIPLE_EXERCISE_DECLINES -> R.string.dashboard_reason_multiple_declines
        ReadinessReasonCode.DELOAD_IN_PROGRESS -> R.string.dashboard_reason_deload_in_progress
        ReadinessReasonCode.DELOAD_SUGGESTED -> R.string.dashboard_reason_deload_suggested
        ReadinessReasonCode.STAGNATION_NEUTRAL -> R.string.dashboard_reason_stagnation_neutral
        ReadinessReasonCode.PLANNED_FAILURE_NEUTRAL -> R.string.dashboard_reason_planned_failure_neutral
        ReadinessReasonCode.DELOAD_CONTEXT_NEUTRAL -> R.string.dashboard_reason_deload_context_neutral
        ReadinessReasonCode.MISSING_RPE_NEUTRAL -> R.string.dashboard_reason_missing_rpe_neutral
        ReadinessReasonCode.UNKNOWN_INTENTION_PRESENT -> R.string.dashboard_reason_unknown_intention
        ReadinessReasonCode.UNEXPECTED_TARGET_MISS_PRESENT -> R.string.dashboard_reason_unexpected_target_miss

        ReadinessReasonCode.MUSCLE_TRAINED_TODAY -> R.string.dashboard_reason_muscle_trained_today
        ReadinessReasonCode.MUSCLE_HIGH_SORENESS -> R.string.dashboard_reason_muscle_high_soreness
        ReadinessReasonCode.MUSCLE_HIGH_RECENT_VOLUME -> R.string.dashboard_reason_muscle_high_volume
        ReadinessReasonCode.MUSCLE_LOW_RECENT_VOLUME -> R.string.dashboard_reason_muscle_low_volume
        ReadinessReasonCode.MUSCLE_NO_RECENT_LOAD -> R.string.dashboard_reason_muscle_no_load

        ReadinessReasonCode.SLEEP_BELOW_THRESHOLD -> R.string.dashboard_reason_sleep_below
        ReadinessReasonCode.ENERGY_BELOW_THRESHOLD -> R.string.dashboard_reason_energy_below
        ReadinessReasonCode.STRESS_ABOVE_THRESHOLD -> R.string.dashboard_reason_stress_above
        ReadinessReasonCode.SORENESS_ABOVE_THRESHOLD -> R.string.dashboard_reason_soreness_above
        ReadinessReasonCode.NO_CHECK_IN -> R.string.dashboard_reason_no_check_in
        ReadinessReasonCode.PARTIAL_CHECK_IN -> R.string.dashboard_reason_partial_check_in
        ReadinessReasonCode.CHECK_IN_ALL_NEUTRAL -> R.string.dashboard_reason_check_in_neutral

        else -> R.string.dashboard_reason_unknown
    }

    /**
     * Maps a portable muscle code from the shared core onto the app vocabulary.
     *
     * Unknown codes are dropped instead of guessed, so a row that the app cannot
     * name never appears as a mislabelled muscle group.
     */
    fun muscleGroupFor(code: String): MuscleGroup? =
        MuscleGroup.entries.firstOrNull { it.name == code.trim().uppercase() }
}
