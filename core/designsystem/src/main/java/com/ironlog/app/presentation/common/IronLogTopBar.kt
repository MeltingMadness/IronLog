package com.ironlog.app.presentation.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ironlog.app.domain.model.AppearanceStyle
import com.ironlog.app.presentation.theme.GlassLevel
import com.ironlog.app.presentation.theme.LocalAppearanceStyle
import com.ironlog.app.presentation.theme.liquidGlass

/**
 * Top bar of the secondary screens. Ember keeps the transparent Material top bar. Liquid Glass
 * shows a larger title, the navigation button as a round glass button and the actions grouped
 * in one glass pill, like the iOS toolbar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IronLogTopBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null
) {
    val glass = LocalAppearanceStyle.current == AppearanceStyle.LIQUID_GLASS
    TopAppBar(
        modifier = modifier,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent
        ),
        title = {
            if (glass) {
                ProvideTextStyle(MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold)) {
                    title()
                }
            } else {
                title()
            }
        },
        navigationIcon = {
            if (navigationIcon != null) {
                if (glass) {
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp, end = 4.dp)
                            .size(48.dp)
                            .liquidGlass(GlassLevel.STANDARD, CircleShape),
                        contentAlignment = Alignment.Center
                    ) { navigationIcon() }
                } else {
                    navigationIcon()
                }
            }
        },
        actions = {
            if (actions != null) {
                if (glass) {
                    Row(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .liquidGlass(GlassLevel.STANDARD, RoundedCornerShape(24.dp)),
                        verticalAlignment = Alignment.CenterVertically
                    ) { actions() }
                } else {
                    actions()
                }
            }
        }
    )
}
