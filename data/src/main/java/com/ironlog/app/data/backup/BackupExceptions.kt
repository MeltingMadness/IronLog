package com.ironlog.app.data.backup

import java.io.IOException

class BackupHashMismatchException(
    val expectedSha256: String,
    val actualSha256: String
) : IOException(
    "Backup source hash changed while importing: expected $expectedSha256, got $actualSha256"
)

class BackupConcurrentModificationException : IllegalStateException(
    "Database changed while preparing import; no rows were deleted"
)
