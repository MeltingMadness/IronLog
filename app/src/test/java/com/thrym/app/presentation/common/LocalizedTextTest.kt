package com.thrym.app.presentation.common

import com.thrym.app.domain.error.ExerciseValidationException
import com.thrym.app.domain.error.IntensityRangeException
import com.thrym.app.domain.error.PlanApplyException
import com.thrym.app.domain.model.ExerciseCategory
import com.thrym.app.domain.model.IntensitySystem
import com.thrym.app.domain.model.MuscleGroup
import com.thrym.app.domain.model.RecordType
import com.thrym.app.fakes.TestUiStrings
import com.thrym.core.designsystem.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalizedTextTest {

    @Test
    fun `every persisted enum code has a label`() {
        val labels = MuscleGroup.entries.map { TestUiStrings.get(it.labelRes()) } +
            ExerciseCategory.entries.map { TestUiStrings.get(it.labelRes()) } +
            RecordType.entries.map { TestUiStrings.get(it.labelRes()) } +
            IntensitySystem.entries.map { TestUiStrings.get(it.labelRes()) }

        assertTrue(labels.all { it.isNotBlank() })
        assertEquals(MuscleGroup.entries.size, MuscleGroup.entries.map { it.labelRes() }.toSet().size)
        assertEquals("Chest", TestUiStrings.get(MuscleGroup.BRUST.labelRes()))
        assertEquals("Bodyweight", TestUiStrings.get(ExerciseCategory.EIGENGEWICHT.labelRes()))
        assertEquals("Off", TestUiStrings.get(IntensitySystem.OFF.labelRes()))
    }

    @Test
    fun `typed domain errors map to localized copy`() {
        assertEquals(
            TestUiStrings.get(R.string.error_exercise_duplicate_name),
            ExerciseValidationException(ExerciseValidationException.Reason.DUPLICATE_NAME, "dev text").toUserDetail(TestUiStrings)
        )
        assertEquals(
            TestUiStrings.get(R.string.error_exercise_name_length, 3, 60),
            ExerciseValidationException(ExerciseValidationException.Reason.NAME_LENGTH, "dev text").toUserDetail(TestUiStrings)
        )
        assertEquals(
            TestUiStrings.get(R.string.error_intensity_rir_range, 0, 9),
            IntensityRangeException(IntensityRangeException.Reason.RIR_OUT_OF_RANGE, "dev text").toUserDetail(TestUiStrings)
        )
        assertEquals(
            TestUiStrings.get(R.string.error_plan_apply_plan_changed),
            PlanApplyException(PlanApplyException.Reason.PLAN_CHANGED, "dev text").userMessageOrNull(TestUiStrings)
        )
        assertEquals("schon lokalisiert", UserMessageException("schon lokalisiert").toUserDetail(TestUiStrings))
    }

    @Test
    fun `other failures never expose their developer message`() {
        val failure = IllegalStateException("SQLITE_BUSY private database details")

        assertNull(failure.userMessageOrNull(TestUiStrings))
        assertEquals(TestUiStrings.get(R.string.common_error_unknown), failure.toUserDetail(TestUiStrings))
        assertFalse(failure.toUserDetail(TestUiStrings).contains("SQLITE"))
    }

    @Test
    fun `plurals pick the singular only for one`() {
        assertEquals("1 rep", TestUiStrings.getQuantity(R.plurals.common_record_reps, 1, 1))
        assertEquals("5 reps", TestUiStrings.getQuantity(R.plurals.common_record_reps, 5, 5))
        assertEquals("0 reps", TestUiStrings.getQuantity(R.plurals.common_record_reps, 0, 0))
    }
}
