package com.ironlog.app.presentation.common

import com.ironlog.app.domain.error.AppError

fun AppError.toUserMessage(action: String): String = when (this) {
    AppError.Validation -> "$action fehlgeschlagen: ungültige Eingabe."
    AppError.NotFound -> "$action fehlgeschlagen: Daten nicht gefunden."
    AppError.Conflict -> "$action fehlgeschlagen: inkonsistenter Zustand."
    is AppError.Unknown -> "$action fehlgeschlagen."
}
