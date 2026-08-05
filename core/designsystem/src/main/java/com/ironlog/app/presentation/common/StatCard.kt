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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ironlog.app.presentation.theme.glow
import com.ironlog.app.presentation.theme.ironLogDimens
import com.ironlog.app.presentation.theme.semantic

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
    val statColor = when (variant) {
        StatCardVariant.PRIMARY -> MaterialTheme.semantic.teal
        StatCardVariant.SECONDARY -> MaterialTheme.semantic.violet
        StatCardVariant.TERTIARY -> MaterialTheme.semantic.sky
    }
    val (alpha, verticalPadding) = when (variant) {
        StatCardVariant.PRIMARY -> 0.78f to 20.dp
        StatCardVariant.SECONDARY -> 0.72f to dims.spacingMd
        StatCardVariant.TERTIARY -> 0.70f to dims.spacingMd
    }

    IronLogSurfaceCard(
        modifier = modifier,
        tone = IronLogSurfaceTone.COLORED,
        alpha = alpha,
        semanticColor = statColor
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
                            modifier = Modifier.glow(statColor, radius = 12.dp, alpha = 0.16f)
                        )
                        Text(
                            text = value,
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = statColor,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                StatCardVariant.SECONDARY -> {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = statColor,
                        textAlign = TextAlign.Center
                    )
                }

                StatCardVariant.TERTIARY -> {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = statColor,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
                textAlign = TextAlign.Center
            )
        }
    }
}
