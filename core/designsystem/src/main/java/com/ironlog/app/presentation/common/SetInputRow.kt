package com.ironlog.app.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ironlog.core.designsystem.R

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
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = weight,
            onValueChange = onWeightChange,
            label = { Text(stringResource(id = R.string.common_unit_kg)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.width(84.dp),
            singleLine = true
        )
        OutlinedTextField(
            value = reps,
            onValueChange = onRepsChange,
            label = { Text(stringResource(id = R.string.common_reps_short)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(76.dp),
            singleLine = true
        )
        OutlinedTextField(
            value = intensity,
            onValueChange = onIntensityChange,
            label = { Text(intensityLabel) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.width(76.dp),
            singleLine = true
        )
        Button(onClick = onLog) {
            Text(stringResource(id = R.string.common_log))
        }
    }
}
