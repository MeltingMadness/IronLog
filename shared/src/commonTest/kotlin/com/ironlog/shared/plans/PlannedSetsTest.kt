package com.ironlog.shared.plans
import kotlin.test.*
class PlannedSetsTest {
    @Test fun invalidValuesAndWarmupOnlyAreRejected() {
        assertFalse(PlannedSets.valid(listOf(PlannedSet(weightKg = Double.NaN))))
        assertFalse(PlannedSets.valid(listOf(PlannedSet(reps = 0))))
        assertFalse(PlannedSets.valid(listOf(PlannedSet(kind = "WARMUP"))))
        assertTrue(PlannedSets.valid(emptyList()))
    }
    @Test fun warmupsAndExtrasCannotFillWorkingSlots() {
        val targets = List(3) { PlannedSet(reps = 10, weightKg = 60.0) }
        val recorded = listOf(PlannedSet("WARMUP", 5, 20.0), PlannedSet("NORMAL", 10, 60.0), PlannedSet("DROP_SET", 12, 40.0), PlannedSet("NORMAL", 9, 60.0))
        assertEquals(listOf(1, 3, null), PlannedSets.matchedIndices(targets, recorded.map { it.kind }))
        assertEquals(listOf(targets[0], targets[1].copy(reps = 9), targets[2]), PlannedSets.mergePerformed(targets, recorded))
    }
}
