package com.thrym.app.presentation.common

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thrym.app.presentation.theme.ThrymTheme
import com.thrym.core.designsystem.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SetInputRowTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun log_button_semantics_trigger_onLog() {
        var logClicks by mutableIntStateOf(0)

        composeRule.setContent {
            ThrymTheme {
                var reps by remember { mutableStateOf(TextFieldValue("10", TextRange(2))) }
                var weight by remember { mutableStateOf(TextFieldValue("80", TextRange(2))) }
                var intensity by remember { mutableStateOf(TextFieldValue("", TextRange.Zero)) }

                SetInputRow(
                    reps = reps,
                    onRepsChange = { reps = it },
                    weight = weight,
                    onWeightChange = { weight = it },
                    intensity = intensity,
                    onIntensityChange = { intensity = it },
                    intensityLabel = "RPE",
                    showIntensityField = false,
                    onLog = { logClicks++ }
                )
            }
        }

        // Expected copy comes from the resources, so the test passes in every device language.
        val logLabel = composeRule.activity.getString(R.string.common_log)
        composeRule.onNodeWithContentDescription(logLabel).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(logLabel).performClick()

        composeRule.runOnIdle {
            assertEquals(1, logClicks)
        }
    }
}
