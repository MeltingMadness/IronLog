package com.ironlog.shared.readinessdata

import com.ironlog.shared.model.MuscleGroup
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReadinessDataCodecTest {

    @Test
    fun `round trips every optional answer unchanged`() {
        val source = ReadinessData(
            checkIns = listOf(
                ReadinessCheckIn(
                    localDate = LocalDate(2026, 9, 11),
                    sleepQuality = 4,
                    energy = 3,
                    stress = 2,
                    muscleSoreness = mapOf(MuscleGroup.BEINE to 5, MuscleGroup.RUECKEN to 1),
                    recordedAtEpochMillis = 1_757_500_000_000,
                ),
            ),
            setIntentions = listOf(
                SetIntentionRecord(setId = 42, intention = SetIntention.PLANNED_FAILURE),
                SetIntentionRecord(setId = 43, intention = SetIntention.UNEXPECTED_TARGET_MISS),
            ),
        )

        val decoded = ReadinessDataCodec.decode(ReadinessDataCodec.encode(source))

        assertEquals(source, decoded)
    }

    @Test
    fun `unanswered dimensions stay null and soreness stays absent`() {
        val source = ReadinessData(
            checkIns = listOf(ReadinessCheckIn(localDate = LocalDate(2026, 9, 11), energy = 3)),
        )

        val decoded = ReadinessDataCodec.decode(ReadinessDataCodec.encode(source))
        val checkIn = decoded.checkIns.single()

        assertNull(checkIn.sleepQuality)
        assertNull(checkIn.stress)
        assertTrue(checkIn.muscleSoreness.isEmpty())
        assertEquals(3, checkIn.energy)
    }

    @Test
    fun `local date is written as an iso calendar date`() {
        val encoded = ReadinessDataCodec.encode(
            ReadinessData(checkIns = listOf(ReadinessCheckIn(localDate = LocalDate(2026, 9, 11)))),
        )

        assertTrue(encoded.contains("\"localDate\":\"2026-09-11\""), encoded)
    }

    @Test
    fun `legacy document without intentions resolves every set to unknown`() {
        val legacy = """{"formatVersion":1,"schemaVersion":1,"checkIns":[]}"""

        val decoded = ReadinessDataCodec.decode(legacy)

        assertTrue(decoded.setIntentions.isEmpty())
        assertEquals(
            SetIntention.UNKNOWN,
            SetIntentionSemantics.intentionFor(7L, decoded.setIntentions),
        )
    }

    @Test
    fun `out of range scale fails closed and names the field`() {
        val invalid =
            """{"formatVersion":1,"schemaVersion":1,"checkIns":[{"localDate":"2026-09-11","sleepQuality":7}]}"""

        val error = assertFailsWith<ReadinessDataValidationException> {
            ReadinessDataCodec.decode(invalid)
        }

        assertTrue(
            error.errors.any { it.contains("sleepQuality") && it.contains("7") },
            error.errors.toString(),
        )
    }

    @Test
    fun `unknown keys fail closed instead of being truncated`() {
        val future =
            """{"formatVersion":1,"schemaVersion":1,"checkIns":[],"futureField":true}"""

        assertFailsWith<ReadinessDataValidationException> {
            ReadinessDataCodec.decode(future)
        }
    }

    @Test
    fun `malformed json fails closed`() {
        assertFailsWith<ReadinessDataValidationException> {
            ReadinessDataCodec.decode("""{"formatVersion":1,"checkIns":[""")
        }
    }

    @Test
    fun `unparseable date fails closed`() {
        val invalid =
            """{"formatVersion":1,"schemaVersion":1,"checkIns":[{"localDate":"2026-13-40"}]}"""

        assertFailsWith<ReadinessDataValidationException> {
            ReadinessDataCodec.decode(invalid)
        }
    }

    @Test
    fun `newer schema version fails closed`() {
        val future = """{"formatVersion":1,"schemaVersion":99,"checkIns":[]}"""

        assertFailsWith<ReadinessDataValidationException> {
            ReadinessDataCodec.decode(future)
        }
    }

    @Test
    fun `encode refuses an invalid in-memory document`() {
        val invalid = ReadinessData(
            checkIns = listOf(ReadinessCheckIn(localDate = LocalDate(2026, 9, 11), stress = 0)),
        )

        assertFailsWith<ReadinessDataValidationException> {
            ReadinessDataCodec.encode(invalid)
        }
    }
}
