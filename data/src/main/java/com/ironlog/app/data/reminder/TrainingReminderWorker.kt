package com.ironlog.app.data.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import java.time.LocalDateTime

class TrainingReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val config = ReminderSchedulerImpl.Companion.run { inputData.toReminderConfig() }
        if (!config.enabled || config.daysOfWeek.isEmpty()) {
            return Result.success()
        }

        TrainingReminderNotifier.ensureChannel(applicationContext)

        val now = LocalDateTime.now()
        if (now.dayOfWeek in config.daysOfWeek) {
            TrainingReminderNotifier.showReminder(applicationContext)
        }

        ReminderSchedulerImpl.enqueueNext(
            workManager = WorkManager.getInstance(applicationContext),
            now = now,
            config = config
        )

        return Result.success()
    }
}
