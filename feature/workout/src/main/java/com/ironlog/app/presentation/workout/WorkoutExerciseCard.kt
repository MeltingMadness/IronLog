package com.ironlog.app.presentation.workout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ironlog.core.designsystem.R
import com.ironlog.feature.workout.R as WorkoutR
import com.ironlog.app.domain.model.IntensitySystem
import com.ironlog.app.domain.model.ProgressionConfig
import com.ironlog.app.domain.model.SetType
import com.ironlog.app.domain.model.UnitSystem
import com.ironlog.app.domain.util.DateFormatting
import com.ironlog.app.domain.util.WeightFormatting
import com.ironlog.app.presentation.common.HapticFeedbackHelper
import com.ironlog.app.presentation.common.IronLogSurfaceCard
import com.ironlog.app.presentation.common.IronLogSurfaceTone
import com.ironlog.shared.readinessdata.SetIntention
import androidx.compose.material3.Surface
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.spring
import com.ironlog.app.presentation.theme.ButtonSize
import com.ironlog.app.presentation.theme.IconSize
import com.ironlog.app.presentation.theme.Radius
import com.ironlog.app.presentation.theme.ironLogDimens
import com.ironlog.app.presentation.theme.semantic

@Composable
internal fun ExerciseCard(
    exerciseWithSets: ExerciseWithSets,
    nextSetRecommendation: NextSetRecommendationUi?,
    tintColor: Color?,
    defaultWarmupFlag: Boolean,
    intensitySystem: IntensitySystem,
    unitSystem: UnitSystem,
    plateCalculatorEnabled: Boolean,
    availablePlates: List<Double>,
    barbellWeightKg: Double,
    isLogging: Boolean,
    logSuccessSubmissions: Set<Long>,
    updateInFlightBySet: Map<Long, Int>,
    updateSuccessCountBySet: Map<Long, Int>,
    setIntentions: Map<Long, SetIntention>,
    setIntentionsLoaded: Boolean,
    setIntentionsFailed: Boolean,
    onLogSet: (Int, Double, SetType, String, Long, SetIntention?) -> Unit,
    onUpdateSet: (Long, Int, Double, String, SetIntention?) -> Unit,
    onDeleteSet: (Long) -> Unit,
    haptic: HapticFeedbackHelper
) {
    val dims = ironLogDimens
    val planTarget = exerciseWithSets.planTarget
    val previousSession = exerciseWithSets.previousSession
    val rowIntensitySystem = if (
        intensitySystem == IntensitySystem.OFF && planTarget?.config is ProgressionConfig.RpeRir
    ) {
        IntensitySystem.RPE
    } else {
        intensitySystem
    }
    val showHistoryToggle = previousSession != null
    val previousWeightHint = previousSession?.lastWorkSetWeightKg?.let { formatWeightValue(it, unitSystem) }
    // RPE_RIR plan targets show what to enter into the intensity field; the
    // stored RPE is 10 - RIR when the RIR scale is active, so the hint mirrors
    // the displayed scale. A missing value would otherwise surface as
    // RPE_MISSING with no indication that the field needs input.
    val intensityPlaceholder = (planTarget?.config as? ProgressionConfig.RpeRir)?.let { config ->
        val hint = when (rowIntensitySystem) {
            IntensitySystem.RIR -> 10.0 - config.targetRpe
            else -> config.targetRpe
        }
        formatRpeValue(hint)
    }
    var showPreviousSession by remember(exerciseWithSets.key) { mutableStateOf(false) }
    val loggedSets = exerciseWithSets.sets.filter { it.reps > 0 }
    val completedWorkSets = loggedSets.count { it.setType == SetType.NORMAL }
    val slots = planTarget?.loggingSlots().orEmpty()
    val targetSetCount = slots.size

    IronLogSurfaceCard(
        modifier = Modifier.fillMaxWidth(),
        tone = IronLogSurfaceTone.MUTED,
        border = tintColor?.let { BorderStroke(1.dp, it.copy(alpha = 0.3f)) },
        alpha = 1f
    ) {
        Column(modifier = Modifier.padding(dims.spacingMd)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (showHistoryToggle) {
                            Modifier.clickable { showPreviousSession = !showPreviousSession }
                        } else {
                            Modifier
                        }
                    )
                    .padding(vertical = dims.spacingXs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showHistoryToggle) {
                    Box(
                        modifier = Modifier.size(IconSize.lg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (showPreviousSession) Icons.Default.Remove else Icons.Default.Add,
                            contentDescription = if (showPreviousSession) {
                                stringResource(id = R.string.workout_previous_hide_cd)
                            } else {
                                stringResource(id = R.string.workout_previous_show_cd)
                            },
                            tint = tintColor ?: MaterialTheme.semantic.sky,
                            modifier = Modifier.size(IconSize.sm)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(IconSize.lg))
                }
                Text(
                    text = exerciseWithSets.exercise.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = dims.spacingXs)
                )
            }

            if (planTarget != null) {
                val targetText = if (planTarget.target.weightKg > 0) {
                    stringResource(
                        id = WorkoutR.string.workout_audit_plan_target_with_weight,
                        planTarget.target.sets,
                        planTarget.target.reps,
                        formatTargetWeight(planTarget.target.weightKg, unitSystem)
                    )
                } else {
                    stringResource(
                        id = WorkoutR.string.workout_audit_plan_target_no_weight,
                        planTarget.target.sets,
                        planTarget.target.reps
                    )
                }
                Text(
                    text = targetText,
                    style = MaterialTheme.typography.bodySmall,
                    color = tintColor ?: MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(
                        id = R.string.workout_progression_scheme,
                        progressionSchemeLabel(planTarget.config)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (previousSession?.lastWorkSetReachedTarget == true) {
                    Text(
                        text = stringResource(id = R.string.workout_previous_last_set_target_reached),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.semantic.success
                    )
                }
                if (completedWorkSets >= planTarget.target.sets) {
                    Text(
                        text = stringResource(id = R.string.workout_target_completed),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.semantic.success
                    )
                }
            }

            Spacer(modifier = Modifier.height(dims.spacingXs))

            if (planTarget != null && targetSetCount > 0) {
                val matched = com.ironlog.shared.plans.PlannedSets.matchedIndices(slots, loggedSets.map { it.setType.name })
                val nextSlotIndex = matched.indexOfFirst { it == null }

                for (setIndex in 1..targetSetCount) {
                    val slot = slots[setIndex - 1]
                    val matchingSet = matched[setIndex - 1]?.let { loggedSets[it] }
                    if (matchingSet != null) {
                        LoggedSetRow(
                            set = matchingSet,
                            intention = setIntentions[matchingSet.id],
                            intentionFailed = setIntentionsFailed,
                            intensitySystem = rowIntensitySystem,
                            unitSystem = unitSystem,
                            plateCalculatorEnabled = plateCalculatorEnabled,
                            availablePlates = availablePlates,
                            barbellWeightKg = barbellWeightKg,
                            isUpdating = (updateInFlightBySet[matchingSet.id] ?: 0) > 0,
                            updateSuccessCount = updateSuccessCountBySet[matchingSet.id] ?: 0,
                            onUpdateSet = onUpdateSet,
                            onDeleteSet = onDeleteSet,
                            haptic = haptic
                        )
                    } else {
                        val isNextToLog = setIndex - 1 == nextSlotIndex
                        if (isNextToLog) {
                            val coachHint = nextSetRecommendation?.recommendedWeightKg?.let {
                                stringResource(
                                    WorkoutR.string.workout_audit_coach_suggestion,
                                    "${formatWeightValue(it, unitSystem)} ${WeightFormatting.unitLabel(unitSystem)}"
                                )
                            }
                            ActiveSetCockpitCard(
                                setNumber = setIndex,
                                setType = if (slot.kind == "WARMUP") SetType.WARMUP else SetType.NORMAL,
                                isExtraOrAdHoc = false,
                                isEditMode = false,
                                coachHint = coachHint,
                                valueSource = if (planTarget.setTargets.isEmpty() && loggedSets.any { it.setType == SetType.NORMAL }) "Werte aus dem letzten Satz übernommen · editierbar" else "Werte aus dem Plan · editierbar",
                                coachRecommendation = nextSetRecommendation,
                                defaultWeight = formatWeightValue(if (planTarget.setTargets.isEmpty()) loggedSets.lastOrNull { it.setType == SetType.NORMAL }?.weightKg ?: slot.weightKg else slot.weightKg, unitSystem),
                                weightPlaceholder = targetWeightHint(planTarget, unitSystem, previousWeightHint),
                                defaultReps = (if (planTarget.setTargets.isEmpty()) loggedSets.lastOrNull { it.setType == SetType.NORMAL }?.reps ?: slot.reps else slot.reps).toString(),
                                repsPlaceholder = if (planTarget.target.reps > 0) planTarget.target.reps.toString() else null,
                                defaultIntensity = "",
                                intensityPlaceholder = intensityPlaceholder,
                                intensitySystem = rowIntensitySystem,
                                unitSystem = unitSystem,
                                locked = isLogging,
                                completedSubmissions = logSuccessSubmissions,
                                plateCalculatorEnabled = plateCalculatorEnabled,
                                availablePlates = availablePlates,
                                barbellWeightKg = barbellWeightKg,
                                haptic = haptic,
                                intentionFailed = setIntentionsFailed,
                                onLog = { reps, weight, setType, intensity, submissionId, intention ->
                                    onLogSet(reps, weight, setType, intensity, submissionId, intention)
                                }
                            )
                        } else {
                            PlannedSetPreviewRow(
                                setNumber = setIndex,
                                targetWeight = formatWeightValue(slot.weightKg, unitSystem),
                                targetReps = slot.reps.toString(),
                                unitSystem = unitSystem
                            )
                        }
                    }
                }

                loggedSets.filterIndexed { index, _ -> index !in matched.filterNotNull() }.forEach { set ->
                    LoggedSetRow(
                        set = set,
                        intention = setIntentions[set.id],
                        intentionFailed = setIntentionsFailed,
                        intensitySystem = rowIntensitySystem,
                        unitSystem = unitSystem,
                        plateCalculatorEnabled = plateCalculatorEnabled,
                        availablePlates = availablePlates,
                        barbellWeightKg = barbellWeightKg,
                        isUpdating = (updateInFlightBySet[set.id] ?: 0) > 0,
                        updateSuccessCount = updateSuccessCountBySet[set.id] ?: 0,
                        onUpdateSet = onUpdateSet,
                        onDeleteSet = onDeleteSet,
                        haptic = haptic
                    )
                }

                Spacer(modifier = Modifier.height(dims.spacingXs))

                var showExtraInput by remember { mutableStateOf(false) }
                AnimatedVisibility(visible = showExtraInput) {
                    val nextExtraSetNumber = loggedSets.size + 1
                    ActiveSetCockpitCard(
                        setNumber = nextExtraSetNumber,
                        setType = if (defaultWarmupFlag) SetType.WARMUP else SetType.NORMAL,
                        isExtraOrAdHoc = true,
                        isEditMode = false,
                        coachHint = null,
                        defaultWeight = loggedSets.lastOrNull()?.let { formatWeightValue(it.weightKg, unitSystem) } ?: targetWeightHint(planTarget, unitSystem, previousWeightHint).orEmpty(),
                        weightPlaceholder = targetWeightHint(planTarget, unitSystem, previousWeightHint),
                        defaultReps = (loggedSets.lastOrNull()?.reps ?: planTarget.target.reps).toString(),
                        repsPlaceholder = if (planTarget.target.reps > 0) planTarget.target.reps.toString() else null,
                        defaultIntensity = "",
                        intensityPlaceholder = intensityPlaceholder,
                        intensitySystem = rowIntensitySystem,
                        unitSystem = unitSystem,
                        locked = isLogging,
                        completedSubmissions = logSuccessSubmissions,
                        plateCalculatorEnabled = plateCalculatorEnabled,
                        availablePlates = availablePlates,
                        barbellWeightKg = barbellWeightKg,
                        haptic = haptic,
                        intentionFailed = setIntentionsFailed,
                        onLog = { reps, weight, setType, intensity, submissionId, intention ->
                            onLogSet(reps, weight, setType, intensity, submissionId, intention)
                        },
                        onCancelEdit = { showExtraInput = false }
                    )
                }
                if (!showExtraInput) {
                    TextButton(onClick = { showExtraInput = true }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(id = R.string.workout_add_extra_set))
                    }
                }
            } else {
                loggedSets.forEach { set ->
                    LoggedSetRow(
                        set = set,
                        intention = setIntentions[set.id],
                        intentionFailed = setIntentionsFailed,
                        intensitySystem = rowIntensitySystem,
                        unitSystem = unitSystem,
                        plateCalculatorEnabled = plateCalculatorEnabled,
                        availablePlates = availablePlates,
                        barbellWeightKg = barbellWeightKg,
                        isUpdating = (updateInFlightBySet[set.id] ?: 0) > 0,
                        updateSuccessCount = updateSuccessCountBySet[set.id] ?: 0,
                        onUpdateSet = onUpdateSet,
                        onDeleteSet = onDeleteSet,
                        haptic = haptic
                    )
                }

                Spacer(modifier = Modifier.height(dims.spacingXs))

                val nextSetNumber = loggedSets.size + 1
                ActiveSetCockpitCard(
                    setNumber = nextSetNumber,
                    setType = if (defaultWarmupFlag) SetType.WARMUP else SetType.NORMAL,
                    isExtraOrAdHoc = true,
                    isEditMode = false,
                    coachHint = null,
                    defaultWeight = loggedSets.lastOrNull()?.let { formatWeightValue(it.weightKg, unitSystem) } ?: previousWeightHint.orEmpty(),
                    weightPlaceholder = previousWeightHint,
                    defaultReps = loggedSets.lastOrNull()?.reps?.toString().orEmpty(),
                    repsPlaceholder = null,
                    defaultIntensity = "",
                    intensityPlaceholder = intensityPlaceholder,
                    intensitySystem = rowIntensitySystem,
                    unitSystem = unitSystem,
                    locked = isLogging,
                    completedSubmissions = logSuccessSubmissions,
                    plateCalculatorEnabled = plateCalculatorEnabled,
                    availablePlates = availablePlates,
                    barbellWeightKg = barbellWeightKg,
                    haptic = haptic,
                    intentionFailed = setIntentionsFailed,
                    onLog = { reps, weight, setType, intensity, submissionId, intention ->
                        onLogSet(reps, weight, setType, intensity, submissionId, intention)
                    }
                )
            }

            nextSetRecommendation?.let { recommendation ->
                NextSetRecommendationChips(
                    recommendation = recommendation,
                    unitSystem = unitSystem,
                    modifier = Modifier.padding(top = dims.spacingXs)
                )
            }

            AnimatedVisibility(
                visible = showPreviousSession && previousSession != null,
                enter = fadeIn() + expandVertically(animationSpec = spring()),
                exit = fadeOut() + shrinkVertically(animationSpec = spring())
            ) {
                previousSession?.let {
                    PreviousSessionMiniHistory(
                        previousSession = it,
                        intensitySystem = rowIntensitySystem,
                        unitSystem = unitSystem,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = dims.spacingSm)
                    )
                }
            }
        }
    }
}

@Composable
internal fun PreviousSessionMiniHistory(
    previousSession: PreviousExerciseSessionUi,
    intensitySystem: IntensitySystem,
    unitSystem: UnitSystem,
    modifier: Modifier = Modifier
) {
    val dims = ironLogDimens
    val dateLabel = previousSession.sessionStart.format(DateFormatting.DATE_SHORT)

    Column(
        modifier = modifier
            .background(
                color = MaterialTheme.semantic.skyLight.copy(alpha = 0.35f),
                shape = MaterialTheme.shapes.small
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.semantic.sky.copy(alpha = 0.28f),
                shape = MaterialTheme.shapes.small
            )
            .padding(horizontal = dims.spacingSm, vertical = dims.spacingXs),
        verticalArrangement = Arrangement.spacedBy(dims.spacing2)
    ) {
        Text(
            text = stringResource(id = WorkoutR.string.workout_audit_previous_session_title, dateLabel),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = dims.spacing2),
            verticalArrangement = Arrangement.spacedBy(dims.spacingXs)
        ) {
            previousSession.sets.forEach { set ->
                PreviousSessionSetRow(
                    set = set,
                    intensitySystem = intensitySystem,
                    unitSystem = unitSystem
                )
            }
        }
    }
}

@Composable
internal fun PreviousSessionSetRow(
    set: com.ironlog.app.domain.model.WorkoutSet,
    intensitySystem: IntensitySystem,
    unitSystem: UnitSystem,
    modifier: Modifier = Modifier
) {
    val dims = ironLogDimens
    val tracksIntensity = intensitySystem != IntensitySystem.OFF
    val setLabel = setTypeLabel(set.setNumber, set.setType)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = dims.spacing2),
        horizontalArrangement = Arrangement.spacedBy(dims.spacingSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = setLabel,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.width(20.dp)
        )

        val weightText = formatWeightValue(set.weightKg, unitSystem)

        LoggedSetBox(
            value = weightText,
            suffix = WeightFormatting.unitLabel(unitSystem),
            isWarmup = set.isWarmup,
            modifier = Modifier.weight(1.2f).alpha(0.7f)
        )

        LoggedSetBox(
            value = set.reps.toString(),
            suffix = stringResource(id = R.string.common_reps_short),
            isWarmup = set.isWarmup,
            modifier = Modifier.weight(1f).alpha(0.7f)
        )

        if (tracksIntensity) {
            val intensityText = formatIntensity(set.rpe, intensitySystem)
            val accentColor = rpeColor(set.rpe)

            LoggedSetBox(
                value = intensityText,
                suffix = intensitySystem.displayName,
                isWarmup = set.isWarmup,
                modifier = Modifier.weight(1f).alpha(0.7f),
                overrideContainerColor = accentColor?.copy(alpha = 0.15f),
                overrideContentColor = accentColor
            )
        }

        // Placeholder for the delete button to maintain perfect alignment with LoggedSetRow
        Spacer(modifier = Modifier.size(40.dp))
    }
}

@Composable
internal fun LoggedSetBox(
    value: String,
    suffix: String,
    isWarmup: Boolean,
    modifier: Modifier = Modifier,
    overrideContainerColor: Color? = null,
    overrideContentColor: Color? = null
) {
    val dims = ironLogDimens
    val containerColor = overrideContainerColor ?: if (isWarmup) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    }

    Box(
        modifier = modifier
            .height(ButtonSize.iconButton)
            .background(color = containerColor, shape = RoundedCornerShape(Radius.sm)),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = dims.spacingSm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = value,
                    fontSize = MaterialTheme.typography.titleMedium.fontSize,
                    fontWeight = FontWeight.SemiBold,
                    color = overrideContentColor ?: if (isWarmup) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    fontStyle = if (isWarmup) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
            if (suffix.isNotEmpty()) {
                Text(
                    text = suffix,
                    style = MaterialTheme.typography.bodyMedium,
                    color = (overrideContentColor ?: MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.9f),
                    modifier = Modifier.padding(start = dims.spacingXs)
                )
            }
        }
    }
}

@Composable
internal fun NextSetRecommendationChips(
    recommendation: NextSetRecommendationUi,
    unitSystem: UnitSystem,
    modifier: Modifier = Modifier
) {
    val dims = ironLogDimens
    val weightText = formatWeightValue(recommendation.recommendedWeightKg, unitSystem)
    val delta = recommendation.recommendedWeightKg - recommendation.lastWeightKg
    val loadText = if (kotlin.math.abs(delta) < 0.05) {
        stringResource(id = R.string.workout_autoregulation_next_set, weightText)
    } else {
        stringResource(
            id = R.string.workout_autoregulation_next_set_delta,
            weightText,
            WeightFormatting.formatWeightDelta(delta, unitSystem)
        )
    }
    val accent = if (recommendation.isOvershoot) {
        MaterialTheme.semantic.danger
    } else {
        MaterialTheme.semantic.sky
    }

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(dims.spacingXs),
        verticalArrangement = Arrangement.spacedBy(dims.spacingXs)
    ) {
        recommendation.targetRpe?.let { target ->
            RecommendationPill(
                text = stringResource(id = R.string.workout_autoregulation_target_rpe, formatRpeValue(target)),
                color = MaterialTheme.semantic.sky
            )
        }
        if (recommendation.isOvershoot) {
            RecommendationPill(
                text = stringResource(
                    id = R.string.workout_autoregulation_overshoot,
                    formatRpeValue(recommendation.lastRpe)
                ),
                color = MaterialTheme.semantic.danger
            )
        }
        RecommendationPill(
            text = stringResource(WorkoutR.string.workout_audit_coach_suggestion, loadText),
            color = accent
        )
        recommendation.backoffWeightKg?.let { backoff ->
            RecommendationPill(
                text = stringResource(
                    id = R.string.workout_autoregulation_backoff_set,
                    formatWeightValue(backoff, unitSystem)
                ),
                color = MaterialTheme.semantic.danger
            )
        }
    }
}

@Composable
internal fun RecommendationPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
internal fun PlannedSetPreviewRow(
    setNumber: Int,
    targetWeight: String?,
    targetReps: String?,
    unitSystem: UnitSystem,
    modifier: Modifier = Modifier
) {
    val dims = ironLogDimens
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = dims.spacingXs),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(6.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = setNumber.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val targetDesc = buildString {
                if (targetWeight != null) {
                    append(targetWeight)
                    append(" ")
                    append(WeightFormatting.unitLabel(unitSystem))
                }
                if (targetReps != null) {
                    if (isNotEmpty()) append(" × ")
                    append(targetReps)
                    append(" ")
                    append(stringResource(R.string.common_reps_short))
                }
            }

            Text(
                text = stringResource(R.string.workout_planned_set_preview, setNumber, targetDesc.ifEmpty { "-" }),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                fontWeight = FontWeight.Medium
            )
        }
    }
}
