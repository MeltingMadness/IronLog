package com.ironlog.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ironlog.app.domain.model.AppPreferences
import com.ironlog.app.domain.repository.AppPreferencesRepository
import com.ironlog.app.domain.repository.ReminderScheduler
import com.ironlog.app.presentation.navigation.BottomNavBar
import com.ironlog.app.presentation.navigation.IronLogNavHost
import com.ironlog.app.presentation.navigation.Screen
import com.ironlog.app.presentation.theme.IronLogTheme
import org.koin.compose.koinInject

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val preferencesRepository: AppPreferencesRepository = koinInject()
            val reminderScheduler: ReminderScheduler = koinInject()
            val preferences by preferencesRepository.preferences.collectAsStateWithLifecycle(
                initialValue = AppPreferences()
            )

            IronLogTheme(
                themeMode = preferences.themeMode,
                themeScheme = preferences.themeScheme,
                useDynamicColor = preferences.useDynamicColor,
                reducedMotion = preferences.reducedMotion
            ) {
                LaunchedEffect(preferences.timerKeepScreenOn) {
                    if (preferences.timerKeepScreenOn) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }

                LaunchedEffect(preferences.reminderConfig) {
                    reminderScheduler.sync(preferences.reminderConfig)
                }

                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                val showBottomBar = currentRoute in listOf(
                    Screen.Dashboard.route,
                    Screen.WorkoutHistory.route,
                    Screen.ExerciseLibrary.route,
                    Screen.TrainingPlanList.route
                )

                Scaffold(
                    bottomBar = {
                        if (showBottomBar) {
                            BottomNavBar(navController)
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .padding(innerPadding)
                            .consumeWindowInsets(innerPadding)
                    ) {
                        IronLogNavHost(navController)
                    }
                }
            }
        }
    }
}
