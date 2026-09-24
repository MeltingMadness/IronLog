package com.ironlog.shared.plans

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Ordered, explicit targets. An empty list retains the classic uniform target. */
@Serializable
data class PlannedSet(val kind: String = "NORMAL", val reps: Int = 10, val weightKg: Double = 0.0)

object PlannedSets {
    private val json = Json { encodeDefaults = true }
    fun encode(sets: List<PlannedSet>): String = json.encodeToString(sets)
    fun decode(value: String): List<PlannedSet> = json.decodeFromString(value)
    fun matchedIndices(targets: List<PlannedSet>, recordedKinds: List<String>): List<Int?> {
        val used = mutableSetOf<Int>()
        return targets.map { target ->
            val kind = if (target.kind == "BACKOFF") "NORMAL" else target.kind
            recordedKinds.indices.firstOrNull { it !in used && recordedKinds[it] == kind }?.also { used.add(it) }
        }
    }
    /** Only performed slots change; unperformed slots and their order stay intact. */
    fun mergePerformed(targets: List<PlannedSet>, recorded: List<PlannedSet>): List<PlannedSet> =
        matchedIndices(targets, recorded.map { it.kind }).mapIndexed { index, recordedIndex ->
            val original = targets[index]
            recordedIndex?.let { original.copy(reps = recorded[it].reps, weightKg = recorded[it].weightKg) } ?: original
        }

    fun valid(sets: List<PlannedSet>): Boolean = sets.size <= 100 && sets.all {
        it.kind in setOf("NORMAL", "WARMUP", "BACKOFF") && it.reps in 1..1000 &&
            it.weightKg.isFinite() && it.weightKg >= 0.0
    } && (sets.isEmpty() || sets.any { it.kind != "WARMUP" })
}
