package com.ironlog.shared.workout

import com.ironlog.shared.backup.NORMAL_SET_TYPE
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.floor

/**
 * Platform-neutral representation of one already persisted workout set.
 *
 * Workout persistence stores RPE as the canonical 1..10 value, regardless of whether the
 * athlete entered RPE or RIR in the UI. [intensityToRpe] below is the one shared conversion for
 * new UI input; this DTO deliberately keeps the stored value named [rpe].
 */
@Serializable
data class NextSetCoachSet(
    val id: Long,
    val setNumber: Int,
    val weightKg: Double,
    val rpe: Double? = null,
    val setType: String = NORMAL_SET_TYPE,
    val completedAtEpochMillis: Long = 0L,
    /** Deterministic tie-breaker for records with the same set number and timestamp. */
    val orderingToken: Long = 0L,
)

/**
 * Shared result rendered by both workout clients.
 *
 * [recommendedWeightKg] is the next normal-set load. [backoffWeightKg] is populated only for a
 * high-RPE overshoot and represents a dedicated backoff set, while the normal next-set hint still
 * remains available in [recommendedWeightKg].
 */
@Serializable
data class NextSetCoachResult(
    val recommendedWeightKg: Double,
    val lastWeightKg: Double,
    val lastRpe: Double,
    val targetRpe: Double?,
    val isOvershoot: Boolean,
    val backoffWeightKg: Double?,
)

/**
 * Intra-workout autoregulation shared by Android and iOS.
 *
 * The rule is intentionally the same as Android's former RpeAutoregulation helper: a 2.5% load
 * adjustment per RPE point from the target, capped at 10% per set. Without a target RPE, only an
 * overshoot (RPE >= 9) yields a recommendation. Inputs that cannot produce a trustworthy coach
 * hint fail closed with null.
 */
object SharedNextSetCoach {
    /** Percent of load adjusted per one RPE point from the target. */
    const val PERCENT_PER_RPE = 2.5

    /** RPE at or above which a set counts as an overshoot without a target. */
    const val OVERSHOOT_RPE = 9.0

    /** Maximum one-step adjustment in either direction. */
    const val MAX_ADJUSTMENT_PERCENT = 10.0

    /** Default dedicated backoff-set reduction. */
    const val DEFAULT_BACKOFF_PERCENT = 10.0

    /**
     * Selects the latest normal set and computes its next-set recommendation.
     *
     * Sets are ordered by set number, completion timestamp, ordering token, and id. This is
     * deterministic for Android Room rows and for backup/iOS rows that share a set number.
     */
    fun evaluate(
        sets: List<NextSetCoachSet>,
        targetRpe: Double?,
        backoffPercent: Double = DEFAULT_BACKOFF_PERCENT,
    ): NextSetCoachResult? {
        val last = sets
            .asSequence()
            .filter { it.setType == NORMAL_SET_TYPE }
            .sortedWith(
                compareBy<NextSetCoachSet> { it.setNumber }
                    .thenBy { it.completedAtEpochMillis }
                    .thenBy { it.orderingToken }
                    .thenBy { it.id },
            )
            .lastOrNull()
            ?: return null
        val lastRpe = last.rpe ?: return null
        val recommended = recommendNextSetWeightKg(
            lastWeightKg = last.weightKg,
            lastRpe = lastRpe,
            targetRpe = targetRpe,
        ) ?: return null
        return NextSetCoachResult(
            recommendedWeightKg = recommended,
            lastWeightKg = last.weightKg,
            lastRpe = lastRpe,
            targetRpe = targetRpe,
            isOvershoot = lastRpe >= OVERSHOOT_RPE,
            backoffWeightKg = if (lastRpe >= OVERSHOOT_RPE) {
                backoffSetWeightKg(last.weightKg, backoffPercent)
            } else {
                null
            },
        )
    }

    /** Scalar calculation kept public for the Android compatibility adapter. */
    fun recommendNextSetWeightKg(
        lastWeightKg: Double,
        lastRpe: Double,
        targetRpe: Double?,
    ): Double? {
        if (!lastWeightKg.isFinite() || lastWeightKg <= 0.0) return null
        if (!lastRpe.isFinite() || lastRpe !in 1.0..10.0) return null

        val referenceRpe = targetRpe
            ?: if (lastRpe >= OVERSHOOT_RPE) OVERSHOOT_RPE else return null
        if (!referenceRpe.isFinite() || referenceRpe !in 1.0..10.0) return null
        val rpeDelta = referenceRpe - lastRpe
        val adjustmentPercent = (rpeDelta * PERCENT_PER_RPE)
            .coerceIn(-MAX_ADJUSTMENT_PERCENT, MAX_ADJUSTMENT_PERCENT)
        val recommended = lastWeightKg * (1.0 + adjustmentPercent / 100.0)
        return roundToOneDecimal(recommended.coerceAtLeast(0.0))
    }

    /** Calculates a dedicated backoff-set load after a hard overshoot. */
    fun backoffSetWeightKg(
        workingWeightKg: Double,
        backoffPercent: Double = DEFAULT_BACKOFF_PERCENT,
    ): Double? {
        if (!workingWeightKg.isFinite() || workingWeightKg <= 0.0) return null
        if (!backoffPercent.isFinite() || backoffPercent < 0.0 || backoffPercent > 50.0) return null

        val backoff = workingWeightKg * (1.0 - backoffPercent / 100.0)
        return roundToOneDecimal(backoff.coerceAtLeast(0.0))
    }

    /**
     * Converts an entered RPE/RIR value to the canonical stored RPE scale.
     * The result is null for OFF, unknown systems, or out-of-range input.
     */
    fun intensityToRpe(value: Double, intensitySystemName: String): Double? {
        if (!value.isFinite()) return null
        return when (intensitySystemName.uppercase()) {
            "RPE" -> value.takeIf { it in 1.0..10.0 }
            "RIR" -> value.takeIf { it in 0.0..9.0 }?.let { 10.0 - it }
            else -> null
        }
    }

    /** Converts a canonical stored RPE to the value shown on the selected RPE/RIR scale. */
    fun rpeToIntensity(value: Double, intensitySystemName: String): Double? {
        if (!value.isFinite() || value !in 1.0..10.0) return null
        return when (intensitySystemName.uppercase()) {
            "RPE" -> value
            "RIR" -> 10.0 - value
            else -> null
        }
    }

    private fun roundToOneDecimal(value: Double): Double = floor(value * 10.0 + 0.5) / 10.0
}

/** Swift-friendly façade with a stable typed result and optional JSON representation. */
object NextSetCoachFacade {
    private val json = Json { encodeDefaults = true }

    /** Convenience overload for callers using the standard ten-percent backoff. */
    fun evaluate(
        sets: List<NextSetCoachSet>,
        targetRpe: Double?,
    ): NextSetCoachResult? = evaluate(
        sets = sets,
        targetRpe = targetRpe,
        backoffPercent = SharedNextSetCoach.DEFAULT_BACKOFF_PERCENT,
    )

    fun evaluate(
        sets: List<NextSetCoachSet>,
        targetRpe: Double?,
        backoffPercent: Double,
    ): NextSetCoachResult? = SharedNextSetCoach.evaluate(
        sets = sets,
        targetRpe = targetRpe,
        backoffPercent = backoffPercent,
    )

    /** Variant for UI callers that have a target value on the visible RPE or RIR scale. */
    fun evaluateWithIntensitySystem(
        sets: List<NextSetCoachSet>,
        targetIntensity: Double?,
        intensitySystemName: String,
        backoffPercent: Double,
    ): NextSetCoachResult? = evaluate(
        sets = sets,
        targetRpe = targetIntensity?.let {
            SharedNextSetCoach.intensityToRpe(it, intensitySystemName)
        },
        backoffPercent = backoffPercent,
    )

    fun evaluateJson(
        sets: List<NextSetCoachSet>,
        targetRpe: Double?,
        backoffPercent: Double,
    ): String = json.encodeToString(
        SharedNextSetCoach.evaluate(
            sets = sets,
            targetRpe = targetRpe,
            backoffPercent = backoffPercent,
        ),
    )

    fun intensityToRpe(value: Double, intensitySystemName: String): Double? =
        SharedNextSetCoach.intensityToRpe(value, intensitySystemName)

    fun rpeToIntensity(value: Double, intensitySystemName: String): Double? =
        SharedNextSetCoach.rpeToIntensity(value, intensitySystemName)
}
