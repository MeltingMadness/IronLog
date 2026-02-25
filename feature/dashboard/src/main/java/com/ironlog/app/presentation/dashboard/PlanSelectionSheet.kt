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
import androidx.compose.material.icons.filled.Layers
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
import androidx.compose.ui.unit.dp
import com.ironlog.core.designsystem.R
import com.ironlog.app.domain.model.TrainingPlan
import com.ironlog.app.presentation.theme.ironLogDimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanSelectionSheet(
    plans: List<TrainingPlan>,
    metaPlanOptions: List<DashboardMetaPlanOption>,
    onDismiss: () -> Unit,
    onPlanSelected: (TrainingPlan) -> Unit,
    onMetaPlanSelected: (Long) -> Unit,
    onFreeWorkoutSelected: () -> Unit
) {
    val dims = ironLogDimens
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 0.dp
    ) {
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
                    Text(
                        text = stringResource(id = R.string.plan_selection_meta_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = dims.spacingMd, vertical = dims.spacingXs)
                    )
                }

                if (metaPlanOptions.isEmpty()) {
                    item {
                        ListItem(
                            headlineContent = { Text(stringResource(id = R.string.plan_selection_meta_empty)) }
                        )
                    }
                } else {
                    items(metaPlanOptions, key = { it.metaPlanId }) { option ->
                        val nextPlanName = option.nextPlan?.name ?: stringResource(id = R.string.common_unknown)
                        ListItem(
                            headlineContent = { Text(option.metaPlanName) },
                            supportingContent = {
                                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(dims.spacing2)) {
                                    Text(
                                        text = stringResource(
                                            id = R.string.plan_selection_meta_continue_with,
                                            nextPlanName
                                        )
                                    )
                                    option.rotationPlans.forEach { subPlan ->
                                        Text(
                                            text = stringResource(
                                                id = R.string.plan_selection_meta_subplan_last_done,
                                                subPlan.plan.name,
                                                lastDoneLabel(subPlan.lastDoneDaysAgo)
                                            ),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            },
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Default.Layers,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            modifier = Modifier.clickable { onMetaPlanSelected(option.metaPlanId) }
                        )
                    }
                }

                item { HorizontalDivider(modifier = Modifier.padding(vertical = dims.spacingXs)) }
                
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

@Composable
private fun lastDoneLabel(daysAgo: Long?): String {
    return when (daysAgo) {
        null -> stringResource(id = R.string.plan_selection_meta_last_done_never)
        0L -> stringResource(id = R.string.plan_selection_meta_last_done_today)
        1L -> stringResource(id = R.string.plan_selection_meta_last_done_yesterday)
        else -> stringResource(id = R.string.plan_selection_meta_last_done_days_ago, daysAgo.toInt())
    }
}
