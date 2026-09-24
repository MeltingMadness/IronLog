package com.ironlog.shared.backup

import com.ironlog.shared.readinessdata.ReadinessData
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable

/**
 * Version of the backup payload, independent from the Room database version.
 *
 * Schema 12 stores the complete workout-set classification. Older payloads
 * only have the [BackupWorkoutSet.isWarmup] compatibility field and therefore
 * can represent NORMAL and WARMUP sets only.
 *
 * Schema 13 adds the optional readiness side channel [BackupPayloadV1.readinessData]
 * (daily check-ins and per-set intentions) without changing the workout graph.
 */
const val CURRENT_BACKUP_SCHEMA_VERSION = 14
const val SET_TYPE_BACKUP_SCHEMA_VERSION = 12
const val READINESS_BACKUP_SCHEMA_VERSION = 13
const val SESSION_DELOAD_BACKUP_SCHEMA_VERSION = 13

const val NORMAL_SET_TYPE = "NORMAL"
const val WARMUP_SET_TYPE = "WARMUP"
const val DROP_SET_TYPE = "DROP_SET"
const val FAILURE_SET_TYPE = "FAILURE"

val SUPPORTED_BACKUP_SET_TYPES: Set<String> = setOf(
    NORMAL_SET_TYPE,
    WARMUP_SET_TYPE,
    DROP_SET_TYPE,
    FAILURE_SET_TYPE
)

@Serializable
data class BackupPayloadV1(
    val formatVersion: Int,
    val schemaVersion: Int,
    val appVersion: String,
    val exportedAtEpochMillis: Long,
    val exercises: List<BackupExercise>,
    val workoutSessions: List<BackupWorkoutSession>,
    val workoutSets: List<BackupWorkoutSet>,
    val trainingPlans: List<BackupTrainingPlan>,
    val planExercises: List<BackupPlanExercise>,
    val personalRecords: List<BackupPersonalRecord>,
    val metaTrainingPlans: List<BackupMetaTrainingPlan> = emptyList(),
    val metaPlanItems: List<BackupMetaPlanItem> = emptyList(),
    val metaPlanSkips: List<BackupMetaPlanSkip> = emptyList(),
    val workoutPlanTargets: List<BackupWorkoutPlanTarget> = emptyList(),
    val progressionSuggestions: List<BackupProgressionSuggestion> = emptyList(),
    /**
     * Additive readiness side channel: optional daily check-ins and per-set
     * intentions. The default is an empty document, so a schema-12 payload
     * decodes to "no readiness data" and every set stays
     * [com.ironlog.shared.readinessdata.SetIntention.UNKNOWN].
     */
    val readinessData: ReadinessData = ReadinessData()
)

@Serializable
data class BackupExercise(
    val id: Long,
    val name: String,
    val primaryMuscleGroup: String,
    val secondaryMuscleGroups: String,
    val category: String,
    val isCustom: Boolean,
    val notes: String = "",
    val isArchived: Boolean = false
)

@Serializable
data class BackupWorkoutSession(
    val id: Long,
    val startTime: Long,
    val endTime: Long?,
    val durationSeconds: Long,
    val name: String,
    val notes: String,
    val planId: Long? = null,
    val metaPlanId: Long? = null,
    /**
     * Whether this session was carried out under the explicit deload mode.
     *
     * `true` marks a session the platform knowingly started (or continued) while a
     * deload was active, so the readiness trend can remove it from the comparison
     * series instead of reading an intentionally lighter unit as fatigue.
     *
     * The flag is intentionally nullable and additive:
     * * `null` (legacy sessions, schema <= 12) means "not recorded" - the trend
     *   engine maps it to `UNKNOWN` and never applies today's setting
     *   retroactively to an old session;
     * * `false` means the platform explicitly recorded a non-deload session;
     * * `true` is only ever set from a known deload state and is never cleared by
     *   a later settings change.
     */
    val isDeload: Boolean? = null
)

@Serializable
data class BackupWorkoutSet(
    val id: Long,
    val sessionId: Long,
    val exerciseId: Long,
    val setNumber: Int,
    val reps: Int,
    val weightKg: Double,
    val setType: String? = null,
    val completedAt: Long,
    val rpe: Double? = null,
    val planTargetSnapshotId: Long? = null,
    /**
     * Compatibility field used by schema 11 and earlier payloads. New
     * exports leave it null so the canonical [setType] value is authoritative.
     */
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val isWarmup: Boolean? = null
) {
    /** Resolve a legacy warmup flag without flattening any current set type. */
    fun resolvedSetType(): String =
        if (setType == null && isWarmup == true) {
            WARMUP_SET_TYPE
        } else {
            setType ?: NORMAL_SET_TYPE
        }

    /**
     * A non-null legacy flag is only meaningful for NORMAL/WARMUP records.
     * Rejecting it for DROP_SET/FAILURE avoids silently accepting a payload
     * whose old and new representations disagree.
     */
    fun hasConflictingWarmupFlag(): Boolean = when (setType) {
        null -> false
        NORMAL_SET_TYPE -> isWarmup == true
        WARMUP_SET_TYPE -> isWarmup == false
        DROP_SET_TYPE, FAILURE_SET_TYPE -> isWarmup == true
        else -> isWarmup == true
    }
}

@Serializable
data class BackupTrainingPlan(
    val id: Long,
    val name: String,
    val createdAt: Long
)

@Serializable
data class BackupPlanExercise(
    val id: Long,
    val planId: Long,
    val exerciseId: Long,
    val orderIndex: Int,
    val supersetGroupId: Int? = null,
    val targetSets: Int,
    val targetReps: Int,
    val targetWeightKg: Double,
    val progression: BackupProgressionConfig = BackupProgressionConfig(),
    val setTargets: List<com.ironlog.shared.plans.PlannedSet> = emptyList()
)

@Serializable
data class BackupProgressionConfig(
    val scheme: String = "MANUAL",
    val incrementValue: Double? = null,
    val incrementUnit: String? = null,
    val incrementKg: Double? = null,
    val minReps: Int? = null,
    val maxReps: Int? = null,
    val targetTotalReps: Long? = null,
    val targetRpe: Double? = null,
    val rpeTolerance: Double? = null,
    val stallThreshold: Int = 2,
    val backoffPercent: Double = 10.0,
    val ruleRevision: Int = 1
)

@Serializable
data class BackupProgressionTarget(val sets: Int, val reps: Int, val weightKg: Double)

@Serializable
data class BackupWorkoutPlanTarget(
    val id: Long,
    val sessionId: Long,
    val planId: Long,
    val exerciseId: Long,
    val orderIndex: Int,
    val supersetGroupId: Int? = null,
    val target: BackupProgressionTarget,
    val progression: BackupProgressionConfig,
    val setTargets: List<com.ironlog.shared.plans.PlannedSet> = emptyList()
)

@Serializable
data class BackupProgressionSuggestion(
    val id: Long,
    val sourceSessionId: Long,
    val sourceTargetSnapshotId: Long,
    val planId: Long,
    val exerciseId: Long,
    val orderIndex: Int,
    val supersetGroupId: Int? = null,
    val sourceTarget: BackupProgressionTarget,
    val sourceProgression: BackupProgressionConfig,
    val outcomeType: String,
    val reasonCode: String,
    val reasonArguments: Map<String, Double> = emptyMap(),
    val countedSetIds: List<Long> = emptyList(),
    val streakEffect: String,
    val suggestedTarget: BackupProgressionTarget? = null,
    val status: String,
    val wasEdited: Boolean = false,
    val finalTarget: BackupProgressionTarget? = null,
    val createdAtEpochMillis: Long,
    val decidedAtEpochMillis: Long? = null
)

@Serializable
data class BackupPersonalRecord(
    val id: Long,
    val exerciseId: Long,
    val type: String,
    val value: Double,
    val achievedAt: Long
)

@Serializable
data class BackupMetaTrainingPlan(
    val id: Long,
    val name: String,
    val createdAt: Long
)

@Serializable
data class BackupMetaPlanItem(
    val id: Long,
    val metaPlanId: Long,
    val trainingPlanId: Long,
    val orderIndex: Int
)

@Serializable
data class BackupMetaPlanSkip(
    val id: Long,
    val metaPlanId: Long,
    val trainingPlanId: Long,
    val skippedAt: Long
)
