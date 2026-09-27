package com.ironlog.app.presentation.theme

import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/** Readable labels for unfilled actions and chips on ordinary and glass surfaces. */
object IronLogInteractiveColors {
    @Composable
    fun textButton() = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.accentText)

    @Composable
    fun outlinedButton() = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.accentText)

    @Composable
    fun filterChip() = FilterChipDefaults.filterChipColors(
        labelColor = MaterialTheme.accentText,
        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
    )
}
