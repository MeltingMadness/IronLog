package com.ironlog.shared.readinessdata

import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Thrown when readiness JSON is malformed or violates the documented invariants.
 *
 * [errors] is the full, human-readable list so the caller can show why an import was refused.
 */
class ReadinessDataValidationException(
    val errors: List<String>,
) : IllegalArgumentException("Invalid readiness data: ${errors.joinToString("; ")}")

/**
 * Versioned JSON codec for [ReadinessData].
 *
 * Both directions validate before returning or emitting, so a caller can never persist invalid
 * readiness data or accept it from a file. Unknown keys are rejected: a newer document that this
 * app does not understand fails closed instead of being silently truncated.
 */
object ReadinessDataCodec {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = false
    }

    /** Validates [data] first, then encodes it. */
    fun encode(data: ReadinessData): String {
        validateOrThrow(data)
        return json.encodeToString(data)
    }

    /** Decodes and validates [serialized]; fails closed with [ReadinessDataValidationException]. */
    fun decode(serialized: String): ReadinessData {
        val decoded = try {
            json.decodeFromString<ReadinessData>(serialized)
        } catch (error: SerializationException) {
            throw ReadinessDataValidationException(
                listOf("Malformed readiness data: ${error.message ?: "unknown serialization error"}"),
            )
        } catch (error: IllegalArgumentException) {
            // A custom serializer (e.g. an unparseable ISO date) surfaces as IllegalArgumentException.
            throw ReadinessDataValidationException(
                listOf("Malformed readiness data: ${error.message ?: "invalid field value"}"),
            )
        }
        val upgraded = upgrade(decoded)
        validateOrThrow(upgraded)
        return upgraded
    }

    /** Validates [data] and throws [ReadinessDataValidationException] when it is invalid. */
    fun validateOrThrow(data: ReadinessData) {
        val result = ReadinessDataValidator.validate(data)
        if (!result.isValid) {
            throw ReadinessDataValidationException(result.errors)
        }
    }

    /**
     * Bumps older documents to the current schema while copying every value unchanged.
     *
     * Schema 1 is the first version, so this is currently a no-op re-stamp. Future versions add
     * their upgrade step here; anything an older version did not know stays
     * [SetIntention.UNKNOWN] and is never inferred. A *newer* document keeps its version so the
     * validator can reject it.
     */
    private fun upgrade(data: ReadinessData): ReadinessData =
        if (data.schemaVersion < CURRENT_READINESS_DATA_SCHEMA_VERSION) {
            data.copy(schemaVersion = CURRENT_READINESS_DATA_SCHEMA_VERSION)
        } else {
            data
        }
}
