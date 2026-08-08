package com.ironlog.app.presentation.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ironlog.core.designsystem.R
import com.ironlog.app.domain.model.TrainingPlan
import com.ironlog.app.presentation.common.IronLogSurfaceCard
import com.ironlog.app.presentation.common.IronLogSurfaceTone
import com.ironlog.app.presentation.theme.ironLogDimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanSelectionSheet(
    plans: List<DashboardPlanStatus>,
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
                .navigationBarsPadding()
                .padding(bottom = dims.spacingMd)
        ) {
            Text(
                text = stringResource(id = R.string.plan_selection_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = dims.spacingMd, vertical = dims.spacingXs)
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = dims.spacingMd, vertical = dims.spacingXs),
                verticalArrangement = Arrangement.spacedBy(dims.spacingXs)
            ) {
                item(key = "meta_header") {
                    Text(
                        text = stringResource(id = R.string.plan_selection_meta_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = dims.spacingMd, vertical = dims.spacingXs)
                    )
                }

                if (metaPlanOptions.isEmpty()) {
                    item(key = "meta_empty") {
                        IronLogSurfaceCard(
                            modifier = Modifier.fillMaxWidth(),
                            tone = IronLogSurfaceTone.MUTED
                        ) {
                            ListItem(
                                headlineContent = { Text(stringResource(id = R.string.plan_selection_meta_empty)) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                        }
                    }
                } else {
                    items(metaPlanOptions, key = { it.metaPlanId }) { option ->
                        val nextPlanName = option.nextPlan?.name ?: stringResource(id = R.string.common_unknown)
                        IronLogSurfaceCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onMetaPlanSelected(option.metaPlanId) }
                                .semantics { role = Role.Button },
                            tone = IronLogSurfaceTone.MUTED
                        ) {
                            ListItem(
                                headlineContent = { Text(option.metaPlanName) },
                                supportingContent = {
                                    Column(verticalArrangement = Arrangement.spacedBy(dims.spacing2)) {
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
                                        contentDescription = option.metaPlanName,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                        }
                    }
                }

                item(key = "meta_plan_divider") {
                    HorizontalDivider(modifier = Modifier.padding(vertical = dims.spacingXs))
                }

                items(plans, key = { it.plan.id }) { plan ->
                    val lastDoneDaysAgo = plan.lastDoneDaysAgo
                    IronLogSurfaceCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlanSelected(plan.plan) }
                            .semantics { role = Role.Button },
                        tone = IronLogSurfaceTone.ELEVATED
                    ) {
                        ListItem(
                            headlineContent = { Text(plan.plan.name) },
                            supportingContent = {
                                Text(
                                    text = lastDoneLabel(lastDoneDaysAgo),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            },
                            leadingContent = {
                                Icon(
                                    Icons.AutoMirrored.Filled.Assignment,
                                    contentDescription = plan.plan.name
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }

                item(key = "normal_plan_divider") {
                    HorizontalDivider(modifier = Modifier.padding(vertical = dims.spacingXs))
                }

                item(key = "free_workout") {
                    IronLogSurfaceCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onFreeWorkoutSelected() }
                            .semantics { role = Role.Button },
                        tone = IronLogSurfaceTone.ELEVATED
                    ) {
                        ListItem(
                            headlineContent = { Text(stringResource(id = R.string.plan_selection_free_workout)) },
                            supportingContent = { Text(stringResource(id = R.string.plan_selection_free_workout_subtitle)) },
                            leadingContent = {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = stringResource(id = R.string.plan_selection_free_workout),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
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
        else -> pluralStringResource(
            id = R.plurals.plan_selection_meta_last_done_days_ago,
            count = daysAgo.toInt(),
            daysAgo.toInt()
        )
    }
}
