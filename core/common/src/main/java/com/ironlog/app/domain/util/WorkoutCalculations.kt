package com.ironlog.app.domain.util

/**
 * Formel zur Berechnung des geschätzten 1-Repetition-Maximums (1RM).
 */
enum class OneRepMaxFormula {
    /** Epley-Formel: Gewicht × (1 + Wiederholungen / 30). Liefert auch bei höheren Wiederholungszahlen stabile Werte. */
    EPLEY,

    /** Brzycki-Formel: Gewicht / (1,0278 − 0,0278 × Wiederholungen). Empfohlener Gültigkeitsbereich: 1–15 Wiederholungen. */
    BRZYCKI
}

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

    /**
     * Berechnet das geschätzte 1-Repetition-Maximum (1RM) nach der Brzycki-Formel.
     *
     * Formel: 1RM = Gewicht / (1,0278 − 0,0278 × Wiederholungen)
     *
     * Der empfohlene Gültigkeitsbereich liegt bei 1–15 Wiederholungen. Bei 1 oder weniger
     * Wiederholungen sowie ab 37 Wiederholungen (dort wird der Nenner ≤ 0 und die Formel
     * undefiniert) wird konservativ das Gewicht selbst zurückgegeben.
     *
     * @param weightKg Gewicht in Kilogramm
     * @param reps Anzahl der Wiederholungen
     * @return Geschätztes 1RM
     */
    fun calculateBrzycki1RM(weightKg: Double, reps: Int): Double {
        if (reps <= 1 || reps >= 37) return weightKg
        return weightKg / (1.0278 - 0.0278 * reps)
    }

    /**
     * Berechnet das geschätzte 1-Repetition-Maximum (1RM) mit der gewählten Formel.
     *
     * @param weightKg Gewicht in Kilogramm
     * @param reps Anzahl der Wiederholungen
     * @param formula Zu verwendende Formel (Epley oder Brzycki)
     * @return Geschätztes 1RM
     */
    fun calculate1RM(weightKg: Double, reps: Int, formula: OneRepMaxFormula): Double = when (formula) {
        OneRepMaxFormula.EPLEY -> calculateE1RM(weightKg, reps)
        OneRepMaxFormula.BRZYCKI -> calculateBrzycki1RM(weightKg, reps)
    }
}
