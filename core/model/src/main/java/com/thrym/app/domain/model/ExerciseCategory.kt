package com.thrym.app.domain.model

/** Persisted code; UI labels come from `labelRes()` in core/designsystem. */
enum class ExerciseCategory {
    LANGHANTEL,
    KURZHANTEL,
    MASCHINE,
    KABEL,
    EIGENGEWICHT;

    companion object {
        fun safeValueOf(name: String, fallback: ExerciseCategory = LANGHANTEL): ExerciseCategory =
            try {
                valueOf(name)
            } catch (_: IllegalArgumentException) {
                fallback
            }
    }
}
