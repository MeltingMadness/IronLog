package com.ironlog.app.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ironlog.app.domain.model.UnitSystem
import com.ironlog.app.domain.util.PlateCalculator
import com.ironlog.app.domain.util.WeightFormatting
import com.ironlog.app.presentation.theme.PlateColors
import com.ironlog.app.presentation.theme.Radius
import com.ironlog.app.presentation.theme.ironLogDimens
import com.ironlog.app.presentation.theme.semantic
import com.ironlog.core.designsystem.R

@Composable
fun PlateVisualizer(
    targetWeightKg: Double,
    barbellWeightKg: Double = 20.0,
    availablePlates: List<Double> = PlateCalculator.DEFAULT_USER_PLATES,
    unitSystem: UnitSystem = UnitSystem.METRIC,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    // Invalid values can arrive while a text field is being edited.  They do
    // not describe a load that can be visualized, so wait for the next valid
    // value instead of passing NaN or infinity to the formatter.
    if (
        !enabled ||
        !targetWeightKg.isFinite() ||
        !barbellWeightKg.isFinite() ||
        targetWeightKg <= barbellWeightKg
    ) return

    val result = remember(targetWeightKg, barbellWeightKg, availablePlates) {
        PlateCalculator.calculate(
            targetWeightKg = targetWeightKg,
            barbellWeightKg = barbellWeightKg,
            availablePlates = availablePlates
        )
    }

    val dims = ironLogDimens
    val reachablePerSideFormatted = WeightFormatting.formatWeight(
        result.reachableWeightPerSideKg,
        unitSystem
    )
    val requestedPerSideFormatted = WeightFormatting.formatWeight(result.weightPerSideKg, unitSystem)
    val barFormatted = WeightFormatting.formatWeight(result.barbellWeightKg, unitSystem)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.sm))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                shape = RoundedCornerShape(Radius.sm)
            )
            .padding(horizontal = dims.spacingSm, vertical = dims.spacingXs)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(dims.spacing2)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(
                        id = R.string.workout_plate_calc_reachable,
                        reachablePerSideFormatted
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(
                        id = R.string.workout_plate_calc_barbell,
                        barFormatted
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!result.isExact) {
                Text(
                    text = stringResource(
                        id = R.string.workout_plate_calc_target,
                        requestedPerSideFormatted
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Visual Barbell Sleeve Representation
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Collar / Inner stop
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(36.dp)
                        .background(Color(0xFF64748B), shape = RoundedCornerShape(1.dp))
                )

                // Loaded Plates on sleeve
                result.platesPerSide.forEach { plate ->
                    PlateBlock(weightKg = plate)
                }

                // Remaining barbell sleeve bar
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .background(Color(0xFF475569).copy(alpha = 0.45f), shape = RoundedCornerShape(1.dp))
                )

                // Outer end collar
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(14.dp)
                        .background(Color(0xFF64748B), shape = RoundedCornerShape(1.dp))
                )
            }

            if (result.platesPerSide.isEmpty()) {
                Text(
                    text = stringResource(R.string.workout_plate_calc_no_fit),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.semantic.warning,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (!result.isExact) {
                val remainderFormatted = WeightFormatting.formatWeight(result.remainderKg, unitSystem)
                Text(
                    text = stringResource(
                        id = R.string.workout_plate_calc_unmatched_deficit,
                        remainderFormatted
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.semantic.warning,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun PlateBlock(weightKg: Double) {
    val plateColor = PlateColors.forWeight(weightKg)
    val textColor = PlateColors.onPlateColor(weightKg)

    val heightDp = when {
        weightKg >= 24.9 -> 34.dp
        weightKg >= 19.9 -> 31.dp
        weightKg >= 14.9 -> 27.dp
        weightKg >= 9.9  -> 23.dp
        weightKg >= 4.9  -> 19.dp
        weightKg >= 2.4  -> 16.dp
        weightKg >= 1.2  -> 14.dp
        else             -> 12.dp
    }

    val widthDp = when {
        weightKg >= 24.9 -> 16.dp
        weightKg >= 19.9 -> 15.dp
        weightKg >= 14.9 -> 13.dp
        weightKg >= 9.9  -> 12.dp
        else             -> 11.dp
    }

    val labelText = if (weightKg % 1.0 == 0.0) {
        weightKg.toInt().toString()
    } else {
        weightKg.toString()
    }

    Box(
        modifier = Modifier
            .width(widthDp)
            .height(heightDp)
            .clip(RoundedCornerShape(2.dp))
            .background(plateColor)
            .border(width = 0.5.dp, color = Color.Black.copy(alpha = 0.35f), shape = RoundedCornerShape(2.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (weightKg >= 4.9) {
            Text(
                text = labelText,
                color = textColor,
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}
