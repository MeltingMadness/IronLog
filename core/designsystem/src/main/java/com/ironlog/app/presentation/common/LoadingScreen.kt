package com.ironlog.app.presentation.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun LoadingScreen(modifier: Modifier = Modifier) {
    LoadingSkeleton(lineCount = 4, modifier = modifier)
}
