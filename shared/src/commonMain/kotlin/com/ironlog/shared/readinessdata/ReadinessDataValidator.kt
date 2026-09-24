package com.ironlog.shared.readinessdata

import kotlinx.datetime.LocalDate

/** Result of validating a whole [ReadinessData] document. */
data class ReadinessValidationResult(
    val isValid: Boolean,
    val errors: List<String>,
)

/**
 * Structural validation for [ReadinessData].
 *
 * Every message names the offender (check-in date or set id, field, and value) so a rejected
 * import can be explained to the user instead of failing opaquely. Values are never repaired.
 */
object ReadinessDataValidator {
    fun validate(
        data: ReadinessData,
        currentSchemaVersion: Int = CURRENT_READINESS_DATA_SCHEMA_VERSION,
    ): ReadinessValidationResult {
        val errors = mutableListOf<String>()

        if (data.formatVersion != READINESS_DATA_FORMAT_VERSION) {
            errors += "Unsupported readiness data format version: ${data.formatVersion}"
        }
        if (data.schemaVersion <= 0) {
            errors += "Readiness data schema version must be positive: ${data.schemaVersion}"
        }
        if (data.schemaVersion > currentSchemaVersion) {
            errors +=
                "Readiness data schema version ${data.schemaVersion} is newer than app schema $currentSchemaVersion"
        }

        checkDuplicateDates(errors, data.checkIns)
        data.checkIns.forEach { checkIn ->
            val owner = "Check-in ${checkIn.localDate}"
            validateScale(errors, owner, "sleepQuality", checkIn.sleepQuality)
            validateScale(errors, owner, "energy", checkIn.energy)
            validateScale(errors, owner, "stress", checkIn.stress)
            checkIn.muscleSoreness.forEach { (group, value) ->
                validateScale(errors, owner, "muscleSoreness.${group.name}", value)
            }
            validateEpoch(errors, owner, "recordedAtEpochMillis", checkIn.recordedAtEpochMillis)
            validateEpoch(errors, owner, "updatedAtEpochMillis", checkIn.updatedAtEpochMillis)
        }

        checkDuplicateSetIds(errors, data.setIntentions)
        data.setIntentions.forEach { record ->
            val owner = "Set intention ${record.setId}"
            if (record.setId <= 0) {
                errors += "$owner must reference a positive set id"
            }
            validateEpoch(errors, owner, "recordedAtEpochMillis", record.recordedAtEpochMillis)
        }

        return ReadinessValidationResult(isValid = errors.isEmpty(), errors = errors)
    }

    /** True when [value] is inside the fixed subjective scale. */
    fun isValidScale(value: Int): Boolean = value in READINESS_SCALE_MIN..READINESS_SCALE_MAX

    private fun validateScale(
        errors: MutableList<String>,
        owner: String,
        field: String,
        value: Int?,
    ) {
        if (value != null && !isValidScale(value)) {
            errors += "$owner field $field=$value is outside $READINESS_SCALE_MIN..$READINESS_SCALE_MAX"
        }
    }

    private fun validateEpoch(
        errors: MutableList<String>,
        owner: String,
        field: String,
        value: Long?,
    ) {
        if (value != null && value < 0) {
            errors += "$owner field $field=$value must not be negative"
        }
    }

    private fun checkDuplicateDates(errors: MutableList<String>, checkIns: List<ReadinessCheckIn>) {
        val seen = mutableSetOf<LocalDate>()
        checkIns.forEach { checkIn ->
            if (!seen.add(checkIn.localDate)) {
                errors += "Duplicate readiness check-in for ${checkIn.localDate}"
            }
        }
    }

    private fun checkDuplicateSetIds(errors: MutableList<String>, records: List<SetIntentionRecord>) {
        val seen = mutableSetOf<Long>()
        records.forEach { record ->
            if (!seen.add(record.setId)) {
                errors += "Duplicate set intention for set ${record.setId}"
            }
        }
    }
}
