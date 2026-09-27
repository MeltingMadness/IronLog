package com.ironlog.app.presentation.common

import androidx.annotation.StringRes

/** Resolves UI copy without retaining an Activity in a ViewModel. */
fun interface UiStrings {
    fun get(@StringRes resourceId: Int, vararg args: Any): String
}
