package com.ironlog.app.presentation.common

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

class HapticFeedbackHelper(private val view: View) {
    fun tick() {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    fun confirm() {
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    }

    fun reject() {
        view.performHapticFeedback(HapticFeedbackConstants.REJECT)
    }
}

@Composable
fun rememberHapticFeedback(): HapticFeedbackHelper {
    val view = LocalView.current
    return remember(view) { HapticFeedbackHelper(view) }
}
