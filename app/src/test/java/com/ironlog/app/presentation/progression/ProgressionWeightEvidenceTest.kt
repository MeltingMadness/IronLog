package com.ironlog.app.presentation.progression

import com.ironlog.app.domain.model.SetType
import com.ironlog.app.domain.model.WorkoutSet
import org.junit.Assert.*
import org.junit.Test

class ProgressionWeightEvidenceTest {
    private fun sets(vararg weights: Double) = weights.mapIndexed { i, weight ->
        WorkoutSet(id = i + 1L, sessionId = 1, exerciseId = 2, setNumber = i + 1,
            reps = 10, weightKg = weight, planTargetSnapshotId = 3)
    }

    @Test fun `uniform trained weight reveals historical zero-plan comparison`() {
        val evidence = sets(45.0, 45.0, 45.0)
        assertEquals(45.0, uniformCountedWeightKg(evidence)!!, 0.0)
        assertTrue(isHistoricalPlanWeightDeviation(evidence, 0.0, 0.0, 45.0))
    }

    @Test fun `true mixed zero and positive weights are not relabelled historical`() {
        val evidence = sets(0.0, 45.0, 45.0)
        assertNull(uniformCountedWeightKg(evidence))
        assertFalse(isHistoricalPlanWeightDeviation(evidence, 0.0, 0.0, 45.0))
    }

    @Test fun `real zero weight is distinct from missing evidence`() {
        assertEquals(0.0, uniformCountedWeightKg(sets(0.0, 0.0))!!, 0.0)
        assertNull(uniformCountedWeightKg(emptyList()))
        assertFalse(isHistoricalPlanWeightDeviation(sets(0.0, 0.0), 0.0, 0.0, 0.0))
    }

    @Test fun `invalid and non-counted evidence is unknown`() {
        assertNull(uniformCountedWeightKg(sets(Double.NaN)))
        assertNull(uniformCountedWeightKg(sets(-1.0)))
        assertNull(uniformCountedWeightKg(sets(45.0).map { it.copy(setType = SetType.WARMUP) }))
        assertFalse(isHistoricalPlanWeightDeviation(sets(45.0), 0.0, null, 45.0))
        assertFalse(isHistoricalPlanWeightDeviation(sets(45.0), 0.0, 0.0, 40.0))
    }
}
