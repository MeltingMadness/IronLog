package com.ironlog.app.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.ironlog.core.designsystem.R
import com.ironlog.app.presentation.theme.ironLogDimens

@Composable
fun SetInputRow(
    reps: String,
    onRepsChange: (String) -> Unit,
    weight: String,
    onWeightChange: (String) -> Unit,
    intensity: String,
    onIntensityChange: (String) -> Unit,
    intensityLabel: String,
    onLog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dims = ironLogDimens
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(dims.spacingXs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = weight,
            onValueChange = onWeightChange,
            label = { Text(stringResource(id = R.string.common_unit_kg)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1.1f),
            singleLine = true
        )
        OutlinedTextField(
            value = reps,
            onValueChange = onRepsChange,
            label = { Text(stringResource(id = R.string.common_reps_short)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
            singleLine = true
        )
        OutlinedTextField(
            value = intensity,
            onValueChange = onIntensityChange,
            label = { Text(intensityLabel) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
            singleLine = true
        )
        IconButton(
            onClick = onLog,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = stringResource(id = R.string.common_log)
            )
        }
    }
}
