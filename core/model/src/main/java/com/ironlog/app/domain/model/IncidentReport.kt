package com.ironlog.app.domain.model

import android.net.Uri

data class IncidentReport(
    val id: String,
    val createdAtEpochMillis: Long,
    val fileName: String,
    val uri: Uri
)
