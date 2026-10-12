package com.thrym.app.domain.model

/** Persisted code; UI labels come from `labelRes()` in core/designsystem. */
enum class MuscleGroup {
    BRUST,
    RUECKEN,
    BEINE,
    SCHULTERN,
    BIZEPS,
    TRIZEPS,
    GESAESS,
    CORE,
    UNTERARME,
    WADEN;

    companion object {
        fun safeValueOf(name: String, fallback: MuscleGroup = BRUST): MuscleGroup =
            try {
                valueOf(name)
            } catch (_: IllegalArgumentException) {
                fallback
            }
    }
}
