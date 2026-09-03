package com.ironlog.app.data.local.entity

import com.ironlog.app.domain.model.SetType
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
            setType = SetType.NORMAL,
            completedAt = LocalDateTime.now(),
            rpe = 8.5
        )
        val entity = WorkoutSetEntity.fromDomain(domainSet)
        assertEquals(8.5, entity.rpe)
        assertEquals(SetType.NORMAL.name, entity.setType)
        val convertedBack = entity.toDomain()
        assertEquals(8.5, convertedBack.rpe)
        assertEquals(SetType.NORMAL, convertedBack.setType)
    }

    @Test
    fun `set type round trips through the entity storage name`() {
        SetType.entries.forEach { type ->
            val domainSet = WorkoutSet(
                id = 1L,
                sessionId = 2L,
                exerciseId = 3L,
                setNumber = 1,
                reps = 10,
                weightKg = 100.0,
                setType = type,
                completedAt = LocalDateTime.now()
            )
            val entity = WorkoutSetEntity.fromDomain(domainSet)
            assertEquals(type.name, entity.setType)
            assertEquals(type, entity.toDomain().setType)
        }
    }

    @Test
    fun `unknown stored set type degrades to normal`() {
        val entity = WorkoutSetEntity(
            id = 1L,
            sessionId = 2L,
            exerciseId = 3L,
            setNumber = 1,
            reps = 10,
            weightKg = 100.0,
            setType = "SOME_FUTURE_TYPE",
            completedAt = 0L
        )
        val domainSet = entity.toDomain()
        assertEquals(SetType.NORMAL, domainSet.setType)
        assertEquals(false, domainSet.isWarmup)
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