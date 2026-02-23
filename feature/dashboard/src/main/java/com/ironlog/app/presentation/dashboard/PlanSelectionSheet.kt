package com.ironlog.app.presentation.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ironlog.core.designsystem.R
import com.ironlog.app.domain.model.TrainingPlan
import com.ironlog.app.presentation.theme.ironLogDimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanSelectionSheet(
    plans: List<TrainingPlan>,
    onDismiss: () -> Unit,
    onPlanSelected: (TrainingPlan) -> Unit,
    onFreeWorkoutSelected: () -> Unit
) {
    val dims = ironLogDimens
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = dims.spacingXl)
        ) {
            Text(
                text = stringResource(id = R.string.plan_selection_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = dims.spacingMd, vertical = dims.spacingXs)
            )
            
            LazyColumn {
                items(plans, key = { it.id }) { plan ->
                    ListItem(
                        headlineContent = { Text(plan.name) },
                        leadingContent = { Icon(Icons.Default.Assignment, contentDescription = null) },
                        modifier = Modifier.clickable { onPlanSelected(plan) }
                    )
                }
                
                if (plans.isNotEmpty()) {
                    item { HorizontalDivider(modifier = Modifier.padding(vertical = dims.spacingXs)) }
                }
                
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(id = R.string.plan_selection_free_workout)) },
                        supportingContent = { Text(stringResource(id = R.string.plan_selection_free_workout_subtitle)) },
                        leadingContent = { 
                            Icon(
                                Icons.Default.Add, 
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            ) 
                        },
                        modifier = Modifier.clickable { onFreeWorkoutSelected() }
                    )
                }
            }
        }
    }
}
