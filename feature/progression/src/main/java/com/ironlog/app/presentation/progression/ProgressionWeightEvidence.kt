package com.ironlog.app.presentation.progression

import com.ironlog.app.domain.model.SetType
import com.ironlog.app.domain.model.WorkoutSet
import kotlin.math.abs

/** A real zero is valid (e.g. no added weight); absent/mixed evidence is unknown. */
fun uniformCountedWeightKg(sets: List<WorkoutSet>): Double? {
    val first = sets.firstOrNull()?.weightKg ?: return null
    if (!first.isFinite() || first < 0.0) return null
    return first.takeIf {
        sets.all { set ->
            set.setType == SetType.NORMAL && set.weightKg.isFinite() &&
                set.weightKg >= 0.0 && abs(set.weightKg - first) <= 0.1
        }
    }
}

/** Older results compared a uniform trained load against a plan default. */
fun isHistoricalPlanWeightDeviation(
    sets: List<WorkoutSet>,
    plannedWeightKg: Double,
    expectedWeightKg: Double?,
    actualWeightKg: Double?
): Boolean {
    val trained = uniformCountedWeightKg(sets) ?: return false
    val expected = expectedWeightKg?.takeIf { it.isFinite() && it >= 0.0 } ?: return false
    val actual = actualWeightKg?.takeIf { it.isFinite() && it >= 0.0 } ?: return false
    return plannedWeightKg.isFinite() && abs(plannedWeightKg - expected) <= 0.1 &&
        abs(trained - actual) <= 0.1 && abs(trained - expected) > 0.1
}
