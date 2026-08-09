package com.ironlog.app.data.local.entity

import com.ironlog.app.domain.model.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun `domain and entity mapping preserve nullable plan target snapshot id`() {
        val linkedDomainSet = WorkoutSet(
            id = 11L,
            sessionId = 2L,
            exerciseId = 3L,
            setNumber = 1,
            reps = 8,
            weightKg = 100.0,
            completedAt = LocalDateTime.now(),
            planTargetSnapshotId = 41L
        )
        val linkedEntity = WorkoutSetEntity.fromDomain(linkedDomainSet)
        assertEquals(41L, linkedEntity.planTargetSnapshotId)
        assertEquals(41L, linkedEntity.toDomain().planTargetSnapshotId)

        val adHocDomainSet = linkedDomainSet.copy(planTargetSnapshotId = null)
        val adHocEntity = WorkoutSetEntity.fromDomain(adHocDomainSet)
        assertNull(adHocEntity.planTargetSnapshotId)
        assertNull(adHocEntity.toDomain().planTargetSnapshotId)
    }
}
