package com.ironlog.app.domain.util

/**
 * Zentralisierte Trainings-Berechnungen.
 */
object WorkoutCalculations {

    /**
     * Berechnet den geschätzten 1-Repetition-Maximum (E1RM) nach der Epley-Formel.
     *
     * Formel: E1RM = Gewicht × (1 + Wiederholungen / 30)
     *
     * @param weightKg Gewicht in Kilogramm
     * @param reps Anzahl der Wiederholungen (muss > 1 sein für sinnvolles Ergebnis)
     * @return Geschätztes 1RM, oder das Gewicht selbst bei 1 oder weniger Wiederholungen
     */
    fun calculateE1RM(weightKg: Double, reps: Int): Double {
        if (reps <= 1) return weightKg
        return weightKg * (1 + reps / 30.0)
    }
}
