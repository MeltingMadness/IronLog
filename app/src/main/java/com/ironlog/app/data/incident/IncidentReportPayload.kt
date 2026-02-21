package com.ironlog.app.data.incident

import kotlinx.serialization.Serializable

@Serializable
internal data class IncidentReportPayload(
    val incidentId: String,
    val createdAtEpochMillis: Long,
    val appVersionName: String,
    val appVersionCode: Int,
    val currentScreen: String,
    val summary: String,
    val details: String,
    val stacktrace: String? = null,
    val diagnostics: IncidentDiagnostics? = null
)

@Serializable
internal data class IncidentDiagnostics(
    val osVersion: String,
    val deviceModel: String,
    val manufacturer: String
)
