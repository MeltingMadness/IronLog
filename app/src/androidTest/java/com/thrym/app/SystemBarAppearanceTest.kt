package com.thrym.app

import android.content.Context
import android.content.res.Configuration
import androidx.core.view.WindowCompat
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.thrym.app.data.preferences.appPreferencesDataStore
import com.thrym.app.domain.model.ThemeMode
import com.thrym.app.domain.repository.AppPreferencesRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

/**
 * Since activity 1.13, edge-to-edge re-applies its system bar style on every configuration change
 * that does not recreate the activity (for example a multi-window resize). The light theme has to
 * keep dark bar icons afterwards instead of falling back to the dark launch style.
 */
@RunWith(AndroidJUnit4::class)
class SystemBarAppearanceTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before fun useLightTheme() {
        runBlocking {
            context.appPreferencesDataStore.edit { it.clear() }
            GlobalContext.get().get<AppPreferencesRepository>().updateThemeMode(ThemeMode.LIGHT)
        }
    }

    @After fun clearPreferences() {
        runBlocking { context.appPreferencesDataStore.edit { it.clear() } }
    }

    @Test fun lightThemeKeepsDarkBarIconsAfterInPlaceConfigurationChange() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitLightSystemBars(scenario)

            scenario.onActivity { activity ->
                val decorView = activity.window.decorView
                decorView.dispatchConfigurationChanged(Configuration(activity.resources.configuration))
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            scenario.onActivity { activity ->
                val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                assertTrue("Status bar icons must stay dark", controller.isAppearanceLightStatusBars)
                assertTrue("Navigation bar icons must stay dark", controller.isAppearanceLightNavigationBars)
            }
        }
    }

    private fun awaitLightSystemBars(scenario: ActivityScenario<MainActivity>) {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            var light = false
            scenario.onActivity { activity ->
                light = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                    .isAppearanceLightStatusBars
            }
            if (light) return
            Thread.sleep(100)
        }
        throw AssertionError("The light theme did not switch to dark bar icons within 10 s")
    }
}
