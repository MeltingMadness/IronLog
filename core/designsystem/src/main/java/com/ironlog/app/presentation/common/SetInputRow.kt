package com.ironlog.app.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.ironlog.core.designsystem.R
import com.ironlog.app.presentation.theme.ButtonSize
import com.ironlog.app.presentation.theme.IconSize
import com.ironlog.app.presentation.theme.ironLogDimens

@Composable
fun SetInputRow(
    reps: TextFieldValue,
    onRepsChange: (TextFieldValue) -> Unit,
    weight: TextFieldValue,
    onWeightChange: (TextFieldValue) -> Unit,
    intensity: TextFieldValue,
    onIntensityChange: (TextFieldValue) -> Unit,
    intensityLabel: String,
    weightPlaceholder: String? = null,
    repsPlaceholder: String? = null,
    showIntensityField: Boolean = true,
    onLog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dims = ironLogDimens
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(dims.spacingSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompactTextField(
            value = weight,
            onValueChange = onWeightChange,
            suffix = stringResource(id = R.string.common_unit_kg),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            placeholderText = weightPlaceholder ?: "-",
            modifier = Modifier.weight(1.2f)
        )
        CompactTextField(
            value = reps,
            onValueChange = onRepsChange,
            suffix = stringResource(id = R.string.common_reps_short),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = if (showIntensityField) ImeAction.Next else ImeAction.Done),
            placeholderText = repsPlaceholder ?: "-",
            modifier = Modifier.weight(1f)
        )
        if (showIntensityField) {
            CompactTextField(
                value = intensity,
                onValueChange = onIntensityChange,
                suffix = intensityLabel,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                modifier = Modifier.weight(1f)
            )
        }
        IconButton(
            onClick = onLog,
            modifier = Modifier.size(ButtonSize.iconButton),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = stringResource(id = R.string.common_log),
                modifier = Modifier.size(IconSize.sm)
            )
        }
    }
}
