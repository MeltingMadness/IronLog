package com.ironlog.app.domain.model

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Aktive Deload-Strategie. `null` in [AppPreferences.deloadMode] bedeutet, dass
 * kein Deload aktiv ist.
 */
enum class DeloadMode {
    /** Satzzahl der Planziele wird halbiert (aufgerundet, mindestens 1). */
    HALVE_SET_VOLUME,

    /** Zielgewicht der Planziele wird um 15 % reduziert. */
    REDUCE_INTENSITY_BY_15_PERCENT
}

/** Signale, die zur Deload-Empfehlung beitragen. */
enum class DeloadSignal {
    /** Geschätztes 1RM der Verbundübungen stagniert über das Fenster. */
    E1RM_STAGNATION,

    /** Geschätztes 1RM der Verbundübungen fällt über das Fenster. */
    E1RM_DROP,

    /** Durchschnittliches RPE steigt von der früheren zur jüngeren Fensterhälfte. */
    RPE_CREEP,

    /** Anteil der Fehlversuche (FAILURE-Sätze) ist erhöht. */
    FAILURE_FREQUENCY
}

/** Eine Trainingseinheit, wie sie der [com.ironlog.app.domain.deload.DeloadDetector] erwartet. */
data class DeloadSessionInput(
    val id: Long,
    val startTime: LocalDateTime,
    val sets: List<WorkoutSet>
)

/**
 * Ergebnis der Deload-Analyse.
 *
 * [recommended] ist genau dann `true`, wenn mindestens ein Signal vorliegt und
 * der Ermüdungswert ([fatigueScore]) die Empfehlungsschwelle erreicht.
 */
data class DeloadAssessment(
    val recommended: Boolean,
    val fatigueScore: Int,
    val signals: List<DeloadSignal>,
    val windowStart: LocalDate,
    val windowEnd: LocalDate,
    val sessionCount: Int,
    /** Verbundübung mit dem schlechtesten E1RM-Trend im Fenster, falls analysiert. */
    val strongestExerciseId: Long? = null,
    /** Relative E1RM-Veränderung (in %) der stärksten Übung. */
    val strongestExerciseChangePercent: Double? = null,
    /** Durchschnittliches RPE über die Trainingseinheiten mit RPE-Werten. */
    val averageRpe: Double? = null,
    /** Anteil der FAILURE-Sätze an den Arbeitssätzen (NORMAL + FAILURE). */
    val failureRate: Double = 0.0,
    /** Anzahl der Verbundübungen mit genug wöchentlichen E1RM-Punkten. */
    val analyzedCompoundCount: Int = 0
)