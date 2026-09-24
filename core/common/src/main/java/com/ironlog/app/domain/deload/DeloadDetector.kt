package com.ironlog.app.domain.deload

import com.ironlog.app.domain.model.DeloadAssessment
import com.ironlog.app.domain.model.DeloadSessionInput
import com.ironlog.app.domain.model.DeloadSignal
import com.ironlog.app.domain.model.Exercise
import com.ironlog.app.domain.model.ExerciseCategory
import com.ironlog.app.domain.model.SetType
import com.ironlog.app.domain.model.WorkoutSet
import com.ironlog.app.domain.util.WorkoutCalculations
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

/**
 * Verbundübungen sind im Seed-Katalog die Langhantel-Übungen
 * (Kniebeuge, Kreuzheben, Bankdrücken, Überkopfdrücken, Rudern …).
 * Maschinen-, Kabel- und Kurzhantelübungen zählen als Isolationsarbeit.
 */
fun Exercise.isCompoundExercise(): Boolean = category == ExerciseCategory.LANGHANTEL

/**
 * Erkennt Überlastung über ein rollierendes Fenster (Standard: 4 Wochen) aus:
 *
 * 1. **E1RM-Trend der Verbundübungen**: wöchentliches Best-E1RM (NORMAL-Sätze,
 *    Epley-Formel), Vergleich der früheren mit der jüngeren Fensterhälfte.
 *    Stagnation (< +1 % Wachstum) und Abfall (≤ −2,5 %) sind Signale.
 * 2. **RPE-Creep**: mittleres RPE pro Trainingseinheit; steigt der Schnitt der
 *    jüngeren Hälfte um ≥ 0,5 über die frühere Hälfte, ist das ein Signal.
 * 3. **Fehlversuchsquote**: Anteil der FAILURE-Sätze an den Arbeitssätzen
 *    (NORMAL + FAILURE); ≥ 15 % ist ein Signal (linear skaliert).
 *
 * Der Ermüdungswert (0–100) summiert die gewichteten Signale:
 * E1RM-Abfall 60, E1RM-Stagnation 25, RPE-Creep 25, Fehlversuche bis 25.
 * Ab 60 Punkten wird eine Deload empfohlen. Ohne ausreichende Daten (weniger
 * als [Config.minSessions] Einheiten) wird nie empfohlen.
 */
class DeloadDetector(val config: Config = Config()) {

    data class Config(
        /** Länge des rollierenden Analysefensters in Wochen. */
        val windowWeeks: Int = 4,
        /** Relatives E1RM-Wachstum (%), unter dem der Trend als Stagnation gilt. */
        val stagnationMaxGrowthPercent: Double = 1.0,
        /** Relativer E1RM-Abfall (%), ab dem der Trend als Abfall gilt. */
        val dropThresholdPercent: Double = -2.5,
        /** RPE-Anstieg der jüngeren vs. frühere Fensterhälfte, ab dem RPE-Creep gilt. */
        val rpeCreepThreshold: Double = 0.5,
        /** Fehlversuchsquote, ab der das Signal voll angerechnet wird. */
        val failureRateThreshold: Double = 0.15,
        /** Mindestanzahl Trainingseinheiten im Fenster für eine Empfehlung. */
        val minSessions: Int = 3,
        /** Mindestanzahl Wochen mit E1RM-Daten pro Verbundübung für die Trendanalyse. */
        val minWeeklyE1rmPoints: Int = 2,
        /** Ermüdungswert, ab dem eine Deload empfohlen wird. */
        val recommendThreshold: Int = 60
    ) {
        init {
            require(windowWeeks >= 2) { "windowWeeks must be >= 2" }
            require(minSessions >= 1) { "minSessions must be >= 1" }
        }
    }

    private companion object {
        const val SCORE_E1RM_DROP = 60
        const val SCORE_E1RM_STAGNATION = 25
        const val SCORE_RPE_CREEP = 25
        const val SCORE_FAILURE_RATE = 25
    }

    /**
     * Wertet [sessions] (muss bereits auf das Analysefenster gefiltert sein)
     * aus. [compoundExerciseIds] begrenzt die E1RM-Trendanalyse auf
     * Verbundübungen; [today] bestimmt das Fensterende für die Ausgabe.
     */
    fun assess(
        sessions: List<DeloadSessionInput>,
        compoundExerciseIds: Set<Long>,
        today: LocalDate = LocalDate.now()
    ): DeloadAssessment {
        val windowEnd = today
        val windowStart = today.minusWeeks(config.windowWeeks.toLong())

        val trainingSessions = sessions
            .filter { it.sets.isNotEmpty() }
            .sortedBy { it.startTime }
        val sessionCount = trainingSessions.size
        if (sessionCount < config.minSessions) {
            return emptyAssessment(windowStart, windowEnd, sessionCount)
        }

        val signals = mutableListOf<DeloadSignal>()
        var score = 0

        // 1) E1RM-Trend der Verbundübungen: schlechteste Übung bestimmt das Signal.
        val compoundTrends = compoundE1rmTrends(trainingSessions, compoundExerciseIds)
        if (compoundTrends.isNotEmpty()) {
            val worst = compoundTrends.minByOrNull { it.changePercent }!!
            when {
                worst.changePercent <= config.dropThresholdPercent -> {
                    signals += DeloadSignal.E1RM_DROP
                    score += SCORE_E1RM_DROP
                }
                worst.changePercent <= config.stagnationMaxGrowthPercent -> {
                    signals += DeloadSignal.E1RM_STAGNATION
                    score += SCORE_E1RM_STAGNATION
                }
            }
        }

        // 2) RPE-Creep zwischen früherer und jüngerer Fensterhälfte.
        val rpeStats = rpeCreep(trainingSessions)
        if (rpeStats.creep >= config.rpeCreepThreshold) {
            signals += DeloadSignal.RPE_CREEP
            score += SCORE_RPE_CREEP
        }

        // 3) Fehlversuchsquote über die Arbeitssätze aller Einheiten.
        val failureRate = failureRate(trainingSessions)
        if (failureRate >= config.failureRateThreshold) {
            signals += DeloadSignal.FAILURE_FREQUENCY
            score += (SCORE_FAILURE_RATE * (failureRate / config.failureRateThreshold)
                .coerceAtMost(1.0)).toInt()
        }

        val fatigueScore = score.toInt().coerceIn(0, 100)
        val recommended = signals.isNotEmpty() && fatigueScore >= config.recommendThreshold

        val strongest = compoundTrends.minByOrNull { it.changePercent }
        return DeloadAssessment(
            recommended = recommended,
            fatigueScore = fatigueScore,
            signals = signals,
            windowStart = windowStart,
            windowEnd = windowEnd,
            sessionCount = sessionCount,
            strongestExerciseId = strongest?.exerciseId,
            strongestExerciseChangePercent = strongest?.changePercent,
            averageRpe = if (rpeStats.rpeSetCount > 0) {
                round1(rpeStats.averageRpe / rpeStats.rpeSetCount)
            } else {
                null
            },
            failureRate = failureRate,
            analyzedCompoundCount = compoundTrends.size,
            analysisWindowWeeks = config.windowWeeks,
            minimumSessionCount = config.minSessions
        )
    }

    private fun emptyAssessment(
        windowStart: LocalDate,
        windowEnd: LocalDate,
        sessionCount: Int
    ) = DeloadAssessment(
        recommended = false,
        fatigueScore = 0,
        signals = emptyList(),
        windowStart = windowStart,
        windowEnd = windowEnd,
        sessionCount = sessionCount,
        analysisWindowWeeks = config.windowWeeks,
        minimumSessionCount = config.minSessions
    )

    /** Wöchentliches Best-E1RM pro Verbundübung und relativer Trend der Fensterhälften. */
    private fun compoundE1rmTrends(
        sessions: List<DeloadSessionInput>,
        compoundExerciseIds: Set<Long>
    ): List<CompoundTrend> {
        // (exerciseId, Wochenanker-Montag) -> bestes E1RM in dieser Woche
        val weeklyBest = mutableMapOf<Pair<Long, LocalDate>, Double>()
        for (session in sessions) {
            for (set in session.sets) {
                if (set.exerciseId !in compoundExerciseIds) continue
                if (set.setType != SetType.NORMAL) continue
                if (set.reps < 1 || !set.weightKg.isFinite() || set.weightKg <= 0.0) continue
                val weekAnchor = set.completedAt.toLocalDate()
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val key = set.exerciseId to weekAnchor
                val e1rm = WorkoutCalculations.calculateE1RM(set.weightKg, set.reps)
                weeklyBest[key] = maxOf(weeklyBest[key] ?: 0.0, e1rm)
            }
        }

        return compoundExerciseIds.mapNotNull { exerciseId ->
            val weeks = weeklyBest
                .filterKeys { it.first == exerciseId }
                .toSortedMap(compareBy<Pair<Long, LocalDate>> { it.second })
                .values
                .toList()
            if (weeks.size < config.minWeeklyE1rmPoints) return@mapNotNull null

            val half = weeks.size / 2
            val earlier = weeks.subList(0, half)
            val later = weeks.subList(half, weeks.size)
            val earlierAvg = earlier.average()
            val laterAvg = later.average()
            if (earlierAvg <= 0.0) return@mapNotNull null
            val changePercent = (laterAvg - earlierAvg) / earlierAvg * 100.0
            CompoundTrend(exerciseId, changePercent)
        }
    }

    /**
     * RPE-Creep: Durchschnitt des mittleren Session-RPE der jüngeren Hälfte
     * minus der früheren Hälfte. Nur Einheiten mit mindestens einem
     * RPE-Wert (NORMAL-Sätze) zählen.
     */
    private fun rpeCreep(sessions: List<DeloadSessionInput>): RpeStats {
        val sessionAverages = sessions.mapNotNull { session ->
            val rpes = session.sets
                .filter { it.setType == SetType.NORMAL && it.rpe != null }
                .mapNotNull { it.rpe }
            if (rpes.isEmpty()) return@mapNotNull null
            rpes.average()
        }
        if (sessionAverages.size < 2) return RpeStats(creep = 0.0)

        val half = sessionAverages.size / 2
        val earlier = sessionAverages.subList(0, half)
        val later = sessionAverages.subList(half, sessionAverages.size)
        return RpeStats(
            creep = later.average() - earlier.average(),
            averageRpe = sessionAverages.sum(),
            rpeSetCount = sessionAverages.size
        )
    }

    private fun failureRate(sessions: List<DeloadSessionInput>): Double {
        var workSets = 0
        var failures = 0
        for (session in sessions) {
            for (set in session.sets) {
                when (set.setType) {
                    SetType.NORMAL -> workSets += 1
                    SetType.FAILURE -> {
                        workSets += 1
                        failures += 1
                    }
                    SetType.WARMUP, SetType.DROP_SET -> Unit
                }
            }
        }
        if (workSets == 0) return 0.0
        return failures.toDouble() / workSets
    }

    private fun round1(value: Double): Double = kotlin.math.round(value * 10.0) / 10.0

    private data class CompoundTrend(
        val exerciseId: Long,
        val changePercent: Double
    )

    private data class RpeStats(
        val creep: Double,
        val averageRpe: Double = 0.0,
        val rpeSetCount: Int = 0
    )
}

private fun List<Double>.average(): Double = if (isEmpty()) 0.0 else sum() / size
