package com.thrym.app.presentation.common

import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalResources
import java.util.Locale

/** Resolves UI copy without retaining an Activity in a ViewModel. */
interface UiStrings {
    fun get(@StringRes resourceId: Int, vararg args: Any): String

    /** Plural copy; [quantity] picks the plural form, [args] fill the placeholders. */
    fun getQuantity(@PluralsRes resourceId: Int, quantity: Int, vararg args: Any): String

    /** Locale of the resolved copy; format dates and numbers built next to it with this locale. */
    val locale: Locale get() = Locale.getDefault()
}

/** [UiStrings] for non-composable code that runs inside a composable (callbacks, sorting). */
class ResourcesUiStrings(private val resources: Resources) : UiStrings {
    override fun get(resourceId: Int, vararg args: Any): String = resources.getString(resourceId, *args)

    override fun getQuantity(resourceId: Int, quantity: Int, vararg args: Any): String =
        resources.getQuantityString(resourceId, quantity, *args)

    override val locale: Locale get() = resources.configuration.locales[0] ?: Locale.getDefault()
}

@Composable
fun rememberUiStrings(): UiStrings {
    val resources = LocalResources.current
    return remember(resources) { ResourcesUiStrings(resources) }
}
