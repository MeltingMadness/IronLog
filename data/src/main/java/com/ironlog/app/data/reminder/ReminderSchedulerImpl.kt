package com.ironlog.app.data.reminder

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.ironlog.app.domain.model.ReminderConfig
import com.ironlog.app.domain.repository.ReminderScheduler
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ReminderSchedulerImpl(
    private val context: Context
) : ReminderScheduler {

    override suspend fun sync(config: ReminderConfig) {
        TrainingReminderNotifier.ensureChannel(context)
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(UNIQUE_WORK_NAME)

        if (!config.enabled || config.daysOfWeek.isEmpty()) {
            return
        }

        enqueueNext(workManager, LocalDateTime.now(), config)
    }

    override suspend fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    companion object {
        internal const val UNIQUE_WORK_NAME = "ironlog_training_reminder"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_HOUR = "hour"
        private const val KEY_MINUTE = "minute"
        private const val KEY_DAYS = "days"

        internal fun enqueueNext(
            workManager: WorkManager,
            now: LocalDateTime,
            config: ReminderConfig
        ) {
            if (!config.enabled || config.daysOfWeek.isEmpty()) return

            val delay = ReminderScheduleCalculator.computeDelay(now, config)
            val work = OneTimeWorkRequestBuilder<TrainingReminderWorker>()
                .setInitialDelay(delay)
                .setInputData(config.toWorkData())
                .build()

            workManager.enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                work
            )
        }

        internal fun ReminderConfig.toWorkData() = workDataOf(
            KEY_ENABLED to enabled,
            KEY_HOUR to hour,
            KEY_MINUTE to minute,
            KEY_DAYS to daysOfWeek.sortedBy { it.value }.joinToString(",") { it.name }
        )

        internal fun androidx.work.Data.toReminderConfig(): ReminderConfig {
            val days = getString(KEY_DAYS)
                ?.split(',')
                ?.mapNotNull { token -> runCatching { java.time.DayOfWeek.valueOf(token) }.getOrNull() }
                ?.toSet()
                ?: emptySet()

            return ReminderConfig(
                enabled = getBoolean(KEY_ENABLED, false),
                hour = getInt(KEY_HOUR, 19).coerceIn(0, 23),
                minute = getInt(KEY_MINUTE, 0).coerceIn(0, 59),
                daysOfWeek = days
            )
        }
    }
}
