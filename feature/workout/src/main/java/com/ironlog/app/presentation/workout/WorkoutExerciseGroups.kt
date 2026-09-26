package com.ironlog.app.presentation.workout

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ironlog.core.designsystem.R
import com.ironlog.app.presentation.common.IronLogSurfaceCard
import com.ironlog.app.presentation.common.IronLogSurfaceTone
import com.ironlog.app.presentation.theme.ironLogDimens
import com.ironlog.app.presentation.theme.semantic

internal data class ExerciseRenderGroup(
    val key: String,
    val supersetGroupId: Int?,
    val exercises: List<ExerciseWithSets>
)

internal fun buildExerciseRenderGroups(exercises: List<ExerciseWithSets>): List<ExerciseRenderGroup> {
    if (exercises.isEmpty()) return emptyList()

    val groups = mutableListOf<ExerciseRenderGroup>()
    var cursor = 0
    while (cursor < exercises.size) {
        val groupId = exercises[cursor].supersetGroupId
        if (groupId == null) {
            val item = exercises[cursor]
            groups += ExerciseRenderGroup(
                key = "single-${item.key.stableListKey()}",
                supersetGroupId = null,
                exercises = listOf(item)
            )
            cursor++
            continue
        }

        var endExclusive = cursor + 1
        while (
            endExclusive < exercises.size &&
            exercises[endExclusive].supersetGroupId == groupId
        ) {
            endExclusive++
        }

        val run = exercises.subList(cursor, endExclusive)
        if (run.size < 2) {
            val item = exercises[cursor]
            groups += ExerciseRenderGroup(
                key = "single-${item.key.stableListKey()}",
                supersetGroupId = null,
                exercises = listOf(item)
            )
        } else {
            groups += ExerciseRenderGroup(
                key = "superset-$groupId-${run.joinToString("-") { it.key.stableListKey() }}",
                supersetGroupId = groupId,
                exercises = run
            )
        }

        cursor = endExclusive
    }

    return groups
}

internal fun WorkoutExerciseKey.stableListKey(): String = when (this) {
    is WorkoutExerciseKey.Planned -> "planned-$snapshotId"
    is WorkoutExerciseKey.AdHoc -> "adhoc-$exerciseId"
}

@Composable
internal fun supersetTintColor(supersetGroupId: Int?, indexInSuperset: Int): Color? {
    if (supersetGroupId == null || indexInSuperset < 0) return null
    return when (indexInSuperset % 3) {
        0 -> MaterialTheme.semantic.violet
        1 -> MaterialTheme.semantic.sky
        else -> MaterialTheme.semantic.rose
    }
}

@Composable
internal fun SupersetHeader(groupId: Int, exerciseCount: Int, exerciseNames: String) {
    val dims = ironLogDimens
    IronLogSurfaceCard(
        modifier = Modifier.fillMaxWidth(),
        tone = IronLogSurfaceTone.ACCENT,
        border = BorderStroke(1.dp, MaterialTheme.semantic.violet.copy(alpha = 0.28f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dims.spacingSm, vertical = dims.spacingXs),
            verticalArrangement = Arrangement.spacedBy(dims.spacing2)
        ) {
            Text(
                text = pluralStringResource(
                    id = R.plurals.workout_superset_header,
                    count = exerciseCount,
                    groupId,
                    exerciseCount
                ),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.semantic.violet
            )
            Text(
                text = exerciseNames,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
