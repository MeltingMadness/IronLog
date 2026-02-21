package com.ironlog.app.domain.model

enum class MuscleGroup(val displayName: String) {
    BRUST("Brust"),
    RUECKEN("Ruecken"),
    BEINE("Beine"),
    SCHULTERN("Schultern"),
    BIZEPS("Bizeps"),
    TRIZEPS("Trizeps"),
    GESAESS("Gesaess"),
    CORE("Core"),
    UNTERARME("Unterarme"),
    WADEN("Waden");

    companion object {
        fun safeValueOf(name: String, fallback: MuscleGroup = BRUST): MuscleGroup =
            try {
                valueOf(name)
            } catch (_: IllegalArgumentException) {
                fallback
            }
    }
}
