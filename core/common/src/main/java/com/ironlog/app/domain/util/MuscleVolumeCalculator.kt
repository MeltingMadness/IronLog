package com.ironlog.app.domain.util

import com.ironlog.app.domain.model.Exercise
import com.ironlog.app.domain.model.MuscleGroup
import com.ironlog.app.domain.model.SetType
import com.ironlog.app.domain.model.WorkoutSet
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Bewertung des wöchentlichen Muskelvolumens.
 */
enum class VolumeStatus {
    /** Unterhalb des Minimum Effective Volume (MEV): zu wenig Reiz für Fortschritt. */
    LOW,

    /** Zwischen MEV und MRV: effektiver Trainingsbereich. */
    OPTIMAL,

    /** Oberhalb des Maximum Recoverable Volume (MRV): Erholung ist gefährdet. */
    HIGH
}

/**
 * Wöchentliche Volumen-Schwellenwerte einer Muskelgruppe in gewichteten Arbeitssätzen.
 *
 * MEV – Minimum Effective Volume: Mindestmenge, ab der Wachstumsreize entstehen.
 * MAV – Maintenance/Adaptive Volume: Zielbereich mit dem besten Fortschritt.
 * MRV – Maximum Recoverable Volume: Obergrenze, ab der die Erholung leidet.
 */
data class VolumeThresholds(
    val mev: Double,
    val mav: Double,
    val mrv: Double
) {
    init {
        require(mev >= 0.0 && mav >= mev && mrv >= mav) {
            "Schwellenwerte müssen MEV <= MAV <= MRV erfüllen (gegeben: $mev / $mav / $mrv)"
        }
    }
}

/**
 * Aggregiertes Wochenvolumen einer Muskelgruppe in gewichteten Arbeitssätzen,
 * bewertet gegen [VolumeThresholds].
 */
data class MuscleVolume(
    val muscleGroup: MuscleGroup,
    val weeklySets: Double,
    val thresholds: VolumeThresholds
) {
    /** Bewertung des Volumens gegen die Schwellenwerte der Muskelgruppe. */
    val status: VolumeStatus
        get() = MuscleVolumeCalculator.evaluateStatus(weeklySets, thresholds)

    /** Fortschrittsanteil Richtung [VolumeThresholds.mrv], gekappt auf 1.0. */
    val progress: Float
        get() = (weeklySets / thresholds.mrv).toFloat().coerceIn(0f, 1f)
}

/**
 * Aggregiert abgeschlossene Arbeitssätze einer Woche je Muskelgruppe (gewichtetes
 * Wochenvolumen) und bewertet sie gegen MEV/MAV/MRV-Schwellenwerte.
 *
 * Zählung: Jeder Arbeitssatz zählt 1,0 für die primäre Muskelgruppe der Übung und
 * 0,5 für jede sekundäre Muskelgruppe. Es zählen [SetType.NORMAL], [SetType.DROP_SET]
 * und [SetType.FAILURE]; Aufwärmsätze ([SetType.WARMUP]) werden ignoriert.
 */
object MuscleVolumeCalculator {

    /**
     * Standard-Schwellenwerte (gewichtete Sätze pro Woche), orientiert an gängigen
     * Hypertrophie-Empfehlungen mit MEV < MAV < MRV.
     */
    val DEFAULT_THRESHOLDS: Map<MuscleGroup, VolumeThresholds> = mapOf(
        // Große Muskelgruppen
        MuscleGroup.BRUST to VolumeThresholds(mev = 10.0, mav = 16.0, mrv = 22.0),
        MuscleGroup.RUECKEN to VolumeThresholds(mev = 10.0, mav = 16.0, mrv = 22.0),
        MuscleGroup.BEINE to VolumeThresholds(mev = 10.0, mav = 16.0, mrv = 22.0),
        // Mittlere Muskelgruppen
        MuscleGroup.SCHULTERN to VolumeThresholds(mev = 8.0, mav = 12.0, mrv = 18.0),
        MuscleGroup.GESAESS to VolumeThresholds(mev = 8.0, mav = 12.0, mrv = 18.0),
        // Kleine Muskelgruppen
        MuscleGroup.BIZEPS to VolumeThresholds(mev = 6.0, mav = 10.0, mrv = 16.0),
        MuscleGroup.TRIZEPS to VolumeThresholds(mev = 6.0, mav = 10.0, mrv = 16.0),
        MuscleGroup.WADEN to VolumeThresholds(mev = 6.0, mav = 10.0, mrv = 16.0),
        MuscleGroup.UNTERARME to VolumeThresholds(mev = 6.0, mav = 10.0, mrv = 16.0),
        MuscleGroup.CORE to VolumeThresholds(mev = 6.0, mav = 10.0, mrv = 16.0)
    )

    /** Zählt ein Satz für das Wochenvolumen? Nur [SetType.WARMUP] ist ausgenommen. */
    fun isCountedSet(setType: SetType): Boolean = setType != SetType.WARMUP

    /**
     * Aggregiert die gewichteten Arbeitssätze der Woche ab [weekStart] je Muskelgruppe.
     *
     * @param sets Arbeitssätze (idealerweise bereits auf abgeschlossene Sessions gefiltert)
     * @param exercises Übungen für die Muskelgruppen-Zuordnung
     * @param weekStart erster Tag der Woche (inklusiv), Ende ist exklusiv 7 Tage später
     * @param thresholds Schwellenwerte je Muskelgruppe; fehlende Gruppen fallen auf [DEFAULT_THRESHOLDS] zurück
     * @return Muskelgruppen mit Volumen > 0, absteigend nach Volumen sortiert
     */
    fun aggregateByMuscleGroup(
        sets: List<WorkoutSet>,
        exercises: List<Exercise>,
        weekStart: LocalDate,
        thresholds: Map<MuscleGroup, VolumeThresholds> = DEFAULT_THRESHOLDS
    ): List<MuscleVolume> {
        val exerciseById = exercises.associateBy { it.id }
        val weekEnd = weekStart.plusDays(7)
        val volume = mutableMapOf<MuscleGroup, Double>()

        for (set in sets) {
            if (!isCountedSet(set.setType)) continue
            val setDate = set.completedAt.toLocalDate()
            if (setDate.isBefore(weekStart) || !setDate.isBefore(weekEnd)) continue

            val exercise = exerciseById[set.exerciseId] ?: continue
            volume[exercise.primaryMuscleGroup] = (volume[exercise.primaryMuscleGroup] ?: 0.0) + 1.0
            for (secondary in exercise.secondaryMuscleGroups) {
                volume[secondary] = (volume[secondary] ?: 0.0) + 0.5
            }
        }

        return volume.map { (group, setsCount) ->
            MuscleVolume(
                muscleGroup = group,
                weeklySets = setsCount,
                thresholds = thresholds[group] ?: DEFAULT_THRESHOLDS.getValue(group)
            )
        }.sortedByDescending { it.weeklySets }
    }

    /**
     * Bewertet ein Volumen gegen die Schwellenwerte:
     * LOW unter MEV, OPTIMAL von MEV bis MRV (inklusive), HIGH über MRV.
     */
    fun evaluateStatus(volume: Double, thresholds: VolumeThresholds): VolumeStatus = when {
        volume < thresholds.mev -> VolumeStatus.LOW
        volume > thresholds.mrv -> VolumeStatus.HIGH
        else -> VolumeStatus.OPTIMAL
    }

    /** Start der Woche (erster Tag [firstDayOfWeek]) für [date], inklusive [date] selbst. */
    fun weekStartFor(date: LocalDate, firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
}