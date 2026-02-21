package com.ironlog.app.domain.error

sealed interface AppError {
    data object Validation : AppError
    data object NotFound : AppError
    data object Conflict : AppError
    data class Unknown(val cause: Throwable? = null) : AppError
}

fun Throwable.toAppError(): AppError = when (this) {
    is IllegalArgumentException -> AppError.Validation
    is NoSuchElementException -> AppError.NotFound
    is IllegalStateException -> AppError.Conflict
    else -> AppError.Unknown(this)
}
