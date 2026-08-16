package com.ironlog.app

import android.content.res.Configuration
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ironlog.app.domain.model.AppPreferences
import com.ironlog.app.domain.model.ThemeMode
import com.ironlog.app.domain.repository.AppPreferencesRepository
import com.ironlog.app.domain.repository.ReminderScheduler
import com.ironlog.app.presentation.navigation.BottomNavBar
import com.ironlog.app.presentation.navigation.IronLogNavHost
import com.ironlog.app.presentation.navigation.Screen
import com.ironlog.app.presentation.theme.IronLogTheme
import org.koin.compose.koinInject

class MainActivity : ComponentActivity() {

    // Keeps the splash screen visible until the theme preferences are loaded,
    // avoiding a white flash between the dark splash theme and the dark Compose UI.
    private val splashKeepOnScreen = mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { splashKeepOnScreen.value }
        super.onCreate(savedInstanceState)

        val systemInDarkMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        // Preferences load asynchronously; until then the app-default theme mode (DARK) applies,
        // so the launch theme is dark regardless of the system setting.
        val launchThemeMode = ThemeMode.DARK
        val launchIsDark = when (launchThemeMode) {
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
            ThemeMode.SYSTEM -> systemInDarkMode
        }
        val transparentScrim = android.graphics.Color.TRANSPARENT
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(transparentScrim, transparentScrim) { launchIsDark },
            navigationBarStyle = SystemBarStyle.auto(transparentScrim, transparentScrim) { launchIsDark }
        )
        setContent {
            val preferencesRepository: AppPreferencesRepository = koinInject()
            val reminderScheduler: ReminderScheduler = koinInject()
            val preferencesState by preferencesRepository.preferences.collectAsStateWithLifecycle(
                initialValue = null
            )
            
            splashKeepOnScreen.value = preferencesState == null

            val preferences = preferencesState ?: return@setContent

            val isDarkTheme = when (preferences.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            IronLogTheme(
                themeMode = preferences.themeMode,
                themeScheme = preferences.themeScheme,
                useDynamicColor = preferences.useDynamicColor,
                reducedMotion = preferences.reducedMotion
            ) {
                LaunchedEffect(isDarkTheme) {
                    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                    insetsController.isAppearanceLightStatusBars = !isDarkTheme
                    insetsController.isAppearanceLightNavigationBars = !isDarkTheme
                }

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
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                                        MaterialTheme.colorScheme.background,
                                        MaterialTheme.colorScheme.background
                                    )
                                )
                            )
                    ) {
                        androidx.compose.material3.Scaffold(
                            containerColor = Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.onBackground,
                            bottomBar = {
                                if (showBottomBar) {
                                    BottomNavBar(navController)
                                }
                            }
                        ) { innerPadding ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
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
    }
}
