package com.thrym.app.domain.repository

import com.thrym.app.domain.model.IncidentReport

interface IncidentReportRepository {
    suspend fun createIncidentReport(
        summary: String,
        details: String,
        currentScreen: String,
        includeDiagnostics: Boolean,
        throwable: Throwable? = null
    ): IncidentReport
}
