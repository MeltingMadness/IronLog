package com.ironlog.app.data.repository

import com.ironlog.app.data.local.dao.ExerciseDao
import com.ironlog.app.data.local.dao.WorkoutSessionDao
import com.ironlog.app.data.local.dao.WorkoutSetDao
import com.ironlog.app.data.local.entity.EpochConverter
import com.ironlog.app.domain.deload.DeloadDetector
import com.ironlog.app.domain.deload.isCompoundExercise
import com.ironlog.app.domain.model.DeloadAssessment
import com.ironlog.app.domain.model.DeloadSessionInput
import com.ironlog.app.domain.repository.DeloadRepository
import java.time.LocalDate

/**
 * Lädt die abgeschlossenen Trainingseinheiten des rollierenden Deload-Fensters
 * und bewertet sie mit dem [DeloadDetector].
 *
 * Verbundübungen werden anhand des Seed-Katalogs klassifiziert
 * (Langhantelübungen, siehe [isCompoundExercise]).
 */
class DeloadRepositoryImpl(
    private val sessionDao: WorkoutSessionDao,
    private val setDao: WorkoutSetDao,
    private val exerciseDao: ExerciseDao,
    private val detector: DeloadDetector = DeloadDetector(),
    private val now: () -> LocalDate = LocalDate::now
) : DeloadRepository {

    override suspend fun assess(): DeloadAssessment {
        val today = now()
        val windowStart = today.minusWeeks(detector.config.windowWeeks.toLong())
        val windowStartMillis = EpochConverter.toLong(windowStart.atStartOfDay())

        val sessionsInWindow = sessionDao.getAllCompletedSessionsList()
            .filter { it.startTime >= windowStartMillis }
            .sortedBy { it.startTime }
        if (sessionsInWindow.isEmpty()) {
            return detector.assess(
                sessions = emptyList(),
                compoundExerciseIds = emptySet(),
                today = today
            )
        }

        val setsBySession = setDao.getSetsForSessions(sessionsInWindow.map { it.id })
            .groupBy { it.sessionId }

        val compoundExerciseIds = exerciseDao
            .getExercisesByIds(setsBySession.values.flatten().map { it.exerciseId }.distinct())
            .filter { it.toDomain().isCompoundExercise() }
            .map { it.id }
            .toSet()

        val inputs = sessionsInWindow.map { session ->
            DeloadSessionInput(
                id = session.id,
                startTime = EpochConverter.toLocalDateTime(session.startTime),
                sets = (setsBySession[session.id] ?: emptyList()).map { it.toDomain() }
            )
        }

        return detector.assess(
            sessions = inputs,
            compoundExerciseIds = compoundExerciseIds,
            today = today
        )
    }
}