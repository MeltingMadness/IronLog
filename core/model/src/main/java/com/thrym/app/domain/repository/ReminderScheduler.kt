package com.thrym.app.domain.repository

import com.thrym.app.domain.model.ReminderConfig

interface ReminderScheduler {
    suspend fun sync(config: ReminderConfig)
    suspend fun cancel()
}
