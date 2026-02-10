package com.ironlog.app.presentation.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironlog.app.domain.model.Exercise
import com.ironlog.app.domain.model.MuscleGroup
import com.ironlog.app.domain.repository.ExerciseRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.compose.koinInject

/**
 * T-05: Refactored ExercisePickerSheet.
 *
 * Vorher: queryFlow und groupFlow wurden bei jeder Recomposition von
 * außen überschrieben, was zu unnötigen Re-Subscriptions führte.
 *
 * Jetzt: MutableStateFlow wird korrekt als Compose-State behandelt
 * und die Flows koppeln Suche und Filter zuverlässig.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalCoroutinesApi::class)
@Composable
fun ExercisePickerSheet(
    onDismiss: () -> Unit,
    onExerciseSelected: (Exercise) -> Unit,
    exerciseRepository: ExerciseRepository = koinInject()
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by remember { mutableStateOf("") }
    var selectedGroup by remember { mutableStateOf<MuscleGroup?>(null) }

    // T-05: State Flows als remember{} statt aus Compose State direkt zu schreiben
    val queryFlow = remember { MutableStateFlow("") }
    val groupFlow = remember { MutableStateFlow<MuscleGroup?>(null) }

    // Sichere Updates der Flows
    queryFlow.value = searchQuery
    groupFlow.value = selectedGroup

    val exercises by remember(queryFlow, groupFlow) {
        combine(queryFlow, groupFlow) { q, g -> Pair(q, g) }
            .flatMapLatest { (q, g) ->
                when {
                    q.isNotBlank() -> exerciseRepository.searchExercises(q)
                    g != null -> exerciseRepository.getExercisesByMuscleGroup(g)
                    else -> exerciseRepository.getAllExercises()
                }
            }
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Übung suchen") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                singleLine = true
            )

            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedGroup == null,
                    onClick = { selectedGroup = null },
                    label = { Text("Alle") }
                )
                MuscleGroup.entries.forEach { group ->
                    FilterChip(
                        selected = selectedGroup == group,
                        onClick = { selectedGroup = group },
                        label = { Text(group.displayName) }
                    )
                }
            }

            LazyColumn {
                items(exercises, key = { it.id }) { exercise ->
                    ListItem(
                        headlineContent = { Text(exercise.name) },
                        supportingContent = {
                            Text("${exercise.primaryMuscleGroup.displayName} · ${exercise.category.displayName}")
                        },
                        modifier = Modifier.clickable { onExerciseSelected(exercise) }
                    )
                }
            }
        }
    }
}
