package com.ironlog.app.domain.repository

import com.ironlog.app.domain.model.IncidentReport

interface IncidentReportRepository {
    suspend fun createIncidentReport(
        summary: String,
        details: String,
        currentScreen: String,
        includeDiagnostics: Boolean,
        throwable: Throwable? = null
    ): IncidentReport
}
