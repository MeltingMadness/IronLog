package com.ironlog.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class EnumParsingTest {

    // --- MuscleGroup ---

    @Test
    fun `MuscleGroup safeValueOf mit gueltigem Wert`() {
        assertEquals(MuscleGroup.BRUST, MuscleGroup.safeValueOf("BRUST"))
        assertEquals(MuscleGroup.RUECKEN, MuscleGroup.safeValueOf("RUECKEN"))
        assertEquals(MuscleGroup.WADEN, MuscleGroup.safeValueOf("WADEN"))
    }

    @Test
    fun `MuscleGroup safeValueOf mit ungueltigem Wert gibt Fallback`() {
        assertEquals(MuscleGroup.BRUST, MuscleGroup.safeValueOf("NONSENSE"))
    }

    @Test
    fun `MuscleGroup safeValueOf mit leerem String gibt Fallback`() {
        assertEquals(MuscleGroup.BRUST, MuscleGroup.safeValueOf(""))
    }

    @Test
    fun `MuscleGroup safeValueOf mit benutzerdefiniertem Fallback`() {
        assertEquals(MuscleGroup.CORE, MuscleGroup.safeValueOf("INVALID", MuscleGroup.CORE))
    }

    // --- ExerciseCategory ---

    @Test
    fun `ExerciseCategory safeValueOf mit gueltigem Wert`() {
        assertEquals(ExerciseCategory.LANGHANTEL, ExerciseCategory.safeValueOf("LANGHANTEL"))
        assertEquals(ExerciseCategory.KABEL, ExerciseCategory.safeValueOf("KABEL"))
        assertEquals(ExerciseCategory.EIGENGEWICHT, ExerciseCategory.safeValueOf("EIGENGEWICHT"))
    }

    @Test
    fun `ExerciseCategory safeValueOf mit ungueltigem Wert gibt Fallback`() {
        assertEquals(ExerciseCategory.LANGHANTEL, ExerciseCategory.safeValueOf("NONSENSE"))
    }

    @Test
    fun `ExerciseCategory safeValueOf mit leerem String gibt Fallback`() {
        assertEquals(ExerciseCategory.LANGHANTEL, ExerciseCategory.safeValueOf(""))
    }

    @Test
    fun `ExerciseCategory safeValueOf mit benutzerdefiniertem Fallback`() {
        assertEquals(ExerciseCategory.MASCHINE, ExerciseCategory.safeValueOf("INVALID", ExerciseCategory.MASCHINE))
    }

    // --- RecordType ---

    @Test
    fun `RecordType safeValueOf mit gueltigem Wert`() {
        assertEquals(RecordType.MAX_WEIGHT, RecordType.safeValueOf("MAX_WEIGHT"))
        assertEquals(RecordType.MAX_REPS, RecordType.safeValueOf("MAX_REPS"))
        assertEquals(RecordType.MAX_E1RM, RecordType.safeValueOf("MAX_E1RM"))
        assertEquals(RecordType.MAX_VOLUME, RecordType.safeValueOf("MAX_VOLUME"))
    }

    @Test
    fun `RecordType safeValueOf mit ungueltigem Wert gibt Fallback`() {
        assertEquals(RecordType.MAX_WEIGHT, RecordType.safeValueOf("NONSENSE"))
    }

    @Test
    fun `RecordType safeValueOf mit leerem String gibt Fallback`() {
        assertEquals(RecordType.MAX_WEIGHT, RecordType.safeValueOf(""))
    }

    @Test
    fun `RecordType safeValueOf mit benutzerdefiniertem Fallback`() {
        assertEquals(RecordType.MAX_E1RM, RecordType.safeValueOf("INVALID", RecordType.MAX_E1RM))
    }

    // --- Alle Enum-Werte durchlaufen ---

    @Test
    fun `Alle MuscleGroup-Werte rund-trip`() {
        for (mg in MuscleGroup.entries) {
            assertEquals(mg, MuscleGroup.safeValueOf(mg.name))
        }
    }

    @Test
    fun `Alle ExerciseCategory-Werte rund-trip`() {
        for (ec in ExerciseCategory.entries) {
            assertEquals(ec, ExerciseCategory.safeValueOf(ec.name))
        }
    }

    @Test
    fun `Alle RecordType-Werte rund-trip`() {
        for (rt in RecordType.entries) {
            assertEquals(rt, RecordType.safeValueOf(rt.name))
        }
    }
}
