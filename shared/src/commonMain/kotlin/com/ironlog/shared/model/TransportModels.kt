package com.ironlog.shared.model

data class BackupBlob(
    val bytes: ByteArray,
    val fileName: String,
    val mimeType: String
)

data class IncidentAttachment(
    val bytes: ByteArray,
    val fileName: String,
    val mimeType: String
)

data class CursorPageRequest(
    val limit: Int,
    val cursor: String? = null
)

data class CursorPage<T>(
    val items: List<T>,
    val nextCursor: String? = null
)
