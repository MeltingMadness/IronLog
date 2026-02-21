package com.ironlog.app.domain.repository

import com.ironlog.app.domain.model.ReminderConfig

interface ReminderScheduler {
    suspend fun sync(config: ReminderConfig)
    suspend fun cancel()
}
