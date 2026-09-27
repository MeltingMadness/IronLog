package com.ironlog.app.presentation.common

import com.ironlog.app.domain.error.AppError
import com.ironlog.core.designsystem.R

fun AppError.toUserMessage(action: String, strings: UiStrings): String = strings.get(
    when (this) {
        AppError.Validation -> R.string.common_action_invalid_input
        AppError.NotFound -> R.string.common_action_not_found
        AppError.Conflict -> R.string.common_action_conflict
        is AppError.Unknown -> R.string.common_action_failed
    },
    action
)
