package com.ironlog.app.data.repository

import android.os.Build
import androidx.core.content.FileProvider
import com.ironlog.app.domain.util.BuildInfo
import com.ironlog.app.data.incident.IncidentDiagnostics
import com.ironlog.app.data.incident.IncidentReportPayload
import com.ironlog.app.data.incident.IncidentReportSanitizer
import com.ironlog.app.domain.model.IncidentReport
import com.ironlog.app.domain.repository.IncidentReportRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

class IncidentReportRepositoryImpl(
    private val context: android.content.Context,
    private val buildInfo: BuildInfo
) : IncidentReportRepository {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    override suspend fun createIncidentReport(
        summary: String,
        details: String,
        currentScreen: String,
        includeDiagnostics: Boolean,
        throwable: Throwable?
    ): IncidentReport {
        val incidentId = UUID.randomUUID().toString().substring(0, 8)
        val createdAt = System.currentTimeMillis()
        val sanitizedSummary = IncidentReportSanitizer.sanitizeText(summary.trim())
        val sanitizedDetails = IncidentReportSanitizer.sanitizeText(details.trim())
        val sanitizedStacktrace = throwable
            ?.stackTraceToString()
            ?.let(IncidentReportSanitizer::sanitizeText)

        val payload = IncidentReportPayload(
            incidentId = incidentId,
            createdAtEpochMillis = createdAt,
            appVersionName = buildInfo.versionName,
            appVersionCode = buildInfo.versionCode,
            currentScreen = currentScreen,
            summary = sanitizedSummary,
            details = sanitizedDetails,
            stacktrace = sanitizedStacktrace,
            diagnostics = if (includeDiagnostics) {
                IncidentDiagnostics(
                    osVersion = Build.VERSION.RELEASE ?: "unknown",
                    deviceModel = Build.MODEL ?: "unknown",
                    manufacturer = Build.MANUFACTURER ?: "unknown"
                )
            } else {
                null
            }
        )

        val incidentDir = File(context.cacheDir, "incidents")
        if (!incidentDir.exists()) {
            incidentDir.mkdirs()
        }

        val fileName = "incident-$incidentId.json"
        val incidentFile = File(incidentDir, fileName)
        incidentFile.writeText(json.encodeToString(payload))

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            incidentFile
        )

        return IncidentReport(
            id = incidentId,
            createdAtEpochMillis = createdAt,
            fileName = fileName,
            uri = uri
        )
    }
}
