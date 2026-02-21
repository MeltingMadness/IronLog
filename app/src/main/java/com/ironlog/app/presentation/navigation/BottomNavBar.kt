package com.ironlog.app.presentation.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.ironlog.app.presentation.theme.ironLogSurfaceRoles

@Composable
fun BottomNavBar(navController: NavController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val surfaces = ironLogSurfaceRoles

    NavigationBar {
        BottomNavItem.entries.forEach { item ->
            val label = stringResource(id = item.labelRes)
            NavigationBarItem(
                modifier = Modifier.testTag(item.testTag),
                icon = { Icon(item.icon, contentDescription = label) },
                label = { Text(label) },
                selected = currentRoute == item.screen.route,
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = surfaces.accentWarning.copy(alpha = 0.16f)
                ),
                onClick = {
                    if (currentRoute != item.screen.route) {
                        navController.navigate(item.screen.route) {
                            popUpTo(Screen.Dashboard.route) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            )
        }
    }
}
