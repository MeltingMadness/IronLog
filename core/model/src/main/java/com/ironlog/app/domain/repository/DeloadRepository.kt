package com.ironlog.app.domain.repository

import com.ironlog.app.domain.model.DeloadAssessment

/**
 * Liefert die Deload-Einschätzung auf Basis der letzten Wochen Training.
 *
 * Die Implementierung lädt die abgeschlossenen Einheiten des rollierenden
 * Analysefensters (3–4 Wochen) und wertet E1RM-Trends der Verbundübungen,
 * RPE-Entwicklung und Fehlversuchsquote mit dem DeloadDetector aus.
 */
interface DeloadRepository {
    suspend fun assess(): DeloadAssessment
}