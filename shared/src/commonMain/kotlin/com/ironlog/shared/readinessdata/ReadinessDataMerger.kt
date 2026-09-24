package com.ironlog.shared.readinessdata

/** Which part of the document a reported import conflict belongs to. */
enum class ReadinessMergeConflictKind {
    CHECK_IN,
    SET_INTENTION,
}

/** One conflict found while merging an imported document into the local one. */
data class ReadinessMergeConflict(
    val kind: ReadinessMergeConflictKind,
    val key: String,
    val localValue: String,
    val importedValue: String,
    val resolvedValue: String,
)

/** Merged document plus every conflict that was surfaced instead of hidden. */
data class ReadinessMergeResult(
    val data: ReadinessData,
    val conflicts: List<ReadinessMergeConflict>,
)

/**
 * Deterministic, data-preserving import.
 *
 * Check-ins are keyed by [ReadinessCheckIn.localDate]; set intentions by
 * [SetIntentionRecord.setId]. Values only present on one side are copied over.
 *
 * Both inputs are validated *before* anything is merged. A document with duplicate keys or
 * out-of-range values is refused with [ReadinessDataValidationException], because merging by key
 * would otherwise drop colliding entries silently.
 *
 * For set intentions an entirely blank [SetIntention.UNKNOWN] record carries no information and
 * loses against a known value in either direction (that is a pure information upgrade, not a
 * conflict). Every other difference - including a differing note or timestamp on otherwise equal
 * intentions - is reported in [ReadinessMergeResult.conflicts], so no note or metadata disappears
 * unnoticed. The local record is kept unless [importedWins] is true.
 *
 * Check-ins compare for full equality, so a differing timestamp alone is already a conflict.
 *
 * **Scope warning**: set intentions are keyed by the raw `setId` of the training graph. Only merge
 * documents that describe *the same* graph (same device/backup lineage). The main workout backup
 * import replaces the whole training graph instead of merging records, so this merger must not be
 * used to combine set intentions across independent databases - the ids would not refer to the
 * same sets.
 */
object ReadinessDataMerger {
    fun merge(
        local: ReadinessData,
        imported: ReadinessData,
        importedWins: Boolean = false,
    ): ReadinessMergeResult {
        validateOrThrow("local", local)
        validateOrThrow("imported", imported)

        val conflicts = mutableListOf<ReadinessMergeConflict>()

        val localCheckIns = local.checkIns.associateBy { it.localDate }
        val importedCheckIns = imported.checkIns.associateBy { it.localDate }
        val dates = (localCheckIns.keys + importedCheckIns.keys).sorted()
        val mergedCheckIns = dates.mapNotNull { date ->
            val localCheckIn = localCheckIns[date]
            val importedCheckIn = importedCheckIns[date]
            when {
                importedCheckIn == null -> localCheckIn
                localCheckIn == null -> importedCheckIn
                localCheckIn == importedCheckIn -> localCheckIn
                else -> {
                    val resolved = if (importedWins) importedCheckIn else localCheckIn
                    conflicts += ReadinessMergeConflict(
                        kind = ReadinessMergeConflictKind.CHECK_IN,
                        key = date.toString(),
                        localValue = localCheckIn.toString(),
                        importedValue = importedCheckIn.toString(),
                        resolvedValue = resolved.toString(),
                    )
                    resolved
                }
            }
        }

        val localIntentions = local.setIntentions.associateBy { it.setId }
        val importedIntentions = imported.setIntentions.associateBy { it.setId }
        val setIds = (localIntentions.keys + importedIntentions.keys).sorted()
        val mergedIntentions = setIds.mapNotNull { setId ->
            val localRecord = localIntentions[setId]
            val importedRecord = importedIntentions[setId]
            when {
                importedRecord == null -> localRecord
                localRecord == null -> importedRecord
                localRecord == importedRecord -> localRecord
                else -> {
                    val resolved = resolveIntention(localRecord, importedRecord, importedWins)
                    if (!isPureUnknownUpgrade(localRecord, importedRecord)) {
                        conflicts += ReadinessMergeConflict(
                            kind = ReadinessMergeConflictKind.SET_INTENTION,
                            key = setId.toString(),
                            localValue = describe(localRecord),
                            importedValue = describe(importedRecord),
                            resolvedValue = describe(resolved),
                        )
                    }
                    resolved
                }
            }
        }

        return ReadinessMergeResult(
            data = ReadinessData(
                checkIns = mergedCheckIns,
                setIntentions = mergedIntentions,
            ),
            conflicts = conflicts,
        )
    }

    private fun resolveIntention(
        local: SetIntentionRecord,
        imported: SetIntentionRecord,
        importedWins: Boolean,
    ): SetIntentionRecord = when {
        // A known intention is never replaced by UNKNOWN, not even when the caller lets the
        // imported document win: UNKNOWN carries no information, so that would be data loss.
        local.intention == SetIntention.UNKNOWN && imported.intention != SetIntention.UNKNOWN -> imported
        imported.intention == SetIntention.UNKNOWN && local.intention != SetIntention.UNKNOWN -> local
        importedWins -> imported
        else -> local
    }

    /** True only for an entirely blank UNKNOWN record that carries no note and no timestamp. */
    private fun isInformationFree(record: SetIntentionRecord): Boolean =
        record.intention == SetIntention.UNKNOWN &&
            record.note.isEmpty() &&
            record.recordedAtEpochMillis == null

    /** A trivial UNKNOWN record adopting a known intention loses no information. */
    private fun isPureUnknownUpgrade(local: SetIntentionRecord, imported: SetIntentionRecord): Boolean =
        (isInformationFree(local) && imported.intention != SetIntention.UNKNOWN) ||
            (isInformationFree(imported) && local.intention != SetIntention.UNKNOWN)

    private fun describe(record: SetIntentionRecord): String {
        val details = buildList {
            if (record.note.isNotEmpty()) add("note=\"${record.note}\"")
            record.recordedAtEpochMillis?.let { add("recordedAt=$it") }
        }
        return if (details.isEmpty()) {
            record.intention.name
        } else {
            "${record.intention.name} (${details.joinToString(", ")})"
        }
    }

    private fun validateOrThrow(label: String, data: ReadinessData) {
        val result = ReadinessDataValidator.validate(data)
        if (!result.isValid) {
            throw ReadinessDataValidationException(result.errors.map { "$label document: $it" })
        }
    }
}
