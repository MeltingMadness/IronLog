package com.ironlog.app.domain.model

import java.time.LocalDateTime

data class WorkoutSession(
    val id: Long = 0,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime? = null,
    val durationSeconds: Long = 0,
    val name: String = "",
    val notes: String = "",
    val planId: Long? = null,
    val metaPlanId: Long? = null,
    /**
     * Explicit deload context recorded for this session.
     *
     * `true` = the session ran as a planned deload, `false` = it explicitly did not,
     * `null` = unknown (every row written before Room schema 13). The value is captured
     * when the session is created and never derived retroactively from the current
     * [AppPreferences.deloadMode]; a later switch change must not rewrite history.
     */
    val isDeload: Boolean? = null
)
