package com.ironlog.app.data.local.entity

import com.ironlog.app.domain.model.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class WorkoutSetEntityTest {

    @Test
    fun `WorkoutSet and WorkoutSetEntity conversion preserves rpe`() {
        val domainSet = WorkoutSet(
            id = 1L,
            sessionId = 2L,
            exerciseId = 3L,
            setNumber = 1,
            reps = 10,
            weightKg = 100.0,
            isWarmup = false,
            completedAt = LocalDateTime.now(),
            rpe = 8.5
        )
        val entity = WorkoutSetEntity.fromDomain(domainSet)
        assertEquals(8.5, entity.rpe)
        val convertedBack = entity.toDomain()
        assertEquals(8.5, convertedBack.rpe)
    }
}
