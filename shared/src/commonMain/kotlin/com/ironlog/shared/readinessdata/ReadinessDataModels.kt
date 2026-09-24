package com.ironlog.shared.readinessdata

import com.ironlog.shared.model.MuscleGroup
import kotlinx.datetime.LocalDate
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Bounds of every subjective self-report value.
 *
 * The scale is deliberately a fixed 1..5 and values outside it are never clamped or rounded:
 * validation fails closed and names the offending field so a broken import cannot silently
 * turn into plausible-looking data.
 */
const val READINESS_SCALE_MIN = 1
const val READINESS_SCALE_MAX = 5

/**
 * ISO-8601 (`yyyy-MM-dd`) wire format for a check-in day.
 *
 * Declared explicitly instead of relying on a library default so the persisted representation
 * stays stable even if kotlinx-datetime changes its built-in serializer.
 */
object ReadinessLocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor(
            "com.ironlog.shared.readinessdata.LocalDateIso8601",
            PrimitiveKind.STRING,
        )

    override fun serialize(encoder: Encoder, value: LocalDate) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): LocalDate =
        LocalDate.parse(decoder.decodeString())
}

/**
 * One optional, self-reported daily form check-in ("Tagesform").
 *
 * [localDate] is the user's calendar day and is always stored explicitly. Nothing in this model
 * derives "today": a check-in without a date cannot be represented, which prevents a timeless
 * entry from drifting to another day when it is reloaded.
 *
 * Every answer is optional. A `null` dimension means "the user did not answer this" and is
 * distinct from a reported value; both states must survive export and import unchanged.
 */
@Serializable
data class ReadinessCheckIn(
    @Serializable(with = ReadinessLocalDateSerializer::class)
    val localDate: LocalDate,
    /** 1..5, or null when the user skipped sleep quality. */
    val sleepQuality: Int? = null,
    /** 1..5, or null when the user skipped energy. */
    val energy: Int? = null,
    /** 1..5, or null when the user skipped stress. */
    val stress: Int? = null,
    /**
     * Optional 1..5 soreness keyed by the existing [MuscleGroup] set. An absent key means
     * "not reported for this muscle group"; it is not the same as a reported low value.
     */
    val muscleSoreness: Map<MuscleGroup, Int> = emptyMap(),
    /** Epoch millis when the entry was first recorded, for preservation and ordering. */
    val recordedAtEpochMillis: Long? = null,
    /** Epoch millis of the last edit, for preservation and ordering. */
    val updatedAtEpochMillis: Long? = null,
) {
    /** True when at least one dimension was answered; an entirely empty check-in is not useful. */
    fun hasAnyAnswer(): Boolean =
        sleepQuality != null || energy != null || stress != null || muscleSoreness.isNotEmpty()
}
