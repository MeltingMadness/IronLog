package com.ironlog.app.domain.model

enum class ExerciseCategory(val displayName: String) {
    LANGHANTEL("Langhantel"),
    KURZHANTEL("Kurzhantel"),
    MASCHINE("Maschine"),
    KABEL("Kabel"),
    EIGENGEWICHT("Eigengewicht");

    companion object {
        fun safeValueOf(name: String, fallback: ExerciseCategory = LANGHANTEL): ExerciseCategory =
            try {
                valueOf(name)
            } catch (_: IllegalArgumentException) {
                fallback
            }
    }
}
