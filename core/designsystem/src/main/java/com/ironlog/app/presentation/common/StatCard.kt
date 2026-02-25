package com.ironlog.app.presentation.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ironlog.app.presentation.theme.glow
import com.ironlog.app.presentation.theme.ironLogDimens

enum class StatCardVariant {
    PRIMARY,
    SECONDARY,
    TERTIARY
}

@Composable
fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    variant: StatCardVariant = StatCardVariant.SECONDARY
) {
    val dims = ironLogDimens
    val primary = MaterialTheme.colorScheme.primary

    val (alpha, verticalPadding) = when (variant) {
        StatCardVariant.PRIMARY -> 0.74f to 20.dp
        StatCardVariant.SECONDARY -> 0.62f to dims.spacingMd
        StatCardVariant.TERTIARY -> 0.52f to dims.spacingMd
    }

    IronLogSurfaceCard(
        modifier = modifier,
        tone = IronLogSurfaceTone.MUTED,
        alpha = alpha
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dims.spacingSm, vertical = verticalPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (variant) {
                StatCardVariant.PRIMARY -> {
                    Box(contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier.glow(primary, radius = 12.dp, alpha = 0.18f)
                        )
                        Text(
                            text = value,
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = primary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                StatCardVariant.SECONDARY -> {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = primary,
                        textAlign = TextAlign.Center
                    )
                }
                StatCardVariant.TERTIARY -> {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
