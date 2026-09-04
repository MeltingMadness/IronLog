package com.ironlog.app.domain.util

import kotlin.math.floor

/**
 * Intra-session RPE autoregulation: adapts the load of the next work set to
 * how hard the last work set actually felt, based on the delta between the
 * logged RPE and the RPE_RIR plan target's target RPE.
 *
 * Pure calculation logic without dependencies, so it can be unit-tested in
 * isolation and reused by the workout UI and future features alike. All
 * weights are kg and rounded to 0.1 kg; invalid inputs yield [null] instead
 * of a nonsense recommendation.
 */
object RpeAutoregulation {

    /** Percent of load adjusted per 1.0 RPE difference from the target (2.5%). */
    const val PERCENT_PER_RPE = 2.5

    /** RPE at or above which a set counts as an overshoot even without a target. */
    const val OVERSHOOT_RPE = 9.0

    /** One-step cap for the autoregulated adjustment (mirrors the failure backoff). */
    const val MAX_ADJUSTMENT_PERCENT = 10.0

    /** Default load drop for a dedicated backoff set. */
    const val DEFAULT_BACKOFF_PERCENT = 10.0

    /**
     * Recommends the load for the next work set.
     *
     * With an active [targetRpe] the recommendation tracks the delta between
     * the last set's RPE and the target: harder than the target lowers the
     * next load by ~2.5% per 1.0 RPE, easier raises it. Without a target a
     * recommendation is only produced for overshoots ([OVERSHOOT_RPE] and
     * above), referencing RPE [OVERSHOOT_RPE] as the implicit ceiling.
     * The one-step adjustment is capped at [MAX_ADJUSTMENT_PERCENT].
     *
     * @return next-set weight in kg rounded to 0.1 kg, or null when no useful
     * recommendation exists (unusable RPE/weight, or a non-overshoot set
     * without a target).
     */
    fun recommendNextSetWeightKg(
        lastWeightKg: Double,
        lastRpe: Double,
        targetRpe: Double?
    ): Double? {
        if (!lastWeightKg.isFinite() || lastWeightKg <= 0.0) return null
        if (!lastRpe.isFinite() || lastRpe !in 1.0..10.0) return null

        val referenceRpe = targetRpe
            ?: if (lastRpe >= OVERSHOOT_RPE) OVERSHOOT_RPE else return null
        val rpeDelta = referenceRpe - lastRpe
        val adjustmentPercent = (rpeDelta * PERCENT_PER_RPE)
            .coerceIn(-MAX_ADJUSTMENT_PERCENT, MAX_ADJUSTMENT_PERCENT)
        val recommended = lastWeightKg * (1.0 + adjustmentPercent / 100.0)
        return roundToOneDecimal(recommended.coerceAtLeast(0.0))
    }

    /**
     * Load for a dedicated backoff set (to be run after a hard overshoot set
     * instead of the working weight): a fixed-percent drop from the working
     * weight, floored at 0 kg.
     *
     * @return backoff weight in kg rounded to 0.1 kg, or null for unusable
     * inputs (non-positive weight, percent outside 0..50).
     */
    fun backoffSetWeightKg(
        workingWeightKg: Double,
        backoffPercent: Double = DEFAULT_BACKOFF_PERCENT
    ): Double? {
        if (!workingWeightKg.isFinite() || workingWeightKg <= 0.0) return null
        if (!backoffPercent.isFinite() || backoffPercent < 0.0 || backoffPercent > 50.0) return null

        val backoff = workingWeightKg * (1.0 - backoffPercent / 100.0)
        return roundToOneDecimal(backoff.coerceAtLeast(0.0))
    }

    // Halbe Zehntel (z. B. 99.25) runden bewusst kaufmaennisch auf, damit die
    // Empfehlung fuer den naechsten Satz nicht willkuerlich abgerundet wird.
    private fun roundToOneDecimal(value: Double): Double = floor(value * 10.0 + 0.5) / 10.0
}