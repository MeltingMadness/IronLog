package com.ironlog.shared.incident

import kotlinx.serialization.Serializable

@Serializable
data class IncidentReportPayload(
    val incidentId: String,
    val createdAtEpochMillis: Long,
    val appVersionName: String,
    val appVersionCode: Int,
    val currentScreen: String,
    val summary: String,
    val details: String,
    val stacktrace: String? = null,
    val diagnostics: IncidentDiagnostics? = null,
)

@Serializable
data class IncidentDiagnostics(
    val osVersion: String,
    val deviceModel: String,
    val manufacturer: String,
)
