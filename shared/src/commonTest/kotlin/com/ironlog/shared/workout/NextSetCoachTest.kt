package com.ironlog.shared.workout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NextSetCoachTest {

    @Test
    fun targetRpeUsesLatestNormalSetAndIgnoresWarmup() {
        val result = NextSetCoachFacade.evaluate(
            sets = listOf(
                NextSetCoachSet(id = 1L, setNumber = 1, weightKg = 80.0, rpe = 7.0),
                NextSetCoachSet(
                    id = 2L,
                    setNumber = 2,
                    weightKg = 40.0,
                    rpe = 10.0,
                    setType = "WARMUP",
                ),
                NextSetCoachSet(id = 3L, setNumber = 3, weightKg = 80.0, rpe = 8.5),
            ),
            targetRpe = 8.0,
            backoffPercent = 10.0,
        )

        requireNotNull(result)
        assertEquals(79.0, result.recommendedWeightKg)
        assertEquals(80.0, result.lastWeightKg)
        assertEquals(8.5, result.lastRpe)
        assertEquals(8.0, result.targetRpe)
        assertEquals(false, result.isOvershoot)
        assertNull(result.backoffWeightKg)
    }

    @Test
    fun highRpeWithoutTargetProvidesNextWeightAndBackoff() {
        val result = NextSetCoachFacade.evaluate(
            sets = listOf(
                NextSetCoachSet(id = 20L, setNumber = 1, weightKg = 80.0, rpe = 9.5),
            ),
            targetRpe = null,
            backoffPercent = 10.0,
        )

        requireNotNull(result)
        assertEquals(79.0, result.recommendedWeightKg)
        assertEquals(true, result.isOvershoot)
        assertEquals(72.0, result.backoffWeightKg)
    }

    @Test
    fun rirInputMapsToCanonicalRpeBeforeCoaching() {
        assertEquals(8.0, NextSetCoachFacade.intensityToRpe(2.0, "RIR"))
        assertEquals(8.0, NextSetCoachFacade.intensityToRpe(8.0, "RPE"))
        assertEquals(2.0, NextSetCoachFacade.rpeToIntensity(8.0, "RIR"))
        assertNull(NextSetCoachFacade.intensityToRpe(10.0, "RIR"))
        assertNull(NextSetCoachFacade.intensityToRpe(8.0, "OFF"))
    }

    @Test
    fun visibleRirTargetUsesTheSameCanonicalCoachRule() {
        val result = NextSetCoachFacade.evaluateWithIntensitySystem(
            sets = listOf(
                NextSetCoachSet(id = 25L, setNumber = 1, weightKg = 80.0, rpe = 8.5),
            ),
            targetIntensity = 2.0,
            intensitySystemName = "RIR",
            backoffPercent = 10.0,
        )

        requireNotNull(result)
        assertEquals(79.0, result.recommendedWeightKg)
        assertEquals(8.0, result.targetRpe)
    }

    @Test
    fun missingRpeOrNormalSetProducesNoHint() {
        assertNull(
            NextSetCoachFacade.evaluate(
                sets = listOf(
                    NextSetCoachSet(
                        id = 30L,
                        setNumber = 1,
                        weightKg = 80.0,
                        rpe = null,
                    ),
                ),
                targetRpe = 8.0,
                backoffPercent = 10.0,
            ),
        )
        assertNull(
            NextSetCoachFacade.evaluate(
                sets = listOf(
                    NextSetCoachSet(
                        id = 31L,
                        setNumber = 1,
                        weightKg = 40.0,
                        rpe = 10.0,
                        setType = "WARMUP",
                    ),
                ),
                targetRpe = null,
                backoffPercent = 10.0,
            ),
        )
    }
}
